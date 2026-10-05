package io.github.thatonecodingperson.thortools.setup

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyList
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.hotkeys.SuggestionProfile
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SwitchCard
import io.github.thatonecodingperson.thortools.ui.theme.ThorThemes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SetupWizardUiModel(
    val step: WizardStep = WizardStep.WELCOME,
    val odinToolsInstalled: Boolean = false,
    val profile: SuggestionProfile = SuggestionProfile.THOR,
    /** How many hotkeys the last "Add these hotkeys" added; null before. */
    val added: Int? = null,
    val takesController: Boolean = true,
    val onlyAynCloses: Boolean = true,
    val themeId: String = ThorThemes.default.id,
    val finished: Boolean = false,
)

/** The guided setup: welcome, access, hotkeys, the quick panel, a theme. Every choice is saved at once. */
@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    private val prefs: SharedPrefsRepo,
    private val status: ServiceStatus,
    odinTools: OdinToolsDetector,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SetupWizardUiModel(
            odinToolsInstalled = odinTools.isInstalled(),
            takesController = prefs.panelTakesController,
            onlyAynCloses = prefs.panelOnlyAynCloses,
            themeId = prefs.themeId,
        ),
    )
    val uiState: StateFlow<SetupWizardUiModel> = _uiState.asStateFlow()

    fun next() {
        val next = _uiState.value.step.next() ?: return finish()
        _uiState.update { it.copy(step = next) }
    }

    /** False on the first step: there is nothing before it. */
    fun back(): Boolean {
        val previous = _uiState.value.step.previous() ?: return false
        _uiState.update { it.copy(step = previous) }
        return true
    }

    /** Finishing and skipping both count: the wizard only opens again when asked for. */
    fun finish() {
        prefs.setupDone = true
        _uiState.update { it.copy(finished = true) }
    }

    fun selectProfile(profile: SuggestionProfile) = _uiState.update { it.copy(profile = profile, added = null) }

    /** The profile's hotkeys whose keys are still free; nothing already set up is replaced. */
    fun addHotkeys() {
        val current = prefs.hotkeys
        val profile = _uiState.value.profile
        val free = profile.hotkeys.count { suggestion -> current.none { it.clashes(suggestion) } }
        prefs.hotkeys = HotkeyList.addFreeSuggestions(current, profile.hotkeys)
        _uiState.update { it.copy(added = free) }
    }

    fun setTakesController(on: Boolean) {
        prefs.panelTakesController = on
        _uiState.update { it.copy(takesController = on) }
    }

    fun setOnlyAynCloses(on: Boolean) {
        prefs.panelOnlyAynCloses = on
        _uiState.update { it.copy(onlyAynCloses = on) }
    }

    fun openPanel() {
        status.toggleQuickPanel?.invoke()
    }

    fun selectTheme(id: String) {
        prefs.themeId = id
        _uiState.update { it.copy(themeId = id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(viewModel: SetupWizardViewModel = hiltViewModel(), onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.finished) { if (state.finished) onClose() }
    BackHandler { if (!viewModel.back()) viewModel.finish() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wizardTitle)) },
                actions = {
                    if (state.step != WizardStep.DONE) TextButton(onClick = viewModel::finish) { Text(stringResource(R.string.wizardSkip)) }
                },
            )
        },
        bottomBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.wizardStepOf, state.step.number, WizardStep.entries.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.step.previous() !=
                    null
                ) {
                    OutlinedButton(onClick = { viewModel.back() }) { Text(stringResource(R.string.wizardBack)) }
                }
                Button(onClick = viewModel::next) {
                    Text(stringResource(if (state.step == WizardStep.DONE) R.string.wizardFinish else R.string.wizardNext))
                }
            }
        },
    ) { padding ->
        Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .widthIn(max = MAX_WIDTH)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                when (state.step) {
                    WizardStep.WELCOME -> Welcome(state)
                    WizardStep.ACCESS -> Access()
                    WizardStep.HOTKEYS -> Hotkeys(state, viewModel)
                    WizardStep.PANEL -> Panel(state, viewModel)
                    WizardStep.THEME -> Theme(state, viewModel)
                    WizardStep.DONE -> Done()
                }
            }
        }
    }
}

