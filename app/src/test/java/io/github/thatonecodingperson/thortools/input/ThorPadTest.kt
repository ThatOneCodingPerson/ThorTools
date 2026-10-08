package io.github.thatonecodingperson.thortools.input

import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import io.github.thatonecodingperson.thortools.input.PadDirections.Change
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The root helper's pad reader: which device is the Thor's pad, its raw axes, and watching from a press on. */
class ThorPadTest {
    /** ABS X, Y, Z, RZ, HAT0X, HAT0Y and the triggers, as AYN's pad reports them. */
    private val padAbs = ThorPad.parseAbs("30627")

    private fun node(name: String, vendor: Int, product: Int, abs: Long = padAbs, path: String = "/dev/input/event5") =
        InputNode(path, name, vendor, product, abs)

    @Test
    fun `sysfs abs bits are read from the last word`() {
        assertEquals(0x30627L, ThorPad.parseAbs("30627\n"))
        assertEquals(0x30027L, ThorPad.parseAbs("0 30027"))
        assertEquals(0L, ThorPad.parseAbs(""))
        assertEquals(0L, ThorPad.parseAbs("zz"))
    }

    @Test
    fun `AYN's pad is found in either layout`() {
        val touch = node("fts_ts", 0, 0, abs = 0L, path = "/dev/input/event2")
        val xbox = node("Xbox Wireless Controller", 0x2020, 0x0112)
        val standard = node("Odin Controller", 0x2020, 0x0111)
        assertEquals(xbox, ThorPad.pick(listOf(touch, xbox)))
        assertEquals(standard, ThorPad.pick(listOf(touch, standard)))
    }

    @Test
    fun `an external pad AYN re-emits under its own name is not the Thor's`() {
        val external = node("Xbox Wireless Controller", 0x045e, 0x0b13, path = "/dev/input/event9")
        val reEmitted = node("Xbox Wireless Controller", 0x2020, 0x0111, path = "/dev/input/event10")
        assertNull(ThorPad.pick(listOf(external, reEmitted)))
        val own = node("Odin Controller", 0x2020, 0x0111, path = "/dev/input/event5")
        assertEquals(own, ThorPad.pick(listOf(external, reEmitted, own)))
    }

    @Test
    fun `Thor Tools' copy of the pad is never taken for AYN's, in either style, listed first or not`() {
        val copy = InputNode("/dev/input/event9", "Xbox Wireless Controller", 0x2020, 0x0112, padAbs, version = 1)
        val pad = InputNode("/dev/input/event13", "Xbox Wireless Controller", 0x2020, 0x0112, padAbs, version = 0)
        assertEquals(pad, ThorPad.pick(listOf(copy, pad)))
        assertNull(ThorPad.pick(listOf(copy)))
        val standardCopy = InputNode("/dev/input/event9", "Odin Controller", 0x2020, 0x0111, padAbs, version = 1)
        assertNull(ThorPad.pick(listOf(standardCopy)))
        assertEquals("/sys/class/input/event13", pad.sysfs)
    }

    @Test
    fun `AYN's virtual mouse has no sticks and is left out`() {
        val mouse = node("ODIN Station Virtual Mouse", 0x2020, 0x0112, abs = 0L)
        assertNull(ThorPad.pick(listOf(mouse)))
    }

    @Test
    fun `raw axes become a sample like Android's`() {
        val pad = RawPad()
        pad.onAbs(ThorPad.ABS_RZ, -32767)
        pad.onAbs(ThorPad.ABS_X, 16384)
        pad.onAbs(ThorPad.ABS_HAT0X, 1)
        pad.onAbs(40, 5)
        val sample = pad.sample()
        assertEquals(-1f, sample.rightY, 0.001f)
        assertEquals(0.5f, sample.leftX, 0.001f)
        assertEquals(1f, sample.hatX, 0.001f)
        assertEquals(0f, sample.rightX, 0.001f)
        pad.reset()
        assertEquals(PadSample(), pad.sample())
    }

    @Test
    fun `watching from the press, the first flick counts at once`() {
        val pad = PadDirections().also { it.start(PadSample()) }
        assertEquals(emptyList<Change>(), pad.update(PadSample(rightY = -0.3f)))
        assertEquals(listOf(Change(PadButton.RSTICK_UP, true)), pad.update(PadSample(rightY = -0.8f)))
        assertEquals(listOf(Change(PadButton.RSTICK_UP, false)), pad.update(PadSample(rightY = -0.1f)))
    }

    @Test
    fun `a stick held before the press counts only after it came back`() {
        val pad = PadDirections().also { it.start(PadSample(leftY = -0.9f)) }
        assertEquals(emptyList<Change>(), pad.update(PadSample(leftY = -1f)))
        assertEquals(emptyList<Change>(), pad.update(PadSample(leftY = -0.2f)))
        assertEquals(listOf(Change(PadButton.LSTICK_UP, true)), pad.update(PadSample(leftY = -0.9f)))
    }

    @Test
    fun `a D-pad held before the press counts only after it was let go`() {
        val pad = PadDirections().also { it.start(PadSample(hatX = 1f)) }
        assertEquals(emptyList<Change>(), pad.update(PadSample(hatX = 1f, rightY = 0f)))
        assertEquals(emptyList<Change>(), pad.update(PadSample()))
        assertEquals(listOf(Change(PadButton.DPAD_RIGHT, true)), pad.update(PadSample(hatX = 1f)))
    }

    @Test
    fun `a D-pad pressed after the press counts at once`() {
        val pad = PadDirections().also { it.start(PadSample()) }
        assertEquals(listOf(Change(PadButton.DPAD_UP, true)), pad.update(PadSample(hatY = -1f)))
    }

    @Test
    fun `without a known start the catcher's first D-pad reading is still a press`() {
        val pad = PadDirections()
        assertEquals(listOf(Change(PadButton.DPAD_LEFT, true)), pad.update(PadSample(hatX = -1f)))
    }
}
