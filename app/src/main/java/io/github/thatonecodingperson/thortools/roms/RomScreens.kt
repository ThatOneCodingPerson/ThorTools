package io.github.thatonecodingperson.thortools.roms

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.retroarch.BiosPaths
import io.github.thatonecodingperson.thortools.retroarch.storageStart
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardHeader
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.CardRowBox
import io.github.thatonecodingperson.thortools.ui.composables.ChoiceCard
import io.github.thatonecodingperson.thortools.ui.composables.CreditNote
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import java.text.DateFormat
import java.util.Date

/** Extra tools → ROM compression: the ROM folders, the consoles found in them, the jobs, the speed and the guide. */
@Composable
fun RomCompressionScreen(onConsole: (RomConsole) -> Unit, onBack: () -> Unit, viewModel: RomViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), viewModel::onFolderPicked)
    val pick: () -> Unit = { runCatching { picker.launch(storageStart(context)) } }
    Scaffold(topBar = { SubTopAppBar(title = R.string.romTitle, onBack = onBack) }) { padding ->
        CardColumns(
            padding = padding,
            left = {
                if (!state.engineReady) NoteCard(stringResource(R.string.romNoEngine), warning = true)
                FoldersCard(state, viewModel, pick)
                JobsCard(state, viewModel)
                SavedCard(state, viewModel)
                ChoiceCard(
                    icon = Icons.Rounded.Speed,
                    title = stringResource(R.string.romSpeed),
                    info = stringResource(speedInfo(state.speed)),
                    options = RomSpeed.entries.map { it to stringResource(speedLabel(it)) },
                    selected = state.speed,
                    onSelect = viewModel::setSpeed,
                )
                GuideCard()
                CreditNote(stringResource(R.string.romCredit), stringResource(R.string.romCreditLink), CHDMAN_URL)
                CreditNote(stringResource(R.string.romCreditMaxcso), stringResource(R.string.romCreditMaxcsoLink), MAXCSO_URL)
                CreditNote(stringResource(R.string.romCreditNod), stringResource(R.string.romCreditNodLink), NOD_URL)
                CreditNote(stringResource(R.string.romCredit7zip), stringResource(R.string.romCredit7zipLink), SEVEN_ZIP_URL)
                CreditNote(stringResource(R.string.romCreditZstd), stringResource(R.string.romCreditZstdLink), ZSTD_URL)
                CreditNote(stringResource(R.string.romCreditZarchive), stringResource(R.string.romCreditZarchiveLink), ZARCHIVE_URL)
            },
            right = {
                ConsolesCard(state, onConsole)
                OthersCard(state)
            },
        )
    }
}

@Composable
private fun FoldersCard(state: RomUiModel, viewModel: RomViewModel, pick: () -> Unit) {
    val shelf = state.shelf
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.Folder,
            stringResource(R.string.romFolders),
            stringResource(if (shelf.folders.isEmpty()) R.string.romFoldersEmpty else R.string.romFoldersInfo),
        )
        shelf.folders.forEachIndexed { index, folder ->
            val reached = shelf.reached.getOrNull(index) != null || shelf.time == 0L
            val consoles = consolesIn(state, folder)
            CardRow(
                title = folder,
                info = when {
                    !reached -> stringResource(R.string.romFolderMissing)
                    consoles.isEmpty() -> stringResource(R.string.romFolderNoConsoles)
                    else -> consoles
                },
                value = stringResource(R.string.romRemove),
                danger = false,
                onClick = { viewModel.removeFolder(folder) },
            )
        }
        shelf.suggestions.forEach { suggestion ->
            CardRow(
                title = stringResource(R.string.romSuggestion, suggestion),
                info = stringResource(R.string.romSuggestionInfo),
                value = stringResource(R.string.romAdd),
                onClick = { viewModel.addFolder(suggestion) },
            )
        }
        CardRow(title = stringResource(R.string.romAddFolder), info = stringResource(R.string.romAddFolderInfo), onClick = pick)
        val retroArch = viewModel.retroArchFolders.filter { ra -> (shelf.folders + shelf.suggestions).none { BiosPaths.isUnder(ra, it) } }
        if (retroArch.isNotEmpty()) {
            CardRow(
                title = stringResource(R.string.romUseRetroArch),
                info = retroArch.joinToString("\n"),
                onClick = viewModel::addRetroArchFolders,
            )
        }
        if (shelf.folders.isNotEmpty()) {
            CardRow(
                title = stringResource(if (shelf.scanning) R.string.romScanning else R.string.romLookAgain),
                info = if (shelf.time >
                    0
                ) {
                    stringResource(R.string.romLookedAt, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(shelf.time)))
                } else {
                    null
                },
                enabled = !shelf.scanning,
                onClick = viewModel::rescan,
            )
        }
        if (shelf.noRoot) CardRowBox { Warning(stringResource(R.string.romNoRoot)) }
        if (state.note == RomNote.FolderUnusable) CardRowBox { Warning(stringResource(R.string.romFolderUnusable)) }
    }
}

@Composable
private fun consolesIn(state: RomUiModel, folder: String): String {
    val added = state.shelf.games.filter { (_, games) -> games.any { BiosPaths.isUnder(it.folder, folder) } }
        .map { (console, games) -> "${stringResource(consoleName(console))} (${games.count { BiosPaths.isUnder(it.folder, folder) }})" }
    return added.joinToString(", ")
}

