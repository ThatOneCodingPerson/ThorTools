package io.github.thatonecodingperson.thortools.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStepsTest {

    @Test
    fun `the wizard opens by itself only on a fresh install that hasn't finished it`() {
        assertTrue(FirstRun.showWizard(setupDone = false, firstInstallTime = 10, lastUpdateTime = 10))
        assertFalse(FirstRun.showWizard(setupDone = true, firstInstallTime = 10, lastUpdateTime = 10))
        assertFalse(FirstRun.showWizard(setupDone = false, firstInstallTime = 10, lastUpdateTime = 20))
    }

    @Test
    fun `steps go forward and back and stop at the ends`() {
        assertEquals(WizardStep.ACCESS, WizardStep.WELCOME.next())
        assertNull(WizardStep.WELCOME.previous())
        assertNull(WizardStep.DONE.next())
        assertEquals(WizardStep.entries.size, WizardStep.DONE.number)
    }
}
