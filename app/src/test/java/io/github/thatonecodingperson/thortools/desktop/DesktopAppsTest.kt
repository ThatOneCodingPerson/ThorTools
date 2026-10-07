package io.github.thatonecodingperson.thortools.desktop

import io.github.thatonecodingperson.thortools.desktop.DesktopApps.Change
import io.github.thatonecodingperson.thortools.desktop.DesktopApps.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopAppsTest {
    private val firefox = "org.mozilla.firefox"
    private val retroArch = "com.retroarch.aarch64"

    @Test
    fun `with no lists they work everywhere, home screens too`() {
        val apps = DesktopApps()
        assertTrue(apps.worksFor(firefox))
        assertTrue(apps.worksFor(retroArch))
        assertTrue(apps.worksFor(null))
    }

    @Test
    fun `a never-in app stops them, every other app keeps them`() {
        val apps = DesktopApps(neverIn = setOf(retroArch))
        assertFalse(apps.worksFor(retroArch))
        assertTrue(apps.worksFor(firefox))
        assertTrue(apps.worksFor(null))
    }

    @Test
    fun `with only-in apps they work there and nowhere else`() {
        val apps = DesktopApps(onlyIn = setOf(firefox))
        assertTrue(apps.worksFor(firefox))
        assertFalse(apps.worksFor(retroArch))
        assertFalse(apps.worksFor(null))
    }

    @Test
    fun `an app on one list can't go on the other until it is taken off`() {
        val blocked = DesktopApps(neverIn = setOf(retroArch))
        assertEquals(Change.OnOtherList(Side.NEVER_IN), blocked.toggle(Side.ONLY_IN, retroArch))
        val freed = (blocked.toggle(Side.NEVER_IN, retroArch) as Change.Done).apps
        assertEquals(DesktopApps(), freed)
        val chosen = (freed.toggle(Side.ONLY_IN, retroArch) as Change.Done).apps
        assertEquals(setOf(retroArch), chosen.onlyIn)
        assertEquals(Change.OnOtherList(Side.ONLY_IN), chosen.toggle(Side.NEVER_IN, retroArch))
    }

    @Test
    fun `a tap on an app already on the list takes it off`() {
        val apps = DesktopApps(onlyIn = setOf(firefox))
        assertEquals(Change.Done(DesktopApps()), apps.toggle(Side.ONLY_IN, firefox))
        assertEquals(Change.Done(DesktopApps(onlyIn = setOf(firefox), neverIn = setOf(retroArch))), apps.toggle(Side.NEVER_IN, retroArch))
    }
}
