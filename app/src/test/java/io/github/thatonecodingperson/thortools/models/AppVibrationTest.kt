package io.github.thatonecodingperson.thortools.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVibrationTest {
    @Test
    fun `ids are stored, so they stay unique and readable back`() {
        assertEquals(AppVibration.entries.size, AppVibration.entries.map { it.id }.toSet().size)
        AppVibration.entries.forEach { assertEquals(it, AppVibration.byId(it.id)) }
        assertNull(AppVibration.byId("no_change"))
        assertNull(AppVibration.byId(null))
    }

    @Test
    fun `strengths stay inside the driver's range, rising, and off has none`() {
        assertNull(AppVibration.OFF.strengthMv)
        val strengths = AppVibration.entries.mapNotNull { it.strengthMv }
        assertTrue(strengths.all { it in AppVibration.RANGE_MV })
        assertEquals(strengths.sorted(), strengths)
    }
}
