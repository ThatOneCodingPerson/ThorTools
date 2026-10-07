package io.github.thatonecodingperson.thortools.desktop

import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopModeTest {
    private val firefox = "org.mozilla.firefox"
    private val retroArch = "com.retroarch.aarch64"
    private val settings = DesktopSettings(true, DesktopApps(neverIn = setOf(retroArch)), DesktopLayout.DEFAULT)
    private val sent = mutableListOf<String>()

    /** Switched on, in Firefox on the top screen, with the helper's devices up; what was sent so far is forgotten. */
    private fun onInFirefox(layout: DesktopLayout = DesktopLayout.DEFAULT): DesktopMode = DesktopMode { sent += it }.also {
        it.configure(settings.copy(layout = layout))
        it.update(firefox, panelOpen = false, topScreen = true)
        it.onReady(true)
        sent.clear()
    }

    @Test
    fun `they are on only with the screen on, on the top screen, with the panel closed, in an app the lists allow`() {
        assertTrue(DesktopMode.wanted(settings, firefox, panelOpen = false, topScreen = true))
        assertFalse(DesktopMode.wanted(settings, firefox, panelOpen = false, topScreen = false))
        assertFalse(DesktopMode.wanted(settings, firefox, panelOpen = true, topScreen = true))
        assertFalse(DesktopMode.wanted(settings, retroArch, panelOpen = false, topScreen = true))
        assertFalse(DesktopMode.wanted(settings, firefox, panelOpen = false, topScreen = true, screenOn = false))
        assertFalse(DesktopMode.wanted(settings.copy(enabled = false), firefox, panelOpen = false, topScreen = true))
    }

    @Test
    fun `the helper hears only of changes`() {
        val mode = DesktopMode { sent += it }
        mode.configure(settings)
        mode.update(firefox, panelOpen = false, topScreen = true)
        mode.update(firefox, panelOpen = false, topScreen = true)
        mode.update(retroArch, panelOpen = false, topScreen = true)
        mode.configure(settings)
        mode.configure(settings.copy(enabled = false))
        assertEquals(listOf("config ${DesktopLayout.DEFAULT.encode()}", "starts ", "on", "off", "stop"), sent)
    }

    @Test
    fun `nothing is taken until the helper says its devices are up`() {
        val mode = DesktopMode { sent += it }
        mode.configure(settings)
        mode.update(firefox, panelOpen = false, topScreen = true)
        assertFalse(mode.takes(DesktopControl.A))
        assertFalse(mode.key(DesktopControl.A, down = true, repeat = false, startsCombos = false))
        mode.onReady(true)
        assertTrue(mode.takes(DesktopControl.A))
        mode.onReady(false)
        assertFalse(mode.takes(DesktopControl.A))
    }

    @Test
    fun `buttons with a job are taken, the others stay the app's`() {
        val mode = onInFirefox(DesktopLayout.DEFAULT.with(DesktopControl.Y, DesktopJob.NONE))
        assertTrue(mode.takes(DesktopControl.A))
        assertTrue(mode.takes(DesktopControl.R2))
        assertFalse(mode.takes(DesktopControl.Y))
        assertFalse(mode.key(DesktopControl.Y, down = true, repeat = false, startsCombos = false))
        assertFalse(mode.key(DesktopControl.Y, down = false, repeat = false, startsCombos = false))
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `a press the hotkeys passed on goes to the helper, and so does its release`() {
        val mode = onInFirefox()
        assertTrue(mode.key(DesktopControl.A, down = true, repeat = false, startsCombos = false))
        assertTrue(mode.key(DesktopControl.A, down = true, repeat = true, startsCombos = false))
        assertTrue(mode.key(DesktopControl.A, down = false, repeat = false, startsCombos = false))
        assertEquals(listOf("key a 1", "key a 0"), sent)
    }

    @Test
    fun `a button that starts hotkeys does its job when let go, unless a combo used it`() {
        val mode = onInFirefox()
        assertTrue(mode.key(DesktopControl.L1, down = true, repeat = false, startsCombos = true))
        assertTrue(mode.key(DesktopControl.L1, down = false, repeat = false, startsCombos = true))
        assertEquals(listOf("tap l1"), sent)
        sent.clear()
        assertTrue(mode.key(DesktopControl.L1, down = true, repeat = false, startsCombos = true))
        mode.comboRan(PadButton.L1)
        assertTrue(mode.key(DesktopControl.L1, down = false, repeat = false, startsCombos = true))
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `a combo with another first button doesn't stop a waiting button`() {
        val mode = onInFirefox()
        mode.key(DesktopControl.L1, down = true, repeat = false, startsCombos = true)
        mode.comboRan(PadButton.HOME)
        mode.key(DesktopControl.L1, down = false, repeat = false, startsCombos = true)
        assertEquals(listOf("tap l1"), sent)
    }

    @Test
    fun `switched off mid-press, the helper lets go and the app never sees the release`() {
        val mode = onInFirefox()
        mode.key(DesktopControl.B, down = true, repeat = false, startsCombos = false)
        mode.update(firefox, panelOpen = true, topScreen = true)
        assertTrue(mode.key(DesktopControl.B, down = false, repeat = false, startsCombos = false))
        assertEquals(listOf("key b 1", "off"), sent)
        // The next press is the app's again.
        assertFalse(mode.key(DesktopControl.B, down = true, repeat = false, startsCombos = false))
    }

    @Test
    fun `a press whose release was lost lets go before pressing again`() {
        val mode = onInFirefox()
        mode.key(DesktopControl.X, down = true, repeat = false, startsCombos = false)
        mode.key(DesktopControl.X, down = true, repeat = false, startsCombos = false)
        assertEquals(listOf("key x 1", "key x 0", "key x 1"), sent)
    }

    @Test
    fun `while paused only Start is taken, to switch them back on`() {
        val mode = onInFirefox()
        mode.paused = true
        assertTrue(mode.takes(DesktopControl.START))
        assertFalse(mode.takes(DesktopControl.A))
        assertFalse(mode.takes(DesktopControl.R2))
    }

    @Test
    fun `the hold for hotkeys is sent on change, and only while switched on`() {
        val off = DesktopMode { sent += it }
        off.hold(true)
        assertEquals(emptyList<String>(), sent)
        val mode = onInFirefox()
        mode.hold(true)
        mode.hold(true)
        mode.hold(false)
        assertEquals(listOf("hold 1", "hold 0"), sent)
    }

    @Test
    fun `the buttons that start hotkeys are sent when they change`() {
        val mode = onInFirefox()
        mode.startsHotkeys(setOf(DesktopControl.L1, DesktopControl.R2))
        mode.startsHotkeys(setOf(DesktopControl.L1, DesktopControl.R2))
        assertEquals(listOf("starts l1,r2"), sent)
    }

    @Test
    fun `a restarted helper gets everything again, unpaused, and nothing taken is left pressed`() {
        val mode = onInFirefox()
        mode.hold(true)
        mode.key(DesktopControl.A, down = true, repeat = false, startsCombos = false)
        mode.paused = true
        sent.clear()
        mode.resend()
        assertFalse(mode.paused)
        assertFalse(mode.ready)
        assertEquals(listOf("config ${DesktopLayout.DEFAULT.encode()}", "starts ", "hold 1", "on"), sent)
        // A's release is still swallowed, but nothing is sent for it: the old helper's devices are gone.
        sent.clear()
        assertTrue(mode.key(DesktopControl.A, down = false, repeat = false, startsCombos = false))
        assertEquals(emptyList<String>(), sent)
    }
}
