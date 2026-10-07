package io.github.thatonecodingperson.thortools.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {

    @Test
    fun `a lock used again unlocks, on another screen it moves the lock`() {
        assertEquals(Screen.BOTTOM, LockPolicy.toggled(null, Screen.BOTTOM))
        assertNull(LockPolicy.toggled(Screen.BOTTOM, Screen.BOTTOM))
        assertEquals(Screen.TOP, LockPolicy.toggled(Screen.BOTTOM, Screen.TOP))
    }

    @Test
    fun `lock to this screen locks where the controller is, and unlocks any lock`() {
        assertEquals(Screen.BOTTOM, LockPolicy.toggledHere(null, Screen.BOTTOM))
        assertNull(LockPolicy.toggledHere(Screen.TOP, Screen.BOTTOM))
    }

    @Test
    fun `moving the controller by hand`() {
        // "Also lock": first press locks, the second unlocks.
        assertEquals(Screen.TOP, LockPolicy.afterMove(null, Screen.TOP, alsoLock = true))
        assertNull(LockPolicy.afterMove(Screen.TOP, Screen.TOP, alsoLock = true))
        // Locked to the top but the controller drifted to the bottom: locked there again (moved back), not unlocked.
        assertEquals(Screen.TOP, LockPolicy.afterMove(Screen.TOP, Screen.TOP, alsoLock = true, onTarget = false))
        assertEquals(Screen.BOTTOM, LockPolicy.afterMove(null, Screen.BOTTOM, alsoLock = true, onTarget = false))
        // A plain move releases a lock on the other screen and keeps one on the same screen.
        assertNull(LockPolicy.afterMove(Screen.BOTTOM, Screen.TOP, alsoLock = false))
        assertEquals(Screen.TOP, LockPolicy.afterMove(Screen.TOP, Screen.TOP, alsoLock = false))
        assertNull(LockPolicy.afterMove(null, Screen.TOP, alsoLock = false))
    }

    @Test
    fun `only the bottom lock pulls the controller back, and not while typing or paused`() {
        assertTrue(LockPolicy.returnsAfterLift(Screen.BOTTOM, Screen.TOP, paused = false))
        assertFalse(LockPolicy.returnsAfterLift(Screen.BOTTOM, Screen.TOP, paused = true))
        assertFalse(LockPolicy.returnsAfterLift(Screen.BOTTOM, Screen.BOTTOM, paused = false))
        assertFalse(LockPolicy.returnsAfterLift(Screen.TOP, Screen.TOP, paused = false))
        assertFalse(LockPolicy.returnsAfterLift(null, Screen.TOP, paused = false))
    }

    @Test
    fun `the bottom lock waits while typing, wherever the keyboard shows`() {
        // A keyboard on either screen holds the return off, every time it looks.
        assertTrue(LockPolicy.waitsForTyping(keyboardShown = true, editableFocused = false, firstLook = false))
        // A text field just tapped holds it off on the first look, while the keyboard is still coming.
        assertTrue(LockPolicy.waitsForTyping(keyboardShown = false, editableFocused = true, firstLook = true))
        // A field left focused after the keyboard closed doesn't keep the controller away.
        assertFalse(LockPolicy.waitsForTyping(keyboardShown = false, editableFocused = true, firstLook = false))
        assertFalse(LockPolicy.waitsForTyping(keyboardShown = false, editableFocused = false, firstLook = true))
    }

    @Test
    fun `touch slots report the first finger and the last one leaving`() {
        val slots = TouchSlots()
        assertEquals(TouchSlots.Change.NEW_FINGER, slots.onEvent(TouchSlots.ABS_MT_TRACKING_ID, 12))
        assertNull(slots.onEvent(TouchSlots.ABS_MT_SLOT, 1))
        assertEquals(TouchSlots.Change.NEW_FINGER, slots.onEvent(TouchSlots.ABS_MT_TRACKING_ID, 13))
        assertNull(slots.onEvent(TouchSlots.ABS_MT_TRACKING_ID, -1))
        assertNull(slots.onEvent(TouchSlots.ABS_MT_SLOT, 0))
        assertEquals(TouchSlots.Change.ALL_LIFTED, slots.onEvent(TouchSlots.ABS_MT_TRACKING_ID, -1))
        // A release for a slot that never pressed (missed events) is not a lift.
        assertNull(slots.onEvent(TouchSlots.ABS_MT_TRACKING_ID, -1))
        assertNull(slots.onEvent(0, 500))
    }

    @Test
    fun `lift lines travel between helper and app`() {
        assertEquals("u top", HelperMessage.format(HelperMessage.Lift(Screen.TOP)))
        assertEquals(HelperMessage.Lift(Screen.BOTTOM), HelperMessage.parse("u bottom"))
    }
}