@Composable
private fun SavedCard(state: RomUiModel, viewModel: RomViewModel) {
    val done = state.shelf.history.filter { it.kind == JobKind.COMPRESS }.associateBy { "${it.console}/${it.title}" }.values
    if (done.isEmpty()) return
    val context = LocalContext.current
    val saved = done.sumOf { (it.original - it.actual).coerceAtLeast(0) }
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.Savings,
            stringResource(R.string.romSaved),
            pluralStringResource(R.plurals.romSavedInfo, done.size, done.size),
        )
        CardRowBox { Text(size(context, saved), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
        CardRow(
            title = stringResource(R.string.romSavedReset),
            info = stringResource(R.string.romSavedResetInfo),
            onClick = viewModel::clearHistory,
        )
    }
}

@Composable
private fun GuideCard() {
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.Info, stringResource(R.string.romGuide), stringResource(R.string.romGuideInfo))
        CardRowBox {
            listOf(R.string.romGuide1, R.string.romGuide2, R.string.romGuide3, R.string.romGuide4, R.string.romGuide5).forEach {
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ConsolesCard(state: RomUiModel, onConsole: (RomConsole) -> Unit) {
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.SportsEsports, stringResource(R.string.romConsoles), stringResource(R.string.romConsolesInfo))
        val found = RomConsole.entries.filter { state.shelf.games[it].orEmpty().isNotEmpty() }
        if (found.isEmpty()) {
            CardRowBox {
                Muted(stringResource(if (state.shelf.folders.isEmpty()) R.string.romConsoleNotLooked else R.string.romConsolesNone))
            }
        }
        found.groupBy { it.maker }.toSortedMap().forEach { (maker, consoles) ->
            CardRowBox {
                Text(
                    stringResource(makerName(maker)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            consoles.sortedBy { it.year }.forEach { console -> ConsoleRow(state, console, onConsole) }
        }
    }
}

/** One console on the ROM page: its games, their size, and what compressing them saves. */
@Composable
private fun ConsoleRow(state: RomUiModel, console: RomConsole, onConsole: (RomConsole) -> Unit) {
    val context = LocalContext.current
    val games = state.shelf.games[console].orEmpty()
    val open = games.filter { it.canCompress }
    val compressed = games.count { it.isCompressed }
    val changing = games.filter { state.convertTarget(it) != null }
    val saving = open.sumOf { it.originalSize - state.estimate(it).middle }
    val converting = changing.sumOf { it.sizeNow - state.estimate(it).middle }
    CardRow(
        title = stringResource(consoleName(console)),
        info = stringResource(
            R.string.romConsoleLine,
            games.size,
            size(
                context,
                games.sumOf {
                    it.originalSize + (it.compressed?.size ?: 0)
                },
            ),
            compressed,
        ),
        value = when {
            open.isNotEmpty() || converting > 0 ->
                stringResource(R.string.romAboutSaving, size(context, saving + converting.coerceAtLeast(0)))
            games.any { it.originalsLeft } ->
                stringResource(R.string.romFrees, size(context, games.filter { it.originalsLeft }.sumOf { it.originalSize }))
            else -> null
        },
        onClick = { onConsole(console) },
    )
}

@Composable
private fun OthersCard(state: RomUiModel) {
    val others = state.shelf.others
    if (others.isEmpty()) return
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.Inventory2, stringResource(R.string.romOthers), stringResource(R.string.romOthersInfo))
        CardRowBox {
            Text(
                others.entries.joinToString(", ") { (system, count) -> "${system.label} ($count)" },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** A console's games: the summary, its emulators, and the list to tick and compress. */
@Composable
fun RomConsoleScreen(onGame: (DiscGame) -> Unit, onBack: () -> Unit, viewModel: RomViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val console = state.console ?: return
    Scaffold(topBar = { SubTopAppBar(title = consoleName(console), onBack = onBack) }) { padding ->
        CardColumns(
            padding = padding,
            left = {
                SummaryCard(state, viewModel, console)
                EmulatorsCard(viewModel, console)
                JobsCard(state, viewModel, showNow = false)
            },
            right = { GamesCard(state, viewModel, onGame) },
        )
    }
    state.ask?.let { AskDialog(it, viewModel::confirm, viewModel::dismissAsk, viewModel::spaceDeleting, viewModel::spaceFewer) }
}

@Composable
private fun SummaryCard(state: RomUiModel, viewModel: RomViewModel, console: RomConsole) {
    val context = LocalContext.current
    val games = state.games
    val open = games.filter { it.canCompress }
    val now = open.sumOf { it.originalSize }
    val after = open.sumOf { viewModel.estimate(it).middle }
    val left = games.filter { it.originalsLeft }
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.Album,
            stringResource(R.string.romSummary),
            stringResource(R.string.romSummaryInfo, stringResource(consoleName(console))),
        )
        CardRowBox {
            Text(pluralStringResource(R.plurals.romSummaryGames, games.size, games.size), style = MaterialTheme.typography.bodyLarge)
            if (open.isNotEmpty()) {
                Muted(
                    stringResource(
                        R.string.romSummaryCould,
                        open.size,
                        size(context, now),
                        size(context, after),
                        size(
                            context,
                            now - after,
                        ),
                    ),
                )
            }
            val changing = games.filter { state.convertTarget(it) != null }
            if (changing.isNotEmpty()) {
                val target = state.listFormat(console)
                val before = changing.sumOf { it.sizeNow }
                val later = changing.sumOf { state.estimate(it, target).middle }
                Muted(
                    stringResource(
                        R.string.romSummaryConvert,
                        changing.size,
                        stringResource(formatName(target)),
                        size(context, before),
                        size(context, later),
                    ),
                )
            }
            val compressed = games.count { it.isCompressed }
            if (compressed > 0) Muted(stringResource(R.string.romSummaryCompressed, compressed))
            if (left.isNotEmpty()) Muted(stringResource(R.string.romSummaryLeft, left.size, size(context, left.sumOf { it.originalSize })))
            val problems = games.count { it.problem != null }
            if (problems > 0) Muted(stringResource(R.string.romSummaryProblems, problems))
            if (state.shelf.measuring) Muted(stringResource(R.string.romMeasuring))
        }
    }
}

@Composable
private fun EmulatorsCard(viewModel: RomViewModel, console: RomConsole) {
    if (console.allFormats.size > 1) {
        FormatsEmulatorsCard(viewModel, console)
        return
    }
    val format = console.allFormats.first()
    val checks = viewModel.checks(console, format)
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.VideogameAsset,
            stringResource(R.string.romEmulators, stringResource(consoleName(console))),
            stringResource(
                if (checks.isEmpty()) R.string.romEmulatorsNone else R.string.romEmulatorsInfo,
                stringResource(formatName(format)),
            ),
        )
        checks.forEach { check ->
            CardRowBox {
                Text(listOfNotNull(check.name, check.version).joinToString(" "), style = MaterialTheme.typography.bodyLarge)
                if (check.opens == true) Muted(checkText(check)) else Warning(checkText(check))
            }
        }
        if (checks.isNotEmpty() && !EmulatorChecks.anyOpens(checks)) {
            CardRowBox { Warning(stringResource(R.string.romEmulatorsWarning, stringResource(formatName(format)))) }
        }
    }
}

/** The installed emulators of a console with several formats: each one with the formats it opens and doesn't. */
@Composable
private fun FormatsEmulatorsCard(viewModel: RomViewModel, console: RomConsole) {
    val formats = console.allFormats
    val perFormat = formats.associateWith { viewModel.checks(console, it) }
    val names = perFormat.values.flatten().map { it.name }.distinct()
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.VideogameAsset,
            stringResource(R.string.romEmulators, stringResource(consoleName(console))),
            stringResource(if (names.isEmpty()) R.string.romEmulatorsNoneMulti else R.string.romEmulatorsInfoMulti),
        )
        names.forEach { name ->
            val checks = formats.mapNotNull { format -> perFormat[format]?.firstOrNull { it.name == name }?.let { format to it } }
            val version = checks.firstOrNull()?.second?.version
            val opens = checks.filter { it.second.opens == true }.map { stringResource(listName(it.first)) }
            val needs = checks.filter { it.second.needs != null }
            val no = checks.filter { it.second.opens == false && it.second.needs == null }.map { stringResource(listName(it.first)) }
            val unknown = checks.filter { it.second.opens == null }.map { stringResource(listName(it.first)) }
            CardRowBox {
                Text(listOfNotNull(name, version).joinToString(" "), style = MaterialTheme.typography.bodyLarge)
                if (opens.isNotEmpty()) Muted(stringResource(R.string.romOpensFormats, opens.joinToString(", ")))
                needs.groupBy { it.second.needs }.forEach { (since, list) ->
                    val which = list.map { stringResource(listName(it.first)) }.joinToString(", ")
                    Warning(stringResource(R.string.romOpensNeeds, name, since.orEmpty(), which))
                }
                if (no.isNotEmpty()) Muted(stringResource(R.string.romOpensNot, no.joinToString(", ")))
                if (unknown.isNotEmpty()) Muted(stringResource(R.string.romOpensUnknown, unknown.joinToString(", ")))
                if (checks.any { it.second.unlisted }) Muted(stringResource(R.string.romUnlisted))
            }
        }
        if (names.isNotEmpty() && formats.none { EmulatorChecks.anyOpens(perFormat[it].orEmpty()) }) {
            CardRowBox { Warning(stringResource(R.string.romEmulatorsWarningMulti)) }
        }
    }
}

