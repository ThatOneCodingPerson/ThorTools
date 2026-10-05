package io.github.thatonecodingperson.thortools.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleChoiceTest {
    @Test
    fun `one mode can be taken out of the switching`() {
        assertEquals("both", CycleChoice.toggled(null, "both", include = false))
        assertTrue(CycleChoice.canSkip(null, "both"))
    }

    @Test
    fun `a second one can't, so two always stay`() {
        assertFalse(CycleChoice.canSkip("both", "analog"))
        assertEquals("both", CycleChoice.toggled("both", "analog", include = false))
    }

    @Test
    fun `putting the skipped one back clears it, putting another back changes nothing`() {
        assertNull(CycleChoice.toggled("both", "both", include = true))
        assertEquals("both", CycleChoice.toggled("both", "analog", include = true))
        assertNull(CycleChoice.toggled(null, "analog", include = true))
    }
}
