package io.github.thatonecodingperson.thortools.panel

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.ActionCategory
import io.github.thatonecodingperson.thortools.actions.ActionPickerDialog
import io.github.thatonecodingperson.thortools.actions.PickerItem
import io.github.thatonecodingperson.thortools.actions.icon
import io.github.thatonecodingperson.thortools.actions.toPickerItem
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.hotkeys.AppPicker
import io.github.thatonecodingperson.thortools.hotkeys.AppPickerMode
import io.github.thatonecodingperson.thortools.hotkeys.LaunchScreen
import io.github.thatonecodingperson.thortools.hotkeys.LaunchableApp
import io.github.thatonecodingperson.thortools.hotkeys.LaunchableApps
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.DialogButton
import io.github.thatonecodingperson.thortools.ui.composables.LinkCard
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PanelEditorUiModel(
    val layout: PanelLayout = PanelLayout.DEFAULT,
    val page: Int = 0,
    val previewWidthDp: Int = 620,
    val previewHeightDp: Int = 540,
    val showTilePicker: Boolean = false,
    val showResetConfirm: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showWidgetPicker: Boolean = false,
    val showAppPicker: Boolean = false,
    val apps: List<LaunchableApp> = emptyList(),
    val appsLoaded: Boolean = false,
    /** Names and icons of the apps on App shortcuts widgets, for the list and the preview. */
    val chosenApps: Map<String, PanelApp> = emptyMap(),
    /** Every Notes widget's note, by id, for the text fields and the preview. */
    val notes: Map<String, PanelNote> = emptyMap(),
    val mediaAllowed: Boolean = false,
    val screenshotsAllowed: Boolean = false,
    /** The apps opened last, for the Recent apps preview. */
    val recent: List<String> = emptyList(),
) {
    val currentPage: PanelPage get() = layout.pages[page.coerceIn(0, layout.pages.lastIndex)]
}

