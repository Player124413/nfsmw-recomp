package dev.recompkit_nfsmw.android.game

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dev.recompkit_nfsmw.android.BuildInfo
import dev.recompkit_nfsmw.android.layout.JsonLite
import dev.recompkit_nfsmw.android.layout.JsonValue
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Gets the game files into files/game/, where the game reads them from.
 *
 * - full builds: staged out of the APK's assets/game/ once per build (the
 *   CI manifest in BuildInfo identifies the tree);
 * - slim builds: imported from the phone, from a folder or a zip archive
 *   of the user's own game installation (see GameFilesFilter).
 *
 * A .manifest.json in files/game/ marks the tree; [staged] is true when
 * the tree matches this build (full) or is a valid import (slim).
 */
object AssetStager {

    private const val TAG = "assetstager"
    private const val GAME_DIR = "game"
    private const val MANIFEST = ".manifest.json"
    private const val SOURCE_BUNDLED = "bundled"
    private const val SOURCE_IMPORTED = "imported"

    class Report(
        val ok: Boolean,
        val fileCount: Int,
        val totalBytes: Long,
        val message: String,
        val warnings: List<String> = emptyList()
    )

    class Progress(val filesDone: Int, val filesTotal: Int, val bytesDone: Long, val bytesTotal: Long)

    private fun gameDir(context: Context) = File(context.filesDir, GAME_DIR)
    private fun manifestFile(context: Context) = File(gameDir(context), MANIFEST)

    /** The staged tree is present and valid for this build. */
    fun staged(context: Context): Boolean {
        val m = manifestFile(context)
        if (!m.exists()) return false
        return try {
            val o = JsonLite.parse(m.readText()) as? JsonValue.Obj ?: return false
            val count = (o.entries["file_count"] as? JsonValue.Num)?.value?.toInt() ?: return false
            if (count <= 0) return false
            if (BuildInfo.hasAssets) {
                val sha = (o.entries["asset_sha"] as? JsonValue.Str)?.value ?: return false
                val bytes = (o.entries["total_bytes"] as? JsonValue.Num)?.value?.toLong() ?: return false
                sha == BuildInfo.ASSET_SHA && bytes == BuildInfo.ASSET_BYTES
            } else {
                (o.entries["source"] as? JsonValue.Str)?.value == SOURCE_IMPORTED
            }
        } catch (t: Throwable) {
            false
        }
    }

    /** Details for the launcher's status line (both build kinds). */
    fun stagedReport(context: Context): Report {
        val m = manifestFile(context)
        if (!m.exists()) return Report(false, 0, 0, "")
        return try {
            val o = JsonLite.parse(m.readText()) as? JsonValue.Obj ?: return Report(false, 0, 0, "")
            val count = (o.entries["file_count"] as? JsonValue.Num)?.value?.toInt() ?: return Report(false, 0, 0, "")
            val bytes = (o.entries["total_bytes"] as? JsonValue.Num)?.value?.toLong() ?: 0L
            Report(staged(context), count, bytes, "")
        } catch (t: Throwable) {
            Report(false, 0, 0, "")
        }
    }

    /**
     * Copies the bundled game files into files/game/. Must run off the UI
     * thread (it streams gigabytes); [onProgress] is called on that thread.
     */
    fun stage(context: Context, onProgress: (Progress) -> Unit): Report {
        val target = gameDir(context)
        if (!BuildInfo.hasAssets) {
            return Report(true, 0, 0, "no game files in this build")
        }
        if (staged(context)) {
            return Report(true, BuildInfo.ASSET_COUNT, BuildInfo.ASSET_BYTES, "already staged")
        }

        val entries = listAssets(context, GAME_DIR)
        if (entries.isEmpty()) {
            return Report(false, 0, 0, "the build info says game files are bundled, but none were found in the APK")
        }

        val outRoot = resetTarget(context) ?: return Report(false, 0, 0, "cannot create " + target.absolutePath)

        var filesDone = 0
        var bytesDone = 0L
        try {
            for (rel in entries) {
                val out = File(outRoot, rel)
                out.parentFile?.mkdirs()
                context.assets.open("$GAME_DIR/$rel").use { input ->
                    out.outputStream().use { output ->
                        input.copyTo(output, 1 shl 20)
                    }
                }
                filesDone++
                bytesDone += out.length()
                onProgress(Progress(filesDone, entries.size, bytesDone, BuildInfo.ASSET_BYTES))
            }
        } catch (e: IOException) {
            Log.e(TAG, "staging failed", e)
            return Report(false, filesDone, bytesDone, "copying the game files failed: " + e.message)
        }
        return finishInstall(context, filesDone, bytesDone, SOURCE_BUNDLED, BuildInfo.ASSET_SHA)
    }

