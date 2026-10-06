package io.github.thatonecodingperson.thortools.display

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.ui.composables.DialogButton
import io.github.thatonecodingperson.thortools.ui.theme.HslColor
import io.github.thatonecodingperson.thortools.ui.theme.ThemeBuilder

/** Picks one colour in a dialog, with [ColorControls]: a ready-made swatch, hue / saturation / lightness, or typed hex. */
@Composable
fun ColorPickerDialog(title: String, initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var hsl by remember { mutableStateOf(HslColor.of(initial)) }
    var hexText by remember { mutableStateOf(ThemeBuilder.toHex(initial)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ColorControls(
                hsl = hsl,
                hexText = hexText,
                onHsl = {
                    hsl = it
                    hexText = ThemeBuilder.toHex(it.color)
                },
                onHex = { text ->
                    hexText = text
                    ThemeBuilder.parseHex(text)?.let { hsl = HslColor.of(it) }
                },
                // Only on a screen too small for the controls.
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { DialogButton(text = stringResource(R.string.done)) { onPick(hsl.color) } },
        dismissButton = { DialogButton(text = stringResource(R.string.cancel), onClick = onDismiss) },
    )
}