/** Edits the panel's pages and their widgets; every change is saved at once, and the panel uses it the next time it opens. */
@HiltViewModel
class PanelEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPrefsRepo,
    private val executor: ShellExecutor,
    private val launchableApps: LaunchableApps,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PanelEditorUiModel(layout = prefs.panelLayout))
    val uiState: StateFlow<PanelEditorUiModel> = _uiState.asStateFlow()
    private val panelNotes = PanelNotes(context)
    private val textSaves = mutableMapOf<String, Job>()

    init {
        val (width, height) = panelSizeDp(context)
        _uiState.update { it.copy(previewWidthDp = width, previewHeightDp = height) }
        loadWidgetData()
    }

    fun refresh() = _uiState.update {
        it.copy(
            mediaAllowed = mediaAllowed(),
            screenshotsAllowed = context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED,
        )
    }

    fun selectPage(index: Int) = _uiState.update { it.copy(page = index) }

    fun addPage() {
        change { it.addPage() }
        _uiState.update { it.copy(page = it.layout.pages.lastIndex) }
    }

    fun deleteClicked() = _uiState.update { it.copy(showDeleteConfirm = true) }

    fun deleteDismissed() = _uiState.update { it.copy(showDeleteConfirm = false) }

    fun deleteConfirmed() {
        change { it.removePage(_uiState.value.page) }
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun changeColumns(by: Int) = changePage { it.withColumns(it.columns + by) }

    fun moveTile(id: String, by: Int) = changePage { it.moveTile(id, by) }

    fun removeTile(id: String) = changePage { it.removeTile(id) }

    fun addTileClicked() = _uiState.update { it.copy(showTilePicker = true) }

    fun tilePickerDismissed() = _uiState.update { it.copy(showTilePicker = false) }

    fun addTile(id: String) {
        changePage { it.addTile(id) }
        _uiState.update { it.copy(showTilePicker = false) }
    }

    fun addWidgetClicked() = _uiState.update { it.copy(showWidgetPicker = true) }

    fun widgetPickerDismissed() = _uiState.update { it.copy(showWidgetPicker = false) }

    fun addWidget(type: WidgetType) {
        changePage { it.addWidget(type, note = if (type == WidgetType.NOTES) PanelNotes.newId() else "") }
        _uiState.update { it.copy(showWidgetPicker = false) }
        loadWidgetData()
    }

    fun removeWidget(type: WidgetType) = changePage { it.removeWidget(type) }

    fun moveWidget(type: WidgetType, by: Int) = changePage { it.moveWidget(type, by) }

    fun resizeWidget(type: WidgetType, size: WidgetSize) = changePage { it.resizeWidget(type, size) }

    fun toggleSlider(slider: LevelSlider) = changePage { page ->
        page.updateWidget(WidgetType.LEVELS) { widget ->
            widget.copy(sliders = if (slider in widget.sliders) widget.sliders - slider else widget.sliders + slider)
        }
    }

    fun addAppClicked() {
        _uiState.update { it.copy(showAppPicker = true) }
        if (_uiState.value.appsLoaded) return
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { launchableApps.load() }
            _uiState.update { it.copy(apps = apps, appsLoaded = true) }
        }
    }

    fun appPickerClosed() = _uiState.update { it.copy(showAppPicker = false) }

    fun addApp(packageName: String) {
        changePage { page ->
            page.updateWidget(WidgetType.APPS) {
                if (packageName in
                    it.apps
                ) {
                    it
                } else {
                    it.copy(apps = it.apps + packageName)
                }
            }
        }
        _uiState.update { it.copy(showAppPicker = false) }
        loadWidgetData()
    }

    fun removeApp(packageName: String) = changePage { page -> page.updateWidget(WidgetType.APPS) { it.copy(apps = it.apps - packageName) } }

    fun moveApp(packageName: String, by: Int) = changePage { page ->
        page.updateWidget(WidgetType.APPS) { widget ->
            val from = widget.apps.indexOf(packageName)
            if (from < 0) return@updateWidget widget
            val to = (from + by).coerceIn(0, widget.apps.lastIndex)
            widget.copy(apps = widget.apps.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    fun setAppsOn(type: WidgetType, screen: LaunchScreen) = changePage { page -> page.updateWidget(type) { it.copy(appsOn = screen) } }

    fun setRemind(minutes: Int) = changePage { page -> page.updateWidget(WidgetType.PLAY_TIMER) { it.copy(remind = minutes) } }

    /** Root grants the pictures permission, then the check runs again. */
    fun allowScreenshots() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                executor.executeAsRoot("pm grant ${context.packageName} ${Manifest.permission.READ_MEDIA_IMAGES}")
            }
            delay(ALLOW_SETTLE_MS)
            refresh()
        }
    }

    /** Shown at once, written to the note's file a moment after the last key. */
    fun setNoteText(id: String, text: String) {
        val limited = text.take(PanelNotes.MAX_TEXT)
        _uiState.update { it.copy(notes = it.notes + (id to (it.notes[id] ?: PanelNote()).copy(text = limited))) }
        textSaves.remove(id)?.cancel()
        textSaves[id] = viewModelScope.launch(Dispatchers.IO) {
            delay(TEXT_SAVE_DELAY_MS)
            panelNotes.saveText(id, limited)
        }
    }

    fun clearDrawing(id: String) {
        _uiState.update { it.copy(notes = it.notes + (id to (it.notes[id] ?: PanelNote()).copy(strokes = emptyList()))) }
        viewModelScope.launch(Dispatchers.IO) { panelNotes.saveStrokes(id, emptyList()) }
    }

    /** Allows Thor Tools' media listener as root (only its own entry), then checks again. */
    fun allowMedia() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                executor.executeAsRoot(
                    "cmd notification allow_listener ${ComponentName(context, MediaListener::class.java).flattenToString()}",
                )
            }
            delay(ALLOW_SETTLE_MS)
            refresh()
        }
    }

    fun resetClicked() = _uiState.update { it.copy(showResetConfirm = true) }

    fun resetDismissed() = _uiState.update { it.copy(showResetConfirm = false) }

    fun resetConfirmed() {
        change { PanelLayout.DEFAULT }
        _uiState.update { it.copy(page = 0, showResetConfirm = false) }
        loadWidgetData()
    }

    private fun mediaAllowed(): Boolean = context.getSystemService(
        NotificationManager::class.java,
    ).isNotificationListenerAccessGranted(ComponentName(context, MediaListener::class.java))

    /** The chosen apps' names and icons and the notes, off the main thread. */
    private fun loadWidgetData() {
        val widgets = _uiState.value.layout.pages.flatMap { it.widgets }
        val recent = if (widgets.any { it.type == WidgetType.RECENT }) prefs.recentApps else emptyList()
        val packages = (widgets.filter { it.type == WidgetType.APPS }.flatMap { it.apps } + recent).distinct()
        val noteIds = widgets.filter { it.type == WidgetType.NOTES }.map { it.note }.filter { it.isNotEmpty() }.distinct()
        _uiState.update { it.copy(recent = recent) }
        viewModelScope.launch {
            val (apps, notes) = withContext(Dispatchers.IO) {
                val packageManager = context.packageManager
                val apps = packages.mapNotNull { name ->
                    runCatching {
                        val info = packageManager.getApplicationInfo(name, 0)
                        name to PanelApp(packageManager.getApplicationLabel(info).toString(), packageManager.getApplicationIcon(info))
                    }.getOrNull()
                }.toMap()
                apps to noteIds.associateWith(panelNotes::load)
            }
            // Text typed meanwhile wins over what was on disk.
            _uiState.update { state -> state.copy(chosenApps = apps, notes = notes + state.notes.filterKeys { it in notes }) }
        }
    }

    private fun changePage(update: (PanelPage) -> PanelPage) = change { it.updatePage(_uiState.value.page, update) }

    private fun change(update: (PanelLayout) -> PanelLayout) {
        val layout = update(_uiState.value.layout)
        prefs.panelLayout = layout
        _uiState.update { it.copy(layout = layout, page = it.page.coerceIn(0, layout.pages.lastIndex)) }
    }

    private companion object {
        const val TEXT_SAVE_DELAY_MS = 600L
        const val ALLOW_SETTLE_MS = 500L
    }
}

