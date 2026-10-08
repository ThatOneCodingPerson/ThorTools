package io.github.thatonecodingperson.thortools.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PadCopyPlanTest {
    /** AYN's pad in the Xbox style, as sysfs and `getevent -p` showed it on the Thor. */
    private val keyCapability = "10 f00000000 0 0 0 7fff000000000000 0 40000000 c004000000000 0"
    private val getevent = """
        add device 1: /dev/input/event9
          name:     "Xbox Wireless Controller"
          events:
            KEY (0001): 0066  0072  0073  009e  0130  0131  0132  0133
            ABS (0003): 0000  : value 0, min -32767, max 32767, fuzz 0, flat 15, resolution 0
                        0001  : value 0, min -32767, max 32767, fuzz 0, flat 15, resolution 0
                        0009  : value 0, min 0, max 32767, fuzz 0, flat 0, resolution 0
                        0010  : value 0, min -1, max 1, fuzz 0, flat 0, resolution 0
          input props:
            <none>
    """.trimIndent()

    @Test
    fun `the pad's keys are read from its sysfs capability`() {
        val keys = PadCopyPlan.bits(keyCapability)
        assertEquals(listOf(102, 114, 115, 158) + (304..318) + listOf(544, 545, 546, 547, 580), keys)
        assertEquals(listOf(0, 1, 2, 5, 9, 10, 16, 17), PadCopyPlan.bits("30627"))
    }

    @Test
    fun `axes and their ranges are read from getevent, keys are not taken for axes`() {
        val axes = PadCopyPlan.axes(getevent)
        assertEquals(setOf(0, 1, 9, 16), axes.keys)
        assertEquals(PadCopyPlan.AbsInfo(-32767, 32767, flat = 15), axes[0])
        assertEquals(PadCopyPlan.AbsInfo(0, 32767), axes[9])
        assertEquals(PadCopyPlan.AbsInfo(-1, 1), axes[16])
    }

    @Test
    fun `the copy registers with the pad's name and ids, its keys and axes with their ranges`() {
        val line = PadCopyPlan.register(
            "Xbox Wireless Controller",
            0x2020,
            0x0112,
            listOf(305, 304),
            mapOf(16 to PadCopyPlan.AbsInfo(-1, 1)),
        )
        assertTrue(line.endsWith("\n"))
        assertTrue("\"name\":\"Xbox Wireless Controller\",\"vid\":8224,\"pid\":274" in line)
        assertTrue("{\"type\":101,\"data\":[304,305]}" in line)
        assertTrue("{\"type\":103,\"data\":[16]}" in line)
        assertTrue("{\"code\":16,\"info\":{\"value\":0,\"minimum\":-1,\"maximum\":1,\"fuzz\":0,\"flat\":0,\"resolution\":0}}" in line)
    }

    @Test
    fun `hidden axes are the pointing and scrolling sticks`() {
        assertEquals(setOf(0, 1), PadCopyPlan.hiddenAxes(left = true, right = false))
        assertEquals(setOf(2, 5), PadCopyPlan.hiddenAxes(left = false, right = true))
        assertEquals(emptySet<Int>(), PadCopyPlan.hiddenAxes(left = false, right = false))
    }

    @Test
    fun `the real pad is taken only with no key down and the sticks and D-pad at rest`() {
        assertTrue(PadCopyPlan.atRest(emptySet(), PadSample(leftX = 0.1f, rightY = -0.2f, leftTrigger = 1f)))
        assertFalse(PadCopyPlan.atRest(setOf(102), PadSample()))
        assertFalse(PadCopyPlan.atRest(emptySet(), PadSample(hatY = 1f)))
        assertFalse(PadCopyPlan.atRest(emptySet(), PadSample(leftY = 0.6f)))
    }

    @Test
    fun `a frame passes everything but the hidden axes, at its report`() {
        val frame = CopyFrame().apply { hide(setOf(0, 1)) }
        assertNull(frame.onEvent(3, 0, 20000))
        assertNull(frame.onEvent(3, 16, 1))
        assertNull(frame.onEvent(1, 304, 1))
        assertEquals("{\"id\":1,\"command\":\"inject\",\"events\":[3,16,1,1,304,1,0,0,0]}\n", frame.onEvent(0, 0, 0))
        assertEquals(setOf(304), frame.keysDown)
        assertEquals(setOf(16), frame.axesOut)
    }

    @Test
    fun `a frame with only hidden axes sends nothing, nor do key repeats`() {
        val frame = CopyFrame().apply { hide(setOf(2, 5)) }
        frame.onEvent(3, 5, -30000)
        frame.onEvent(1, 304, 2)
        assertNull(frame.onEvent(0, 0, 0))
    }

    @Test
    fun `a stick hidden while the copy held it out goes back to rest there at once`() {
        val frame = CopyFrame()
        frame.onEvent(3, 0, 20000)
        frame.onEvent(0, 0, 0)
        assertEquals("{\"id\":1,\"command\":\"inject\",\"events\":[3,0,0,0,0,0]}\n", frame.hide(setOf(0, 1)))
        assertTrue(frame.axesOut.isEmpty())
        // Hidden, it still follows the real pad, and nothing more is sent for it.
        frame.onEvent(3, 0, 25000)
        assertNull(frame.onEvent(0, 0, 0))
    }

    @Test
    fun `a stick shown again gets the real pad's value at once, one at rest sends nothing`() {
        val frame = CopyFrame().apply { hide(setOf(0, 1)) }
        frame.onEvent(3, 0, 25000)
        frame.onEvent(0, 0, 0)
        assertEquals("{\"id\":1,\"command\":\"inject\",\"events\":[3,0,25000,0,0,0]}\n", frame.hide(emptySet()))
        assertEquals(setOf(0), frame.axesOut)
        assertNull(frame.hide(emptySet()))
    }

    @Test
    fun `lost events drop the half frame`() {
        val frame = CopyFrame()
        frame.onEvent(1, 305, 1)
        assertNull(frame.onEvent(0, 3, 0))
        assertNull(frame.onEvent(0, 0, 0))
    }
}
