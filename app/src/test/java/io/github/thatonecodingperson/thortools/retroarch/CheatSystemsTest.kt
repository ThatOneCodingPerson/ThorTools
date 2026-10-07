package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatSystemsTest {
    /** libretro-database's `cht` folders as GitHub listed them (2026-10-06). */
    private val databaseFolders = setOf(
        "Amstrad - GX4000", "Atari - 2600", "Atari - 5200", "Atari - 7800", "Atari - 8-bit Family", "Atari - Jaguar", "Atari - Lynx",
        "Bandai - WonderSwan Color", "Bandai - WonderSwan", "ChaiLove", "Coleco - ColecoVision", "DOS", "FBNeo - Arcade Games",
        "Mattel - Intellivision", "Microsoft - MSX", "Microsoft - MSX2", "NEC - PC Engine - TurboGrafx 16",
        "NEC - PC Engine CD - TurboGrafx-CD", "NEC - PC Engine SuperGrafx", "NEC - PC-FX", "Nintendo - Family Computer Disk System",
        "Nintendo - Game Boy Advance", "Nintendo - Game Boy Color", "Nintendo - Game Boy", "Nintendo - Nintendo 64 (Aleck64)",
        "Nintendo - Nintendo 64 (Unreleased)", "Nintendo - Nintendo 64 (iQue)", "Nintendo - Nintendo 64", "Nintendo - Nintendo DS",
        "Nintendo - Nintendo Entertainment System", "Nintendo - Satellaview", "Nintendo - Super Nintendo Entertainment System",
        "Nintendo - Virtual Boy", "PrBoom", "PuzzleScript", "SNK - Neo Geo Pocket Color", "SNK - Neo Geo Pocket", "Sega - 32X",
        "Sega - Dreamcast", "Sega - Game Gear", "Sega - Master System - Mark III", "Sega - Mega Drive - Genesis",
        "Sega - Mega-CD - Sega CD", "Sega - SG-1000", "Sega - Saturn", "Sinclair - ZX Spectrum +3", "Sony - PlayStation Portable",
        "Sony - PlayStation", "TIC-80", "Thomson - MOTO", "Wolfenstein 3D",
    )

    @Test
    fun `every console with cheats names a folder libretro-database has`() {
        CheatSystem.entries.mapNotNull { it.db }.forEach { assertTrue(it, it in databaseFolders) }
    }

    @Test
    fun `the owner's ROM folders are recognised, any case`() {
        val expected = mapOf(
            "3DS" to CheatSystem.N3DS,
            "GB" to CheatSystem.GB,
            "GBA" to CheatSystem.GBA,
            "GBC" to CheatSystem.GBC,
            "N64" to CheatSystem.N64,
            "NDS" to CheatSystem.NDS,
            "NES" to CheatSystem.NES,
            "PS2" to CheatSystem.PS2,
            "PSP" to CheatSystem.PSP,
            "Ps1" to CheatSystem.PS1,
            "SNES" to CheatSystem.SNES,
            "Switch" to CheatSystem.SWITCH,
            "Wii" to CheatSystem.WII,
            "WiiU" to CheatSystem.WII_U,
            "gamecube" to CheatSystem.GAMECUBE,
            "genesis" to CheatSystem.MEGA_DRIVE,
        )
        expected.forEach { (folder, system) -> assertEquals(folder, system, CheatSystems.forFolder(folder)) }
        assertNull(CheatSystems.forFolder("iiSULauncher"))
        assertNull(CheatSystems.forFolder("Roms"))
    }

    @Test
    fun `other common folder names and the database's own names count too`() {
        assertEquals(CheatSystem.MEGA_DRIVE, CheatSystems.forFolder("Mega Drive"))
        assertEquals(CheatSystem.MEGA_DRIVE, CheatSystems.forFolder("Sega Genesis"))
        assertEquals(CheatSystem.SNES, CheatSystems.forFolder("sfc"))
        assertEquals(CheatSystem.SNES, CheatSystems.forFolder("Super Famicom"))
        assertEquals(CheatSystem.PS1, CheatSystems.forFolder("psx"))
        assertEquals(CheatSystem.PS1, CheatSystems.forFolder("PlayStation"))
        assertEquals(CheatSystem.NES, CheatSystems.forFolder("famicom"))
        assertEquals(CheatSystem.GAME_GEAR, CheatSystems.forFolder("gamegear"))
        assertEquals(CheatSystem.PC_ENGINE, CheatSystems.forFolder("tg16"))
        assertEquals(CheatSystem.N64, CheatSystems.forFolder("Nintendo - Nintendo 64"))
    }

    @Test
    fun `a game's console comes from an extension only it uses, else from its folders`() {
        val roms = "/storage/EBBF-779E/Roms"
        assertEquals(CheatSystem.N64, CheatSystems.of("$roms/N64/007 - GoldenEye (USA).n64.zip"))
        assertEquals(CheatSystem.GBC, CheatSystems.of("$roms/GBC/Donkey Kong Country.gbc"))
        assertEquals(CheatSystem.GB, CheatSystems.of("$roms/GBC/Tetris (World).gb"))
        assertEquals(CheatSystem.PS1, CheatSystems.of("$roms/Ps1/007 - The World Is Not Enough (USA).chd"))
        assertEquals(CheatSystem.PSP, CheatSystems.of("$roms/PSP/Disgaea - Afternoon of Darkness (USA).cso"))
        assertEquals(CheatSystem.NDS, CheatSystems.of("$roms/NDS/4273 - Pokemon Mystery Dungeon - Explorers of Sky (US)(XenoPhobia).7z"))
        assertEquals(CheatSystem.SNES, CheatSystems.of("$roms/SNES/Chrono Trigger (USA).zip"))
        assertEquals(CheatSystem.MEGA_DRIVE, CheatSystems.of("/storage/emulated/0/Download/Sonic the Hedgehog (USA, Europe).md"))
        assertEquals(CheatSystem.SNES, CheatSystems.of("$roms/SNES/Hacks/Some Hack.zip"))
        assertNull(CheatSystems.of("$roms/iiSULauncher/cover.zip"))
    }

    @Test
    fun `game files are told from everything else by extension`() {
        val extensions = CheatSystems.gameExtensions
        listOf("zip", "7z", "chd", "cue", "iso", "n64", "gbc", "nds", "cso", "m3u").forEach { assertTrue(it, it in extensions) }
        listOf("png", "txt", "srm", "state", "cht", "xml").forEach { assertTrue(it, it !in extensions) }
    }
}
