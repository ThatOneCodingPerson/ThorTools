package io.github.thatonecodingperson.thortools.debug

import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.hotkeys.Hotkey
import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import io.github.thatonecodingperson.thortools.hotkeys.PressKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugReportTest {
    @Test
    fun `action lines say what changed, whether it went back, and a summary`() {
        val section = DebugReport.actionSection(
            listOf(
                CheckResult(ThorAction.CYCLE_FAN, CheckState.PASS, "1", "4", restored = true),
                CheckResult(ThorAction.CLEAR_BACKGROUND, CheckState.FAIL, "none|running", "none|running", detail = "still running"),
                CheckResult(ThorAction.SWAP_SCREENS, CheckState.SKIPPED, detail = "no second screen"),
            ),
            time = "now",
        )
        val lines = section.lines()
        assertEquals("time: now", lines[0])
        assertEquals("PASS perf_cycle_fan: 1 -> 4; restored=yes", lines[1])
        assertEquals("FAIL system_clear_background: none|running -> none|running; still running", lines[2])
        assertEquals("SKIPPED screens_swap; no second screen", lines[3])
        assertEquals("summary: 1 pass, 1 fail, 1 skipped", lines[4])
    }

    @Test
    fun `each tool replaces its own section and keeps the other`() {
        val first = DebugReport.merge(null, listOf("app: 1"), DebugReport.ACTIONS, "PASS a")
        assertTrue(first.startsWith("Thor Tools debug report\napp: 1\n"))
        val second = DebugReport.merge(first, listOf("app: 2"), DebugReport.HOTKEYS, "SEEN b")
        val third = DebugReport.merge(second, listOf("app: 3"), DebugReport.ACTIONS, "FAIL a")
        assertEquals(mapOf(DebugReport.ACTIONS to "FAIL a", DebugReport.HOTKEYS to "SEEN b"), DebugReport.sections(third))
        assertTrue(third.contains("app: 3") && !third.contains("app: 2"))
        assertTrue(third.indexOf("=== Action check ===") < third.indexOf("=== Hotkey check ==="))
    }

    @Test
    fun `the hotkey section lists the rows and the presses`() {
        val hotkey = Hotkey(PadButton.HOME, null, PressKind.DOUBLE, ThorAction.SWAP_SCREENS)
        val section = DebugReport.hotkeySection(
            HotkeyType.DOUBLES,
            listOf(HotkeyRow(hotkey, seenAt = 4)),
            listOf(HotkeyEvent.Matched(hotkey, ran = false, time = 4)),
            runActions = false,
            time = "now",
        )
        assertTrue(section.contains("SEEN double home -> screens_swap"))
        assertTrue(section.contains("press @4: double home -> screens_swap (not run)"))
        assertTrue(section.endsWith("summary: 1 of 1 recognised"))
    }
}