@Composable
private fun GamesCard(state: RomUiModel, viewModel: RomViewModel, onGame: (DiscGame) -> Unit) {
    val context = LocalContext.current
    val all = state.games
    val shown = state.shown
    val internal = stringResource(R.string.romInternalStorage)
    val card = stringResource(R.string.romSdCard)
    val places = remember(all, internal, card) { GameList.places(all, internal, card) }
    // Select all stands for the games to compress when the list shows any, else those with originals to clean up.
    val selectable = state.selectable
    val toCompress = state.toCompress
    val toClean = state.toClean
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.SportsEsports,
            stringResource(R.string.romGames),
            if (all.isEmpty()) stringResource(R.string.romGamesNone) else stringResource(R.string.romGamesInfo),
        )
        NowCompressing(state, viewModel)
        CardRowBox {
            val console = state.console
            if (console != null &&
                console.formatsFor(console.listKind).size > 1 &&
                all.any { it.kind == console.listKind && (it.canCompress || it.canConvert) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Text(stringResource(R.string.romCompressInto), style = MaterialTheme.typography.bodyMedium)
                    RomDropdown(
                        options = console.formatsFor(console.listKind),
                        selected = state.listFormat(console),
                        label = { stringResource(formatName(it)) },
                        detail = { stringResource(formatInfo(it, console)) },
                        modifier = Modifier.weight(1f),
                        onSelect = { viewModel.setListFormat(console, it) },
                    )
                }
            }
            if (toCompress.isNotEmpty() || !state.cleanFirst) {
                CompressBar(
                    text = if (toCompress.isEmpty()) {
                        stringResource(R.string.romCompressNone)
                    } else {
                        pluralStringResource(R.plurals.romCompressGames, toCompress.size, toCompress.size)
                    },
                    enabled = toCompress.isNotEmpty() && state.engineReady,
                    originals = state.originals,
                    onOriginals = viewModel::setOriginals,
                    onCompress = viewModel::compressTicked,
                )
                if (toCompress.isNotEmpty()) {
                    val now = toCompress.sumOf { it.sizeNow }
                    val after = toCompress.sumOf { viewModel.estimate(it).middle }
                    Muted(stringResource(R.string.romTickedSummary, size(context, now), size(context, after), size(context, now - after)))
                }
            }
            if (toClean.isNotEmpty() || state.cleanFirst) {
                if (toCompress.isNotEmpty()) Spacer(Modifier.height(8.dp))
                CompressBar(
                    text = if (toClean.isEmpty()) {
                        stringResource(R.string.romCleanNone)
                    } else {
                        pluralStringResource(R.plurals.romCleanGames, toClean.size, toClean.size)
                    },
                    enabled = toClean.isNotEmpty() && state.engineReady,
                    originals = state.cleanup,
                    onOriginals = viewModel::setCleanup,
                    onCompress = viewModel::cleanTicked,
                    choices = listOf(Originals.DELETE, Originals.MOVE),
                )
                if (toClean.isNotEmpty()) {
                    val originals = size(context, toClean.sumOf { it.originalSize })
                    Muted(
                        if (state.cleanup == Originals.DELETE) {
                            stringResource(R.string.romCleanDeleteSummary, originals)
                        } else {
                            stringResource(R.string.romCleanMoveSummary, originals)
                        },
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                RomDropdown(
                    options = GameFilter.entries,
                    selected = state.filter,
                    label = { stringResource(filterLabel(it)) },
                    detail = {
                        val count = all.count { game -> GameList.matches(game, it) }
                        pluralStringResource(R.plurals.romSummaryGames, count, count)
                    },
                    modifier = Modifier.weight(1f),
                    onSelect = viewModel::setFilter,
                )
                RomDropdown(
                    options = GameSort.entries,
                    selected = state.sort,
                    label = { stringResource(sortLabel(it)) },
                    detail = { stringResource(sortInfo(it)) },
                    modifier = Modifier.weight(1f),
                    onSelect = viewModel::setSort,
                )
            }
        }
        // Select all lines up with the games' own boxes; the search field takes the rest of its line.
        val allTicked = selectable.isNotEmpty() && selectable.all { it.id in state.ticked }
        val selectAll = stringResource(R.string.romSelectAllShort)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, bottom = 4.dp),
        ) {
            Checkbox(
                checked = allTicked,
                onCheckedChange = { viewModel.selectAll() },
                enabled = selectable.isNotEmpty(),
                modifier = Modifier.semantics { contentDescription = selectAll },
            )
            if (all.size > SEARCH_FROM) {
                GameSearch(state.search, viewModel::setSearch, Modifier.weight(1f))
            } else {
                Text(
                    stringResource(R.string.romSelectAll, selectable.size),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable(enabled = selectable.isNotEmpty(), onClick = viewModel::selectAll),
                )
            }
        }
        shown.take(SHOWN).forEach { game ->
            GameRow(
                title = game.title,
                info = gameInfo(state, viewModel, game).let { info ->
                    places[game.id]?.let { stringResource(R.string.romGameIn, it) + " · " + info } ?: info
                },
                value = gameValue(state, viewModel, game),
                checked = game.id in state.ticked,
                canTick = (state.canMake(game) || game.originalsLeft) && !state.busy(game),
                onTick = { viewModel.tick(game, it) },
                onOpen = { onGame(game) },
            )
        }
        if (shown.size > SHOWN) CardRowBox { Muted(stringResource(R.string.romShowing, SHOWN, shown.size)) }
        if (shown.isEmpty() && all.isNotEmpty()) CardRowBox { Muted(stringResource(R.string.romNoneHere)) }
    }
}