@Composable
private fun Heading(@StringRes title: Int, @StringRes intro: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    Text(stringResource(intro), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Points(vararg points: Int) {
    SettingsCard {
        points.forEach { point ->
            Text("• " + stringResource(point), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
        }
    }
}

@Composable
private fun Welcome(state: SetupWizardUiModel) {
    Heading(R.string.wizardWelcome, R.string.wizardWelcomeIntro)
    Points(
        R.string.wizardPointPanel,
        R.string.wizardPointHotkeys,
        R.string.wizardPointScreens,
        R.string.wizardPointPower,
        R.string.wizardPointProfiles,
    )
    if (state.odinToolsInstalled) NoteCard(stringResource(R.string.wizardOdinTools))
}

/** The same list as Permissions & access, with one button that fixes everything it can. */
@Composable
private fun Access(viewModel: PermissionsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    SystemScreenLauncher(viewModel)
    Heading(R.string.wizardAccess, R.string.wizardAccessIntro)
    if (state.report.needsAttention) {
        FilledTonalButton(onClick = viewModel::fixAll) { Text(stringResource(R.string.wizardFixAll)) }
    } else if (state.report.states.isNotEmpty()) {
        NoteCard(stringResource(R.string.wizardAccessOk))
    }
    if (state.report.state(AccessCheck.ROOT) == AccessState.MISSING) NoteCard(stringResource(R.string.wizardNoRoot), warning = true)
    SettingsCard { AccessList(state.report, viewModel::fix) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hotkeys(state: SetupWizardUiModel, viewModel: SetupWizardViewModel) {
    Heading(R.string.wizardHotkeys, R.string.wizardHotkeysIntro)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SuggestionProfile.entries.forEach { profile ->
            FilterChip(
                selected = profile == state.profile,
                onClick = { viewModel.selectProfile(profile) },
                label = { Text(stringResource(profile.title)) },
            )
        }
    }
    Text(
        stringResource(state.profile.info),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FilledTonalButton(onClick = viewModel::addHotkeys) { Text(stringResource(R.string.wizardAddHotkeys)) }
    state.added?.let { added ->
        NoteCard(
            stringResource(
                if (added >
                    0
                ) {
                    R.string.wizardHotkeysAdded
                } else {
                    R.string.wizardHotkeysNoneFree
                },
                added,
            ),
        )
    }
    Text(
        stringResource(R.string.wizardHotkeysLater),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Panel(state: SetupWizardUiModel, viewModel: SetupWizardViewModel) {
    Heading(R.string.wizardPanel, R.string.wizardPanelIntro)
    SwitchCard(
        icon = Icons.Rounded.SportsEsports,
        title = R.string.panelTakesController,
        info = R.string.panelTakesControllerDescription,
        checked = state.takesController,
        onChange = viewModel::setTakesController,
    )
    SwitchCard(
        icon = Icons.Rounded.Lock,
        title = R.string.panelOnlyAynCloses,
        info = R.string.panelOnlyAynClosesDescription,
        checked = state.onlyAynCloses,
        onChange = viewModel::setOnlyAynCloses,
    )
    OutlinedButton(onClick = viewModel::openPanel) { Text(stringResource(R.string.wizardTryPanel)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Theme(state: SetupWizardUiModel, viewModel: SetupWizardViewModel) {
    Heading(R.string.wizardTheme, R.string.wizardThemeIntro)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThorThemes.builtIn.forEach { palette ->
            FilterChip(
                selected = palette.id == state.themeId,
                onClick = { viewModel.selectTheme(palette.id) },
                label = { Text(palette.label?.let { stringResource(it) } ?: palette.name) },
            )
        }
        FilterChip(
            selected = state.themeId == ThorThemes.SYSTEM,
            onClick = { viewModel.selectTheme(ThorThemes.SYSTEM) },
            label = { Text(stringResource(R.string.themeSystem)) },
        )
    }
    Text(
        stringResource(R.string.wizardThemeLater),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Done() {
    Heading(R.string.wizardDone, R.string.wizardDoneIntro)
    Points(R.string.wizardDonePower, R.string.wizardDoneProfiles, R.string.wizardDoneEditor, R.string.wizardDoneAgain)
    Spacer(Modifier.padding(4.dp))
}

private val MAX_WIDTH = 760.dp
