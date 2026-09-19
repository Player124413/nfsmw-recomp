package dev.recompkit_nfsmw.android.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameFilesFilterTest {

    @Test
    fun gameDataIsKept() {
        for (p in listOf(
            "CARS/00000000.tpk",
            "SOUND/Music/001.mp3",
            "SOUND/Voice/actor_01.mus",
            "GLOBAL/data.bun",
            "FRONTEND/ui.dat",
            "TRACKS/track_01.dat",
            "MOVIES/intro.bik",      // VP6 cinematics stay: the game decodes them
            "MOVIES/end.vp6",
            "fonts/font.tpk",
            "data/file without extension"
        )) {
            assertTrue(p, GameFilesFilter.include(p))
        }
    }

    @Test
    fun bundleExcludeListIsApplied() {
        for (p in listOf(
            "Uninstall/nfsw_setup.exe",
            "Support/manual.pdf",
            "scripts/widescreen_fix.dll",
            "SAVE/slot_1.dat",
            "foobar/whatever.bin",
            "server.cfg",
            "ReadMe.txt",
            "EULA.txt"
        )) {
            assertFalse(p, GameFilesFilter.include(p))
        }
    }

    @Test
    fun excludedNamesAreCaseInsensitive() {
        assertFalse(GameFilesFilter.include("save/slot.bin"))
        assertFalse(GameFilesFilter.include("Save/slot.bin"))
        assertFalse(GameFilesFilter.include("SAVE/deep/nest/slot.bin"))
        assertFalse(GameFilesFilter.include("GLOBAL/config.TXT"))
        assertFalse(GameFilesFilter.include("CARS/Code.CXX"))
    }

    @Test
    fun windowsPlatformJunkIsDropped() {
        for (p in listOf(
            "speed.exe",
            "NFSMW.exe",
            "code.cxx",
            "common.cxx",
            "crash_reporter.cxx",
            "widescreen_fix.dll",
            "loader.dll",
            "setup.msi",
            "patch.cab",
            "game.lnk",
            "Thumbs.db",
            ".DS_Store",
            "desktop.ini",
            "CARS/\$RECYCLE.BIN/old.dat",
            "System Volume Information/swapfile.dat",
            "GLOBAL/session.log",
            "GLOBAL/debug.tmp",
            "data/backup.zip",
            "data/archive.7z",
            "data/disk.iso"
        )) {
            assertFalse(p, GameFilesFilter.include(p))
        }
    }

    @Test
    fun dataExtensionsAreNotConfusedWithJunk() {
        assertTrue(GameFilesFilter.include("GLOBAL/config.ini"))
        assertTrue(GameFilesFilter.include("GLOBAL/notes.bin"))
        assertTrue(GameFilesFilter.include("data/archive.tar"))
        assertTrue(GameFilesFilter.include("data/disk.imgx"))
    }

    @Test
    fun isExcludedDirChecksSingleNames() {
        assertTrue(GameFilesFilter.isExcludedDir("SAVE"))
        assertTrue(GameFilesFilter.isExcludedDir("save"))
        assertFalse(GameFilesFilter.isExcludedDir("CARS"))
        assertFalse(GameFilesFilter.isExcludedDir("savegame"))
    }

    @Test
    fun singleWrapperDirectoryIsUnwrapped() {
        val wrapped = listOf(
            "Need For Speed Most Wanted/CARS/a.tpk",
            "Need For Speed Most Wanted/SOUND/b.mp3",
            "Need For Speed Most Wanted/GLOBAL/c.bun"
        )
        val n = GameFilesFilter.normalizeTopDir(wrapped)
        assertEquals(
            listOf("CARS/a.tpk", "SOUND/b.mp3", "GLOBAL/c.bun"),
            n.paths
        )
        assertEquals("Need For Speed Most Wanted/", n.strippedPrefix)
    }

    @Test
    fun looseContentIsLeftAlone() {
        val loose = listOf("CARS/a.tpk", "SOUND/b.mp3", "GLOBAL/c.bun")
        val n = GameFilesFilter.normalizeTopDir(loose)
        assertEquals(loose, n.paths)
        assertEquals("", n.strippedPrefix)
    }

    @Test
    fun singleContentDirectoryIsNotUnwrapped() {
        // The user picked a folder that directly contains one game folder:
        // unwrapping it would put the files at the wrong level.
        val single = listOf("GLOBAL/a.bun", "GLOBAL/b.bun")
        val n = GameFilesFilter.normalizeTopDir(single)
        assertEquals(single, n.paths)
        assertEquals("", n.strippedPrefix)
    }

    @Test
    fun emptyListSurvivesNormalization() {
        val n = GameFilesFilter.normalizeTopDir(emptyList())
        assertTrue(n.paths.isEmpty())
        assertEquals("", n.strippedPrefix)
    }

    @Test
    fun zipPathsAreSanitized() {
        assertEquals("CARS/a.tpk", GameFilesFilter.safeZipPath("CARS/a.tpk"))
        assertEquals("CARS/a.tpk", GameFilesFilter.safeZipPath("CARS//a.tpk"))
        assertEquals("WIN/PATH/x.dat", GameFilesFilter.safeZipPath("WIN\\PATH\\x.dat"))
        assertEquals("a.dat", GameFilesFilter.safeZipPath("/a.dat"))
        assertNull(GameFilesFilter.safeZipPath(".."))
        assertNull(GameFilesFilter.safeZipPath("a/../../x"))
        assertNull(GameFilesFilter.safeZipPath("a/.."))
        assertNull(GameFilesFilter.safeZipPath("dir/"))
        assertNull(GameFilesFilter.safeZipPath(""))
    }
}
