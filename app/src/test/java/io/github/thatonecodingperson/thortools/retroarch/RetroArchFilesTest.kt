package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Test

class RetroArchFilesTest {
    @Test
    fun `a path root sees is shown the way the Files app shows it`() {
        assertEquals(
            "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg",
            RetroArchFiles.shown("/data/media/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg"),
        )
        assertEquals(
            "/storage/emulated/0/Android/data/com.retroarch/files/retroarch.cfg",
            RetroArchFiles.shown("/sdcard/Android/data/com.retroarch/files/retroarch.cfg"),
        )
        val private = "/data/user/0/com.retroarch/files/retroarch.cfg"
        assertEquals(private, RetroArchFiles.shown(private))
        assertEquals("/sdcardx/retroarch.cfg", RetroArchFiles.shown("/sdcardx/retroarch.cfg"))
    }
}
