package io.github.thatonecodingperson.thortools.charging

enum class Sensitivity(val changesToAlert: Int) {
    LOW(10),
    MEDIUM(6),
    HIGH(4),
}

data class FlapVerdict(val alerting: Boolean, val alertChanged: Boolean, val changesInWindow: Int)

/**
 * Counts how often the charging state flips while the charger stays connected. Brief "unplugged" blips shorter
 * than [unplugGraceMs] count as flips too, because a USB-PD renegotiation can look like a short disconnect.
 * A real unplug resets everything.
 */
class FlapDetector(
    var sensitivity: Sensitivity = Sensitivity.MEDIUM,
    private val windowMs: Long = 10 * MINUTE,
    private val calmMs: Long = 5 * MINUTE,
    private val unplugGraceMs: Long = 5 * SECOND,
) {
    private val changes = ArrayDeque<Long>()
    private var lastState: Pair<ChargeClass, InputBand>? = null
    private var lastChangeAt = 0L
    private var unpluggedAt: Long? = null
    private var alerting = false

    fun onSample(timeMs: Long, chargeClass: ChargeClass, band: InputBand = InputBand.UNKNOWN): FlapVerdict {
        val before = alerting
        if (chargeClass == ChargeClass.UNPLUGGED) {
            if (unpluggedAt == null) unpluggedAt = timeMs
            return evaluate(timeMs, before)
        }

        unpluggedAt?.let { since ->
            unpluggedAt = null
            if (timeMs - since > unplugGraceMs) {
                reset()
            } else {
                recordChange(timeMs)
            }
        }

        val state = chargeClass to band
        val previous = lastState
        lastState = state
        if (previous != null && previous != state && !onlyBandAppeared(previous, state)) {
            recordChange(timeMs)
        }
        return evaluate(timeMs, before)
    }

    /** Re-evaluates without a new sample, so an alert can clear after a calm period or a real unplug. */
    fun tick(timeMs: Long): FlapVerdict = evaluate(timeMs, alerting)

    private fun evaluate(timeMs: Long, alertingBefore: Boolean): FlapVerdict {
        unpluggedAt?.let { if (timeMs - it > unplugGraceMs) reset() }
        while (changes.isNotEmpty() && timeMs - changes.first() > windowMs) changes.removeFirst()

        alerting = when {
            changes.size >= sensitivity.changesToAlert -> true
            alerting && timeMs - lastChangeAt < calmMs -> true
            else -> false
        }
        return FlapVerdict(alerting, alerting != alertingBefore, changes.size)
    }

    private fun recordChange(timeMs: Long) {
        changes.addLast(timeMs)
        lastChangeAt = timeMs
    }

    private fun reset() {
        changes.clear()
        lastState = null
        unpluggedAt = null
        alerting = false
    }

    // The input voltage node can become readable a moment after plugging in; that is not a flip.
    private fun onlyBandAppeared(previous: Pair<ChargeClass, InputBand>, current: Pair<ChargeClass, InputBand>) =
        previous.first == current.first && (previous.second == InputBand.UNKNOWN || current.second == InputBand.UNKNOWN)

    private companion object {
        const val SECOND = 1_000L
        const val MINUTE = 60 * SECOND
    }
}
