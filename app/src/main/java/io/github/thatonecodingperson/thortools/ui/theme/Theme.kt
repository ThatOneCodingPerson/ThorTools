package io.github.thatonecodingperson.thortools.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/** The active palette, for colours Material 3 has no role for (quick panel tiles, dim, corners). */
val LocalThorPalette = staticCompositionLocalOf { ThorThemes.default }

/** [palette] null means Android's dynamic colours (the "System colours" theme). */
@Composable
fun ThorToolsTheme(palette: ThorPalette? = ThorThemes.default, content: @Composable () -> Unit) {
    val dark = palette?.isDark ?: isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = palette?.toColorScheme() ?: if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val tokens = palette ?: colorScheme.toPalette(dark)

    CompositionLocalProvider(LocalThorPalette provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

private fun ThorPalette.toColorScheme(): ColorScheme {
    val background = Color(background)
    val surface = Color(surface)
    val surfaceVariant = Color(surfaceVariant)
    val primary = Color(primary)
    val onPrimary = Color(onPrimary)
    val onSurface = Color(onSurface)
    val container = Color(tileOn)
    val muted = lerp(onSurface, surface, 0.3f)
    val outline = lerp(onSurface, surface, 0.55f)
    return if (isDark) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = onSurface,
            secondary = primary, onSecondary = onPrimary, secondaryContainer = container, onSecondaryContainer = onSurface,
            tertiary = primary, onTertiary = onPrimary, tertiaryContainer = container, onTertiaryContainer = onSurface,
            background = background, onBackground = onSurface, surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = muted, outline = outline, outlineVariant = surfaceVariant,
            surfaceContainerLowest = background, surfaceContainerLow = surface, surfaceContainer = surface,
            surfaceContainerHigh = surfaceVariant, surfaceContainerHighest = surfaceVariant,
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = onSurface,
            secondary = primary, onSecondary = onPrimary, secondaryContainer = container, onSecondaryContainer = onSurface,
            tertiary = primary, onTertiary = onPrimary, tertiaryContainer = container, onTertiaryContainer = onSurface,
            background = background, onBackground = onSurface, surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = muted, outline = outline, outlineVariant = surfaceVariant,
            surfaceContainerLowest = surface, surfaceContainerLow = background, surfaceContainer = background,
            surfaceContainerHigh = surfaceVariant, surfaceContainerHighest = surfaceVariant,
        )
    }
}

private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

private fun ColorScheme.toPalette(dark: Boolean) = ThorPalette(
    id = ThorThemes.SYSTEM,
    name = "System",
    isDark = dark,
    background = background.argb(),
    surface = surface.argb(),
    surfaceVariant = surfaceVariant.argb(),
    primary = primary.argb(),
    onPrimary = onPrimary.argb(),
    onSurface = onSurface.argb(),
    tileOn = primaryContainer.argb(),
    tileOff = surfaceVariant.argb(),
    dimAmount = if (dark) 0.55f else 0.35f,
    cornerDp = 16,
    chart = if (dark) ThorPalette.DARK_CHART else ThorPalette.LIGHT_CHART,
)
