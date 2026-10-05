package io.github.thatonecodingperson.thortools.leds

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** How the stick lights behave. [AYN]: Thor Tools leaves them to AYN's own settings. */
enum class LedMode(val id: String) {
    AYN("ayn"),
    OFF("off"),
    COLOUR("colour"),
    BREATHE("breathe"),
    PULSE("pulse"),
    RAINBOW("rainbow"),
    BATTERY("battery"),
    ;

    companion object {
        fun byId(id: String?): LedMode? = entries.find { it.id == id }
    }
}

/**
 * The stick lights as chosen: a [mode], each stick's colour (0xRRGGBB), [brightness] and effect [speed] from 0 to 1,
 * and whether they breathe green while charging.
 */
data class LedLook(
    val mode: LedMode = LedMode.AYN,
    val left: Int = DEFAULT_COLOUR,
    val right: Int = DEFAULT_COLOUR,
    val brightness: Float = DEFAULT_BRIGHTNESS,
    val speed: Float = DEFAULT_SPEED,
    val chargingBreathe: Boolean = false,
) {
    fun encode(): String = listOf(
        "mode=${mode.id}",
        "left=${LedPlan.hex(left)}",
        "right=${LedPlan.hex(right)}",
        "bright=${LedPlan.decimal(brightness)}",
        "speed=${LedPlan.decimal(speed)}",
        "charging=${if (chargingBreathe) 1 else 0}",
    ).joinToString(";")

    companion object {
        const val DEFAULT_COLOUR = 0xFF1734
        const val DEFAULT_BRIGHTNESS = 0.6f
        const val DEFAULT_SPEED = 0.5f

        /** Unknown or broken parts fall back to the defaults, so a stored look never fails to load. */
        fun decode(text: String?): LedLook {
            val fields = text.orEmpty().split(';').mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (key, value) -> key to value }
            return LedLook(
                mode = LedMode.byId(fields["mode"]) ?: LedMode.AYN,
                left = fields["left"]?.let(LedPlan::parseColour) ?: DEFAULT_COLOUR,
                right = fields["right"]?.let(LedPlan::parseColour) ?: DEFAULT_COLOUR,
                brightness = fields["bright"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: DEFAULT_BRIGHTNESS,
                speed = fields["speed"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: DEFAULT_SPEED,
                chargingBreathe = fields["charging"] == "1",
            )
        }
    }
}

/** One moment of the lights: each ring's colour (0xRRGGBB) and the brightness, 0 to 255. */
data class LedFrame(val left: Int, val right: Int, val brightness: Int)

/** The stick lights' rules. Pure. */
object LedPlan {
    const val GREEN = 0x00FF40
    private const val RED = 0xFF0000
    private const val AMBER = 0xFFA000
    private const val SLOWEST_MS = 6000L
    private const val FASTEST_MS = 1200L
    private const val BREATHE_FLOOR = 0.12f
    private const val FULL = 100
    private const val LOW = 20
    private const val MIDDLE = 50

    /** The light the service writes itself (effects, battery level, charging); AYN's own and plain colours go through AYN. */
    fun drawnByThorTools(look: LedLook, charging: Boolean): Boolean =
        (charging && look.chargingBreathe && look.mode != LedMode.AYN) || look.mode in DRAWN

    /** Whether frames change over time, so something must keep writing them. */
    fun animated(look: LedLook, charging: Boolean, battery: Int): Boolean =
        (charging && look.chargingBreathe && look.mode != LedMode.AYN && battery < FULL) ||
            look.mode == LedMode.BREATHE ||
            look.mode == LedMode.PULSE ||
            look.mode == LedMode.RAINBOW

    /** The rings at [timeMs]; null for AYN's own look (Thor Tools doesn't draw it). */
    fun frame(look: LedLook, timeMs: Long, battery: Int, charging: Boolean): LedFrame? {
        val bright = level(look.brightness)
        if (charging && look.chargingBreathe && look.mode != LedMode.AYN) {
            val level = if (battery >= FULL) bright else scaled(bright, breathe(timeMs, look.speed))
            return LedFrame(GREEN, GREEN, level)
        }
        return when (look.mode) {
            LedMode.AYN -> null
            LedMode.OFF -> LedFrame(0, 0, 0)
            LedMode.COLOUR -> LedFrame(look.left, look.right, bright)
            LedMode.BREATHE -> LedFrame(look.left, look.right, scaled(bright, breathe(timeMs, look.speed)))
            LedMode.PULSE -> LedFrame(look.left, look.right, scaled(bright, pulse(timeMs, look.speed)))
            LedMode.RAINBOW -> {
                val phase = phase(timeMs, look.speed)
                LedFrame(hsv(phase, 1f, 1f), hsv((phase + 0.5f) % 1f, 1f, 1f), bright)
            }
            LedMode.BATTERY -> batteryColour(battery).let { LedFrame(it, it, bright) }
        }
    }

    /** What stands in for an animated look while it can't run: its colours, steady, at the chosen brightness. */
    fun still(look: LedLook, battery: Int, charging: Boolean): LedFrame? {
        val bright = level(look.brightness)
        if (charging && look.chargingBreathe && look.mode != LedMode.AYN) return LedFrame(GREEN, GREEN, bright)
        val breathing = look.mode == LedMode.BREATHE || look.mode == LedMode.PULSE
        return if (breathing) LedFrame(look.left, look.right, bright) else frame(look, 0, battery, charging)
    }

    /** Red when low, amber in the middle, green when full, blended in between. */
    fun batteryColour(level: Int): Int {
        val clamped = level.coerceIn(0, FULL)
        return when {
            clamped <= LOW -> RED
            clamped <= MIDDLE -> blend(RED, AMBER, (clamped - LOW) / (MIDDLE - LOW).toFloat())
            else -> blend(AMBER, GREEN, (clamped - MIDDLE) / (FULL - MIDDLE).toFloat())
        }
    }

    /**
     * One ring's line for the light node: `1-R:G:B` (zone 1 is the whole ring on the Thor). The driver has no brightness
     * of its own, so the colour is scaled, as AYN's settings app does it.
     */
    fun nodeLine(colour: Int, brightness: Int): String {
        val level = brightness.coerceIn(0, 255)
        fun scaled(shift: Int) = ((colour shr shift) and 0xFF) * level / 255
        return "1-${scaled(16)}:${scaled(8)}:${scaled(0)}"
    }

    /** AYN's colour setting for both sticks: `#AARRGGBB,#AARRGGBB`. */
    fun aynColours(left: Int, right: Int): String = "#ff${hex(left)},#ff${hex(right)}"

    /** AYN's two colours from its setting, or null when it can't be read. */
    fun parseAynColours(text: String?): Pair<Int, Int>? {
        val parts = text?.split(',')?.map { it.trim().removePrefix("#") } ?: return null
        if (parts.size != 2) return null
        val colours = parts.map { it.takeLast(6).toIntOrNull(16) ?: return null }
        return colours[0] to colours[1]
    }

    /** The look AYN's three settings make: enabled per stick (`1,1`), colours, brightness 0 to 1. */
    fun aynFrame(enabled: String?, colours: String?, brightness: String?): LedFrame? {
        val (left, right) = parseAynColours(colours) ?: return null
        val on = enabled?.split(',')?.map { it.trim() == "1" } ?: listOf(true, true)
        val level = ((brightness?.toFloatOrNull() ?: 1f).coerceIn(0f, 1f) * 255).roundToInt()
        return LedFrame(if (on.getOrElse(0) { true }) left else 0, if (on.getOrElse(1) { true }) right else 0, level)
    }

    fun hex(colour: Int): String = "%06x".format(colour and 0xFFFFFF)

    fun parseColour(text: String): Int? = text.trim().removePrefix("#").takeLast(6).takeIf { it.length == 6 }?.toIntOrNull(16)

    fun decimal(value: Float): String = "%.2f".format(java.util.Locale.US, value)

    fun hsv(hue: Float, saturation: Float, value: Float): Int {
        val h = (hue % 1f + 1f) % 1f * 6f
        val c = value * saturation
        val x = c * (1 - abs(h % 2f - 1))
        val m = value - c
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return (channel(r + m) shl 16) or (channel(g + m) shl 8) or channel(b + m)
    }

    /** How long one cycle takes: 6 s at the slowest speed, 1.2 s at the fastest. */
    fun periodMs(speed: Float): Long = SLOWEST_MS - ((SLOWEST_MS - FASTEST_MS) * speed.coerceIn(0f, 1f)).toLong()

    private fun level(brightness: Float): Int = (brightness.coerceIn(0f, 1f) * 255).roundToInt()

    private fun scaled(brightness: Int, factor: Float): Int = (brightness * factor).roundToInt()

    private fun phase(timeMs: Long, speed: Float): Float = (timeMs % periodMs(speed)) / periodMs(speed).toFloat()

    /** A slow rise and fall that never quite goes dark. */
    private fun breathe(timeMs: Long, speed: Float): Float {
        val wave = sin(phase(timeMs, speed) * PI).toFloat()
        return BREATHE_FLOOR + (1 - BREATHE_FLOOR) * wave * wave
    }

    /** A quick flash that fades out. */
    private fun pulse(timeMs: Long, speed: Float): Float {
        val p = phase(timeMs, speed)
        return if (p < PULSE_RISE) p / PULSE_RISE else 1f - (p - PULSE_RISE) / (1f - PULSE_RISE)
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        fun mix(shift: Int) =
            ((from shr shift and 0xFF) + ((to shr shift and 0xFF) - (from shr shift and 0xFF)) * t.coerceIn(0f, 1f)).roundToInt()
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * 255).roundToInt()

    private const val PULSE_RISE = 0.12f
    private val DRAWN = setOf(LedMode.BREATHE, LedMode.PULSE, LedMode.RAINBOW, LedMode.BATTERY)
}
