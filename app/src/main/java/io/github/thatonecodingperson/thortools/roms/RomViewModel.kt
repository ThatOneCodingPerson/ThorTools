package io.github.thatonecodingperson.thortools.roms

import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.retroarch.BiosPaths
import io.github.thatonecodingperson.thortools.tools.RootFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class GameFilter { NOT_COMPRESSED, ORIGINALS, COMPRESSED, ATTENTION, ALL }

enum class GameSort { SAVING, NAME, SIZE }

/** Which games a console page shows, and in what order (the order they are compressed in too). */
object GameList {
    /**
     * Where each game that shares its title with another one in [games] is, by id: the part of its folder that tells
     * them apart (the storage named [internal] or [card], a subfolder), or its file name when they share the folder.
     */
    fun places(games: List<DiscGame>, internal: String, card: String): Map<String, String> {
        val places = mutableMapOf<String, String>()
        games.groupBy { it.title.lowercase() }.values.filter { it.size > 1 }.forEach { group ->
            val paths = group.map { game -> named(game.folder, internal, card).split('/').filter { it.isNotEmpty() } }
            val common = paths.reduce { a, b -> a.zip(b).takeWhile { (x, y) -> x.equals(y, true) }.map { it.first } }.size
            group.zip(paths).forEach { (game, parts) ->
                val rest = parts.drop(common)
                places[game.id] = when {
                    group.count { it.folder.equals(game.folder, true) } > 1 ->
                        game.main?.substringAfterLast('/') ?: game.compressed?.name ?: game.title
                    rest.isEmpty() -> parts.lastOrNull() ?: game.folder
                    else -> rest.joinToString("/")
                }
            }
        }
        return places
    }

    private val CARD = Regex("""^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}(?=/|$)""")

    private fun named(folder: String, internal: String, card: String): String {
        val shared = listOf("/storage/emulated/0", "/sdcard").firstOrNull { folder == it || folder.startsWith("$it/") }
        if (shared != null) return internal + folder.removePrefix(shared)
        val volume = CARD.find(folder) ?: return folder
        return card + folder.substring(volume.range.last + 1)
    }

    fun matches(game: DiscGame, filter: GameFilter): Boolean = when (filter) {
        GameFilter.NOT_COMPRESSED -> game.notCompressed
        GameFilter.ORIGINALS -> game.originalsLeft
        GameFilter.COMPRESSED -> game.compressed != null
        GameFilter.ATTENTION -> game.problem != null
        GameFilter.ALL -> true
    }

    /**
     * The list a console page opens on: games to compress, else everything when some can change format, else originals
     * to clean up, else everything.
     */
    fun firstFilter(games: List<DiscGame>): GameFilter = when {
        games.any { it.notCompressed } -> GameFilter.NOT_COMPRESSED
        games.any { it.canConvert } -> GameFilter.ALL
        games.any { it.originalsLeft } -> GameFilter.ORIGINALS
        else -> GameFilter.ALL
    }

    /**
     * What sorting by saving goes by: the estimated saving of a game to compress or convert ([saving], below 0 when it
     * can't be), the originals' size of one to clean up.
     */
    private fun saved(game: DiscGame, saving: (DiscGame) -> Long): Long = when {
        game.canCompress || game.canConvert -> saving(game)
        game.originalsLeft -> game.originalSize
        else -> -1L
    }

    /** [games] matching [filter] and every word of [search], sorted; [saving] is a game's estimated saving in bytes. */
    fun shown(games: List<DiscGame>, filter: GameFilter, sort: GameSort, search: String, saving: (DiscGame) -> Long): List<DiscGame> {
        val words = search.lowercase().split(' ').filter { it.isNotBlank() }
        val list = games.filter { matches(it, filter) && words.all { word -> word in it.title.lowercase() } }
        return when (sort) {
            GameSort.NAME -> list.sortedBy { it.title.lowercase() }
            GameSort.SIZE -> list.sortedByDescending { it.originalSize + (it.compressed?.size ?: 0) }
            GameSort.SAVING -> list.sortedByDescending { saved(it, saving) }
        }
    }
}

