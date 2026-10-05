package io.github.thatonecodingperson.thortools.ui.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The colours a user picks for their own theme; [ThemeBuilder.build] works out the rest. */
data class ThemeBasics(val background: Long, val cards: Long, val text: Long, val accent: Long, val cornerDp: Int)

/**
 * Builds a full [ThorPalette] from four colours, checks readability, and holds the colour maths
 * the theme editor needs. Colours are opaque ARGB longs, like everywhere in [ThorPalette].
 */
object ThemeBuilder {
    const val MAX_CORNER_DP = 28

    /** Ready-made choices in the colour picker: greys, dark backgrounds, then accents around the colour wheel. */
    val SWATCHES: List<Long> = listOf(
        0xFF000000, 0xFF111111, 0xFF1E1E1E, 0xFF2B2B2B, 0xFF6B6B6B, 0xFFBDBDBD, 0xFFE6E9F2, 0xFFFFFFFF,
        0xFF0F1115, 0xFF0E1A20, 0xFF1A1210, 0xFF0F1712, 0xFF2F5BD3, 0xFF00838F, 0xFFC2185B, 0xFF2E7D32,
        0xFF7AA2F7, 0xFF6FD3E8, 0xFF7FD69B, 0xFFFFD166, 0xFFFFA45C, 0xFFFF6B6B, 0xFFF0629A, 0xFFB48EF5,
    )

    enum class Issue { TEXT_ON_BACKGROUND, TEXT_ON_CARDS, ACCENT_ON_CARDS }

    private const val BLACK = 0xFF000000
    private const val WHITE = 0xFFFFFFFF
    private const val DARK_BELOW_LUMINANCE = 0.2
    private const val READABLE = 4.5
    private const val VISIBLE = 3.0

    // How far inactive tiles lean toward the text colour, and how far active tiles may lean toward the accent.
    private const val TILE_TOWARD_TEXT = 0.10f
    private val TILE_TOWARD_ACCENT = listOf(0.35f, 0.25f, 0.15f, 0.08f)

    fun build(id: String, name: String, basics: ThemeBasics): ThorPalette {
        val dark = ThorThemes.luminance(basics.background) < DARK_BELOW_LUMINANCE
        val tileOff = mix(basics.cards, basics.text, TILE_TOWARD_TEXT)
        // Active tiles lean toward the accent, but never so far that text on them becomes hard to read.
        val tileOn = TILE_TOWARD_ACCENT.map { mix(basics.cards, basics.accent, it) }
            .firstOrNull { ThorThemes.contrast(basics.text, it) >= READABLE } ?: tileOff
        val onAccent = if (ThorThemes.contrast(BLACK, basics.accent) >= ThorThemes.contrast(WHITE, basics.accent)) BLACK else WHITE
        return ThorPalette(
            id = id,
            name = name,
            isDark = dark,
            background = basics.background,
            surface = basics.cards,
            surfaceVariant = tileOff,
            primary = basics.accent,
            onPrimary = onAccent,
            onSurface = basics.text,
            tileOn = tileOn,
            tileOff = tileOff,
            dimAmount = if (dark) DARK_DIM else LIGHT_DIM,
            cornerDp = basics.cornerDp.coerceIn(0, MAX_CORNER_DP),
            chart = if (dark) ThorPalette.DARK_CHART else ThorPalette.LIGHT_CHART,
        )
    }

    /** The four colours behind a palette, for editing it (or starting a new theme from it). */
    fun basics(palette: ThorPalette) = ThemeBasics(
        background = palette.background,
        cards = palette.surface,
        text = palette.onSurface,
        accent = palette.primary,
        cornerDp = palette.cornerDp,
    )

    fun issues(palette: ThorPalette): List<Issue> = buildList {
        if (ThorThemes.contrast(palette.onSurface, palette.background) < READABLE) add(Issue.TEXT_ON_BACKGROUND)
        if (ThorThemes.contrast(palette.onSurface, palette.surface) < READABLE) add(Issue.TEXT_ON_CARDS)
        if (ThorThemes.contrast(palette.primary, palette.surface) < VISIBLE) add(Issue.ACCENT_ON_CARDS)
    }

    /** [amount] 0 gives [from], 1 gives [to], channel by channel. */
    fun mix(from: Long, to: Long, amount: Float): Long {
        fun channel(shift: Int): Long {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * amount).roundToInt().toLong().coerceIn(0, 255) shl shift
        }
        return 0xFF000000 or channel(16) or channel(8) or channel(0)
    }

    fun toHex(color: Long): String = "#" + (color and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

    /** `#RRGGBB` or `RRGGBB`; anything else is null. */
    fun parseHex(text: String): Long? {
        val digits = text.trim().removePrefix("#")
        if (digits.length != 6) return null
        return digits.toLongOrNull(16)?.let { 0xFF000000 or it }
    }

    /** Hue 0..360, saturation and lightness 0..1. */
    fun toHsl(color: Long): FloatArray {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val lightness = (high + low) / 2
        val delta = high - low
        if (delta == 0f) return floatArrayOf(0f, 0f, lightness)
        val saturation = delta / (1 - abs(2 * lightness - 1))
        val hue = when (high) {
            r -> 60 * (((g - b) / delta).mod(6f))
            g -> 60 * ((b - r) / delta + 2)
            else -> 60 * ((r - g) / delta + 4)
        }
        return floatArrayOf(hue, saturation.coerceIn(0f, 1f), lightness)
    }

    fun fromHsl(hue: Float, saturation: Float, lightness: Float): Long {
        val chroma = (1 - abs(2 * lightness - 1)) * saturation
        val section = (hue.mod(360f)) / 60
        val x = chroma * (1 - abs(section.mod(2f) - 1))
        val (r, g, b) = when (section.toInt()) {
            0 -> Triple(chroma, x, 0f)
            1 -> Triple(x, chroma, 0f)
            2 -> Triple(0f, chroma, x)
            3 -> Triple(0f, x, chroma)
            4 -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
        val m = lightness - chroma / 2
        fun channel(value: Float) = ((value + m) * 255).roundToInt().toLong().coerceIn(0, 255)
        return 0xFF000000 or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    private const val DARK_DIM = 0.55f
    private const val LIGHT_DIM = 0.35f
}
