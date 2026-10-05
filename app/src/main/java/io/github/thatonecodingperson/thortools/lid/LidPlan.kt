package io.github.thatonecodingperson.thortools.lid

import kotlin.math.roundToInt

/** The unit Back to sleep's wait is set in, with the range and step of its slider. */
enum class WaitUnit(val id: String, val ms: Long, val range: IntRange, val step: Int, val default: Int) {
    SECONDS("s", 1_000L, 5..60, 5, 10),
    MINUTES("m", 60_000L, 1..15, 1, 1),
    ;

    /** [amount] as the nearest value this unit's slider can show. */
    fun clamp(amount: Int): Int {
        val steps = ((amount.coerceIn(range) - range.first) / step.toFloat()).roundToInt()
        return (range.first + steps * step).coerceAtMost(range.last)
    }

    companion object {
        fun byId(id: String?): WaitUnit? = entries.find { it.id == id }
    }
}

/** What happens when the lid closes; everything is off by default. */
data class LidChoices(
    val wifiOff: Boolean = false,
    val bluetoothOff: Boolean = false,
    val backToSleep: Boolean = false,
    /** How long a wake with the lid closed lasts before it goes back to sleep, in [sleepUnit]s. */
    val sleepWait: Int = WaitUnit.SECONDS.default,
    val sleepUnit: WaitUnit = WaitUnit.SECONDS,
) {
    /** How many of the three are on, for the main menu. */
    val switchedOn: Int
        get() = listOf(wifiOff, bluetoothOff, backToSleep).count { it }
}

/** A radio the lid turns off while it is closed. */
enum class LidItem { WIFI, BLUETOOTH }

/** Whether Wi-Fi and Bluetooth are on, as read at one moment; null: couldn't be read. */
data class Radios(val wifi: Boolean?, val bluetooth: Boolean?)

/**
 * One closing of the lid: when, and which radios it turned off (to turn back on when it opens). Saved before anything
 * is turned off, so a crash or a reboot can't lose it.
 */
data class LidSession(val closedAt: Long, val turnedOff: Set<LidItem>) {
    fun encode(): String = "closed=$closedAt;wifi=${flag(LidItem.WIFI)};bt=${flag(LidItem.BLUETOOTH)}"

    private fun flag(item: LidItem): String = if (item in turnedOff) "1" else "0"

    companion object {
        fun decode(text: String?): LidSession? {
            val fields = fields(text) ?: return null
            val closedAt = fields["closed"]?.toLongOrNull() ?: return null
            return LidSession(
                closedAt,
                setOfNotNull(LidItem.WIFI.takeIf { fields["wifi"] == "1" }, LidItem.BLUETOOTH.takeIf { fields["bt"] == "1" }),
            )
        }

        internal fun fields(text: String?): Map<String, String>? = text
            ?.split(';')
            ?.mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
            ?.associate { (key, value) -> key to value }
    }
}

/** Performance, fan and airplane mode as a closing saved them to put back, where a stored session still has them. */
data class OldLidChanges(val performance: Int?, val fan: Int?, val airplane: Boolean?) {
    val any: Boolean
        get() = performance != null || fan != null || airplane != null

    companion object {
        fun decode(text: String?): OldLidChanges? {
            val fields = LidSession.fields(text) ?: return null
            return OldLidChanges(
                performance = fields["perf"]?.toIntOrNull(),
                fan = fields["fan"]?.toIntOrNull(),
                airplane = when (fields["air"]) {
                    "1" -> true
                    "0" -> false
                    else -> null
                },
            ).takeIf { it.any }
        }
    }
}

/** The lid's rules. Pure. */
object LidPlan {
    /**
     * Root shell: the lid sensor's switch states (`getevent -S`), its node found by name because the numbers change.
     * The kernel's word, unlike `dumpsys window`, whose saved last-ANR copy can hold a lid state from long ago.
     */
    const val LID_STATE =
        "for d in /sys/class/input/event*; do [ \"\$(cat \$d/device/name)\" = hall_switch ] && getevent -S /dev/input/\${d##*/}; done"

    /**
     * Whether the lid is closed, from `getevent -S <hall_switch node>`: the kernel's switch states as a hex mask, bit 0
     * (`SW_LID`) set while closed. Null when the output holds no mask (the sensor or the shell wasn't there).
     */
    fun lidClosed(switchStates: String?): Boolean? {
        val mask = switchStates?.lineSequence()?.map { it.trim() }?.lastOrNull { MASK.matches(it) } ?: return null
        return mask.toLong(16) and 1L != 0L
    }

