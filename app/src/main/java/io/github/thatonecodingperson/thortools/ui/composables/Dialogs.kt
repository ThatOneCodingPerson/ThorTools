package io.github.thatonecodingperson.thortools.ui.composables

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.coexist.Overlap
import kotlin.system.exitProcess

@Composable
fun UnsupportedDeviceDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            DialogButton(text = stringResource(id = R.string.accept)) {
                onAccept()
            }
        },
        dismissButton = {
            DialogButton(text = stringResource(id = R.string.close)) {
                exitProcess(0)
            }
        },
        title = {
            Text(text = stringResource(id = R.string.warning))
        },
        text = {
            Text(text = stringResource(id = R.string.incompatibleDevice))
        },
    )
}

@Composable
fun PServerNotAvailableDialog(onDiagnostics: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            DialogButton(text = stringResource(id = R.string.closeButSad)) {
                exitProcess(0)
            }
        },
        dismissButton = {
            DialogButton(text = stringResource(id = R.string.diagnostics), onClick = onDiagnostics)
        },
        title = {
            Text(text = stringResource(id = R.string.warning))
        },
        text = {
            Text(text = stringResource(id = R.string.pServerNotAvailable))
        },
    )
}

@Composable
fun DialogButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
    ) {
        Text(text = text)
    }
}

@Composable
fun DeleteConfirmDialog(onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            DialogButton(text = stringResource(id = R.string.delete), onClick = onDelete)
        },
        dismissButton = {
            DialogButton(text = stringResource(id = R.string.cancel), onClick = onDismiss)
        },
        text = {
            Text(text = stringResource(id = R.string.deleteConfirm))
        },
    )
}

/** Asked before single-press Home goes off while [count] hotkeys use Home: AYN would catch their taps first. */
@Composable
fun HomeOffDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { DialogButton(text = stringResource(R.string.homeOffKeep), onClick = onDismiss) },
        dismissButton = { DialogButton(text = stringResource(R.string.homeOffConfirm), onClick = onConfirm) },
        title = { Text(text = stringResource(R.string.homeOffTitle)) },
        text = { Text(text = pluralStringResource(R.plurals.homeOffText, count, count)) },
    )
}

@Composable
fun OverlapConfirmDialog(overlap: Overlap, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogButton(text = stringResource(id = R.string.enableAnyway), onClick = onConfirm)
        },
        dismissButton = {
            DialogButton(text = stringResource(id = R.string.cancel), onClick = onDismiss)
        },
        title = {
            Text(text = stringResource(id = R.string.confirmOverlapTitle))
        },
        text = {
            Text(text = stringResource(id = R.string.confirmOverlapText, stringResource(id = overlap.detail)))
        },
    )
}
