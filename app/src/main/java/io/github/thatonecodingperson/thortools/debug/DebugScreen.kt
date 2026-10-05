package io.github.thatonecodingperson.thortools.debug

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.ActionCategory
import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.actions.icon
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.hotkeys.TriggerKeys
import io.github.thatonecodingperson.thortools.hotkeys.triggerText
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.DialogButton
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchCard
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The debug toolkit: the action check, the hotkey check, and the results file. */
@Composable
fun DebugScreen(viewModel: DebugViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val padShown by viewModel.padShown.collectAsState()
    val asking by viewModel.asking.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    // The hotkey check runs only while its tab is on screen and the app is in front, so the hotkeys never stay paused.
    val lifecycleOwner = LocalLifecycleOwner.current
    val onHotkeys = state.tab == DebugTab.HOTKEYS
    DisposableEffect(lifecycleOwner, onHotkeys) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (onHotkeys) viewModel.startHotkeyCheck()
                Lifecycle.Event.ON_STOP -> viewModel.stopHotkeyCheck()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopHotkeyCheck()
        }
    }

    asking?.let { action ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.debugAskTitle)) },
            text = { Text(stringResource(R.string.debugAskText, stringResource(action.label))) },
            confirmButton = { DialogButton(text = stringResource(R.string.debugYes)) { viewModel.answer(true) } },
            dismissButton = { DialogButton(text = stringResource(R.string.debugNo)) { viewModel.answer(false) } },
        )
    }
    state.confirm?.let { action ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRisky,
            title = { Text(stringResource(R.string.debugRiskyTitle)) },
            text = { Text(stringResource(riskyText(action))) },
            confirmButton = { DialogButton(text = stringResource(R.string.debugRun), onClick = viewModel::confirmRisky) },
            dismissButton = { DialogButton(text = stringResource(R.string.cancel), onClick = viewModel::dismissRisky) },
        )
    }
    state.reportText?.let { text -> ReportDialog(text, viewModel::hideReport) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(topBar = { SubTopAppBar(title = R.string.debugTitle, onBack = onBack) }) { padding ->
            Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
                PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                    Tab(selected = !onHotkeys, onClick = {
                        viewModel.selectTab(DebugTab.ACTIONS)
                    }, text = { Text(stringResource(R.string.debugTabActions)) })
                    Tab(selected = onHotkeys, onClick = {
                        viewModel.selectTab(DebugTab.HOTKEYS)
                    }, text = { Text(stringResource(R.string.debugTabHotkeys)) })
                }
                Box(Modifier.weight(1f)) {
                    if (onHotkeys) HotkeyTab(state, viewModel) else ActionTab(state, viewModel)
                }
            }
        }
        if (padShown) GesturePad(viewModel::swiped)
    }
}

