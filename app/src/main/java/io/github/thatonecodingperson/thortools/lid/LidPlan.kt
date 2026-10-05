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

/** What happens when the lid closes: every part is its own switch, all off by default; [enabled] turns them all off. */
data class LidChoices(
    val enabled: Boolean = true,
    val powerSaving: Boolean = false,
    val closeBackground: Boolean = false,
    val pauseMedia: Boolean = false,
    val wifiOff: Boolean = false,
    val bluetoothOff: Boolean = false,
    val airplane: Boolean = false,
    val backToSleep: Boolean = false,
    /** How long a wake with the lid closed lasts before it goes back to sleep, in [sleepUnit]s. */
    val sleepWait: Int = WaitUnit.SECONDS.default,
    val sleepUnit: WaitUnit = WaitUnit.SECONDS,
    /** Minutes after closing before the Save power part runs, one of [LidPlan.DELAYS]. */
    val delayMinutes: Int = 0,
    /** While media plays, the Save power part waits until it stops. */
    val notWhileMedia: Boolean = false,
) {
    /** Any of the Save power part is on. */
    val savesPower: Boolean
        get() = powerSaving || closeBackground || pauseMedia || wifiOff || bluetoothOff || airplane

    /** How many actions happen when the lid closes (none while [enabled] is off). */
    val switchedOn: Int
        get() = if (!enabled) {
            0
        } else {
            listOf(powerSaving, closeBackground, pauseMedia, wifiOff, bluetoothOff, airplane, backToSleep).count { it }
        }

    /** Anything at all happens when the lid closes. */
    val active: Boolean
        get() = enabled && (savesPower || backToSleep)
}

/** The values the lid actions change, as read at one moment; null: not read (or not to be changed). */
data class LidReadings(
    val performance: Int? = null,
    val fan: Int? = null,
    val wifi: Boolean? = null,
    val bluetooth: Boolean? = null,
    val airplane: Boolean? = null,
)

/** One thing a closed lid changes, for saying what couldn't be put back. */
enum class LidItem { PERFORMANCE, FAN, WIFI, BLUETOOTH, AIRPLANE }

/**
 * What a closed lid changed: [restore] holds the values to put back when it opens (null where nothing changed);
 * [pending] while the Save power part hasn't run yet (a delay, or media playing). Saved before anything changes, so a
 * crash or a reboot can't lose it.
 */
data class LidSession(val closedAt: Long, val restore: LidReadings, val pending: Boolean = false) {
    fun encode(): String = listOf(
        "closed=$closedAt",
        "perf=${restore.performance ?: ""}",
        "fan=${restore.fan ?: ""}",
        "wifi=${restore.wifi.flag()}",
        "bt=${restore.bluetooth.flag()}",
        "air=${restore.airplane.flag()}",
        "pending=${if (pending) 1 else 0}",
    ).joinToString(";")

    companion object {
        fun decode(text: String?): LidSession? {
            val fields = text?.split(';')?.mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
                ?.associate { (key, value) -> key to value } ?: return null
            val closedAt = fields["closed"]?.toLongOrNull() ?: return null
            return LidSession(
                closedAt = closedAt,
                restore = LidReadings(
                    performance = fields["perf"]?.toIntOrNull(),
                    fan = fields["fan"]?.toIntOrNull(),
                    wifi = fields["wifi"].bool(),
                    bluetooth = fields["bt"].bool(),
                    airplane = fields["air"].bool(),
                ),
                pending = fields["pending"] == "1",
            )
        }

        private fun Boolean?.flag(): String = when (this) {
            true -> "1"
            false -> "0"
            null -> ""
        }

        private fun String?.bool(): Boolean? = when (this) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }
}

/** The lid sandbox's rules. Pure. */
object LidPlan {
    /** Power-saving mode: AYN's lowest performance mode and the quiet fan. (The refresh rate means nothing with both screens off.) */
    const val SAVING_PERFORMANCE = 0
    const val SAVING_FAN = 1

    /** The choices for how long after closing the Save power part runs, in minutes. */
    val DELAYS = listOf(0, 1, 5, 15)
    const val MINUTE_MS = 60_000L

    /** The values the lid sets while closed; null where a choice leaves a value alone. */
    fun closedTargets(choices: LidChoices): LidReadings = LidReadings(
        performance = SAVING_PERFORMANCE.takeIf { choices.powerSaving },
        fan = SAVING_FAN.takeIf { choices.powerSaving },
        wifi = false.takeIf { choices.wifiOff },
        bluetooth = false.takeIf { choices.bluetoothOff },
        airplane = true.takeIf { choices.airplane },
    )