private val WidgetType.icon: ImageVector
    get() = when (this) {
        WidgetType.TILES -> Icons.Rounded.GridView
        WidgetType.LEVELS -> Icons.Rounded.Tune
        WidgetType.DEVICE -> Icons.Rounded.Speed
        WidgetType.MEDIA -> Icons.Rounded.MusicNote
        WidgetType.BATTERY -> Icons.Rounded.BatteryChargingFull
        WidgetType.GRAPH -> Icons.AutoMirrored.Rounded.ShowChart
        WidgetType.APPS -> Icons.Rounded.Apps
        WidgetType.NOTES -> Icons.AutoMirrored.Rounded.StickyNote2
        WidgetType.PLAY_TIMER -> Icons.Rounded.HourglassTop
        WidgetType.RECENT -> Icons.Rounded.History
        WidgetType.CONTROLLER -> Icons.Rounded.SportsEsports
        WidgetType.CLOCK -> Icons.Rounded.Timer
        WidgetType.SCREENSHOTS -> Icons.Rounded.Screenshot
        WidgetType.STORAGE -> Icons.Rounded.Storage
        WidgetType.NETWORK -> Icons.Rounded.Wifi
        WidgetType.TOGGLES -> Icons.Rounded.ToggleOn
    }

@get:StringRes
private val WidgetType.label: Int
    get() = when (this) {
        WidgetType.TILES -> R.string.widgetTiles
        WidgetType.LEVELS -> R.string.widgetLevels
        WidgetType.DEVICE -> R.string.widgetDevice
        WidgetType.MEDIA -> R.string.widgetMedia
        WidgetType.BATTERY -> R.string.widgetBattery
        WidgetType.GRAPH -> R.string.widgetGraph
        WidgetType.APPS -> R.string.widgetApps
        WidgetType.NOTES -> R.string.widgetNotes
        WidgetType.PLAY_TIMER -> R.string.widgetPlayTimer
        WidgetType.RECENT -> R.string.widgetRecent
        WidgetType.CONTROLLER -> R.string.widgetController
        WidgetType.CLOCK -> R.string.widgetClock
        WidgetType.SCREENSHOTS -> R.string.widgetScreenshots
        WidgetType.STORAGE -> R.string.widgetStorage
        WidgetType.NETWORK -> R.string.widgetNetwork
        WidgetType.TOGGLES -> R.string.widgetToggles
    }

