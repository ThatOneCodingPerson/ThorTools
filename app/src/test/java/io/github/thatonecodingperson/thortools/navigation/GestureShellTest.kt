package io.github.thatonecodingperson.thortools.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureShellTest {
    /** What [GestureShell.READ] prints on a Thor where nothing was changed. */
    private val untouched = """
        left=null
        right=null
        bottom=0
        bar=1
          displayId=0
            mDisabled1=0x0
    """.trimIndent()

    private val allOff = """
        left=0.0
        right=0.0
        bottom=1
        bar=1
          displayId=0
            mDisabled1=0x1200000
        [x] com.android.shell:thortools_handle_light
        [x] com.android.shell:thortools_handle_dark
    """.trimIndent()

    @Test
    fun `the untouched Thor reads as every swipe working`() {
        val reading = GestureShell.parse(untouched)!!
        assertEquals(GestureParts(), reading.parts)
        assertEquals(BackValues(null, null, "0"), reading.back)
        assertTrue(reading.topBarHidden)
        assertFalse(reading.handleHidden)
    }

    @Test
    fun `Thor Tools' values read as both swipes stopped`() {
        val reading = GestureShell.parse(allOff)!!
        assertEquals(GestureParts(homeSwipe = false, backSwipe = false), reading.parts)
        assertTrue(reading.back.ours)
        assertTrue(reading.handleHidden)
    }

    @Test
    fun `the swipe up stops only with both flags, and only display 0's count`() {
        val homeOnly = untouched.replace("mDisabled1=0x0", "mDisabled1=0x200000")
        assertTrue(GestureShell.parse(homeOnly)!!.parts.homeSwipe)
        val bottomScreen = untouched.replace("displayId=0", "displayId=4").replace("mDisabled1=0x0", "mDisabled1=0x1200000")
        assertTrue(GestureShell.parse(bottomScreen)!!.parts.homeSwipe)
    }

    @Test
    fun `one disabled overlay is not a hidden handle`() {
        val one = allOff.replace("[x] com.android.shell:thortools_handle_dark", "[ ] com.android.shell:thortools_handle_dark")
        assertFalse(GestureShell.parse(one)!!.handleHidden)
    }

    @Test
    fun `output without the four settings is no reading`() {
        assertNull(GestureShell.parse(""))
        assertNull(GestureShell.parse("sh: settings: not found"))
    }

    @Test
    fun `the swipe up commands`() {
        assertEquals("cmd statusbar send-disable-flag home recents", GestureShell.home(works = false))
        assertEquals("cmd statusbar send-disable-flag none", GestureShell.home(works = true))
    }

    @Test
    fun `restoring puts unset keys back as unset`() {
        val script = GestureShell.backRestore(saved = BackValues(null, null, "0"), now = BackValues("0", "0", "1"))
        assertEquals(
            listOf(
                "settings delete secure back_gesture_inset_scale_left",
                "settings delete secure back_gesture_inset_scale_right",
                "settings put global second_disable_back_gesture 0",
            ),
            script.lines(),
        )
    }

    @Test
    fun `restoring puts set keys back exactly`() {
        val script = GestureShell.backRestore(saved = BackValues("1.2", "0.8", "1"), now = BackValues("0.0", "0", "1"))
        assertEquals(
            listOf(
                "settings put secure back_gesture_inset_scale_left 1.2",
                "settings put secure back_gesture_inset_scale_right 0.8",
                "settings put global second_disable_back_gesture 1",
            ),
            script.lines(),
        )
    }

    @Test
    fun `a key the user changed since is left alone`() {
        val script = GestureShell.backRestore(saved = BackValues(null, null, "0"), now = BackValues("1.33", "0", "0"))
        assertEquals(listOf("settings delete secure back_gesture_inset_scale_right"), script.lines())
        assertEquals("", GestureShell.backRestore(BackValues(null, null, "0"), BackValues(null, "1", "0")))
    }

    @Test
    fun `a saved value that isn't a number never reaches the shell`() {
        val script = GestureShell.backRestore(saved = BackValues("1; reboot", null, "0"), now = BackValues("0", "1", "0"))
        assertEquals("settings delete secure back_gesture_inset_scale_left", script)
    }

    @Test
    fun `saved values survive storing, unset ones too`() {
        listOf(BackValues(null, null, "0"), BackValues("1.2", null, null), BackValues("0.8", "1.33", "1")).forEach {
            assertEquals(it, BackValues.decode(it.encode()))
        }
        assertNull(BackValues.decode(null))
        assertNull(BackValues.decode("1|2"))
    }

    @Test
    fun `turning the back swipe off writes all three keys`() {
        assertEquals(
            listOf(
                "settings put secure back_gesture_inset_scale_left 0",
                "settings put secure back_gesture_inset_scale_right 0",
                "settings put global second_disable_back_gesture 1",
            ),
            GestureShell.BACK_OFF.lines(),
        )
    }

    @Test
    fun `the handle overlays are made and enabled, and only disabled when switched off`() {
        val on = GestureShell.handle(hidden = true).lines()
        assertEquals(4, on.size)
        assertTrue(on[0].startsWith("cmd overlay fabricate --target com.android.systemui --name thortools_handle_light "))
        assertTrue(on[0].endsWith("com.android.systemui:color/navigation_bar_home_handle_light_color 0x1c 0x00000000"))
        assertEquals("cmd overlay enable com.android.shell:thortools_handle_dark", on[3])
        assertEquals(
            listOf(
                "cmd overlay disable com.android.shell:thortools_handle_light",
                "cmd overlay disable com.android.shell:thortools_handle_dark",
            ),
            GestureShell.handle(hidden = false).lines(),
        )
    }

    @Test
    fun `the debug toolkit's reading`() {
        assertEquals("home=on,back=on", GestureShell.probe(GestureShell.parse(untouched)))
        assertEquals("home=off,back=off", GestureShell.probe(GestureShell.parse(allOff)))
        assertNull(GestureShell.probe(null))
    }
}
