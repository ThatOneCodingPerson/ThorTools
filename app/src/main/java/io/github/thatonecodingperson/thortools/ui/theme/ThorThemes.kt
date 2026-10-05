package io.github.thatonecodingperson.thortools.ui.theme

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The colours of one theme, used by the app and the quick panel. Colours are ARGB longs so a palette can be stored
 * as text ([encode]), which is how user-made themes are stored.
 */
data class ThorPalette(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val background: Long,
    val surface: Long,
    val surfaceVariant: Long,
    val primary: Long,
    val onPrimary: Long,
    val onSurface: Long,
    val tileOn: Long,
    val tileOff: Long,
    val dimAmount: Float,
    val cornerDp: Int,
    @StringRes val label: Int? = null,
    /** Four distinct colours for charts such as the quick panel's stat rings. */
    val chart: List<Long> = DARK_CHART,
) {
    fun encode(): String = listOf(
        "id=$id",
        "name=${name.replace(SEPARATOR, ' ').replace('\n', ' ')}",
        "dark=${if (isDark) 1 else 0}",
        "background=${hex(background)}",
        "surface=${hex(surface)}",
        "surfaceVariant=${hex(surfaceVariant)}",
        "primary=${hex(primary)}",
        "onPrimary=${hex(onPrimary)}",
        "onSurface=${hex(onSurface)}",
        "tileOn=${hex(tileOn)}",
        "tileOff=${hex(tileOff)}",
        "dim=$dimAmount",
        "corner=$cornerDp",
        "chart=${chart.joinToString(",") { hex(it) }}",
    ).joinToString(SEPARATOR.toString())

    companion object {
        private const val SEPARATOR = ';'
        val DARK_CHART = listOf(0xFFF0629AL, 0xFF55D3F2L, 0xFFFFA94DL, 0xFF6FDA86L)
        val LIGHT_CHART = listOf(0xFFC2185BL, 0xFF00838FL, 0xFFE65100L, 0xFF2E7D32L)

        private fun hex(color: Long) = color.toString(16).padStart(8, '0').uppercase()

        /** Null for anything that isn't a complete, well-formed palette. */
        fun decode(text: String): ThorPalette? = runCatching {
            val values = text.split(SEPARATOR).associate { it.substringBefore('=') to it.substringAfter('=', "") }
            fun color(key: String) = values.getValue(key).toLong(16)
            ThorPalette(
                id = values.getValue("id").ifBlank { return null },
                name = values.getValue("name"),
                isDark = values.getValue("dark") == "1",
                background = color("background"),
                surface = color("surface"),
                surfaceVariant = color("surfaceVariant"),
                primary = color("primary"),
                onPrimary = color("onPrimary"),
                onSurface = color("onSurface"),
                tileOn = color("tileOn"),
                tileOff = color("tileOff"),
                dimAmount = values.getValue("dim").toFloat().coerceIn(0f, 1f),
                cornerDp = values.getValue("corner").toInt().coerceIn(0, 32),
                // Palettes saved before chart colours existed keep working.
                chart = values["chart"]?.split(',')?.map { it.toLong(16) }?.takeIf { it.size == 4 } ?: DARK_CHART,
            )
        }.getOrNull()
    }
}

object ThorThemes {
    /** Android's own dynamic colours, following the system's light or dark mode. */
    const val SYSTEM = "system"

    val builtIn = listOf(
        ThorPalette(
            id = "midnight", name = "Midnight", label = R.string.themeMidnight, isDark = true,
            background = 0xFF0F1115, surface = 0xFF181B21, surfaceVariant = 0xFF232730,
            primary = 0xFF7AA2F7, onPrimary = 0xFF0B1220, onSurface = 0xFFE6E8EE,
            tileOn = 0xFF2E4A7D, tileOff = 0xFF232730, dimAmount = 0.55f, cornerDp = 16,
        ),
        ThorPalette(
            id = "graphite", name = "Graphite", label = R.string.themeGraphite, isDark = true,
            background = 0xFF000000, surface = 0xFF111111, surfaceVariant = 0xFF1E1E1E,
            primary = 0xFFFFFFFF, onPrimary = 0xFF000000, onSurface = 0xFFFFFFFF,
            tileOn = 0xFF3A3A3A, tileOff = 0xFF1E1E1E, dimAmount = 0.6f, cornerDp = 12,
        ),
        ThorPalette(
            id = "glacier", name = "Glacier", label = R.string.themeGlacier, isDark = true,
            background = 0xFF0E1A20, surface = 0xFF14242C, surfaceVariant = 0xFF1D323C,
            primary = 0xFF6FD3E8, onPrimary = 0xFF00222B, onSurface = 0xFFE3F2F6,
            tileOn = 0xFF1F5563, tileOff = 0xFF1D323C, dimAmount = 0.5f, cornerDp = 18,
        ),
        ThorPalette(
            id = "ember", name = "Ember", label = R.string.themeEmber, isDark = true,
            background = 0xFF1A1210, surface = 0xFF241916, surfaceVariant = 0xFF33241F,
            primary = 0xFFFFA45C, onPrimary = 0xFF2B1300, onSurface = 0xFFF5E9E4,
            tileOn = 0xFF6B3A1C, tileOff = 0xFF33241F, dimAmount = 0.55f, cornerDp = 16,
        ),
        ThorPalette(
            id = "forest", name = "Forest", label = R.string.themeForest, isDark = true,
            background = 0xFF0F1712, surface = 0xFF16211A, surfaceVariant = 0xFF203026,
            primary = 0xFF7FD69B, onPrimary = 0xFF00210D, onSurface = 0xFFE5F1E8,
            tileOn = 0xFF2B5A3A, tileOff = 0xFF203026, dimAmount = 0.5f, cornerDp = 16,
        ),
        ThorPalette(
            id = "daylight", name = "Daylight", label = R.string.themeDaylight, isDark = false,
            background = 0xFFF6F7FB, surface = 0xFFFFFFFF, surfaceVariant = 0xFFE6E9F2,
            primary = 0xFF2F5BD3, onPrimary = 0xFFFFFFFF, onSurface = 0xFF161A22,
            tileOn = 0xFFC9D6FA, tileOff = 0xFFE6E9F2, dimAmount = 0.35f, cornerDp = 16, chart = ThorPalette.LIGHT_CHART,
        ),
    )

    val default: ThorPalette get() = builtIn.first()

    /** The palette for a stored theme id; null means [SYSTEM]. Unknown ids fall back to [default]. */
    fun resolve(id: String?, custom: List<ThorPalette> = emptyList()): ThorPalette? {
        if (id == SYSTEM) return null
        return (builtIn + custom).find { it.id == id } ?: default
    }

    /** WCAG contrast ratio between two opaque colours. */
    fun contrast(first: Long, second: Long): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    /** WCAG relative luminance, 0 (black) to 1 (white). */
    fun luminance(color: Long): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
