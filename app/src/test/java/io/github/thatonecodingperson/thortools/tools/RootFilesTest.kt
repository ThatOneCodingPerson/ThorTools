package io.github.thatonecodingperson.thortools.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootFilesTest {
    @Test
    fun `a root write passes only with the folder's owner, the mode, the full size and the same bytes`() {
        assertTrue(RootSaveCheck.passes("10152:1078 10152:1078 660 2623 same", 2623))
        assertTrue(RootSaveCheck.passes("  10152:1078 10152:1078 660 2623 same\n", 2623))
        assertFalse(RootSaveCheck.passes("10152:1078 0:0 660 2623 same", 2623))
        assertFalse(RootSaveCheck.passes("10152:1078 10152:1078 600 2623 same", 2623))
        assertFalse(RootSaveCheck.passes("10152:1078 10152:1078 660 2622 same", 2623))
        assertFalse(RootSaveCheck.passes("10152:1078 10152:1078 660 2623 differs", 2623))
        assertFalse(RootSaveCheck.passes("10152:1078 10152:1078 660 2623", 2623))
        assertFalse(RootSaveCheck.passes(null, 2623))
    }

    @Test
    fun `a root copy passes with the folder's owner, the source's size and the same bytes, whatever the mode`() {
        assertTrue(RootSaveCheck.copied("10109:1023 10109:1023 660 524288 same 524288"))
        assertTrue(RootSaveCheck.copied("0:1023 0:1023 770 524288 same 524288\n"))
        assertFalse(RootSaveCheck.copied("10109:1023 0:0 660 524288 same 524288"))
        assertFalse(RootSaveCheck.copied("10109:1023 10109:1023 660 524000 same 524288"))
        assertFalse(RootSaveCheck.copied("10109:1023 10109:1023 660 524288 differs 524288"))
        assertFalse(RootSaveCheck.copied("${RootSaveCheck.FAILED}cp"))
        assertFalse(RootSaveCheck.copied(null))
    }

    @Test
    fun `shared storage is written through Android's own view of it, other places as they are`() {
        assertEquals(
            "/storage/emulated/0/Android/media/com.retroarch.aarch64/RetroArch/system/pcsx2/bios/x.bin",
            RootFiles.storageView("/data/media/0/Android/media/com.retroarch.aarch64/RetroArch/system/pcsx2/bios/x.bin"),
        )
        assertEquals(null, RootFiles.storageView("/data/user/0/com.retroarch/files/retroarch.cfg"))
        assertEquals(null, RootFiles.storageView("/storage/EBBF-779E/bios/x.bin"))
        assertEquals(null, RootFiles.storageView("/data/media/0x/x.bin"))
    }

    @Test
    fun `a path with quotes or spaces stays one shell word`() {
        assertEquals("'/sdcard/a b/c.cht'", RootFiles.quote("/sdcard/a b/c.cht"))
        assertEquals("'Milo'\\''s Astro Lanes (USA).cht'", RootFiles.quote("Milo's Astro Lanes (USA).cht"))
    }
}
