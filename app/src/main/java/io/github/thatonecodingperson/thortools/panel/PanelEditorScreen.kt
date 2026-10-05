package io.github.thatonecodingperson.thortools.panel

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.ActionCategory
import io.github.thatonecodingperson.thortools.actions.ActionPickerDialog
import io.github.thatonecodingperson.thortools.actions.PickerItem
import io.github.thatonecodingperson.thortools.actions.icon
import io.github.thatonecodingperson.thortools.actions.toPickerItem
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.ui.composables.DialogButton
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PanelEditorUiModel(
    val layout: PanelLayout = PanelLayout.DEFAULT,
    val page: Int = 0,
    val previewWidthDp: Int = 620,
    val previewHeightDp: Int = 540,
    val showTilePicker: Boolean = false,
    val showResetConfirm: Boolean = false,
) {
    val currentPage: PanelPage get() = layout.pages[page.coerceIn(0, layout.pages.lastIndex)]
}

/** Edits the panel's pages; every change is saved at once, and the panel uses it the next time it opens. */
@HiltViewModel
class PanelEditorViewModel @Inject constructor(@ApplicationContext private val context: Context, private val prefs: SharedPrefsRepo) :
    ViewModel() {

    private val _uiState = MutableStateFlow(PanelEditorUiModel(layout = prefs.panelLayout))
    val uiState: StateFlow<PanelEditorUiModel> = _uiState.asStateFlow()

    init {
        val (width, height) = panelSizeDp(context)
        _uiState.update { it.copy(previewWidthDp = width, previewHeightDp = height) }
    }

    fun selectPage(index: Int) = _uiState.update { it.copy(page = index) }

    fun addPage() {
        change { it.addPage() }
        _uiState.update { it.copy(page = it.layout.pages.lastIndex) }
    }

    fun deletePage() = change { it.removePage(_uiState.value.page) }

    fun changeColumns(by: Int) = changePage { it.withColumns(it.columns + by) }

    fun toggleWidget(widget: PanelWidget) = changePage { it.toggle(widget) }

    fun moveTile(id: String, by: Int) = changePage { it.moveTile(id, by) }

    fun removeTile(id: String) = changePage { it.removeTile(id) }

    fun addTileClicked() = _uiState.update { it.copy(showTilePicker = true) }

    fun tilePickerDismissed() = _uiState.update { it.copy(showTilePicker = false) }

    fun addTile(id: String) {
        changePage { it.addTile(id) }
        _uiState.update { it.copy(showTilePicker = false) }
    }

    fun resetClicked() = _uiState.update { it.copy(showResetConfirm = true) }

    fun resetDismissed() = _uiState.update { it.copy(showResetConfirm = false) }

    fun resetConfirmed() {
        change { PanelLayout.DEFAULT }
        _uiState.update { it.copy(page = 0, showResetConfirm = false) }
    }

    private fun changePage(update: (PanelPage) -> PanelPage) = change { it.updatePage(_uiState.value.page, update) }

    private fun change(update: (PanelLayout) -> PanelLayout) {
        val layout = update(_uiState.value.layout)
        prefs.panelLayout = layout
        _uiState.update { it.copy(layout = layout, page = it.page.coerceIn(0, layout.pages.lastIndex)) }
    }
}

@Composable
fun PanelEditorScreen(viewModel: PanelEditorViewModel = hiltViewModel(), onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val page = uiState.currentPage

    if (uiState.showTilePicker) {
        val thorTools = PickerItem(
            key = PanelTiles.THOR_TOOLS,
            icon = Icons.Rounded.Settings,
            label = stringResource(R.string.panelTileThorTools),
            description = stringResource(R.string.panelTileThorToolsInfo),
            category = ActionCategory.APPS,
        )
        val items = PanelTiles.actions.map { it.toPickerItem() } + thorTools
        ActionPickerDialog(
            items = items.filter { it.key !in page.tiles },
            selectedKey = null,
            onPick = viewModel::addTile,
            onDismiss = viewModel::tilePickerDismissed,
        )
    }

    if (uiState.showResetConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::resetDismissed,
            text = { Text(stringResource(R.string.panelEditorResetText)) },
            confirmButton = { DialogButton(text = stringResource(R.string.panelEditorResetConfirm), onClick = viewModel::resetConfirmed) },
            dismissButton = { DialogButton(text = stringResource(R.string.cancel), onClick = viewModel::resetDismissed) },
        )
    }

    Scaffold(topBar = { SubTopAppBar(title = R.string.editPanel, onBack = onBack) }) { contentPadding ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(PREVIEW_WEIGHT)
                    .fillMaxHeight(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    uiState.layout.pages.indices.forEach { index ->
                        FilterChip(
                            selected = index == uiState.page,
                            onClick = { viewModel.selectPage(index) },
                            label = { Text(stringResource(R.string.panelEditorPage, index + 1)) },
                        )
                    }
                    if (uiState.layout.pages.size < PanelLayout.MAX_PAGES) {
                        AssistChip(
                            onClick = viewModel::addPage,
                            label = { Text(stringResource(R.string.panelEditorAddPage)) },
                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
                PanelPreview(
                    layout = uiState.layout,
                    page = uiState.page,
                    state = samplePanelState(),
                    widthDp = uiState.previewWidthDp,
                    heightDp = uiState.previewHeightDp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.panelEditorHint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f - PREVIEW_WEIGHT)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                Heading(stringResource(R.string.panelEditorPage, uiState.page + 1))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.panelEditorColumns), modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.changeColumns(-1) }, enabled = page.columns > PanelPage.MIN_COLUMNS) {
                        Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.fewer))
                    }
                    Text(text = page.columns.toString(), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { viewModel.changeColumns(1) }, enabled = page.columns < PanelPage.MAX_COLUMNS) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.more))
                    }
                }
                WidgetSwitch(R.string.widgetLevels, R.string.widgetLevelsInfo, PanelWidget.LEVELS in page.widgets) {
                    viewModel.toggleWidget(PanelWidget.LEVELS)
                }
                WidgetSwitch(R.string.widgetDevice, R.string.widgetDeviceInfo, PanelWidget.DEVICE in page.widgets) {
                    viewModel.toggleWidget(PanelWidget.DEVICE)
                }

                Heading(stringResource(R.string.panelEditorIcons))
                if (page.tiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.panelEditorNoIcons),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                page.tiles.forEachIndexed { index, id ->
                    TileRow(
                        id = id,
                        canMoveUp = index > 0,
                        canMoveDown = index < page.tiles.lastIndex,
                        onMove = { by -> viewModel.moveTile(id, by) },
                        onRemove = { viewModel.removeTile(id) },
                    )
                }
                FilledTonalButton(onClick = viewModel::addTileClicked, modifier = Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(text = stringResource(R.string.panelEditorAddIcons), modifier = Modifier.padding(start = 8.dp))
                }
                if (uiState.layout.pages.size > 1) {
                    OutlinedButton(onClick = viewModel::deletePage) {
                        Text(text = stringResource(R.string.panelEditorDeletePage), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = viewModel::resetClicked, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(text = stringResource(R.string.panelEditorReset))
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun WidgetSwitch(@StringRes title: Int, @StringRes description: Int, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(title))
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun TileRow(id: String, canMoveUp: Boolean, canMoveDown: Boolean, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    val action = PanelTiles.action(id)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(action?.icon ?: Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(action?.label ?: R.string.panelTileThorTools),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.moveUp))
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.moveDown))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.remove))
        }
    }
}

private const val PREVIEW_WEIGHT = 0.55f