/** What a waiting or running game says instead of its sizes: being compressed (how far, paused), or its place in line. */
@Composable
private fun placeText(state: RomUiModel, game: DiscGame): String? {
    val place = state.place(game) ?: return null
    if (place == 0) {
        val running = state.runner.current ?: return null
        val percent = running.progress?.percent?.toInt()
        val working = stringResource(
            when {
                state.runner.paused -> R.string.romGamePaused
                running.job.kind == JobKind.ORIGINALS -> R.string.romGameCleaning
                running.job.kind == JobKind.EXTRACT -> R.string.romGameExtracting
                else -> R.string.romGameWorking
            },
        )
        return working + (percent?.let { " · " + stringResource(R.string.romPercent, it) } ?: "")
    }
    return if (place == 1) stringResource(R.string.romGameNext) else stringResource(R.string.romGameInLine, place)
}

@Composable
private fun gameInfo(state: RomUiModel, viewModel: RomViewModel, game: DiscGame): String {
    val context = LocalContext.current
    placeText(state, game)?.let { return it }
    game.problem?.let { return problemText(it) }
    val compressed = game.compressed
    return when {
        compressed != null && game.originalsLeft -> stringResource(
            R.string.romGameBoth,
            size(context, compressed.size),
            size(context, game.originalSize),
        )
        compressed != null -> listOfNotNull(
            game.current?.let { stringResource(formatName(it)) },
            size(context, compressed.size),
            game.imageSize?.let {
                val label = when (game.kind) {
                    DiscKind.CART -> R.string.romRomSize
                    DiscKind.FOLDER -> R.string.romUnpackedSize
                    else -> R.string.romImageSize
                }
                stringResource(label, size(context, it))
            },
        ).joinToString(" · ")
        else -> {
            val estimate = viewModel.estimate(game)
            val sizes = stringResource(
                if (estimate.measured) R.string.romGameEstimate else R.string.romGameTypical,
                size(context, game.originalSize),
                estimate.savingPercent(game.originalSize),
            )
            val tracks = game.files.size - 1
            when {
                game.kind == DiscKind.FOLDER && game.titles.size > 1 ->
                    sizes + " · " + pluralStringResource(R.plurals.romTitles, game.titles.size - 1, game.titles.size - 1)
                game.lone || tracks <= 1 || game.kind == DiscKind.FOLDER -> sizes
                else -> sizes + " · " + pluralStringResource(R.plurals.romTracks, tracks, tracks)
            }
        }
    }
}

