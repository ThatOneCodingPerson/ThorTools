package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.CheatSystems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomConsolesTest {
    @Test
    fun `PS1 folders are found under the names front ends and people use`() {
        listOf("Ps1", "PS1", "psx", "PSX", "PlayStation", "Sony - PlayStation", "playstation1").forEach { name ->
            assertEquals(name, RomConsole.PS1, RomConsole.of(CheatSystems.forFolder(name)))
        }
        listOf("PS2", "ps2", "PlayStation 2", "Sony - PlayStation 2").forEach { name ->
            assertEquals(name, RomConsole.PS2, RomConsole.of(CheatSystems.forFolder(name)))
        }
        listOf("PSP", "psp", "pspminis", "Sony - PlayStation Portable").forEach { name ->
            assertEquals(name, RomConsole.PSP, RomConsole.of(CheatSystems.forFolder(name)))
        }
        assertEquals(RomConsole.GBA, RomConsole.of(CheatSystems.forFolder("GBA")))
        assertNull(RomConsole.of(CheatSystems.forFolder("Switch")))
    }

    @Test
    fun `an ePSXe older than its CHD support can't open CHDs and says which version it needs`() {
        val checks = EmulatorChecks.of(
            RomConsole.PS1,
            RomFormat.CHD_CD,
            listOf(InstalledApp("com.epsxe.ePSXe", "2.0.15")),
            coreStems = null,
        )
        assertEquals(1, checks.size)
        assertEquals(Support.NO, checks[0].support)
        assertEquals("2.0.16", checks[0].needs)
        assertFalse(EmulatorChecks.anyOpens(checks))
        val updated = EmulatorChecks.of(
            RomConsole.PS1,
            RomFormat.CHD_CD,
            listOf(InstalledApp("com.epsxe.ePSXe", "2.0.19")),
            coreStems = null,
        )
        assertEquals(Support.DOCUMENTED, updated[0].support)
        assertTrue(EmulatorChecks.anyOpens(updated))
    }

    @Test
    fun `RetroArch's installed PS1 cores count, other cores don't`() {
        val checks = EmulatorChecks.of(
            RomConsole.PS1,
            RomFormat.CHD_CD,
            listOf(InstalledApp("com.epsxe.ePSXe", "2.0.15")),
            coreStems = setOf("mednafen_psx_hw_libretro", "snes9x_libretro"),
        )
        assertEquals(listOf("ePSXe", "RetroArch: Beetle PSX"), checks.map { it.name })
        assertTrue(EmulatorChecks.anyOpens(checks))
    }

    @Test
    fun `an emulator nobody confirmed is neither yes nor no`() {
        val checks = EmulatorChecks.of(
            RomConsole.PS1,
            RomFormat.CHD_CD,
            listOf(InstalledApp("com.emulator.fpse64", "1.0")),
            coreStems = emptySet(),
        )
        assertNull(checks.single().opens)
        assertFalse(EmulatorChecks.anyOpens(checks))
    }

    @Test
    fun `versions compare by their numbers`() {
        assertTrue(EmulatorChecks.compareVersions("2.0.15", "2.0.16") < 0)
        assertTrue(EmulatorChecks.compareVersions("v1.20.4", "1.17") > 0)
        assertEquals(0, EmulatorChecks.compareVersions("2.0", "2.0.0"))
        assertTrue(EmulatorChecks.compareVersions("2.0.16-beta", "2.0.16") == 0)
    }
}
