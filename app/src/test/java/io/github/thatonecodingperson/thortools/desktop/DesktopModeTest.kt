package io.github.thatonecodingperson.thortools.desktop

import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopModeTest {
    private val firefox = "org.mozilla.firefox"
    private val retroArch = "com.retroarch.aarch64"
    private val settings = DesktopSettings(true, DesktopApps(neverIn = setOf(retroArch)), DesktopPreset.STEAM_CONTROLLER.layout)
    private val sent = mutableListOf<String>()

    /** Switched on, in Firefox on the top screen, with the helper's devices up; what was sent so far is forgotten. */
    private fun onInFirefox(layout: DesktopLayout = DesktopPreset.STEAM_CONTROLLER.layout): DesktopMode = DesktopMode { sent += it }.also {
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

    private val bothScreens =
        settings.copy(layout = DesktopPreset.STEAM_CONTROLLER.layout.copy(bottomScreen = true, bottomButtons = setOf(DesktopControl.A)))
    private val holdStart = DesktopPreset.STEAM_CONTROLLER.layout.copy(holdStartSwitch = true)

    private fun situation(
        controllerOnTop: Boolean = false,
        topApp: String? = firefox,
        bottomApp: String? = null,
        bottomShown: Boolean = true,
        panelOpen: Boolean = false,
        topLocked: Boolean = false,
    ) = DesktopSituation(controllerOnTop, topApp, bottomApp, bottomShown, panelOpen, topLocked = topLocked)

    @Test
    fun `keeping the controller on the bottom screen, they work for the top screen's app wherever the controller is`() {
        assertEquals(DesktopDecision(on = true, split = true), DesktopMode.decide(bothScreens, situation()))
        assertEquals(DesktopDecision(on = true, split = true), DesktopMode.decide(bothScreens, situation(controllerOnTop = true)))
        // The panel stops them, but the controller still belongs on the bottom screen when it closes.
        assertEquals(DesktopDecision(on = false, split = true), DesktopMode.decide(bothScreens, situation(panelOpen = true)))
    }

    @Test
    fun `the top lock, no bottom screen or a never-in app below make them work only on the top screen again`() {
        val onTop = DesktopDecision(on = true, split = false)
        val off = DesktopDecision.OFF.copy()
        assertEquals(onTop, DesktopMode.decide(bothScreens, situation(controllerOnTop = true, topLocked = true)))
        assertEquals(onTop, DesktopMode.decide(bothScreens, situation(controllerOnTop = true, bottomShown = false)))
        assertEquals(onTop, DesktopMode.decide(bothScreens, situation(controllerOnTop = true, bottomApp = retroArch)))
        assertEquals(off, DesktopMode.decide(bothScreens, situation(bottomApp = retroArch)))
    }

    @Test
    fun `a never-in app on the top screen stops them, the controller kept below or not`() {
        assertEquals(DesktopDecision.OFF, DesktopMode.decide(bothScreens, situation(topApp = retroArch)))
        assertEquals(DesktopDecision.OFF, DesktopMode.decide(settings, situation(controllerOnTop = true, topApp = retroArch)))
    }

    @Test
    fun `without the choice they work only while the controller is on the top screen`() {
        assertEquals(DesktopDecision(on = true, split = false), DesktopMode.decide(settings, situation(controllerOnTop = true)))
        assertEquals(DesktopDecision.OFF, DesktopMode.decide(settings, situation()))
    }

    @Test
    fun `the helper hears when the controller is kept below, and the bottom screen's buttons are not taken then`() {
        val mode = DesktopMode { sent += it }
        mode.configure(bothScreens)
        sent.clear()
        mode.update(DesktopMode.decide(bothScreens, situation()))
        mode.onReady(true)
        assertEquals(listOf("split 1", "on"), sent)
        assertFalse(mode.takes(DesktopControl.A))
        assertTrue(mode.takes(DesktopControl.R2))
        sent.clear()
        mode.resend()
        assertEquals(listOf("config ${bothScreens.layout.encode()}", "starts ", "split 1", "on"), sent)
    }

    @Test
    fun `in use for the top screen's app also while the panel is open or the controller is below`() {
        assertTrue(DesktopMode.inUse(settings, situation(controllerOnTop = true)))
        assertTrue(DesktopMode.inUse(settings, situation(controllerOnTop = true, panelOpen = true)))
        assertTrue(DesktopMode.inUse(settings, situation(controllerOnTop = false)))
        assertFalse(DesktopMode.inUse(settings.copy(enabled = false), situation(controllerOnTop = true)))
        assertFalse(DesktopMode.inUse(settings, situation(controllerOnTop = true, topApp = retroArch)))
        assertFalse(DesktopMode.inUse(settings, situation(controllerOnTop = true, topApp = "rip.moth.cocoonshell")))
        assertTrue(DesktopMode.inUse(settings.copy(offOnFrontEnds = false), situation(controllerOnTop = true, topApp = null)))
    }

    @Test
    fun `off on a home screen or a front end on the top screen, the controller kept below or not`() {
        assertEquals(DesktopDecision.OFF, DesktopMode.decide(settings, situation(controllerOnTop = true, topApp = null)))
        val cocoon = situation(controllerOnTop = true, topApp = "rip.moth.cocoonshell")
        assertEquals(DesktopDecision.OFF, DesktopMode.decide(settings, cocoon))
        assertEquals(DesktopDecision.OFF, DesktopMode.decide(bothScreens, situation(topApp = "org.es_de.frontend")))
        assertEquals(DesktopDecision.OFF, DesktopMode.decide(settings, situation(controllerOnTop = true, topApp = "com.example.iisu")))
        // With the switch off they work there as anywhere.
        val anywhere = settings.copy(offOnFrontEnds = false)
        val onTop = DesktopDecision(on = true, split = false)
        assertEquals(onTop, DesktopMode.decide(anywhere, situation(controllerOnTop = true, topApp = null)))
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
        assertEquals(listOf("config ${DesktopPreset.STEAM_CONTROLLER.layout.encode()}", "starts ", "on", "off", "stop"), sent)
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
        val mode = onInFirefox(DesktopPreset.STEAM_CONTROLLER.layout.with(DesktopControl.Y, DesktopJob.NONE))
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
    fun `while paused nothing is taken, and the helper hears Start so holding it switches them back on`() {
        val mode = onInFirefox(holdStart)
        mode.paused = true
        assertFalse(mode.takes(DesktopControl.START))
        assertFalse(mode.takes(DesktopControl.A))
        assertFalse(mode.key(DesktopControl.A, down = true, repeat = false, startsCombos = false))
        assertFalse(mode.key(DesktopControl.START, down = true, repeat = false, startsCombos = false))
        assertFalse(mode.key(DesktopControl.START, down = true, repeat = true, startsCombos = false))
        // Switched back on while held: the release is still the app's, which had the press.
        mode.paused = false
        assertFalse(mode.key(DesktopControl.START, down = false, repeat = false, startsCombos = false))
        assertEquals(listOf("key start 1", "key start 0"), sent)
    }

    @Test
    fun `while paused, Start held for a combo doesn't switch them back on`() {
        val mode = onInFirefox(holdStart)
        mode.paused = true
        mode.key(DesktopControl.START, down = true, repeat = false, startsCombos = true)
        mode.comboRan(PadButton.START)
        assertFalse(mode.key(DesktopControl.START, down = false, repeat = false, startsCombos = true))
        assertEquals(listOf("key start 1", "key start 0"), sent)
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
    fun `devices that didn't come up are tried again a few times, then left until the next change`() {
        val mode = onInFirefox()
        assertTrue(mode.onReady(false))
        assertFalse(mode.engaged)
        mode.update(firefox, panelOpen = false, topScreen = true)
        assertEquals(listOf("on"), sent)
        assertTrue(mode.onReady(false))
        mode.update(firefox, panelOpen = false, topScreen = true)
        assertTrue(mode.onReady(false))
        mode.update(firefox, panelOpen = false, topScreen = true)
        assertFalse(mode.onReady(false))
        // Once up, the count starts again.
        mode.update(firefox, panelOpen = false, topScreen = true)
        assertFalse(mode.onReady(true))
        assertTrue(mode.ready)
    }

    @Test
    fun `switched off and on again quickly, only the answer to the newest request counts`() {
        val mode = onInFirefox()
        mode.update(firefox, panelOpen = true, topScreen = true)
        mode.update(firefox, panelOpen = false, topScreen = true)
        // The answers to off and to on again, in order.
        assertFalse(mode.onReady(false))
        assertTrue(mode.engaged)
        assertFalse(mode.onReady(true))
        assertTrue(mode.ready)
        assertTrue(mode.takes(DesktopControl.A))
    }

    @Test
    fun `a failure the helper reports on its own still counts`() {
        val mode = onInFirefox()
        assertTrue(mode.onReady(false))
        assertFalse(mode.ready)
    }

    @Test
    fun `the answer to off is no failure`() {
        val mode = onInFirefox()
        mode.update(firefox, panelOpen = true, topScreen = true)
        assertFalse(mode.onReady(false))
    }

    @Test
    fun `with the helper gone nothing is taken, and what was pressed is swallowed on release`() {
        val mode = onInFirefox()
        mode.key(DesktopControl.A, down = true, repeat = false, startsCombos = false)
        mode.helperGone()
        assertFalse(mode.takes(DesktopControl.A))
        sent.clear()
        assertTrue(mode.key(DesktopControl.A, down = false, repeat = false, startsCombos = false))
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `a pad that went away lets go of what it held`() {
        val mode = onInFirefox()
        mode.key(DesktopControl.R3, down = true, repeat = false, startsCombos = false)
        mode.key(DesktopControl.L1, down = true, repeat = false, startsCombos = true)
        sent.clear()
        mode.padGone()
        assertEquals(listOf("key r3 0"), sent)
    }

    @Test
    fun `a canceled release of a held-back button is no tap`() {
        val mode = onInFirefox()
        mode.key(DesktopControl.L1, down = true, repeat = false, startsCombos = true)
        assertTrue(mode.key(DesktopControl.L1, down = false, repeat = false, startsCombos = true, canceled = true))
        assertEquals(emptyList<String>(), sent)
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
        assertEquals(listOf("config ${DesktopPreset.STEAM_CONTROLLER.layout.encode()}", "starts ", "hold 1", "on"), sent)
        // A's release is still swallowed, but nothing is sent for it: the old helper's devices are gone.
        sent.clear()
        assertTrue(mode.key(DesktopControl.A, down = false, repeat = false, startsCombos = false))
        assertEquals(emptyList<String>(), sent)
    }
}
