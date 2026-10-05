package io.github.thatonecodingperson.thortools.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessReportTest {

    @Test
    fun `only a missing requirement needs attention`() {
        assertFalse(AccessReport.of(AccessCheck.ROOT to AccessState.OK, AccessCheck.BATTERY to AccessState.NOT_NEEDED).needsAttention)
        assertFalse(AccessReport.of(AccessCheck.INPUT_HELPER to AccessState.UNKNOWN).needsAttention)
        assertTrue(AccessReport.of(AccessCheck.ROOT to AccessState.OK, AccessCheck.NOTIFICATIONS to AccessState.MISSING).needsAttention)
    }

    @Test
    fun `requirements that don't apply are not needed`() {
        assertEquals(AccessState.NOT_NEEDED, AccessReport.okIfNeeded(needed = false, present = false))
        assertEquals(AccessState.MISSING, AccessReport.okIfNeeded(needed = true, present = false))
        assertEquals(AccessState.OK, AccessReport.okIfNeeded(needed = true, present = true))
    }

    @Test
    fun `unchecked items read as unknown`() {
        assertEquals(AccessState.UNKNOWN, AccessReport.of().state(AccessCheck.ACCESSIBILITY))
    }
}
