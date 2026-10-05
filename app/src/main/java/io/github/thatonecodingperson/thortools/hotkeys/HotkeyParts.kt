package io.github.thatonecodingperson.thortools.hotkeys

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.thatonecodingperson.thortools.R

private const val DISABLED_ALPHA = 0.38f

/** A button drawn as a key: big in the editor's grid, [small] in lists. */
@Composable
fun KeyCap(
    button: PadButton,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    small: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (small) 6.dp else 10.dp)
    val container = if (selected) colors.primary else colors.surfaceContainerHigh
    val content = when {
        selected -> colors.onPrimary
        enabled -> colors.onSurface
        else -> colors.onSurface.copy(alpha = DISABLED_ALPHA)
    }
    val border = if (selected) null else BorderStroke(1.dp, if (enabled) colors.outline else colors.outline.copy(alpha = DISABLED_ALPHA))
    val label: @Composable () -> Unit = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .defaultMinSize(minWidth = if (small) 28.dp else 56.dp, minHeight = if (small) 24.dp else 44.dp)
                .padding(horizontal = if (small) 6.dp else 12.dp),
        ) {
            Text(
                text = stringResource(button.keyLabel),
                style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
    if (onClick == null) {
        Surface(shape = shape, color = container, contentColor = content, border = border, modifier = modifier, content = label)
    } else {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            color = container,
            contentColor = content,
            border = border,
            modifier = modifier,
            content = label,
        )
    }
}

/** A trigger as keys and its press: [Home] + [A] double. */
@Composable
fun TriggerKeys(button: PadButton, second: PadButton?, press: PressKind, small: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 8.dp)) {
        KeyCap(button, small = small)
        if (second != null) {
            Text(text = "+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            KeyCap(second, small = small)
        }
        Text(
            text = stringResource(press.short),
            style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The same trigger in words: "AYN: tap" or "Hold Home + double tap A". */
@Composable
fun triggerText(button: PadButton, second: PadButton?, press: PressKind): String = if (second == null) {
    stringResource(R.string.hotkeyTriggerSingle, stringResource(button.label), stringResource(press.label))
} else {
    stringResource(R.string.hotkeyTriggerCombo, stringResource(button.label), stringResource(press.label), stringResource(second.label))
}

/** Every button, grouped the way they sit on the Thor; one tap picks a button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonGrid(selected: PadButton?, disabled: Set<PadButton>, onPick: (PadButton) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ButtonGroup.entries.forEach { group ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = stringResource(group.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        group.buttons.forEach { button ->
                            KeyCap(
                                button = button,
                                selected = button == selected,
                                enabled = button !in disabled,
                                onClick = { onPick(button) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A highlighted note with an optional button, e.g. a setting the hotkeys need. */
@Composable
fun NoteCard(
    text: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    caution: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (warning) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (warning) colors.onErrorContainer else colors.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Icon(
                imageVector = when {
                    warning -> Icons.Rounded.WarningAmber
                    caution -> Icons.Rounded.PriorityHigh
                    else -> Icons.Rounded.Info
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(text = text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            if (actionLabel != null) FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