/** A question before jobs start. */
sealed class RomAsk {
    /** None of the installed emulators is known to open [format]; [checks] says which can't and why. */
    data class NoEmulator(val format: RomFormat, val checks: List<EmulatorCheck>, val jobs: List<RomJob>) : RomAsk()

    /** The originals of [jobs] will be deleted. */
    data class Delete(val jobs: List<RomJob>) : RomAsk()

    /** The new files of [jobs] won't all fit on their storage ([plan]); [withDelete] is the plan when originals go. */
    data class Space(val jobs: List<RomJob>, val plan: SpacePlan, val withDelete: SpacePlan) : RomAsk()
}

sealed class RomNote {
    data object FolderUnusable : RomNote()
    data object PlaylistMade : RomNote()
    data object PlaylistFailed : RomNote()
}

data class RomUiModel(
    val shelf: ShelfState = ShelfState(),
    val runner: RunnerState = RunnerState(),
    val console: RomConsole? = null,
    val gameId: String? = null,
    val filter: GameFilter = GameFilter.NOT_COMPRESSED,
    /** The filter was chosen by the user (or set once the games were known), so it no longer follows them. */
    val filterSet: Boolean = false,
    val sort: GameSort = GameSort.SAVING,
    val search: String = "",
    val ticked: Set<String> = emptySet(),
    val originals: Originals = Originals.KEEP,
    /** What cleaning up does with the originals of compressed games: delete or move them, never keep. */
    val cleanup: Originals = Originals.DELETE,
    val speed: RomSpeed = RomSpeed.NORMAL,
    val ask: RomAsk? = null,
    val note: RomNote? = null,
    val engineReady: Boolean = true,
    /** The format each console's list compresses into, as chosen (by console id); else [defaultFormat]. */
    val listFormats: Map<String, RomFormat> = emptyMap(),
    /** The format picked on the game page; null: the list's. */
    val gameFormat: RomFormat? = null,
) {
    val games: List<DiscGame> get() = console?.let { shelf.games[it] }.orEmpty()

    /** The format a disc of [kind] gets by default: the console's first one an installed emulator opens. */
    fun defaultFormat(console: RomConsole, kind: DiscKind): RomFormat =
        EmulatorChecks.defaultFormat(console, kind, shelf.installed, shelf.cores) ?: RomFormat.CHD_CD

    /** The format the list turns [game] into when it is compressed already: the list's, if that is another one. */
    fun convertTarget(game: DiscGame): RomFormat? = listFormat(game.console).takeIf { game.canConvert && it in game.targets }

    /** True when [game] can go into the list's format now: compressed, or converted. */
    fun canMake(game: DiscGame): Boolean = (game.canCompress && formatFor(game) in game.targets) || convertTarget(game) != null

    /** The format [console]'s list compresses DVD images into (CD games always get the CD format). */
    fun listFormat(console: RomConsole): RomFormat =
        listFormats[console.id]?.takeIf { it in console.formatsFor(console.listKind) } ?: defaultFormat(console, console.listKind)

    /** The format [game] gets from the list: the list's for a DVD image, the CD default for a CD. */
    fun formatFor(game: DiscGame): RomFormat = when (game.kind) {
        DiscKind.DVD, DiscKind.CART, DiscKind.FOLDER -> listFormat(game.console)
        else -> defaultFormat(game.console, DiscKind.CD)
    }

    /**
     * A game's estimate in [format]: measured when it has been (a GameCube or Wii game, compressed or not), else for a
     * compressed game from its file and the format it is in, else the console's usual saving.
     */
    fun estimate(game: DiscGame, format: RomFormat = convertTarget(game) ?: formatFor(game)): Estimate = when {
        game.kind == DiscKind.CART && game.main == null && game.compressed != null ->
            ShrinkEstimate.cartridgeConverted(game.sizeNow, game.imageSize, game.current, format, game.console)
        game.kind == DiscKind.CART ->
            shelf.estimates[RomShelf.key(game, format)] ?: ShrinkEstimate.cartridge(game.originalSize, null, game.console, format)
        game.kind == DiscKind.FOLDER && game.main == null && game.compressed != null -> {
            val unpacked = game.imageSize ?: (game.sizeNow * 2)
            if (format ==
                RomFormat.FOLDERS
            ) {
                Estimate(unpacked, unpacked, game.imageSize != null)
            } else {
                Estimate(game.sizeNow, game.sizeNow, false)
            }
        }
        game.kind == DiscKind.FOLDER ->
            shelf.estimates[RomShelf.key(game, format)] ?: ShrinkEstimate.folders(game.originalSize, null)
        game.main == null && game.compressed != null ->
            shelf.estimates[RomShelf.key(game, format)] ?: ShrinkEstimate.converted(game.sizeNow, game.imageSize, game.current, format)
        else -> shelf.estimates[RomShelf.key(game, format)] ?: ShrinkEstimate.typical(game.originalSize, game.console, format)
    }

    /** The console page's games as shown: filtered, searched and sorted. */
    val shown: List<DiscGame>
        get() = GameList.shown(games, filter, sort, search) { if (canMake(it)) it.sizeNow - estimate(it).middle else -1L }

    /**
     * The shown games that can be ticked: to compress, to convert into the list's format, or compressed with their
     * originals to clean up; none waiting.
     */
    val tickable: List<DiscGame> get() = shown.filter { (canMake(it) || it.originalsLeft) && !busy(it) }

    /**
     * What Select all ticks: the shown games to compress when there are any, else those to convert, else those with
     * originals to clean up.
     */
    val selectable: List<DiscGame>
        get() = tickable.filter { it.notCompressed && canMake(it) }
            .ifEmpty { tickable.filter { convertTarget(it) != null } }
            .ifEmpty { tickable }

    /** The ticked games to compress or convert, and the ticked ones whose originals are to be cleaned up. */
    val toCompress: List<DiscGame> get() = games.filter { it.id in ticked && canMake(it) && !busy(it) }
    val toClean: List<DiscGame> get() = games.filter { it.id in ticked && it.originalsLeft && !busy(it) }

    /**
     * The games card offers cleaning up instead of compressing: when only games with originals are ticked, or nothing
     * is ticked while the list is about originals. Both show when both kinds are ticked.
     */
    val cleanFirst: Boolean
        get() = toCompress.isEmpty() &&
            (toClean.isNotEmpty() || filter == GameFilter.ORIGINALS || selectable.firstOrNull()?.let { !canMake(it) } == true)

    /** Where [game] stands in the queue: 0 being compressed now, 1 next, and so on; null when it isn't waiting. */
    fun place(game: DiscGame): Int? = listOfNotNull(game.main, game.compressed?.path).firstNotNullOfOrNull { runner.place(it) }
    val game: DiscGame? get() = gameId?.let { key -> shelf.games.values.flatten().firstOrNull { it.key.equals(key, ignoreCase = true) } }

    /** True while [game] is waiting or being worked on. */
    fun busy(game: DiscGame): Boolean {
        val inputs = setOfNotNull(game.main, game.compressed?.path)
        return runner.current?.job?.input in inputs || runner.queue.any { it.input in inputs }
    }
}

