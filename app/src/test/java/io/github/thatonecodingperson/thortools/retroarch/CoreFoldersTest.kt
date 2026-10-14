package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreFoldersTest {
    private val snes = listOf("Nintendo - Super Nintendo Entertainment System")
    private val mesen2 = CoreInfo("mesen2_libretro", "MesenCE", snes)
    private val mesen = CoreInfo("mesen_libretro", "Mesen", listOf("Nintendo - Nintendo Entertainment System"))
    private val gpgx = CoreInfo("genesis_plus_gx_libretro", "Genesis Plus GX", listOf("Sega - Mega Drive - Genesis"))
    private val beetleHw = CoreInfo("mednafen_psx_hw_libretro", "Beetle PSX HW", listOf("Sony - PlayStation"))
    private val beetle = CoreInfo("mednafen_psx_libretro", "Beetle PSX", listOf("Sony - PlayStation"))

    // The folders RetroArch made on a Thor (config, saves, states).
    private val made = setOf("Mesen2", "Mesen", "Genesis Plus GX", "Beetle PSX HW", "PCSX-ReARMed", "remaps")

    @Test
    fun `a core's folder is the one RetroArch made under its own name, not always its info file's`() {
        val folders = CoreFolders.resolve(listOf(mesen2, mesen, gpgx, beetleHw, beetle), made, emptyMap(), emptyList())
        assertEquals("Mesen2", folders["mesen2_libretro"])
        assertEquals("Mesen", folders["mesen_libretro"])
        assertEquals("Genesis Plus GX", folders["genesis_plus_gx_libretro"])
        assertEquals("Beetle PSX HW", folders["mednafen_psx_hw_libretro"])
        assertEquals("Beetle PSX", folders["mednafen_psx_libretro"])
    }

    @Test
    fun `saves of the games a core started name its folder when nothing is named like it`() {
        val odd = CoreInfo("odd_libretro", "Odd", snes)
        val history = listOf(HistoryEntry("/storage/x/SNES/Game (USA).zip", "Game (USA).zip", "odd_libretro"))
        val played = mapOf("Strange Name" to setOf("Game (USA).srm"), "Other" to emptySet())
        val folders = CoreFolders.resolve(listOf(odd), setOf("Strange Name", "Other"), played, history)
        assertEquals("Strange Name", folders["odd_libretro"])
    }

    @Test
    fun `saves elsewhere override the name only when none are in the folder named like the core`() {
        val core = CoreInfo("x_libretro", "Xcore", snes)
        val history = listOf(HistoryEntry("/storage/x/SNES/Game (USA).zip", "Game (USA).zip", "x_libretro"))
        val both = mapOf("Xcore" to setOf("Game (USA).srm"), "Xcore Next" to setOf("Game (USA).state"))
        assertEquals("Xcore", CoreFolders.resolve(listOf(core), both.keys, both, history)["x_libretro"])
        val next = mapOf("Xcore" to setOf("Other (USA).srm"), "Xcore Next" to setOf("Game (USA).state"))
        assertEquals("Xcore Next", CoreFolders.resolve(listOf(core), next.keys, next, history)["x_libretro"])
    }

    @Test
    fun `a folder another core is named after is never taken from saves`() {
        val history = listOf(HistoryEntry("/storage/x/genesis/Zombies (USA).zip", "Zombies (USA).zip", "genesis_plus_gx_libretro"))
        val played = mapOf("Mesen2" to setOf("Zombies (USA).srm"))
        val folders = CoreFolders.resolve(listOf(mesen2, gpgx), setOf("Mesen2"), played, history)
        assertEquals("Genesis Plus GX", folders["genesis_plus_gx_libretro"])
    }

    @Test
    fun `a core that isn't installed has the folder named like its file`() {
        assertEquals("PCSX-ReARMed", CoreFolders.forStem("pcsx_rearmed_libretro", emptyMap(), made))
        assertEquals(null, CoreFolders.forStem("snes9x_libretro", emptyMap(), made))
    }
}
