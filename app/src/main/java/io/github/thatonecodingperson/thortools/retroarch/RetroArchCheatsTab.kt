package io.github.thatonecodingperson.thortools.retroarch

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.ui.composables.CardCheckRow
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardHeader
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.CardRowBox
import io.github.thatonecodingperson.thortools.ui.composables.CardSwitchRow
import io.github.thatonecodingperson.thortools.ui.composables.ChoiceCard
import io.github.thatonecodingperson.thortools.ui.composables.CreditNote
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.cardChipColors
import java.text.DateFormat
import java.util.Date

/** Rows shown at most in the game and cheat lists, so a console with thousands stays quick; a search finds the rest. */
private const val SHOWN = 200

/** Cheat files a search through a console's whole list shows. */
private const val FILES_SHOWN = 30

/**
 * Cheats: where they come from, the game folders and RetroArch's side on the left; the games (or the open game with its
 * cheat file, cores and cheats) on the right.
 */
@Composable
internal fun CheatsTab(state: RaUiModel, viewModel: RetroArchViewModel, pickFolder: () -> Unit) {
    val cheats = state.cheats
    CardColumns(
        padding = PaddingValues(),
        left = {
            StatusNotes(state, viewModel)
            Text(
                stringResource(R.string.raCheatIntro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SourceCard(cheats, viewModel)
            FoldersCard(cheats, viewModel, pickFolder)
            RetroArchCard(state, viewModel)
            CheatNoteCards(cheats.note)
            ResultNote(state.note)
            CreditNote(stringResource(R.string.raCheatCredit), stringResource(R.string.raCheatCreditLink), GitHubCheatsApi.LINK)
        },
        right = {
            val open = cheats.game
            if (open != null) GameCards(state, open, viewModel) else GamesCard(state, viewModel)
        },
    )
}

/** The choice of source, with the pack's rows below it. */
@Composable
private fun SourceCard(cheats: RaCheats, viewModel: RetroArchViewModel) {
    val github = cheats.source == CheatSourceKind.GITHUB
    ChoiceCard(
        icon = Icons.Rounded.CloudDownload,
        title = stringResource(R.string.raCheatSource),
        info = stringResource(if (github) R.string.raCheatSourceGitHubInfo else R.string.raCheatSourcePackInfo),
        options = listOf(
            CheatSourceKind.GITHUB to stringResource(R.string.raCheatSourceGitHub),
            CheatSourceKind.PACK to stringResource(R.string.raCheatSourcePack),
        ),
        selected = cheats.source,
        onSelect = viewModel::setCheatSource,
        rows = { if (!github || cheats.pack != null) PackRows(cheats, viewModel) },
    )
}

/** The pack: its download (with how far it got), its date and size, and removing it. */
@Composable
private fun PackRows(cheats: RaCheats, viewModel: RetroArchViewModel) {
    val context = LocalContext.current
    val pack = cheats.pack
    val progress = cheats.packProgress
    if (progress != null) {
        val (done, total) = progress
        CardRowBox {
            Muted(
                if (total > 0) {
                    stringResource(R.string.raCheatPackDownloading, size(context, done), size(context, total))
                } else {
                    stringResource(R.string.raCheatPackDownloadingUnknown, size(context, done))
                },
            )
            LinearProgressIndicator(
                progress = { if (total > 0) done.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        return
    }
    CardRow(
        title = stringResource(if (pack == null) R.string.raCheatPackDownload else R.string.raCheatPackRefresh),
        info = if (pack == null) {
            stringResource(R.string.raCheatPackDownloadInfo)
        } else {
            stringResource(R.string.raCheatPackInfo, date(pack.time), size(context, pack.size))
        },
        icon = Icons.Rounded.Download,
        onClick = viewModel::downloadPack,
    )
    if (pack != null) {
        CardRow(
            title = stringResource(R.string.raCheatPackDelete),
            info = stringResource(R.string.raCheatPackDeleteInfo, size(context, pack.size)),
            danger = true,
            onClick = viewModel::deletePack,
        )
    }
}

@Composable
private fun FoldersCard(cheats: RaCheats, viewModel: RetroArchViewModel, pickFolder: () -> Unit) {
    val listing = cheats.listing
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.Folder,
            stringResource(R.string.raCheatFolders),
            stringResource(if (cheats.folders.isEmpty()) R.string.raCheatNoFolders else R.string.raCheatFoldersInfo),
        )
        cheats.folders.forEach { folder ->
            val files = listing?.files(folder)
            val info = when {
                listing == null || folder !in listing.folders -> null
                files == null -> stringResource(R.string.raCheatFolderMissing)
                else -> stringResource(R.string.raCheatFolderFiles, files)
            }
            CardRow(
                title = folder,
                info = info,
                value = stringResource(R.string.raCheatRemove),
                onClick = { viewModel.removeRomFolder(folder) },
            )
        }
        CardRow(
            title = stringResource(R.string.raCheatAddFolder),
            info = stringResource(R.string.raCheatAddFolderInfo),
            icon = Icons.Rounded.CreateNewFolder,
            onClick = pickFolder,
        )
        CardRow(
            title = stringResource(R.string.raCheatLookAgain),
            info = listing?.let { stringResource(R.string.raCheatLookAgainInfo, dateTime(it.time)) },
            icon = Icons.Rounded.Refresh,
            enabled = !cheats.scanning && cheats.folders.isNotEmpty(),
            onClick = { viewModel.loadCheats(fullScan = true) },
        )
    }
    if (cheats.scanning) NoteCard(stringResource(R.string.raCheatLooking))
}

/** RetroArch's cheats folder, and whether it switches ticked cheats on when a game starts. */
@Composable
private fun RetroArchCard(state: RaUiModel, viewModel: RetroArchViewModel) {
    val root = state.cheats.side?.cheatsRoot
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.SportsEsports,
            stringResource(R.string.raCheatInRetroArch),
            if (root == null) {
                stringResource(R.string.raCheatFolderUnknown)
            } else {
                stringResource(R.string.raCheatFolderInfo, BiosPaths.shown(root))
            },
        )
        CardSwitchRow(
            title = stringResource(R.string.raCheatApply),
            info = stringResource(R.string.raCheatApplyInfo),
            checked = state.config?.flag(RaUiModel.APPLY_CHEATS) == true,
            enabled = state.canChange,
            onChange = viewModel::setApplyCheats,
        )
    }
}

/** The games of the chosen console, with a chip per console and a search. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GamesCard(state: RaUiModel, viewModel: RetroArchViewModel) {
    val context = LocalContext.current
    val cheats = state.cheats
    val system = cheats.system
    val systems = cheats.systems
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.VideogameAsset,
            stringResource(R.string.raCheatGames),
            if (cheats.games.isEmpty()) {
                stringResource(R.string.raCheatNoGames)
            } else {
                stringResource(R.string.raCheatGamesInfo, cheats.games.size)
            },
        )
        if (systems.isNotEmpty()) {
            CardRowBox {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    systems.forEach { (each, count) ->
                        FilterChip(
                            selected = each == system,
                            onClick = { viewModel.selectCheatSystem(each) },
                            label = { Text(stringResource(R.string.raCheatSystemChip, each.label, count)) },
                            colors = cardChipColors(),
                        )
                    }
                }
            }
            CardRowBox { SearchField(cheats.search, stringResource(R.string.raCheatSearchGames), viewModel::searchGames) }
        }
        val without = cheats.withoutCheats
        if (without.isNotEmpty()) {
            val names = without.joinToString(", ") { (each, count) -> context.getString(R.string.raCheatSystemChip, each.label, count) }
            CardRowBox { Muted(stringResource(R.string.raCheatWithoutCheats, names)) }
        }
        if (system != null) {
            if (system in cheats.loading) CardRowBox { Muted(stringResource(R.string.raCheatGettingList, system.label)) }
            val (shown, total) = cheats.shownGames(SHOWN)
            shown.forEach { game -> GameRow(state, game, viewModel) }
            if (total > shown.size) CardRowBox { Muted(stringResource(R.string.raCheatShowing, shown.size, total)) }
        }
    }
}

/** A game with how it stands: in RetroArch with how many cheats on, the cheat file found, or none. */
@Composable
private fun GameRow(state: RaUiModel, game: RomGame, viewModel: RetroArchViewModel) {
    val cheats = state.cheats
    val installed = cheats.installed(game, state.found?.version).firstOrNull()
    val pick = cheats.pick(game)
    val info = when {
        installed != null -> stringResource(R.string.raCheatGameInRetroArch, installed.count, installed.on)
        game.system !in cheats.indexes -> null
        pick != null && pick.fit == CheatFit.OTHER_REGION -> stringResource(R.string.raCheatGameOtherRegion, pick.file.stem)
        pick != null -> stringResource(R.string.raCheatGameFound, pick.file.stem)
        game.path in cheats.choices.files -> stringResource(R.string.raCheatGameNone)
        else -> stringResource(R.string.raCheatGameNotFound)
    }
    CardRow(title = game.title, info = info, onClick = { viewModel.openGame(game) })
}

/** The open game: its cheat file, the cores it goes to and the add row, then its cheats. */
@Composable
private fun GameCards(state: RaUiModel, open: OpenGame, viewModel: RetroArchViewModel) {
    val cheats = state.cheats
    val game = open.game
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.SportsEsports, game.title, stringResource(R.string.raCheatGameInfo, game.system.label, game.name))
        CardRow(
            title = stringResource(R.string.raCheatAllGames),
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            onClick = viewModel::closeGame,
        )
        if (game.system in cheats.loading) CardRowBox { Muted(stringResource(R.string.raCheatGettingList, game.system.label)) }
        cheats.indexes[game.system]?.let { index -> CheatFileRows(open, index, game.path in cheats.choices.files, viewModel) }
        CoreRows(state, open, viewModel)
        AddRow(state, open, viewModel)
    }
    if (open.loading) NoteCard(stringResource(R.string.raCheatGettingFile))
    if (open.writing) NoteCard(stringResource(R.string.raCheatWriting))
    if (open.file != null) CheatListCard(open, viewModel)
}

