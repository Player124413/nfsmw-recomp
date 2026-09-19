package dev.recompkit_nfsmw.android

import android.content.Context
import dev.recompkit_nfsmw.android.layout.JsonLite
import dev.recompkit_nfsmw.android.layout.JsonValue
import java.io.File

/**
 * Launcher settings, written to files/nfsmw-settings.json. The game reads
 * the file through the path the launcher passes in nfsmw_android_env.
 */
class GameSettings(
    var renderScale: Float = 1.0f,   // 0.5 .. 2.0
    var frameLimit: Int = 0,         // 0 = unlimited
    var audio: Boolean = true,
    var haptics: Boolean = true
)

object SettingsStore {

    private const val FILE_NAME = "nfsmw-settings.json"
    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 2.0f
    val FRAME_LIMITS = intArrayOf(0, 30, 60, 90, 120)

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun load(context: Context): GameSettings {
        val f = file(context)
        if (!f.exists()) return GameSettings()
        return try {
            val o = JsonLite.parse(f.readText()) as? JsonValue.Obj
            val s = GameSettings()
            if (o != null) {
                (o.entries["render_scale"] as? JsonValue.Num)?.value?.let {
                    s.renderScale = it.toFloat().coerceIn(MIN_SCALE, MAX_SCALE)
                }
                (o.entries["frame_limit"] as? JsonValue.Num)?.value?.toInt()?.let {
                    if (FRAME_LIMITS.contains(it)) s.frameLimit = it
                }
                (o.entries["audio"] as? JsonValue.Bool)?.let { s.audio = it.value }
                (o.entries["haptics"] as? JsonValue.Bool)?.let { s.haptics = it.value }
            }
            s
        } catch (t: Throwable) {
            GameSettings()
        }
    }

    fun save(context: Context, s: GameSettings) {
        val f = file(context)
        val tmp = File(context.filesDir, FILE_NAME + ".tmp")
        tmp.writeText(
            JsonLite.encode(
                JsonLite.obj(
                    "render_scale" to JsonLite.num(s.renderScale.toDouble()),
                    "frame_limit" to JsonLite.num(s.frameLimit.toDouble()),
                    "audio" to JsonLite.bool(s.audio),
                    "haptics" to JsonLite.bool(s.haptics)
                )
            )
        )
        if (!tmp.renameTo(f)) {
            tmp.copyTo(f, overwrite = true)
            tmp.delete()
        }
    }
}
