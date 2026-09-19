package dev.recompkit_nfsmw.android.layout

/**
 * The layout file format (files/controls-layout.json):
 *
 *   { "version": 1,
 *     "buttons": {
 *       "accel":  { "x": 0.87, "y": 0.70, "size": 150, "visible": true },
 *       ...
 *     } }
 *
 * Decoding is tolerant on purpose: unknown keys are dropped, missing buttons
 * and fields fall back to the defaults, out-of-range values are clamped, and
 * an unreadable file yields the defaults (the launcher keeps the broken file
 * as controls-layout.json.bad for debugging).
 */
object LayoutCodec {

    fun encode(layout: ControlLayout): String {
        val btns = LinkedHashMap<String, JsonValue>()
        for ((id, b) in layout.buttons) {
            btns[id.key] = JsonLite.obj(
                "x" to JsonLite.num(b.x.toDouble()),
                "y" to JsonLite.num(b.y.toDouble()),
                "size" to JsonLite.num(b.sizeDp.toDouble()),
                "visible" to JsonLite.bool(b.visible)
            )
        }
        return JsonLite.encode(
            JsonLite.obj(
                "version" to JsonLite.num(layout.version.toDouble()),
                "buttons" to JsonLite.obj(*btns.entries.map { it.key to it.value }.toTypedArray())
            )
        )
    }

    fun decode(text: String, base: ControlLayout, widthPx: Int, heightPx: Int, density: Float): ControlLayout {
        val root = try {
            JsonLite.parse(text)
        } catch (t: JsonLiteException) {
            return base.clampAll(widthPx, heightPx, density)
        }
        val rootObj = root as? JsonValue.Obj ?: return base.clampAll(widthPx, heightPx, density)
        val version = (rootObj.entries["version"] as? JsonValue.Num)?.value?.toInt()
            ?: ControlLayout.LAYOUT_VERSION
        val btnRoot = (rootObj.entries["buttons"] as? JsonValue.Obj)?.entries

        val m = LinkedHashMap<ButtonId, ControlButton>()
        for (id in ButtonId.values()) {
            val d = base.button(id)
            var x = d.x
            var y = d.y
            var size = d.sizeDp
            var visible = d.visible
            val e = btnRoot?.get(id.key) as? JsonValue.Obj
            if (e != null) {
                (e.entries["x"] as? JsonValue.Num)?.value?.let { if (it in 0.0..1.0) x = it.toFloat() }
                (e.entries["y"] as? JsonValue.Num)?.value?.let { if (it in 0.0..1.0) y = it.toFloat() }
                (e.entries["size"] as? JsonValue.Num)?.value?.let { if (it > 0.0) size = it.toInt() }
                (e.entries["visible"] as? JsonValue.Bool)?.let { visible = it.value }
            }
            m[id] = ControlButton(id, x, y, size, visible)
        }
        return ControlLayout(version, m).clampAll(widthPx, heightPx, density)
    }
}