/** The cheat file in use; a tap opens the ones to choose from and a search through the console's whole list. */
@Composable
private fun CheatFileRows(open: OpenGame, index: CheatIndex, chosen: Boolean, viewModel: RetroArchViewModel) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val pick = open.pick
    val choices = (open.candidates + open.similar).distinctBy { it.file }
    var expanded by rememberSaveable(open.game.path) { mutableStateOf(false) }
    val choose: (String?) -> Unit = { name ->
        viewModel.chooseCheatFile(name)
        expanded = false
    }
    CardRowBox {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.raCheatFile), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                Text(pick?.file?.name ?: stringResource(R.string.raCheatNoFileName), style = MaterialTheme.typography.bodyLarge)
                val (text, color) = fitText(pick, chosen)
                Text(text, style = MaterialTheme.typography.bodySmall, color = color)
                if (!expanded) Muted(stringResource(R.string.raCheatChoose, choices.size))
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.raCheatHide else R.string.raCheatShow),
                tint = colors.onSurfaceVariant,
            )
        }
    }
    if (!expanded) return
    choices.forEach { choice ->
        CardCheckRow(
            title = choice.file.name,
            info = listOfNotNull(stringResource(fitLabel(choice.fit)), choice.file.size?.let { size(context, it) }).joinToString(" · "),
            checked = pick?.file == choice.file,
            onChange = { choose(choice.file.name) },
        )
    }
    CardCheckRow(title = stringResource(R.string.raCheatNoneOption), checked = chosen && pick == null, onChange = { choose(null) })
    if (chosen) {
        CardRow(title = stringResource(R.string.raCheatBestOption), onClick = {
            viewModel.useBestCheatFile()
            expanded = false
        })
    }
    CardRowBox {
        SearchField(
            open.fileSearch,
            stringResource(R.string.raCheatSearchFiles, index.files.size, index.system.label),
            viewModel::searchCheatFiles,
        )
    }
    if (open.fileSearch.isNotBlank()) {
        val (found, total) = index.search(open.fileSearch, FILES_SHOWN)
        found.forEach { file ->
            CardCheckRow(
                title = file.name,
                info = file.size?.let { size(context, it) },
                checked = pick?.file == file,
                onChange = { choose(file.name) },
            )
        }
        if (total > found.size) CardRowBox { Muted(stringResource(R.string.raCheatShowing, found.size, total)) }
    }
}

