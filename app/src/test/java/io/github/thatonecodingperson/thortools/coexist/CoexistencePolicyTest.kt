package io.github.thatonecodingperson.thortools.coexist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoexistencePolicyTest {

    @Test
    fun `features keep their own default without OdinTools`() {
        assertTrue(CoexistencePolicy.resolve(defaultEnabled = true, stored = null, odinToolsInstalled = false))
        assertFalse(CoexistencePolicy.resolve(defaultEnabled = false, stored = null, odinToolsInstalled = false))
    }

    @Test
    fun `overlapping features default off next to OdinTools`() {
        assertFalse(CoexistencePolicy.resolve(defaultEnabled = true, stored = null, odinToolsInstalled = true))
        assertFalse(CoexistencePolicy.resolve(defaultEnabled = false, stored = null, odinToolsInstalled = true))
    }

    @Test
    fun `an explicit choice always wins`() {
        assertTrue(CoexistencePolicy.resolve(defaultEnabled = false, stored = true, odinToolsInstalled = true))
        assertFalse(CoexistencePolicy.resolve(defaultEnabled = true, stored = false, odinToolsInstalled = false))
    }

    @Test
    fun `only switching on next to OdinTools needs confirmation`() {
        assertTrue(CoexistencePolicy.needsConfirmation(turningOn = true, odinToolsInstalled = true))
        assertFalse(CoexistencePolicy.needsConfirmation(turningOn = false, odinToolsInstalled = true))
        assertFalse(CoexistencePolicy.needsConfirmation(turningOn = true, odinToolsInstalled = false))
    }

    @Test
    fun `a guard only switch is on without OdinTools, whatever was stored next to it`() {
        assertTrue(CoexistencePolicy.resolve(defaultEnabled = true, stored = false, odinToolsInstalled = false, guardOnly = true))
        assertFalse(CoexistencePolicy.resolve(defaultEnabled = true, stored = false, odinToolsInstalled = true, guardOnly = true))
        assertFalse(CoexistencePolicy.resolve(defaultEnabled = true, stored = null, odinToolsInstalled = true, guardOnly = true))
    }
}
