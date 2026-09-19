package dev.recompkit_nfsmw.android.game

import android.content.Context
import android.util.Log
import dev.recompkit_nfsmw.android.BuildInfo
import dev.recompkit_nfsmw.android.layout.JsonLite
import dev.recompkit_nfsmw.android.layout.JsonValue
import java.io.File
import java.io.IOException

/**
 * Copies the bundled game files (assets/game/**) into files/game/ on first
 * launch, once per build (the CI manifest in BuildInfo identifies the tree).
 * The game gets the absolute path of files/game/ at init.
 */
object AssetStager {

    private const val TAG = "assetstager"
    private const val GAME_DIR = "game"
    private const val MANIFEST = ".manifest.json"

    class Report(val ok: Boolean, val fileCount: Int, val totalBytes: Long, val message: String)

    class Progress(val filesDone: Int, val filesTotal: Int, val bytesDone: Long, val bytesTotal: Long)

    private fun gameDir(context: Context) = File(context.filesDir, GAME_DIR)
    private fun manifestFile(context: Context) = File(gameDir(context), MANIFEST)

    /** The staged tree matches the build info. */
    fun staged(context: Context): Boolean {
        if (!BuildInfo.hasAssets) return false
        val m = manifestFile(context)
        if (!m.exists()) return false
        return try {
            val o = JsonLite.parse(m.readText()) as? JsonValue.Obj ?: return false
            val sha = (o.entries["asset_sha"] as? JsonValue.Str)?.value ?: return false
            val count = (o.entries["file_count"] as? JsonValue.Num)?.value?.toInt() ?: return false
            val bytes = (o.entries["total_bytes"] as? JsonValue.Num)?.value?.toLong() ?: return false
            sha == BuildInfo.ASSET_SHA && count == BuildInfo.ASSET_COUNT && bytes == BuildInfo.ASSET_BYTES
        } catch (t: Throwable) {
            false
        }
    }

    fun stagedReport(context: Context): Report {
        return if (staged(context)) {
            Report(true, BuildInfo.ASSET_COUNT, BuildInfo.ASSET_BYTES, "staged")
        } else {
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

        // A fresh tree every build: stale files from an older build must not
        // shadow the new ones.
        runCatching { target.deleteRecursively() }
        if (!target.mkdirs()) {
            return Report(false, 0, 0, "cannot create " + target.absolutePath)
        }

        var filesDone = 0
        var bytesDone = 0L
        try {
            for (rel in entries) {
                val out = File(target, rel)
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

        val manifest = JsonLite.encode(
            JsonLite.obj(
                "schema" to JsonLite.num(1.0),
                "asset_sha" to JsonLite.str(BuildInfo.ASSET_SHA),
                "file_count" to JsonLite.num(entries.size.toDouble()),
                "total_bytes" to JsonLite.num(bytesDone.toDouble())
            )
        )
        runCatching { manifestFile(context).writeText(manifest) }
        Log.i(TAG, "staged ${entries.size} files, ${bytesDone / (1024 * 1024)} MB")
        return Report(true, entries.size, bytesDone, "staged")
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
