package io.github.thatonecodingperson.thortools.hotkeys

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.actions.icon

/**
 * One hotkey on a whole screen: what it does and its keys on the left, the buttons by group on the right. The buttons
 * can also be pressed on the Thor. Two columns when the screen is wide enough, one otherwise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotkeyEditor(state: HotkeysUiModel, draft: HotkeyDraft, viewModel: HotkeysViewModel) {
    // While recording, the service keeps every button from everything else; leaving the app must end that.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.stopRecording() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (draft.editing == null) R.string.hotkeyNew else R.string.hotkeyEdit)) },
                navigationIcon = {
                    IconButton(onClick = viewModel::closeDraft) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    if (draft.editing != null) {
                        IconButton(onClick = viewModel::deleteDraft) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    Button(onClick = viewModel::saveDraft, enabled = draft.allowed, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            if (maxWidth >= TWO_COLUMNS_WIDTH) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    EditorColumn(Modifier.weight(1f)) { Summary(state, draft, viewModel) }
                    EditorColumn(Modifier.weight(1.25f)) { ButtonChoice(draft, viewModel) }
                }
            } else {
                EditorColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Summary(state, draft, viewModel)
                    ButtonChoice(draft, viewModel)
                }
            }
        }
    }
}

@Composable
private fun EditorColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        content = content,
    )
}

/** What the hotkey does, its keys as they stand, recording, the press, and anything worth knowing before saving. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.Summary(state: HotkeysUiModel, draft: HotkeyDraft, viewModel: HotkeysViewModel) {
    val launch = AppLaunch.decode(draft.arg).takeIf { draft.action == ThorAction.LAUNCH_APP }
    val app = launch?.let { state.app(it.packageName) }

    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app != null) {
                    Image(painter = rememberDrawablePainter(app.icon), contentDescription = null, modifier = Modifier.size(32.dp))
                } else {
                    Icon(
                        draft.action.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        text = if (launch != null) {
                            stringResource(R.string.hotkeyOpenApp, app?.name ?: launch.packageName)
                        } else {
                            stringResource(draft.action.label)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(draft.action.description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            val button = draft.button
            val second = draft.comboSecond
            when {
                button == null -> Text(
                    text = stringResource(R.string.hotkeyChooseButton),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(min = 44.dp),
                )
                draft.combo && second == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    KeyCap(button)
                    Text(
                        text = stringResource(R.string.hotkeyComboWaiting),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                else -> {
                    TriggerKeys(button, second, draft.effectivePress, small = false)
                    Text(
                        text = triggerText(button, second, draft.effectivePress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    Recording(state, viewModel)

    Label(stringResource(R.string.hotkeyPress))
    // Five choices don't fit in one segmented row on the Thor's narrow column; chips wrap instead.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PressChoice.entries.forEach { choice ->
            FilterChip(
                selected = draft.choice == choice,
                onClick = { viewModel.pickChoice(choice) },
                label = { Text(stringResource(choice.label)) },
            )
        }
    }
    Text(
        text = stringResource(if (draft.combo) R.string.hotkeyComboInfo else R.string.hotkeyPressInfo),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (draft.combo) {
        Label(stringResource(R.string.hotkeySecondPress))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PressKind.entries.forEachIndexed { index, press ->
                SegmentedButton(
                    selected = draft.secondPress == press,
                    onClick = { viewModel.pickSecondPress(press) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = PressKind.entries.size),
                    icon = {},
                    label = { Text(stringResource(press.title), maxLines = 1) },
                )
            }
        }
    }

    if (launch != null) {
        Text(stringResource(R.string.hotkeyOpensOn), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LaunchScreen.entries.forEach { screen ->
                FilterChip(
                    selected = launch.screen == screen,
                    onClick = { viewModel.pickScreen(screen) },
                    label = { Text(stringResource(screen.label)) },
                )
            }
        }
    }

    if (draft.action == ThorAction.CLOSE_APP) {
        val closeOn = CloseAppArg.decode(draft.arg)
        Text(
            text = stringResource(R.string.hotkeyClosesOn),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Only hotkeys saved before 0.16.0 have no screen; they keep acting on the app that came up last.
            if (closeOn == null) {
                FilterChip(selected = true, onClick = {}, label = { Text(stringResource(R.string.hotkeyCloseLegacy)) })
            }
            LaunchScreen.entries.forEach { screen ->
                FilterChip(
                    selected = closeOn == screen,
                    onClick = { viewModel.pickCloseScreen(screen) },
                    label = { Text(stringResource(screen.label)) },
                )
            }
        }
    }

    Text(stringResource(R.string.hotkeySwitches), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    SwitchRow(
        title = stringResource(R.string.hotkeyShowText),
        info = stringResource(R.string.hotkeyShowTextInfo),
        checked = draft.showText,
        onChange = viewModel::setDraftText,
    )
    if (Hotkey.canLock(draft.action)) {
        SwitchRow(
            title = stringResource(R.string.hotkeyAlsoLock),
            info = stringResource(R.string.hotkeyAlsoLockInfo),
            checked = draft.lock,
            onChange = viewModel::setDraftLock,
        )
    }
    if (Hotkey.canCleanMemory(draft.action)) {
        SwitchRow(
            title = stringResource(R.string.hotkeyAlsoCleanMemory),
            info = stringResource(R.string.hotkeyAlsoCleanMemoryInfo),
            checked = draft.cleanMemory,
            onChange = viewModel::setDraftCleanMemory,
        )
    }

    Advice(state, draft, viewModel)
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchRow(title: String, info: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun Recording(state: HotkeysUiModel, viewModel: HotkeysViewModel) {
    when {
        state.recording -> NoteCard(
            text = stringResource(R.string.hotkeyRecording),
            actionLabel = stringResource(R.string.cancel),
            onAction = viewModel::stopRecording,
        )
        state.serviceRunning -> OutlinedButton(onClick = viewModel::startRecording, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.SportsEsports, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.hotkeyRecord))
        }
        else -> NoteCard(stringResource(R.string.hotkeyRecordNeedsService))
    }
}

/**
 * Notes about the hotkey as it stands: the combo's second button missing, a game button on its own or another hotkey that
 * isn't allowed, replaces another, a combo held on a game button, D-pad or stick motion caught, Home held back. Only the
 * ones that aren't allowed block saving.
 */
