package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatMatchTest {
    private val retroArch = "1.22.2_GIT"

    @Test
    fun `extensions and stems, a game extension inside an archive's name too`() {
        assertEquals(listOf("n64", "zip"), RomNames.extensions("007 - GoldenEye (USA).n64.zip"))
        assertEquals(listOf("nes"), RomNames.extensions("Super Mario Bros. 3 (USA).nes"))
        assertEquals(listOf("nes"), RomNames.extensions("Dr. Mario (Japan, USA).nes"))
        assertEquals("007 - GoldenEye (USA)", RomNames.stem("007 - GoldenEye (USA).n64.zip"))
        assertEquals("Chrono Trigger (USA)", RomNames.stem("Chrono Trigger (USA).zip"))
        assertEquals("Super Mario Bros. 3 (USA)", RomNames.stem("Super Mario Bros. 3 (USA).nes"))
        assertEquals("Castlevania (U) (V1.1)", RomNames.stem("Castlevania (U) (V1.1).cht"))
    }

    @Test
    fun `saves and states are named after the file without its last extension, as on the Thor`() {
        // states/Mupen64Plus-Next/Castlevania (USA) (Rev B).n64.state on the owner's Thor.
        assertEquals("Castlevania (USA) (Rev B).n64", RomNames.base("Castlevania (USA) (Rev B).n64.zip"))
    }

    @Test
    fun `RetroArch from 1_20 on cuts the cheat file's name at a dot once more`() {
        assertEquals("Castlevania (USA) (Rev B).cht", RomNames.cheatFile("Castlevania (USA) (Rev B).n64.zip", retroArch))
        assertEquals("007 - GoldenEye (USA).cht", RomNames.cheatFile("007 - GoldenEye (USA).n64.zip", retroArch))
        assertEquals("Chrono Trigger (USA).cht", RomNames.cheatFile("Chrono Trigger (USA).zip", retroArch))
        assertEquals("Super Mario Bros.cht", RomNames.cheatFile("Super Mario Bros. 3 (USA).nes", retroArch))
        assertEquals("Castlevania (USA) (Rev B).cht", RomNames.cheatFile("Castlevania (USA) (Rev B).n64.zip", "1.20.0"))
        assertEquals("Castlevania (USA) (Rev B).cht", RomNames.cheatFile("Castlevania (USA) (Rev B).n64.zip", null))
    }

    @Test
    fun `RetroArch before 1_20 added cht to the base name`() {
        assertEquals("Castlevania (USA) (Rev B).n64.cht", RomNames.cheatFile("Castlevania (USA) (Rev B).n64.zip", "1.19.1"))
        assertEquals("Super Mario Bros. 3 (USA).cht", RomNames.cheatFile("Super Mario Bros. 3 (USA).nes", "1.9.0"))
    }

    @Test
    fun `titles lose tags, case, accents, punctuation, articles and discs`() {
        val castlevania = GameTitle.of("Castlevania (USA) (Rev B)")
        assertEquals("castlevania", castlevania.title)
        assertEquals(setOf(CheatRegion.USA), castlevania.regions)
        assertEquals(2, castlevania.revision)
        val good = GameTitle.of("Castlevania (U) (V1.1)")
        assertEquals(setOf(CheatRegion.USA), good.regions)
        assertEquals(1, good.revision)
        assertEquals(
            GameTitle.of("The Legend of Zelda - A Link to the Past (USA)").title,
            GameTitle.of("Legend of Zelda, The - A Link to the Past (Europe)").title,
        )
        assertEquals("pokemon gold version", GameTitle.of("Pokémon - Gold Version (USA, Europe) (SGB Enhanced)").title)
        assertEquals("final fantasy vii", GameTitle.of("Final Fantasy VII Disc 1").title)
        assertEquals("kirbys adventure", GameTitle.of("Kirby's Adventure (USA)").title)
        assertEquals(setOf(CheatRegion.USA, CheatRegion.EUROPE), GameTitle.of("Boxxle (USA, Europe) (Rev 1)").regions)
        assertEquals(setOf(CheatRegion.JAPAN, CheatRegion.USA), GameTitle.of("Tetris (JU) [!]").regions)
    }

    @Test
    fun `language tags and dump flags aren't regions, square-bracket regions are`() {
        assertEquals(setOf(CheatRegion.EUROPE), GameTitle.of("Castlevania (Europe) (En,Fr,De)").regions)
        assertTrue(GameTitle.of("Game [a1] [b2]").regions.isEmpty())
        assertEquals(setOf(CheatRegion.JAPAN), GameTitle.of("Lucky Star [JP] [ULJM-05541]").regions)
    }

    @Test
    fun `a leading release number can be left out, and word order doesn't matter`() {
        val scene = GameTitle.of("4273 - Pokemon Mystery Dungeon - Explorers of Sky (US)(XenoPhobia)")
        assertEquals("pokemon mystery dungeon explorers of sky", scene.bare)
        assertEquals(setOf(CheatRegion.USA), scene.regions)
        assertEquals(GameTitle.of("GoldenEye 007 (USA)").words, GameTitle.of("007 - GoldenEye (USA)").words)
    }

    /** Names from libretro-database's N64 folder. */
    private val n64 = CheatIndex(
        CheatSystem.N64,
        listOf(
            "CASTLEVANIA.cht", "Castlevania (Europe) (En,Fr,De).cht", "Castlevania (U) (V1.0).cht", "Castlevania (U) (V1.1).cht",
            "Castlevania (USA).cht", "Castlevania - Legacy of Darkness (USA).cht", "GOLDENEYE.cht", "GoldenEye 007 (Europe).cht",
            "GoldenEye 007 (Japan).cht", "GoldenEye 007 (USA).cht", "Goldeneye 007 (U).cht", "007 - The World Is Not Enough (USA).cht",
        ).map { CheatFileRef(it) },
    )

    @Test
    fun `the owner's Castlevania gets a USA file nearest its revision, other regions last`() {
        val picks = n64.candidates("Castlevania (USA) (Rev B).n64.zip")
        assertEquals("Castlevania (U) (V1.1).cht", picks.first().file.name)
        assertEquals(CheatFit.SAME_REGION, picks.first().fit)
        assertEquals(
            listOf("Castlevania (U) (V1.1).cht", "Castlevania (USA).cht", "Castlevania (U) (V1.0).cht", "CASTLEVANIA.cht"),
            picks.take(4).map { it.file.name },
        )
        assertEquals(CheatFit.NO_REGION, picks[3].fit)
        assertEquals("Castlevania (Europe) (En,Fr,De).cht", picks.last().file.name)
        assertEquals(CheatFit.OTHER_REGION, picks.last().fit)
        assertFalse(picks.any { it.file.name.contains("Legacy") })
    }

    @Test
    fun `GoldenEye is found whatever order its words are in`() {
        val picks = n64.candidates("007 - GoldenEye (USA).n64.zip")
        assertEquals(CheatFit.SAME_REGION, picks.first().fit)
        assertTrue(picks.first().file.name in setOf("GoldenEye 007 (USA).cht", "Goldeneye 007 (U).cht"))
        assertTrue(picks.any { it.file.name == "GOLDENEYE.cht" })
        assertFalse(picks.any { it.file.name.contains("World Is Not Enough") })
    }

    @Test
    fun `the same name wins over everything`() {
        val snes = CheatIndex(
            CheatSystem.SNES,
            listOf("Chrono Trigger (U) [!].cht", "Chrono Trigger (Japan).cht", "Chrono Trigger (USA).cht").map { CheatFileRef(it) },
        )
        val best = snes.best("Chrono Trigger (USA).zip")
        assertEquals("Chrono Trigger (USA).cht", best?.file?.name)
        assertEquals(CheatFit.SAME_NAME, best?.fit)
    }

    @Test
    fun `betas and demos come after the real game`() {
        val files = listOf("Game (USA) (Beta).cht", "Game (Europe).cht", "Game (U).cht").map { CheatFileRef(it) }
        val index = CheatIndex(CheatSystem.NES, files)
        assertEquals(
            listOf("Game (U).cht", "Game (USA) (Beta).cht", "Game (Europe).cht"),
            index.candidates("Game (USA) (Rev 1).nes").map { it.file.name },
        )
    }

    @Test
    fun `a game no file has the title of gets similar names to check, and none is picked`() {
        val gbc = CheatIndex(
            CheatSystem.GBC,
            listOf("Pokemon Gold (USA).cht", "Pokemon Silver (USA).cht", "Tetris DX (World).cht").map { CheatFileRef(it) },
        )
        assertNull(gbc.best("Pokemon - Gold Version.gbc"))
        val similar = gbc.similar("Pokemon - Gold Version.gbc")
        assertEquals("Pokemon Gold (USA).cht", similar.first().file.name)
        assertTrue(similar.all { it.fit == CheatFit.SIMILAR })
        assertFalse(similar.any { it.file.name.startsWith("Tetris") })
    }

    @Test
    fun `the search finds files holding every word, any case`() {
        val (found, total) = n64.search("castlevania usa", 2)
        assertEquals(2, found.size)
        assertEquals(2, total)
        assertEquals(1, n64.search("legacy", 10).second)
        assertEquals(n64.files.size, n64.search(" ", 100).second)
    }
}