@Composable
private fun gameValue(state: RomUiModel, viewModel: RomViewModel, game: DiscGame): String? {
    if (state.busy(game)) return null
    val context = LocalContext.current
    return when {
        game.canCompress -> stringResource(R.string.romSaves, size(context, game.originalSize - viewModel.estimate(game).middle))
        game.originalsLeft -> stringResource(R.string.romFrees, size(context, game.originalSize))
        else -> state.convertTarget(game)?.let { target -> convertValue(viewModel, game, target) }
    }
}

/** What turning a compressed game into [target] does: "→ CHD: saves 61 MB", or "→ ISO: 1.7 GB". */
@Composable
private fun convertValue(viewModel: RomViewModel, game: DiscGame, target: RomFormat): String {
    val context = LocalContext.current
    val after = viewModel.estimate(game, target).middle
    val name = stringResource(shortName(target))
    return when {
        target.tool == RomTool.NONE -> stringResource(R.string.romToSize, name, size(context, after))
        after < game.sizeNow -> stringResource(R.string.romToSaves, name, size(context, game.sizeNow - after))
        else -> stringResource(R.string.romToCosts, name, size(context, after - game.sizeNow))
    }
}

@Composable
private fun problemText(problem: GameProblem): String = when (problem) {
    is GameProblem.MissingTracks -> {
        val missing = if (problem.names.size == 1) {
            stringResource(R.string.romProblemMissingOne, problem.names.first())
        } else {
            stringResource(R.string.romProblemMissing, problem.names.size, problem.total)
        }
        problem.copyIn?.let { missing + " " + stringResource(R.string.romProblemCopyIn, it.substringAfterLast('/')) } ?: missing
    }
    GameProblem.UnreadableSheet -> stringResource(R.string.romProblemSheet)
    GameProblem.NeedsSheet -> stringResource(R.string.romProblemNeedsSheet)
    GameProblem.OddSize -> stringResource(R.string.romProblemOddSize)
    GameProblem.NotDisc -> stringResource(R.string.romProblemNotDisc)
    GameProblem.NotGame -> stringResource(R.string.romProblemNotGame)
    GameProblem.NKit -> stringResource(R.string.romProblemNKit)
    GameProblem.CantRead -> stringResource(R.string.romProblemCantRead)
    GameProblem.Empty -> stringResource(R.string.romProblemEmpty)
    GameProblem.NotOneGame -> stringResource(R.string.romProblemNotOneGame)
    GameProblem.Encrypted -> stringResource(R.string.romProblemEncrypted)
    GameProblem.NeedsKeys -> stringResource(R.string.romProblemNeedsKeys)
    GameProblem.LoneTitle -> stringResource(R.string.romProblemLoneTitle)
    is GameProblem.NotHandled -> stringResource(notHandledText(problem.extension), problem.extension)
}

private fun notHandledText(extension: String): Int = when (extension) {
    "ccd" -> R.string.romNotHandledCcd
    "pbp" -> R.string.romNotHandledPbp
    "7z", "zip" -> R.string.romNotHandledArchive
    "iso" -> R.string.romNotHandledIso
    "gz" -> R.string.romNotHandledGz
    else -> R.string.romNotHandled
}

/** One game: its files, the format with its estimate, Compress, and what else can be done with it. */
@Composable
fun RomGameScreen(onBack: () -> Unit, viewModel: RomViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val game = state.game
    Scaffold(topBar = { SubTopAppBar(title = R.string.romGame, onBack = onBack) }) { padding ->
        if (game == null) {
            CardColumns(padding = padding, left = { NoteCard(stringResource(R.string.romGameGone)) }, right = {})
            return@Scaffold
        }
        CardColumns(
            padding = padding,
            left = {
                FilesCard(game)
                EmulatorsCard(viewModel, game.console)
            },
            right = {
                CompressCard(state, viewModel, game)
                MoreCard(state, viewModel, game)
                JobsCard(state, viewModel)
                when (state.note) {
                    RomNote.PlaylistMade -> NoteCard(stringResource(R.string.romPlaylistMade))
                    RomNote.PlaylistFailed -> NoteCard(stringResource(R.string.romPlaylistFailed), warning = true)
                    else -> Unit
                }
            },
        )
    }
    state.ask?.let { AskDialog(it, viewModel::confirm, viewModel::dismissAsk, viewModel::spaceDeleting, viewModel::spaceFewer) }
}

