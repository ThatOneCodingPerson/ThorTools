package io.github.thatonecodingperson.thortools.ui.composables

import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.main.CheckboxPreferenceUiModel
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.L2R2Style
import io.github.thatonecodingperson.thortools.ui.theme.Typography
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsHeader(@StringRes name: Int) {
    Text(
        text = stringResource(id = name),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, bottom = 2.dp, top = 8.dp),
    )
}

/** A short label beside a preference title, e.g. that OdinTools has the same feature. */
@Composable
fun PreferenceTag(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun PreferenceDescription(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes description: Int,
    modifier: Modifier = Modifier,
    @StringRes tag: Int? = null,
) = PreferenceDescription(icon, stringResource(title), stringResource(description), modifier, tag?.let { stringResource(it) })

@Composable
fun PreferenceDescription(@DrawableRes icon: Int, title: String, description: String, modifier: Modifier = Modifier, tag: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(painter = painterResource(id = icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            modifier = Modifier.padding(start = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Text(text = title)
                if (tag != null) PreferenceTag(tag)
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun SwitchPreference(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes description: Int,
    state: Boolean,
    @StringRes tag: Int? = null,
    onChange: (newValue: Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onChange(state.not())
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PreferenceDescription(
            icon = icon,
            title = title,
            description = description,
            tag = tag,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        )
        Switch(
            checked = state,
            onCheckedChange = {
                onChange(it)
            },
        )
    }
}

@Composable
fun SwitchableTriggerPreference(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes description: Int,
    state: Boolean,
    onClick: () -> Unit,
    @StringRes tag: Int? = null,
    onChange: (newValue: Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PreferenceDescription(
            icon = icon,
            title = title,
            description = description,
            tag = tag,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        )
        Row(Modifier.height(IntrinsicSize.Min)) {
            VerticalDivider(
                modifier = Modifier
                    .width(17.dp)
                    .padding(end = 16.dp)
                    .fillMaxHeight(),
            )
            Switch(
                checked = state,
                onCheckedChange = {
                    onChange(it)
                },
            )
        }
    }
}

@Composable
fun TriggerPreference(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes description: Int,
    @StringRes tag: Int? = null,
    onClick: () -> Unit,
) = TriggerPreference(icon, stringResource(title), stringResource(description), tag?.let { stringResource(it) }, onClick)

@Composable
fun TriggerPreference(@DrawableRes icon: Int, title: String, description: String, tag: String? = null, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PreferenceDescription(icon = icon, title = title, description = description, tag = tag)
    }
}

/** A label and a dropdown whose selection always follows [selectedKey]. Items are key to display text. */
@Composable
fun ChoiceRow(
    @StringRes label: Int,
    items: List<Pair<String, String>>,
    selectedKey: String?,
    onSelected: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = stringResource(id = label),
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        )
        LargeDropdownMenu(
            items = items,
            selectedIndex = items.indexOfFirst { it.first == selectedKey },
            onItemSelected = { _, item -> onSelected(item.first) },
            selectedItemToString = { it.second },
        )
    }
}

@Composable
fun CheckBoxDialogPreference(
    items: List<CheckboxPreferenceUiModel>,
    @StringRes title: Int,
    minSelected: Int = 2,
    @StringRes message: Int? = null,
    onCancel: () -> Unit,
    onSave: (items: List<CheckboxPreferenceUiModel>) -> Unit,
) {
    AlertDialog(onDismissRequest = {}, confirmButton = {
        DialogButton(text = stringResource(id = R.string.save)) {
            onSave(items)
        }
    }, dismissButton = {
        DialogButton(text = stringResource(id = R.string.cancel), onCancel)
    }, title = {
        Text(text = stringResource(id = title))
    }, text = {
        // The Thor's screens are short; the descriptions make the rows tall.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (message != null) DialogMessage(stringResource(message))
            items.forEach { item ->
                fun canChangeCheckbox(): Boolean = item.checked.not() || items.count { it.checked } == minSelected + 1
                CheckboxDialogRow(
                    text = stringResource(id = item.text),
                    supporting = item.supporting?.let { stringResource(it) },
                    checked = item.checked,
                    enabled = canChangeCheckbox(),
                ) {
                    item.checked = it
                }
            }
        }
    })
}

@Composable
fun CheckboxDialogRow(text: String, enabled: Boolean, checked: Boolean, supporting: String? = null, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            if (enabled) onCheckedChange.invoke(!checked)
        },
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = text)
            if (supporting != null) SupportingText(supporting)
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** One option of [ChoiceDialogPreference]: [info] says what it does. */
data class ChoiceOption(val key: String, val title: String, val info: String)

