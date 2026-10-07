package io.github.thatonecodingperson.thortools.retroarch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.ButtonGroup
import io.github.thatonecodingperson.thortools.hotkeys.KeyCap
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardHeader
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.CardRowBox
import io.github.thatonecodingperson.thortools.ui.composables.CardSwitchRow
import io.github.thatonecodingperson.thortools.ui.composables.ChoiceCard
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchCard

/** The RetroArch assistant: RetroArch's settings in plain words, changed straight in its settings file. */
@Composable
fun RetroArchScreen(viewModel: RetroArchViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), viewModel::onBiosFolderPicked)
    val romFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), viewModel::onRomFolderPicked)
    val context = LocalContext.current
    val pickFolder: () -> Unit = { runCatching { folderPicker.launch(storageStart(context)) } }
    val pickRomFolder: () -> Unit = { runCatching { romFolderPicker.launch(storageStart(context)) } }
    Scaffold(topBar = { SubTopAppBar(title = R.string.raTitle, onBack = onBack) }) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            PrimaryScrollableTabRow(selectedTabIndex = state.tab.ordinal, edgePadding = 16.dp) {
                RaTab.entries.forEach { tab ->
                    Tab(selected = tab == state.tab, onClick = { viewModel.selectTab(tab) }, text = { Text(stringResource(tab.title)) })
                }
            }
            when (state.tab) {
                RaTab.OVERVIEW -> OverviewTab(state, viewModel)
                RaTab.LOOK -> LookTab(state, viewModel)
                RaTab.HOTKEYS -> HotkeysTab(state, viewModel)
                RaTab.BIOS -> BiosTab(state, viewModel, pickFolder)
                RaTab.CHEATS -> CheatsTab(state, viewModel, pickRomFolder)
            }
        }
    }
}

@Composable
private fun OverviewTab(state: RaUiModel, viewModel: RetroArchViewModel) {
    val context = LocalContext.current
    CardColumns(
        padding = PaddingValues(),
        left = {
            val found = state.found
            SettingsCard(contentPadding = PaddingValues()) {
                CardHeader(
                    Icons.Rounded.SportsEsports,
                    found?.let { stringResource(R.string.raFound, it.label, it.version ?: "?") } ?: stringResource(R.string.raTitleShort),
                    found?.packageName,
                )
                found?.configPath?.let(RetroArchFiles::shown)?.let { path ->
                    CardRow(
                        title = stringResource(R.string.raSettingsFile),
                        info = path,
                        value = stringResource(R.string.raCopyPath),
                        onClick = {
                            context.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.raSettingsFile), path))
                        },
                    )
                }
                if (found != null) {
                    CardRow(title = stringResource(R.string.raOpen), onClick = {
                        context.packageManager.getLaunchIntentForPackage(found.packageName)?.let {
                            runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    })
                }
                CardRow(
                    title = stringResource(R.string.raUndo),
                    info = stringResource(R.string.raUndoInfo),
                    enabled = state.undo != null && state.canChange,
                    onClick = viewModel::undo,
                )
            }
            Text(
                stringResource(R.string.raHowItWorks, RetroArchFiles.BACKUP_SUFFIX),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        right = {
            StatusNotes(state, viewModel)
            if (state.config != null) Checks(state, viewModel)
            ResultNote(state.note)
        },
    )
}

/** What the assistant found worth a look, each with the way to fix it. */
@Composable
private fun Checks(state: RaUiModel, viewModel: RetroArchViewModel) {
    var any = false
    if (state.systemDir != null && state.systemDirThere == false) {
        any = true
        NoteCard(
            stringResource(R.string.raCheckBios, state.systemDir),
            warning = true,
            actionLabel = stringResource(R.string.raBiosFix),
            onAction = { viewModel.selectTab(RaTab.BIOS) },
        )
    }
    if (!state.anyHotkey) {
        any = true
        NoteCard(
            stringResource(R.string.raCheckNoHotkeys),
            caution = true,
            actionLabel = stringResource(R.string.raCheckNoHotkeysAction),
            onAction = { viewModel.selectTab(RaTab.HOTKEYS) },
        )
    }
    if (state.config?.flag(RaUiModel.SAVE_ON_EXIT) == false) {
        any = true
        NoteCard(
            stringResource(R.string.raCheckSaveOff),
            caution = true,
            actionLabel = stringResource(R.string.raTurnOn),
            onAction = { viewModel.setSaveOnExit(true) },
        )
    }
    if (state.clashes.isNotEmpty()) {
        any = true
        NoteCard(
            stringResource(R.string.raCheckClashes, state.clashes.size),
            caution = true,
            actionLabel = stringResource(R.string.raShow),
            onAction = { viewModel.selectTab(RaTab.HOTKEYS) },
        )
    }
    if (state.otherStyle) {
        any = true
        NoteCard(
            stringResource(R.string.raOtherStyle),
            caution = true,
            actionLabel = stringResource(R.string.raOtherStyleAction),
            onAction = viewModel::applyForThisStyle,
        )
    }
    if (!any) NoteCard(stringResource(R.string.raCheckFine))
}

