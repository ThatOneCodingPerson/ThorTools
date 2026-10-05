package io.github.thatonecodingperson.thortools.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenRulesTest {

    @Test
    fun `the bottom screen goes off only while the app is on the top screen`() {
        assertEquals(ScreenMode.BOTTOM_OFF, ScreenMode.forRule(BottomScreenRule.OFF, appOnTop = true))
        assertNull(ScreenMode.forRule(BottomScreenRule.OFF, appOnTop = false))
        assertEquals(ScreenMode.BOTH_ON, ScreenMode.forRule(BottomScreenRule.ON, appOnTop = false))
        assertNull(ScreenMode.forRule(null, appOnTop = true))
    }

    @Test
    fun `stored ids read back, unknown ones mean no change`() {
        AppRefreshRate.entries.forEach { assertEquals(it, AppRefreshRate.byId(it.id)) }
        BottomScreenRule.entries.forEach { assertEquals(it, BottomScreenRule.byId(it.id)) }
        assertNull(AppRefreshRate.byId("odintools#no_change"))
        assertNull(BottomScreenRule.byId(null))
        assertEquals(listOf(60, 120), AppRefreshRate.entries.map { it.hz })
    }
}
