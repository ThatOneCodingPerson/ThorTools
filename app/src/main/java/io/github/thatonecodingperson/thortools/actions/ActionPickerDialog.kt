package io.github.thatonecodingperson.thortools.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thatonecodingperson.thortools.R

/** One choice in [ActionPickerDialog]: an action, or something else a caller offers (the panel's "Thor Tools" tile). */
data class PickerItem(val key: String, val icon: ImageVector, val label: String, val description: String, val category: ActionCategory)

@Composable
fun ThorAction.toPickerItem() = PickerItem(id, icon, stringResource(label), stringResource(description), category)

/** Picks one item by category and search instead of scrolling one long list. Returns the item's key. */
@Composable
fun ActionPickerDialog(items: List<PickerItem>, selectedKey: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by remember { mutableStateOf<ActionCategory?>(null) }
    val categoryLabels = ActionCategory.entries.associateWith { stringResource(it.label) }
    val shown = ActionSearch.filter(items, category, query, {
        it.category
    }) { "${it.label} ${it.description} ${categoryLabels[it.category]}" }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(DIALOG_FRACTION)
                .fillMaxHeight(DIALOG_FRACTION),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.actionPickerTitle),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel)) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.actionPickerSearch)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    FilterChip(selected = category == null, onClick = {
                        category = null
                    }, label = { Text(stringResource(R.string.actionPickerAll)) })
                    ActionCategory.entries.filter { wanted -> items.any { it.category == wanted } }.forEach { wanted ->
                        FilterChip(
                            selected = category == wanted,
                            onClick = { category = wanted },
                            label = { Text(categoryLabels.getValue(wanted)) },
                        )
                    }
                }
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.actionPickerNothing),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(shown, key = { it.key }) { item ->
                        PickerRow(item, selected = item.key == selectedKey) { onPick(item.key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(item: PickerItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text = item.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val DIALOG_FRACTION = 0.9f