@Composable
private fun LookTab(state: RaUiModel, viewModel: RetroArchViewModel) {
    val enabled = state.canChange
    CardColumns(
        padding = PaddingValues(),
        left = {
            StatusNotes(state, viewModel)
            ChoiceCard(
                icon = Icons.Rounded.Dashboard,
                title = stringResource(R.string.raMenu),
                info = state.menu?.let { stringResource(it.info) } ?: stringResource(R.string.raMenuInfo),
                options = RaMenu.entries.map { it to stringResource(it.label) },
                selected = state.menu,
                enabled = enabled,
                onSelect = viewModel::setMenu,
            )
            Text(
                stringResource(R.string.raMenuNextTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchCard(
                icon = Icons.Rounded.Save,
                title = R.string.raSaveOnExit,
                info = R.string.raSaveOnExitInfo,
                checked = state.config?.flag(RaUiModel.SAVE_ON_EXIT) == true,
                enabled = enabled,
                rows = {
                    CardSwitchRow(
                        title = stringResource(R.string.raQuitTwice),
                        info = stringResource(R.string.raQuitTwiceInfo),
                        checked = state.config?.flag(RaUiModel.QUIT_TWICE) == true,
                        enabled = enabled,
                        onChange = viewModel::setQuitTwice,
                    )
                },
                onChange = viewModel::setSaveOnExit,
            )
        },
        right = {
            val fast = state.fastForward
            ChoiceCard(
                icon = Icons.Rounded.FastForward,
                title = stringResource(R.string.raFastForward),
                info = stringResource(R.string.raFastForwardInfo),
                options = RaUiModel.FAST_FORWARD_CHOICES.map { it to speedLabel(it, faster = true) },
                selected = RaUiModel.FAST_FORWARD_CHOICES.firstOrNull { RaUiModel.same(fast, it) },
                enabled = enabled,
                onSelect = viewModel::setFastForward,
            )
            val slow = state.slowMotion
            ChoiceCard(
                icon = Icons.Rounded.SlowMotionVideo,
                title = stringResource(R.string.raSlowMotion),
                info = stringResource(R.string.raSlowMotionInfo),
                options = RaUiModel.SLOW_MOTION_CHOICES.map { it to speedLabel(it, faster = false) },
                selected = RaUiModel.SLOW_MOTION_CHOICES.firstOrNull { RaUiModel.same(slow, it) },
                enabled = enabled,
                onSelect = viewModel::setSlowMotion,
            )
            ResultNote(state.note)
        },
    )
}

@Composable
private fun speedLabel(value: Float, faster: Boolean): String = when {
    value == 0f -> stringResource(R.string.raNoLimit)
    faster -> stringResource(R.string.raTimesFaster, value.toInt())
    else -> stringResource(R.string.raTimesSlower, value.toInt())
}

@Composable
private fun HotkeysTab(state: RaUiModel, viewModel: RetroArchViewModel) {
    CardColumns(
        padding = PaddingValues(),
        left = {
            StatusNotes(state, viewModel)
            Text(
                stringResource(R.string.raHotkeysIntro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsCard(contentPadding = PaddingValues()) {
                CardHeader(Icons.Rounded.Keyboard, stringResource(R.string.raSuggestion), stringResource(R.string.raSuggestionInfo))
                val applied = state.config?.let { RetroArchBinds.isSuggestion(it, state.xbox) } == true
                CardRow(
                    title = stringResource(if (applied) R.string.raSuggestionApplied else R.string.raSuggestionApply),
                    enabled = state.canChange && !applied,
                    onClick = viewModel::applySuggestion,
                )
            }
            HotkeyList(state, viewModel)
        },
        right = {
            HotkeyEditor(state, viewModel)
            ThorHotkeysCard(state, viewModel)
            if (state.otherStyle) {
                NoteCard(
                    stringResource(R.string.raOtherStyle),
                    caution = true,
                    actionLabel = stringResource(R.string.raOtherStyleAction),
                    onAction = viewModel::applyForThisStyle,
                )
            }
            ResultNote(state.note)
        },
    )
}

/** The hotkey enable button, then every hotkey with its buttons; a tap picks one for the editor. */
@Composable
private fun HotkeyList(state: RaUiModel, viewModel: RetroArchViewModel) {
    SettingsCard(contentPadding = PaddingValues(vertical = 6.dp)) {
        HotkeyLine(stringResource(R.string.raEnable), state, RaKey.Enable, viewModel)
        RaHotkey.entries.forEach { hotkey -> HotkeyLine(stringResource(hotkey.title), state, RaKey.Of(hotkey), viewModel) }
    }
}

@Composable
private fun HotkeyLine(title: String, state: RaUiModel, key: RaKey, viewModel: RetroArchViewModel) {
    val colors = MaterialTheme.colorScheme
    val selected = state.selected == key
    Surface(
        onClick = { viewModel.select(key) },
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.secondaryContainer else colors.surfaceContainer,
        border = if (selected) BorderStroke(1.dp, colors.primary) else null,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            BindingKeys(state, key)
        }
    }
}

/** A binding as keys: the enable button alone, or held with the hotkey's own button. */
@Composable
private fun BindingKeys(state: RaUiModel, key: RaKey) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (val binding = state.binding(key)) {
        RaBinding.None -> Text(stringResource(R.string.raNotSet), style = MaterialTheme.typography.labelLarge, color = muted)
        is RaBinding.Other -> Text(
            stringResource(R.string.raOtherBinding, binding.text),
            style = MaterialTheme.typography.labelMedium,
            color = muted,
        )
        is RaBinding.Thor -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val enable = state.enable
            if (key is RaKey.Of && enable != null) {
                KeyCap(enable, small = true)
                Text("+", style = MaterialTheme.typography.titleMedium, color = muted)
            }
            KeyCap(binding.button, small = true)
        }
    }
}

/** The chosen hotkey: what it does, and the Thor button for it. */
@Composable
private fun HotkeyEditor(state: RaUiModel, viewModel: RetroArchViewModel) {
    val key = state.selected
    val (title, info) = when (key) {
        RaKey.Enable -> stringResource(R.string.raEnable) to stringResource(R.string.raEnableInfo)
        is RaKey.Of -> stringResource(key.hotkey.title) to stringResource(key.hotkey.info)
    }
    val current = (state.binding(key) as? RaBinding.Thor)?.button
    val suggested = if (key is RaKey.Of) key.hotkey.suggested else PadButton.SELECT
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.SportsEsports, title, info)
        CardRowBox {
            Text(
                stringResource(if (key == RaKey.Enable) R.string.raPickEnable else R.string.raPickButton),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            ButtonPicker(
                selected = current,
                disabled = if (key is RaKey.Of) setOfNotNull(state.enable) else emptySet(),
                enabled = state.canChange,
                onPick = viewModel::pick,
            )
        }
        CardRow(
            title = stringResource(R.string.raUseSuggestion),
            value = stringResource(suggested.keyLabel),
            enabled = state.canChange && current != suggested,
            onClick = viewModel::useSuggestion,
        )
        CardRow(
            title = stringResource(R.string.raClear),
            danger = true,
            enabled = state.canChange && state.binding(key) != RaBinding.None,
            onClick = { viewModel.pick(null) },
        )
    }
    if (key == RaKey.Of(RaHotkey.EXIT) && state.config?.flag(RaUiModel.QUIT_TWICE) == true) {
        NoteCard(stringResource(R.string.raExitTwiceNote))
    }
}