    private val MASK = Regex("[0-9a-fA-F]{4,16}")

    /** The radios closing the lid turns off: chosen, and on right now. One that couldn't be read is left alone. */
    fun toTurnOff(choices: LidChoices, now: Radios): Set<LidItem> = setOfNotNull(
        LidItem.WIFI.takeIf { choices.wifiOff && now.wifi == true },
        LidItem.BLUETOOTH.takeIf { choices.bluetoothOff && now.bluetooth == true },
    )

    /** The radios [session] turned off that aren't back on [now] (one that couldn't be read counts as not back). */
    fun notBack(session: LidSession, now: Radios): List<LidItem> = listOfNotNull(
        LidItem.WIFI.takeIf { it in session.turnedOff && now.wifi != true },
        LidItem.BLUETOOTH.takeIf { it in session.turnedOff && now.bluetooth != true },
    )

    /** Android's own on/off for Wi-Fi and Bluetooth (`wifi_on`, `bluetooth_on`): 1 is on, 2 is on in airplane mode. */
    fun radioOn(setting: Int?): Boolean? = setting?.let { it == 1 || it == 2 }

    /** How long a wake with the lid closed lasts before Back to sleep acts. */
    fun sleepWaitMs(choices: LidChoices): Long = choices.sleepUnit.clamp(choices.sleepWait) * choices.sleepUnit.ms

    /** [choices] with the wait set in [unit]: as near the same length as that unit's slider allows. */
    fun withUnit(choices: LidChoices, unit: WaitUnit): LidChoices {
        if (unit == choices.sleepUnit) return choices
        val roundedUp = ((sleepWaitMs(choices) + unit.ms - 1) / unit.ms).toInt()
        return choices.copy(sleepUnit = unit, sleepWait = unit.clamp(roundedUp))
    }

    /**
     * Whether a wake with the lid closed goes back to sleep once its wait is over: only while the screen is still on,
     * the lid sensor says closed ([lidClosed] null counts as open), the helper hasn't seen it open, and the Thor isn't
     * [docked] (in use on an external display with its lid closed).
     */
    fun backToSleep(lidClosed: Boolean?, helperSaysOpen: Boolean, screenOn: Boolean, docked: Boolean): Boolean =
        screenOn && lidClosed == true && !helperSaysOpen && !docked

    /** An external display is connected: the Thor's bottom screen is one of the [publicDisplays] besides the top one. */
    fun docked(publicDisplays: Int): Boolean = publicDisplays > 1
}

/** How the last closing went: when the lid closed and opened, what didn't come back on, how often it went back to sleep. */
data class LidResult(val closedAt: Long, val openedAt: Long, val notBack: List<LidItem>, val sentBack: Int = 0) {
    fun encode(): String = "$closedAt;$openedAt;${notBack.joinToString(",") { it.name }};$sentBack"

    companion object {
        fun decode(text: String?): LidResult? {
            val parts = text?.split(';')?.takeIf { it.size == 3 || it.size == 4 } ?: return null
            return LidResult(
                closedAt = parts[0].toLongOrNull() ?: return null,
                openedAt = parts[1].toLongOrNull() ?: return null,
                notBack = parts[2].split(',').mapNotNull { name -> LidItem.entries.find { it.name == name } },
                sentBack = parts.getOrNull(3)?.toIntOrNull() ?: 0,
            )
        }
    }
}

/**
 * Back to sleep's way out: a wake within [WINDOW_MS] after the Thor was sent back to sleep is one more in a row, and the
 * third wake in a row is let through, so a stuck lid sensor (or a magnet near it) can never lock anyone out. Times are
 * milliseconds of a clock that keeps counting while the Thor sleeps. Pure.
 */
class WakeGuard {
    private var lastSentBack: Long? = null
    private var inARow = 0

    /** A wake at [time] with the lid closed: true to wait and send it back to sleep, false to let it stay awake. */
    fun onWake(time: Long): Boolean {
        val last = lastSentBack
        inARow = if (last != null && time - last <= WINDOW_MS) inARow + 1 else 0
        if (inARow < ALLOWED) return true
        reset()
        return false
    }

    /** The Thor went back to sleep at [time]. */
    fun sentBack(time: Long) {
        lastSentBack = time
    }

    fun reset() {
        lastSentBack = null
        inARow = 0
    }

    companion object {
        const val WINDOW_MS = 60_000L
        private const val ALLOWED = 2
    }
}
