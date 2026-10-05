package io.github.thatonecodingperson.thortools.hotkeys

import io.github.thatonecodingperson.thortools.actions.ThorAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyAppsTest {
    private val everywhere = Hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.HOME_BOTH)
    private val inGame = Hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.SWAP_SCREENS, apps = setOf("com.game"))
    private val inEmulators = Hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.RECENTS, apps = setOf("com.emu", "com.game"))
    private val other = Hotkey(PadButton.BACK, null, PressKind.HOLD, ThorAction.SCREENSHOT)

    @Test
    fun `apps are kept in the stored form, and lines without them read as before`() {
        val list = listOf(everywhere, inGame, inEmulators, other)
        assertEquals(list, HotkeyList.decode(HotkeyList.encode(list)))
        assertEquals("home;;double;home_both;;text", HotkeyList.encode(listOf(everywhere)))
        val twoApps = inGame.copy(apps = setOf("com.game", "com.emu"))
        assertEquals("home;;double;screens_swap;;text;com.emu,com.game", HotkeyList.encode(listOf(twoApps)))
    }

    @Test
    fun `a hotkey for some apps sits next to the one for every app, but not next to another for the same app`() {
        assertFalse(inGame.clashes(everywhere))
        assertTrue(inGame.clashes(inEmulators))
        assertTrue(everywhere.clashes(everywhere.copy(action = ThorAction.RECENTS)))
        assertFalse(inGame.clashes(inGame.copy(apps = setOf("com.other"))))
        assertEquals(listOf(everywhere, inGame), HotkeyList.put(listOf(everywhere), inGame))
        assertEquals(listOf(everywhere, inEmulators), HotkeyList.put(listOf(everywhere, inGame), inEmulators))
        assertFalse(inGame.sameJob(inGame.copy(apps = emptySet())))
    }

    @Test
    fun `in an app its own hotkeys take the place of the ones for every app with the same keys`() {
        val all = listOf(everywhere, other, inGame)
        assertEquals(listOf(other, inGame), HotkeyList.forApp(all, "com.game"))
        assertEquals(listOf(everywhere, other), HotkeyList.forApp(all, "com.else"))
        assertEquals(listOf(everywhere, other), HotkeyList.forApp(all, null))
        val plain = listOf(everywhere, other)
        assertEquals(plain, HotkeyList.forApp(plain, "com.game"))
    }

    @Test
    fun `suggestions and the AYN panel only fill keys free for every app`() {
        val tapAynInGame = HotkeyList.aynPanel.copy(action = ThorAction.SCREENSHOT, apps = setOf("com.game"))
        assertEquals(listOf(HotkeyList.aynPanel, tapAynInGame), HotkeyList.withAynPanel(listOf(tapAynInGame)))
        assertEquals(listOf(inGame, everywhere), HotkeyList.addFreeSuggestions(listOf(inGame), listOf(everywhere)))
    }

    @Test
    fun `the draft keeps the apps`() {
        val draft = HotkeyDraft.of(inEmulators)
        assertEquals(inEmulators, draft.toHotkey())
        assertEquals(emptySet<String>(), draft.copy(apps = emptySet()).toHotkey()?.apps)
    }
}