    /**
     * The session for closing the lid with [choices] while [now] is in place: for every value the lid will change, the
     * value to put back. A value that is already as wanted, or that couldn't be read, is left alone. A [pending] session
     * changes nothing yet: its Save power part comes later ([savingDone]).
     */
    fun session(choices: LidChoices, now: LidReadings, time: Long, pending: Boolean = false): LidSession =
        LidSession(closedAt = time, restore = if (pending) LidReadings() else restoreFor(choices, now), pending = pending)

    /** [session] once its Save power part runs while [now] is in place. */
    fun savingDone(session: LidSession, choices: LidChoices, now: LidReadings): LidSession =
        session.copy(restore = restoreFor(choices, now), pending = false)

    /** The Save power part waits while media plays, when asked to. */
    fun savingWaits(choices: LidChoices, musicActive: Boolean): Boolean = choices.notWhileMedia && musicActive

    /** The Save power part runs later instead of at closing: after a delay, or once media stops. */
    fun savingLater(choices: LidChoices, musicActive: Boolean): Boolean =
        choices.enabled && choices.savesPower && (choices.delayMinutes > 0 || savingWaits(choices, musicActive))

    /** When a later Save power part is first due (wall clock); while media plays it is checked again every minute. */
    fun savingDueAt(session: LidSession, choices: LidChoices): Long = session.closedAt + choices.delayMinutes * MINUTE_MS

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
     * Android says the lid is closed ([lidClosed] null counts as open), the helper hasn't seen it open, and the Thor
     * isn't [docked] (in use on an external display with its lid closed).
     */
    fun backToSleep(lidClosed: Boolean?, helperSaysOpen: Boolean, screenOn: Boolean, docked: Boolean): Boolean =
        screenOn && lidClosed == true && !helperSaysOpen && !docked

    /** An external display is connected: the Thor's bottom screen is one of the [publicDisplays] besides the top one. */
    fun docked(publicDisplays: Int): Boolean = publicDisplays > 1

    private fun restoreFor(choices: LidChoices, now: LidReadings): LidReadings {
        val target = closedTargets(choices)
        return LidReadings(
            performance = back(target.performance, now.performance),
            fan = back(target.fan, now.fan),
            wifi = back(target.wifi, now.wifi),
            bluetooth = back(target.bluetooth, now.bluetooth),
            airplane = back(target.airplane, now.airplane),
        )
    }

    /** What isn't back yet after opening the lid: every saved value [now] differs from (unknown counts as not back). */
    fun notRestored(session: LidSession, now: LidReadings): List<LidItem> {
        val wanted = session.restore
        return listOfNotNull(
            LidItem.PERFORMANCE.takeIf { wanted.performance != null && now.performance != wanted.performance },
            LidItem.FAN.takeIf { wanted.fan != null && now.fan != wanted.fan },
            LidItem.WIFI.takeIf { wanted.wifi != null && now.wifi != wanted.wifi },
            LidItem.BLUETOOTH.takeIf { wanted.bluetooth != null && now.bluetooth != wanted.bluetooth },
            LidItem.AIRPLANE.takeIf { wanted.airplane != null && now.airplane != wanted.airplane },
        )
    }

    /** Android's own on/off for Wi-Fi and Bluetooth (`wifi_on`, `bluetooth_on`): 1 is on, 2 is on in airplane mode. */
    fun radioOn(setting: Int?): Boolean? = setting?.let { it == 1 || it == 2 }

    /** Whether the lid is closed, from root `dumpsys window` (`mLidState=LID_CLOSED|LID_OPEN`); null when it doesn't say. */
    fun lidClosed(dump: String?): Boolean? = when {
        dump == null -> null
        "mLidState=LID_CLOSED" in dump -> true
        "mLidState=LID_OPEN" in dump -> false
        else -> null
    }

    private fun <T> back(target: T?, current: T?): T? = if (target != null && current != null && target != current) current else null
}

/** How the last closing went: when the lid closed and opened, what couldn't be put back, how often it went back to sleep. */
data class LidResult(val closedAt: Long, val openedAt: Long, val notRestored: List<LidItem>, val sentBack: Int = 0) {
    fun encode(): String = "$closedAt;$openedAt;${notRestored.joinToString(",") { it.name }};$sentBack"

    companion object {
        fun decode(text: String?): LidResult? {
            val parts = text?.split(';')?.takeIf { it.size == 3 || it.size == 4 } ?: return null
            return LidResult(
                closedAt = parts[0].toLongOrNull() ?: return null,
                openedAt = parts[1].toLongOrNull() ?: return null,
                notRestored = parts[2].split(',').mapNotNull { name -> LidItem.entries.find { it.name == name } },
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
