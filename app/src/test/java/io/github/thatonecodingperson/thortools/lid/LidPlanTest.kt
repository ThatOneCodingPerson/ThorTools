package io.github.thatonecodingperson.thortools.lid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LidPlanTest {
    private val now = LidReadings(performance = 2, fan = 4, wifi = true, bluetooth = true, airplane = false)

    @Test
    fun `nothing is chosen by default, so nothing happens`() {
        assertFalse(LidChoices().active)
        assertEquals(LidSession(5, LidReadings()), LidPlan.session(LidChoices(), now, time = 5))
    }

    @Test
    fun `power saving saves the modes in place and sets the lowest ones`() {
        val choices = LidChoices(powerSaving = true)
        assertEquals(LidReadings(performance = 0, fan = 1), LidPlan.closedTargets(choices))
        assertEquals(LidReadings(performance = 2, fan = 4), LidPlan.session(choices, now, time = 1).restore)
    }

    @Test
    fun `a value already as wanted, or unread, is left alone and not restored`() {
        val choices = LidChoices(powerSaving = true, wifiOff = true, bluetoothOff = true)
        val readings = LidReadings(performance = 0, fan = null, wifi = false, bluetooth = true)
        assertEquals(LidReadings(bluetooth = true), LidPlan.session(choices, readings, time = 1).restore)
    }

    @Test
    fun `radios and airplane mode are saved to be put back`() {
        val choices = LidChoices(wifiOff = true, bluetoothOff = true, airplane = true)
        assertEquals(LidReadings(wifi = true, bluetooth = true, airplane = false), LidPlan.session(choices, now, time = 1).restore)
    }

    @Test
    fun `back to sleep alone is something to do, and the master switch turns everything off`() {
        val choices = LidChoices(backToSleep = true)
        assertTrue(choices.active)
        assertFalse(choices.copy(enabled = false).active)
    }

    @Test
    fun `the session survives being stored`() {
        val session = LidPlan.session(LidChoices(powerSaving = true, wifiOff = true), now, time = 1234)
        assertEquals(session, LidSession.decode(session.encode()))
        val empty = LidSession(9, LidReadings())
        assertEquals(empty, LidSession.decode(empty.encode()))
        assertNull(LidSession.decode(null))
        assertNull(LidSession.decode("perf=1"))
    }

    @Test
    fun `a stored session that still lists muted inputs reads without them`() {
        val stored = "closed=7;perf=2;fan=;wifi=1;bt=;air=;muted=buttons,controller;pending=0"
        assertEquals(LidSession(7, LidReadings(performance = 2, wifi = true)), LidSession.decode(stored))
    }

    @Test
    fun `opening the lid checks every saved value came back`() {
        val session = LidPlan.session(LidChoices(powerSaving = true, wifiOff = true), now, time = 1)
        assertEquals(emptyList<LidItem>(), LidPlan.notRestored(session, now))
        assertEquals(listOf(LidItem.WIFI), LidPlan.notRestored(session, now.copy(wifi = false)))
        assertEquals(listOf(LidItem.PERFORMANCE, LidItem.FAN, LidItem.WIFI), LidPlan.notRestored(session, LidReadings()))
    }

    @Test
    fun `the last result survives being stored, older ones too`() {
        val result = LidResult(closedAt = 10, openedAt = 20, notRestored = listOf(LidItem.WIFI, LidItem.FAN), sentBack = 3)
        assertEquals(result, LidResult.decode(result.encode()))
        val clean = LidResult(closedAt = 10, openedAt = 20, notRestored = emptyList())
        assertEquals(clean, LidResult.decode(clean.encode()))
        assertEquals(LidResult(10, 20, listOf(LidItem.WIFI)), LidResult.decode("10;20;WIFI,INPUTS"))
        assertNull(LidResult.decode("10;20"))
    }

    @Test
    fun `back to sleep only with the lid surely closed, the screen on and no external display`() {
        assertTrue(LidPlan.backToSleep(lidClosed = true, helperSaysOpen = false, screenOn = true, docked = false))
        assertFalse(LidPlan.backToSleep(lidClosed = null, helperSaysOpen = false, screenOn = true, docked = false))
        assertFalse(LidPlan.backToSleep(lidClosed = false, helperSaysOpen = false, screenOn = true, docked = false))
        assertFalse(LidPlan.backToSleep(lidClosed = true, helperSaysOpen = true, screenOn = true, docked = false))
        assertFalse(LidPlan.backToSleep(lidClosed = true, helperSaysOpen = false, screenOn = false, docked = false))
        assertFalse(LidPlan.backToSleep(lidClosed = true, helperSaysOpen = false, screenOn = true, docked = true))
    }

    @Test
    fun `docked means a display besides the two screens`() {
        assertFalse(LidPlan.docked(publicDisplays = 0))
        assertFalse(LidPlan.docked(publicDisplays = 1))
        assertTrue(LidPlan.docked(publicDisplays = 2))
    }

    @Test
    fun `the third wake in a row stays awake`() {
        val guard = WakeGuard()
        assertTrue(guard.onWake(0))
        guard.sentBack(10_000)
        assertTrue(guard.onWake(20_000))
        guard.sentBack(30_000)
        assertFalse(guard.onWake(40_000))
        // Let through once; the next one waits again.
        assertTrue(guard.onWake(500_000))
    }

    @Test
    fun `a wake more than a minute after going back to sleep starts over`() {
        val guard = WakeGuard()
        assertTrue(guard.onWake(0))
        guard.sentBack(10_000)
        assertTrue(guard.onWake(20_000))
        guard.sentBack(30_000)
        assertTrue(guard.onWake(30_000 + WakeGuard.WINDOW_MS + 1))
        guard.sentBack(200_000)
        assertTrue(guard.onWake(210_000))
        guard.sentBack(220_000)
        assertFalse(guard.onWake(230_000))
    }

    @Test
    fun `the way out also works with a wait of minutes`() {
        val guard = WakeGuard()
        val wait = 5 * LidPlan.MINUTE_MS
        assertTrue(guard.onWake(0))
        guard.sentBack(wait)
        assertTrue(guard.onWake(wait + 5_000))
        guard.sentBack(2 * wait + 5_000)
        assertFalse(guard.onWake(2 * wait + 10_000))
    }

    @Test
    fun `a reset forgets the wakes in a row`() {
        val guard = WakeGuard()
        guard.onWake(0)
        guard.sentBack(1_000)
        guard.onWake(2_000)
        guard.sentBack(3_000)
        guard.reset()
        assertTrue(guard.onWake(4_000))
    }

    @Test
    fun `the wait is shown in steps of its unit's slider`() {
        assertEquals(listOf(5, 5, 10, 10, 60, 60), listOf(0, 7, 8, 10, 60, 99).map(WaitUnit.SECONDS::clamp))
        assertEquals(listOf(1, 3, 15), listOf(0, 3, 20).map(WaitUnit.MINUTES::clamp))
        assertEquals(10_000L, LidPlan.sleepWaitMs(LidChoices()))
        assertEquals(3 * LidPlan.MINUTE_MS, LidPlan.sleepWaitMs(LidChoices(sleepWait = 3, sleepUnit = WaitUnit.MINUTES)))
    }

    @Test
    fun `switching the unit keeps the wait as near as it can`() {
        val tenSeconds = LidChoices(sleepWait = 10)
        assertEquals(LidChoices(sleepWait = 1, sleepUnit = WaitUnit.MINUTES), LidPlan.withUnit(tenSeconds, WaitUnit.MINUTES))
        val threeMinutes = LidChoices(sleepWait = 3, sleepUnit = WaitUnit.MINUTES)
        assertEquals(LidChoices(sleepWait = 60), LidPlan.withUnit(threeMinutes, WaitUnit.SECONDS))
        assertEquals(tenSeconds, LidPlan.withUnit(tenSeconds, WaitUnit.SECONDS))
        assertEquals(WaitUnit.MINUTES, WaitUnit.byId(WaitUnit.MINUTES.id))
        assertNull(WaitUnit.byId("x"))
    }

    @Test
    fun `Wi-Fi and Bluetooth count as on also while kept on in airplane mode`() {
        assertEquals(listOf(false, true, true, false, null), listOf(0, 1, 2, 3, null).map(LidPlan::radioOn))
    }

    @Test
    fun `the lid state is read from Android's window dump`() {
        assertEquals(true, LidPlan.lidClosed("mLidState=LID_ABSENT\n  mLidState=LID_CLOSED"))
        assertEquals(false, LidPlan.lidClosed("mDockMode=X mLidState=LID_ABSENT\nmDockMode=X mLidState=LID_OPEN"))
        assertNull(LidPlan.lidClosed("mLidState=LID_ABSENT"))
        assertNull(LidPlan.lidClosed(null))
    }

    @Test
    fun `the main menu counts the lid actions that are on`() {
        assertEquals(0, LidChoices().switchedOn)
        assertEquals(2, LidChoices(wifiOff = true, backToSleep = true).switchedOn)
        assertEquals(0, LidChoices(enabled = false, wifiOff = true).switchedOn)
    }

    @Test
    fun `save power runs later after a delay, or while media plays when asked`() {
        val wifi = LidChoices(wifiOff = true)
        assertFalse(LidPlan.savingLater(wifi, musicActive = true))
        assertTrue(LidPlan.savingLater(wifi.copy(delayMinutes = 5), musicActive = false))
        assertTrue(LidPlan.savingLater(wifi.copy(notWhileMedia = true), musicActive = true))
        assertFalse(LidPlan.savingLater(wifi.copy(notWhileMedia = true), musicActive = false))
        // Nothing to save, nothing to wait for.
        assertFalse(LidPlan.savingLater(LidChoices(backToSleep = true, delayMinutes = 5), musicActive = false))
        assertFalse(LidPlan.savingLater(wifi.copy(enabled = false, delayMinutes = 5), musicActive = false))
    }

    @Test
    fun `a waiting session changes nothing until its save power part runs`() {
        val choices = LidChoices(wifiOff = true, delayMinutes = 5)
        val waiting = LidPlan.session(choices, now, time = 1_000, pending = true)
        assertEquals(LidSession(1_000, LidReadings(), pending = true), waiting)
        assertEquals(emptyList<LidItem>(), LidPlan.notRestored(waiting, now.copy(wifi = false)))
        assertEquals(1_000 + 5 * LidPlan.MINUTE_MS, LidPlan.savingDueAt(waiting, choices))
        assertEquals(LidSession(1_000, LidReadings(wifi = true)), LidPlan.savingDone(waiting, choices, now))
    }

    @Test
    fun `a waiting session survives being stored, and older ones read as done`() {
        val waiting = LidSession(7, LidReadings(), pending = true)
        assertEquals(waiting, LidSession.decode(waiting.encode()))
        assertFalse(LidSession.decode("closed=7;perf=;fan=;wifi=1;bt=;air=;muted=-")!!.pending)
    }
}