    /**
     * Imports the game files from a folder on the phone (the user's game
     * installation directory, or a folder that contains it). Two passes:
     * list, then copy, so the progress bar knows the total up front.
     */
    fun importFromTree(context: Context, treeUri: Uri, onProgress: (Progress) -> Unit): Report {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        if (root == null || !root.isDirectory) {
            return Report(false, 0, 0, "could not open the selected folder")
        }

        val found = ArrayList<Pair<String, DocumentFile>>()
        collectTree(root, "", found)
        val rels = found.map { it.first }
        if (rels.isEmpty()) {
            return Report(false, 0, 0, "the selected folder is empty")
        }

        val normalized = GameFilesFilter.normalizeTopDir(rels)
        val included = normalized.paths.filter { GameFilesFilter.include(it) }.toSortedArray()
        if (included.isEmpty()) {
            return Report(false, 0, 0, "no game files found in the folder (everything was filtered out)")
        }

        // normalized.paths keeps the order of rels, so index i maps to found[i].
        val byRel = HashMap<String, DocumentFile>()
        for (i in found.indices) byRel[normalized.paths[i]] = found[i].second
        val totalBytes = included.sumOf { byRel[it]?.length() ?: 0L }

        val target = gameDir(context)
        val outRoot = resetTarget(context) ?: return Report(false, 0, 0, "cannot create " + target.absolutePath)

        var filesDone = 0
        var bytesDone = 0L
        try {
            for (rel in included) {
                val doc = byRel[rel] ?: continue
                val out = File(outRoot, rel)
                out.parentFile?.mkdirs()
                context.contentResolver.openInputStream(doc.uri)?.use { input ->
                    out.outputStream().use { output ->
                        input.copyTo(output, 1 shl 20)
                    }
                } ?: throw IOException("could not open " + rel)
                filesDone++
                bytesDone += out.length()
                onProgress(Progress(filesDone, included.size, bytesDone, totalBytes))
            }
        } catch (e: IOException) {
            Log.e(TAG, "import from folder failed", e)
            return Report(false, filesDone, bytesDone, "copying the game files failed: " + e.message)
        }
        return finishInstall(context, filesDone, bytesDone, SOURCE_IMPORTED, "")
    }

