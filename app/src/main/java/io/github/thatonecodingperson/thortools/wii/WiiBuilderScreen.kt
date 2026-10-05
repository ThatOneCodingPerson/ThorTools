package io.github.thatonecodingperson.thortools.wii

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import io.github.thatonecodingperson.thortools.ui.composables.CreditNote
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchCard
import io.github.thatonecodingperson.thortools.ui.composables.cardChipColors

/** The Thor button groups a Wii button can take, in the order they sit on the Thor. */
private val PICKER_GROUPS = listOf(
    ButtonGroup.FACE,
    ButtonGroup.SHOULDERS,
    ButtonGroup.STICKS,
    ButtonGroup.MENU,
    ButtonGroup.DPAD,
    ButtonGroup.LEFT_STICK,
    ButtonGroup.RIGHT_STICK,
)

/** Builds Wii Remote profiles for Dolphin: one tab per way of holding the Wii controls. */
@Composable
fun WiiBuilderScreen(viewModel: WiiBuilderViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), viewModel::onAccessPicked)
    val pickFolder: () -> Unit = { viewModel.pickerStart()?.let { runCatching { picker.launch(it) } } }
    Scaffold(topBar = { SubTopAppBar(title = R.string.wiiTitle, onBack = onBack) }) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            PrimaryScrollableTabRow(selectedTabIndex = state.setup.ordinal, edgePadding = 16.dp) {
                WiiSetup.entries.forEach { setup ->
                    Tab(
                        selected = setup == state.setup,
                        onClick = { viewModel.selectSetup(setup) },
                        text = { Text(stringResource(setup.title)) },
                    )
                }
            }
            CardColumns(
                padding = PaddingValues(),
                left = {
                    Text(
                        stringResource(state.setup.info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ControllerCard(state.setup, state.mapping, state.control, viewModel::select)
                    Text(
                        stringResource(R.string.wiiBothStyles),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CreditNote(stringResource(R.string.wiiCredit), stringResource(R.string.wiiCreditLink), WiiSetup.LAYOUT_CREDIT_URL)
                },
                right = {
                    ControlCard(state.mapping, state.control, state.comboPart, viewModel)
                    OptionsCard(state.mapping, state.vibrates, viewModel)
                    SaveCard(state, viewModel, pickFolder)
                    SaveResultNote(state, viewModel, pickFolder)
                },
            )
        }
    }
}

/** The tab's Wii controller drawn as keys, each with the Thor button under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControllerCard(setup: WiiSetup, mapping: WiiMapping, selected: WiiControl, onSelect: (WiiControl) -> Unit) {
    val key: @Composable (WiiControl) -> Unit = { control ->
        WiiKey(control, mapping, control == selected, round = control == WiiControl.REMOTE_A) { onSelect(control) }
    }
    SettingsCard {
        Text(
            stringResource(R.string.wiiTapHint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        when (setup) {
            WiiSetup.NUNCHUK -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Body(R.string.wiiRemoteBody) { RemoteKeys(key) }
                Body(R.string.wiiNunchukBody) {
                    key(WiiControl.NUNCHUK_STICK)
                    key(WiiControl.NUNCHUK_C)
                    key(WiiControl.NUNCHUK_Z)
                    key(WiiControl.NUNCHUK_SHAKE)
                }
            }
            WiiSetup.POINTING -> Body(R.string.wiiRemoteBody) { RemoteKeys(key) }
            WiiSetup.SIDEWAYS -> Body(R.string.wiiRemoteSidewaysBody) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    key(WiiControl.REMOTE_DPAD)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        key(WiiControl.REMOTE_A)
                        key(WiiControl.REMOTE_B)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        key(WiiControl.REMOTE_MINUS)
                        key(WiiControl.REMOTE_HOME)
                        key(WiiControl.REMOTE_PLUS)
                    }
                    key(WiiControl.REMOTE_1)
                    key(WiiControl.REMOTE_2)
                }
            }
            WiiSetup.CLASSIC -> Body(R.string.wiiClassicBody) { ClassicKeys(key) }
        }
        val motion = setup.controls.filter { it in MOTION_KEYS }
        if (motion.isNotEmpty()) {
            Text(
                stringResource(R.string.wiiMotionRow),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                motion.forEach { key(it) }
            }
        }
        val hotkeys = setup.controls.filter { it.kind == WiiKind.COMBO }
        if (hotkeys.isNotEmpty()) {
            Text(
                stringResource(R.string.wiiHotkeysRow),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hotkeys.forEach { key(it) }
            }
        }
    }
}

private val MOTION_KEYS = setOf(WiiControl.POINTER, WiiControl.RECENTER, WiiControl.SHAKE, WiiControl.TILT, WiiControl.SWING)

/** A controller's outline: its name and its keys in a rounded body. */
@Composable
private fun Body(title: Int, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            ) { content() }
        }
    }
}

