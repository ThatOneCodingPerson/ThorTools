package io.github.thatonecodingperson.thortools.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherLookupTest {

    private val dump = """
        ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)
        Display #0 (activities from top to bottom):
          * Task{a1 #12 type=standard A=10123:com.example.game}
            * Hist  #0: ActivityRecord{b2 u0 com.example.game/.MainActivity t12}
          * Task{c3 #1 type=home}
            * Hist  #0: ActivityRecord{d4 u0 com.android.launcher3/.uioverrides.QuickstepLauncher t1}
        Display #2 (activities from top to bottom):
          * Task{e5 #7 type=standard A=10200:com.example.browser}
            * Hist  #0: ActivityRecord{f6 u0 com.example.browser/com.example.browser.Main t7}
          * Task{g7 #3 type=home}
            * Hist  #0: ActivityRecord{h8 u0 com.android.launcher3/com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher t3}
    """.trimIndent()
    private val launchers = setOf("com.android.launcher3")

    @Test
    fun `the launcher listed on each display is found, short class names expanded`() {
        assertEquals(
            "com.android.launcher3" to "com.android.launcher3.uioverrides.QuickstepLauncher",
            LauncherLookup.onDisplay(dump, 0, launchers),
        )
        assertEquals(
            "com.android.launcher3" to "com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher",
            LauncherLookup.onDisplay(dump, 2, launchers),
        )
    }

    @Test
    fun `no launcher, an unknown display or other apps give null`() {
        assertNull(LauncherLookup.onDisplay(dump, 5, launchers))
        assertNull(LauncherLookup.onDisplay(dump, 2, setOf("com.other.launcher")))
        assertNull(LauncherLookup.onDisplay("", 0, launchers))
    }
}