/** The name RetroArch loads, the cores whose folders get the file, and games that share that name. */
@Composable
private fun CoreRows(state: RaUiModel, open: OpenGame, viewModel: RetroArchViewModel) {
    val game = open.game
    CardRowBox { Muted(stringResource(R.string.raCheatLoadsAs, RomNames.cheatFile(game.name, state.found?.version))) }
    if (open.targets.isEmpty()) CardRowBox { Warning(stringResource(R.string.raCheatNoCore, game.system.label)) }
    open.targets.forEach { target ->
        val parts = listOfNotNull(
            stringResource(R.string.raCheatCorePlayed).takeIf { target.played },
            target.installed?.let { stringResource(R.string.raCheatCoreHas, it.count, it.on) },
        ).ifEmpty { listOf(stringResource(R.string.raCheatCoreInstalled)) }
        CardCheckRow(
            title = target.core,
            info = parts.joinToString(" · "),
            checked = target.core in open.cores,
            enabled = !open.writing,
            onChange = { on -> viewModel.tickCore(target.core, on) },
        )
    }
    val sharing = CheatPlan.sharing(game, state.cheats.games, state.found?.version)
    if (sharing.isNotEmpty()) CardRowBox { Warning(stringResource(R.string.raCheatShared, sharing.joinToString(", ") { it.title })) }
}

