package io.github.thatonecodingperson.thortools.display

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.ui.composables.DialogButton
import io.github.thatonecodingperson.thortools.ui.theme.ThemeBuilder

/** Picks one colour: a ready-made swatch, hue / saturation / lightness sliders, or typed hex. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(title: String, initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    // Kept as hue / saturation / lightness so the hue survives while saturation is at zero.
    var hsl by remember { mutableStateOf(ThemeBuilder.toHsl(initial).toList()) }
    var hexText by remember { mutableStateOf(ThemeBuilder.toHex(initial)) }
    val color = ThemeBuilder.fromHsl(hsl[0], hsl[1], hsl[2])

    fun setColor(picked: Long) {
        hsl = ThemeBuilder.toHsl(picked).toList()
        hexText = ThemeBuilder.toHex(picked)
    }

    fun setPart(index: Int, value: Float) {
        hsl = hsl.toMutableList().also { it[index] = value }
        hexText = ThemeBuilder.toHex(ThemeBuilder.fromHsl(hsl[0], hsl[1], hsl[2]))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(color))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { text ->
                            hexText = text
                            ThemeBuilder.parseHex(text)?.let { hsl = ThemeBuilder.toHsl(it).toList() }
                        },
                        label = { Text(stringResource(R.string.colorHex)) },
                        singleLine = true,
                        modifier = Modifier.width(160.dp),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeBuilder.SWATCHES.forEach { swatch ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(swatch))
                                .border(
                                    width = if (swatch == color) 3.dp else 1.dp,
                                    color = if (swatch == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape,
                                )
                                .clickable { setColor(swatch) },
                        )
                    }
                }
                HslSlider(R.string.colorHue, hsl[0], 0f..HUE_MAX) { setPart(0, it) }
                HslSlider(R.string.colorSaturation, hsl[1], 0f..1f) { setPart(1, it) }
                HslSlider(R.string.colorLightness, hsl[2], 0f..1f) { setPart(2, it) }
            }
        },
        confirmButton = { DialogButton(text = stringResource(R.string.done)) { onPick(color) } },
        dismissButton = { DialogButton(text = stringResource(R.string.cancel), onClick = onDismiss) },
    )
}

@Composable
private fun HslSlider(@StringRes label: Int, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(text = stringResource(label), style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

private const val HUE_MAX = 359f
