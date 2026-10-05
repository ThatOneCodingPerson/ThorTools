package io.github.thatonecodingperson.thortools.charging

import kotlin.math.abs

/** The kind of charger, from the kernel's USB type (`/sys/class/qcom-battery/usb_real_type`). */
enum class ChargerKind {
    NONE,

    /** A computer's USB port (SDP): about 2.5 W. */
    COMPUTER,

    /** A computer's charging port (CDP). */
    COMPUTER_CHARGING,

    /** A plain wall charger (DCP). */
    WALL,

    /** Qualcomm Quick Charge (HVDCP). */
    QUICK_CHARGE,

    /** USB Power Delivery, with or without PPS. */
    PD,
    OTHER,
    ;

    companion object {
        fun from(usbRealType: String?): ChargerKind {
            val type = usbRealType?.trim()?.uppercase().orEmpty()
            return when {
                type.isEmpty() || type == "UNKNOWN" -> NONE
                type == "SDP" -> COMPUTER
                type == "CDP" -> COMPUTER_CHARGING
                type == "DCP" -> WALL
                type.startsWith("HVDCP") -> QUICK_CHARGE
                type.startsWith("PD") || type.startsWith("USB_PD") -> PD
                else -> OTHER
            }
        }
    }
}

/** What was going on while charging kept switching, for the alert's text. */
data class ChargeAlertContext(
    val aynLimit: Boolean = false,
    val separation: Boolean = false,
    val automation: Boolean = false,
    val weakCharger: Boolean = false,
)

/** The extra lines of the alert's text, in order. */
enum class AlertNote { AYN_LIMIT, SEPARATION, AUTOMATION, WEAK_CHARGER }

/** One reading for Power management's "Charging right now". Null where a value couldn't be read. */
data class ChargeNow(
    val chargeClass: ChargeClass = ChargeClass.UNPLUGGED,
    val level: Int? = null,
    val temperatureC: Float? = null,
    val charger: ChargerKind = ChargerKind.NONE,
    val chargerType: String? = null,
    val offeredWatts: Float? = null,
    val inputWatts: Float? = null,
    val inputVolts: Float? = null,
    val healthPercent: Int? = null,
    val aynLimit: Boolean? = null,
    val separation: Boolean? = null,
    val fiveVoltCap: Boolean = false,
)

/** The rules behind Power management's charging card and the alert's text. Pure. */
object ChargeStatus {
    /** AYN's own charge limit stops charging here. */
    const val AYN_LIMIT = 80

    /** Below this the charger can't run the Thor while it's in use; Android calls it "Charging slowly". */
    const val WEAK_CHARGER_WATTS = 5f

    private const val MICRO = 1_000_000f
    private const val DEFAULT_MICRO_VOLT = 5_000_000

    /** What the charger offered (the numbers Android's "Charging rapidly / slowly" comes from), in W. */
    fun offeredWatts(maxCurrentMicroAmp: Int, maxVoltageMicroVolt: Int): Float? {
        if (maxCurrentMicroAmp <= 0) return null
        val microVolt = maxVoltageMicroVolt.takeIf { it > 0 } ?: DEFAULT_MICRO_VOLT
        return maxCurrentMicroAmp / MICRO * (microVolt / MICRO)
    }

    /** What is coming in from the charger right now, in W. */
    fun inputWatts(microVolt: Long?, microAmp: Long?): Float? {
        if (microVolt == null || microAmp == null || microVolt <= 0) return null
        return microVolt / MICRO * (abs(microAmp) / MICRO)
    }

    /** With AYN's 80 % limit on, the automation's top level is never reached when it is above 80 %. */
    fun limitUnreachable(aynLimitOn: Boolean, topLevel: Int): Boolean = aynLimitOn && topLevel > AYN_LIMIT

    fun notes(context: ChargeAlertContext): List<AlertNote> = listOfNotNull(
        AlertNote.AYN_LIMIT.takeIf { context.aynLimit },
        AlertNote.SEPARATION.takeIf { context.separation },
        AlertNote.AUTOMATION.takeIf { context.automation },
        AlertNote.WEAK_CHARGER.takeIf { context.weakCharger },
    )

    /** One line of [count] values separated by `|` (one shell call for every node); null where one was empty or missing. */
    fun fields(output: String?, count: Int): List<String?> {
        val parts = output?.lines()?.firstOrNull().orEmpty().split('|')
        return List(count) { index -> parts.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() } }
    }
}
