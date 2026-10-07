package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChtFileTest {
    /** The start of libretro-database's `Castlevania (USA).cht`, cut to three cheats. */
    private val database = """
        cheats = 3

        cheat0_desc = "Level Modifier"
        cheat0_code = "89389C90 0000+89389C92 0000"
        cheat0_enable = false

        cheat1_desc = "Starting Point Modifier"
        cheat1_code = "813509D0 0000+813509D4 0000+813509D8 0000"
        cheat1_enable = false

        cheat2_desc = "Permanent Upgraded Weapon"
        cheat2_code = "81389C42 0000+81389C42 0003"
        cheat2_enable = false

    """.trimIndent()

    private fun of(text: String) = ChtFile.of(text.toByteArray(Charsets.UTF_8))

    private fun text(file: ChtFile) = String(file.bytes, Charsets.UTF_8)

    @Test
    fun `the cheats are read with what they do and whether they are on`() {
        val cheats = of(database).cheats
        assertEquals(3, cheats.size)
        assertEquals("Starting Point Modifier", cheats[1].description)
        assertEquals("813509D0 0000+813509D4 0000+813509D8 0000", cheats[1].code)
        assertTrue(cheats.none { it.enabled })
    }

    @Test
    fun `switching cheats on changes only the enable lines`() {
        val changed = of(database).withEnabled(setOf(1))
        val before = database.lines()
        val after = text(changed).lines()
        assertEquals(before.size, after.size)
        before.indices.forEach { i ->
            when {
                before[i].startsWith("cheat1_enable") -> assertEquals("cheat1_enable = true", after[i])
                else -> assertEquals(before[i], after[i])
            }
        }
        assertEquals(listOf(false, true, false), changed.cheats.map { it.enabled })
        assertEquals(1, changed.enabledCount)
        assertEquals(listOf(false, false, false), changed.withEnabled(emptySet()).cheats.map { it.enabled })
    }

    @Test
    fun `a cheat without an enable line gets one after its last line when switched on`() {
        val file = of("cheats = 2\ncheat0_desc = \"A\"\ncheat0_code = \"1\"\ncheat1_desc = \"B\"\ncheat1_code = \"2\"\n")
        val changed = text(file.withEnabled(setOf(0)))
        val expected = "cheats = 2\ncheat0_desc = \"A\"\ncheat0_code = \"1\"\ncheat0_enable = true\n" +
            "cheat1_desc = \"B\"\ncheat1_code = \"2\"\n"
        assertEquals(expected, changed)
    }

    @Test
    fun `RetroArch's own keys, quoted values and Windows line ends stay`() {
        val saved = "cheats = \"1\"\r\ncheat0_desc = \"Infinite Lives\"\r\ncheat0_code = \"00FF\"\r\ncheat0_enable = \"true\"\r\n" +
            "cheat0_handler = \"1\"\r\ncheat0_address = \"1234\"\r\n"
        val file = of(saved)
        assertTrue(file.cheats.single().enabled)
        val off = text(file.withEnabled(emptySet()))
        assertEquals(saved.replace("cheat0_enable = \"true\"", "cheat0_enable = false"), off)
    }

    @Test
    fun `the bytes come back exactly, whatever the encoding`() {
        val latin = "cheats = 1\ncheat0_desc = \"Vies infinies é\"\ncheat0_enable = false\n".toByteArray(Charsets.ISO_8859_1)
        val file = ChtFile.of(latin)
        assertArrayEquals(latin, file.bytes)
        assertEquals("Vies infinies é", file.cheats.single().description)
        assertEquals("Pokémon", of("cheats = 1\ncheat0_desc = \"Pokémon\"\n").cheats.single().description)
    }

    @Test
    fun `a file without a count line has as many cheats as its numbers say`() {
        val file = of("cheat0_desc = \"A\"\ncheat2_desc = \"C\"\n")
        assertEquals(3, file.cheats.size)
        assertEquals("", file.cheats[1].description)
        assertFalse(file.cheats[2].enabled)
    }
}