@Composable
private fun AddRow(state: RaUiModel, open: OpenGame, viewModel: RetroArchViewModel) {
    val update = open.installed.isNotEmpty()
    val backup = RomNames.cheatFile(open.game.name, state.found?.version) + RetroArchFiles.BACKUP_SUFFIX
    CardRow(
        title = stringResource(if (update) R.string.raCheatUpdate else R.string.raCheatAdd, open.ticks.size),
        info = if (update) stringResource(R.string.raCheatUpdateInfo, backup) else stringResource(R.string.raCheatAddInfo),
        icon = Icons.Rounded.AddCircleOutline,
        enabled = open.file != null && open.cores.isNotEmpty() && !open.writing && state.cheats.side != null && state.config != null,
        onClick = viewModel::addCheats,
    )
}

/** The file's cheats to tick, the first [SHOWN] the search finds. */
@Composable
private fun CheatListCard(open: OpenGame, viewModel: RetroArchViewModel) {
    val all = open.file?.cheats.orEmpty()
    val (shown, total) = open.shownCheats(SHOWN)
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.Checklist,
            stringResource(R.string.raCheatCheats, all.size),
            stringResource(R.string.raCheatCheatsInfo, open.ticks.size),
        )
        CardRowBox { SearchField(open.cheatSearch, stringResource(R.string.raCheatSearchCheats), viewModel::searchCheats) }
        CardRow(
            title = stringResource(R.string.raCheatTickNone),
            enabled = open.ticks.isNotEmpty() && !open.writing,
            onClick = viewModel::tickNoCheats,
        )
        shown.forEach { cheat ->
            CardCheckRow(
                title = cheat.description.ifBlank { stringResource(R.string.raCheatUnnamed, cheat.index + 1) },
                checked = cheat.index in open.ticks,
                enabled = !open.writing,
                onChange = { on -> viewModel.tickCheat(cheat.index, on) },
            )
        }
        if (total > shown.size) CardRowBox { Muted(stringResource(R.string.raCheatShowing, shown.size, total)) }
    }
}

@Composable
private fun CheatNoteCards(note: CheatNote?) {
    when (note) {
        null -> Unit
        CheatNote.ScanFailed -> NoteCard(stringResource(R.string.raCheatScanFailed), warning = true)
        CheatNote.Unusable -> NoteCard(stringResource(R.string.raCheatUnusable), caution = true)
        CheatNote.PackReady -> NoteCard(stringResource(R.string.raCheatPackReady))
        is CheatNote.Problem -> NoteCard(problemText(note.problem), warning = true)
        is CheatNote.Added -> {
            if (note.cores.isNotEmpty()) NoteCard(stringResource(R.string.raCheatAdded, note.name, note.cores.joinToString(", "), note.on))
            note.failed.forEach { (core, why) -> NoteCard(stringResource(R.string.raCheatAddFailed, core, why), warning = true) }
        }
    }
}

@Composable
private fun problemText(problem: CheatProblem): String = when (problem) {
    is CheatProblem.Offline -> stringResource(R.string.raCheatOffline)
    is CheatProblem.RateLimited -> problem.resetAt?.let { at ->
        stringResource(R.string.raCheatRateLimitedAt, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at * MILLIS)))
    } ?: stringResource(R.string.raCheatRateLimited)
    is CheatProblem.Http -> stringResource(R.string.raCheatHttp, problem.code)
    is CheatProblem.NoPack -> stringResource(R.string.raCheatNoPack)
    is CheatProblem.Failed -> stringResource(R.string.raCheatFailed, problem.detail)
}

/** How the file in use fits the game, in the colour that says whether to check it. */
@Composable
private fun fitText(pick: CheatPick?, chosen: Boolean): Pair<String, Color> {
    val colors = MaterialTheme.colorScheme
    return when {
        pick == null && chosen -> stringResource(R.string.raCheatNoneChosen) to colors.onSurfaceVariant
        pick == null -> stringResource(R.string.raCheatNoFile) to colors.error
        else -> {
            val text = stringResource(fitLabel(pick.fit))
            val shown = if (chosen) stringResource(R.string.raCheatYourChoice, text) else text
            shown to if (pick.fit == CheatFit.OTHER_REGION || pick.fit == CheatFit.SIMILAR) colors.error else colors.primary
        }
    }
}

@StringRes
private fun fitLabel(fit: CheatFit): Int = when (fit) {
    CheatFit.SAME_NAME -> R.string.raCheatFitSameName
    CheatFit.SAME_REGION -> R.string.raCheatFitSameRegion
    CheatFit.NO_REGION -> R.string.raCheatFitNoRegion
    CheatFit.OTHER_REGION -> R.string.raCheatFitOtherRegion
    CheatFit.SIMILAR -> R.string.raCheatFitSimilar
}

@Composable
private fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Warning(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

private const val MILLIS = 1000L

private fun size(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

private fun date(time: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))

private fun dateTime(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