@Composable
private fun Advice(state: HotkeysUiModel, draft: HotkeyDraft, viewModel: HotkeysViewModel) {
    val button = draft.button ?: return
    val second = draft.comboSecond
    if (draft.combo && second == null) {
        NoteCard(stringResource(R.string.hotkeyComboNeedsSecond))
        return
    }
    if (second == null && button.gamepad) {
        NoteCard(stringResource(R.string.hotkeyGameButtonNote, stringResource(button.label)), warning = true)
        return
    }
    if (!draft.allowed) {
        NoteCard(stringResource(R.string.hotkeyNotAllowed), warning = true)
        return
    }
    state.draftConflict()?.let { other ->
        NoteCard(stringResource(R.string.hotkeyReplaces, stringResource(other.action.label)), warning = true)
    }
    if (second != null && !Hotkey.goodComboFirst(button)) {
        NoteCard(stringResource(R.string.hotkeyComboFirstCaution, stringResource(button.label)), caution = true)
    }
    if (second?.secondOnly == true) {
        NoteCard(stringResource(R.string.hotkeyMotionNote, stringResource(button.label)))
    }
    if ((button == PadButton.HOME || second == PadButton.HOME) && !state.singlePressHome) {
        NoteCard(
            text = stringResource(R.string.hotkeyHomeNote),
            warning = true,
            actionLabel = stringResource(R.string.hotkeysTurnOn),
            onAction = viewModel::turnOnSinglePressHome,
        )
    }
}

/**
 * The slots and the grid that fills the chosen one: one slot for a single press; "Hold" and "Then press" for a combo.
 */
@Composable
private fun ColumnScope.ButtonChoice(draft: HotkeyDraft, viewModel: HotkeysViewModel) {
    val slot = if (draft.combo) draft.slot else DraftSlot.FIRST
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Slot(
            title = stringResource(if (draft.combo) R.string.hotkeySlotHold else R.string.hotkeySlotButton),
            button = draft.button,
            empty = stringResource(R.string.hotkeySlotChoose),
            selected = slot == DraftSlot.FIRST,
            modifier = Modifier.weight(1f),
        ) { viewModel.selectSlot(DraftSlot.FIRST) }
        if (draft.combo) {
            Slot(
                title = stringResource(R.string.hotkeySlotSecond),
                button = draft.second,
                empty = stringResource(R.string.hotkeySlotChoose),
                selected = slot == DraftSlot.SECOND,
                modifier = Modifier.weight(1f),
            ) { viewModel.selectSlot(DraftSlot.SECOND) }
        }
    }
    Text(
        text = stringResource(
            when {
                !draft.combo -> R.string.hotkeySlotButtonInfo
                slot == DraftSlot.FIRST -> R.string.hotkeySlotHoldInfo
                else -> R.string.hotkeySlotSecondInfo
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val first = draft.button
    ButtonGrid(
        selected = if (slot == DraftSlot.FIRST) draft.button else draft.second,
        disabled = when {
            // A game button is only ever part of a combo (Hotkey.allowed).
            slot == DraftSlot.FIRST -> PadButton.entries.filter { it.secondOnly || (!draft.combo && it.gamepad) }.toSet()
            first == null -> PadButton.entries.toSet()
            else -> PadButton.entries.filterNot { Hotkey.allowed(first, it) }.toSet()
        },
        onPick = viewModel::pickButton,
    )
}

@Composable
private fun Slot(title: String, button: PadButton?, empty: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.secondaryContainer else colors.surfaceContainer,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            if (button != null) {
                KeyCap(button)
            } else {
                Text(empty, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.heightIn(min = 44.dp).padding(top = 10.dp))
            }
        }
    }
}

private val TWO_COLUMNS_WIDTH = 640.dp
