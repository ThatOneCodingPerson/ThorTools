package io.github.thatonecodingperson.thortools.roms

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.ui.composables.CardHeader
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.CardRowBox
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard

/** A choice shown as a button that opens a list; each entry may have a second line. */
@Composable
fun <T> RomDropdown(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    detail: @Composable (T) -> String? = { null },
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label(selected), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(label(option))
                            detail(option)?.let { Muted(it) }
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** The Compress (or Clean up) button with what happens to the originals in a dropdown beside it, out of [choices]. */
@Composable
fun CompressBar(
    text: String,
    enabled: Boolean,
    originals: Originals,
    onOriginals: (Originals) -> Unit,
    onCompress: () -> Unit,
    choices: List<Originals> = Originals.entries,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // The button keeps its whole text ("Compress 11 games"); the dropdown takes the rest.
        Button(
            onClick = onCompress,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.widthIn(min = 150.dp),
        ) {
            Text(text, maxLines = 1)
        }
        RomDropdown(
            options = choices,
            selected = originals,
            label = { stringResource(originalsLabel(it)) },
            detail = { stringResource(originalsInfo(it)) },
            modifier = Modifier.weight(1f),
            onSelect = onOriginals,
        )
    }
}

fun originalsLabel(choice: Originals): Int = when (choice) {
    Originals.KEEP -> R.string.romOriginalsKeep
    Originals.DELETE -> R.string.romOriginalsDelete
    Originals.MOVE -> R.string.romOriginalsMove
}

fun originalsInfo(choice: Originals): Int = when (choice) {
    Originals.KEEP -> R.string.romOriginalsKeepInfo
    Originals.DELETE -> R.string.romOriginalsDeleteInfo
    Originals.MOVE -> R.string.romOriginalsMoveInfo
}

/** A search field as low as the dropdowns beside a game list's Select all box, with a clear button once typed in. */
@Composable
fun GameSearch(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier,
        decorationBox = { field ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .border(1.dp, colors.outline, CircleShape)
                    .padding(start = 12.dp, end = 4.dp),
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp).size(20.dp),
                )
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            stringResource(R.string.romSearch),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    field()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.romSearchClear),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.clip(CircleShape).clickable { onValueChange("") }.padding(8.dp).size(20.dp),
                    )
                }
            }
        },
    )
}

/** A game in a list: a box to tick it and the rest of the row to open it. */
@Composable
fun GameRow(
    title: String,
    info: String,
    value: String?,
    checked: Boolean,
    canTick: Boolean,
    onTick: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp)) {
        Checkbox(checked = checked && canTick, onCheckedChange = onTick, enabled = canTick)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Muted(info)
            }
            value?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/**
 * What is being compressed now, plainly: the game, how far, which of how many, the time left for it and for all, and
 * Pause or Resume, Skip and Stop. Nothing when no job is running or waiting.
 */
@Composable
fun NowCompressing(state: RomUiModel, viewModel: RomViewModel) {
    val runner = state.runner
    if (!runner.active) return
    val running = runner.current
    CardRowBox {
        Text(
            when {
                running == null -> stringResource(R.string.romNowNext)
                runner.paused -> stringResource(R.string.romNowPaused, running.job.title)
                else -> stringResource(
                    when (running.job.kind) {
                        JobKind.COMPRESS -> R.string.romNowWorking
                        JobKind.ORIGINALS -> R.string.romNowCleaning
                        JobKind.EXTRACT -> R.string.romNowExtracting
                    },
                    running.job.title,
                )
            },
            style = MaterialTheme.typography.titleMedium,
        )
        val percent = running?.progress?.percent
        val parts = listOfNotNull(
            running?.let { phaseText(it.phase, percent) },
            stringResource(R.string.romGameOf, runner.done + 1, runner.total).takeIf { runner.total > 1 },
        )
        Muted(parts.joinToString(" · "))
        if (percent != null) {
            LinearProgressIndicator(progress = { (percent / 100).toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        } else if (!runner.paused) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        }
        if (!runner.paused && running != null) {
            val (thisOne, all) = QueueTime.left(runner, System.currentTimeMillis())
            val times = listOfNotNull(
                thisOne?.let { stringResource(R.string.romLeftThis, duration(it)) },
                all?.takeIf { runner.queue.isNotEmpty() }?.let { stringResource(R.string.romLeftAll, duration(it)) },
            )
            if (times.isNotEmpty()) {
                Muted(times.joinToString(" · "))
            } else if (QueueTime.canTell(runner)) {
                Muted(stringResource(R.string.romLeftSoon))
            }
        }
        // Words only: three buttons side by side in a card column leave no room for icons.
        val padding = PaddingValues(horizontal = 8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            if (runner.paused) {
                Button(onClick = viewModel::resumeRun, contentPadding = padding, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.romResume), maxLines = 1)
                }
            } else {
                OutlinedButton(onClick = viewModel::pause, contentPadding = padding, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.romPause), maxLines = 1)
                }
            }
            if (running != null && runner.queue.isNotEmpty()) {
                OutlinedButton(onClick = viewModel::skip, contentPadding = padding, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.romSkip), maxLines = 1)
                }
            }
            OutlinedButton(onClick = viewModel::stopAll, contentPadding = padding, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.romStop), maxLines = 1)
            }
        }
        if (runner.paused) Muted(stringResource(R.string.romPausedInfo))
    }
}

