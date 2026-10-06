package io.github.thatonecodingperson.thortools.oled

import io.github.thatonecodingperson.thortools.input.Screen

/** How a screen that sits idle is protected. */
enum class IdleLook(val id: String) {
    /** A see-through black layer: the screen stays readable, its pixels work less. */
    DIM("dim"),

    /** Fully black: an OLED's pixels are off. */
    BLACK("black"),
    ;

    companion object {
        fun byId(id: String?): IdleLook? = entries.find { it.id == id }
    }
}

/** When Thor Tools' pixel shifter moves a screen. */
enum class ShiftMode(val id: String) {
    /** All the time, slowly, like phones and TVs. */
    ALWAYS("always"),

    /** Only once that screen's picture has stayed still for a while. */
    STILL("still"),
    ;

    companion object {
        fun byId(id: String?): ShiftMode? = entries.find { it.id == id }
    }
}

/** What happens to a still area. */
enum class AreaAction(val id: String, val dim: Boolean, val shift: Boolean) {
    DIM("dim", dim = true, shift = false),
    SHIFT("shift", dim = false, shift = true),
    BOTH("both", dim = true, shift = true),
    ;

    companion object {
        fun byId(id: String?): AreaAction? = entries.find { it.id == id }
    }
}

/** Thor Tools' own protections that can be switched on (the refresher counts when it runs by itself). */
enum class OledPart { SHIFTER, REFRESHER, STILL_AREAS, IDLE }

/** Which screens a protection covers. */
enum class ScreenChoice(val id: String, val screens: Set<Screen>) {
    TOP("top", setOf(Screen.TOP)),
    BOTTOM("bottom", setOf(Screen.BOTTOM)),
    BOTH("both", setOf(Screen.TOP, Screen.BOTTOM)),
    ;

    companion object {
        fun byId(id: String?): ScreenChoice? = entries.find { it.id == id }
    }
}

