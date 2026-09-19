package dev.recompkit_nfsmw.android.game

/**
 * Decides which files of a game installation belong on the phone.
 *
 * The base rule is `[bundle].exclude` from game.toml (the same list the iOS
 * bundle uses: uninstaller, manual, Windows ASI loader and widescreen fix,
 * LAN server, installer helpers, the repack's saves), extended with
 * platform junk that can only waste storage: executables, native code
 * modules, archives, OS metadata. Case-insensitive, applied to relative
 * paths with forward slashes. Pure functions - unit-tested in
 * GameFilesFilterTest - shared by both importers (folder and zip).
 */
object GameFilesFilter {

    /** Directories dropped wherever they appear (lowercase names). */
    private val excludedDirs = setOf(
        "uninstall", "support", "scripts", "save", "foobar",
        "__macosx", ".git", ".svn", "system volume information", "\$recycle.bin"
    )

    /** Exact file names dropped wherever they appear (lowercase names). */
    private val excludedFiles = setOf(
        "thumbs.db", ".ds_store", "desktop.ini", "server.cfg",
        "readme.md", "readme.txt", "manifest.json", "manifest.txt", ".gitkeep"
    )

    /** File extensions dropped wherever they appear (lowercase names). */
    private val excludedExtensions = setOf(
        ".dll", ".exe", ".cxx", ".msi", ".cab", ".lnk", ".txt",
        ".tmp", ".bak", ".log", ".zip", ".7z", ".rar", ".iso", ".img"
    )

    /** Whether a directory name is dropped together with its contents. */
    fun isExcludedDir(name: String): Boolean = name.lowercase() in excludedDirs

    /** Whether a relative path (forward slashes) is a game file. */
    fun include(relPath: String): Boolean {
        val parts = relPath.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return false
        val name = parts.last().lowercase()
        if (name in excludedFiles) return false
        val dot = name.lastIndexOf('.')
        if (dot in 1 until name.length && name.substring(dot) in excludedExtensions) return false
        return parts.dropLast(1).none { isExcludedDir(it) }
    }

    /** The result of [normalizeTopDir]: the paths and the prefix (if any). */
    data class Normalized(val paths: List<String>, val strippedPrefix: String)

    /**
     * If every path sits under one single top-level directory (the user
     * zipped or picked the wrapper folder that contains the game), that
     * prefix is stripped. A real game root has at least two subdirectories,
     * so a content-only pick (e.g. a folder with just GLOBAL/) is left
     * untouched.
     */
    fun normalizeTopDir(paths: List<String>): Normalized {
        if (paths.isEmpty()) return Normalized(paths, "")
        val tops = paths.map { it.substringBefore('/') }
        if (!tops.all { it.lowercase() == tops.first().lowercase() }) return Normalized(paths, "")
        val prefix = tops.first() + "/"
        val stripped = paths.map { it.removePrefix(prefix) }.filter { it.isNotEmpty() }
        if (stripped.size != paths.size) return Normalized(paths, "")
        // Only real subdirectories count: a flat file list has no slash,
        // so its "tops" are file names, not a second directory level.
        val subdirs = stripped.mapNotNull { p ->
            val i = p.indexOf('/')
            if (i > 0) p.substring(0, i).lowercase() else null
        }.distinct()
        return if (subdirs.size >= 2) Normalized(stripped, prefix) else Normalized(paths, "")
    }

    /**
     * A safe relative path out of a zip entry name, or null for anything
     * that could escape the target directory (zip-slip), for absolute
     * names, or for empty entries. Backslashes (zips made on Windows) are
     * folded to forward slashes.
     */
    fun safeZipPath(name: String): String? {
        val norm = name.replace('\\', '/')
        if (norm.isEmpty() || norm.endsWith('/')) return null  // the latter: a directory entry
        val parts = norm.split('/').filter { it.isNotEmpty() }  // drops leading-slash empties
        if (parts.any { it == ".." }) return null
        if (parts.isEmpty()) return null
        return parts.joinToString("/")
    }
}
