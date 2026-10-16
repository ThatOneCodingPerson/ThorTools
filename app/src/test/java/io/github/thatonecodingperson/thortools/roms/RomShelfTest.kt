package io.github.thatonecodingperson.thortools.roms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomShelfTest {
    @Test
    fun `the texts of several files come apart again, and survive being kept`() {
        val texts = mapOf("/a/Game (USA).cue" to "FILE \"x.bin\" BINARY\n  TRACK 01 MODE2/2352", "/a/b.m3u" to "one.chd\ntwo.chd")
        val output = "@@thortools-file /a/Game (USA).cue\nFILE \"x.bin\" BINARY\n  TRACK 01 MODE2/2352\n\n" +
            "@@thortools-file /a/b.m3u\none.chd\ntwo.chd\n\n"
        assertEquals(texts, RomShelf.splitTexts(output))
        assertEquals(texts, RomShelf.decodeTexts(RomShelf.encodeTexts(texts)))
    }

    @Test
    fun `history lines survive being kept, broken ones are skipped`() {
        val entry = HistoryEntry(1L, "ps1", "Game\twith tab", JobKind.COMPRESS, 700, 400, 380)
        assertEquals(entry.copy(title = "Game with tab"), RomShelf.decodeHistory(RomShelf.encodeHistory(entry).trimEnd()))
        assertNull(RomShelf.decodeHistory("1\tps1\tGame"))
    }

    @Test
    fun `kept estimates are read back`() {
        assertEquals(mapOf("/a|5" to Estimate(1, 2, true)), RomShelf.decodeEstimates("/a|5\t1\t2\nbroken\n"))
    }
}