@HiltViewModel
class RomViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val prefs: SharedPrefsRepo,
    private val shelf: RomShelf,
    private val runner: RomJobRunner,
    private val rootFiles: RootFiles,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RomUiModel(
            console = RomConsole.byId(savedState.get<String>(ARG_CONSOLE)),
            gameId = savedState.get<String>(ARG_GAME),
            speed = prefs.romSpeed?.let { runCatching { RomSpeed.valueOf(it) }.getOrNull() } ?: RomSpeed.NORMAL,
            listFormats = prefs.romFormats.mapNotNull { (id, name) ->
                runCatching { RomFormat.valueOf(name) }.getOrNull()?.let { id to it }
            }.toMap(),
        ),
    )
    val uiState: StateFlow<RomUiModel> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(shelf.state, runner.state) { s, r -> s to r }.collect { (s, r) ->
                _uiState.update { state ->
                    val next = state.copy(shelf = s, runner = r)
                    when {
                        next.filterSet || next.games.isEmpty() -> next
                        else -> next.copy(filter = GameList.firstFilter(next.games), filterSet = true)
                    }
                }
            }
        }
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { runner.engine != null }
            _uiState.update { it.copy(engineReady = ready) }
        }
    }

    fun refresh() {
        shelf.load()
        val console = _uiState.value.console ?: _uiState.value.game?.console
        if (console != null) shelf.measure(console)
    }

    fun rescan() = shelf.rescan()

    fun onFolderPicked(tree: Uri?) {
        tree ?: return
        val path = runCatching { BiosPaths.fromTree(tree.authority, DocumentsContract.getTreeDocumentId(tree)) }.getOrNull()
        if (path == null) {
            _uiState.update { it.copy(note = RomNote.FolderUnusable) }
            return
        }
        shelf.addFolder(path)
    }

    fun addFolder(path: String) = shelf.addFolder(path)

    fun removeFolder(path: String) = shelf.removeFolder(path)

    fun addRetroArchFolders() = shelf.addRetroArchFolders()

    fun dismissSuggestions() = shelf.dismissSuggestions()

    val retroArchFolders: List<String> get() = prefs.retroArchRomFolders

    fun setFilter(filter: GameFilter) = _uiState.update { it.copy(filter = filter, filterSet = true) }

    fun setSort(sort: GameSort) = _uiState.update { it.copy(sort = sort) }

    fun setSearch(text: String) = _uiState.update { it.copy(search = text) }

    fun tick(game: DiscGame, on: Boolean) = _uiState.update { it.copy(ticked = if (on) it.ticked + game.id else it.ticked - game.id) }

    /** Ticks every shown game Select all stands for ([RomUiModel.selectable]), or unticks them all when they already are. */
    fun selectAll() = _uiState.update { state ->
        val ids = state.selectable.map { it.id }.toSet()
        state.copy(ticked = if (ids.isNotEmpty() && state.ticked.containsAll(ids)) state.ticked - ids else state.ticked + ids)
    }

    fun setOriginals(choice: Originals) {
        _uiState.update { it.copy(originals = choice) }
    }

    /** What [console]'s list compresses DVD images into; remembered. */
    fun setListFormat(console: RomConsole, format: RomFormat) {
        prefs.romFormats = prefs.romFormats + (console.id to format.name)
        _uiState.update { it.copy(listFormats = it.listFormats + (console.id to format)) }
    }

    fun setGameFormat(format: RomFormat) = _uiState.update { it.copy(gameFormat = format) }

    fun setCleanup(choice: Originals) {
        if (choice != Originals.KEEP) _uiState.update { it.copy(cleanup = choice) }
    }

    fun setSpeed(speed: RomSpeed) {
        prefs.romSpeed = speed.name
        _uiState.update { it.copy(speed = speed) }
    }

    fun dismissNote() = _uiState.update { it.copy(note = null) }

    fun dismissAsk() = _uiState.update { it.copy(ask = null) }

    /** The installed emulators of [console] and what each can do with [format]. */
    fun checks(console: RomConsole, format: RomFormat = console.allFormats.first()): List<EmulatorCheck> =
        EmulatorChecks.of(console, format, _uiState.value.shelf.installed, _uiState.value.shelf.cores)

    fun estimate(game: DiscGame, format: RomFormat? = null): Estimate =
        _uiState.value.let { state -> if (format == null) state.estimate(game) else state.estimate(game, format) }

    /** The installed front ends that don't list [format]'s files for [console]. */
    fun frontEndsMissing(console: RomConsole, format: RomFormat): List<String> =
        EmulatorChecks.frontEndsMissing(console, format, _uiState.value.shelf.installed)

    /** Compresses the ticked games of the console page, in the order the page shows them. */
    fun compressTicked() {
        val state = _uiState.value
        val games = inShownOrder(state, state.toCompress)
        startCompressing(games)
        untick(games)
    }

    /**
     * Cleans up the originals of the ticked compressed games, in the order the page shows them: each game's CHD is
     * checked again first, then its originals are deleted (after a question) or moved.
     */
    fun cleanTicked() {
        val state = _uiState.value
        val games = inShownOrder(state, state.toClean)
        val jobs = games.mapNotNull { RomJobs.originals(nextId(), it, state.cleanup, state.speed, romFolderOf(it.folder)) }
        if (jobs.isEmpty()) return
        if (state.cleanup == Originals.DELETE) _uiState.update { it.copy(ask = RomAsk.Delete(jobs)) } else runner.enqueue(jobs)
        untick(games)
    }

    private fun inShownOrder(state: RomUiModel, games: List<DiscGame>): List<DiscGame> {
        val shown = state.shown.filter { it in games }
        return shown + games.filter { it !in shown }
    }

    private fun untick(games: List<DiscGame>) {
        val ids = games.map { it.id }.toSet()
        _uiState.update { it.copy(ticked = it.ticked - ids) }
    }

    /** Compresses [game] alone, into the format picked on its page (else the list's). */
    fun compress(game: DiscGame) {
        val state = _uiState.value
        val format = state.gameFormat?.takeIf { it in game.targets }
            ?: (state.convertTarget(game) ?: state.formatFor(game)).takeIf { it in game.targets }
            ?: game.targets.firstOrNull()
            ?: return
        startCompressing(listOf(game), format)
    }

    /** Compresses [games]: each into [single] when given, else the format its kind gets from the list. */
    private fun startCompressing(games: List<DiscGame>, single: RomFormat? = null) {
        if (games.isEmpty()) return
        val state = _uiState.value
        val jobs = games.mapNotNull { game ->
            val format = single ?: state.convertTarget(game) ?: state.formatFor(game)
            if (game.canConvert) {
                RomJobs.convert(
                    nextId(),
                    game,
                    format,
                    state.originals,
                    state.speed,
                    state.estimate(game, format),
                    romFolderOf(game.folder),
                )
            } else {
                RomJobs.compress(
                    id = nextId(),
                    game = game,
                    choice = state.originals,
                    speed = state.speed,
                    estimate = state.estimate(game, format),
                    romFolder = romFolderOf(game.folder),
                    sheetText = game.main?.let { shelf.text(it) },
                    format = format,
                )
            }
        }
        if (jobs.isEmpty()) return
        val console = games.first().console
        val unread = jobs.map { it.format }.distinct().firstOrNull { !EmulatorChecks.anyOpens(checks(console, it)) }
        if (unread != null) {
            _uiState.update { it.copy(ask = RomAsk.NoEmulator(unread, checks(console, unread), jobs)) }
        } else {
            checkSpace(jobs)
        }
    }

    /** Asks before a run whose new files won't fit, then about deleting, then starts it. */
    private fun checkSpace(jobs: List<RomJob>) {
        viewModelScope.launch {
            val waiting = listOfNotNull(_uiState.value.runner.current?.job) + _uiState.value.runner.queue
            val free = withContext(Dispatchers.IO) { shelf.freeBytes(RomSpace.volumes(jobs)) }
            val plan = RomSpace.plan(jobs, waiting, free)
            if (!plan.fitsAll) {
                val deleting = jobs.map { it.copy(choice = Originals.DELETE) }
                _uiState.update { it.copy(ask = RomAsk.Space(jobs, plan, RomSpace.plan(deleting, waiting, free))) }
            } else {
                askDelete(jobs)
            }
        }
    }

    private fun askDelete(jobs: List<RomJob>) {
        if (jobs.isEmpty()) {
            _uiState.update { it.copy(ask = null) }
        } else if (jobs.any { it.choice == Originals.DELETE }) {
            _uiState.update { it.copy(ask = RomAsk.Delete(jobs)) }
        } else {
            _uiState.update { it.copy(ask = null) }
            runner.enqueue(jobs)
        }
    }

    /** Goes on after a question was answered yes. */
    fun confirm() {
        when (val ask = _uiState.value.ask) {
            is RomAsk.NoEmulator -> {
                _uiState.update { it.copy(ask = null) }
                checkSpace(ask.jobs)
            }
            is RomAsk.Delete -> {
                _uiState.update { it.copy(ask = null) }
                runner.enqueue(ask.jobs)
            }
            is RomAsk.Space, null -> Unit
        }
    }

    /** The space question's answers: the originals of these games go once each new file passed its check… */
    fun spaceDeleting() {
        val ask = _uiState.value.ask as? RomAsk.Space ?: return
        val jobs = ask.jobs.map { it.copy(choice = Originals.DELETE) }.take(ask.withDelete.fit)
        askDelete(jobs)
    }

    /** …or only the games that fit are compressed. */
    fun spaceFewer() {
        val ask = _uiState.value.ask as? RomAsk.Space ?: return
        askDelete(ask.jobs.take(ask.plan.fit))
    }

    /** Turns [game]'s compressed file back into its image or sheet and track; [choice] applies to the compressed file. */
    fun extract(game: DiscGame) {
        val job = RomJobs.extract(nextId(), game, Originals.KEEP, _uiState.value.speed, romFolderOf(game.folder)) ?: return
        runner.enqueue(listOf(job))
    }

    /** Checks [game]'s compressed file again, then applies [choice] to the files it was made from. */
    fun removeOriginals(game: DiscGame, choice: Originals) {
        val job = RomJobs.originals(nextId(), game, choice, _uiState.value.speed, romFolderOf(game.folder)) ?: return
        if (choice == Originals.DELETE) _uiState.update { it.copy(ask = RomAsk.Delete(listOf(job))) } else runner.enqueue(listOf(job))
    }

    fun cancel(id: Long) = runner.cancel(id)

    fun pause() = runner.pause()

    fun resumeRun() = runner.resumeRun()

    fun skip() = runner.skip()

    fun stopAll() = runner.stopAll()

    fun clearEnded() = runner.clearEnded()

    fun clearHistory() = shelf.clearHistory()

    /** Writes an `.m3u` listing [discs] (in disc order) beside them. */
    fun makePlaylist(discs: List<DiscGame>) {
        val first = discs.minByOrNull { it.disc ?: 0 } ?: return
        val names = discs.sortedBy { it.disc ?: 0 }.map { disc ->
            (disc.compressed?.name ?: disc.main?.substringAfterLast('/')).orEmpty()
        }.filter { it.isNotEmpty() }
        val target = "${first.folder}/${RomSets.playlistTitle(first.title)}.m3u"
        viewModelScope.launch {
            val written = withContext(Dispatchers.IO) {
                rootFiles.exists(target) == false &&
                    rootFiles.write(target, RomSets.playlist(names).toByteArray(), ownerOf = first.folder).isSuccess
            }
            _uiState.update { it.copy(note = if (written) RomNote.PlaylistMade else RomNote.PlaylistFailed) }
            if (written) shelf.rescan()
        }
    }

    private fun romFolderOf(folder: String): String? = _uiState.value.shelf.folders.firstOrNull { BiosPaths.isUnder(folder, it) }

    private fun nextId(): Long = System.nanoTime()

    companion object {
        const val ARG_CONSOLE = "console"
        const val ARG_GAME = "game"
    }
}