/**
 * The jobs card: what is being compressed now ([showNow]; a console page shows that above its games instead), why the
 * queue stopped, and the jobs that ended this session.
 */
@Composable
fun JobsCard(state: RomUiModel, viewModel: RomViewModel, showNow: Boolean = true) {
    val runner = state.runner
    val now = showNow && runner.active
    if (!now && runner.ended.isEmpty() && runner.stopped == null) return
    val context = LocalContext.current
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.Compress, stringResource(R.string.romJobsTitle), jobsInfo(runner))
        if (now) NowCompressing(state, viewModel)
        runner.stopped?.let { stop ->
            CardRowBox {
                Warning(
                    stringResource(
                        when (stop) {
                            QueueStop.LOW_BATTERY -> R.string.romStopBattery
                            QueueStop.NO_ENGINE -> R.string.romStopEngine
                            QueueStop.NO_ROOT -> R.string.romStopRoot
                        },
                    ),
                )
            }
        }
        runner.ended.asReversed().take(ENDED_SHOWN).forEach { ended ->
            CardRowBox {
                Text(ended.job.title, style = MaterialTheme.typography.bodyMedium)
                Muted(endText(context, ended))
            }
        }
        if (runner.ended.isNotEmpty() && runner.current == null) {
            CardRow(title = stringResource(R.string.romClearEnded), onClick = viewModel::clearEnded)
        }
    }
}

@Composable
private fun jobsInfo(runner: RunnerState): String = when {
    runner.paused -> stringResource(R.string.romPaused)
    runner.current != null -> stringResource(R.string.romJobsRunning)
    runner.queue.isNotEmpty() -> stringResource(R.string.romJobsWaiting)
    else -> stringResource(R.string.romJobsDone)
}

@Composable
private fun phaseText(phase: String, percent: Double?): String {
    val step = stringResource(
        when (phase) {
            "verify" -> R.string.romPhaseVerify
            "extract" -> R.string.romPhaseExtract
            "unpack" -> R.string.romPhaseUnpack
            "finish" -> R.string.romPhaseFinish
            "" -> R.string.romPhaseStarting
            else -> R.string.romPhaseCompress
        },
    )
    return listOfNotNull(step, percent?.let { stringResource(R.string.romPercent, it.toInt()) }).joinToString(" ")
}

/** "40 s", "12 min", "1 h 20 min". */
@Composable
fun duration(seconds: Long): String = when {
    seconds < SECONDS_SHOWN -> stringResource(R.string.romDurSeconds, seconds.coerceAtLeast(1))
    seconds < HOUR -> stringResource(R.string.romDurMinutes, (seconds + MINUTE - 1) / MINUTE)
    else -> stringResource(R.string.romDurHours, seconds / HOUR, (seconds % HOUR) / MINUTE)
}

