package io.github.thatonecodingperson.thortools.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedListTest {

    private val ours = "io.example.thortools"
    private val ourService = "$ours/$ours.service.ForegroundAppWatcherService"
    private val odinService = "de.langerhans.odintools/de.langerhans.odintools.service.ForegroundAppWatcherService"

    @Test
    fun `missing or empty lists contain nothing`() {
        assertFalse(SharedList.containsPackage(null, ':', ours))
        assertFalse(SharedList.containsPackage("", ':', ours))
        assertFalse(SharedList.containsPackage("::", ':', ours))
    }

    @Test
    fun `components match by exact package`() {
        assertTrue(SharedList.containsPackage("$odinService:$ourService", ':', ours))
        assertTrue(SharedList.containsPackage("$ours/.service.ForegroundAppWatcherService", ':', ours))
        assertFalse(SharedList.containsPackage("$ours.debug/$ours.service.ForegroundAppWatcherService", ':', ours))
        assertFalse(SharedList.containsPackage(odinService, ':', "de.langerhans"))
    }

    @Test
    fun `plain package lists match whole entries only`() {
        assertTrue(SharedList.containsPackage("com.a,$ours,com.b", ',', ours))
        assertFalse(SharedList.containsPackage("com.a,$ours.debug", ',', ours))
        assertFalse(SharedList.containsPackage("com.a,io.example", ',', ours))
    }

    @Test
    fun `adding puts our entry first and keeps the rest untouched`() {
        assertEquals("$ourService:$odinService", SharedList.withEntry(odinService, ':', ourService))
        assertEquals("$ours,com.a,,com.b", SharedList.withEntry("com.a,,com.b", ',', ours))
    }

    @Test
    fun `adding to an empty list gives just our entry`() {
        assertEquals(ourService, SharedList.withEntry(null, ':', ourService))
        assertEquals(ours, SharedList.withEntry(",", ',', ours))
    }

    @Test
    fun `adding twice changes nothing`() {
        val list = "com.a,$ours"
        assertEquals(list, SharedList.withEntry(list, ',', ours))
        val services = "$ours/.service.ForegroundAppWatcherService:$odinService"
        assertEquals(services, SharedList.withEntry(services, ':', ourService))
    }

    @Test
    fun `removing drops only our entries`() {
        assertEquals(odinService, SharedList.withoutPackage("$ourService:$odinService", ':', ours))
        assertEquals("com.a,$ours.debug", SharedList.withoutPackage("com.a,$ours,$ours.debug", ',', ours))
        assertEquals("", SharedList.withoutPackage(ours, ',', ours))
    }
}
