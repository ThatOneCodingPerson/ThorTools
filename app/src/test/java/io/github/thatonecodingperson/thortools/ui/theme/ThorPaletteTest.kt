package io.github.thatonecodingperson.thortools.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThorPaletteTest {

    @Test
    fun `palettes survive a text round trip`() {
        ThorThemes.builtIn.forEach { palette ->
            assertEquals(palette.copy(label = null), ThorPalette.decode(palette.encode()))
        }
    }

    @Test
    fun `broken palette text is rejected`() {
        assertNull(ThorPalette.decode(""))
        assertNull(ThorPalette.decode("id=x;name=y"))
        assertNull(ThorPalette.decode(ThorThemes.default.encode().replace("primary=FF7AA2F7", "primary=zz")))
    }

    @Test
    fun `stored ids resolve, unknown ids fall back, system means dynamic colours`() {
        assertEquals("ember", ThorThemes.resolve("ember")?.id)
        assertEquals(ThorThemes.default, ThorThemes.resolve("gone"))
        assertEquals(ThorThemes.default, ThorThemes.resolve(null))
        assertNull(ThorThemes.resolve(ThorThemes.SYSTEM))
        val custom = ThorThemes.default.copy(id = "mine", name = "Mine", label = null)
        assertEquals(custom, ThorThemes.resolve("mine", listOf(custom)))
    }

    @Test
    fun `every built-in theme keeps text readable`() {
        ThorThemes.builtIn.forEach { palette ->
            assertTrue("${palette.id} text on surface", ThorThemes.contrast(palette.onSurface, palette.surface) >= 4.5)
            assertTrue("${palette.id} text on primary", ThorThemes.contrast(palette.onPrimary, palette.primary) >= 4.5)
            assertTrue("${palette.id} text on an active tile", ThorThemes.contrast(palette.onSurface, palette.tileOn) >= 4.5)
            assertTrue("${palette.id} text on a tile", ThorThemes.contrast(palette.onSurface, palette.tileOff) >= 4.5)
        }
    }

    @Test
    fun `palettes saved before chart colours existed still load`() {
        val old = ThorThemes.default.encode().substringBefore(";chart=")
        assertEquals(ThorPalette.DARK_CHART, ThorPalette.decode(old)?.chart)
    }

    @Test
    fun `chart colours stand out from the panel surface`() {
        ThorThemes.builtIn.forEach { palette ->
            palette.chart.forEach { color ->
                assertTrue("${palette.id} chart colour", ThorThemes.contrast(color, palette.surface) >= 3.0)
            }
        }
    }

    @Test
    fun `ids are unique`() {
        assertEquals(ThorThemes.builtIn.size, ThorThemes.builtIn.map { it.id }.toSet().size)
    }
}
