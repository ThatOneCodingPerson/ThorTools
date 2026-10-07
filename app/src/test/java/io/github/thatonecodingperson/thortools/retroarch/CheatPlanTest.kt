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
    }

    @Test
    fun `the user's choices are kept and read back, a choice of none too`() {
        val choices = CheatChoices(
            files = mapOf("$roms/N64/a.zip" to "A (USA).cht", "$roms/N64/b.zip" to null),
            cores = mapOf("$roms/N64/a.zip" to setOf("Mupen64Plus-Next", "ParaLLEl N64"), "$roms/N64/c.zip" to emptySet()),
        )
        assertEquals(choices, CheatChoices.decode(choices.encode()))
        assertEquals(CheatChoices(), CheatChoices.decode("not ours"))
    }

    private val castlevania = RomGame("$roms/N64/Castlevania (USA) (Rev B).n64.zip", "Castlevania (USA) (Rev B).n64.zip", CheatSystem.N64)

    private val side = RetroArchSide(
        cheatsRoot = "/data/media/0/Android/media/com.retroarch.aarch64/RetroArch/cheats",
        installed = listOf(InstalledCheats("Mupen64Plus-Next", "Something Else.cht", 3, 0)),
        cheatFolders = setOf("Mupen64Plus-Next"),
        coreFolders = setOf("Mupen64Plus-Next", "parallel n64"),
        played = mapOf("Mupen64Plus-Next" to setOf("Castlevania (USA) (Rev B).n64.state", "Castlevania (USA) (Rev B).n64.state.png")),
        cores = listOf(
            CoreInfo("mupen64plus_next_libretro", "Mupen64Plus-Next", listOf("Nintendo - Nintendo 64")),
            CoreInfo("parallel_n64_libretro", "ParaLLEl N64", listOf("Nintendo - Nintendo 64", "Nintendo - Nintendo 64DD")),
            CoreInfo("snes9x_libretro", "Snes9x", listOf("Nintendo - Super Nintendo Entertainment System")),
        ),
    )

    @Test
    fun `the core the game was played with comes first and is ticked, the other cores of its console follow`() {
        val targets = CheatPlan.targets(castlevania, side, retroArch)
        assertEquals(listOf("Mupen64Plus-Next", "parallel n64"), targets.map { it.core })
        assertTrue(targets[0].played)
        assertNull(targets[0].installed)
        assertEquals(setOf("Mupen64Plus-Next"), CheatPlan.defaultCores(targets))
        assertEquals(setOf("Mupen64Plus-Next"), CheatPlan.chosenCores(castlevania, targets, CheatChoices()))
        val chosen = CheatChoices(cores = mapOf(castlevania.path to setOf("parallel n64", "Gone Core")))
        assertEquals(setOf("parallel n64"), CheatPlan.chosenCores(castlevania, targets, chosen))
    }

    @Test
    fun `saves of a game of the same name in another console's core don't count`() {
        val snes = RomGame("$roms/SNES/Castlevania (USA) (Rev B).n64.zip", "Castlevania (USA) (Rev B).n64.zip", CheatSystem.SNES)
        val targets = CheatPlan.targets(snes, side, retroArch)
        assertEquals(listOf("Snes9x"), targets.map { it.core })
        assertTrue(targets.none { it.played })
    }

    @Test
    fun `a game never played goes to every installed core of its console, and a file already there shows`() {
        val installed = side.copy(
            played = emptyMap(),
            installed = listOf(InstalledCheats("Mupen64Plus-Next", "Castlevania (USA) (Rev B).cht", 249, 2)),
        )
        val targets = CheatPlan.targets(castlevania, installed, retroArch)
        assertEquals(InstalledCheats("Mupen64Plus-Next", "Castlevania (USA) (Rev B).cht", 249, 2), targets[0].installed)
        assertEquals(setOf("Mupen64Plus-Next"), CheatPlan.defaultCores(targets))
        val fresh = CheatPlan.targets(castlevania, side.copy(played = emptyMap(), installed = emptyList()), retroArch)
        assertEquals(setOf("Mupen64Plus-Next", "parallel n64"), CheatPlan.defaultCores(fresh))
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
