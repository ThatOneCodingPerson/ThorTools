package io.github.thatonecodingperson.thortools.panel

import java.util.Locale
import kotlin.math.abs

/** One sample of the quick panel's live stats; null where this device doesn't let us read a value. */
data class StatsReading(
    val cpuGhz: Float? = null,
    /** Clock of the fastest cluster against its maximum, 0..1. */
    val cpuLoad: Float? = null,
    val gpuMhz: Int? = null,
    /** GPU busy time, 0..1. */
    val gpuLoad: Float? = null,
    val cpuTemp: Float? = null,
    val gpuTemp: Float? = null,
    val ramUsedGb: Float? = null,
    val ramTotalGb: Float? = null,
) {
    /** Values that a read as root could still fill in. */
    val incomplete: Boolean get() = cpuGhz == null || gpuMhz == null || cpuTemp == null || gpuTemp == null

    fun orElse(other: StatsReading) = StatsReading(
        cpuGhz = cpuGhz ?: other.cpuGhz,
        cpuLoad = cpuLoad ?: other.cpuLoad,
        gpuMhz = gpuMhz ?: other.gpuMhz,
        gpuLoad = gpuLoad ?: other.gpuLoad,
        cpuTemp = cpuTemp ?: other.cpuTemp,
        gpuTemp = gpuTemp ?: other.gpuTemp,
        ramUsedGb = ramUsedGb ?: other.ramUsedGb,
        ramTotalGb = ramTotalGb ?: other.ramTotalGb,
    )

    /** One line, so the root helper can send it back as a command result. */
    fun encode(): String = listOf(
        "cpu" to cpuGhz,
        "cpuload" to cpuLoad,
        "gpu" to gpuMhz,
        "gpuload" to gpuLoad,
        "cputemp" to cpuTemp,
        "gputemp" to gpuTemp,
        "ram" to ramUsedGb,
        "ramtotal" to ramTotalGb,
    ).joinToString(";") { (key, value) -> "$key=${value ?: ""}" }

    companion object {
        fun decode(text: String): StatsReading {
            val values = text.split(';').associate { it.substringBefore('=') to it.substringAfter('=', "") }
            fun float(key: String) = values[key]?.toFloatOrNull()
            return StatsReading(
                cpuGhz = float("cpu"),
                cpuLoad = float("cpuload"),
                gpuMhz = values["gpu"]?.toIntOrNull(),
                gpuLoad = float("gpuload"),
                cpuTemp = float("cputemp"),
                gpuTemp = float("gputemp"),
                ramUsedGb = float("ram"),
                ramTotalGb = float("ramtotal"),
            )
        }
    }
}

/** Turns the raw sysfs and /proc texts into numbers. */
object StatsParser {
    private val CPU_ZONES = listOf("cpuss-", "cpu-")
    private val GPU_ZONES = listOf("gpuss-", "gpu")
    private const val KB_PER_GB = 1024f * 1024f

    /** The leading integer of a sysfs value such as "1920000" or "37 %". */
    fun number(text: String?): Long? = text?.trim()?.substringBefore(' ')?.toLongOrNull()

    /** [clusters] are (current, maximum) clocks in kHz; the fastest-running cluster speaks for the CPU. */
    fun cpu(clusters: List<Pair<Long, Long>>): Pair<Float, Float>? {
        val (current, max) = clusters.filter { it.first > 0 }.maxByOrNull { it.first } ?: return null
        val load = if (max > 0) (current.toFloat() / max).coerceIn(0f, 1f) else 0f
        return current / 1_000_000f to load
    }

    fun gpuMhz(hz: Long?): Int? = hz?.takeIf { it > 0 }?.let { (it / 1_000_000).toInt() }

    fun fraction(percentText: String?): Float? = number(percentText)?.let { (it / 100f).coerceIn(0f, 1f) }

    fun isShownZone(type: String): Boolean = (CPU_ZONES + GPU_ZONES).any { type.startsWith(it) }

    /** The hottest CPU zone; `cpuss-*` sums up the clusters, single-core `cpu-*` zones are the fallback. */
    fun cpuTemp(zones: List<Pair<String, Long>>): Float? = hottest(zones, CPU_ZONES)

    fun gpuTemp(zones: List<Pair<String, Long>>): Float? = hottest(zones, GPU_ZONES)

