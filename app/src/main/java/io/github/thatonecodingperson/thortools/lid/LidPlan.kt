package io.github.thatonecodingperson.thortools.lid

/** Input devices that can be muted while the lid is closed. The power key and the lid sensor never are. */
enum class InputGroup(val id: String) {
    /** The AYN button and Volume up (`gpio-keys`) and Volume down (`pmic_resin`). */
    BUTTONS("buttons"),

    /** AYN's pad. */
    CONTROLLER("controller"),

    /** Both touch panels. */
    TOUCH("touch"),
    ;

    companion object {
        fun byId(id: String): InputGroup? = entries.find { it.id == id }

        fun encode(groups: Set<InputGroup>): String = groups.sortedBy { it.ordinal }.joinToString(",") { it.id }.ifEmpty { "-" }

        fun decode(text: String?): Set<InputGroup> = text.orEmpty().split(',').mapNotNull(::byId).toSet()
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
    val muteButtons: Boolean = false,
    val muteController: Boolean = false,
    val muteTouch: Boolean = false,
    val backToSleep: Boolean = false,
    /** Minutes after closing before the Save power part runs, one of [LidPlan.DELAYS]. */
    val delayMinutes: Int = 0,
    /** While media plays, the Save power part waits until it stops. */
    val notWhileMedia: Boolean = false,
    /** The AYN and volume buttons stay on when one of these apps is in front or playing as the lid closes. */
    val keepButtonsFor: Set<String> = emptySet(),
) {
    /** Any of the Save power part is on. */
    val savesPower: Boolean
        get() = powerSaving || closeBackground || pauseMedia || wifiOff || bluetoothOff || airplane

    val mutes: Set<InputGroup>
        get() = setOfNotNull(
            InputGroup.BUTTONS.takeIf { muteButtons },
            InputGroup.CONTROLLER.takeIf { muteController },
            InputGroup.TOUCH.takeIf { muteTouch },
        )

    /** How many actions happen when the lid closes (none while [enabled] is off). */
    val switchedOn: Int
        get() = if (!enabled) {
            0
        } else {
            listOf(powerSaving, closeBackground, pauseMedia, wifiOff, bluetoothOff, airplane, backToSleep).count { it } + mutes.size
        }

    /** Anything at all happens when the lid closes. */
    val active: Boolean
        get() = enabled && (savesPower || mutes.isNotEmpty() || backToSleep)
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
enum class LidItem { PERFORMANCE, FAN, WIFI, BLUETOOTH, AIRPLANE, INPUTS }

/**
 * What a closed lid changed: [restore] holds the values to put back when it opens (null where nothing changed), [muted]
 * the input groups to unmute; [pending] while the Save power part hasn't run yet (a delay, or media playing). Saved
 * before anything changes, so a crash or a reboot can't lose it.
 */
data class LidSession(val closedAt: Long, val restore: LidReadings, val muted: Set<InputGroup>, val pending: Boolean = false) {
    fun encode(): String = listOf(
        "closed=$closedAt",
        "perf=${restore.performance ?: ""}",
        "fan=${restore.fan ?: ""}",
        "wifi=${restore.wifi.flag()}",
        "bt=${restore.bluetooth.flag()}",
        "air=${restore.airplane.flag()}",
        "muted=${InputGroup.encode(muted)}",
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
                muted = InputGroup.decode(fields["muted"]),
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
    fun session(
        choices: LidChoices,
        now: LidReadings,
        time: Long,
        muted: Set<InputGroup> = mutes(choices, emptySet()),
        pending: Boolean = false,
    ): LidSession =
        LidSession(closedAt = time, restore = if (pending) LidReadings() else restoreFor(choices, now), muted = muted, pending = pending)

    /** [session] once its Save power part runs while [now] is in place. */
    fun savingDone(session: LidSession, choices: LidChoices, now: LidReadings): LidSession =
        session.copy(restore = restoreFor(choices, now), pending = false)

    /** The groups to mute: the chosen ones, but the buttons stay on while one of [apps] (in front, playing) is kept. */
    fun mutes(choices: LidChoices, apps: Set<String>): Set<InputGroup> = when {
        !choices.enabled -> emptySet()
        apps.any { it in choices.keepButtonsFor } -> choices.mutes - InputGroup.BUTTONS
        else -> choices.mutes
    }

    /** The Save power part waits while media plays, when asked to. */
    fun savingWaits(choices: LidChoices, musicActive: Boolean): Boolean = choices.notWhileMedia && musicActive

    /** The Save power part runs later instead of at closing: after a delay, or once media stops. */
    fun savingLater(choices: LidChoices, musicActive: Boolean): Boolean =
        choices.enabled && choices.savesPower && (choices.delayMinutes > 0 || savingWaits(choices, musicActive))

    /** When a later Save power part is first due (wall clock); while media plays it is checked again every minute. */
    fun savingDueAt(session: LidSession, choices: LidChoices): Long = session.closedAt + choices.delayMinutes * MINUTE_MS

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

    /**
     * What isn't back yet after opening the lid: every saved value [now] differs from (unknown counts as not back), and
     * the inputs if the session muted some and [mutedInputs] devices still are (null: couldn't be read, counts as muted).
     */
    fun notRestored(session: LidSession, now: LidReadings, mutedInputs: Int?): List<LidItem> {
        val wanted = session.restore
        return listOfNotNull(
            LidItem.PERFORMANCE.takeIf { wanted.performance != null && now.performance != wanted.performance },
            LidItem.FAN.takeIf { wanted.fan != null && now.fan != wanted.fan },
            LidItem.WIFI.takeIf { wanted.wifi != null && now.wifi != wanted.wifi },
            LidItem.BLUETOOTH.takeIf { wanted.bluetooth != null && now.bluetooth != wanted.bluetooth },
            LidItem.AIRPLANE.takeIf { wanted.airplane != null && now.airplane != wanted.airplane },
            LidItem.INPUTS.takeIf { session.muted.isNotEmpty() && mutedInputs != 0 },
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

/** How the last closing went: when the lid closed and opened, and what couldn't be put back. */
data class LidResult(val closedAt: Long, val openedAt: Long, val notRestored: List<LidItem>) {
    fun encode(): String = "$closedAt;$openedAt;${notRestored.joinToString(",") { it.name }}"

    companion object {
        fun decode(text: String?): LidResult? {
            val parts = text?.split(';')?.takeIf { it.size == 3 } ?: return null
            return LidResult(
                closedAt = parts[0].toLongOrNull() ?: return null,
                openedAt = parts[1].toLongOrNull() ?: return null,
                notRestored = parts[2].split(',').mapNotNull { name -> LidItem.entries.find { it.name == name } },
            )
        }
    }
}

/**
 * "Back to sleep" while the lid is closed: every wake goes straight back to sleep, except a third one within
 * [WINDOW_MS], which is taken as meant (a stuck lid sensor must never lock anyone out). Pure.
 */
class WakeGuard {
    private val sentBack = ArrayDeque<Long>()

    /** A wake at [time] (ms) with the lid closed: true to send it back to sleep. */
    fun onWake(time: Long): Boolean {
        while (sentBack.isNotEmpty() && time - sentBack.first() > WINDOW_MS) sentBack.removeFirst()
        if (sentBack.size >= ALLOWED) {
            sentBack.clear()
            return false
        }
        sentBack.addLast(time)
        return true
    }

    fun reset() = sentBack.clear()

    companion object {
        const val WINDOW_MS = 60_000L
        private const val ALLOWED = 2
    }
}
