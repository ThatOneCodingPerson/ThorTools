package io.github.thatonecodingperson.thortools.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareStatsTest {

    @Test
    fun `the fastest-running cluster speaks for the CPU`() {
        val (ghz, load) = StatsParser.cpu(listOf(1_200_000L to 2_000_000L, 2_800_000L to 3_200_000L, 0L to 1_000_000L))!!
        assertEquals(2.8f, ghz, 0.001f)
        assertEquals(0.875f, load, 0.001f)
        assertNull(StatsParser.cpu(emptyList()))
    }

    @Test
    fun `sysfs numbers and percentages are read`() {
        assertEquals(680L, StatsParser.number("680\n"))
        assertEquals(37L, StatsParser.number("37 %"))
        assertNull(StatsParser.number("n/a"))
        assertEquals(0.37f, StatsParser.fraction("37 %")!!, 0.001f)
        assertEquals(680, StatsParser.gpuMhz(680_000_000L))
        assertNull(StatsParser.gpuMhz(0L))
    }

    @Test
    fun `cluster zones beat single-core zones and silly values are dropped`() {
        val zones = listOf("cpu-1-0-0" to 70_000L, "cpuss-0" to 48_500L, "cpuss-1" to 51_000L, "gpuss-0" to 44_000L, "gpuss-1" to 999_000L)
        assertEquals(51f, StatsParser.cpuTemp(zones)!!, 0.01f)
        assertEquals(44f, StatsParser.gpuTemp(zones)!!, 0.01f)
        assertEquals(70f, StatsParser.cpuTemp(listOf("cpu-1-0-0" to 70_000L))!!, 0.01f)
        assertEquals(45f, StatsParser.celsius(45), 0.01f)
        assertNull(StatsParser.gpuTemp(listOf("battery" to 30_000L)))
        assertTrue(StatsParser.isShownZone("cpuss-2"))
        assertFalse(StatsParser.isShownZone("skin-msm-therm"))
    }

    @Test
    fun `memory comes from MemTotal and MemAvailable`() {
        val (used, total) = StatsParser.memory("MemTotal:       16777216 kB\nMemFree:  100 kB\nMemAvailable:    8388608 kB\n")!!
        assertEquals(8f, used, 0.001f)
        assertEquals(16f, total, 0.001f)
        assertNull(StatsParser.memory("MemFree: 1 kB"))
    }

    @Test
    fun `available memory is read from meminfo and from one grep line, and shows in GB`() {
        assertEquals(3355443L, StatsParser.availableKb("MemTotal:  8000000 kB\nMemAvailable:    3355443 kB\n"))
        assertEquals(2097152L, StatsParser.availableKb("MemAvailable:    2097152 kB"))
        assertNull(StatsParser.availableKb(""))
        assertEquals("3.2 GB", StatsParser.gb(3355443L))
        assertEquals("2.0 GB", StatsParser.gb(2097152L))
    }

    @Test
    fun `battery power works with either sign and with mA drivers`() {
        assertEquals(7.7f, StatsParser.watts(-2_000_000L, 3850)!!, 0.01f)
        assertEquals(7.7f, StatsParser.watts(2_000L, 3850)!!, 0.01f)
        assertNull(StatsParser.watts(Long.MIN_VALUE, 3850))
        assertNull(StatsParser.watts(1_000_000L, 0))
    }

    @Test
    fun `the sampler reads clusters, zones and memory from the files`() {
        val files = mapOf(
            "/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq" to "1000000",
            "/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq" to "2000000",
            "/sys/devices/system/cpu/cpufreq/policy7/scaling_cur_freq" to "3000000",
            "/sys/devices/system/cpu/cpufreq/policy7/cpuinfo_max_freq" to "3000000",
            "/sys/class/kgsl/kgsl-3d0/gpuclk" to "220000000",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage" to "12 %",
            "/sys/class/thermal/thermal_zone3/type" to "cpuss-0",
            "/sys/class/thermal/thermal_zone3/temp" to "52000",
            "/sys/class/thermal/thermal_zone9/type" to "battery",
            "/sys/class/thermal/thermal_zone9/temp" to "30000",
            "/proc/meminfo" to "MemTotal: 2097152 kB\nMemAvailable: 1048576 kB",
        )
        val dirs = mapOf(
            "/sys/devices/system/cpu/cpufreq" to listOf("policy0", "policy7"),
            "/sys/class/thermal" to listOf("thermal_zone3", "thermal_zone9", "cooling_device0"),
        )
        val reading = StatsSampler(files::get) { dirs[it].orEmpty() }.sample()
        assertEquals(StatsReading(3f, 1f, 220, 0.12f, 52f, null, 1f, 2f), reading)
        assertTrue(reading.incomplete)
    }

    @Test
    fun `a reading survives the trip through the helper and fills gaps`() {
        val full = StatsReading(2.4f, 0.5f, 680, 0.3f, 48f, 44f, 6.5f, 16f)
        assertEquals(full, StatsReading.decode(full.encode()))
        assertEquals(StatsReading(gpuMhz = 1), StatsReading.decode(StatsReading(gpuMhz = 1).encode()))
        assertEquals(full, StatsReading(cpuGhz = 2.4f).orElse(full))
        assertFalse(full.incomplete)
    }
}