    /**
     * Imports the game files from a zip archive on the phone (a zip of the
     * game installation directory). Two streaming passes: the first lists
     * the entries, the second copies them; entry sizes come from the zip
     * header, so the total is known before anything is copied.
     */
    fun importFromZip(context: Context, uri: Uri, onProgress: (Progress) -> Unit): Report {
        // Pass 1: list the entries we would copy.
        val plan = ArrayList<Pair<String, Long>>() // zip name -> known size (0 when unknown)
        try {
            context.contentResolver.openInputStream(uri)?.use { fis ->
                val zin = ZipInputStream(BufferedInputStream(fis, 1 shl 20))
                var e: ZipEntry? = zin.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        plan.add(e.name to (if (e.size >= 0) e.size else 0L))
                    }
                    zin.closeEntry()
                    e = zin.nextEntry
                }
            } ?: return Report(false, 0, 0, "could not open the archive")
        } catch (e: IOException) {
            return Report(false, 0, 0, "could not read the archive: " + e.message)
        }

        val rels = plan.map { GameFilesFilter.safeZipPath(it.first) ?: "" }.filter { it.isNotEmpty() }
        if (rels.isEmpty()) {
            return Report(false, 0, 0, "the archive contains no files")
        }
        val normalized = GameFilesFilter.normalizeTopDir(rels)
        val included = normalized.paths.filter { GameFilesFilter.include(it) }.toSortedArray()
        if (included.isEmpty()) {
            return Report(false, 0, 0, "no game files found in the archive (everything was filtered out)")
        }

        val sizeByRel = HashMap<String, Long>()
        for (i in rels.indices) sizeByRel[normalized.paths[i]] = plan[i].second
        val totalBytes = included.sumOf { sizeByRel[it] ?: 0L }

        val target = gameDir(context)
        val outRoot = resetTarget(context) ?: return Report(false, 0, 0, "cannot create " + target.absolutePath)

        // Pass 2: copy the entries we planned.
        val wanted = included.toHashSet()
        var filesDone = 0
        var bytesDone = 0L
        try {
            context.contentResolver.openInputStream(uri)?.use { fis ->
                val zin = ZipInputStream(BufferedInputStream(fis, 1 shl 20))
                var e: ZipEntry? = zin.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val rel = GameFilesFilter.safeZipPath(e.name)?.removePrefix(normalized.strippedPrefix)
                        if (rel in wanted) {
                            val out = File(outRoot, rel)
                            out.parentFile?.mkdirs()
                            out.outputStream().use { output ->
                                zin.copyTo(output, 1 shl 20)
                            }
                            filesDone++
                            bytesDone += out.length()
                            onProgress(Progress(filesDone, included.size, bytesDone, totalBytes))
                        }
                    }
                    zin.closeEntry()
                    e = zin.nextEntry
                }
            } ?: return Report(false, filesDone, bytesDone, "could not open the archive")
        } catch (e: IOException) {
            Log.e(TAG, "import from zip failed", e)
            return Report(false, filesDone, bytesDone, "could not read the archive: " + e.message)
        }
        if (filesDone < included.size) {
            return Report(
                false, filesDone, bytesDone,
                "the archive changed between listing and copying (got $filesDone of ${included.size} files)"
            )
        }
        return finishInstall(context, filesDone, bytesDone, SOURCE_IMPORTED, "")
    }

    // ------------------------------------------------------------------

    /** A fresh files/game/ tree: stale files must not shadow the new ones. */
    private fun resetTarget(context: Context): File? {
        val target = gameDir(context)
        runCatching { target.deleteRecursively() }
        return if (target.mkdirs()) target else null
    }

    private fun writeManifest(
        context: Context,
        source: String,
        count: Int,
        bytes: Long,
        assetSha: String
    ) {
        val manifest = JsonLite.encode(
            JsonLite.obj(
                "schema" to JsonLite.num(1.0),
                "source" to JsonLite.str(source),
                "asset_sha" to JsonLite.str(assetSha),
                "file_count" to JsonLite.num(count.toDouble()),
                "total_bytes" to JsonLite.num(bytes.toDouble())
            )
        )
        runCatching { manifestFile(context).writeText(manifest) }
    }

    private fun finishInstall(context: Context, filesDone: Int, bytesDone: Long, source: String, assetSha: String): Report {
        writeManifest(context, source, filesDone, bytesDone, assetSha)
        val warnings = verifyTree(context)
        Log.i(TAG, "installed $filesDone files, ${bytesDone / (1024 * 1024)} MB ($source)")
        return Report(true, filesDone, bytesDone, "installed", warnings)
    }

    /** Sanity check of the installed tree (the same eyes as the CI validator). */
    fun verifyTree(context: Context): List<String> {
        val warnings = ArrayList<String>()
        val dir = gameDir(context)
        if (!dir.isDirectory) {
            warnings.add("the game directory is missing")
            return warnings
        }
        var count = 0
        var bytes = 0L
        for (f in dir.walkTopDown()) {
            if (f.isFile) {
                count++
                bytes += f.length()
            }
        }
        if (count == 0) {
            warnings.add("no game files were copied")
        } else if (bytes < 50L * 1024 * 1024) {
            warnings.add("the game is only %d MB; a full install is over 1 GB".format(bytes / (1024 * 1024)))
        }
        val have = dir.listFiles()?.map { it.name.uppercase() }?.toSet() ?: emptySet()
        for (need in listOf("SOUND", "TRACKS", "GLOBAL")) {
            if (need !in have) warnings.add("no $need/ folder in the game files")
        }
        return warnings
    }

    /** Recursively lists the files of a SAF tree; excluded dirs are pruned. */
    private fun collectTree(
        dir: DocumentFile,
        prefix: String,
        out: MutableList<Pair<String, DocumentFile>>
    ) {
        for (child in dir.listFiles()) {
            val name = child.name ?: continue
            if (GameFilesFilter.isExcludedDir(name)) continue
            val rel = if (prefix.isEmpty()) name else "$prefix/$name"
            if (child.isDirectory) {
                collectTree(child, rel, out)
            } else {
                out.add(rel to child)
            }
        }
    }

    /** All files under the given assets/ path, as relative paths. */
    private fun listAssets(context: Context, path: String): List<String> {
        val result = ArrayList<String>()
        val names = context.assets.list(path) ?: return result
        for (name in names) {
            val child = "$path/$name"
            // A directory has no openable file; openFd throws for it.
            val isDir = try {
                context.assets.openFd(child).use { false }
            } catch (e: IOException) {
                true
            }
            if (isDir) result += listAssets(context, child) else result += child
        }
        return result
    }
}
