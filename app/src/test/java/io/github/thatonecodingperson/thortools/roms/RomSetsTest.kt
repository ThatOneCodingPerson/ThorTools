package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.CheatSystem
import io.github.thatonecodingperson.thortools.retroarch.RomFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomSetsTest {
    private val ps1 = "/storage/EBBF-779E/Roms/Ps1"
    private val disc = 2352L * 200_000

    private fun file(path: String, size: Long = disc) = RomFile(path, size, 1_760_000_000L)

    /** A folder shaped like a real one: lone bins, a 3-track cue game, a game folder, a CHD, the BIOS and saves. */
    private val files = listOf(
        file("$ps1/Crash Bandicoot (USA).bin"),
        file("$ps1/SCPH1001.BIN", 524_288),
        file("$ps1/epsxe000.mcr", 131_072),
        file("$ps1/Game.000", 2352L * 10),
        file("$ps1/Racer (USA).cue", 300),
        file("$ps1/Racer (USA) (Track 1).bin"),
        file("$ps1/Racer (USA) (Track 2).bin", 2352L * 1000),
        file("$ps1/Racer (USA) (Track 3).bin", 2352L * 1000),
        file("$ps1/Spyro (USA).chd", 300_000_000),
        file("$ps1/Spyro (Europe).bin"),
        file("$ps1/Spyro (Europe).sbi", 400),
        file("$ps1/Castle (USA)/Castle (USA).cue", 200),
        file("$ps1/Castle (USA)/Castle (USA) (Track 1).bin"),
        file("$ps1/Lost (USA) (Track 1).bin"),
        file("$ps1/Lost (USA) (Track 2).bin", 2352L * 1000),
        file("$ps1/Solo (USA) (Track 1).bin"),
        file("$ps1/FF (USA) (Disc 1).cue", 100),
        file("$ps1/FF (USA) (Disc 1).bin"),
        file("$ps1/FF (USA) (Disc 2).cue", 100),
        file("$ps1/FF (USA) (Disc 2).bin"),
        file("$ps1/FF (USA).m3u", 60),
        file("$ps1/Eboot.pbp", 300_000_000),
        file("/storage/EBBF-779E/Originals (Thor Tools)/Ps1/Old (USA).bin"),
        file("/storage/EBBF-779E/Roms/PS2/Game.iso", 4_000_000_000),
    )

    private val texts = mapOf(
        "$ps1/Racer (USA).cue" to "FILE \"Racer (USA) (Track 1).bin\" BINARY\n TRACK 01 MODE2/2352\n" +
            "FILE \"Racer (USA) (Track 2).bin\" BINARY\n TRACK 02 AUDIO\nFILE \"Racer (USA) (Track 3).bin\" BINARY\n TRACK 03 AUDIO\n",
        "$ps1/Castle (USA)/Castle (USA).cue" to "FILE \"Castle (USA) (Track 1).bin\" BINARY\n TRACK 01 MODE2/2352\n" +
            "FILE \"Castle (USA) (Track 2).bin\" BINARY\n TRACK 02 AUDIO\n",
        "$ps1/FF (USA) (Disc 1).cue" to "FILE \"FF (USA) (Disc 1).bin\" BINARY\n TRACK 01 MODE2/2352\n",
        "$ps1/FF (USA) (Disc 2).cue" to "FILE \"FF (USA) (Disc 2).bin\" BINARY\n TRACK 01 MODE2/2352\n",
        "$ps1/FF (USA).m3u" to "FF (USA) (Disc 1).cue\nFF (USA) (Disc 2).cue\n",
    )

    private val games = RomSets.games(RomConsole.PS1, files, texts).associateBy { it.title }

    @Test
    fun `a lone bin is a game of its own and the BIOS, memory cards and saves are left out`() {
        val crash = games.getValue("Crash Bandicoot (USA)")
        assertTrue(crash.lone)
        assertTrue(crash.canCompress)
        assertEquals("$ps1/Crash Bandicoot (USA).chd", crash.output(RomFormat.CHD_CD))
        assertNull(games["SCPH1001"])
        assertNull(games["epsxe000"])
        assertTrue(games.values.none { it.files.any { file -> file.name == "Game.000" } })
    }

    @Test
    fun `a cue game takes the tracks its sheet names and knows which are audio`() {
        val racer = games.getValue("Racer (USA)")
        assertFalse(racer.lone)
        assertEquals(4, racer.files.size)
        assertEquals(2352L * 2000, racer.audioSize)
        assertTrue(racer.canCompress)
        assertTrue(games.keys.none { it.startsWith("Racer (USA) (Track") })
    }

    @Test
    fun `a sheet naming a file that isn't there needs a look`() {
        val castle = games.getValue("Castle (USA)")
        assertEquals(GameProblem.MissingTracks(listOf("Castle (USA) (Track 2).bin"), total = 2), castle.problem)
        assertFalse(castle.canCompress)
    }

    @Test
    fun `track files without their sheet need it, while a single track one is a lone game`() {
        assertEquals(GameProblem.NeedsSheet, games.getValue("Lost (USA)").problem)
        val solo = games.getValue("Solo (USA)")
        assertNull(solo.problem)
        assertTrue(solo.lone)
    }

    @Test
    fun `a CHD with the same name joins its game, a CHD alone is a game of its own`() {
        val spyro = games.getValue("Spyro (USA)")
        assertNull(spyro.main)
        assertEquals("Spyro (USA).chd", spyro.compressed?.name)
        assertFalse(spyro.canCompress)
        assertEquals("$ps1/Spyro (Europe).sbi", games.getValue("Spyro (Europe)").sbi)
    }

    @Test
    fun `discs know their number and the playlist naming them`() {
        val first = games.getValue("FF (USA) (Disc 1)")
        assertEquals(1, first.disc)
        assertEquals(listOf("$ps1/FF (USA).m3u"), first.playlists)
        assertEquals(2, games.getValue("FF (USA) (Disc 2)").disc)
    }

    @Test
    fun `files Thor Tools doesn't compress yet are listed with the reason, moved originals not at all`() {
        assertEquals(GameProblem.NotHandled("pbp"), games.getValue("Eboot").problem)
        assertNull(games["Old (USA)"])
        assertNull(games["Game"])
    }

    @Test
    fun `other consoles are counted for the not added yet list`() {
        val more = files + file("/storage/EBBF-779E/Roms/Switch/Zelda (USA).nsp", 1_000_000_000) +
            file("/storage/EBBF-779E/Roms/3DS/Zelda (USA).cci", 1_000_000_000) +
            file("/storage/EBBF-779E/Roms/gamecube/Zelda (USA).rvz", 1_000_000_000)
        assertEquals(mapOf(CheatSystem.SWITCH to 1), RomSets.otherConsoles(more))
    }

    @Test
    fun `an emulator's data files beside the games of a console not added yet aren't counted as games`() {
        val switch = "/storage/emulated/0/Roms/Switch"
        val more = files + listOf(
            file("$switch/Zelda.nsp", 9_000_000_000),
            file("$switch/keys/a.bin", 4096),
            file("$switch/nand/b.bin", 4096),
            file("/storage/emulated/0/Roms/WiiU/Saves/mlc01/usr/title/0005000e/101c9500/content/0010/a.bin", 4096),
            file("/storage/EBBF-779E/Roms/PS2/Old (USA).bin"),
        )
        assertEquals(mapOf(CheatSystem.SWITCH to 1), RomSets.otherConsoles(more))
    }

    @Test
    fun `a playlist's lines naming the old sheet name the compressed file, the rest stays`() {
        val text = "#EXTM3U\nFF (USA) (Disc 1).cue\n  sub/FF (USA) (Disc 2).cue\n"
        assertEquals(
            "#EXTM3U\nFF (USA) (Disc 1).chd\n  sub/FF (USA) (Disc 2).cue\n",
            RomSets.rewritePlaylist(text, "ff (usa) (disc 1).cue", "FF (USA) (Disc 1).chd"),
        )
        assertNull(RomSets.rewritePlaylist(text, "Other.cue", "Other.chd"))
        assertEquals("a.chd\r\nb.cue\r\n", RomSets.rewritePlaylist("a.cue\r\nb.cue\r\n", "a.cue", "a.chd"))
    }

    @Test
    fun `a playlist's name drops the disc number`() {
        assertEquals("FF (USA)", RomSets.playlistTitle("FF (USA) (Disc 1)"))
        assertEquals("Game (Europe) (En,Fr)", RomSets.playlistTitle("Game (Europe) (En,Fr) (Disc 2 of 3)"))
        assertEquals("a.chd\nb.chd\n", RomSets.playlist(listOf("a.chd", "b.chd")))
    }
}
