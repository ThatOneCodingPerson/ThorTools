package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaHistoryTest {
    // Entries as RetroArch 1.22.2 wrote them on a Thor.
    private val history = """
        {
          "version": "1.5",
          "default_core_path": "",
          "default_core_name": "",
          "items": [
            {
              "path": "/storage/EBBF-779E/Roms/Ps1/3Xtreme (USA).chd",
              "label": "",
              "core_path": "/data/data/com.retroarch.aarch64/cores/mednafen_psx_hw_libretro_android.so",
              "core_name": "Sony - PlayStation (Beetle PSX HW)",
              "crc32": "",
              "db_name": ""
            },
            {
              "path": "saf://content:%2F%2Fcom.android.externalstorage.documents%2Ftree%2FEBBF-779E%253ARoms/SNES/Super Mario World (USA).zip",
              "label": "",
              "core_path": "/data/user/0/com.retroarch.aarch64/cores/mesen2_libretro_android.so",
              "core_name": "Nintendo - NES / FC / FDS / SNES / SFC / GB / GBC / GBA / NEC - PCE / PCE-CD / Sega - SMS / GG / Bandai - Wswan (MesenCE)",
              "crc32": "",
              "db_name": ""
            },
            {
              "path": "/storage/EBBF-779E/Roms/NES/Pack.zip#Inner Game (USA).nes",
              "core_path": "",
              "core_name": ""
            },
            { "path": "", "core_path": "/data/user/0/com.retroarch.aarch64/cores/x_libretro_android.so" }
          ]
        }
    """.trimIndent()

    @Test
    fun `the history gives each game's file and core, newest first`() {
        val entries = RaHistory.parse(history)
        assertEquals(
            listOf(
                HistoryEntry("/storage/EBBF-779E/Roms/Ps1/3Xtreme (USA).chd", "3Xtreme (USA).chd", "mednafen_psx_hw_libretro"),
                HistoryEntry("/storage/EBBF-779E/Roms/SNES/Super Mario World (USA).zip", "Super Mario World (USA).zip", "mesen2_libretro"),
                HistoryEntry("/storage/EBBF-779E/Roms/NES/Pack.zip", "Inner Game (USA).nes", null),
            ),
            entries,
        )
        assertEquals("Pack.zip", entries[2].name)
    }

    @Test
    fun `a folder picked in Android's picker becomes the path the Files app shows`() {
        assertEquals(
            "/storage/emulated/0/Roms/GBA/Game (USA).gba",
            RaHistory.shownPath("saf://content:%2F%2Fcom.android.externalstorage.documents%2Ftree%2Fprimary%253ARoms/GBA/Game (USA).gba"),
        )
        assertEquals("/storage/emulated/0/Roms/x.nes", RaHistory.shownPath("/data/media/0/Roms/x.nes"))
        assertEquals("/storage/EBBF-779E/C++ Games/a.gb", RaHistory.shownPath("/storage/EBBF-779E/C++ Games/a.gb"))
        assertNull(RaHistory.shownPath("saf://content:%2F%2Fcom.other.provider%2Ftree%2Fabc/x.nes"))
    }

    @Test
    fun `anything that isn't a history gives no entries`() {
        assertTrue(RaHistory.parse("not json").isEmpty())
        assertTrue(RaHistory.parse("{\"items\": 3}").isEmpty())
        assertNull(RaHistory.coreStem("/data/user/0/com.retroarch.aarch64/cores/"))
    }
}
