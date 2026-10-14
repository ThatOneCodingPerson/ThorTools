package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShaderGuessTest {
    private val links = "From links/someone-shaders"

    @Test
    fun `every shader of the catalogue is guessed where the catalogue sorts it`() {
        val wrong = ShaderCatalog.entries.mapNotNull { entry ->
            val expected = entry.kind?.let { listOf(ShaderShortcuts.kindFolder(it)) } ?: entry.systems.map(ShaderShortcuts::consoleFolder)
            val guessed = ShaderGuess.place(entry.slang, linkFolder = links)
            if (guessed.toSet() == expected.toSet()) null else "${entry.slang}: $guessed, not $expected"
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun `short console words only count as whole words, phrases first`() {
        assertEquals(listOf("Any console/CRT TVs"), ShaderGuess.place("crt/rgb-crt.slangp", linkFolder = links))
        assertEquals(listOf("Game Boy Color"), ShaderGuess.place("shaders/game-boy-color-lcd.slangp", linkFolder = links))
        assertEquals(listOf("Game Boy"), ShaderGuess.place("GameBoy/DotMatrix.slangp", linkFolder = links))
        assertEquals(listOf("Nintendo 64"), ShaderGuess.place("presets/n64-smooth.slangp", linkFolder = links))
        assertEquals(listOf(links), ShaderGuess.place("misc/my-thing.slangp", linkFolder = links))
        assertEquals(
            listOf("Game Boy Advance"),
            ShaderGuess.place("misc/look.slangp", pulled = listOf("handheld/shaders/agb001.slang", "gba/palette.png"), linkFolder = links),
        )
    }

    @Test
    fun `presets as people save them, consoles spelled out and CRT looks by their signal`() {
        fun console(system: CheatSystem) = listOf(ShaderShortcuts.consoleFolder(system))
        assertEquals(console(CheatSystem.GBA), ShaderGuess.place("lcd-gameboy-advanced.slangp", linkFolder = links))
        assertEquals(console(CheatSystem.GBC), ShaderGuess.place("mega-bezel-gameboy-color-10-9.slangp", linkFolder = links))
        assertEquals(console(CheatSystem.NDS), ShaderGuess.place("lcd-nintendo-ds-lite.slangp", linkFolder = links))
        assertEquals(console(CheatSystem.GB), ShaderGuess.place("mega-bezel-gameboy-10-9.slangp", linkFolder = links))
        assertEquals(listOf("Any console/CRT TVs"), ShaderGuess.place("mega-bezel-ntsc-composite-4-3.slangp", linkFolder = links))
        assertEquals(listOf("Any console/CRT TVs"), ShaderGuess.place("hdr-ntsc-svideo.slangp", linkFolder = links))
    }

    @Test
    fun `plain names from file names`() {
        assertEquals("Game Boy pocket high contrast", ShaderGuess.name("handheld/gameboy-pocket-high-contrast.slangp"))
        assertEquals("CRT easymode halation", ShaderGuess.name("crt/crt-easymode-halation.slangp"))
        assertEquals("xBRZ freescale", ShaderGuess.name("xbrz-freescale.slangp"))
        assertEquals("AGB-001", ShaderGuess.name("handheld/agb001.slangp"))
        assertEquals("OSSC slot", ShaderGuess.name("scanlines/ossc_slot.slangp"))
        assertEquals("CRT lite NTSC composite 4x3", ShaderGuess.name("crt-lite-ntsc-composite-4-3.slangp"))
        assertEquals("Mega Bezel Nintendo DS hybrid top 4x3", ShaderGuess.name("mega-bezel-nintendo-ds-hybrid-top-4-3.slangp"))
        assertEquals("Mega Bezel Game Boy 10x9", ShaderGuess.name("mega-bezel-gameboy-10-9.slangp"))
        assertEquals("HDR NTSC S-Video", ShaderGuess.name("hdr-ntsc-svideo.slangp"))
        assertEquals("CRT royale 2", ShaderGuess.name("crt-royale-2.slangp"))
        assertEquals(listOf("rgb", "crt"), ShaderGuess.words("rgb-crt"))
        assertEquals(listOf("dot", "matrix"), ShaderGuess.words("DotMatrix"))
    }
}
