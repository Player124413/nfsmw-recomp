package dev.recompkit_nfsmw.android.game

/**
 * The NFSCODE_* key codes from nfsmw_android.h (the ABI between the launcher
 * and the game library). Kept in one place so the overlay and the bridge can
 * never disagree.
 */
object GameCodes {
    const val NONE = 0
    const val UP = 1
    const val DOWN = 2
    const val LEFT = 3
    const val RIGHT = 4
    const val RETURN = 5
    const val ESCAPE = 6
    const val SPACE = 7
    const val KEY_M = 8
    const val KEY_C = 9
    const val KEY_R = 10
}

/**
 * The NFSMW_SYM_* bitmask values from nfsmw_android.h.
 */
object GameSymbols {
    const val INIT = 1 shl 0
    const val KEY = 1 shl 1
    const val SHUTDOWN = 1 shl 2
    const val PAUSE = 1 shl 3
    const val RESUME = 1 shl 4
    const val TOUCH = 1 shl 5
    const val REQUEST_SHUTDOWN = 1 shl 6
    const val SET_RENDER_SCALE = 1 shl 7
    const val SET_FRAME_LIMIT = 1 shl 8
    const val RESIZE = 1 shl 9
}