    private fun hottest(zones: List<Pair<String, Long>>, prefixes: List<String>): Float? {
        val prefix = prefixes.firstOrNull { prefix -> zones.any { it.first.startsWith(prefix) } } ?: return null
        return zones.filter { it.first.startsWith(prefix) }.map { celsius(it.second) }.filter { it in -20f..150f }.maxOrNull()
    }

    /** Thermal zones report millidegrees, but a few drivers report whole degrees. */
    fun celsius(raw: Long): Float = if (abs(raw) >= 1000) raw / 1000f else raw.toFloat()

    /** Used and total memory in GB from /proc/meminfo. */
    fun memory(meminfo: String): Pair<Float, Float>? {
        val total = meminfoKb(meminfo, "MemTotal")?.takeIf { it > 0 } ?: return null
        val available = availableKb(meminfo) ?: return null
        return (total - available) / KB_PER_GB to total / KB_PER_GB
    }

    /** One value in kB from /proc/meminfo, or from a single line of it. */
    fun meminfoKb(meminfo: String, key: String): Long? = meminfo.lineSequence().firstOrNull { it.trimStart().startsWith("$key:") }
        ?.substringAfter(':')?.trim()?.substringBefore(' ')?.toLongOrNull()

    fun availableKb(meminfo: String): Long? = meminfoKb(meminfo, "MemAvailable")

    /** kB as GB with one decimal, e.g. "3.2 GB". */
    fun gb(kb: Long): String = String.format(Locale.US, "%.1f GB", kb / KB_PER_GB)

    /**
     * Battery power in watts from `BatteryManager` current (µA; the sign differs between devices) and voltage (mV).
     * Some drivers report mA instead; no running handheld draws under 20 mA, so smaller numbers are taken as mA.
     */
    fun watts(current: Long, milliVolts: Int): Float? {
        if (current == Long.MIN_VALUE || current == 0L || milliVolts <= 0) return null
        val microAmps = if (abs(current) < 20_000) abs(current) * 1000 else abs(current)
        return microAmps * milliVolts / 1_000_000_000f
    }
}

/**
 * Reads a [StatsReading] from sysfs and /proc. The app uses it directly; the root input helper runs the same code when
 * SELinux keeps the app out of some of these files.
 */
class StatsSampler(private val read: (String) -> String?, private val list: (String) -> List<String>) {
    // Found once: there are around a hundred zones and only a few are shown.
    private var zones: List<Pair<String, String>>? = null

    fun sample(): StatsReading {
        val clusters = list(CPUFREQ).filter { it.startsWith("policy") }.mapNotNull { policy ->
            val current = StatsParser.number(read("$CPUFREQ/$policy/scaling_cur_freq")) ?: return@mapNotNull null
            current to (StatsParser.number(read("$CPUFREQ/$policy/cpuinfo_max_freq")) ?: 0L)
        }
        val cpu = StatsParser.cpu(clusters)
        val temps = thermalZones().mapNotNull { (type, path) -> StatsParser.number(read(path))?.let { type to it } }
        val memory = read(MEMINFO)?.let(StatsParser::memory)
        return StatsReading(
            cpuGhz = cpu?.first,
            cpuLoad = cpu?.second,
            gpuMhz = StatsParser.gpuMhz(StatsParser.number(read("$KGSL/gpuclk"))),
            gpuLoad = StatsParser.fraction(read("$KGSL/gpu_busy_percentage")),
            cpuTemp = StatsParser.cpuTemp(temps),
            gpuTemp = StatsParser.gpuTemp(temps),
            ramUsedGb = memory?.first,
            ramTotalGb = memory?.second,
        )
    }

    private fun thermalZones(): List<Pair<String, String>> = zones ?: list(THERMAL)
        .filter { it.startsWith("thermal_zone") }
        .mapNotNull { zone ->
            val type = read("$THERMAL/$zone/type")?.trim() ?: return@mapNotNull null
            if (StatsParser.isShownZone(type)) type to "$THERMAL/$zone/temp" else null
        }
        .also { zones = it }

    private companion object {
        const val CPUFREQ = "/sys/devices/system/cpu/cpufreq"
        const val KGSL = "/sys/class/kgsl/kgsl-3d0"
        const val THERMAL = "/sys/class/thermal"
        const val MEMINFO = "/proc/meminfo"
    }
}