@get:StringRes
private val WidgetType.info: Int
    get() = when (this) {
        WidgetType.TILES -> R.string.widgetTilesInfo
        WidgetType.LEVELS -> R.string.widgetLevelsInfo
        WidgetType.DEVICE -> R.string.widgetDeviceInfo
        WidgetType.MEDIA -> R.string.widgetMediaInfo
        WidgetType.BATTERY -> R.string.widgetBatteryInfo
        WidgetType.GRAPH -> R.string.widgetGraphInfo
        WidgetType.APPS -> R.string.widgetAppsInfo
        WidgetType.NOTES -> R.string.widgetNotesInfo
        WidgetType.PLAY_TIMER -> R.string.widgetPlayTimerInfo
        WidgetType.RECENT -> R.string.widgetRecentInfo
        WidgetType.CONTROLLER -> R.string.widgetControllerInfo
        WidgetType.CLOCK -> R.string.widgetClockInfo
        WidgetType.SCREENSHOTS -> R.string.widgetScreenshotsInfo
        WidgetType.STORAGE -> R.string.widgetStorageInfo
        WidgetType.NETWORK -> R.string.widgetNetworkInfo
        WidgetType.TOGGLES -> R.string.widgetTogglesInfo
    }

@get:StringRes
private val LevelSlider.label: Int
    get() = when (this) {
        LevelSlider.VOLUME -> R.string.panelVolume
        LevelSlider.TOP -> R.string.panelLevelTop
        LevelSlider.BOTTOM -> R.string.panelLevelBottom
    }

@Composable
fun PanelEditorScreen(viewModel: PanelEditorViewModel = hiltViewModel(), onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val page = uiState.currentPage
    LaunchedEffect(Unit) { viewModel.refresh() }

    if (uiState.showAppPicker) {
        AppPicker(
            mode = AppPickerMode.OPEN_APP,
            apps = uiState.apps,
            loaded = uiState.appsLoaded,
            checked = emptySet(),
            onPick = viewModel::addApp,
            onClose = viewModel::appPickerClosed,
        )
        return
    }

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

    if (uiState.showWidgetPicker) {
        WidgetPickerDialog(
            missing = WidgetType.entries.filter {
                page.widget(it) == null
            },
            onPick = viewModel::addWidget,
            onDismiss = viewModel::widgetPickerDismissed,
        )
    }

    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::deleteDismissed,
            text = { Text(stringResource(R.string.panelEditorDeleteText, uiState.page + 1)) },
            confirmButton = { DialogButton(text = stringResource(R.string.panelEditorDeletePage), onClick = viewModel::deleteConfirmed) },
            dismissButton = { DialogButton(text = stringResource(R.string.cancel), onClick = viewModel::deleteDismissed) },
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
                    state = samplePanelState().copy(apps = uiState.chosenApps, notes = uiState.notes, recent = uiState.recent),
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f - PREVIEW_WEIGHT)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                Heading(stringResource(R.string.panelEditorPage, uiState.page + 1))
                Text(
                    text = stringResource(R.string.panelEditorWidgets),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (page.widgets.isEmpty()) Muted(stringResource(R.string.panelEditorNoWidgets))
                page.widgets.forEachIndexed { index, widget ->
                    WidgetEditor(
                        widget = widget,
                        page = page,
                        state = uiState,
                        canMoveUp = index > 0,
                        canMoveDown = index < page.widgets.lastIndex,
                        viewModel = viewModel,
                    )
                }
                if (WidgetGrid.rows(WidgetGrid.place(page.widgets)) > WidgetGrid.ROWS) Muted(stringResource(R.string.panelEditorTallPage))
                val left = WidgetType.entries.size - page.widgets.size
                if (left > 0) {
                    LinkCard(
                        icon = Icons.Rounded.Add,
                        title = stringResource(R.string.panelEditorAddWidget),
                        value = stringResource(R.string.panelEditorWidgetsLeft, left),
                        onClick = viewModel::addWidgetClicked,
                    )
                }
                SettingsCard(contentPadding = PaddingValues()) {
                    val canDelete = uiState.layout.pages.size > 1
                    if (canDelete) {
                        CardRow(
                            title = stringResource(R.string.panelEditorDeletePage),
                            icon = Icons.Rounded.Delete,
                            danger = true,
                            divider = false,
                            onClick = viewModel::deleteClicked,
                        )
                    }
                    CardRow(
                        title = stringResource(R.string.panelEditorReset),
                        icon = Icons.Rounded.RestartAlt,
                        divider = canDelete,
                        onClick = viewModel::resetClicked,
                    )
                }
            }
        }
    }
}

