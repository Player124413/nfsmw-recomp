package dev.recompkit_nfsmw.android.layout

import android.content.Context

/** Loads and saves the control layout (files/controls-layout.json). */
object ControlStore {

    private const val FILE_NAME = "controls-layout.json"

    fun file(context: Context) = java.io.File(context.filesDir, FILE_NAME)

    fun load(context: Context, widthPx: Int, heightPx: Int, density: Float): ControlLayout {
        val f = file(context)
        val defaults = ControlLayout.defaults().clampAll(widthPx, heightPx, density)
        if (!f.exists()) return defaults
        return try {
            LayoutCodec.decode(f.readText(), ControlLayout.defaults(), widthPx, heightPx, density)
        } catch (t: Throwable) {
            // Keep the broken file next to it so it can be inspected, and
            // start from defaults rather than crash the launcher.
            runCatching { f.copyTo(java.io.File(context.filesDir, FILE_NAME + ".bad"), overwrite = true) }
            defaults
        }
    }

    fun save(context: Context, layout: ControlLayout) {
        val target = file(context)
        val tmp = java.io.File(context.filesDir, FILE_NAME + ".tmp")
        tmp.writeText(LayoutCodec.encode(layout))
        // renameTo on the same filesystem is atomic; fall back to a copy.
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}