/** Picks one option, each with a line saying what it does; a tap applies it and closes the dialog. */
@Composable
fun ChoiceDialogPreference(
    @StringRes title: Int,
    @StringRes message: Int,
    options: List<ChoiceOption>,
    selectedKey: String?,
    onSelect: (key: String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { DialogButton(text = stringResource(id = R.string.cancel), onClick = onDismiss) },
        title = { Text(text = stringResource(id = title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                DialogMessage(stringResource(message))
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.key) }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = option.key == selectedKey, onClick = { onSelect(option.key) })
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(text = option.title)
                            SupportingText(option.info)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun DialogMessage(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SupportingText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SaturationPreferenceDialog(initialValue: Float, onCancel: () -> Unit, onSave: (newVal: Float) -> Unit) {
    var userValue: Float by remember {
        mutableFloatStateOf(initialValue)
    }

    AlertDialog(onDismissRequest = {}, confirmButton = {
        DialogButton(text = stringResource(id = R.string.save)) {
            onSave(userValue)
        }
    }, dismissButton = {
        DialogButton(text = stringResource(id = R.string.cancel), onCancel)
    }, title = {
        Text(text = stringResource(id = R.string.saturation))
    }, text = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = userValue,
                valueRange = 0f..2f,
                steps = 19,
                onValueChange = {
                    userValue = it
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
            )
            Text(
                text = String.format(Locale.getDefault(), "%.1f", userValue),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    })
}

@Composable
fun VibrationPreferenceDialog(initialValue: Int, onCancel: () -> Unit, onSave: (newValue: Int) -> Unit) {
    var userValue: Int by remember {
        mutableIntStateOf(initialValue)
    }

    AlertDialog(onDismissRequest = {}, confirmButton = {
        DialogButton(text = stringResource(id = R.string.save)) {
            onSave(userValue)
        }
    }, dismissButton = {
        DialogButton(text = stringResource(id = R.string.cancel), onCancel)
    }, title = {
        Text(text = stringResource(id = R.string.vibrationStrength))
    }, text = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = userValue.toFloat(),
                valueRange = 1000f..5800f,
                steps = 23,
                onValueChange = {
                    userValue = it.toInt()
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
            )
            Text(
                text = "$userValue",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    })
}

@Composable
fun RemapButtonDialog(initialValue: Int, onCancel: () -> Unit, onReset: () -> Unit, onSave: (newValue: Int) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var userValue: Int by remember {
        mutableIntStateOf(initialValue)
    }

    Dialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent {
                    if (it.type == KeyEventType.KeyUp) {
                        userValue = it.nativeKeyEvent.keyCode
                    }
                    return@onKeyEvent true
                },
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Title
                Text(
                    text = stringResource(id = R.string.remapButton),
                    modifier = Modifier
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.padding(8.dp))
                // Body
                Text(
                    text = stringResource(id = R.string.pressAnyButton),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = KeyEvent.keyCodeToString(userValue),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.padding(12.dp))
                // Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onReset) {
                        Text(text = stringResource(id = R.string.setDefault))
                    }
                    Row {
                        TextButton(onClick = onCancel) {
                            Text(text = stringResource(id = R.string.cancel))
                        }
                        TextButton(onClick = { onSave(userValue) }) {
                            Text(text = stringResource(id = R.string.save))
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun ChargeLimitPreferenceDialog(initialValue: ClosedRange<Int>, onCancel: () -> Unit, onSave: (newValue: ClosedRange<Int>) -> Unit) {
    var userValue by remember {
        mutableStateOf(initialValue.start.toFloat()..initialValue.endInclusive.toFloat())
    }
    val start = userValue.start.roundToInt()
    val end = userValue.endInclusive.roundToInt()

    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            DialogButton(text = stringResource(id = R.string.save)) {
                onSave(start..end)
            }
        },
        dismissButton = {
            DialogButton(text = stringResource(id = R.string.cancel), onCancel)
        },
        title = {
            Text(text = stringResource(id = R.string.chargeLimit))
        },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RangeSlider(
                        value = userValue,
                        valueRange = 0f..100f,
                        steps = 9,
                        onValueChange = {
                            userValue = it
                        },
                        modifier = Modifier
                            .weight(1f),
                    )
                }
                Row {
                    Text(text = stringResource(id = R.string.chargeLimitPreferenceDialogOffAt, start))
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = stringResource(id = R.string.chargeLimitPreferenceDialogOnAt, end))
                }
                Row {
                    Text(
                        style = Typography.labelSmall,
                        text = stringResource(id = R.string.chargeLimitPreferenceDialogDescription),
                    )
                }
            }
        },
    )
}