/** The OLED Safety choices of Thor Tools' own protection; AYN's lives in AYN's settings ([AynProtection]). */
data class OledChoices(
    /** Protect a screen that has had no input for [idleMinutes]. */
    val idle: Boolean = false,
    val look: IdleLook = IdleLook.DIM,
    val idleScreens: ScreenChoice = ScreenChoice.BOTH,
    val idleMinutes: Int = 5,
    /** How dark the dim layer is, in percent. */
    val dimPercent: Int = 70,
    /** The screen with the controller isn't protected while media plays (a video). */
    val notWhileMedia: Boolean = true,
    /** Where the refresher runs, what it shows and for how long. */
    val refreshScreens: ScreenChoice = ScreenChoice.BOTH,
    val refreshSeconds: Int = 10,
    val refreshPattern: RefreshPattern = RefreshPattern.SWEEP,
    /** The refresher runs by itself on a screen whose picture stayed still for [refreshAfterMinutes]. */
    val refreshAuto: Boolean = false,
    val refreshAfterMinutes: Int = 10,
    /** Thor Tools' pixel shifter: moves [shiftScreens] along a circle of [shiftRadius] px, a step every [shiftEverySeconds]. */
    val shift: Boolean = false,
    val shiftScreens: ScreenChoice = ScreenChoice.BOTH,
    val shiftRadius: Int = 3,
    val shiftEverySeconds: Int = 60,
    val shiftMode: ShiftMode = ShiftMode.ALWAYS,
    /** With [ShiftMode.STILL]: how long the picture stays still before the shifter starts. */
    val shiftStillSeconds: Int = 10,
    /** With [ShiftMode.STILL]: back to the middle when the picture moves again. */
    val shiftCenter: Boolean = false,
    /** Still areas (bright parts that stay put while the rest moves) on [areaScreens], after [areaStillSeconds]. */
    val areas: Boolean = false,
    val areaScreens: ScreenChoice = ScreenChoice.BOTH,
    val areaAction: AreaAction = AreaAction.DIM,
    val areaStillSeconds: Int = 180,
    val areaDimPercent: Int = 30,
    val areaShiftPixels: Int = 2,
    val areaEverySeconds: Int = 60,
    /** Still areas the experimental way: tighter, steadier, checked twice a second. */
    val areaExperimental: Boolean = false,
) {
    /** The protections switched on, in the order the page shows them. */
    val activeParts: List<OledPart>
        get() = listOfNotNull(
            OledPart.SHIFTER.takeIf { shift },
            OledPart.STILL_AREAS.takeIf { areas },
            OledPart.REFRESHER.takeIf { refreshAuto },
            OledPart.IDLE.takeIf { idle },
        )

    fun encode(): String = listOf(
        "idle=${if (idle) 1 else 0}",
        "look=${look.id}",
        "screens=${idleScreens.id}",
        "minutes=$idleMinutes",
        "dim=$dimPercent",
        "media=${if (notWhileMedia) 1 else 0}",
        "refresh=${refreshScreens.id}",
        "seconds=$refreshSeconds",
        "shift=${if (shift) 1 else 0}",
        "shiftscreens=${shiftScreens.id}",
        "radius=$shiftRadius",
        "every=$shiftEverySeconds",
        "shiftmode=${shiftMode.id}",
        "stillfor=$shiftStillSeconds",
        "center=${if (shiftCenter) 1 else 0}",
        "pattern=${refreshPattern.id}",
        "auto=${if (refreshAuto) 1 else 0}",
        "autoafter=$refreshAfterMinutes",
        "areas=${if (areas) 1 else 0}",
        "areascreens=${areaScreens.id}",
        "areaaction=${areaAction.id}",
        "areastill=$areaStillSeconds",
        "areadim=$areaDimPercent",
        "areashift=$areaShiftPixels",
        "areaevery=$areaEverySeconds",
        "areaexp=${if (areaExperimental) 1 else 0}",
    ).joinToString(";")

    companion object {
        val MINUTES = 1..30
        val DIM_PERCENT = 30..90
        const val DIM_STEP = 10
        val REFRESH_SECONDS = 5..60
        const val REFRESH_STEP = 5
        val SHIFT_RADIUS = 1..10

        /** The step times offered, in seconds. */
        val SHIFT_EVERY = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300, 600)

        /** The "still for" times offered, in seconds. */
        val SHIFT_STILL = listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300)

        /** For still areas: how long they stay put first, and how often they move, in seconds. */
        val AREA_STILL = listOf(30, 60, 120, 180, 300, 600, 900, 1800)
        val AREA_EVERY = listOf(10, 15, 20, 30, 45, 60, 90, 120, 180, 300, 600)
        val AREA_DIM = 10..60
        val AREA_SHIFT = 1..6

        /** Unknown or missing parts take their defaults, numbers are kept in their ranges. */
        fun decode(text: String?): OledChoices {
            val fields = text.orEmpty().split(';').mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (key, value) -> key to value }
            val default = OledChoices()
            return OledChoices(
                idle = fields["idle"] == "1",
                look = IdleLook.byId(fields["look"]) ?: default.look,
                idleScreens = ScreenChoice.byId(fields["screens"]) ?: default.idleScreens,
                idleMinutes = fields["minutes"]?.toIntOrNull()?.coerceIn(MINUTES) ?: default.idleMinutes,
                dimPercent = fields["dim"]?.toIntOrNull()?.coerceIn(DIM_PERCENT) ?: default.dimPercent,
                notWhileMedia = fields["media"]?.let { it == "1" } ?: default.notWhileMedia,
                refreshScreens = ScreenChoice.byId(fields["refresh"]) ?: default.refreshScreens,
                refreshSeconds = fields["seconds"]?.toIntOrNull()?.coerceIn(REFRESH_SECONDS) ?: default.refreshSeconds,
                shift = fields["shift"] == "1",
                shiftScreens = ScreenChoice.byId(fields["shiftscreens"]) ?: default.shiftScreens,
                shiftRadius = fields["radius"]?.toIntOrNull()?.coerceIn(SHIFT_RADIUS) ?: default.shiftRadius,
                shiftEverySeconds = fields["every"]?.toIntOrNull()?.takeIf { it in SHIFT_EVERY } ?: default.shiftEverySeconds,
                shiftMode = ShiftMode.byId(fields["shiftmode"]) ?: default.shiftMode,
                shiftStillSeconds = fields["stillfor"]?.toIntOrNull()?.takeIf { it in SHIFT_STILL } ?: default.shiftStillSeconds,
                shiftCenter = fields["center"] == "1",
                refreshPattern = RefreshPattern.byId(fields["pattern"]) ?: default.refreshPattern,
                refreshAuto = fields["auto"] == "1",
                refreshAfterMinutes = fields["autoafter"]?.toIntOrNull()?.coerceIn(MINUTES) ?: default.refreshAfterMinutes,
                areas = fields["areas"] == "1",
                areaScreens = ScreenChoice.byId(fields["areascreens"]) ?: default.areaScreens,
                areaAction = AreaAction.byId(fields["areaaction"]) ?: default.areaAction,
                areaStillSeconds = fields["areastill"]?.toIntOrNull()?.takeIf { it in AREA_STILL } ?: default.areaStillSeconds,
                areaDimPercent = fields["areadim"]?.toIntOrNull()?.coerceIn(AREA_DIM) ?: default.areaDimPercent,
                areaShiftPixels = fields["areashift"]?.toIntOrNull()?.coerceIn(AREA_SHIFT) ?: default.areaShiftPixels,
                areaEverySeconds = fields["areaevery"]?.toIntOrNull()?.takeIf { it in AREA_EVERY } ?: default.areaEverySeconds,
                areaExperimental = fields["areaexp"] == "1",
            )
        }
    }
}
