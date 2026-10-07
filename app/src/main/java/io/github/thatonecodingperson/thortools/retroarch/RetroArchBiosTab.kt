package io.github.thatonecodingperson.thortools.retroarch

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.ui.composables.CardCheckRow
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardHeader
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.CardRowBox
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard

/**
 * The BIOS finder: the user's BIOS folders and RetroArch's BIOS folder on the left, a card per console with what was
 * found on the right.
 */
@Composable
internal fun BiosTab(state: RaUiModel, viewModel: RetroArchViewModel, pickFolder: () -> Unit) {
    val bios = state.bios
    CardColumns(
        padding = PaddingValues(),
        left = {
            StatusNotes(state, viewModel)
            Text(
                stringResource(R.string.raBiosIntro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FoldersCard(bios, viewModel, pickFolder)
            TargetCard(state, viewModel)
            BiosNoteCards(bios.note)
            ResultNote(state.note)
            Text(
                stringResource(R.string.raBiosSource),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        right = {
            val scan = bios.scan
            when {
                scan != null -> Consoles(scan.entries, bios, viewModel)
                bios.scanning -> NoteCard(stringResource(R.string.raBiosLooking))
            }
        },
    )
}

@Composable
private fun FoldersCard(bios: RaBios, viewModel: RetroArchViewModel, pickFolder: () -> Unit) {
    val scan = bios.scan
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.Folder,
            stringResource(R.string.raBiosFolders),
            stringResource(if (bios.folders.isEmpty()) R.string.raBiosNoFolders else R.string.raBiosFoldersInfo),
        )
        bios.folders.forEach { folder ->
            val files = scan?.files(folder)
            val info = when {
                scan == null || folder !in scan.folders -> null
                files == null -> stringResource(R.string.raBiosFolderMissing)
                else -> stringResource(R.string.raBiosFolderFiles, files)
            }
            CardRow(title = folder, info = info, value = stringResource(R.string.raBiosRemove), onClick = {
                viewModel.removeBiosFolder(folder)
            })
        }
        CardRow(
            title = stringResource(R.string.raBiosAddFolder),
            info = stringResource(R.string.raBiosAddFolderInfo),
            icon = Icons.Rounded.CreateNewFolder,
            onClick = pickFolder,
        )
        CardRow(
            title = stringResource(R.string.raBiosLookAgain),
            icon = Icons.Rounded.Refresh,
            enabled = !bios.scanning && !bios.copying,
            onClick = viewModel::scanBios,
        )
    }
    if (bios.scanning && scan != null) NoteCard(stringResource(R.string.raBiosLooking))
}

/** RetroArch's BIOS folder, the offer of its own system folder when that one isn't there, and the copy. */
@Composable
private fun TargetCard(state: RaUiModel, viewModel: RetroArchViewModel) {
    val bios = state.bios
    val target = bios.target
    val configured = target?.configured
    val info = when {
        target == null -> stringResource(R.string.raBiosTargetUnknown)
        target.there -> stringResource(R.string.raBiosTargetThere, configured.orEmpty())
        configured != null && target.copyRoot != null -> stringResource(R.string.raBiosTargetMade, configured)
        configured != null -> stringResource(R.string.raBiosTargetMissing, configured)
        else -> stringResource(R.string.raBiosTargetNotSet)
    }
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.FolderSpecial, stringResource(R.string.raBiosTarget), info)
        val own = target?.own
        if (target?.offerOwn == true && own != null) {
            CardRow(
                title = stringResource(R.string.raBiosUseOwn),
                info = stringResource(R.string.raBiosUseOwnInfo, own),
                enabled = state.canChange,
                onClick = viewModel::useOwnBiosFolder,
            )
        }
        val copies = bios.copies
        CardRow(
            title = stringResource(R.string.raBiosCopy, if (target?.copyRoot == null) bios.ticks.size else copies.size),
            info = if (target?.copyRoot == null) {
                stringResource(R.string.raBiosCopyNoTarget)
            } else {
                stringResource(R.string.raBiosCopyInfo, RetroArchFiles.BACKUP_SUFFIX)
            },
            icon = Icons.Rounded.ContentCopy,
            enabled = copies.isNotEmpty() && !bios.copying && !bios.scanning,
            onClick = viewModel::copyBios,
        )
    }
    if (bios.copying) NoteCard(stringResource(R.string.raBiosCopying))
}

