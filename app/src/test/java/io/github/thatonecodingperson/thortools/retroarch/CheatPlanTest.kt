package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatPlanTest {
    private val roms = "/storage/EBBF-779E/Roms"
    private val retroArch = "1.22.2_GIT"

    private fun file(path: String) = RomFile(path, 1, 1)

    @Test
    fun `games are the game files of known consoles, without a disc's tracks or pictures`() {
        val games = RomLibrary.games(
            listOf(
                file("$roms/N64/007 - GoldenEye (USA).n64.zip"),
                file("$roms/GBC/Donkey Kong Country.gbc"),
                file("$roms/Ps1/Crash Bandicoot (USA).cue"),
                file("$roms/Ps1/Crash Bandicoot (USA) (Track 1).bin"),
                file("$roms/Ps1/Crash Bandicoot (USA) (Track 2).bin"),
                file("$roms/Ps1/007 - The World Is Not Enough (USA).chd"),
                file("$roms/PS2/God of War (USA).iso"),
                file("$roms/iiSULauncher/cover.png"),
                file("$roms/N64/notes.txt"),
            ),
        )
        assertEquals(
            listOf(
                "Donkey Kong Country.gbc" to CheatSystem.GBC,
                "007 - GoldenEye (USA).n64.zip" to CheatSystem.N64,
                "007 - The World Is Not Enough (USA).chd" to CheatSystem.PS1,
                "Crash Bandicoot (USA).cue" to CheatSystem.PS1,
                "God of War (USA).iso" to CheatSystem.PS2,
            ),
            games.map { it.name to it.system },
        )
        assertEquals("007 - GoldenEye (USA)", games[1].title)
        assertEquals(
            listOf("$roms/Ps1/Crash Bandicoot (USA) (Track 1).bin", "$roms/Ps1/Crash Bandicoot (USA) (Track 2).bin"),
            games[3].tracks,
        )
    }

    @Test
    fun `the user's choices are kept and read back, a choice of none too`() {
        val choices = CheatChoices(
            files = mapOf("$roms/N64/a.zip" to "A (USA).cht", "$roms/N64/b.zip" to null),
            off = mapOf("$roms/N64/a.zip" to setOf("ParaLLEl N64"), "$roms/N64/c.zip" to emptySet()),
        )
        assertEquals(choices, CheatChoices.decode(choices.encode()))
        assertEquals(CheatChoices(), CheatChoices.decode("not ours"))
    }

    @Test
    fun `choices kept before keep their files and forget the cores ticked then`() {
        val old = "thortools-cheat-choices 1\nfile\t$roms/N64/a.zip\tA (USA).cht\ncores\t$roms/N64/a.zip\tPCSX-ReARMed\n"
        assertEquals(CheatChoices(files = mapOf("$roms/N64/a.zip" to "A (USA).cht")), CheatChoices.decode(old))
    }

    private val castlevania = RomGame("$roms/N64/Castlevania (USA) (Rev B).n64.zip", "Castlevania (USA) (Rev B).n64.zip", CheatSystem.N64)

    private val side = RetroArchSide(
        cheatsRoot = "/data/media/0/Android/media/com.retroarch.aarch64/RetroArch/cheats",
        installed = listOf(InstalledCheats("Mupen64Plus-Next", "Something Else.cht", 3, 0)),
        cheatFolders = setOf("Mupen64Plus-Next"),
        made = setOf("Mupen64Plus-Next", "parallel n64"),
        played = mapOf("Mupen64Plus-Next" to setOf("Castlevania (USA) (Rev B).n64.state")),
        cores = listOf(
            CoreInfo("mupen64plus_next_libretro", "Mupen64Plus-Next", listOf("Nintendo - Nintendo 64")),
            CoreInfo("parallel_n64_libretro", "ParaLLEl N64", listOf("Nintendo - Nintendo 64", "Nintendo - Nintendo 64DD"), cheats = false),
            CoreInfo("snes9x_libretro", "Snes9x", listOf("Nintendo - Super Nintendo Entertainment System")),
        ),
    )

    private fun started(game: RomGame, core: String) = HistoryEntry(game.path, game.name, core)

    @Test
    fun `every installed core of the console gets the file, the one RetroArch last started the game with first`() {
        val launches = listOf(started(castlevania, "parallel_n64_libretro"), started(castlevania, "mupen64plus_next_libretro"))
        val targets = CheatPlan.targets(castlevania, side, launches, retroArch)
        assertEquals(listOf("parallel n64", "Mupen64Plus-Next"), targets.map { it.core })
        assertEquals(listOf(true, false), targets.map { it.lastPlayed })
        assertEquals(false, targets[0].takesCheats)
        assertEquals(setOf("parallel n64", "Mupen64Plus-Next"), CheatPlan.chosenCores(castlevania, targets, CheatChoices()))
        val unticked = CheatChoices(off = mapOf(castlevania.path to setOf("PARALLEL N64", "Gone Core")))
        assertEquals(setOf("Mupen64Plus-Next"), CheatPlan.chosenCores(castlevania, targets, unticked))
        val never = CheatPlan.targets(castlevania, side, emptyList(), retroArch)
        assertEquals(listOf("Mupen64Plus-Next", "parallel n64"), never.map { it.core })
        assertTrue(never.none { it.lastPlayed })
    }

    @Test
    fun `a game is never sent to a core of another console`() {
        val snes = RomGame("$roms/SNES/Castlevania (USA) (Rev B).n64.zip", "Castlevania (USA) (Rev B).n64.zip", CheatSystem.SNES)
        assertEquals(listOf("Snes9x"), CheatPlan.targets(snes, side, emptyList(), retroArch).map { it.core })
    }

    private val mesenSide = RetroArchSide(
        cheatsRoot = "/data/media/0/Android/media/com.retroarch.aarch64/RetroArch/cheats",
        installed = listOf(
            InstalledCheats("MesenCE", "Super Mario World (USA).cht", 249, 0),
            InstalledCheats("MesenCE", "Zombies Ate My Neighbors (USA).cht", 247, 0),
            InstalledCheats("Mesen2", "Zombies Ate My Neighbors (USA).cht", 1, 1),
        ),
        cheatFolders = setOf("MesenCE", "Mesen2"),
        made = setOf("Mesen2", "Genesis Plus GX"),
        played = mapOf("Mesen2" to setOf("Super Mario World (USA).srm")),
        cores = listOf(
            CoreInfo("genesis_plus_gx_libretro", "Genesis Plus GX", listOf("Sega - Mega Drive - Genesis")),
            CoreInfo("mesen2_libretro", "MesenCE", listOf("Nintendo - Super Nintendo Entertainment System")),
        ),
    )

    @Test
    fun `cheats in the info file's folder are found where RetroArch doesn't look`() {
        val smw = RomGame("$roms/SNES/Super Mario World (USA).zip", "Super Mario World (USA).zip", CheatSystem.SNES)
        val launches = listOf(started(smw, "mesen2_libretro"))
        val targets = CheatPlan.targets(smw, mesenSide, launches, retroArch)
        assertEquals(listOf("Mesen2"), targets.map { it.core })
        assertNull(targets[0].installed)
        assertEquals(CheatStatus("Mesen2", null, "MesenCE", false), CheatPlan.status(smw, mesenSide, launches, retroArch))
    }

    @Test
    fun `a file of the same name in another console's core isn't the game's`() {
        val zombies =
            RomGame("$roms/genesis/Zombies Ate My Neighbors (USA).zip", "Zombies Ate My Neighbors (USA).zip", CheatSystem.MEGA_DRIVE)
        val targets = CheatPlan.targets(zombies, mesenSide, emptyList(), retroArch)
        assertEquals(listOf("Genesis Plus GX"), targets.map { it.core })
        val names = CheatPlan.names(zombies, emptyList(), retroArch)
        assertTrue(CheatPlan.elsewhere(zombies, names, mesenSide, targets).isEmpty())
        assertEquals(CheatStatus(null, null, null, false), CheatPlan.status(zombies, mesenSide, emptyList(), retroArch))
    }

    @Test
    fun `a disc started by its track also gets the track's name`() {
        val sotn = RomGame(
            "$roms/Ps1/Castlevania - Symphony of the Night (USA).cue",
            "Castlevania - Symphony of the Night (USA).cue",
            CheatSystem.PS1,
            tracks = listOf("$roms/Ps1/Castlevania - Symphony of the Night (USA) (Track 1).bin"),
        )
        val track = HistoryEntry(sotn.tracks[0], "Castlevania - Symphony of the Night (USA) (Track 1).bin", "pcsx_rearmed_libretro")
        assertEquals(
            listOf("Castlevania - Symphony of the Night (USA) (Track 1).cht", "Castlevania - Symphony of the Night (USA).cht"),
            CheatPlan.names(sotn, listOf(track), retroArch),
        )
    }

    @Test
    fun `RetroArch's history joins the games of the folders, and brings the ones that aren't there`() {
        val sotn = RomGame(
            "$roms/Ps1/Castlevania - Symphony of the Night (USA).cue",
            "Castlevania - Symphony of the Night (USA).cue",
            CheatSystem.PS1,
            tracks = listOf("$roms/Ps1/Castlevania - Symphony of the Night (USA) (Track 1).bin"),
        )
        val history = listOf(
            HistoryEntry("$roms/PS1/3Xtreme (USA).chd", "3Xtreme (USA).chd", "mednafen_psx_hw_libretro"),
            HistoryEntry(sotn.tracks[0].uppercase(), "Castlevania - Symphony of the Night (USA) (Track 1).bin", "pcsx_rearmed_libretro"),
            HistoryEntry("/storage/x/odd/Game.bin", "Game.bin", "snes9x_libretro"),
            HistoryEntry("/storage/x/odd/Unknown.bin", "Unknown.bin", null),
            HistoryEntry("$roms/PS1/3Xtreme (USA).chd", "3Xtreme (USA).chd", "pcsx_rearmed_libretro"),
        )
        val played = RomLibrary.played(listOf(sotn), history, side.cores)
        assertEquals(listOf("3Xtreme (USA).chd", sotn.name, "Game.bin"), played.games.map { it.name })
        assertEquals(CheatSystem.PS1, played.games[0].system)
        assertEquals(CheatSystem.SNES, played.games[2].system)
        assertEquals(listOf("mednafen_psx_hw_libretro", "pcsx_rearmed_libretro"), played.launches[played.games[0].path]?.map { it.core })
        assertEquals(1, played.launches[sotn.path]?.size)
    }

    @Test
    fun `a console's list shows the games RetroArch played first, then the others`() {
        val snes = listOf("Axelay (USA).sfc", "Super Mario World (USA).zip", "Zombies Ate My Neighbors (USA).zip")
            .map { RomGame("$roms/SNES/$it", it, CheatSystem.SNES) }
        val onlyPlayed = RomGame("/storage/x/SNES/Final Fight 3 (USA).zip", "Final Fight 3 (USA).zip", CheatSystem.SNES)
        val gba = RomGame("$roms/GBA/Kirby (USA).gba", "Kirby (USA).gba", CheatSystem.GBA)
        val played = PlayedGames(
            games = listOf(snes[2], onlyPlayed, gba),
            launches = listOf(snes[2], onlyPlayed, gba).associate { it.path to listOf(HistoryEntry(it.path, it.name, null)) },
        )
        val cheats = RaCheats(games = snes + gba, played = played, system = CheatSystem.SNES)
        assertEquals(listOf(CheatSystem.GBA to 1, CheatSystem.SNES to 4), cheats.systems)
        val shown = cheats.shownGames(3)
        assertEquals(listOf(snes[2], onlyPlayed), shown.played)
        assertEquals(listOf(snes[0]), shown.others)
        assertEquals(4, shown.total)
        assertEquals(listOf(snes[1]), cheats.copy(search = "mario").shownGames(10).others)
    }

    @Test
    fun `the user's cheat file wins while it is in the list, none means none`() {
        val index = CheatIndex(CheatSystem.N64, listOf("Castlevania (USA).cht", "Castlevania (U) (V1.1).cht").map { CheatFileRef(it) })
        assertEquals("Castlevania (U) (V1.1).cht", CheatPlan.pick(castlevania, index, CheatChoices())?.file?.name)
        val chosen = CheatChoices(files = mapOf(castlevania.path to "Castlevania (USA).cht"))
        assertEquals("Castlevania (USA).cht", CheatPlan.pick(castlevania, index, chosen)?.file?.name)
        assertNull(CheatPlan.pick(castlevania, index, CheatChoices(files = mapOf(castlevania.path to null))))
        val gone = CheatChoices(files = mapOf(castlevania.path to "Not There.cht"))
        assertEquals("Castlevania (U) (V1.1).cht", CheatPlan.pick(castlevania, index, gone)?.file?.name)
    }

    @Test
    fun `games RetroArch gives the same cheat file name are found`() {
        val nes = listOf("Super Mario Bros. (World).nes", "Super Mario Bros. 3 (USA).nes", "Tetris (USA).nes")
            .map { RomGame("$roms/NES/$it", it, CheatSystem.NES) }
        assertEquals(listOf(nes[1]), CheatPlan.sharing(nes[0], nes, retroArch))
        assertTrue(CheatPlan.sharing(nes[2], nes, retroArch).isEmpty())
        assertTrue(CheatPlan.sharing(nes[0], nes, "1.19.1").isEmpty())
    }
}