@StringRes
private fun riskyText(action: ThorAction): Int = when (action) {
    ThorAction.CLEAR_BACKGROUND -> R.string.debugRiskyClear
    ThorAction.LOCK_SCREEN -> R.string.debugRiskyLock
    else -> R.string.debugRiskyBottom
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionTab(state: DebugUiModel, viewModel: DebugViewModel) {
    val results by viewModel.results.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val counts = results.values.groupingBy { it.state }.eachCount()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SettingsCard {
                if (!state.serviceRunning) NoteCard(stringResource(R.string.debugNoService), warning = true)
                Text(stringResource(R.string.debugActionsIntro), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(onClick = viewModel::runAll, enabled = !busy && state.serviceRunning) {
                        Text(stringResource(R.string.debugRunAll))
                    }
                    OutlinedButton(onClick = viewModel::runWatch, enabled = !busy && state.serviceRunning) {
                        Text(stringResource(R.string.debugRunWatch))
                    }
                    OutlinedButton(onClick = viewModel::runFailed, enabled = !busy && (counts[CheckState.FAIL] ?: 0) > 0) {
                        Text(stringResource(R.string.debugRunFailed))
                    }
                    if (busy) OutlinedButton(onClick = viewModel::stop) { Text(stringResource(R.string.debugStop)) }
                }
                Text(
                    stringResource(
                        R.string.debugSummary,
                        counts[CheckState.PASS] ?: 0,
                        counts[CheckState.FAIL] ?: 0,
                        counts[CheckState.SKIPPED] ?: 0,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        ActionCategory.entries.forEach { category ->
            val checks = ActionChecks.ALL.filter { it.action.category == category }
            if (checks.isEmpty()) return@forEach
            item(key = category.name) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(
                        stringResource(category.label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { viewModel.runGroup(category) }, enabled = !busy && state.serviceRunning) {
                        Text(stringResource(R.string.debugRunGroup))
                    }
                }
            }
            items(checks, key = { it.action.id }) { check ->
                CheckRow(check, results[check.action], enabled = !busy) { viewModel.runOne(check.action) }
            }
        }
        item { ReportCard(state, viewModel) }
    }
}

@Composable
private fun CheckRow(check: ActionCheck, result: CheckResult?, enabled: Boolean, onRun: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (label, color) = when (result?.state) {
        null -> stringResource(R.string.debugStateNone) to colors.onSurfaceVariant
        CheckState.RUNNING -> stringResource(R.string.debugStateRunning) to colors.primary
        CheckState.PASS -> stringResource(R.string.debugStatePass) to colors.primary
        CheckState.FAIL -> stringResource(R.string.debugStateFail) to colors.error
        CheckState.SKIPPED -> stringResource(R.string.debugStateSkipped) to colors.onSurfaceVariant
        CheckState.ASK -> stringResource(R.string.debugStateAsk) to colors.onSurfaceVariant
    }
    SettingsCard(modifier = Modifier.clickable(enabled = enabled, onClick = onRun)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(check.action.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(check.action.label), style = MaterialTheme.typography.titleSmall)
                val kind = when (check.kind) {
                    CheckKind.AUTO -> null
                    CheckKind.WATCH -> stringResource(R.string.debugKindWatch)
                    CheckKind.RISKY -> stringResource(R.string.debugKindRisky)
                }
                val detail = listOfNotNull(
                    kind,
                    result?.let { r -> if (r.before != null || r.after != null) "${r.before ?: "?"} → ${r.after ?: "?"}" else null },
                    result?.restored?.takeIf { !it }?.let { stringResource(R.string.debugRestoredNo) },
                    result?.detail?.takeIf { it.isNotEmpty() },
                ).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
                }
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotkeyTab(state: DebugUiModel, viewModel: DebugViewModel) {
    val events by viewModel.events.collectAsState()
    val probe by viewModel.probe.collectAsState()
    val (seen, total) = HotkeyCheck.summary(state.rows)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SettingsCard {
                Text(stringResource(R.string.debugHotkeysIntro), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    HotkeyType.entries.forEach { type ->
                        FilterChip(
                            selected = type == state.hotkeyType,
                            onClick = { viewModel.pickType(type) },
                            label = { Text(stringResource(type.label)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.debugRunActions), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.debugRunActionsInfo), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.runActions, onCheckedChange = viewModel::setRunActions)
                }
                if (probe != null && !state.runActions) NoteCard(stringResource(R.string.debugBanner))
                Text(
                    stringResource(R.string.debugHotkeySummary, seen, total),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (state.rows.isEmpty()) item { Text(stringResource(R.string.debugNoHotkeys), style = MaterialTheme.typography.bodyMedium) }
        items(state.rows.size) { index -> HotkeyRowCard(state.rows[index]) }
        if (events.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.debugPresses),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(events.asReversed().take(SHOWN_EVENTS)) { event -> Text(eventText(event), style = MaterialTheme.typography.bodySmall) }
        }
        item { ReportCard(state, viewModel) }
    }
}

@Composable
private fun HotkeyRowCard(row: HotkeyRow) {
    val colors = MaterialTheme.colorScheme
    SettingsCard(highlighted = row.seenAt != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TriggerKeys(row.hotkey.button, row.hotkey.second, row.hotkey.press)
                Text(
                    stringResource(row.hotkey.action.label),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (row.onlyInApps) {
                    Text(
                        stringResource(R.string.debugOnlyInApps),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Text(
                stringResource(if (row.seenAt != null) R.string.debugStateSeen else R.string.debugStateWaiting),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (row.seenAt != null) colors.primary else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun eventText(event: HotkeyEvent): String = when (event) {
    is HotkeyEvent.Matched -> stringResource(
        R.string.debugEventMatched,
        triggerText(event.hotkey.button, event.hotkey.second, event.hotkey.press),
        stringResource(event.hotkey.action.label),
    )
    is HotkeyEvent.GivenBack -> stringResource(R.string.debugEventGivenBack, stringResource(event.button.label))
    is HotkeyEvent.Unmatched -> stringResource(R.string.debugEventUnmatched, stringResource(event.button.label))
}

/** The results file: on or off, and a row that shows what it holds. */
@Composable
private fun ReportCard(state: DebugUiModel, viewModel: DebugViewModel) {
    SwitchCard(
        icon = Icons.Rounded.Description,
        title = R.string.debugReportFile,
        info = R.string.debugReportFileInfo,
        checked = state.reportFile,
        rows = { CardRow(title = stringResource(R.string.debugShow), info = viewModel.reportPath, onClick = viewModel::showReport) },
        onChange = viewModel::setReportFile,
    )
}

@Composable
private fun ReportDialog(text: String, onClose: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.debugReportFile)) },
        text = {
            SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text.ifEmpty {
                        stringResource(R.string.debugNoReport)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            DialogButton(text = stringResource(R.string.debugCopy)) {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Thor Tools debug report", text))) }
            }
        },
        dismissButton = { DialogButton(text = stringResource(R.string.debugClose), onClick = onClose) },
    )
}

/** Covers the screen while the swipe checks run and tells which way a swipe went. */
@Composable
private fun GesturePad(onSwipe: (String) -> Unit) {
    val drag = remember { floatArrayOf(0f, 0f) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { _: Offset -> drag.fill(0f) },
                    onDragEnd = {
                        val (dx, dy) = drag
                        onSwipe(if (abs(dx) > abs(dy)) (if (dx > 0) "right" else "left") else (if (dy > 0) "down" else "up"))
                    },
                ) { change, amount ->
                    change.consume()
                    drag[0] += amount.x
                    drag[1] += amount.y
                }
            },
    ) {
        Text(stringResource(R.string.debugPad), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private const val SHOWN_EVENTS = 20
