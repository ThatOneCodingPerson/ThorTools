package io.github.thatonecodingperson.thortools.oled

/**
 * What the root helper's OLED engine does, by display id: which screens its pixel shifter moves (within [radius], a
 * step every [everyMs]; with [whenStill] only once the picture has stayed still for [stillMs], and back to the middle
 * when it moves again if [center]), which screens it watches and reports as still or moving, and on which screens it
 * looks after still areas (after [areaStillMs]: dimmed by [areaDim] percent, and/or their still pixels moved within
 * [areaShift] px every [areaEveryMs]; 0 for either means not). Sent as one word without spaces. Pure.
 */
data class EngineConfig(
    val shiftDisplays: Set<Int> = emptySet(),
    val radius: Int = 3,
    val everyMs: Long = 60_000,
    val whenStill: Boolean = false,
    val stillMs: Long = 10_000,
    val center: Boolean = false,
    val watchDisplays: Set<Int> = emptySet(),
    val areaDisplays: Set<Int> = emptySet(),
    val areaStillMs: Long = 180_000,
    val areaDim: Int = 0,
    val areaShift: Int = 0,
    val areaEveryMs: Long = 60_000,
    /** Still areas the experimental way ([StillAreas]); otherwise as first built ([ClassicAreas]). */
    val areaExperimental: Boolean = false,
) {
    /** The screens it takes tiny copies of: the ones asked for, and the ones it shifts only while still. */
    val watching: Set<Int> get() = watchDisplays + if (whenStill) shiftDisplays else emptySet()

    val idle: Boolean get() = shiftDisplays.isEmpty() && watchDisplays.isEmpty() && areaDisplays.isEmpty()

    fun encode(): String = listOf(
        "shift=${ids(shiftDisplays)}",
        "r=$radius",
        "every=$everyMs",
        "still=${if (whenStill) stillMs else 0}",
        "center=${if (center) 1 else 0}",
        "watch=${ids(watchDisplays)}",
        "areas=${ids(areaDisplays)}",
        "astill=$areaStillMs",
        "adim=$areaDim",
        "ashift=$areaShift",
        "aevery=$areaEveryMs",
        "aexp=${if (areaExperimental) 1 else 0}",
    ).joinToString(";")

    companion object {
        /** Unknown parts take their defaults; a radius and the times are kept sensible. */
        fun decode(text: String?): EngineConfig {
            val fields = text.orEmpty().split(';').mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (key, value) -> key to value }
            val default = EngineConfig()
            val still = fields["still"]?.toLongOrNull() ?: 0L
            return EngineConfig(
                shiftDisplays = ids(fields["shift"]),
                radius = fields["r"]?.toIntOrNull()?.coerceIn(1, MAX_RADIUS) ?: default.radius,
                everyMs = fields["every"]?.toLongOrNull()?.coerceAtLeast(MIN_EVERY_MS) ?: default.everyMs,
                whenStill = still > 0,
                stillMs = if (still > 0) still else default.stillMs,
                center = fields["center"] == "1",
                watchDisplays = ids(fields["watch"]),
                areaDisplays = ids(fields["areas"]),
                areaStillMs = fields["astill"]?.toLongOrNull()?.coerceAtLeast(MIN_EVERY_MS) ?: default.areaStillMs,
                areaDim = fields["adim"]?.toIntOrNull()?.coerceIn(0, MAX_DIM) ?: default.areaDim,
                areaShift = fields["ashift"]?.toIntOrNull()?.coerceIn(0, MAX_RADIUS) ?: default.areaShift,
                areaEveryMs = fields["aevery"]?.toLongOrNull()?.coerceAtLeast(MIN_EVERY_MS) ?: default.areaEveryMs,
                areaExperimental = fields["aexp"] == "1",
            )
        }

        private fun ids(set: Set<Int>): String = set.sorted().joinToString("+").ifEmpty { "-" }

        private fun ids(text: String?): Set<Int> = text.orEmpty().split('+').mapNotNull { it.toIntOrNull() }.toSet()

        const val MAX_RADIUS = 10
        const val MIN_EVERY_MS = 1_000L
        const val MAX_DIM = 90
    }
}
