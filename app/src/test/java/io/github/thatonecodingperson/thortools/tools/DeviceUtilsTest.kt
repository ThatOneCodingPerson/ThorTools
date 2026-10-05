package io.github.thatonecodingperson.thortools.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceUtilsTest {

    @Test
    fun `two built-in screens mean a Thor, whatever the codename says`() {
        assertEquals(DeviceType.THOR, DeviceUtils.detect(codename = "Q9", model = "AYN", device = "kona", internalDisplays = 2))
    }

    @Test
    fun `a Thor name is enough on its own`() {
        assertEquals(DeviceType.THOR, DeviceUtils.detect(codename = "Q9", model = "AYN Thor", device = "x"))
    }

    @Test
    fun `single-screen devices keep their codename mapping`() {
        assertEquals(DeviceType.ODIN2, DeviceUtils.detect(codename = "Q9", model = "Odin2", device = "q9"))
        assertEquals(DeviceType.RP4, DeviceUtils.detect(codename = "4.0P", model = "RP4", device = "x"))
        assertEquals(DeviceType.OTHER, DeviceUtils.detect(codename = "", model = "Pixel", device = "x"))
    }
}
