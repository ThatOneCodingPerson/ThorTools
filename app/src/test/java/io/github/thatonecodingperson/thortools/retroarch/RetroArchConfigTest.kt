package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroArchConfigTest {
    /** Lines as RetroArch 1.22 writes them on a Thor, plus a comment and a repeated key. */
    private val text = """
        apply_cheats_after_load = "false"
        config_save_on_exit = "true"
        fastforward_ratio = "0.000000"
        # menu_driver = "rgui"
        input_enable_hotkey_btn = "nul"
        menu_driver = "glui"
        menu_driver = "xmb"
        system_directory = "/storage/3839-3463/bios/EmuDeck_2.3.8_BIOS_Pack"
        rgui_browser_directory = "saf://content:%2F%2Fcom.android.externalstorage.documents%2Ftree%2F3839-3463%253ARoms/N64"
    """.trimIndent() + "\n"

    private val config = RetroArchConfig(text)

    @Test
    fun `values come without their quotes, a repeated key gives its last value, comments are left out`() {
        assertEquals("false", config.value("apply_cheats_after_load"))
        assertEquals("xmb", config.value("menu_driver"))
        assertEquals("nul", config.value("input_enable_hotkey_btn"))
        assertEquals("/storage/3839-3463/bios/EmuDeck_2.3.8_BIOS_Pack", config.value("system_directory"))
        assertNull(config.value("video_driver"))
        assertEquals(true, config.flag("config_save_on_exit"))
        assertEquals(false, config.flag("apply_cheats_after_load"))
        assertNull(config.flag("menu_driver"))
    }

    @Test
    fun `a change rewrites only its own lines and keeps every other line as it was`() {
        val changed = config.with(mapOf("menu_driver" to "ozone", "fastforward_ratio" to "3.000000"))
        val before = text.lines()
        val after = changed.text.lines()
        assertEquals(before.size, after.size)
        before.zip(after).forEach { (old, new) ->
            when {
                old.startsWith("menu_driver") -> assertEquals("menu_driver = \"ozone\"", new)
                old.startsWith("fastforward_ratio") -> assertEquals("fastforward_ratio = \"3.000000\"", new)
                else -> assertEquals(old, new)
            }
        }
        assertEquals("ozone", changed.value("menu_driver"))
        assertEquals("# menu_driver = \"rgui\"", after[3])
        assertEquals(setOf("menu_driver", "fastforward_ratio"), changed.changedFrom(config))
    }

    @Test
    fun `a missing key is added at the end and the file still ends with a new line`() {
        val changed = config.with(mapOf("input_menu_toggle_btn" to "100"))
        assertEquals("100", changed.value("input_menu_toggle_btn"))
        assertTrue(changed.text.endsWith("input_menu_toggle_btn = \"100\"\n"))
        assertEquals(setOf("input_menu_toggle_btn"), changed.changedFrom(config))
    }

    @Test
    fun `asking for what is there already changes nothing`() {
        assertSame(config, config.with(mapOf("menu_driver" to "xmb", "config_save_on_exit" to "true")))
        assertTrue(config.changedFrom(RetroArchConfig(text)).isEmpty())
        assertFalse(config.with(mapOf("menu_driver" to "rgui")).text == text)
    }
}