private fun endText(context: Context, ended: EndedJob): String = when (val end = ended.end) {
    is JobEnd.Done -> when (ended.job.kind) {
        JobKind.COMPRESS -> context.getString(
            R.string.romEndCompressed,
            size(context, ended.job.inputBytes),
            size(context, end.size),
            size(context, (ended.job.inputBytes - end.size).coerceAtLeast(0)),
        )
        JobKind.EXTRACT -> context.getString(R.string.romEndExtracted, size(context, end.size))
        JobKind.ORIGINALS -> context.getString(
            if (ended.job.choice == Originals.MOVE) R.string.romEndOriginalsMoved else R.string.romEndOriginalsDeleted,
        )
    }
    JobEnd.Cancelled -> context.getString(R.string.romEndCancelled)
    JobEnd.Exists -> context.getString(R.string.romEndExists, ended.job.outputName)
    is JobEnd.NoSpace -> context.getString(R.string.romEndNoSpace, size(context, ended.job.need), size(context, end.free))
    is JobEnd.Failed -> when (end.step) {
        "verify" -> context.getString(
            if (ended.job.kind == JobKind.ORIGINALS) R.string.romEndOriginalsUnverified else R.string.romEndUnverified,
        )
        "stopped" -> context.getString(R.string.romEndStopped)
        "start" -> context.getString(R.string.romEndNoStart)
        "engine" -> context.getString(R.string.romEndNoEngine)
        "notdisc" -> context.getString(R.string.romEndNotDisc)
        "notdvd" -> context.getString(R.string.romEndNotDvd)
        else -> context.getString(R.string.romEndFailed, end.step + (end.code?.let { " $it" } ?: ""))
    }
}

/** A question before jobs start. */
@Composable
fun AskDialog(ask: RomAsk, onConfirm: () -> Unit, onDismiss: () -> Unit, onDeleting: () -> Unit = {}, onFewer: () -> Unit = {}) {
    if (ask is RomAsk.Space) {
        SpaceDialog(ask, onDeleting, onFewer, onDismiss)
        return
    }
    val text = when (ask) {
        is RomAsk.NoEmulator -> {
            val reasons = ask.checks.filter { it.opens != true }.map { check -> checkText(check) }.joinToString("\n")
            listOf(stringResource(R.string.romAskNoEmulator), reasons).filter { it.isNotBlank() }.joinToString("\n\n")
        }
        is RomAsk.Delete -> pluralStringResource(R.plurals.romAskDelete, ask.jobs.size, ask.jobs.size)
        is RomAsk.Space -> ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (ask is RomAsk.Delete) R.string.romAskDeleteTitle else R.string.romAskNoEmulatorTitle)) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.romAskGoOn)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.romAskStop)) } },
    )
}

/** Before a run that won't fit: delete the originals after each check, only the games that fit, or not now. */
@Composable
private fun SpaceDialog(ask: RomAsk.Space, onDeleting: () -> Unit, onFewer: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val plan = ask.plan
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.romAskSpaceTitle)) },
        text = {
            Text(stringResource(R.string.romAskSpace, size(context, plan.needBytes), size(context, plan.freeBytes), plan.fit, plan.total))
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (ask.withDelete.fit > 0) {
                    TextButton(onClick = onDeleting) {
                        Text(
                            if (ask.withDelete.fitsAll) {
                                stringResource(R.string.romAskSpaceDelete)
                            } else {
                                stringResource(R.string.romAskSpaceDeleteSome, ask.withDelete.fit)
                            },
                        )
                    }
                }
                if (plan.fit > 0) TextButton(onClick = onFewer) { Text(stringResource(R.string.romAskSpaceFewer, plan.fit)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.romAskStop)) }
            }
        },
    )
}

/** An emulator's line: whether it opens the format, and why not. */
@Composable
fun checkText(check: EmulatorCheck): String = when {
    check.needs != null -> stringResource(R.string.romCheckNeeds, check.name, check.version.orEmpty(), check.needs)
    check.support == Support.CONFIRMED -> stringResource(R.string.romCheckConfirmed)
    check.support == Support.DOCUMENTED -> stringResource(R.string.romCheckDocumented)
    check.support == Support.UNCONFIRMED -> stringResource(R.string.romCheckUnknown, check.name)
    else -> stringResource(R.string.romCheckNo, check.name)
}

@Composable
fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Warning(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

fun size(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

private const val SECONDS_SHOWN = 60L
private const val MINUTE = 60L
private const val HOUR = 3600L
private const val ENDED_SHOWN = 5