/** The Thor's buttons RetroArch can use, grouped as they sit on the Thor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonPicker(selected: PadButton?, disabled: Set<PadButton>, enabled: Boolean, onPick: (PadButton) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PICKER_GROUPS.forEach { group ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(8.dp)) {
                    Text(
                        stringResource(group.label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        group.buttons.filter { it in RetroArchBinds.usable }.forEach { button ->
                            KeyCap(
                                button,
                                selected = button == selected,
                                enabled = enabled && button !in disabled,
                                small = true,
                                onClick = { onPick(button) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val PICKER_GROUPS = listOf(ButtonGroup.FACE, ButtonGroup.SHOULDERS, ButtonGroup.STICKS, ButtonGroup.MENU, ButtonGroup.DPAD)

/** Thor Tools' own hotkeys in RetroArch, and the ones that take a RetroArch hotkey's press. */
@Composable
private fun ThorHotkeysCard(state: RaUiModel, viewModel: RetroArchViewModel) {
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.Keyboard, stringResource(R.string.raThorHotkeys), stringResource(R.string.raThorHotkeysInfo))
        CardSwitchRow(
            title = stringResource(R.string.raThorHotkeysOn),
            info = stringResource(if (state.thorHotkeysOff) R.string.raThorHotkeysOffInfo else R.string.raThorHotkeysOnInfo),
            checked = !state.thorHotkeysOff,
            enabled = state.found != null,
            onChange = { on -> viewModel.setThorHotkeysOff(!on) },
        )
    }
    state.clashes.forEach { (hotkey, thor) ->
        NoteCard(
            stringResource(
                R.string.raClash,
                stringResource(thor.button.label),
                stringResource(thor.second?.label ?: thor.button.label),
                stringResource(thor.action.label),
                stringResource(hotkey.title),
            ),
            caution = true,
        )
    }
}