/** The Wii Remote held upright, top to bottom. */
@Composable
private fun RemoteKeys(key: @Composable (WiiControl) -> Unit) {
    key(WiiControl.REMOTE_DPAD)
    key(WiiControl.REMOTE_A)
    key(WiiControl.REMOTE_B)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        key(WiiControl.REMOTE_MINUS)
        key(WiiControl.REMOTE_HOME)
        key(WiiControl.REMOTE_PLUS)
    }
    key(WiiControl.REMOTE_1)
    key(WiiControl.REMOTE_2)
}

/** The Classic Controller: shoulders on top, D-pad and sticks at the sides, the buttons as a diamond. */
@Composable
private fun ClassicKeys(key: @Composable (WiiControl) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        key(WiiControl.CLASSIC_L)
        key(WiiControl.CLASSIC_ZL)
        Spacer(Modifier.width(24.dp))
        key(WiiControl.CLASSIC_ZR)
        key(WiiControl.CLASSIC_R)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            key(WiiControl.CLASSIC_DPAD)
            key(WiiControl.CLASSIC_LEFT_STICK)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            key(WiiControl.CLASSIC_MINUS)
            key(WiiControl.CLASSIC_HOME)
            key(WiiControl.CLASSIC_PLUS)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            key(WiiControl.CLASSIC_X)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                key(WiiControl.CLASSIC_Y)
                key(WiiControl.CLASSIC_A)
            }
            key(WiiControl.CLASSIC_B)
            key(WiiControl.CLASSIC_RIGHT_STICK)
        }
    }
}

