package io.github.thatonecodingperson.thortools.ui.composables

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

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

/** A card frame for content of its own; [highlighted] draws it like a switched-on card. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, highlighted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted) colors.secondaryContainer else colors.surfaceContainer,
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) colors.primary else colors.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), content = content)
    }
}

/** One setting as a card: icon, title, what it does and a switch. A tap anywhere on the card flips it. */
@Composable
fun SwitchCard(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes info: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val on = checked && enabled
    Surface(
        onClick = { onChange(!checked) },
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (on) colors.secondaryContainer else colors.surfaceContainer,
        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) colors.primary else colors.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(info), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
        }
    }
}