@Composable
private fun FilesCard(game: DiscGame) {
    val context = LocalContext.current
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.Album, game.title, game.folder)
        if (game.kind == DiscKind.FOLDER && game.main != null) {
            // A Wii U game's title folders (the game, its update, its DLC) instead of their many files.
            game.titles.values.ifEmpty { listOf(game.main) }.forEach { root ->
                val inside = game.files.filter { it.path.startsWith("$root/") }
                CardRowBox {
                    Text(root.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium)
                    Muted(pluralStringResource(R.plurals.romFolderFiles, inside.size, inside.size, size(context, inside.sumOf { it.size })))
                }
            }
        }
        game.files.takeIf { game.kind != DiscKind.FOLDER }.orEmpty().forEach { file ->
            CardRowBox {
                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                Muted(
                    listOfNotNull(
                        size(context, file.size),
                        stringResource(R.string.romFileAudio).takeIf { file.audio },
                        stringResource(R.string.romFileLone).takeIf { game.lone && file.path == game.main },
                        stringResource(R.string.romFileImage).takeIf { game.kind == DiscKind.DVD && file.path == game.main },
                    ).joinToString(" · "),
                )
            }
        }
        game.compressed?.let { chd ->
            CardRowBox {
                Text(chd.name, style = MaterialTheme.typography.bodyMedium)
                Muted(stringResource(R.string.romFileCompressed, size(context, chd.size)))
            }
        }
        if (game.elsewhere) CardRowBox { Muted(stringResource(R.string.romFileElsewhere)) }
        game.sbi?.let { CardRowBox { Muted(stringResource(R.string.romFileSbi, it.substringAfterLast('/'))) } }
        game.playlists.forEach { CardRowBox { Muted(stringResource(R.string.romFilePlaylist, it.substringAfterLast('/'))) } }
        game.problem?.let { CardRowBox { Warning(problemText(it)) } }
    }
}

@Composable
private fun CompressCard(state: RomUiModel, viewModel: RomViewModel, game: DiscGame) {
    if (!game.canCompress && !game.canConvert) return
    val context = LocalContext.current
    val targets = game.targets
    val format = state.gameFormat?.takeIf { it in targets }
        ?: (state.convertTarget(game) ?: state.formatFor(game)).takeIf { it in targets }
        ?: targets.firstOrNull()
        ?: return
    val estimate = viewModel.estimate(game, format)
    val readers = viewModel.checks(game.console, format).filter { it.opens == true }.joinToString(", ") { it.name }
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.Compress,
            stringResource(R.string.romFormat),
            stringResource(if (estimate.measured) R.string.romFormatMeasured else R.string.romFormatTypical),
        )
        CardRowBox {
            RomDropdown(
                options = targets,
                selected = format,
                label = { stringResource(formatName(it)) },
                detail = { option ->
                    val guess = viewModel.estimate(game, option)
                    val opens = viewModel.checks(game.console, option).filter { it.opens == true }.joinToString(", ") { it.name }
                    listOfNotNull(
                        stringResource(formatInfo(option, game.console)).takeIf { game.formats.size > 1 },
                        stringResource(
                            R.string.romFormatDetail,
                            guess.savingPercent(game.originalSize),
                            size(context, game.originalSize - guess.middle),
                            size(context, guess.low),
                            size(context, guess.high),
                        ),
                        if (opens.isEmpty()) {
                            stringResource(
                                R.string.romFormatNoReader,
                            )
                        } else {
                            stringResource(R.string.romFormatReaders, opens)
                        },
                    ).joinToString("\n")
                },
                modifier = Modifier.fillMaxWidth(),
                onSelect = viewModel::setGameFormat,
            )
            if (game.formats.size > 1) Muted(stringResource(formatInfo(format, game.console)))
            Muted(
                stringResource(
                    R.string.romFormatDetail,
                    estimate.savingPercent(game.originalSize),
                    size(context, game.originalSize - estimate.middle),
                    size(context, estimate.low),
                    size(context, estimate.high),
                ),
            )
            Muted(if (readers.isEmpty()) stringResource(R.string.romFormatNoReader) else stringResource(R.string.romFormatReaders, readers))
            viewModel.frontEndsMissing(game.console, format).takeIf { it.isNotEmpty() }?.let { ends ->
                Warning(stringResource(R.string.romFormatFrontEnds, ends.joinToString(", ")))
            }
            CompressBar(
                text = placeText(state, game) ?: stringResource(
                    when (format) {
                        RomFormat.ISO -> R.string.romUnpack
                        RomFormat.ROM_FILE -> R.string.romUnpackRom
                        RomFormat.FOLDERS -> R.string.romUnpackFolders
                        else -> R.string.romCompress
                    },
                ),
                enabled = !state.busy(game) && state.engineReady,
                originals = state.originals,
                onOriginals = viewModel::setOriginals,
                onCompress = { viewModel.compress(game) },
            )
        }
    }
}

