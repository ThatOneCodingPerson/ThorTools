package io.github.thatonecodingperson.thortools.oled

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicAreasTest {
    private fun grey(level: Int): Int = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    /** A 64 x 32 screen whose background changes with [frame], with a bright still badge at x 8..23, y 8..15. */
    private fun frame(frame: Int, badge: Boolean = true): Sample {
        val pixels = IntArray(64 * 32) { i ->
            val x = i % 64
            val y = i / 64
            if (badge && x in 8..23 && y in 8..15) grey(230) else grey((x * 3 + y * 5 + frame * 40) % 200)
        }
        return Sample(64, 32, pixels)
    }

    @Test
    fun `a bright part that stays put while the rest moves is an area, once still long enough`() {
        val areas = ClassicAreas(block = 8)
        for (step in 0..5) areas.update(frame(step), now = step * 2_000L)
        assertEquals(emptyList<Box>(), areas.areas(now = 10_000, stillMs = 60_000))
        assertEquals(listOf(Box(8, 8, 24, 16)), areas.areas(now = 10_000, stillMs = 8_000))
    }

    @Test
    fun `a still screen is no area, and a badge that changes stops being one`() {
        val still = ClassicAreas(block = 8)
        repeat(4) { still.update(frame(0), now = it * 2_000L) }
        assertEquals("most of the screen still: the shifter and refresher's job", emptyList<Box>(), still.areas(6_000, 2_000))

        val areas = ClassicAreas(block = 8)
        for (step in 0..5) areas.update(frame(step), now = step * 2_000L)
        val badge = areas.stillPixels(Box(8, 8, 24, 16), now = 10_000, stillMs = 8_000)
        assertEquals(16 * 8, badge.size)
        assertFalse(areas.anyChanged(badge, after = 10_000))
        areas.update(frame(6, badge = false), now = 12_000)
        assertTrue(areas.anyChanged(badge, after = 10_000))
        assertEquals(emptyList<Box>(), areas.areas(now = 12_000, stillMs = 8_000))
    }
}
