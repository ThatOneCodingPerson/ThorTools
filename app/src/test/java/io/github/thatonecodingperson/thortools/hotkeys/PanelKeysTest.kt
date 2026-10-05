package io.github.thatonecodingperson.thortools.hotkeys

import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyRecognizer.Effect.GiveBack
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyRecognizer.Effect.Run
import io.github.thatonecodingperson.thortools.hotkeys.PanelKeys.Out
import io.github.thatonecodingperson.thortools.hotkeys.PanelKeys.Panel
import io.github.thatonecodingperson.thortools.panel.PanelTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelKeysTest {
    private val withController = Panel(hasController = true, openedFromTop = true)
    private val withoutController = Panel(hasController = false, openedFromTop = true)

    /** An action that runs with the panel open, so routing tests see the hotkey unchanged. */
    private fun hotkey(
        button: PadButton,
        press: PressKind,
        second: PadButton? = null,
        action: ThorAction = ThorAction.CYCLE_PERFORMANCE,
        arg: String? = null,
    ) = Hotkey(button, second, press, action, arg)

    /** What the service does with a key while the panel is open: the gates, then the recognizer, then routing. */
    private class OpenPanel(hotkeys: List<Hotkey>, private val panel: Panel = Panel(hasController = true, openedFromTop = true)) {
        val keys = HotkeyRecognizer(hotkeys)
        private var homeForPanel = false

        fun key(button: PadButton, down: Boolean, time: Long): Pair<Boolean, List<Out>> {
            val answered = down && PanelKeys.answersHome(button, panel.hasController, keys.takes(PadButton.HOME))
            if (button == PadButton.HOME && (homeForPanel || answered)) {
                homeForPanel = down
                return true to if (down) emptyList() else listOf(Out.PanelHome)
            }
            val step = keys.onKey(button, down, repeat = false, time = time)
            return step.consume to PanelKeys.route(step.effects, panel)
        }

        fun timer(time: Long): List<Out> = PanelKeys.route(keys.onTimer(time).effects, panel)
    }

    @Test
    fun `the panel answers Home only with the controller and no hotkey on Home`() {
        assertTrue(PanelKeys.answersHome(PadButton.HOME, panelHasController = true, hotkeysTakeHome = false))
        assertFalse(PanelKeys.answersHome(PadButton.HOME, panelHasController = true, hotkeysTakeHome = true))
        assertFalse(PanelKeys.answersHome(PadButton.HOME, panelHasController = false, hotkeysTakeHome = false))
        assertFalse(PanelKeys.answersHome(PadButton.BACK, panelHasController = true, hotkeysTakeHome = false))
        assertFalse(PanelKeys.answersHome(PadButton.AYN, panelHasController = true, hotkeysTakeHome = false))
    }

    @Test
    fun `with the panel closed everything stays as the hotkeys decided`() {
        val effects = listOf(
            GiveBack(PadButton.BACK, 1),
            GiveBack(PadButton.HOME, 1),
            Run(hotkey(PadButton.BACK, PressKind.TAP)),
            Run(hotkey(PadButton.HOME, PressKind.DOUBLE, action = ThorAction.HOME)),
        )
        assertEquals(effects.map(Out::Hotkeys), PanelKeys.route(effects, panel = null))
    }

    @Test
    fun `a Back given back is the panel's Back, and the rest go to the app`() {
        assertEquals(listOf(Out.PanelBack), PanelKeys.route(listOf(GiveBack(PadButton.BACK, 1)), withController))
        assertEquals(
            listOf(Out.PanelBack, Out.Hotkeys(GiveBack(PadButton.BACK, 2))),
            PanelKeys.route(listOf(GiveBack(PadButton.BACK, 3)), withController),
        )
    }

    @Test
    fun `without the controller a Back given back and Back's tap go to the app`() {
        val effects = listOf(GiveBack(PadButton.BACK, 1), Run(hotkey(PadButton.BACK, PressKind.TAP)))
        assertEquals(effects.map(Out::Hotkeys), PanelKeys.route(effects, withoutController))
    }

    @Test
    fun `Back's own tap hotkey is the panel's Back, its other hotkeys still run`() {
        val tap = hotkey(PadButton.BACK, PressKind.TAP)
        val double = hotkey(PadButton.BACK, PressKind.DOUBLE)
        val combo = hotkey(PadButton.BACK, PressKind.TAP, second = PadButton.R1)
        val secondBack = hotkey(PadButton.X, PressKind.TAP, second = PadButton.BACK)
        assertEquals(listOf(Out.PanelBack), PanelKeys.route(listOf(Run(tap)), withController))
        assertEquals(
            listOf(Out.Hotkeys(Run(double)), Out.Hotkeys(Run(combo)), Out.Hotkeys(Run(secondBack))),
            PanelKeys.route(listOf(Run(double), Run(combo), Run(secondBack)), withController),
        )
    }

    @Test
    fun `a hotkey for Back is the panel's Back with the controller, and Back for the app without`() {
        val selectBack = hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.SELECT, action = ThorAction.BACK)
        assertEquals(listOf(Out.PanelBack), PanelKeys.route(listOf(Run(selectBack)), withController))
        assertEquals(
            listOf(Out.AfterClosing(Run(selectBack), PanelTiles.LEAVE_MS)),
            PanelKeys.route(listOf(Run(selectBack)), withoutController),
        )
    }

    @Test
    fun `a Home given back closes the panel and goes Home where it was opened from`() {
        assertEquals(listOf(Out.PanelHome), PanelKeys.route(listOf(GiveBack(PadButton.HOME, 1)), withController))
        assertEquals(listOf(Out.PanelHome), PanelKeys.route(listOf(GiveBack(PadButton.HOME, 2)), withoutController))
    }

    @Test
    fun `AYN given back opens AYN's drawer once the panel has closed`() {
        val ayn = GiveBack(PadButton.AYN, 1)
        assertEquals(listOf(Out.AfterClosing(ayn, PanelTiles.LEAVE_MS)), PanelKeys.route(listOf(ayn), withController))
    }

    @Test
    fun `volume and pad buttons given back are untouched`() {
        val effects = listOf(GiveBack(PadButton.VOLUME_UP, 2), GiveBack(PadButton.X, 1))
        assertEquals(effects.map(Out::Hotkeys), PanelKeys.route(effects, withController))
    }

    @Test
    fun `levels and modes run with the panel open`() {
        listOf(
            ThorAction.CYCLE_PERFORMANCE,
            ThorAction.CYCLE_FAN,
            ThorAction.TOGGLE_REFRESH_RATE,
            ThorAction.CYCLE_CONTROLLER_STYLE,
            ThorAction.CYCLE_L2R2,
            ThorAction.BRIGHTER,
            ThorAction.QUIETER,
            ThorAction.TOGGLE_STAY_AWAKE,
            ThorAction.CLEAR_BACKGROUND,
            ThorAction.OPEN_QUICK_PANEL,
        ).forEach { action ->
            val run = Run(hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.X, action = action))
            assertEquals(action.id, listOf(Out.Hotkeys(run)), PanelKeys.route(listOf(run), withController))
        }
    }

    @Test
    fun `actions that leave the panel close it first, as its tiles do`() {
        listOf(
            ThorAction.CONTROLLER_TO_TOP,
            ThorAction.CONTROLLER_TO_BOTTOM,
            ThorAction.TOGGLE_CONTROLLER_LOCK,
            ThorAction.LOCK_CONTROLLER_HERE,
            ThorAction.SWAP_SCREENS,
            ThorAction.CLOSE_OTHER_SCREEN_APP,
            ThorAction.HOME_TOP,
            ThorAction.HOME_BOTH,
            ThorAction.RECENTS,
            ThorAction.NOTIFICATIONS,
            ThorAction.QUICK_SETTINGS,
            ThorAction.LOCK_SCREEN,
            ThorAction.SWIPE_UP,
            ThorAction.AYN_DRAWER,
        ).forEach { action ->
            val run = Run(hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.X, action = action))
            assertEquals(action.id, listOf(Out.AfterClosing(run, PanelTiles.LEAVE_MS)), PanelKeys.route(listOf(run), withController))
        }
    }

    @Test
    fun `a screenshot waits until the panel is gone`() {
        val run = Run(hotkey(PadButton.VOLUME_DOWN, PressKind.HOLD, action = ThorAction.SCREENSHOT))
        val delay = (PanelKeys.route(listOf(run), withController).single() as Out.AfterClosing).delayMs
        assertEquals(PanelTiles.leaveDelayMs(ThorAction.SCREENSHOT.id), delay)
        assertTrue(delay > PanelTiles.LEAVE_MS)
    }

    @Test
    fun `every action leaves the panel exactly when its tile does`() {
        ThorAction.entries.filter { it != ThorAction.LAUNCH_APP }.forEach { action ->
            assertEquals(action.id, PanelTiles.leaveDelayMs(action.id), PanelKeys.leaveDelayMs(action))
        }
        assertEquals(PanelTiles.LEAVE_MS, PanelKeys.leaveDelayMs(ThorAction.LAUNCH_APP))
        assertNull(PanelKeys.leaveDelayMs(ThorAction.OPEN_QUICK_PANEL))
    }

    @Test
    fun `Home as an action becomes Home on the screen the panel was opened from`() {
        val home = hotkey(PadButton.HOME, PressKind.DOUBLE, action = ThorAction.HOME)
        assertEquals(ThorAction.HOME_TOP, PanelKeys.pinHere(home, openedFromTop = true).action)
        assertEquals(ThorAction.HOME_BOTTOM, PanelKeys.pinHere(home, openedFromTop = false).action)
        assertEquals(
            listOf(Out.AfterClosing(Run(home.copy(action = ThorAction.HOME_BOTTOM)), PanelTiles.LEAVE_MS)),
            PanelKeys.route(listOf(Run(home)), Panel(hasController = true, openedFromTop = false)),
        )
    }

    @Test
    fun `closing the app here means the screen the panel was opened from`() {
        val here = hotkey(PadButton.HOME, PressKind.HOLD, second = PadButton.B, action = ThorAction.CLOSE_APP, arg = "here")
        assertEquals("top", PanelKeys.pinHere(here, openedFromTop = true).arg)
        assertEquals("bottom", PanelKeys.pinHere(here, openedFromTop = false).arg)
        val bottom = here.copy(arg = "bottom")
        assertEquals(bottom, PanelKeys.pinHere(bottom, openedFromTop = true))
        val lastApp = here.copy(arg = null)
        assertEquals(lastApp, PanelKeys.pinHere(lastApp, openedFromTop = true))
    }

    @Test
    fun `opening an app here means the screen the panel was opened from`() {
        val here = hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.Y, action = ThorAction.LAUNCH_APP, arg = "com.example.game")
        assertEquals("com.example.game@bottom", PanelKeys.pinHere(here, openedFromTop = false).arg)
        assertEquals("com.example.game@top", PanelKeys.pinHere(here, openedFromTop = true).arg)
        val top = here.copy(arg = "com.example.game@top")
        assertEquals(top, PanelKeys.pinHere(top, openedFromTop = false))
    }

    @Test
    fun `other actions are not pinned`() {
        val swap = hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.X, action = ThorAction.SWAP_SCREENS)
        assertEquals(swap, PanelKeys.pinHere(swap, openedFromTop = false))
    }

    @Test
    fun `a single Back with a double tap hotkey closes the panel after the tap gap`() {
        val panel = OpenPanel(listOf(hotkey(PadButton.BACK, PressKind.DOUBLE)))
        assertEquals(true to emptyList<Out>(), panel.key(PadButton.BACK, down = true, time = 0))
        assertEquals(true to emptyList<Out>(), panel.key(PadButton.BACK, down = false, time = 100))
        assertEquals(listOf(Out.PanelBack), panel.timer(400))
    }

    @Test
    fun `a double tap of Back runs its hotkey and leaves the panel open`() {
        val double = hotkey(PadButton.BACK, PressKind.DOUBLE)
        val panel = OpenPanel(listOf(double))
        panel.key(PadButton.BACK, down = true, time = 0)
        panel.key(PadButton.BACK, down = false, time = 100)
        panel.key(PadButton.BACK, down = true, time = 200)
        assertEquals(true to listOf(Out.Hotkeys(Run(double))), panel.key(PadButton.BACK, down = false, time = 250))
    }

    @Test
    fun `holding Back runs its hold hotkey and the release does nothing`() {
        val hold = hotkey(PadButton.BACK, PressKind.HOLD, action = ThorAction.SWAP_SCREENS)
        val panel = OpenPanel(listOf(hold))
        panel.key(PadButton.BACK, down = true, time = 0)
        assertEquals(listOf(Out.AfterClosing(Run(hold), PanelTiles.LEAVE_MS)), panel.timer(1000))
        assertEquals(true to emptyList<Out>(), panel.key(PadButton.BACK, down = false, time = 1200))
    }

    @Test
    fun `a quick Back with only a hold hotkey closes the panel on release`() {
        val panel = OpenPanel(listOf(hotkey(PadButton.BACK, PressKind.HOLD)))
        panel.key(PadButton.BACK, down = true, time = 0)
        assertEquals(true to listOf(Out.PanelBack), panel.key(PadButton.BACK, down = false, time = 100))
    }

    @Test
    fun `Back plus R1 runs its combo with the panel open`() {
        val combo = hotkey(PadButton.BACK, PressKind.TAP, second = PadButton.R1)
        val panel = OpenPanel(listOf(combo))
        panel.key(PadButton.BACK, down = true, time = 0)
        assertEquals(true to listOf(Out.Hotkeys(Run(combo))), panel.key(PadButton.R1, down = true, time = 100))
        panel.key(PadButton.R1, down = false, time = 150)
        assertEquals(true to emptyList<Out>(), panel.key(PadButton.BACK, down = false, time = 200))
    }

    @Test
    fun `Back with no hotkeys passes both events so the panel closes itself`() {
        val panel = OpenPanel(listOf(hotkey(PadButton.HOME, PressKind.DOUBLE)))
        assertEquals(false to emptyList<Out>(), panel.key(PadButton.BACK, down = true, time = 0))
        assertEquals(false to emptyList<Out>(), panel.key(PadButton.BACK, down = false, time = 100))
    }

    @Test
    fun `X held plus Back runs the X plus Back combo with the panel open`() {
        val combo = hotkey(PadButton.X, PressKind.TAP, second = PadButton.BACK)
        val panel = OpenPanel(listOf(combo))
        assertEquals(false to emptyList<Out>(), panel.key(PadButton.X, down = true, time = 0))
        assertEquals(true to listOf(Out.Hotkeys(Run(combo))), panel.key(PadButton.BACK, down = true, time = 100))
    }

    @Test
    fun `holding Back to look at its combos leaves the panel open`() {
        val panel = OpenPanel(listOf(hotkey(PadButton.BACK, PressKind.TAP, second = PadButton.R1)))
        panel.key(PadButton.BACK, down = true, time = 0)
        assertEquals(true to emptyList<Out>(), panel.key(PadButton.BACK, down = false, time = 1500))
    }

    @Test
    fun `a quick Back that only starts combos closes the panel on release`() {
        val panel = OpenPanel(listOf(hotkey(PadButton.BACK, PressKind.TAP, second = PadButton.R1)))
        panel.key(PadButton.BACK, down = true, time = 0)
        assertEquals(true to listOf(Out.PanelBack), panel.key(PadButton.BACK, down = false, time = 100))
    }

    @Test
    fun `A, B, L1 and R1 pressed on their own go to the panel`() {
        val panel = OpenPanel(listOf(hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.A)))
        listOf(PadButton.A, PadButton.B, PadButton.L1, PadButton.R1).forEach {
            assertEquals(false to emptyList<Out>(), panel.key(it, down = true, time = 0))
            assertEquals(false to emptyList<Out>(), panel.key(it, down = false, time = 50))
        }
    }

    @Test
    fun `a combo starting with L1 runs with the panel open, and L1 still turns the page`() {
        val combo = hotkey(PadButton.L1, PressKind.TAP, second = PadButton.R1)
        val panel = OpenPanel(listOf(combo))
        assertEquals(false to emptyList<Out>(), panel.key(PadButton.L1, down = true, time = 0))
        assertEquals(true to listOf(Out.Hotkeys(Run(combo))), panel.key(PadButton.R1, down = true, time = 100))
        assertEquals(true to emptyList<Out>(), panel.key(PadButton.R1, down = false, time = 150))
        assertEquals(false to emptyList<Out>(), panel.key(PadButton.L1, down = false, time = 200))
    }

    @Test
    fun `Home's and Back's own tap leave them to the panel and Android`() {
        val panel = OpenPanel(HotkeyList.ownPresses)
        assertEquals(true to emptyList<Out>(), panel.key(PadButton.HOME, down = true, time = 0))
        assertEquals(true to listOf(Out.PanelHome), panel.key(PadButton.HOME, down = false, time = 80))
        assertEquals(false to emptyList<Out>(), panel.key(PadButton.BACK, down = true, time = 200))
        assertEquals(false to emptyList<Out>(), panel.key(PadButton.BACK, down = false, time = 260))
    }

    @Test
    fun `Home with no hotkeys is answered by the panel on release`() {
        val panel = OpenPanel(listOf(hotkey(PadButton.BACK, PressKind.DOUBLE)))
        assertEquals(true to emptyList<Out>(), panel.key(PadButton.HOME, down = true, time = 0))
        assertEquals(true to listOf(Out.PanelHome), panel.key(PadButton.HOME, down = false, time = 80))
    }

    @Test
    fun `Home plus L1 moves the controller after the panel closes`() {
        val toTop = hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.L1, action = ThorAction.CONTROLLER_TO_TOP)
        val toBottom = hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.R1, action = ThorAction.CONTROLLER_TO_BOTTOM)
        val panel = OpenPanel(listOf(toTop, toBottom))
        assertEquals(true to emptyList<Out>(), panel.key(PadButton.HOME, down = true, time = 0))
        assertEquals(
            true to listOf(Out.AfterClosing(Run(toTop), PanelTiles.LEAVE_MS)),
            panel.key(PadButton.L1, down = true, time = 120),
        )
        assertEquals(true to emptyList<Out>(), panel.key(PadButton.L1, down = false, time = 180))
        assertEquals(true to emptyList<Out>(), panel.key(PadButton.HOME, down = false, time = 250))
    }

    @Test
    fun `a quick Home that only starts combos closes the panel and goes Home`() {
        val panel = OpenPanel(listOf(hotkey(PadButton.HOME, PressKind.TAP, second = PadButton.L1, action = ThorAction.CONTROLLER_TO_TOP)))
        assertEquals(true to emptyList<Out>(), panel.key(PadButton.HOME, down = true, time = 0))
        assertEquals(true to listOf(Out.PanelHome), panel.key(PadButton.HOME, down = false, time = 90))
    }

    @Test
    fun `a single Home with a double tap hotkey goes Home after the tap gap`() {
        val panel = OpenPanel(listOf(hotkey(PadButton.HOME, PressKind.DOUBLE, action = ThorAction.RECENTS)))
        panel.key(PadButton.HOME, down = true, time = 0)
        assertEquals(true to emptyList<Out>(), panel.key(PadButton.HOME, down = false, time = 80))
        assertEquals(listOf(Out.PanelHome), panel.timer(400))
    }

    @Test
    fun `Home plus B held closes the app the panel was opened over`() {
        val close = hotkey(PadButton.HOME, PressKind.HOLD, second = PadButton.B, action = ThorAction.CLOSE_APP, arg = "here")
        val panel = OpenPanel(listOf(close), Panel(hasController = true, openedFromTop = false))
        panel.key(PadButton.HOME, down = true, time = 0)
        assertEquals(true to emptyList<Out>(), panel.key(PadButton.B, down = true, time = 100))
        assertEquals(listOf(Out.AfterClosing(Run(close.copy(arg = "bottom")), PanelTiles.LEAVE_MS)), panel.timer(1100))
    }
}
