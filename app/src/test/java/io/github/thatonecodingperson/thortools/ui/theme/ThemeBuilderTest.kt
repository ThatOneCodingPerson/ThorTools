package io.github.thatonecodingperson.thortools.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeBuilderTest {

    private val night = ThemeBasics(background = 0xFF101418, cards = 0xFF1A2027, text = 0xFFE8EDF2, accent = 0xFFFFB454, cornerDp = 20)
    private val paper = ThemeBasics(background = 0xFFFAF7F0, cards = 0xFFFFFFFF, text = 0xFF20201C, accent = 0xFF1F5FBF, cornerDp = 8)

    @Test
    fun `dark and light follow the background`() {
        assertTrue(ThemeBuilder.build("a", "A", night).isDark)
        assertFalse(ThemeBuilder.build("b", "B", paper).isDark)
        assertEquals(ThorPalette.DARK_CHART, ThemeBuilder.build("a", "A", night).chart)
        assertEquals(ThorPalette.LIGHT_CHART, ThemeBuilder.build("b", "B", paper).chart)
    }

    @Test
    fun `text stays readable on tiles and buttons`() {
        listOf(night, paper).forEach { basics ->
            val palette = ThemeBuilder.build("t", "T", basics)
            assertTrue(ThorThemes.contrast(palette.onSurface, palette.tileOn) >= 4.5)
            assertTrue(ThorThemes.contrast(palette.onSurface, palette.tileOff) >= 4.5)
            assertTrue(ThorThemes.contrast(palette.onPrimary, palette.primary) >= 4.5)
            assertEquals(emptyList<ThemeBuilder.Issue>(), ThemeBuilder.issues(palette))
        }
    }

    @Test
    fun `button text is black on a light accent and white on a dark one`() {
        assertEquals(0xFF000000, ThemeBuilder.build("y", "Y", night.copy(accent = 0xFFFFD166)).onPrimary)
        assertEquals(0xFFFFFFFF, ThemeBuilder.build("n", "N", night.copy(accent = 0xFF1A237E)).onPrimary)
    }

    @Test
    fun `hard to read choices are reported`() {
        val grey = ThemeBuilder.build("g", "G", night.copy(text = 0xFF2A2F35, accent = 0xFF1C2229))
        assertEquals(
            listOf(ThemeBuilder.Issue.TEXT_ON_BACKGROUND, ThemeBuilder.Issue.TEXT_ON_CARDS, ThemeBuilder.Issue.ACCENT_ON_CARDS),
            ThemeBuilder.issues(grey),
        )
    }

    @Test
    fun `a built palette gives back the colours it was made from and survives storage`() {
        val palette = ThemeBuilder.build("custom_1", "Mine; with\nbreaks", night)
        assertEquals(night, ThemeBuilder.basics(palette))
        val stored = ThorPalette.decode(palette.encode())!!
        assertEquals(palette.copy(name = "Mine  with breaks"), stored)
        assertEquals(ThemeBuilder.MAX_CORNER_DP, ThemeBuilder.build("c", "C", night.copy(cornerDp = 99)).cornerDp)
    }

    @Test
    fun `hex text is read and written`() {
        assertEquals("#7AA2F7", ThemeBuilder.toHex(0xFF7AA2F7))
        assertEquals(0xFF7AA2F7, ThemeBuilder.parseHex("#7aa2f7"))
        assertEquals(0xFF000000, ThemeBuilder.parseHex("000000"))
        assertNull(ThemeBuilder.parseHex("#12345"))
        assertNull(ThemeBuilder.parseHex("zzzzzz"))
    }

    @Test
    fun `hue, saturation and lightness round trip`() {
        (ThemeBuilder.SWATCHES + listOf(0xFF123456, 0xFFABCDEF, 0xFF808080)).forEach { color ->
            val (h, s, l) = ThemeBuilder.toHsl(color).toList()
            val back = ThemeBuilder.fromHsl(h, s, l)
            listOf(16, 8, 0).forEach { shift ->
                val difference = ((color shr shift) and 0xFF) - ((back shr shift) and 0xFF)
                assertTrue("${ThemeBuilder.toHex(color)} became ${ThemeBuilder.toHex(back)}", difference in -1..1)
            }
        }
    }

    @Test
    fun `mixing goes from one colour to the other`() {
        assertEquals(0xFF000000, ThemeBuilder.mix(0xFF000000, 0xFFFFFFFF, 0f))
        assertEquals(0xFFFFFFFF, ThemeBuilder.mix(0xFF000000, 0xFFFFFFFF, 1f))
        assertEquals(0xFF808080, ThemeBuilder.mix(0xFF000000, 0xFFFFFFFF, 0.5f))
    }

    @Test
    fun `each of the four colours is read and replaced on its own`() {
        val values = mapOf(
            ThemeColor.BACKGROUND to night.background,
            ThemeColor.CARDS to night.cards,
            ThemeColor.TEXT to night.text,
            ThemeColor.ACCENT to night.accent,
        )
        values.forEach { (color, value) -> assertEquals(value, ThemeBuilder.colorOf(night, color)) }
        ThemeColor.entries.forEach { color ->
            val changed = ThemeBuilder.withColor(night, color, 0xFF123456)
            assertEquals(0xFF123456, ThemeBuilder.colorOf(changed, color))
            (ThemeColor.entries - color).forEach { other -> assertEquals(values[other], ThemeBuilder.colorOf(changed, other)) }
            assertEquals(night.cornerDp, changed.cornerDp)
        }
    }

    @Test
    fun `each colour shows only the readability issues it takes part in`() {
        val grey = ThemeBuilder.build("g", "G", night.copy(text = 0xFF2A2F35, accent = 0xFF1C2229))
        assertEquals(listOf(ThemeBuilder.Issue.TEXT_ON_BACKGROUND), ThemeBuilder.issuesFor(grey, ThemeColor.BACKGROUND))
        assertEquals(
            listOf(ThemeBuilder.Issue.TEXT_ON_CARDS, ThemeBuilder.Issue.ACCENT_ON_CARDS),
            ThemeBuilder.issuesFor(grey, ThemeColor.CARDS),
        )
        assertEquals(
            listOf(ThemeBuilder.Issue.TEXT_ON_BACKGROUND, ThemeBuilder.Issue.TEXT_ON_CARDS),
            ThemeBuilder.issuesFor(grey, ThemeColor.TEXT),
        )
        assertEquals(listOf(ThemeBuilder.Issue.ACCENT_ON_CARDS), ThemeBuilder.issuesFor(grey, ThemeColor.ACCENT))
        val fine = ThemeBuilder.build("f", "F", night)
        ThemeColor.entries.forEach { assertEquals(emptyList<ThemeBuilder.Issue>(), ThemeBuilder.issuesFor(fine, it)) }
    }

    @Test
    fun `the contrast shown for each colour is the one that matters for it`() {
        val palette = ThemeBuilder.build("k", "K", night)
        assertEquals(ThorThemes.contrast(night.text, night.background), ThemeBuilder.keyContrast(palette, ThemeColor.BACKGROUND), 0.0)
        assertEquals(ThorThemes.contrast(night.text, night.cards), ThemeBuilder.keyContrast(palette, ThemeColor.CARDS), 0.0)
        val worst = minOf(ThorThemes.contrast(night.text, night.background), ThorThemes.contrast(night.text, night.cards))
        assertEquals(worst, ThemeBuilder.keyContrast(palette, ThemeColor.TEXT), 0.0)
        assertEquals(ThorThemes.contrast(night.accent, night.cards), ThemeBuilder.keyContrast(palette, ThemeColor.ACCENT), 0.0)
    }

    @Test
    fun `a picked colour keeps its hue while it has no saturation`() {
        val red = HslColor(hue = 0f, saturation = 1f, lightness = 0.5f)
        assertEquals(0xFFFF0000, red.color)
        val grey = red.copy(saturation = 0f)
        assertEquals(0xFF808080, grey.color)
        assertEquals(0f, grey.hue, 0f)
        assertEquals(0xFF00FF00, grey.copy(hue = 120f, saturation = 1f).color)
    }

    @Test
    fun `a copy of any built-in theme starts from its own colours`() {
        ThorThemes.builtIn.forEach { palette ->
            val copy = ThemeBuilder.build("custom_copy", "Copy", ThemeBuilder.basics(palette))
            assertEquals(ThemeBuilder.basics(palette), ThemeBuilder.basics(copy))
            assertEquals(palette.isDark, copy.isDark)
        }
    }
}
