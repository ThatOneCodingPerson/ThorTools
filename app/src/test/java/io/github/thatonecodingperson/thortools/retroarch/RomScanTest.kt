package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomScanTest {
    private val card = "/storage/EBBF-779E/Roms"
    private val media = "/data/media/0/Android/media/com.retroarch.aarch64/RetroArch"

    @Test
    fun `the ROM script tries each form of a folder and lists its files with size and time`() {
        val script = RomScan.romScript(listOf(listOf(card, "/mnt/media_rw/EBBF-779E/Roms")))
        assertTrue(script.startsWith("for c in '$card' '/mnt/media_rw/EBBF-779E/Roms'; do if [ -d \"\$c\" ]"))
        assertTrue(script.contains("stat -c 'R 0 %s %Y %n'"))
    }

    @Test
    fun `the ROM listing keeps names with spaces and quotes and skips what isn't a line of its own`() {
        val output = """
            D 0 $card
            R 0 33554432 1759700000 $card/N64/007 - GoldenEye (USA).n64.zip
            R 0 524288 1759700001 $card/GBC/Milo's Quest (USA).gbc
            find: $card/lost+found: Permission denied
            R 1 12 1759700002 /elsewhere/x.nes
        """.trimIndent()
        val listing = RomScan.parseRoms(output, listOf("/storage/EBBF-779E/Roms", "/storage/emulated/0/Gone"), 42L)
        assertEquals(listOf(card, null), listing.reached)
        assertEquals(3, listing.files.size)
        assertEquals(RomFile("$card/GBC/Milo's Quest (USA).gbc", 524288, 1759700001), listing.files[1])
        assertEquals(2, listing.files("/storage/EBBF-779E/Roms"))
        assertNull(listing.files("/storage/emulated/0/Gone"))
    }

    @Test
    fun `the cache keeps the listing as it was`() {
        val listing = RomListing(
            time = 1759700000123,
            folders = listOf("/storage/EBBF-779E/Roms", "/storage/emulated/0/Gone"),
            reached = listOf(card, null),
            files = listOf(RomFile("$card/N64/007 - GoldenEye (USA).n64.zip", 33554432, 1759700000)),
        )
        assertEquals(listing, RomScan.decode(RomScan.encode(listing)))
        assertNull(RomScan.decode("something else"))
    }

    @Test
    fun `RetroArch's side gives cheat files, core folders, played games and cores`() {
        val output = """
            T $media/cheats
            P $media/cheats/Mupen64Plus-Next
            P $media/cheats/Snes9x
            H 2 249 $media/cheats/Mupen64Plus-Next/Castlevania (USA) (Rev B).cht
            Q $media/states/Mupen64Plus-Next
            V $media/states/Mupen64Plus-Next/Castlevania (USA) (Rev B).n64.state
            V $media/states/Mupen64Plus-Next/Castlevania (USA) (Rev B).n64.state.png
            Q $media/saves/PCSX-ReARMed
            V $media/saves/PCSX-ReARMed/3Xtreme (USA).srm
            K snes9x_libretro
            I corename = "Snes9x"
            I database = "Nintendo - Super Nintendo Entertainment System|Nintendo - Sufami Turbo|Nintendo - Satellaview"
            K mupen64plus_next_libretro
            I corename = "Mupen64Plus-Next"
            I database = "Nintendo - Nintendo 64"
            K no_info_libretro
        """.trimIndent()
        val side = RomScan.parseRetroArch(output)
        assertEquals("$media/cheats", side.cheatsRoot)
        assertEquals(listOf(InstalledCheats("Mupen64Plus-Next", "Castlevania (USA) (Rev B).cht", 249, 2)), side.installed)
        assertEquals(setOf("Mupen64Plus-Next", "Snes9x"), side.cheatFolders)
        assertEquals(setOf("Mupen64Plus-Next", "Snes9x", "PCSX-ReARMed"), side.coreFolders)
        assertEquals(setOf("3Xtreme (USA).srm"), side.played["PCSX-ReARMed"])
        assertEquals(2, side.played["Mupen64Plus-Next"]?.size)
        assertEquals(
            listOf(
                CoreInfo(
                    "snes9x_libretro",
                    "Snes9x",
                    listOf("Nintendo - Super Nintendo Entertainment System", "Nintendo - Sufami Turbo", "Nintendo - Satellaview"),
                ),
                CoreInfo("mupen64plus_next_libretro", "Mupen64Plus-Next", listOf("Nintendo - Nintendo 64")),
            ),
            side.cores,
        )
    }

    @Test
    fun `RetroArch's folders come from its settings, else its own ones`() {
        val config = RetroArchConfig(
            """
            cheat_database_path = "/storage/emulated/0/Android/media/com.retroarch.aarch64/RetroArch/cheats"
            savefile_directory = "/storage/emulated/0/Android/media/com.retroarch.aarch64/RetroArch/saves"
            savestate_directory = "default"
            libretro_directory = "/data/user/0/com.retroarch.aarch64/cores"
            """.trimIndent(),
        )
        val places = RetroArchPlaces.of(config, "com.retroarch.aarch64")
        assertEquals("$media/cheats", places.cheats.first())
        assertEquals("$media/saves", places.saves.first())
        assertTrue(places.states.isEmpty())
        assertEquals("/data/user/0/com.retroarch.aarch64/cores", places.cores)
        assertEquals("/data/user/0/com.retroarch.aarch64/info", places.info)
        val script = RomScan.retroArchScript(places)
        assertTrue(script.contains("'/data/user/0/com.retroarch.aarch64/cores'/*_libretro_android.so"))
        assertTrue(script.contains("-name '*.cht'"))
    }
}
