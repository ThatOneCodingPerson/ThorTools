package io.github.thatonecodingperson.thortools.charging

import android.os.BatteryManager

/** What the charger is doing right now, in the same buckets SystemUI uses for "Charging rapidly / slowly". */
enum class ChargeClass {
    UNPLUGGED,
    NOT_CHARGING,
    SLOW,
    NORMAL,
    FAST,

    /** Plugged in but not charging on purpose: full, held at the charge limit, or running on direct power. */
    HELD,
}

/** Rough USB input voltage, used to spot USB-PD renegotiation (for example 9 V dropping to 5 V and back). */
enum class InputBand { UNKNOWN, V5, V9, V12, V15, V20 }

data class ChargeSample(
    val timeMs: Long,
    val plugged: Boolean,
    val status: Int,
    val level: Int,
    val maxCurrentMicroAmp: Int,
    val maxVoltageMicroVolt: Int,
    val inputMicroVolt: Long? = null,
    val chargeLimitOn: Boolean = false,
    val directPowerOn: Boolean = false,
)

object ChargeClassifier {

    // SystemUI defaults for config_chargingSlowlyThreshold and config_chargingFastThreshold, in microwatts.
    const val SLOW_BELOW_MICRO_WATT = 5_000_000L
    const val FAST_ABOVE_MICRO_WATT = 7_500_000L
    private const val DEFAULT_MICRO_VOLT = 5_000_000

    fun classify(sample: ChargeSample): ChargeClass {
        if (!sample.plugged) return ChargeClass.UNPLUGGED
        val charging = sample.status == BatteryManager.BATTERY_STATUS_CHARGING
        return when {
            sample.status == BatteryManager.BATTERY_STATUS_FULL -> ChargeClass.HELD
            !charging && (sample.directPowerOn || sample.chargeLimitOn || sample.level >= 100) -> ChargeClass.HELD
            !charging -> ChargeClass.NOT_CHARGING
            else -> bySpeed(sample)
        }
    }

    fun inputBand(microVolt: Long?): InputBand = when {
        microVolt == null || microVolt <= 0 -> InputBand.UNKNOWN
        microVolt < 7_000_000 -> InputBand.V5
        microVolt < 10_500_000 -> InputBand.V9
        microVolt < 13_500_000 -> InputBand.V12
        microVolt < 17_500_000 -> InputBand.V15
        else -> InputBand.V20
    }

    private fun bySpeed(sample: ChargeSample): ChargeClass {
        if (sample.maxCurrentMicroAmp <= 0) return ChargeClass.NORMAL
        val microVolt = sample.maxVoltageMicroVolt.takeIf { it > 0 } ?: DEFAULT_MICRO_VOLT
        val microWatt = (sample.maxCurrentMicroAmp / 1000L) * (microVolt / 1000L)
        return when {
            microWatt < SLOW_BELOW_MICRO_WATT -> ChargeClass.SLOW
            microWatt > FAST_ABOVE_MICRO_WATT -> ChargeClass.FAST
            else -> ChargeClass.NORMAL
        }
    }
}