/** One Wii key: the label printed on the Wii controller, and below it what it is on the Thor. */
@Composable
private fun WiiKey(control: WiiControl, mapping: WiiMapping, selected: Boolean, round: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val thor = thorText(mapping, control)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = if (round) CircleShape else RoundedCornerShape(10.dp),
            color = if (selected) colors.primary else colors.surfaceContainerHighest,
            contentColor = if (selected) colors.onPrimary else colors.onSurface,
            border = if (selected) null else BorderStroke(1.dp, colors.outline),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(minWidth = if (round) 48.dp else 44.dp, minHeight = if (round) 48.dp else 36.dp)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    control.cap ?: stringResource(control.title),
                    style = if (control.cap != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        Text(
            thor ?: stringResource(R.string.wiiNotMapped),
            style = MaterialTheme.typography.labelSmall,
            color = if (thor != null) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** What [control] is on the Thor in a few letters, or null when it is left out. */
@Composable
private fun thorText(mapping: WiiMapping, control: WiiControl): String? = when (control.kind) {
    WiiKind.BUTTON -> mapping.buttons[control]?.let { stringResource(it.keyLabel) }
    WiiKind.STICK -> mapping.sticks[control]?.takeIf { it.isNotEmpty() }
        ?.sortedBy { it.ordinal }?.map { stringResource(shortLabel(it)) }?.joinToString(" + ")
    WiiKind.COMBO -> mapping.combos[control]?.let { comboText(it) }
}

@Composable
private fun comboText(combo: Pair<PadButton, PadButton>): String =
    stringResource(R.string.wiiCombo, stringResource(combo.first.keyLabel), stringResource(combo.second.keyLabel))

private fun shortLabel(source: StickSource): Int = when (source) {
    StickSource.DPAD -> R.string.wiiShortDpad
    StickSource.LEFT -> R.string.wiiShortLeft
    StickSource.RIGHT -> R.string.wiiShortRight
}

/** The chosen Wii control: what it does, and the Thor button or sticks it takes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlCard(mapping: WiiMapping, control: WiiControl, comboPart: Int, viewModel: WiiBuilderViewModel) {
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.SportsEsports, stringResource(control.title), stringResource(control.info))
        CardRowBox {
            Text(
                stringResource(R.string.wiiOnThor),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            when (control.kind) {
                WiiKind.BUTTON -> ThorButtonPicker(mapping.buttons[control], viewModel::pickButton)
                WiiKind.STICK -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StickSource.entries.forEach { source ->
                            FilterChip(
                                selected = source in mapping.sticks[control].orEmpty(),
                                onClick = { viewModel.toggleSource(source) },
                                label = { Text(stringResource(source.label)) },
                                colors = cardChipColors(),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.wiiSourcesHint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WiiKind.COMBO -> {
                    val combo = mapping.combos[control]
                    val none = stringResource(R.string.wiiNotMapped)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        FilterChip(
                            selected = comboPart == 0,
                            onClick = { viewModel.selectComboPart(0) },
                            label = {
                                Text(stringResource(R.string.wiiComboHold, combo?.first?.let { stringResource(it.keyLabel) } ?: none))
                            },
                            colors = cardChipColors(),
                        )
                        FilterChip(
                            selected = comboPart == 1,
                            onClick = { viewModel.selectComboPart(1) },
                            label = {
                                Text(stringResource(R.string.wiiComboPress, combo?.second?.let { stringResource(it.keyLabel) } ?: none))
                            },
                            colors = cardChipColors(),
                        )
                    }
                    ThorButtonPicker(if (comboPart == 0) combo?.first else combo?.second, viewModel::pickButton)
                    if (mapping.brokenCombo(control)) {
                        Text(
                            stringResource(R.string.wiiComboSame),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
        val suggested = suggestionText(mapping.setup, control)
        CardRow(
            title = stringResource(R.string.wiiUseSuggested),
            value = suggested ?: stringResource(R.string.wiiNotMapped),
            enabled = !mapping.isSuggested(control),
            onClick = viewModel::useSuggested,
        )
        val mapped = when (control.kind) {
            WiiKind.BUTTON -> mapping.buttons[control] != null
            WiiKind.COMBO -> mapping.combos[control] != null
            WiiKind.STICK -> false
        }
        if (mapped) {
            CardRow(title = stringResource(R.string.wiiLeaveOut), onClick = { viewModel.pickButton(null) })
        }
    }
    val sharing = mapping.sharing(control)
    if (sharing.isNotEmpty()) {
        val names = sharing.map { stringResource(it.title) }.joinToString(", ")
        NoteCard(stringResource(R.string.wiiSharing, names), caution = true)
    }
}

@Composable
private fun suggestionText(setup: WiiSetup, control: WiiControl): String? = when (control.kind) {
    WiiKind.BUTTON -> setup.suggestedButtons[control]?.let { stringResource(it.keyLabel) }
    WiiKind.STICK -> setup.suggestedSticks[control]?.sortedBy { it.ordinal }?.map { stringResource(shortLabel(it)) }?.joinToString(" + ")
    WiiKind.COMBO -> setup.suggestedCombos[control]?.let { comboText(it) }
}

/** The Thor's buttons as keys, grouped as they sit on the Thor; a tap on the chosen one keeps it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThorButtonPicker(selected: PadButton?, onPick: (PadButton) -> Unit) {
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
                        group.buttons.filter { it in WII_BUTTONS }.forEach { button ->
                            KeyCap(button, selected = button == selected, small = true, onClick = { onPick(button) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionsCard(mapping: WiiMapping, vibrates: Boolean, viewModel: WiiBuilderViewModel) {
    SwitchCard(
        icon = Icons.Rounded.Vibration,
        title = R.string.wiiRumble,
        info = if (vibrates) R.string.wiiRumbleInfo else R.string.wiiRumbleNone,
        checked = mapping.rumble && vibrates,
        enabled = vibrates,
        rows = if (mapping.setup.hasPointer) {
            {
                CardSwitchRow(
                    title = stringResource(R.string.wiiRelative),
                    info = stringResource(R.string.wiiRelativeInfo),
                    checked = mapping.relativePointer,
                    onChange = viewModel::setRelative,
                )
            }
        } else {
            null
        },
        onChange = viewModel::setRumble,
    )
}

/** The profile's name and where it goes: straight into Dolphin, or a copy in Downloads. */
@Composable
private fun SaveCard(state: WiiUiModel, viewModel: WiiBuilderViewModel, pickFolder: () -> Unit) {
    val context = LocalContext.current
    val dolphin = state.dolphin
    val status = when (dolphin) {
        null -> stringResource(R.string.wiiDolphinLooking)
        DolphinState.NotInstalled -> stringResource(R.string.wiiDolphinMissing)
        is DolphinState.NeverOpened -> stringResource(R.string.wiiDolphinNeverOpened, dolphin.label)
        is DolphinState.Found -> when {
            state.access -> stringResource(R.string.wiiDolphinFoundAccess, dolphin.label)
            dolphin.userDir != null -> stringResource(R.string.wiiDolphinFound, dolphin.label)
            dolphin.authority != null -> stringResource(R.string.wiiDolphinFoundAsk, dolphin.label)
            else -> stringResource(R.string.wiiDolphinFoundClosed, dolphin.label)
        }
    }
    val fileName = DolphinProfile.fileName(state.name, stringResource(state.setup.defaultName))
    val packageName = when (dolphin) {
        is DolphinState.Found -> dolphin.packageName
        is DolphinState.NeverOpened -> dolphin.packageName
        else -> null
    }
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.Save, stringResource(R.string.wiiSave), status)
        CardRowBox {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.wiiName)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (fileName in state.existing) {
                Text(
                    stringResource(R.string.wiiNameExists),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val leftOut = state.mapping.leftOut()
            if (leftOut.isNotEmpty()) {
                Text(
                    stringResource(R.string.wiiLeftOut, leftOut.map { stringResource(it.title) }.joinToString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (state.profileFolder) {
                Text(
                    if (state.existing.isEmpty()) {
                        stringResource(R.string.wiiInDolphinNone)
                    } else {
                        stringResource(R.string.wiiInDolphin, state.existing.sortedBy { it.lowercase() }.joinToString())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        val found = state.found
        CardRow(
            title = stringResource(if (state.saving) R.string.wiiSaving else R.string.wiiSaveToDolphin),
            info = stringResource(R.string.wiiSaveToDolphinInfo),
            enabled = found != null && !state.saving,
            onClick = viewModel::saveToDolphin,
        )
        CardRow(
            title = stringResource(R.string.wiiSaveCopy),
            info = stringResource(R.string.wiiSaveCopyInfo, DolphinFiles.COPY_FOLDER, fileName),
            enabled = !state.saving,
            onClick = viewModel::saveCopy,
        )
        if (found?.authority != null) {
            CardSwitchRow(
                title = stringResource(R.string.wiiAccess),
                info = stringResource(if (state.access) R.string.wiiAccessOn else R.string.wiiAccessOff),
                checked = state.access,
                enabled = !state.saving,
                onChange = { on -> if (on) pickFolder() else viewModel.removeAccess() },
            )
        }
        val folder = remember(found?.authority) { viewModel.folderIntent() }
        if (folder != null) {
            CardRow(
                title = stringResource(R.string.wiiOpenFolder),
                info = stringResource(R.string.wiiOpenFolderInfo),
                onClick = { runCatching { context.startActivity(folder) } },
            )
        }
        if (packageName != null) {
            CardRow(title = stringResource(R.string.wiiOpenDolphin), onClick = {
                context.packageManager.getLaunchIntentForPackage(packageName)?.let {
                    context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
        }
        CardRow(title = stringResource(R.string.wiiReset), danger = true, onClick = viewModel::resetSetup)
    }
    Text(
        stringResource(R.string.wiiHowToLoad),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SaveResultNote(state: WiiUiModel, viewModel: WiiBuilderViewModel, pickFolder: () -> Unit) {
    val context = LocalContext.current
    when (val result = state.result) {
        null -> Unit
        is WiiSaveResult.InDolphin -> NoteCard(
            stringResource(
                when {
                    result.route == SaveRoute.ROOT -> R.string.wiiSavedByRoot
                    result.listed -> R.string.wiiSavedByDolphin
                    else -> R.string.wiiSavedUnlisted
                },
                result.name,
            ),
        )
        is WiiSaveResult.NeedsAccess -> NoteCard(
            stringResource(R.string.wiiNeedsAccess),
            caution = true,
            actionLabel = stringResource(R.string.wiiNeedsAccessAction),
            onAction = pickFolder,
        )
        WiiSaveResult.AccessGiven -> NoteCard(stringResource(R.string.wiiAccessGiven))
        WiiSaveResult.WrongFolder -> NoteCard(
            stringResource(R.string.wiiWrongFolder),
            warning = true,
            actionLabel = stringResource(R.string.wiiTryAgain),
            onAction = pickFolder,
        )
        is WiiSaveResult.ByHand -> if (result.copied) {
            if (result.detail != null) NoteCard(stringResource(R.string.wiiDolphinFailed, result.detail), warning = true)
            val path = viewModel.profilePath()
            NoteCard(
                stringResource(R.string.wiiCopyGuide, DolphinFiles.COPY_FOLDER, result.name, path),
                actionLabel = stringResource(R.string.wiiCopyPath),
                onAction = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.wiiCopyPathLabel), path))
                },
            )
        } else {
            NoteCard(stringResource(R.string.wiiSaveFailed), warning = true)
        }
    }
}