/** A card for each console with something found or in RetroArch's folder, then one card for the rest. */
@Composable
private fun Consoles(entries: List<BiosEntry>, bios: RaBios, viewModel: RetroArchViewModel) {
    val byConsole = entries.groupBy { it.file.console }
    val ticks = bios.ticks
    val (seen, unseen) = BiosConsole.entries.filter { it in byConsole }.partition { console -> byConsole.getValue(console).any { it.seen } }
    seen.forEach { console -> ConsoleCard(console, byConsole.getValue(console), ticks, enabled = !bios.copying, viewModel) }
    if (unseen.isNotEmpty()) NothingFoundCard(unseen, byConsole)
}

@Composable
private fun ConsoleCard(
    console: BiosConsole,
    entries: List<BiosEntry>,
    ticks: Map<String, String>,
    enabled: Boolean,
    viewModel: RetroArchViewModel,
) {
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.Memory, stringResource(console.title), console.cores)
        console.note?.let { note ->
            CardRowBox {
                Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        BiosPlan.folded(entries, ticks).forEach { row ->
            when (row) {
                is BiosRow.File -> FileRows(row.entry, ticks[row.entry.key], enabled, viewModel)
                is BiosRow.Others -> OtherRows(row, ticks, enabled, viewModel)
                is BiosRow.Missing -> CardRowBox {
                    Text(stringResource(row.group.title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(row.group.need.label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.raBiosMissing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * A BIOS file: its name where the cores look, what it is, what RetroArch's folder has and what will be copied; a tap
 * opens the files found for it to choose from, and choosing one closes them again.
 */
@Composable
private fun FileRows(entry: BiosEntry, ticked: String?, enabled: Boolean, viewModel: RetroArchViewModel) {
    val file = entry.file
    val chosen = entry.candidates.firstOrNull { it.path == ticked }
    val found = entry.candidates.isNotEmpty()
    var open by rememberSaveable(entry.key) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    CardRowBox {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(enabled = found) { open = !open },
        ) {
            Column(Modifier.weight(1f)) {
                Text(file.path, style = MaterialTheme.typography.bodyLarge)
                Text(describe(file), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                val (status, color) = status(entry)
                Text(status, style = MaterialTheme.typography.bodySmall, color = color)
                if (!open && chosen != null) {
                    Text(
                        stringResource(R.string.raBiosWillCopy, chosen.path.substringAfterLast('/')),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                    )
                } else if (!open && found) {
                    Text(
                        stringResource(R.string.raBiosFoundChoose, entry.candidates.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (found) {
                Icon(
                    if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(if (open) R.string.raBiosHide else R.string.raBiosShow),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
    if (!open) return
    entry.candidates.forEach { candidate ->
        val parts = listOfNotNull(
            stringResource(R.string.raBiosIn, BiosPaths.shown(candidate.path.substringBeforeLast('/'))),
            stringResource(fitLabel(candidate.fit)),
            stringResource(R.string.raBiosReplaces).takeIf { entry.installed?.atPath == true },
        )
        CardCheckRow(
            title = candidate.path.substringAfterLast('/'),
            info = parts.joinToString(" · "),
            checked = ticked == candidate.path,
            enabled = enabled,
            onChange = { on ->
                viewModel.tickBios(entry, candidate, on)
                if (on) open = false
            },
        )
    }
}

/** The other files of a group where one is enough, under one line that opens them. */
@Composable
private fun OtherRows(row: BiosRow.Others, ticks: Map<String, String>, enabled: Boolean, viewModel: RetroArchViewModel) {
    var open by rememberSaveable(row.group.name) { mutableStateOf(false) }
    CardRow(
        title = stringResource(R.string.raBiosOtherVersions, row.entries.size),
        info = stringResource(if (row.group.oneEnough) R.string.raBiosOtherVersionsInfo else R.string.raBiosOtherOptionalInfo),
        value = stringResource(if (open) R.string.raBiosHide else R.string.raBiosShow),
        onClick = { open = !open },
    )
    if (open) row.entries.forEach { FileRows(it, ticks[it.key], enabled, viewModel) }
}

/** The consoles nothing was found for, each with the files its cores look for. */
@Composable
private fun NothingFoundCard(consoles: List<BiosConsole>, byConsole: Map<BiosConsole, List<BiosEntry>>) {
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.Memory, stringResource(R.string.raBiosNothingFound), stringResource(R.string.raBiosNothingFoundInfo))
        consoles.forEach { console ->
            val entries = byConsole.getValue(console)
            val rows = BiosPlan.rows(entries)
            val names = if (rows.isEmpty()) {
                entries.mapNotNull { it.file.group }.distinct().map { stringResource(it.title) }
            } else {
                rows.map { row ->
                    when (row) {
                        is BiosRow.File -> row.entry.file.path
                        is BiosRow.Missing -> stringResource(row.group.title)
                        is BiosRow.Others -> stringResource(row.group.title)
                    }
                }
            }
            CardRowBox {
                Text(stringResource(console.title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.raBiosLooksFor, names.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BiosNoteCards(note: BiosNote?) {
    when (note) {
        null -> Unit
        BiosNote.ScanFailed -> NoteCard(stringResource(R.string.raBiosScanFailed), warning = true)
        BiosNote.Unusable -> NoteCard(stringResource(R.string.raBiosFolderUnusable), caution = true)
        is BiosNote.Copied -> {
            if (note.copied.isNotEmpty()) NoteCard(stringResource(R.string.raBiosCopied, note.copied.joinToString(", ")))
            note.failed.forEach { (path, why) -> NoteCard(stringResource(R.string.raBiosCopyFailed, path, why), warning = true) }
        }
    }
}

/** What the file is: its region or part, its model, the cores that use it when not all do, and how much it is needed. */
@Composable
private fun describe(file: BiosFile): String {
    val parts = file.what.map { stringResource(it.label) } + listOfNotNull(file.detail, file.cores) + stringResource(file.need.label)
    return parts.joinToString(" · ")
}

/** What RetroArch's folder has for the file, in the colour that says whether that is right. */
@Composable
private fun status(entry: BiosEntry): Pair<String, Color> {
    val colors = MaterialTheme.colorScheme
    val installed = entry.installed
    if (installed != null) {
        return when {
            installed.fit == BiosFit.CHECKED && !installed.atPath ->
                stringResource(R.string.raBiosInFolderAs, installed.path.substringAfterLast('/')) to colors.primary
            installed.fit == BiosFit.CHECKED -> stringResource(R.string.raBiosInFolderChecked) to colors.primary
            installed.fit == BiosFit.OTHER_FILE -> stringResource(R.string.raBiosInFolderOther) to colors.error
            else -> stringResource(R.string.raBiosInFolder) to colors.primary
        }
    }
    return when {
        entry.candidates.isNotEmpty() -> stringResource(R.string.raBiosNotYet) to colors.onSurfaceVariant
        entry.file.need == BiosNeed.OPTIONAL -> stringResource(R.string.raBiosMissing) to colors.onSurfaceVariant
        else -> stringResource(R.string.raBiosMissing) to colors.error
    }
}

@StringRes
private fun fitLabel(fit: BiosFit): Int = when (fit) {
    BiosFit.CHECKED -> R.string.raBiosChecked
    BiosFit.SAME_NAME -> R.string.raBiosSameName
    BiosFit.OTHER_FILE -> R.string.raBiosOtherFile
    BiosFit.NAME_AND_SIZE -> R.string.raBiosNameAndSize
}
