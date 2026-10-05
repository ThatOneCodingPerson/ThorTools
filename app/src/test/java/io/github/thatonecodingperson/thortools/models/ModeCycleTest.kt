package io.github.thatonecodingperson.thortools.models

import org.junit.Assert.assertEquals
import org.junit.Test

class ModeCycleTest {

    @Test
    fun `controller style cycles and skips the disabled style`() {
        assertEquals(ControllerStyle.Odin, ControllerStyle.next(ControllerStyle.Xbox, disabled = null))
        assertEquals(ControllerStyle.Disconnect, ControllerStyle.next(ControllerStyle.Odin, disabled = null))
        assertEquals(ControllerStyle.Xbox, ControllerStyle.next(ControllerStyle.Disconnect, disabled = null))
        assertEquals(ControllerStyle.Disconnect, ControllerStyle.next(ControllerStyle.Xbox, disabled = ControllerStyle.Odin))
        assertEquals(ControllerStyle.Odin, ControllerStyle.next(ControllerStyle.Unknown, disabled = ControllerStyle.Xbox))
    }

    @Test
    fun `the L2 and R2 setting value maps to its mode, anything else is Unknown`() {
        assertEquals(L2R2Style.Analog, L2R2Style.fromSettingsValue(0))
        assertEquals(L2R2Style.Digital, L2R2Style.fromSettingsValue(1))
        assertEquals(L2R2Style.Both, L2R2Style.fromSettingsValue(2))
        assertEquals(L2R2Style.Unknown, L2R2Style.fromSettingsValue(-1))
        assertEquals(L2R2Style.Unknown, L2R2Style.fromSettingsValue(7))
        assertEquals(listOf(L2R2Style.Analog, L2R2Style.Digital, L2R2Style.Both), L2R2Style.choices)
        assertEquals(listOf(ControllerStyle.Xbox, ControllerStyle.Odin, ControllerStyle.Disconnect), ControllerStyle.choices)
    }

    @Test
    fun `layout toggle switches between Xbox and Standard`() {
        assertEquals(ControllerStyle.Odin, ControllerStyle.toggledLayout(ControllerStyle.Xbox))
        assertEquals(ControllerStyle.Xbox, ControllerStyle.toggledLayout(ControllerStyle.Odin))
        assertEquals(ControllerStyle.Xbox, ControllerStyle.toggledLayout(ControllerStyle.Disconnect))
    }

    @Test
    fun `L2 R2 mode cycles and skips the disabled mode`() {
        assertEquals(L2R2Style.Digital, L2R2Style.next(L2R2Style.Analog, disabled = null))
        assertEquals(L2R2Style.Both, L2R2Style.next(L2R2Style.Analog, disabled = L2R2Style.Digital))
        assertEquals(L2R2Style.Analog, L2R2Style.next(L2R2Style.Both, disabled = null))
    }

    @Test
    fun `performance and fan modes cycle, fan never turns off`() {
        assertEquals(PerfMode.Performance, PerfMode.next(PerfMode.Standard))
        assertEquals(PerfMode.Standard, PerfMode.next(PerfMode.HighPerformance))
        assertEquals(FanMode.Smart, FanMode.next(FanMode.Quiet))
        assertEquals(FanMode.Quiet, FanMode.next(FanMode.Sport))
        assertEquals(FanMode.Quiet, FanMode.next(FanMode.Off))
    }

    @Test
    fun `refresh rate toggles between 60 and 120 Hz`() {
        assertEquals(RefreshRate.HIGH, RefreshRate.toggled(60f))
        assertEquals(RefreshRate.LOW, RefreshRate.toggled(120f))
        assertEquals(RefreshRate.LOW, RefreshRate.toggled(90.1f))
    }
}