@Composable
private fun MoreCard(state: RomUiModel, viewModel: RomViewModel, game: DiscGame) {
    val context = LocalContext.current
    val discs = state.shelf.games[game.console].orEmpty().filter { other ->
        other.folder == game.folder &&
            other.disc != null &&
            RomSets.playlistTitle(other.title).equals(RomSets.playlistTitle(game.title), ignoreCase = true)
    }
    val needsPlaylist = game.console.playlists && game.disc != null && discs.size > 1 && discs.all { it.playlists.isEmpty() }
    val rows = game.originalsLeft || (game.compressed != null && game.main == null) || needsPlaylist
    if (!rows) return
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.Inventory2, stringResource(R.string.romMore), null)
        if (game.originalsLeft) {
            CardRow(
                title = stringResource(R.string.romDeleteOriginals),
                info = stringResource(R.string.romDeleteOriginalsInfo, size(context, game.originalSize)),
                danger = true,
                enabled = !state.busy(game),
                onClick = { viewModel.removeOriginals(game, Originals.DELETE) },
            )
            CardRow(
                title = stringResource(R.string.romMoveOriginals),
                info = stringResource(R.string.romMoveOriginalsInfo, RomJobs.KEPT_FOLDER),
                enabled = !state.busy(game),
                onClick = { viewModel.removeOriginals(game, Originals.MOVE) },
            )
        }
        if (game.compressed != null && game.main == null) {
            val image = game.kind == DiscKind.DVD || game.packed?.tool == RomTool.MAXCSO || (game.console.takesIso && game.kind == null)
            val cart = game.kind == DiscKind.CART
            val folders = game.kind == DiscKind.FOLDER
            CardRow(
                title = stringResource(
                    when {
                        cart -> R.string.romExtractRom
                        folders -> R.string.romExtractFolders
                        image -> R.string.romExtractIso
                        else -> R.string.romExtract
                    },
                ),
                info = stringResource(if (image || cart || folders) R.string.romExtractInfoImage else R.string.romExtractInfo),
                enabled = !state.busy(game),
                onClick = { viewModel.extract(game) },
            )
        }
        if (needsPlaylist) {
            CardRow(
                title = stringResource(R.string.romMakePlaylist),
                info = stringResource(R.string.romMakePlaylistInfo, "${RomSets.playlistTitle(game.title)}.m3u", discs.size),
                onClick = { viewModel.makePlaylist(discs) },
            )
        }
    }
}

fun consoleName(console: RomConsole): Int = when (console) {
    RomConsole.PS1 -> R.string.romConsolePs1
    RomConsole.PS2 -> R.string.romConsolePs2
    RomConsole.PSP -> R.string.romConsolePsp
    RomConsole.GAMECUBE -> R.string.romConsoleGameCube
    RomConsole.WII -> R.string.romConsoleWii
    RomConsole.NES -> R.string.romConsoleNes
    RomConsole.FDS -> R.string.romConsoleFds
    RomConsole.SNES -> R.string.romConsoleSnes
    RomConsole.SATELLAVIEW -> R.string.romConsoleSatellaview
    RomConsole.N64 -> R.string.romConsoleN64
    RomConsole.VIRTUAL_BOY -> R.string.romConsoleVirtualBoy
    RomConsole.GB -> R.string.romConsoleGb
    RomConsole.GBC -> R.string.romConsoleGbc
    RomConsole.GBA -> R.string.romConsoleGba
    RomConsole.NDS -> R.string.romConsoleNds
    RomConsole.POKEMON_MINI -> R.string.romConsolePokemonMini
    RomConsole.SG1000 -> R.string.romConsoleSg1000
    RomConsole.MASTER_SYSTEM -> R.string.romConsoleMasterSystem
    RomConsole.GENESIS -> R.string.romConsoleGenesis
    RomConsole.SEGA_32X -> R.string.romConsoleSega32x
    RomConsole.GAME_GEAR -> R.string.romConsoleGameGear
    RomConsole.PC_ENGINE -> R.string.romConsolePcEngine
    RomConsole.SUPERGRAFX -> R.string.romConsoleSuperGrafx
    RomConsole.NEO_GEO_POCKET -> R.string.romConsoleNeoGeoPocket
    RomConsole.NEO_GEO_POCKET_COLOR -> R.string.romConsoleNeoGeoPocketColor
    RomConsole.WONDERSWAN -> R.string.romConsoleWonderSwan
    RomConsole.WONDERSWAN_COLOR -> R.string.romConsoleWonderSwanColor
    RomConsole.ATARI_2600 -> R.string.romConsoleAtari2600
    RomConsole.ATARI_5200 -> R.string.romConsoleAtari5200
    RomConsole.ATARI_7800 -> R.string.romConsoleAtari7800
    RomConsole.LYNX -> R.string.romConsoleLynx
    RomConsole.JAGUAR -> R.string.romConsoleJaguar
    RomConsole.COLECOVISION -> R.string.romConsoleColecoVision
    RomConsole.INTELLIVISION -> R.string.romConsoleIntellivision
    RomConsole.VECTREX -> R.string.romConsoleVectrex
    RomConsole.ODYSSEY2 -> R.string.romConsoleOdyssey2
    RomConsole.CHANNEL_F -> R.string.romConsoleChannelF
    RomConsole.SUPERVISION -> R.string.romConsoleSupervision
    RomConsole.MEGA_DUCK -> R.string.romConsoleMegaDuck
    RomConsole.N3DS -> R.string.romConsole3ds
    RomConsole.WII_U -> R.string.romConsoleWiiU
}

fun makerName(maker: Maker): Int = when (maker) {
    Maker.NINTENDO -> R.string.romMakerNintendo
    Maker.SONY -> R.string.romMakerSony
    Maker.SEGA -> R.string.romMakerSega
    Maker.NEC -> R.string.romMakerNEC
    Maker.SNK -> R.string.romMakerSNK
    Maker.BANDAI -> R.string.romMakerBandai
    Maker.ATARI -> R.string.romMakerAtari
    Maker.OTHER -> R.string.romMakerOthers
}

/** A format's short name, for "→ CHD". */
private fun shortName(format: RomFormat): Int = when (format) {
    RomFormat.CSO -> R.string.romFormatCso
    RomFormat.ZSO -> R.string.romFormatZso
    RomFormat.ISO -> R.string.romShortIso
    RomFormat.ROM_FILE -> R.string.romShortRom
    RomFormat.FOLDERS -> R.string.romShortFolders
    RomFormat.CHD_CD, RomFormat.CHD_DVD_ZSTD, RomFormat.CHD_DVD, RomFormat.CHD_DVD_ZLIB -> R.string.romShortChd
    else -> formatName(format)
}