@Composable
fun SpinnerDialogPreference(
    @StringRes label: Int,
    enabled: Boolean = true,
    items: List<Pair<String, String>>,
    selectedKey: String,
    onItemSelected: (selectedItem: String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedIndex = items.indexOfFirst { it.first == selectedKey }.coerceAtLeast(0)
    val selectedItem = items.getOrNull(selectedIndex)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .padding(8.dp),
    ) {
        OutlinedTextField(
            label = { Text(text = stringResource(id = label)) },
            value = selectedItem?.second ?: "",
            enabled = enabled,
            readOnly = true,
            onValueChange = { },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            interactionSource = interactionSource,
        )

        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect {
                expanded = it is PressInteraction.Release
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach {
                DropdownMenuItem(
                    text = {
                        Text(text = it.second)
                    },
                    onClick = {
                        expanded = false
                        onItemSelected(it.first)
                    },
                )
            }
        }
    }
}

@Composable
fun VideoOutputOverridePreferenceDialog(
    initialControllerStyle: ControllerStyle,
    initialL2R2Style: L2R2Style,
    onCancel: () -> Unit,
    onSave: (newControllerStyle: ControllerStyle, newL2R2Style: L2R2Style) -> Unit,
) {
    var controllerStyle: String by remember {
        mutableStateOf(initialControllerStyle.id)
    }
    var l2R2Style: String by remember {
        mutableStateOf(initialL2R2Style.id)
    }

    val controllerStyleList = listOf(
        ControllerStyle.Unknown.id to stringResource(id = R.string.noChange),
        ControllerStyle.Odin.id to stringResource(id = ControllerStyle.Odin.textRes),
        ControllerStyle.Xbox.id to stringResource(id = ControllerStyle.Xbox.textRes),
        ControllerStyle.Disconnect.id to stringResource(id = ControllerStyle.Disconnect.textRes),
    )
    val l2R2StyleList = listOf(
        L2R2Style.Unknown.id to stringResource(id = R.string.noChange),
        L2R2Style.Analog.id to stringResource(id = L2R2Style.Analog.textRes),
        L2R2Style.Digital.id to stringResource(id = L2R2Style.Digital.textRes),
        L2R2Style.Both.id to stringResource(id = L2R2Style.Both.textRes),
    )

    Dialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier,
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
            ) {
                // Title
                Text(
                    text = stringResource(id = R.string.videoOutputOverride),
                    modifier = Modifier
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.padding(8.dp))
                // Content
                SpinnerDialogPreference(
                    label = R.string.controllerStyle,
                    items = controllerStyleList,
                    selectedKey = controllerStyle,
                ) {
                    controllerStyle = it
                }
                SpinnerDialogPreference(
                    label = R.string.l2r2mode,
                    items = l2R2StyleList,
                    selectedKey = l2R2Style,
                ) {
                    l2R2Style = it
                }
                Spacer(modifier = Modifier.padding(12.dp))
                // Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.End),
                ) {
                    TextButton(onClick = onCancel) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                    TextButton(onClick = {
                        onSave(
                            ControllerStyle.getById(controllerStyle),
                            L2R2Style.getById(l2R2Style),
                        )
                    }) {
                        Text(text = stringResource(id = R.string.save))
                    }
                }
            }
        }
    }
}
