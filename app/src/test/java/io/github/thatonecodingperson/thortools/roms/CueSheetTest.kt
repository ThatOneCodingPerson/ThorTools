package io.github.thatonecodingperson.thortools.roms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CueSheetTest {
    @Test
    fun `a sheet with one file per track keeps every file in order and knows the audio ones`() {
        val sheet = CueSheet.parse(
            """
            FILE "Game (USA) (Track 1).bin" BINARY
              TRACK 01 MODE2/2352
                INDEX 01 00:00:00
            FILE "Game (USA) (Track 2).bin" BINARY
              TRACK 02 AUDIO
                INDEX 00 00:00:00
                INDEX 01 00:02:00
            """.trimIndent().replace("\n", "\r\n"),
        )!!
        assertEquals(listOf("Game (USA) (Track 1).bin", "Game (USA) (Track 2).bin"), sheet.files)
        assertEquals(2, sheet.tracks.size)
        assertFalse(sheet.isAudio("Game (USA) (Track 1).bin"))
        assertTrue(sheet.isAudio("Game (USA) (Track 2).bin"))
    }

    @Test
    fun `a single file with several tracks counts as data when any track in it is data`() {
        val sheet = CueSheet.parse(
            "﻿file \"Tom Clancy's Game.bin\" binary\n  track 1 mode2/2352\n  track 2 audio\n",
        )!!
        assertEquals(listOf("Tom Clancy's Game.bin"), sheet.files)
        assertEquals("MODE2/2352", sheet.tracks[0].type)
        assertFalse(sheet.isAudio("Tom Clancy's Game.bin"))
    }

    @Test
    fun `unquoted names, wave files and windows folders are read`() {
        val sheet = CueSheet.parse("FILE music\\track02.wav WAVE\nTRACK 02 AUDIO\nFILE data.bin BINARY\nTRACK 01 MODE1/2352\n")!!
        assertEquals(listOf("track02.wav", "data.bin"), sheet.files)
        assertTrue(sheet.isAudio("track02.wav"))
    }

    @Test
    fun `a text without a file or a track isn't a sheet`() {
        assertNull(CueSheet.parse("REM nothing here\n"))
        assertNull(CueSheet.parse("TRACK 01 MODE2/2352\n"))
        assertNull(CueSheet.parse("FILE \"a.bin\" BINARY\n"))
    }

    @Test
    fun `a lone image gets a one-track PS1 sheet and only whole sectors count as an image`() {
        assertEquals("FILE \"disc.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n", CueSheet.forLoneImage("disc.bin"))
        assertTrue(CueSheet.isRawImage(2352L * 300_000))
        assertFalse(CueSheet.isRawImage(524_288))
        assertFalse(CueSheet.isRawImage(0))
    }
}
