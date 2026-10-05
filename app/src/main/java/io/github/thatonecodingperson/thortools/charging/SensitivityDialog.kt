package io.github.thatonecodingperson.thortools.charging

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.ui.composables.DialogButton

private val Sensitivity.label: Int
    get() = when (this) {
        Sensitivity.LOW -> R.string.sensitivityLow
        Sensitivity.MEDIUM -> R.string.sensitivityMedium
        Sensitivity.HIGH -> R.string.sensitivityHigh
    }

@Composable
fun SensitivityDialog(initial: Sensitivity, onCancel: () -> Unit, onSave: (Sensitivity) -> Unit) {
    var selected by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.sensitivity)) },
        text = {
            Column {
                Sensitivity.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, role = Role.RadioButton) { selected = option }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(stringResource(option.label), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { DialogButton(text = stringResource(R.string.save)) { onSave(selected) } },
        dismissButton = { DialogButton(text = stringResource(R.string.cancel), onClick = onCancel) },
    )
}
