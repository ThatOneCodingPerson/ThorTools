package io.github.thatonecodingperson.thortools.lid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LidPlanTest {
    private val bothOn = Radios(wifi = true, bluetooth = true)

    @Test
    fun `the lid is read from the kernel's switch mask`() {
        assertEquals(true, LidPlan.lidClosed("0001"))
        assertEquals(false, LidPlan.lidClosed("0000"))
        assertEquals(true, LidPlan.lidClosed("0003\n"))
        assertEquals(false, LidPlan.lidClosed("0002"))
        assertEquals(true, LidPlan.lidClosed("could not get something\n0001"))
        assertNull(LidPlan.lidClosed(""))
        assertNull(LidPlan.lidClosed("add"))
        assertNull(LidPlan.lidClosed("getevent: No such file"))
        assertNull(LidPlan.lidClosed(null))
    }

    @Test
    fun `the lid command finds the sensor by name and asks the kernel`() {
        assertTrue("hall_switch" in LidPlan.LID_STATE)
        assertTrue("getevent -S" in LidPlan.LID_STATE)
        assertFalse("dumpsys" in LidPlan.LID_STATE)
    }

    @Test
    fun `closing turns off only the chosen radios that are on`() {
        assertEquals(emptySet<LidItem>(), LidPlan.toTurnOff(LidChoices(), bothOn))
        assertEquals(setOf(LidItem.WIFI, LidItem.BLUETOOTH), LidPlan.toTurnOff(LidChoices(wifiOff = true, bluetoothOff = true), bothOn))
        assertEquals(setOf(LidItem.BLUETOOTH), LidPlan.toTurnOff(LidChoices(bluetoothOff = true), bothOn))
        val wifiAlreadyOff = Radios(wifi = false, bluetooth = true)
        assertEquals(setOf(LidItem.BLUETOOTH), LidPlan.toTurnOff(LidChoices(wifiOff = true, bluetoothOff = true), wifiAlreadyOff))
        val unread = Radios(wifi = null, bluetooth = null)
        assertEquals(emptySet<LidItem>(), LidPlan.toTurnOff(LidChoices(wifiOff = true, bluetoothOff = true), unread))
    }

    @Test
    fun `opening checks that what was turned off is back on`() {
        val session = LidSession(1, setOf(LidItem.WIFI, LidItem.BLUETOOTH))
        assertEquals(emptyList<LidItem>(), LidPlan.notBack(session, bothOn))
        assertEquals(listOf(LidItem.BLUETOOTH), LidPlan.notBack(session, Radios(wifi = true, bluetooth = false)))
        assertEquals(listOf(LidItem.WIFI, LidItem.BLUETOOTH), LidPlan.notBack(session, Radios(wifi = null, bluetooth = null)))
        // Only what the closing turned off has to come back.
        assertEquals(emptyList<LidItem>(), LidPlan.notBack(LidSession(1, setOf(LidItem.WIFI)), Radios(wifi = true, bluetooth = false)))
    }

    @Test
    fun `the session survives being stored`() {
        val session = LidSession(1234, setOf(LidItem.WIFI))
        assertEquals(session, LidSession.decode(session.encode()))
        val nothing = LidSession(9, emptySet())
        assertEquals(nothing, LidSession.decode(nothing.encode()))
        assertNull(LidSession.decode(null))
        assertNull(LidSession.decode("wifi=1"))
    }

    @Test
    fun `an older stored session reads as what it turned off`() {
        val older = "closed=7;perf=2;fan=4;wifi=1;bt=;air=0;muted=controller;pending=0"
        assertEquals(LidSession(7, setOf(LidItem.WIFI)), LidSession.decode(older))
        assertEquals(OldLidChanges(performance = 2, fan = 4, airplane = false), OldLidChanges.decode(older))
        assertNull(OldLidChanges.decode("closed=7;perf=;fan=;wifi=1;bt=1;air=;pending=1"))
        assertNull(OldLidChanges.decode(LidSession(7, setOf(LidItem.WIFI)).encode()))
        assertNull(OldLidChanges.decode(null))
    }

    @Test
    fun `the last result survives being stored, older ones too`() {
        val result = LidResult(closedAt = 10, openedAt = 20, notBack = listOf(LidItem.WIFI), sentBack = 3)
        assertEquals(result, LidResult.decode(result.encode()))
        val clean = LidResult(closedAt = 10, openedAt = 20, notBack = emptyList())
        assertEquals(clean, LidResult.decode(clean.encode()))
        assertEquals(LidResult(10, 20, listOf(LidItem.BLUETOOTH)), LidResult.decode("10;20;FAN,BLUETOOTH,INPUTS"))
        assertNull(LidResult.decode("10;20"))
    }

    @Test
    fun `Wi-Fi and Bluetooth count as on also while kept on in airplane mode`() {
        assertEquals(listOf(false, true, true, false, null), listOf(0, 1, 2, 3, null).map(LidPlan::radioOn))
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
        val wait = 5 * 60_000L
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
        assertEquals(3 * 60_000L, LidPlan.sleepWaitMs(LidChoices(sleepWait = 3, sleepUnit = WaitUnit.MINUTES)))
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
    fun `the main menu counts the lid switches that are on`() {
        assertEquals(0, LidChoices().switchedOn)
        assertEquals(2, LidChoices(wifiOff = true, backToSleep = true).switchedOn)
        assertEquals(3, LidChoices(wifiOff = true, bluetoothOff = true, backToSleep = true).switchedOn)
    }
}
