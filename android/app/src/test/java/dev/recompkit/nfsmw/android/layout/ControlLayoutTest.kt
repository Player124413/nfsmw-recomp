package dev.recompkit_nfsmw.android.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlLayoutTest {

    @Test
    fun defaultsFitAStandardLandscape() {
        val d = ControlLayout.defaults().clampAll(1280, 720, 2f)
        for (b in d.buttons.values) {
            val rPx = b.sizeDp * 2f / 2f
            val cx = b.x * 1280f
            val cy = b.y * 720f
            assertTrue("left ${b.id}", cx >= rPx - 1f)
            assertTrue("right ${b.id}", cx + rPx <= 1280f + 1f)
            assertTrue("top ${b.id}", cy >= rPx - 1f)
            assertTrue("bottom ${b.id}", cy + rPx <= 720f + 1f)
            assertTrue(b.sizeDp in ControlLayout.MIN_SIZE_DP..ControlLayout.MAX_SIZE_DP)
        }
    }

    @Test
    fun clampSurvivesATinyScreen() {
        // A small phone in portrait: buttons bigger than the screen must
        // land in the middle, not crash the clamp.
        val d = ControlLayout.defaults().clampAll(500, 300, 1f)
        for (b in d.buttons.values) {
            assertTrue(b.x in 0f..1f)
            assertTrue(b.y in 0f..1f)
        }
    }

    @Test
    fun buttonBiggerThanTheScreenLandsInTheMiddle() {
        // 150 dp (the default accel size) at density 3.0 is 450 px wide,
        // bigger than the 300 px screen: the clamp must park the button in
        // the middle instead of at an unreachable edge.
        val d = ControlLayout.defaults().clampAll(300, 300, 3f)
        assertEquals(0.5f, d.button(ButtonId.ACCEL).x, 0.001f)
        assertEquals(0.5f, d.button(ButtonId.ACCEL).y, 0.001f)
        for (b in d.buttons.values) {
            assertTrue(b.x in 0f..1f)
            assertTrue(b.y in 0f..1f)
        }
    }

    @Test
    fun sizeIsClampedToTheAllowedRange() {
        val l = ControlLayout.defaults().copy()
        l.button(ButtonId.BRAKE).sizeDp = 10
        val d = l.clampAll(1280, 720, 2f)
        assertEquals(ControlLayout.MIN_SIZE_DP, d.button(ButtonId.BRAKE).sizeDp)
    }

    @Test
    fun copyIsIndependent() {
        val d = ControlLayout.defaults()
        val c = d.copy()
        c.button(ButtonId.ACCEL).x = 0.99f
        assertEquals(0.87f, d.button(ButtonId.ACCEL).x, 0.001f)
    }

    @Test
    fun fromKeyRoundTrips() {
        for (id in ButtonId.values()) {
            assertEquals(id, ButtonId.fromKey(id.key))
        }
        assertEquals(null, ButtonId.fromKey("nope"))
    }
}
