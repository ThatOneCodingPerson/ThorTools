package io.github.thatonecodingperson.thortools.lid

import io.github.thatonecodingperson.thortools.input.InputNode
import io.github.thatonecodingperson.thortools.input.MuteTargets
import io.github.thatonecodingperson.thortools.input.ThorPad
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
        assertEquals(LidSession(5, LidReadings(), emptySet()), LidPlan.session(LidChoices(), now, time = 5))
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
    fun `muted groups follow the switches, and the master switch turns them off`() {
        val choices = LidChoices(muteButtons = true, muteTouch = true)
        assertEquals(setOf(InputGroup.BUTTONS, InputGroup.TOUCH), LidPlan.session(choices, now, time = 1).muted)
        assertEquals(emptySet<InputGroup>(), LidPlan.session(choices.copy(enabled = false), now, time = 1).muted)
        assertFalse(choices.copy(enabled = false).active)
        assertTrue(choices.active)
    }

    @Test
    fun `the session survives being stored`() {
        val session = LidPlan.session(LidChoices(powerSaving = true, wifiOff = true, muteController = true), now, time = 1234)
        assertEquals(session, LidSession.decode(session.encode()))
        val empty = LidSession(9, LidReadings(), emptySet())
        assertEquals(empty, LidSession.decode(empty.encode()))
        assertNull(LidSession.decode(null))
        assertNull(LidSession.decode("perf=1"))
    }

    @Test
    fun `opening the lid checks every saved value came back`() {
        val session = LidPlan.session(LidChoices(powerSaving = true, wifiOff = true, muteTouch = true), now, time = 1)
        assertEquals(emptyList<LidItem>(), LidPlan.notRestored(session, now, mutedInputs = 0))
        val wifiStillOff = now.copy(wifi = false)
        assertEquals(listOf(LidItem.WIFI), LidPlan.notRestored(session, wifiStillOff, mutedInputs = 0))
        val unread = LidReadings()
        val everything = listOf(LidItem.PERFORMANCE, LidItem.FAN, LidItem.WIFI, LidItem.INPUTS)
        assertEquals(everything, LidPlan.notRestored(session, unread, mutedInputs = null))
        assertEquals(listOf(LidItem.INPUTS), LidPlan.notRestored(session, now, mutedInputs = 2))
        val nothingMuted = LidPlan.session(LidChoices(wifiOff = true), now, time = 1)
        assertEquals(emptyList<LidItem>(), LidPlan.notRestored(nothingMuted, now, mutedInputs = null))
    }

    @Test
    fun `the last result survives being stored`() {
        val result = LidResult(closedAt = 10, openedAt = 20, notRestored = listOf(LidItem.WIFI, LidItem.INPUTS))
        assertEquals(result, LidResult.decode(result.encode()))
        val clean = LidResult(closedAt = 10, openedAt = 20, notRestored = emptyList())
        assertEquals(clean, LidResult.decode(clean.encode()))
        assertNull(LidResult.decode("10;20"))
    }

    @Test
    fun `back to sleep gives way on the third wake within a minute`() {
        val guard = WakeGuard()
        assertTrue(guard.onWake(0))
        assertTrue(guard.onWake(10_000))
        assertFalse(guard.onWake(20_000))
        assertTrue(guard.onWake(30_000))
        assertTrue(guard.onWake(200_000))
        assertTrue(guard.onWake(300_000))
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
    fun `muting never touches the power key or the lid sensor`() {
        val abs = ThorPad.parseAbs("30627")
        val nodes = listOf(
            InputNode("/dev/input/event0", "gpio-keys", 0, 0),
            InputNode("/dev/input/event1", "pmic_pwrkey", 0, 0),
            InputNode("/dev/input/event2", "pmic_resin", 0, 0),
            InputNode("/dev/input/event3", "hall_switch", 0, 0),
            InputNode("/dev/input/event5", "fts_ts_3", 0, 0),
            InputNode("/dev/input/event6", "fts_ts", 0, 0),
            InputNode("/dev/input/event9", "Xbox Wireless Controller", 0x2020, 0x0112, abs),
        )
        val all = MuteTargets.pick(nodes, InputGroup.entries.toSet()).map { it.name }
        assertEquals(listOf("gpio-keys", "pmic_resin", "fts_ts_3", "fts_ts", "Xbox Wireless Controller"), all)
        assertTrue(all.none { it in MuteTargets.NEVER })
        assertEquals(listOf("Xbox Wireless Controller"), MuteTargets.pick(nodes, setOf(InputGroup.CONTROLLER)).map { it.name })
        assertEquals("/sys/class/input/event9/device/inhibited", MuteTargets.inhibitedFile(nodes.last()))
    }

    @Test
    fun `input groups travel as one argument`() {
        val groups = setOf(InputGroup.TOUCH, InputGroup.BUTTONS)
        assertEquals(groups, InputGroup.decode(InputGroup.encode(groups)))
        assertEquals("-", InputGroup.encode(emptySet()))
        assertEquals(emptySet<InputGroup>(), InputGroup.decode("-"))
    }

    @Test
    fun `the main menu counts the lid actions that are on`() {
        assertEquals(0, LidChoices().switchedOn)
        assertEquals(3, LidChoices(wifiOff = true, muteTouch = true, backToSleep = true).switchedOn)
        assertEquals(0, LidChoices(enabled = false, wifiOff = true).switchedOn)
    }

    @Test
    fun `chosen apps keep the buttons on, the other mutes stay`() {
        val choices = LidChoices(muteButtons = true, muteTouch = true, keepButtonsFor = setOf("music"))
        assertEquals(setOf(InputGroup.TOUCH), LidPlan.mutes(choices, setOf("game", "music")))
        assertEquals(setOf(InputGroup.BUTTONS, InputGroup.TOUCH), LidPlan.mutes(choices, setOf("game")))
        assertEquals(setOf(InputGroup.BUTTONS, InputGroup.TOUCH), LidPlan.mutes(choices, emptySet()))
        assertEquals(emptySet<InputGroup>(), LidPlan.mutes(choices.copy(enabled = false), setOf("game")))
    }

    @Test
    fun `save power runs later after a delay, or while media plays when asked`() {
        val wifi = LidChoices(wifiOff = true)
        assertFalse(LidPlan.savingLater(wifi, musicActive = true))
        assertTrue(LidPlan.savingLater(wifi.copy(delayMinutes = 5), musicActive = false))
        assertTrue(LidPlan.savingLater(wifi.copy(notWhileMedia = true), musicActive = true))
        assertFalse(LidPlan.savingLater(wifi.copy(notWhileMedia = true), musicActive = false))
        // Nothing to save, nothing to wait for: only the mutes, at once.
        assertFalse(LidPlan.savingLater(LidChoices(muteTouch = true, delayMinutes = 5), musicActive = false))
        assertFalse(LidPlan.savingLater(wifi.copy(enabled = false, delayMinutes = 5), musicActive = false))
    }

    @Test
    fun `a waiting session changes nothing until its save power part runs`() {
        val choices = LidChoices(wifiOff = true, muteTouch = true, delayMinutes = 5)
        val waiting = LidPlan.session(choices, now, time = 1_000, pending = true)
        assertEquals(LidSession(1_000, LidReadings(), setOf(InputGroup.TOUCH), pending = true), waiting)
        assertEquals(emptyList<LidItem>(), LidPlan.notRestored(waiting, now.copy(wifi = false), mutedInputs = 0))
        assertEquals(1_000 + 5 * LidPlan.MINUTE_MS, LidPlan.savingDueAt(waiting, choices))
        val done = LidPlan.savingDone(waiting, choices, now)
        assertEquals(LidSession(1_000, LidReadings(wifi = true), setOf(InputGroup.TOUCH)), done)
    }

    @Test
    fun `a waiting session survives being stored, and older ones read as done`() {
        val waiting = LidSession(7, LidReadings(), setOf(InputGroup.BUTTONS), pending = true)
        assertEquals(waiting, LidSession.decode(waiting.encode()))
        assertFalse(LidSession.decode("closed=7;perf=;fan=;wifi=1;bt=;air=;muted=-")!!.pending)
    }
}
