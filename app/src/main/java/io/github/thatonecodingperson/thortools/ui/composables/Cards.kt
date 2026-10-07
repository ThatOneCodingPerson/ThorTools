package io.github.thatonecodingperson.thortools.ui.composables

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Credit for an idea taken from elsewhere, with its link as a button so a controller can reach it. */
@Composable
fun CreditNote(text: String, linkLabel: String, url: String) {
    val links = LocalUriHandler.current
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { runCatching { links.openUri(url) } }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(linkLabel)
            }
        }
    }
}

/** Card screens show two columns side by side from this width on. */
val TWO_COLUMNS_WIDTH = 640.dp

/** The body of a card screen: one scrolling column, or [left] and [right] side by side when the screen is wide. */
@Composable
fun CardColumns(padding: PaddingValues, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
        if (maxWidth >= TWO_COLUMNS_WIDTH) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                ScrollingCardColumn(Modifier.weight(1f)) { left() }
                ScrollingCardColumn(Modifier.weight(1f)) { right() }
            }
        } else {
            ScrollingCardColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                left()
                right()
            }
        }
    }
}

@Composable
private fun ScrollingCardColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
    ) { content() }
}

/** A titled group of cards: a heading in the primary colour and, below it, what the group is about. */
@Composable
fun CardSection(@StringRes title: Int, @StringRes intro: Int? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (intro != null) {
            Text(stringResource(intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

/**
 * A card frame for content of its own; [highlighted] draws it like a switched-on card. Pass `PaddingValues()` as
 * [contentPadding] when the card holds [CardRow]s, which bring their own.
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = CARD_PADDING_H, vertical = CARD_PADDING_V),
    content: @Composable ColumnScope.() -> Unit,
) {
    CardFrame(highlighted, modifier) { Column(Modifier.padding(contentPadding), content = content) }
}

/**
 * One setting as a card: icon, title, what it does and a switch; a tap on the top part flips it. [rows] are what belongs
 * to the setting ([CardRow], [CardSwitchRow], [CardRowBox]), below it in the same card.
 */
@Composable
fun SwitchCard(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes info: Int,
    checked: Boolean,
    enabled: Boolean = true,
    rows: (@Composable ColumnScope.() -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    CardFrame(highlighted = checked && enabled) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
                .padding(horizontal = CARD_PADDING_H, vertical = CARD_PADDING_V),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(ICON_SIZE),
            )
            Column(Modifier.weight(1f).padding(horizontal = ICON_GAP)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(info), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
        }
        rows?.invoke(this)
    }
}

/**
 * A setting that opens something (a dialog, a list, another screen) as a card: icon, title, what it is, its current
 * [value] and an arrow. [rows] as for [SwitchCard].
 */
@Composable
fun LinkCard(
    icon: ImageVector,
    title: String,
    info: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    rows: (@Composable ColumnScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    CardFrame(highlighted = highlighted) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = CARD_PADDING_H, vertical = CARD_PADDING_V),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(ICON_SIZE),
            )
            Column(Modifier.weight(1f).padding(horizontal = ICON_GAP)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (info != null) Text(info, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            RowEnd(value, enabled)
        }
        rows?.invoke(this)
    }
}

/**
 * Something that belongs to the card's setting: a row under a thin line, with its current [value] and an arrow. Without
 * an [icon] it starts where the card's title starts. [danger] for what removes or resets. No [divider] for the first row
 * of a card made only of rows.
 */
@Composable
fun CardRow(
    title: String,
    value: String? = null,
    info: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    divider: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    if (divider) CardDivider()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(horizontal = CARD_PADDING_H, vertical = ROW_PADDING_V),
    ) {
        RowStart(icon, enabled)
        Column(Modifier.weight(1f)) {
            val color = when {
                !enabled -> colors.onSurface.copy(alpha = DISABLED_ALPHA)
                danger -> colors.error
                else -> colors.onSurface
            }
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            if (info != null) Text(info, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        RowEnd(value, enabled)
    }
}

/** A switch that belongs to the card's setting, as a row under a thin line. */
@Composable
fun CardSwitchRow(
    title: String,
    checked: Boolean,
    info: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    CardDivider()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(horizontal = CARD_PADDING_H, vertical = ROW_PADDING_V),
    ) {
        RowStart(icon, enabled)
        Column(Modifier.weight(1f).padding(end = ICON_GAP)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (info != null) Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

/** Something to tick that belongs to the card's setting, as a row under a thin line, the box where a switch row has its switch. */
@Composable
fun CardCheckRow(title: String, checked: Boolean, info: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    CardDivider()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Checkbox) { onChange(!checked) }
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(horizontal = CARD_PADDING_H, vertical = ROW_PADDING_V),
    ) {
        RowStart(null, enabled)
        Column(Modifier.weight(1f).padding(end = ICON_GAP)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = DISABLED_ALPHA),
            )
            if (info != null) Text(info, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

/** Content of its own (choice chips, say) that belongs to the card's setting, under a thin line, lined up with the title. */
@Composable
fun CardRowBox(content: @Composable ColumnScope.() -> Unit) {
    CardDivider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CARD_PADDING_H + ICON_SIZE + ICON_GAP, end = CARD_PADDING_H, top = ROW_PADDING_V, bottom = CARD_PADDING_V),
        content = content,
    )
}

/** A card's top line when the card is made of rows ([SettingsCard] with no padding): icon, title and what it is. */
@Composable
fun CardHeader(icon: ImageVector, title: String, info: String? = null, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = CARD_PADDING_H, vertical = CARD_PADDING_V),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(ICON_SIZE),
        )
        Column(Modifier.weight(1f).padding(start = ICON_GAP)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (info != null) Text(info, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/** One choice out of a few as a card: the header, and a chip per option below it; [selected] null while unknown. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceCard(
    icon: ImageVector,
    title: String,
    info: String?,
    options: List<Pair<T, String>>,
    selected: T?,
    enabled: Boolean = true,
    rows: (@Composable ColumnScope.() -> Unit)? = null,
    onSelect: (T) -> Unit,
) {
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(icon, title, info, enabled)
        CardRowBox {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (option, label) ->
                    FilterChip(
                        selected = option == selected,
                        enabled = enabled,
                        onClick = { onSelect(option) },
                        label = { Text(label) },
                        colors = cardChipColors(),
                    )
                }
            }
        }
        rows?.invoke(this)
    }
}

/** Choice chips inside a card: the chosen one in the accent colour, so it shows on a switched-on card too. */
@Composable
fun cardChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun CardFrame(highlighted: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted) colors.secondaryContainer else colors.surfaceContainer,
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) colors.primary else colors.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = CARD_PADDING_H))
}

@Composable
private fun RowStart(icon: ImageVector?, enabled: Boolean) {
    if (icon == null) {
        Spacer(Modifier.width(ICON_SIZE + ICON_GAP))
        return
    }
    val colors = MaterialTheme.colorScheme
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ICON_SIZE)) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(ROW_ICON_SIZE),
        )
    }
    Spacer(Modifier.width(ICON_GAP))
}

@Composable
private fun RowEnd(value: String?, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    if (value != null) {
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = ICON_GAP).widthIn(max = VALUE_MAX_WIDTH),
        )
    }
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
}

private val CARD_PADDING_H = 14.dp
private val CARD_PADDING_V = 12.dp
private val ROW_PADDING_V = 8.dp
private val ROW_MIN_HEIGHT = 48.dp
private val ICON_SIZE = 28.dp
private val ROW_ICON_SIZE = 22.dp
private val ICON_GAP = 12.dp
private val VALUE_MAX_WIDTH = 200.dp
private const val DISABLED_ALPHA = 0.38f
