package io.github.thatonecodingperson.thortools.roms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShrinkTest {
    @Test
    fun `without samples the console's usual saving gives the range`() {
        val estimate = ShrinkEstimate.typical(1000, RomConsole.PS1)
        assertEquals(400, estimate.low)
        assertEquals(600, estimate.high)
        assertFalse(estimate.measured)
        assertEquals(50, estimate.savingPercent(1000))
    }

    @Test
    fun `zeros compress to almost nothing and random data not at all`() {
        val zeros = ShrinkEstimate.deflateRatio(listOf(ByteArray(65536)))!!
        val noise = ShrinkEstimate.deflateRatio(listOf(Random(1).nextBytes(65536)))!!
        assertTrue(zeros < 0.01)
        assertTrue(noise > 0.99)
        assertNull(ShrinkEstimate.deflateRatio(emptyList()))
    }

    @Test
    fun `a measured estimate puts data and audio together and stays within the original size`() {
        val estimate = ShrinkEstimate.measured(dataSize = 1000, audioSize = 1000, dataRatio = 0.5)
        assertTrue(estimate.measured)
        assertTrue(estimate.low < estimate.high)
        assertTrue(estimate.middle in 800..1300)
        val incompressible = ShrinkEstimate.measured(dataSize = 1000, audioSize = 0, dataRatio = 1.5)
        assertTrue(incompressible.high <= 1000)
    }

    @Test
    fun `real results correct later estimates, but only so far`() {
        assertEquals(1.0, ShrinkEstimate.correction(emptyList()), 0.0)
        assertEquals(
            1.1,
            ShrinkEstimate.correction(listOf(ShrinkResult(10, 100, 110), ShrinkResult(10, 100, 105), ShrinkResult(10, 100, 120))),
            1e-9,
        )
        assertEquals(0.7, ShrinkEstimate.correction(listOf(ShrinkResult(10, 100, 10))), 0.0)
    }

    @Test
    fun `samples are spread over the whole file in whole chunks`() {
        assertEquals(listOf(0L, 65536L * 7, 65536L * 14), ShrinkEstimate.sampleOffsets(65536L * 15, 65536, 3))
        assertEquals(listOf(0L), ShrinkEstimate.sampleOffsets(1000, 65536, 5))
        assertTrue(ShrinkEstimate.sampleOffsets(0, 65536, 5).isEmpty())
    }

    @Test
    fun `chdman's progress is read from its last line`() {
        val text = "Compressing, 0.0% complete... (ratio=100.0%)  \rCompressing, 32.3% complete... (ratio=27.9%)  \r"
        assertEquals(ChdProgress(ChdProgress.Phase.COMPRESSING, 32.3, 27.9), ChdTool.progress(text))
        val done = text + "Compression complete ... final ratio = 36.1%            \n"
        assertEquals(ChdProgress(ChdProgress.Phase.DONE, 100.0, 36.1), ChdTool.progress(done))
        assertEquals(36.1, ChdTool.finalRatio(done)!!, 0.0)
        assertEquals(ChdProgress(ChdProgress.Phase.VERIFYING, 10.0), ChdTool.progress("Verifying, 10.0% complete... \r"))
        assertEquals(ChdProgress.Phase.EXTRACTING, ChdTool.progress("Extracting, 50.5% complete... \r")?.phase)
        assertNull(ChdTool.progress("chdman - MAME Compressed Hunks of Data (CHD) manager 0.289 (mame0289)\n"))
        assertTrue(ChdTool.verified("Raw SHA1 verification successful!\nOverall SHA1 verification successful!\n"))
    }

    @Test
    fun `command lines`() {
        assertEquals(listOf("createcd", "-i", "a.cue", "-o", "a.chd", "-np", "6"), ChdTool.createCd("a.cue", "a.chd", 6))
        assertEquals(listOf("extractcd", "-i", "a.chd", "-o", "a.cue", "-ob", "a.bin"), ChdTool.extractCd("a.chd", "a.cue", "a.bin"))
    }
}
