package io.github.thatonecodingperson.thortools.panel

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.PanelCloseTarget
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.CardSection
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PanelSettingsUiModel(
    val takesController: Boolean = true,
    val serviceRunning: Boolean = true,
    val closeAppTarget: PanelCloseTarget = PanelCloseTarget.OPENED_FROM,
    val cleanMemory: Boolean = false,
    val onlyAynCloses: Boolean = true,
    val layout: PanelLayout? = null,
    val previewWidthDp: Int = 620,
    val previewHeightDp: Int = 540,
)

@HiltViewModel
class PanelSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPrefsRepo,
    private val status: ServiceStatus,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            PanelSettingsUiModel(
                takesController = prefs.panelTakesController,
                serviceRunning = status.connected,
                closeAppTarget = prefs.panelCloseAppTarget,
                cleanMemory = prefs.panelCleanMemory,
                onlyAynCloses = prefs.panelOnlyAynCloses,
                layout = prefs.panelLayout,
            ),
        )
    val uiState: StateFlow<PanelSettingsUiModel> = _uiState.asStateFlow()

    init {
        val (width, height) = panelSizeDp(context)
        _uiState.update { it.copy(previewWidthDp = width, previewHeightDp = height) }
    }

    /** Back from the editor: the preview shows the panel as it is now. */
    fun refresh() = _uiState.update { it.copy(layout = prefs.panelLayout, serviceRunning = status.connected) }

    fun setTakesController(enabled: Boolean) {
        prefs.panelTakesController = enabled
        _uiState.update { it.copy(takesController = enabled) }
    }

    fun setOnlyAynCloses(on: Boolean) {
        prefs.panelOnlyAynCloses = on
        _uiState.update { it.copy(onlyAynCloses = on) }
    }

    fun setCleanMemory(on: Boolean) {
        prefs.panelCleanMemory = on
        _uiState.update { it.copy(cleanMemory = on) }
    }

    fun setCloseAppTarget(target: PanelCloseTarget) {
        prefs.panelCloseAppTarget = target
        _uiState.update { it.copy(closeAppTarget = target) }
    }

    fun openPanel() {
        val toggle = status.toggleQuickPanel
        _uiState.update { it.copy(serviceRunning = toggle != null) }
        toggle?.invoke()
    }
}

/**
 * The quick panel's settings as cards, like the Controller modes screen: the panel itself with a live preview and the
 * way into the editor, then how it opens and closes, then how some tiles behave. Two columns when wide.
 */
@Composable
fun PanelSettingsScreen(viewModel: PanelSettingsViewModel = hiltViewModel(), onEditPanel: () -> Unit, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(topBar = { SubTopAppBar(title = R.string.quickPanel, onBack = onBack) }) { padding ->
        CardColumns(
            padding = padding,
            left = { YourPanel(uiState, onEditPanel = onEditPanel, onOpen = viewModel::openPanel) },
            right = {
                CardSection(R.string.panelOpening) {
                    SwitchCard(
                        icon = Icons.Rounded.SportsEsports,
                        title = R.string.panelTakesController,
                        info = R.string.panelTakesControllerDescription,
                        checked = uiState.takesController,
                        onChange = viewModel::setTakesController,
                    )
                    SwitchCard(
                        icon = Icons.Rounded.Lock,
                        title = R.string.panelOnlyAynCloses,
                        info = R.string.panelOnlyAynClosesDescription,
                        checked = uiState.onlyAynCloses,
                        onChange = viewModel::setOnlyAynCloses,
                    )
                }
                CardSection(R.string.panelTiles, R.string.panelTilesIntro) {
                    CloseTargetCard(uiState.closeAppTarget, viewModel::setCloseAppTarget)
                    SwitchCard(
                        icon = Icons.Rounded.CleaningServices,
                        title = R.string.panelCleanMemory,
                        info = R.string.panelCleanMemoryDescription,
                        checked = uiState.cleanMemory,
                        onChange = viewModel::setCleanMemory,
                    )
                }
                Text(
                    text = stringResource(R.string.quickPanelFootnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
private fun YourPanel(uiState: PanelSettingsUiModel, onEditPanel: () -> Unit, onOpen: () -> Unit) {
    CardSection(R.string.panelYours, R.string.panelYoursIntro) {
        SettingsCard(contentPadding = PaddingValues()) {
            uiState.layout?.let { layout ->
                PanelPreview(
                    layout = layout,
                    page = 0,
                    state = samplePanelState(),
                    widthDp = uiState.previewWidthDp,
                    heightDp = uiState.previewHeightDp,
                    modifier = Modifier.fillMaxWidth().heightIn(max = PREVIEW_MAX_HEIGHT).padding(12.dp),
                )
            }
            CardRow(
                title = stringResource(R.string.editPanel),
                value = uiState.layout?.let { stringResource(R.string.panelPageCount, it.pages.size) },
                icon = Icons.Rounded.Edit,
                onClick = onEditPanel,
            )
            CardRow(
                title = stringResource(R.string.openQuickPanelNow),
                icon = Icons.Rounded.Dashboard,
                enabled = uiState.serviceRunning,
                onClick = onOpen,
            )
        }
        if (!uiState.serviceRunning) NoteCard(stringResource(R.string.serviceOffDescription), warning = true)
    }
}

/** Which app the Close the current app tile closes: the three choices in the card, no dialog. */
@Composable
private fun CloseTargetCard(selected: PanelCloseTarget, onSelect: (PanelCloseTarget) -> Unit) {
    SettingsCard {
        Text(stringResource(R.string.panelCloseTitle), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.panelClosePickInfo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.selectableGroup().padding(top = 4.dp)) {
            PanelCloseTarget.entries.forEach { target ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = target == selected, role = Role.RadioButton) { onSelect(target) }
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(selected = target == selected, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(stringResource(target.label), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(target.info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val PREVIEW_MAX_HEIGHT = 260.dp
