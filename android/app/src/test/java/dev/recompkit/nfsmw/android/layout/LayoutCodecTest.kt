package dev.recompkit_nfsmw.android.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutCodecTest {

    private val screenW = 1280
    private val screenH = 720
    private val density = 2.0f

    @Test
    fun encodeDecodeRoundTrip() {
        val base = ControlLayout.defaults()
        val modified = base.copy()
        modified.button(ButtonId.ACCEL).x = 0.5f
        modified.button(ButtonId.ACCEL).y = 0.5f
        modified.button(ButtonId.MENU).sizeDp = 100
        modified.button(ButtonId.MAP).visible = false

        val back = LayoutCodec.decode(JsonLite.encode(modified), base, screenW, screenH, density)
        assertEquals(0.5f, back.button(ButtonId.ACCEL).x, 0.001f)
        assertEquals(0.5f, back.button(ButtonId.ACCEL).y, 0.001f)
        assertEquals(100, back.button(ButtonId.MENU).sizeDp)
        assertFalse(back.button(ButtonId.MAP).visible)
        // Untouched buttons keep their values.
        assertEquals(base.button(ButtonId.HANDBRAKE).x, back.button(ButtonId.HANDBRAKE).x, 0.0001f)
    }

    @Test
    fun unreadableFileFallsBackToDefaults() {
        val base = ControlLayout.defaults()
        val back = LayoutCodec.decode("{ this is not json", base, screenW, screenH, density)
        assertEquals(base.button(ButtonId.ACCEL).x, back.button(ButtonId.ACCEL).x, 0.0001f)
        assertEquals(base.button(ButtonId.BRAKE).visible, back.button(ButtonId.BRAKE).visible)
    }

    @Test
    fun missingButtonsFallBackToDefaults() {
        val text = """{"version":1,"buttons":{"accel":{"x":0.5,"y":0.5,"size":90,"visible":true}}}"""
        val back = LayoutCodec.decode(text, ControlLayout.defaults(), screenW, screenH, density)
        assertEquals(8, back.buttons.size)
        assertEquals(0.5f, back.button(ButtonId.ACCEL).x, 0.001f)
        val d = ControlLayout.defaults()
        assertEquals(d.button(ButtonId.MENU).sizeDp, back.button(ButtonId.MENU).sizeDp)
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        val text = """{"version":1,"buttons":{
            "accel":{"x":1.5,"y":-0.2,"size":5000,"visible":true},
            "menu":{"x":0.5,"y":0.5,"size":-5,"visible":true}
        }}"""
        val back = LayoutCodec.decode(text, ControlLayout.defaults(), screenW, screenH, density)
        val a = back.button(ButtonId.ACCEL)
        assertTrue(a.x in 0f..1f)
        assertTrue(a.y in 0f..1f)
        assertEquals(ControlLayout.MAX_SIZE_DP, a.sizeDp)
        // size <= 0 is invalid: the default size comes back, clamped.
        val m = back.button(ButtonId.MENU)
        assertTrue(m.sizeDp in ControlLayout.MIN_SIZE_DP..ControlLayout.MAX_SIZE_DP)
    }

    @Test
    fun unknownButtonsAreDropped() {
        val text = """{"version":1,"buttons":{
            "accel":{"x":0.5,"y":0.5,"size":90,"visible":true},
            "teleport":{"x":0.1,"y":0.1,"size":90,"visible":true}
        }}"""
        val back = LayoutCodec.decode(text, ControlLayout.defaults(), screenW, screenH, density)
        assertEquals(8, back.buttons.size)
        assertEquals(null, ButtonId.fromKey("teleport"))
    }

    @Test
    fun versionIsKept() {
        val text = """{"version":7,"buttons":{}}"""
        val back = LayoutCodec.decode(text, ControlLayout.defaults(), screenW, screenH, density)
        assertEquals(7, back.version)
    }

    @Test
    fun encodeIsStable() {
        val l = ControlLayout.defaults()
        assertEquals(LayoutCodec.encode(l), LayoutCodec.encode(l.copy()))
    }
}
