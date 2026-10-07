package io.github.thatonecodingperson.thortools.hotkeys

import android.view.KeyEvent
import io.github.thatonecodingperson.thortools.actions.ThorAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyListTest {
    @Test
    fun `nothing stored means the AYN defaults, an empty list means none`() {
        assertEquals(HotkeyList.defaults, HotkeyList.decode(null))
        assertEquals(emptyList<Hotkey>(), HotkeyList.decode(""))
    }

    @Test
    fun `hotkeys survive encoding`() {
        val list = HotkeyList.defaults + Hotkey(PadButton.HOME, PadButton.R1, PressKind.DOUBLE, ThorAction.SWAP_SCREENS)
        assertEquals(list, HotkeyList.decode(HotkeyList.encode(list)))
    }

    @Test
    fun `each hotkey keeps its own switches`() {
        val quiet = Hotkey(PadButton.HOME, PadButton.L1, PressKind.TAP, ThorAction.CONTROLLER_TO_TOP, showText = false, lock = true)
        val loud = Hotkey(PadButton.BACK, null, PressKind.DOUBLE, ThorAction.TOGGLE_REFRESH_RATE)
        assertEquals(listOf(quiet, loud), HotkeyList.decode(HotkeyList.encode(listOf(quiet, loud)), textByDefault = false))
    }

    @Test
    fun `hotkeys saved before the switches follow the old Messages switch`() {
        val old = "back;;double;display_toggle_refresh_rate;"
        assertEquals(true, HotkeyList.decode(old, textByDefault = true).single().showText)
        assertEquals(false, HotkeyList.decode(old, textByDefault = false).single().showText)
        assertEquals(false, HotkeyList.decode(old).single().lock)
    }

    @Test
    fun `only controller moves can also lock`() {
        assertTrue(Hotkey.canLock(ThorAction.CONTROLLER_TO_TOP))
        assertTrue(Hotkey.canLock(ThorAction.CONTROLLER_TO_BOTTOM))
        assertEquals(false, Hotkey.canLock(ThorAction.SWAP_SCREENS))
    }

    @Test
    fun `unreadable lines and Home with Back are dropped`() {
        val text = "home;;double;screens_swap;\nnope;;tap;screens_swap;\nhome;back;tap;screens_swap;\nback;;tap;gone_action;"
        assertEquals(listOf(Hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.SWAP_SCREENS)), HotkeyList.decode(text))
    }

    @Test
    fun `lines saved by 0_15_0 decode exactly as before`() {
        val text = listOf(
            "ayn;;tap;open_quick_panel;;text",
            "ayn;;hold;ayn_drawer;;text",
            "back;;double;system_screenshot;;",
            "home;l1;tap;focus_top;;text,lock",
            "home;b;hold;system_close_app;;text",
            "select;r1;tap;launch_app;org.example.game@bottom;text",
        ).joinToString("\n")
        assertEquals(
            listOf(
                Hotkey(PadButton.AYN, null, PressKind.TAP, ThorAction.OPEN_QUICK_PANEL),
                Hotkey(PadButton.AYN, null, PressKind.HOLD, ThorAction.AYN_DRAWER),
                Hotkey(PadButton.BACK, null, PressKind.DOUBLE, ThorAction.SCREENSHOT, showText = false),
                Hotkey(PadButton.HOME, PadButton.L1, PressKind.TAP, ThorAction.CONTROLLER_TO_TOP, lock = true),
                Hotkey(PadButton.HOME, PadButton.B, PressKind.HOLD, ThorAction.CLOSE_APP),
                Hotkey(PadButton.SELECT, PadButton.R1, PressKind.TAP, ThorAction.LAUNCH_APP, arg = "org.example.game@bottom"),
            ),
            HotkeyList.decode(text),
        )
    }

    @Test
    fun `a Close the current app line from 0_15_0 keeps the old target, new ones keep their screen`() {
        assertEquals(null, CloseAppArg.decode(HotkeyList.decode("home;b;hold;system_close_app;;text").single().arg))
        val top = Hotkey(PadButton.HOME, PadButton.B, PressKind.HOLD, ThorAction.CLOSE_APP, arg = CloseAppArg.encode(LaunchScreen.TOP))
        assertEquals(LaunchScreen.TOP, CloseAppArg.decode(HotkeyList.decode(HotkeyList.encode(listOf(top))).single().arg))
    }

    @Test
    fun `a tap of AYN opens the Thor Tools quick panel by default`() {
        assertEquals(HotkeyList.aynPanel, HotkeyList.decode(null).first())
        assertEquals(ThorAction.OPEN_QUICK_PANEL, HotkeyList.aynPanel.action)
        assertEquals(PadButton.AYN to PressKind.TAP, HotkeyList.aynPanel.button to HotkeyList.aynPanel.press)
    }

    @Test
    fun `the quick panel comes back on AYN's tap only when nothing else has that tap`() {
        val other = Hotkey(PadButton.BACK, null, PressKind.DOUBLE, ThorAction.RECENTS)
        assertEquals(listOf(HotkeyList.aynPanel, other), HotkeyList.withAynPanel(listOf(other)))
        assertEquals(listOf(HotkeyList.aynPanel), HotkeyList.withAynPanel(emptyList()))
        val rebound = Hotkey(PadButton.AYN, null, PressKind.TAP, ThorAction.SCREENSHOT)
        assertEquals(listOf(rebound), HotkeyList.withAynPanel(listOf(rebound)))
        assertEquals(HotkeyList.defaults, HotkeyList.withAynPanel(HotkeyList.defaults))
        val combo = Hotkey(PadButton.AYN, PadButton.B, PressKind.TAP, ThorAction.SCREENSHOT)
        assertEquals(listOf(HotkeyList.aynPanel, combo), HotkeyList.withAynPanel(listOf(combo)))
    }

    @Test
    fun `a hotkey replaces the one with the same trigger`() {
        val old = Hotkey(PadButton.AYN, null, PressKind.TAP, ThorAction.OPEN_QUICK_PANEL)
        val new = Hotkey(PadButton.AYN, null, PressKind.TAP, ThorAction.SCREENSHOT)
        assertEquals(HotkeyList.defaults.drop(1) + new, HotkeyList.put(HotkeyList.defaults, new))
        assertEquals(HotkeyList.defaults.drop(1) + new, HotkeyList.put(HotkeyList.defaults, new, replacing = old))
    }

    @Test
    fun `an app launch keeps its app and screen`() {
        assertEquals("org.example.game", AppLaunch("org.example.game").encode())
        assertEquals(AppLaunch("org.example.game"), AppLaunch.decode("org.example.game"))
        assertEquals(AppLaunch("org.example.game", LaunchScreen.BOTTOM), AppLaunch.decode("org.example.game@bottom"))
        val top = AppLaunch("org.example.game", LaunchScreen.TOP)
        assertEquals(top, AppLaunch.decode(top.encode()))
        assertEquals(AppLaunch("org.example.game"), AppLaunch.decode("org.example.game@sideways"))
        assertEquals(null, AppLaunch.decode(null))
        assertEquals(null, AppLaunch.decode("@top"))
    }

    @Test
    fun `the memory switch is stored and read back, and is off for older lines`() {
        val clean = Hotkey(PadButton.HOME, null, PressKind.TRIPLE, ThorAction.CLEAR_BACKGROUND, cleanMemory = true)
        assertEquals(listOf(clean), HotkeyList.decode(HotkeyList.encode(listOf(clean))))
        assertEquals(false, HotkeyList.decode("home;;triple;system_clear_background;;text").single().cleanMemory)
    }

    @Test
    fun `unknown switches are ignored`() {
        val read = HotkeyList.decode("home;;triple;system_clear_background;;text,sparkle").single()
        assertEquals(true, read.showText)
        assertEquals(false, read.cleanMemory)
    }

    @Test
    fun `only Close background apps can clean memory, and combos are best held on Home, Back or AYN`() {
        assertTrue(Hotkey.canCleanMemory(ThorAction.CLEAR_BACKGROUND))
        assertEquals(false, Hotkey.canCleanMemory(ThorAction.CLOSE_APP))
        listOf(PadButton.HOME, PadButton.BACK, PadButton.AYN).forEach { assertTrue(Hotkey.goodComboFirst(it)) }
        listOf(PadButton.SELECT, PadButton.A, PadButton.VOLUME_UP).forEach { assertEquals(false, Hotkey.goodComboFirst(it)) }
    }

    @Test
    fun `D-pad and stick directions are second buttons after Home, Back, AYN or volume only`() {
        assertTrue(Hotkey.allowed(PadButton.HOME, PadButton.RSTICK_UP))
        assertTrue(Hotkey.allowed(PadButton.VOLUME_UP, PadButton.DPAD_LEFT))
        assertEquals(false, Hotkey.allowed(PadButton.DPAD_UP, null))
        assertEquals(false, Hotkey.allowed(PadButton.LSTICK_UP, PadButton.A))
        assertEquals(false, Hotkey.allowed(PadButton.SELECT, PadButton.DPAD_UP))
        val text = listOf("dpad_up;;tap;screens_swap;;text", "home;rstick_down;tap;focus_bottom;;text").joinToString("\n")
        val kept = Hotkey(PadButton.HOME, PadButton.RSTICK_DOWN, PressKind.TAP, ThorAction.CONTROLLER_TO_BOTTOM)
        assertEquals(listOf(kept), HotkeyList.decode(text))
    }

    @Test
    fun `no key event ever reads as a D-pad direction or stick flick`() {
        assertEquals(null, PadButton.of(KeyEvent.KEYCODE_UNKNOWN, 0, xboxLayout = false))
        assertEquals(null, PadButton.of(KeyEvent.KEYCODE_DPAD_UP, 0, xboxLayout = false))
        assertTrue(PadButton.entries.filter { it.secondOnly }.none { it.gamepad })
    }

    @Test
    fun `actions that move focus end the joystick watch, levels keep it`() {
        assertTrue(MotionWatch.endsWatch(ThorAction.CONTROLLER_TO_TOP))
        assertTrue(MotionWatch.endsWatch(ThorAction.SWAP_SCREENS))
        assertTrue(MotionWatch.endsWatch(ThorAction.OPEN_QUICK_PANEL))
        assertTrue(MotionWatch.endsWatch(ThorAction.LAUNCH_APP))
        assertEquals(false, MotionWatch.endsWatch(ThorAction.BRIGHTER))
        // Switching desktop controls moves nothing, so a stick can still be flicked for the next combo.
        assertEquals(false, MotionWatch.endsWatch(ThorAction.TOGGLE_DESKTOP))
        assertEquals(false, MotionWatch.endsWatch(ThorAction.CYCLE_PERFORMANCE))
        assertEquals(false, MotionWatch.endsWatch(ThorAction.SCREENSHOT))
    }

    @Test
    fun `every button belongs to one group`() {
        assertEquals(PadButton.entries.toSet(), ButtonGroup.entries.flatMap { it.buttons }.toSet())
        assertEquals(PadButton.entries.size, ButtonGroup.entries.sumOf { it.buttons.size })
    }

    @Test
    fun `buttons are read by printed label`() {
        assertEquals(PadButton.AYN, PadButton.of(KeyEvent.KEYCODE_HOME, PadButton.AYN_SCAN_CODE, xboxLayout = false))
        assertEquals(PadButton.HOME, PadButton.of(KeyEvent.KEYCODE_HOME, 102, xboxLayout = false))
        assertEquals(PadButton.A, PadButton.of(KeyEvent.KEYCODE_BUTTON_A, 304, xboxLayout = false))
        assertEquals(PadButton.A, PadButton.of(KeyEvent.KEYCODE_BUTTON_B, 304, xboxLayout = true))
        assertEquals(PadButton.L1, PadButton.of(KeyEvent.KEYCODE_BUTTON_L1, 310, xboxLayout = true))
        assertEquals(null, PadButton.of(KeyEvent.KEYCODE_DPAD_UP, 0, xboxLayout = false))
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, PadButton.A.keyCodeIn(xboxLayout = true))
    }
}