/** One widget of the page: what it is, its place in the order, its size, and its own options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetEditor(
    widget: PanelWidget,
    page: PanelPage,
    state: PanelEditorUiModel,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    viewModel: PanelEditorViewModel,
) {
    val type = widget.type
    SettingsCard(contentPadding = PaddingValues()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            WidgetSettings(widget, page, state, canMoveUp, canMoveDown, viewModel)
        }
        when (type) {
            WidgetType.TILES -> CardRow(
                title = stringResource(R.string.panelEditorAddIcons),
                icon = Icons.Rounded.Add,
                onClick = viewModel::addTileClicked,
            )
            WidgetType.APPS -> CardRow(
                title = stringResource(R.string.panelEditorAddApp),
                icon = Icons.Rounded.Add,
                onClick = viewModel::addAppClicked,
            )
            WidgetType.NOTES -> if (state.notes[widget.note]?.strokes?.isNotEmpty() == true) {
                CardRow(title = stringResource(R.string.panelEditorClearDrawing), danger = true, onClick = {
                    viewModel.clearDrawing(widget.note)
                })
            }
            WidgetType.MEDIA -> if (!state.mediaAllowed) {
                CardRow(
                    title = stringResource(R.string.panelMediaAllow),
                    info = stringResource(R.string.panelEditorMediaNotAllowed),
                    onClick = viewModel::allowMedia,
                )
            }
            WidgetType.SCREENSHOTS -> if (!state.screenshotsAllowed) {
                CardRow(
                    title = stringResource(R.string.panelMediaAllow),
                    info = stringResource(R.string.panelEditorShotsNotAllowed),
                    onClick = viewModel::allowScreenshots,
                )
            }
            else -> Unit
        }
    }
}

/** A widget's own settings: its name, order, size and options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetSettings(
    widget: PanelWidget,
    page: PanelPage,
    state: PanelEditorUiModel,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    viewModel: PanelEditorViewModel,
) {
    val type = widget.type
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(stringResource(type.label), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(type.info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { viewModel.moveWidget(type, -1) }, enabled = canMoveUp) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.moveUp))
            }
            IconButton(onClick = { viewModel.moveWidget(type, 1) }, enabled = canMoveDown) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.moveDown))
            }
            IconButton(onClick = { viewModel.removeWidget(type) }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.remove))
            }
        }
        Label(stringResource(R.string.panelEditorSize))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            type.sizes.forEach { size ->
                FilterChip(
                    selected = size == widget.size,
                    onClick = { viewModel.resizeWidget(type, size) },
                    label = { Text(stringResource(R.string.panelEditorSizeValue, size.columns, size.rows)) },
                )
            }
        }
        when (type) {
            WidgetType.TILES -> TilesOptions(page, viewModel)
            WidgetType.LEVELS -> {
                Label(stringResource(R.string.panelEditorSliders))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LevelSlider.entries.forEach { slider ->
                        FilterChip(
                            selected = slider in widget.sliders,
                            onClick = { viewModel.toggleSlider(slider) },
                            label = { Text(stringResource(slider.label)) },
                        )
                    }
                }
            }
            WidgetType.APPS -> AppsOptions(widget, state, viewModel)
            WidgetType.NOTES -> {
                OutlinedTextField(
                    value = state.notes[widget.note]?.text.orEmpty(),
                    onValueChange = { viewModel.setNoteText(widget.note, it) },
                    label = { Text(stringResource(R.string.panelEditorNoteText)) },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            WidgetType.MEDIA -> if (state.mediaAllowed) Muted(stringResource(R.string.panelEditorMediaAllowed))
            WidgetType.PLAY_TIMER -> {
                Label(stringResource(R.string.panelEditorRemind))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelLayout.REMINDERS.forEach { minutes ->
                        FilterChip(
                            selected = minutes == widget.remind,
                            onClick = { viewModel.setRemind(minutes) },
                            label = {
                                Text(
                                    if (minutes ==
                                        0
                                    ) {
                                        stringResource(R.string.panelEditorRemindOff)
                                    } else {
                                        stringResource(R.string.panelTimerMinutes, minutes)
                                    },
                                )
                            },
                        )
                    }
                }
                Muted(stringResource(R.string.panelEditorRemindInfo))
            }
            WidgetType.RECENT -> OpensOn(widget, viewModel)
            WidgetType.SCREENSHOTS -> {
                if (state.screenshotsAllowed) Muted(stringResource(R.string.panelEditorShotsAllowed))
                OpensOn(widget, viewModel)
            }
            WidgetType.DEVICE, WidgetType.BATTERY, WidgetType.GRAPH, WidgetType.CONTROLLER, WidgetType.CLOCK,
            WidgetType.STORAGE, WidgetType.NETWORK, WidgetType.TOGGLES,
            -> Unit
        }
    }
}

@Composable
private fun TilesOptions(page: PanelPage, viewModel: PanelEditorViewModel) {
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
    if (page.tiles.isEmpty()) Muted(stringResource(R.string.panelEditorNoIcons))
    page.tiles.forEachIndexed { index, id ->
        val action = PanelTiles.action(id)
        ListRow(
            icon = { Icon(action?.icon ?: Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            text = stringResource(action?.label ?: R.string.panelTileThorTools),
            canMoveUp = index > 0,
            canMoveDown = index < page.tiles.lastIndex,
            onMove = { by -> viewModel.moveTile(id, by) },
            onRemove = { viewModel.removeTile(id) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppsOptions(widget: PanelWidget, state: PanelEditorUiModel, viewModel: PanelEditorViewModel) {
    Label(stringResource(R.string.panelEditorApps))
    widget.apps.forEachIndexed { index, name ->
        val app = state.chosenApps[name]
        ListRow(
            icon = {
                if (app != null) {
                    Image(rememberDrawablePainter(app.icon), contentDescription = null, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Rounded.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            },
            text = app?.name ?: name,
            canMoveUp = index > 0,
            canMoveDown = index < widget.apps.lastIndex,
            onMove = { by -> viewModel.moveApp(name, by) },
            onRemove = { viewModel.removeApp(name) },
        )
    }
    OpensOn(widget, viewModel)
}

/** The screen what the widget opens goes to. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpensOn(widget: PanelWidget, viewModel: PanelEditorViewModel) {
    Label(stringResource(R.string.panelEditorAppsOn))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LaunchScreen.entries.forEach { screen ->
            FilterChip(
                selected = screen == widget.appsOn,
                onClick = { viewModel.setAppsOn(widget.type, screen) },
                label = { Text(stringResource(screen.label)) },
            )
        }
    }
}

@Composable
private fun WidgetPickerDialog(missing: List<WidgetType>, onPick: (WidgetType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.panelEditorAddWidget)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                missing.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(type) }
                            .padding(vertical = 10.dp),
                    ) {
                        Icon(type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(stringResource(type.label), style = MaterialTheme.typography.titleMedium)
                            Muted(stringResource(type.info))
                        }
                    }
                }
            }
        },
        confirmButton = { DialogButton(text = stringResource(R.string.cancel), onClick = onDismiss) },
    )
}

@Composable
private fun ListRow(
    icon: @Composable () -> Unit,
    text: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        icon()
        Text(
            text = text,
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

@Composable
private fun Heading(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Label(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
private fun Muted(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val PREVIEW_WEIGHT = 0.55f
