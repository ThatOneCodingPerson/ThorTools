package io.github.thatonecodingperson.thortools.display

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.ui.theme.HslColor
import io.github.thatonecodingperson.thortools.ui.theme.ThemeBuilder
import kotlin.math.roundToInt

/**
 * Picks a colour, compact enough to need no scrolling: the colour with its hex code (and [trailing] beside them),
 * ready-made swatches in a row that scrolls sideways, and hue, saturation and lightness sliders. Holds no state: the
 * caller keeps [hsl] and [hexText].
 */
@Composable
fun ColorControls(
    hsl: HslColor,
    hexText: String,
    onHsl: (HslColor) -> Unit,
    onHex: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val color = hsl.color
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(shape)
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape),
            )
            OutlinedTextField(
                value = hexText,
                onValueChange = onHex,
                label = { Text(stringResource(R.string.colorHex)) },
                singleLine = true,
                modifier = Modifier.width(132.dp),
            )
            trailing()
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(ThemeBuilder.SWATCHES) { swatch ->
                val chosen = swatch == color
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        .border(
                            width = if (chosen) 3.dp else 1.dp,
                            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onHsl(HslColor.of(swatch)) },
                )
            }
        }
        SliderRow(R.string.colorHue, hsl.hue, 0f..HUE_MAX, stringResource(R.string.colorDegrees, hsl.hue.roundToInt())) {
            onHsl(hsl.copy(hue = it))
        }
        SliderRow(R.string.colorSaturation, hsl.saturation, 0f..1f, percent(hsl.saturation)) { onHsl(hsl.copy(saturation = it)) }
        SliderRow(R.string.colorLightness, hsl.lightness, 0f..1f, percent(hsl.lightness)) { onHsl(hsl.copy(lightness = it)) }
    }
}

/** One labelled slider on a single line, with its value at the end. */
@Composable
fun SliderRow(
    @StringRes label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(LABEL_WIDTH))
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, modifier = Modifier.weight(1f))
        Text(
            valueText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(VALUE_WIDTH),
        )
    }
}

@Composable
private fun percent(fraction: Float): String = stringResource(R.string.colorPercent, (fraction * 100).roundToInt())

private const val HUE_MAX = 359f
private val LABEL_WIDTH = 92.dp
private val VALUE_WIDTH = 52.dp