fun formatName(format: RomFormat): Int = when (format) {
    RomFormat.CHD_CD -> R.string.romFormatChd
    RomFormat.CHD_DVD_ZSTD -> R.string.romFormatChdLight
    RomFormat.CHD_DVD -> R.string.romFormatChdSmallest
    RomFormat.CHD_DVD_ZLIB -> R.string.romFormatChdZlib
    RomFormat.CSO -> R.string.romFormatCso
    RomFormat.ZSO -> R.string.romFormatZso
    RomFormat.RVZ -> R.string.romFormatRvz
    RomFormat.WBFS -> R.string.romFormatWbfs
    RomFormat.WIA -> R.string.romFormatWia
    RomFormat.CISO -> R.string.romFormatCiso
    RomFormat.GCZ -> R.string.romFormatGcz
    RomFormat.ISO -> R.string.romFormatIso
    RomFormat.ZIP -> R.string.romFormatZip
    RomFormat.SEVEN_Z -> R.string.romFormat7z
    RomFormat.Z3DS -> R.string.romFormatZ3ds
    RomFormat.ROM_FILE -> R.string.romFormatRomFile
    RomFormat.WUA -> R.string.romFormatWua
    RomFormat.FOLDERS -> R.string.romFormatFolders
}

/** A format's name among others of the same console: the CD one says it is for CD games. */
private fun listName(format: RomFormat): Int = if (format == RomFormat.CHD_CD) R.string.romFormatChdCdGames else formatName(format)

/** What a format is good for on [console], one line. */
fun formatInfo(format: RomFormat, console: RomConsole): Int = when {
    console == RomConsole.PSP && format == RomFormat.CSO -> R.string.romFormatCsoPspInfo
    console == RomConsole.PSP && format == RomFormat.CHD_DVD_ZSTD -> R.string.romFormatChdLightPspInfo
    console == RomConsole.PSP && format == RomFormat.CHD_DVD -> R.string.romFormatChdSmallestPspInfo
    else -> formatInfo(format)
}

/** What a format is good for, one line. */
fun formatInfo(format: RomFormat): Int = when (format) {
    RomFormat.CHD_CD -> R.string.romFormatChdInfo
    RomFormat.CHD_DVD_ZSTD -> R.string.romFormatChdLightInfo
    RomFormat.CHD_DVD -> R.string.romFormatChdSmallestInfo
    RomFormat.CHD_DVD_ZLIB -> R.string.romFormatChdZlibInfo
    RomFormat.CSO -> R.string.romFormatCsoInfo
    RomFormat.ZSO -> R.string.romFormatZsoInfo
    RomFormat.RVZ -> R.string.romFormatRvzInfo
    RomFormat.WBFS, RomFormat.WIA, RomFormat.CISO, RomFormat.GCZ -> R.string.romFormatReadOnlyInfo
    RomFormat.ISO -> R.string.romFormatIsoInfo
    RomFormat.ZIP -> R.string.romFormatZipInfo
    RomFormat.SEVEN_Z -> R.string.romFormat7zInfo
    RomFormat.Z3DS -> R.string.romFormatZ3dsInfo
    RomFormat.ROM_FILE -> R.string.romFormatRomFileInfo
    RomFormat.WUA -> R.string.romFormatWuaInfo
    RomFormat.FOLDERS -> R.string.romFormatFoldersInfo
}

private fun speedLabel(speed: RomSpeed): Int = when (speed) {
    RomSpeed.GENTLE -> R.string.romSpeedGentle
    RomSpeed.NORMAL -> R.string.romSpeedNormal
    RomSpeed.FAST -> R.string.romSpeedFast
}

private fun speedInfo(speed: RomSpeed): Int = when (speed) {
    RomSpeed.GENTLE -> R.string.romSpeedGentleInfo
    RomSpeed.NORMAL -> R.string.romSpeedNormalInfo
    RomSpeed.FAST -> R.string.romSpeedFastInfo
}

private fun filterLabel(filter: GameFilter): Int = when (filter) {
    GameFilter.NOT_COMPRESSED -> R.string.romFilterOpen
    GameFilter.ORIGINALS -> R.string.romFilterOriginals
    GameFilter.COMPRESSED -> R.string.romFilterCompressed
    GameFilter.ATTENTION -> R.string.romFilterAttention
    GameFilter.ALL -> R.string.romFilterAll
}

private fun sortLabel(sort: GameSort): Int = when (sort) {
    GameSort.SAVING -> R.string.romSortSaving
    GameSort.NAME -> R.string.romSortName
    GameSort.SIZE -> R.string.romSortSize
}

private fun sortInfo(sort: GameSort): Int = when (sort) {
    GameSort.SAVING -> R.string.romSortSavingInfo
    GameSort.NAME -> R.string.romSortNameInfo
    GameSort.SIZE -> R.string.romSortSizeInfo
}

private const val SHOWN = 200
private const val SEARCH_FROM = 12
private const val CHDMAN_URL = "https://github.com/mamedev/mame"
private const val MAXCSO_URL = "https://github.com/unknownbrackets/maxcso"
private const val NOD_URL = "https://github.com/encounter/nod-rs"
private const val SEVEN_ZIP_URL = "https://www.7-zip.org"
private const val ZSTD_URL = "https://github.com/facebook/zstd"
private const val ZARCHIVE_URL = "https://github.com/Exzap/ZArchive"
