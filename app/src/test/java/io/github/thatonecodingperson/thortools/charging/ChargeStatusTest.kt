package io.github.thatonecodingperson.thortools.charging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeStatusTest {
    @Test
    fun `the charger kind comes from the kernel's USB type`() {
        assertEquals(ChargerKind.COMPUTER, ChargerKind.from("SDP"))
        assertEquals(ChargerKind.COMPUTER_CHARGING, ChargerKind.from("CDP\n"))
        assertEquals(ChargerKind.WALL, ChargerKind.from("DCP"))
        assertEquals(ChargerKind.QUICK_CHARGE, ChargerKind.from("HVDCP_3"))
        assertEquals(ChargerKind.PD, ChargerKind.from("PD"))
        assertEquals(ChargerKind.PD, ChargerKind.from("pd_pps"))
        assertEquals(ChargerKind.NONE, ChargerKind.from("Unknown"))
        assertEquals(ChargerKind.NONE, ChargerKind.from(null))
        assertEquals(ChargerKind.OTHER, ChargerKind.from("BrickID"))
    }

    @Test
    fun `watts from what the charger offered and what comes in`() {
        // The owner's PC port: 900 mA at 5 V offered, 450 mA at 4.74 V coming in.
        assertEquals(4.5f, ChargeStatus.offeredWatts(900_000, 5_000_000)!!, 0.01f)
        assertEquals(4.5f, ChargeStatus.offeredWatts(900_000, -1)!!, 0.01f)
        assertNull(ChargeStatus.offeredWatts(-1, 5_000_000))
        assertEquals(2.13f, ChargeStatus.inputWatts(4_740_000, 450_000)!!, 0.01f)
        assertEquals(2.13f, ChargeStatus.inputWatts(4_740_000, -450_000)!!, 0.01f)
        assertNull(ChargeStatus.inputWatts(null, 450_000))
        assertNull(ChargeStatus.inputWatts(0, 450_000))
    }

    @Test
    fun `AYN's 80 percent limit makes a higher top level unreachable`() {
        assertTrue(ChargeStatus.limitUnreachable(aynLimitOn = true, topLevel = 90))
        assertFalse(ChargeStatus.limitUnreachable(aynLimitOn = true, topLevel = 80))
        assertFalse(ChargeStatus.limitUnreachable(aynLimitOn = false, topLevel = 90))
    }

    @Test
    fun `the alert names what was on, in order`() {
        assertEquals(emptyList<AlertNote>(), ChargeStatus.notes(ChargeAlertContext()))
        assertEquals(
            listOf(AlertNote.AYN_LIMIT, AlertNote.AUTOMATION, AlertNote.WEAK_CHARGER),
            ChargeStatus.notes(ChargeAlertContext(aynLimit = true, automation = true, weakCharger = true)),
        )
    }

    @Test
    fun `node readings keep their places, unreadable ones are null`() {
        assertEquals(listOf("SDP", null, "100"), ChargeStatus.fields("SDP| |100", 3))
        assertEquals(listOf("SDP", null, null), ChargeStatus.fields("SDP\nmore", 3))
        assertEquals(listOf(null, null), ChargeStatus.fields(null, 2))
    }
}
