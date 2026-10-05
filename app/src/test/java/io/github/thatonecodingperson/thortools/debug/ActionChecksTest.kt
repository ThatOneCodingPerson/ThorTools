package io.github.thatonecodingperson.thortools.debug

import io.github.thatonecodingperson.thortools.actions.ThorAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionChecksTest {
    @Test
    fun `every action has exactly one check`() {
        assertEquals(ThorAction.entries.toSet(), ActionChecks.ALL.map { it.action }.toSet())
        assertEquals(ThorAction.entries.size, ActionChecks.ALL.size)
    }

    @Test
    fun `the risky checks ask first and stay out of run all`() {
        val risky = ActionChecks.ALL.filter { it.kind == CheckKind.RISKY }.map { it.action }.toSet()
        assertEquals(setOf(ThorAction.CLEAR_BACKGROUND, ThorAction.LOCK_SCREEN, ThorAction.TOGGLE_BOTTOM_SCREEN), risky)
        assertTrue(ActionChecks.runAll.none { it.kind == CheckKind.RISKY })
    }

    @Test
    fun `a setting check passes when the value changed, and nothing read is a fail`() {
        assertTrue(ActionChecks.passes(Expect.Changed, "xbox", "odin"))
        assertFalse(ActionChecks.passes(Expect.Changed, "xbox", "xbox"))
        assertFalse(ActionChecks.passes(Expect.Changed, "xbox", null))
        assertTrue(ActionChecks.passes(Expect.Is("open"), "closed", "open"))
        assertFalse(ActionChecks.passes(Expect.Asked, "a", "b"))
    }

    @Test
    fun `levels must move the right way on the right screens`() {
        assertTrue(ActionChecks.passes(Expect.Rises(listOf(0, 1)), "0.5,0.5", "0.6,0.6"))
        assertFalse(ActionChecks.passes(Expect.Rises(listOf(0, 1)), "0.5,0.5", "0.6,0.5"))
        assertTrue(ActionChecks.passes(Expect.Falls(listOf(1)), "0.5,0.5", "0.5,0.4"))
        assertTrue(ActionChecks.passes(Expect.Rises(listOf(0)), "7", "8"))
        assertFalse(ActionChecks.passes(Expect.Rises(listOf(1)), "0.5", "0.6"))
        assertTrue(ActionChecks.atLimit(1f, 0.05f, 0.95f))
        assertFalse(ActionChecks.atLimit(0.5f, 0.05f, 0.95f))
    }

    @Test
    fun `the refresh rate check wants both settings on the new rate`() {
        assertTrue(ActionChecks.passes(Expect.RefreshToggled, "120.0/120.0", "60.0/60.0"))
        assertFalse(ActionChecks.passes(Expect.RefreshToggled, "120.0/120.0", "60.0/120.0"))
        assertFalse(ActionChecks.passes(Expect.RefreshToggled, "60.0/60.0", "60.0/60.0"))
        assertTrue(ActionChecks.passes(Expect.RefreshToggled, "null/120.0", "60.0/60.0"))
    }

    @Test
    fun `the test app's screens and process decide the app checks`() {
        val onBottom = ActionChecks.testAppReading(setOf("bottom"), running = true)
        assertEquals("bottom|running", onBottom)
        assertEquals("top+bottom|stopped", ActionChecks.testAppReading(setOf("bottom", "top"), running = false))
        assertEquals("none|running", ActionChecks.testAppReading(emptySet(), running = true))
        assertTrue(ActionChecks.passes(Expect.AppOn(Where.BOTTOM), "none|stopped", onBottom))
        assertTrue(ActionChecks.passes(Expect.AppNotOn(Where.CONTROLLER), "top|running", "none|running", controllerOnTop = true))
        assertFalse(ActionChecks.passes(Expect.AppNotOn(Where.CONTROLLER), "top|running", "top|running", controllerOnTop = true))
        assertTrue(ActionChecks.passes(Expect.AppNotOn(Where.OTHER), "x", "top|running", controllerOnTop = true))
        assertTrue(ActionChecks.passes(Expect.AppGone, "bottom|running", "none|stopped"))
        assertFalse(ActionChecks.passes(Expect.AppGone, "bottom|running", "none|running"))
        assertTrue(ActionChecks.passes(Expect.AppSwapped, "bottom|running", "top|running"))
        assertFalse(ActionChecks.passes(Expect.AppSwapped, "bottom|running", "bottom|running"))
        assertTrue(ActionChecks.passes(Expect.NoApps, "top=a;bottom=b", ""))
        assertFalse(ActionChecks.passes(Expect.NoApps, "top=a", "top=a"))
    }

    @Test
    fun `screens resolve from where the controller is`() {
        assertEquals("top", ActionChecks.resolve(Where.CONTROLLER, controllerOnTop = true))
        assertEquals("bottom", ActionChecks.resolve(Where.OTHER, controllerOnTop = true))
        assertEquals("bottom", ActionChecks.resolve(Where.CONTROLLER, controllerOnTop = false))
        assertEquals("top", ActionChecks.resolve(Where.TOP, controllerOnTop = false))
    }
}
