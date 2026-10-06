package io.github.thatonecodingperson.thortools.oled

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class StillMapTest {
    private fun grey(level: Int): Int = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    private fun sample(width: Int = 40, height: Int = 20, paint: (x: Int, y: Int) -> Int): Sample =
        Sample(width, height, IntArray(width * height) { i -> paint(i % width, i / width) })

    @Test
    fun `the same picture is still, a changed one isn't, a tiny change doesn't count`() {
        val map = StillMap()
        val picture = sample { x, _ -> grey(if (x < 20) 200 else 20) }
        assertFalse("the first copy has nothing to compare with", map.update(picture, now = 0))
        assertTrue(map.update(picture, now = 2_000))
        assertFalse(map.update(sample { x, _ -> grey(if (x < 20) 20 else 200) }, now = 4_000))
        // One cell of 50 changing is a ticking clock, not a moving picture.
        val clock = sample { x, y -> if (x < 4 && y < 4) grey(255) else grey(if (x < 20) 20 else 200) }
        assertTrue(map.update(clock, now = 6_000))
        // Noise smaller than a cell's threshold is no change either.
        assertTrue(map.update(sample { x, _ -> grey(if (x < 20) 21 else 199) }, now = 8_000))
    }

    @Test
    fun `each cell remembers since when it stays the same, and its brightness`() {
        val map = StillMap(cell = 10)
        map.update(sample { _, _ -> grey(100) }, now = 0)
        map.update(sample { x, _ -> grey(if (x < 10) 250 else 100) }, now = 5_000)
        assertEquals(4, map.columns)
        assertEquals(2, map.rows)
        assertEquals(5_000L, map.stillSince(0))
        assertEquals(0L, map.stillSince(1))
        assertEquals(250, map.brightness(0))
        assertEquals(100, map.brightness(1))
    }

    @Test
    fun `brightness follows what the eye sees`() {
        assertEquals(255, StillMap.luma(grey(255)))
        assertEquals(0, StillMap.luma(grey(0)))
        assertTrue(StillMap.luma(0xFF00FF00.toInt()) > StillMap.luma(0xFF0000FF.toInt()))
    }

    @Test
    fun `the colour wash changes once per step and comes round again`() {
        assertEquals(0xFF0000, RefreshPattern.colourAt(0))
        assertEquals(0xFF0000, RefreshPattern.colourAt(RefreshPattern.COLOUR_MS - 1))
        assertEquals(0x00FF00, RefreshPattern.colourAt(RefreshPattern.COLOUR_MS))
        assertEquals(0xFF0000, RefreshPattern.colourAt(RefreshPattern.COLOUR_MS * RefreshPattern.COLOURS_RGB.size))
        assertTrue("no faster than about once a second", RefreshPattern.COLOUR_MS >= 1_000)
    }

    @Test
    fun `the helper's copy turns into a negative, and a broken copy into nothing`() {
        val rgb = byteArrayOf(255.toByte(), 0, 10, 0, 0, 0)
        val inverse = RefreshPattern.negative("2 1 ${Base64.getEncoder().encodeToString(rgb)}")!!
        assertEquals(2, inverse.width)
        assertEquals(0xFF00FFF5.toInt(), inverse.pixels[0])
        assertEquals(0xFFFFFFFF.toInt(), inverse.pixels[1])
        assertNull(RefreshPattern.negative("3 1 ${Base64.getEncoder().encodeToString(rgb)}"))
        assertNull(RefreshPattern.negative("garbage"))
    }
}