/** Why nothing can be changed right now, with what to do about it. */
@Composable
internal fun StatusNotes(state: RaUiModel, viewModel: RetroArchViewModel) {
    val links = LocalUriHandler.current
    val found = state.found
    when {
        state.retroArch == null -> NoteCard(stringResource(R.string.raLooking))
        state.retroArch == RetroArchState.NotInstalled -> NoteCard(
            stringResource(R.string.raMissing),
            warning = true,
            actionLabel = stringResource(R.string.raGetIt),
            onAction = { runCatching { links.openUri(RETROARCH_DOWNLOADS) } },
        )
        found != null && !found.rootWorks -> NoteCard(stringResource(R.string.raNoRoot), warning = true)
        found != null && found.configPath == null -> NoteCard(stringResource(R.string.raNeverOpened, found.label), caution = true)
        state.running -> NoteCard(
            stringResource(R.string.raRunning),
            caution = true,
            actionLabel = stringResource(R.string.raClose),
            onAction = viewModel::closeRetroArch,
        )
        state.writing -> NoteCard(stringResource(R.string.raWriting))
    }
}

@Composable
internal fun ResultNote(note: RaNote?) {
    when (note) {
        null -> Unit
        RaNote.Saved -> NoteCard(stringResource(R.string.raSaved))
        RaNote.Undone -> NoteCard(stringResource(R.string.raUndone))
        RaNote.UndoStale -> NoteCard(stringResource(R.string.raUndoStale), caution = true)
        RaNote.Running -> NoteCard(stringResource(R.string.raNotSavedRunning), warning = true)
        RaNote.Closed -> NoteCard(stringResource(R.string.raClosed))
        RaNote.CloseFailed -> NoteCard(stringResource(R.string.raCloseFailed), warning = true)
        is RaNote.Failed -> NoteCard(stringResource(R.string.raFailed, note.detail), warning = true)
    }
}

private const val RETROARCH_DOWNLOADS = "https://www.retroarch.com/?page=platforms"

/** Where the folder picker starts: the SD card when one is in, else the Thor's own storage. */
private fun storageStart(context: Context): Uri? = runCatching {
    val card = context.getSystemService(StorageManager::class.java).storageVolumes
        .firstOrNull { it.isRemovable && it.state == Environment.MEDIA_MOUNTED && it.uuid != null }
    DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE, "${card?.uuid ?: PRIMARY}:")
}.getOrNull()

private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
private const val PRIMARY = "primary"
