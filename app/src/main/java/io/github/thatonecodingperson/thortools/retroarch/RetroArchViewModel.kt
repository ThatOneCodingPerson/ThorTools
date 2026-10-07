package io.github.thatonecodingperson.thortools.retroarch

import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.hotkeys.Hotkey
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyList
import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

enum class RaTab(@StringRes val title: Int) {
    OVERVIEW(R.string.raTabOverview),
    LOOK(R.string.raTabLook),
    HOTKEYS(R.string.raTabHotkeys),
    BIOS(R.string.raBiosTab),
    CHEATS(R.string.raCheatTab),
}

/** RetroArch's menu drivers ([value] in `menu_driver`). */
enum class RaMenu(val value: String, @StringRes val label: Int, @StringRes val info: Int) {
    OZONE("ozone", R.string.raMenuOzone, R.string.raMenuOzoneInfo),
    XMB("xmb", R.string.raMenuXmb, R.string.raMenuXmbInfo),
    GLUI("glui", R.string.raMenuGlui, R.string.raMenuGluiInfo),
    RGUI("rgui", R.string.raMenuRgui, R.string.raMenuRguiInfo),
    ;

    companion object {
        fun of(value: String?): RaMenu? = entries.find { it.value == value }
    }
}

/** What the hotkey editor works on: the hotkey enable button, or one hotkey's button. */
sealed interface RaKey {
    data object Enable : RaKey

    data class Of(val hotkey: RaHotkey) : RaKey
}

/** The note after something was done. */
sealed class RaNote {
    data object Saved : RaNote()

    /** RetroArch was open, so nothing was written: it would write its own settings over the file on quitting. */
    data object Running : RaNote()

    data class Failed(val detail: String) : RaNote()

    data object Undone : RaNote()

    /** RetroArch has changed its settings since Thor Tools' last change, so there is nothing to undo any more. */
    data object UndoStale : RaNote()

    data object Closed : RaNote()

    data object CloseFailed : RaNote()
}

/** The note after the BIOS finder did something. */
sealed class BiosNote {
    /** The BIOS files copied and checked, and each one that wasn't, with why. */
    data class Copied(val copied: List<String>, val failed: List<Pair<String, String>>) : BiosNote()

    /** Root couldn't look through the folders. */
    data object ScanFailed : BiosNote()

    /** The folder picked is another app's, with no path root could read. */
    data object Unusable : BiosNote()
}

/** The BIOS tab: the user's folders, RetroArch's BIOS folder, what was found, and what is ticked. */
data class RaBios(
    val folders: List<String> = emptyList(),
    val target: BiosTarget? = null,
    val scan: BiosScanResult? = null,
    /** The user's ticks by entry key: a found file's path, or null for none. */
    val choices: Map<String, String?> = emptyMap(),
    val scanning: Boolean = false,
    val copying: Boolean = false,
    val note: BiosNote? = null,
) {
    val ticks: Map<String, String> get() = scan?.let { BiosPlan.ticks(it.entries, choices) }.orEmpty()

    /** The copies the ticks ask for; none while there is nowhere to copy to. */
    val copies: List<BiosCopy>
        get() {
            val root = target?.copyRoot ?: return emptyList()
            return BiosPlan.copies(scan?.entries.orEmpty(), ticks, root, target.makeRoot)
        }
}

data class RaUiModel(
    val tab: RaTab = RaTab.OVERVIEW,
    /** Null while RetroArch is being looked for. */
    val retroArch: RetroArchState? = null,
    val config: RetroArchConfig? = null,
    val running: Boolean = false,
    val xbox: Boolean = true,
    val writing: Boolean = false,
    val note: RaNote? = null,
    val selected: RaKey = RaKey.Enable,
    /** Thor Tools' hotkeys that count in RetroArch. */
    val thorHotkeys: List<Hotkey> = emptyList(),
    /** RetroArch is one of the apps without Thor Tools hotkeys (only the AYN button's work there). */
    val thorHotkeysOff: Boolean = false,
    /** The hotkeys were set while the controller had the other style. */
    val otherStyle: Boolean = false,
    /** RetroArch's BIOS folder as its settings name it, and whether it is there (null when unknown). */
    val systemDir: String? = null,
    val systemDirThere: Boolean? = null,
    /** The settings as Thor Tools' last change left them, while undoing it is possible. */
    val undo: RetroArchConfig? = null,
    val bios: RaBios = RaBios(),
    val cheats: RaCheats = RaCheats(),
) {
    val found: RetroArchState.Found? get() = retroArch as? RetroArchState.Found

    /** Changes can be written: the file is there and RetroArch is closed. */
    val canChange: Boolean get() = config != null && found?.configPath != null && !running && !writing

    fun binding(key: RaKey): RaBinding {
        val config = config ?: return RaBinding.None
        return RetroArchBinds.read(config, keyName(key), xbox)
    }

    val enable: PadButton? get() = (binding(RaKey.Enable) as? RaBinding.Thor)?.button

    val hotkeyButtons: Map<RaHotkey, PadButton>
        get() = RaHotkey.entries.mapNotNull { hotkey ->
            (binding(RaKey.Of(hotkey)) as? RaBinding.Thor)?.let { hotkey to it.button }
        }.toMap()

    val clashes: List<Pair<RaHotkey, Hotkey>>
        get() = if (thorHotkeysOff) emptyList() else RetroArchBinds.clashes(enable, hotkeyButtons, thorHotkeys)

    val anyHotkey: Boolean get() = RaHotkey.entries.any { binding(RaKey.Of(it)) != RaBinding.None }

    val menu: RaMenu? get() = RaMenu.of(config?.value(MENU_DRIVER))

    val fastForward: Float? get() = config?.value(FAST_FORWARD)?.toFloatOrNull()

    val slowMotion: Float? get() = config?.value(SLOW_MOTION)?.toFloatOrNull()

    companion object {
        const val MENU_DRIVER = "menu_driver"
        const val FAST_FORWARD = "fastforward_ratio"
        const val SLOW_MOTION = "slowmotion_ratio"
        const val SAVE_ON_EXIT = "config_save_on_exit"
        const val QUIT_TWICE = "quit_press_twice"
        const val SYSTEM_DIR = "system_directory"
        const val APPLY_CHEATS = "apply_cheats_after_load"

        /** Fast-forward speeds offered; 0 is RetroArch's "as fast as it goes". */
        val FAST_FORWARD_CHOICES = listOf(2f, 3f, 4f, 6f, 0f)
        val SLOW_MOTION_CHOICES = listOf(2f, 3f, 4f)

        fun keyName(key: RaKey) = when (key) {
            RaKey.Enable -> RetroArchBinds.ENABLE
            is RaKey.Of -> key.hotkey.key
        }

        fun same(a: Float?, b: Float) = a != null && abs(a - b) < 0.01f

        /** A ratio the way RetroArch writes it. */
        fun ratio(value: Float): String = String.format(Locale.US, "%.6f", value)
    }
}

@HiltViewModel
class RetroArchViewModel @Inject constructor(
    private val prefs: SharedPrefsRepo,
    private val executor: ShellExecutor,
    private val files: RetroArchFiles,
    private val biosFiles: BiosFiles,
    private val cheatFiles: CheatFiles,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RaUiModel())
    val uiState: StateFlow<RaUiModel> = _uiState.asStateFlow()

    /** Writes take turns, each on the file as it is just then. */
    private val writes = Mutex()

    /** BIOS scans and copies take turns. */
    private val biosWork = Mutex()

    /** A scan waits for its turn; asking again until it starts changes nothing, since it reads everything when it starts. */
    private var scanWaiting = false

    /** Cheat scans and writes take turns. */
    private val cheatWork = Mutex()

    /** The choices file is written by one at a time, each time as the choices are just then. */
    private val choiceSaves = Mutex()

    /** Looks for RetroArch and reads its settings again, e.g. after coming back from RetroArch. */
    fun refresh() {
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) {
                val retroArch = files.find()
                val found = retroArch as? RetroArchState.Found
                val config = found?.configPath?.let(files::read)
                val running = found?.let { files.running(it.packageName) } == true
                val style = ControllerStyle.getStyle(executor)
                val systemDir = config?.value(RaUiModel.SYSTEM_DIR)?.takeIf { it.isNotBlank() && it != "default" }
                Read(retroArch, config, running, style, systemDir, systemDir?.let(files::exists))
            }
            val packageName = read.retroArch.let { it as? RetroArchState.Found }?.packageName
            _uiState.update { state ->
                state.copy(
                    retroArch = read.retroArch,
                    config = read.config,
                    running = read.running,
                    xbox = read.style != ControllerStyle.Odin,
                    systemDir = read.systemDir,
                    systemDirThere = read.systemDirThere,
                    thorHotkeys = packageName?.let { HotkeyList.forApp(prefs.hotkeys, it) }.orEmpty(),
                    thorHotkeysOff = packageName != null && packageName in prefs.hotkeyOffApps,
                    undo = state.undo?.takeIf { state.config?.text == read.config?.text },
                ).let { it.copy(otherStyle = otherStyle(it, read.style)) }
            }
            if (_uiState.value.tab == RaTab.BIOS) scanBios()
            if (_uiState.value.tab == RaTab.CHEATS) loadCheats()
        }
    }

    fun selectTab(tab: RaTab) {
        _uiState.update { it.copy(tab = tab, note = null) }
        val bios = _uiState.value.bios
        if (tab == RaTab.BIOS && bios.scan == null && !bios.scanning) scanBios()
        if (tab == RaTab.CHEATS && !_uiState.value.cheats.loaded) loadCheats()
    }

    fun select(key: RaKey) = _uiState.update { it.copy(selected = key) }

    fun setMenu(menu: RaMenu) = change(mapOf(RaUiModel.MENU_DRIVER to menu.value))

    fun setFastForward(value: Float) = change(mapOf(RaUiModel.FAST_FORWARD to RaUiModel.ratio(value)))

    fun setSlowMotion(value: Float) = change(mapOf(RaUiModel.SLOW_MOTION to RaUiModel.ratio(value)))

    fun setSaveOnExit(on: Boolean) = change(mapOf(RaUiModel.SAVE_ON_EXIT to on.toString()))

    fun setQuitTwice(on: Boolean) = change(mapOf(RaUiModel.QUIT_TWICE to on.toString()))

    /** The selected hotkey (or the enable button) on [button]; null clears it. */
    fun pick(button: PadButton?) {
        val state = _uiState.value
        change(RetroArchBinds.changes(RaUiModel.keyName(state.selected), button, state.xbox), hotkeys = true)
    }

    fun useSuggestion() {
        val key = _uiState.value.selected
        pick(if (key is RaKey.Of) key.hotkey.suggested else PadButton.SELECT)
    }

    fun applySuggestion() = change(RetroArchBinds.suggestion(_uiState.value.xbox), hotkeys = true)

    /** Writes the hotkeys again for the controller style in use now, as they were meant in the style they were set in. */
    fun applyForThisStyle() {
        val state = _uiState.value
        val config = state.config ?: return
        val wasXbox = !state.xbox
        val changes = buildMap {
            (listOf(RaKey.Enable) + RaHotkey.entries.map { RaKey.Of(it) }).forEach { key ->
                val name = RaUiModel.keyName(key)
                val meant = RetroArchBinds.read(config, name, wasXbox) as? RaBinding.Thor ?: return@forEach
                putAll(RetroArchBinds.changes(name, meant.button, state.xbox))
            }
        }
        change(changes, hotkeys = true)
    }

    fun setThorHotkeysOff(off: Boolean) {
        val packageName = _uiState.value.found?.packageName ?: return
        val apps = prefs.hotkeyOffApps
        prefs.hotkeyOffApps = if (off) apps + packageName else apps - packageName
        _uiState.update { it.copy(thorHotkeysOff = off) }
    }

    /** Stops RetroArch so its settings can be changed; what was changed inside RetroArch and not saved is lost. */
    fun closeRetroArch() {
        val packageName = _uiState.value.found?.packageName ?: return
        viewModelScope.launch {
            val closed = withContext(Dispatchers.IO) { files.close(packageName) }
            _uiState.update { it.copy(note = if (closed) RaNote.Closed else RaNote.CloseFailed) }
            refresh()
        }
    }

    /** Puts back the settings from before Thor Tools' last change, unless RetroArch has changed the file since. */
    fun undo() {
        val state = _uiState.value
        val before = state.undo ?: return
        val after = state.config ?: return
        write(undoing = true) { current -> if (current.text == after.text) before else null }
    }

    private fun change(changes: Map<String, String>, hotkeys: Boolean = false) = write(hotkeys = hotkeys) { it.with(changes) }

    /**
     * Reads the file as it is now, makes [next] of it and writes that; an unchanged [next] writes nothing, a null one
     * means there is nothing left to undo. [hotkeys]: the change binds buttons, so the controller style is noted.
     */
    private fun write(hotkeys: Boolean = false, undoing: Boolean = false, next: (RetroArchConfig) -> RetroArchConfig?) {
        val found = _uiState.value.found ?: return
        val path = found.configPath ?: return
        val xbox = _uiState.value.xbox
        viewModelScope.launch {
            writes.withLock {
                _uiState.update { it.copy(writing = true, note = null) }
                val outcome = withContext(Dispatchers.IO) {
                    if (files.running(found.packageName) != false) return@withContext Outcome(note = RaNote.Running, running = true)
                    val current = files.read(path) ?: return@withContext Outcome(note = RaNote.Failed("read"))
                    val target = next(current) ?: return@withContext Outcome(current, note = RaNote.UndoStale)
                    if (target.text == current.text) return@withContext Outcome(current)
                    files.write(path, target).fold(
                        onSuccess = { Outcome(target, before = current, note = if (undoing) RaNote.Undone else RaNote.Saved) },
                        onFailure = { Outcome(current, note = RaNote.Failed(it.message ?: "PServer")) },
                    )
                }
                if (hotkeys && outcome.note == RaNote.Saved) {
                    prefs.retroArchHotkeyStyle = if (xbox) ControllerStyle.Xbox.id else ControllerStyle.Odin.id
                }
                _uiState.update { state ->
                    state.copy(
                        writing = false,
                        config = outcome.config ?: state.config,
                        running = outcome.running,
                        note = outcome.note,
                        undo = when {
                            undoing || outcome.note == RaNote.UndoStale -> null
                            else -> outcome.before ?: state.undo
                        },
                    ).let { it.copy(otherStyle = otherStyle(it, null)) }
                }
                // A new BIOS folder: the BIOS tab looks in the new one.
                val before = outcome.before
                if (before != null && outcome.config?.value(RaUiModel.SYSTEM_DIR) != before.value(RaUiModel.SYSTEM_DIR)) refresh()
            }
        }
    }

    /** Looks through the BIOS folders and RetroArch's BIOS folder again. */
    fun scanBios() {
        if (scanWaiting) return
        scanWaiting = true
        viewModelScope.launch {
            biosWork.withLock {
                scanWaiting = false
                val state = _uiState.value
                val packageName = state.found?.packageName
                val config = state.config
                val folders = prefs.retroArchBiosFolders
                _uiState.update { it.copy(bios = it.bios.copy(folders = folders, scanning = true)) }
                val (target, scan) = withContext(Dispatchers.IO) {
                    val target = if (packageName != null && config != null) biosFiles.target(packageName, config) else null
                    target to biosFiles.scan(folders, target?.configuredRoot)
                }
                _uiState.update { now ->
                    val note = if (scan == null) BiosNote.ScanFailed else now.bios.note.takeUnless { it == BiosNote.ScanFailed }
                    now.copy(bios = now.bios.copy(target = target, scan = scan, scanning = false, note = note))
                }
            }
        }
    }

    /** Adds a folder picked in the folder picker; root reads it, so the picker's access to it isn't kept. */
    fun onBiosFolderPicked(tree: Uri?) {
        tree ?: return
        val path = runCatching { BiosPaths.fromTree(tree.authority, DocumentsContract.getTreeDocumentId(tree)) }.getOrNull()
        if (path == null) {
            _uiState.update { it.copy(bios = it.bios.copy(note = BiosNote.Unusable)) }
            return
        }
        prefs.retroArchBiosFolders = BiosPaths.withoutNested(prefs.retroArchBiosFolders + path)
        _uiState.update { it.copy(bios = it.bios.copy(folders = prefs.retroArchBiosFolders, note = null)) }
        scanBios()
    }

    fun removeBiosFolder(folder: String) {
        prefs.retroArchBiosFolders = prefs.retroArchBiosFolders - folder
        _uiState.update { it.copy(bios = it.bios.copy(folders = prefs.retroArchBiosFolders, note = null)) }
        scanBios()
    }

    /** Ticks [candidate] for [entry] (one file per BIOS file), or unticks it. */
    fun tickBios(entry: BiosEntry, candidate: BiosCandidate, on: Boolean) = _uiState.update {
        it.copy(bios = it.bios.copy(choices = it.bios.choices + (entry.key to candidate.path.takeIf { on })))
    }

    /** Points RetroArch's settings at its own system folder, through the same write as every other change. */
    fun useOwnBiosFolder() {
        val own = _uiState.value.bios.target?.own ?: return
        change(mapOf(RaUiModel.SYSTEM_DIR to own))
    }

    /** Copies the ticked files into RetroArch's BIOS folder, then looks again. */
    fun copyBios() {
        val bios = _uiState.value.bios
        val copies = bios.copies
        val ownerOf = bios.target?.ownerOf ?: return
        if (copies.isEmpty() || bios.copying) return
        viewModelScope.launch {
            biosWork.withLock {
                _uiState.update { it.copy(bios = it.bios.copy(copying = true, note = null)) }
                val results = withContext(Dispatchers.IO) { copies.map { it to biosFiles.copy(it, ownerOf) } }
                val note = BiosNote.Copied(
                    copied = results.filter { (_, result) -> result.isSuccess }.map { (copy, _) -> copy.file.path },
                    failed = results.mapNotNull { (copy, result) ->
                        result.exceptionOrNull()?.let {
                            copy.file.path to
                                (it.message ?: "PServer")
                        }
                    },
                )
                _uiState.update { it.copy(bios = it.bios.copy(copying = false, choices = emptyMap(), note = note)) }
            }
            scanBios()
        }
    }

    /**
     * Looks at what RetroArch has again and shows the ROM list kept from the last look; with [fullScan], or when the
     * folders changed since, it looks through the ROM folders again (the user's choices stay).
     */
    fun loadCheats(fullScan: Boolean = false) {
        viewModelScope.launch {
            cheatWork.withLock {
                val state = _uiState.value
                val found = state.found
                val config = state.config
                val folders = prefs.retroArchRomFolders
                val source = CheatSourceKind.of(prefs.retroArchCheatSource)
                _uiState.update { it.copy(cheats = it.cheats.copy(folders = folders, source = source)) }
                val kept = if (fullScan) null else withContext(Dispatchers.IO) { cheatFiles.keptRoms() }
                val listing = kept?.takeIf { it.folders.toSet() == folders.toSet() }
                val scanRoms = listing == null && folders.isNotEmpty()
                if (scanRoms) _uiState.update { it.copy(cheats = it.cheats.copy(scanning = true)) }
                val read = withContext(Dispatchers.IO) {
                    val roms = listing ?: if (scanRoms) cheatFiles.scanRoms(folders) else null
                    val places = if (found != null && config != null) RetroArchPlaces.of(config, found.packageName) else null
                    CheatsRead(
                        listing = roms,
                        games = roms?.let { RomLibrary.games(it.files) }.orEmpty(),
                        side = places?.let(cheatFiles::scanRetroArch),
                        choices = cheatFiles.choices(),
                        pack = cheatFiles.pack.info,
                    )
                }
                _uiState.update { now ->
                    val cheats = now.cheats
                    val systems = read.games.map { it.system }.filter { it.hasCheats }.distinct()
                    val note = when {
                        scanRoms && read.listing == null -> CheatNote.ScanFailed
                        cheats.note == CheatNote.ScanFailed -> null
                        else -> cheats.note
                    }
                    now.copy(
                        cheats = cheats.copy(
                            loaded = true,
                            scanning = false,
                            listing = read.listing,
                            games = read.games,
                            side = read.side ?: cheats.side,
                            choices = read.choices,
                            pack = read.pack,
                            system = cheats.system?.takeIf { it in systems } ?: systems.firstOrNull(),
                            game = cheats.game?.takeIf { open -> read.games.any { it.path == open.game.path } },
                            note = note,
                        ),
                    )
                }
            }
            _uiState.value.cheats.system?.let(::loadIndex)
            refreshTargets()
        }
    }

    fun selectCheatSystem(system: CheatSystem) {
        _uiState.update { it.copy(cheats = it.cheats.copy(system = system)) }
        loadIndex(system)
    }

    fun searchGames(text: String) = _uiState.update { it.copy(cheats = it.cheats.copy(search = text)) }

    fun openGame(game: RomGame) {
        _uiState.update { it.copy(cheats = it.cheats.copy(game = OpenGame(game))) }
        loadGame()
    }

    fun closeGame() = _uiState.update { it.copy(cheats = it.cheats.copy(game = null)) }

    /** Uses the cheat file [name] for the open game, or none when null, and remembers that. */
    fun chooseCheatFile(name: String?) = rememberChoice { choices, game -> choices.copy(files = choices.files + (game.path to name)) }

    /** Goes back to the best match for the open game. */
    fun useBestCheatFile() = rememberChoice { choices, game -> choices.copy(files = choices.files - game.path) }

    fun tickCore(core: String, on: Boolean) {
        val open = _uiState.value.cheats.game ?: return
        val cores = if (on) open.cores + core else open.cores - core
        rememberChoice(reload = false) { choices, game -> choices.copy(cores = choices.cores + (game.path to cores)) }
    }

    fun tickCheat(index: Int, on: Boolean) = updateOpen { it.copy(ticks = if (on) it.ticks + index else it.ticks - index) }

    fun tickNoCheats() = updateOpen { it.copy(ticks = emptySet()) }

    fun searchCheats(text: String) = updateOpen { it.copy(cheatSearch = text) }

    fun searchCheatFiles(text: String) = updateOpen { it.copy(fileSearch = text) }

    /** Turns RetroArch's "apply cheats after game load" on or off, through the same write as every other change. */
    fun setApplyCheats(on: Boolean) = change(mapOf(RaUiModel.APPLY_CHEATS to on.toString()))

    /**
     * Writes the open game's cheat file, with the ticked cheats on, into each ticked core's folder in RetroArch's cheats
     * folder; with any cheat ticked, RetroArch is set to switch them on when the game starts.
     */
    fun addCheats() {
        val state = _uiState.value
        val open = state.cheats.game ?: return
        val file = open.file ?: return
        val side = state.cheats.side ?: return
        val found = state.found ?: return
        val config = state.config ?: return
        val targets = open.targets.filter { it.core in open.cores }
        if (targets.isEmpty() || open.writing) return
        val root = side.cheatsRoot ?: RetroArchPlaces.of(config, found.packageName).cheats.first()
        val name = RomNames.cheatFile(open.game.name, found.version)
        val ticks = open.ticks
        val cht = file.withEnabled(ticks)
        val cheatsThere = side.cheatsRoot != null
        viewModelScope.launch {
            val written = cheatWork.withLock {
                updateOpen { it.copy(writing = true) }
                val results = withContext(Dispatchers.IO) {
                    targets.map { target ->
                        target.core to cheatFiles.install(root, cheatsThere, target.core, target.core in side.cheatFolders, name, cht)
                    }
                }
                val done = results.filter { (_, result) -> result.isSuccess }.map { (core, _) -> core }
                val failed = results.mapNotNull { (core, result) -> result.exceptionOrNull()?.let { core to (it.message ?: "PServer") } }
                _uiState.update { now ->
                    val before = now.cheats.side ?: side
                    val after = before.copy(
                        cheatsRoot = before.cheatsRoot ?: root.takeIf { done.isNotEmpty() },
                        installed = before.installed.filterNot { it.name == name && it.core in done } +
                            done.map { InstalledCheats(it, name, cht.cheats.size, ticks.size) },
                        cheatFolders = before.cheatFolders + done,
                        coreFolders = before.coreFolders + done,
                    )
                    now.copy(
                        cheats = now.cheats.copy(
                            side = after,
                            game = now.cheats.game?.copy(writing = false),
                            note = CheatNote.Added(name, done, ticks.size, failed),
                        ),
                    )
                }
                refreshTargets()
                done
            }
            if (ticks.isNotEmpty() && written.isNotEmpty() && config.flag(RaUiModel.APPLY_CHEATS) != true) setApplyCheats(true)
        }
    }

    fun setCheatSource(kind: CheatSourceKind) {
        if (kind == _uiState.value.cheats.source) return
        prefs.retroArchCheatSource = kind.id
        _uiState.update { it.copy(cheats = it.cheats.copy(source = kind, indexes = emptyMap(), note = null)) }
        _uiState.value.cheats.system?.let(::loadIndex)
        loadGame()
    }

    /** Downloads libretro's cheat pack (again); the one there stays until the new one is complete. */
    fun downloadPack() {
        if (_uiState.value.cheats.packProgress != null) return
        _uiState.update { it.copy(cheats = it.cheats.copy(packProgress = 0L to 0L, note = null)) }
        viewModelScope.launch {
            val (result, info) = withContext(Dispatchers.IO) {
                val job = currentCoroutineContext().job
                val result = runCatching {
                    cheatFiles.pack.download { done, total ->
                        job.ensureActive()
                        _uiState.update { it.copy(cheats = it.cheats.copy(packProgress = done to total)) }
                    }
                }
                result to cheatFiles.pack.info
            }
            packChanged(info, problemNote(result.exceptionOrNull()) ?: CheatNote.PackReady)
        }
    }

    fun deletePack() {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                cheatFiles.pack.delete()
                cheatFiles.pack.info
            }
            packChanged(info, null)
        }
    }

    fun onRomFolderPicked(tree: Uri?) {
        tree ?: return
        val path = runCatching { BiosPaths.fromTree(tree.authority, DocumentsContract.getTreeDocumentId(tree)) }.getOrNull()
        if (path == null) {
            _uiState.update { it.copy(cheats = it.cheats.copy(note = CheatNote.Unusable)) }
            return
        }
        prefs.retroArchRomFolders = BiosPaths.withoutNested(prefs.retroArchRomFolders + path)
        loadCheats(fullScan = true)
    }

    fun removeRomFolder(folder: String) {
        prefs.retroArchRomFolders = prefs.retroArchRomFolders - folder
        loadCheats(fullScan = true)
    }

    /** The cheat list of [system] from the chosen source, unless it is here or on its way. */
    private fun loadIndex(system: CheatSystem) {
        val cheats = _uiState.value.cheats
        if (!system.hasCheats || system in cheats.indexes || system in cheats.loading) return
        val kind = cheats.source
        _uiState.update { it.copy(cheats = it.cheats.copy(loading = it.cheats.loading + system)) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { cheatFiles.source(kind).index(system) } }
            val stale = _uiState.value.cheats.source != kind
            _uiState.update { now ->
                val index = result.getOrNull()?.takeUnless { stale }
                now.copy(
                    cheats = now.cheats.copy(
                        loading = now.cheats.loading - system,
                        indexes = if (index != null) now.cheats.indexes + (system to index) else now.cheats.indexes,
                        note = problemNote(result.exceptionOrNull()?.takeUnless { stale })
                            ?: now.cheats.note.takeUnless { it is CheatNote.Problem && index != null },
                    ),
                )
            }
            when {
                stale -> loadIndex(system)
                _uiState.value.cheats.game?.game?.system == system -> loadGame()
            }
        }
    }

    /**
     * Works out the open game's cheat file and cores; when the file in use changed, reads it from the source and RetroArch's
     * copy as root, whose ticks are taken when it is the same file.
     */
    private fun loadGame() {
        val state = _uiState.value
        val cheats = state.cheats
        val open = cheats.game ?: return
        val game = open.game
        val side = cheats.side ?: RetroArchSide()
        val targets = CheatPlan.targets(game, side, state.found?.version)
        val cores = CheatPlan.chosenCores(game, targets, cheats.choices)
        val index = cheats.indexes[game.system]
        if (index == null) {
            updateOpen { it.copy(targets = targets, cores = cores) }
            loadIndex(game.system)
            return
        }
        val pick = CheatPlan.pick(game, index, cheats.choices)
        val candidates = index.candidates(game.name)
        val similar = if (candidates.isEmpty()) index.similar(game.name) else emptyList()
        val same = open.file != null && open.pick?.file == pick?.file
        updateOpen {
            it.copy(
                candidates = candidates,
                similar = similar,
                pick = pick,
                file = it.file.takeIf { same },
                ticks = if (same) it.ticks else emptySet(),
                loading = pick != null && !same,
                targets = targets,
                cores = cores,
            )
        }
        if (pick == null || same) return
        val kind = cheats.source
        val root = side.cheatsRoot
        val there = targets.firstOrNull { it.core in cores && it.installed != null }?.installed
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = ChtFile.of(cheatFiles.source(kind).read(game.system, pick.file))
                    file to there?.let { if (root != null) cheatFiles.readInstalled("$root/${it.core}/${it.name}") else null }
                }
            }
            _uiState.update { now ->
                val current = now.cheats.game?.takeIf { it.game.path == game.path && it.pick == pick } ?: return@update now
                val (file, installed) = result.getOrNull() ?: (null to null)
                val ticks = when {
                    file == null -> emptySet()
                    installed != null && installed.cheats.map { it.description } == file.cheats.map { it.description } ->
                        installed.cheats.filter { it.enabled }.map { it.index }.toSet()
                    else -> file.cheats.filter { it.enabled }.map { it.index }.toSet()
                }
                now.copy(
                    cheats = now.cheats.copy(
                        game = current.copy(file = file, loading = false, ticks = ticks),
                        note = problemNote(result.exceptionOrNull()) ?: now.cheats.note,
                    ),
                )
            }
        }
    }

    /** The open game's cores again, after RetroArch's side or the choices changed; its file and ticks stay. */
    private fun refreshTargets() = _uiState.update { now ->
        val open = now.cheats.game ?: return@update now
        val targets = CheatPlan.targets(open.game, now.cheats.side ?: RetroArchSide(), now.found?.version)
        val cores = CheatPlan.chosenCores(open.game, targets, now.cheats.choices)
        now.copy(cheats = now.cheats.copy(game = open.copy(targets = targets, cores = cores)))
    }

    private fun updateOpen(change: (OpenGame) -> OpenGame) = _uiState.update { now ->
        val open = now.cheats.game ?: return@update now
        now.copy(cheats = now.cheats.copy(game = change(open)))
    }

    /** Changes the user's choices for the open game, keeps them, and works the game out again ([reload]) or only its cores. */
    private fun rememberChoice(reload: Boolean = true, change: (CheatChoices, RomGame) -> CheatChoices) {
        val game = _uiState.value.cheats.game?.game ?: return
        _uiState.update { it.copy(cheats = it.cheats.copy(choices = change(it.cheats.choices, game))) }
        viewModelScope.launch(Dispatchers.IO) {
            choiceSaves.withLock { cheatFiles.saveChoices(_uiState.value.cheats.choices) }
        }
        if (reload) loadGame() else refreshTargets()
    }

    private fun packChanged(info: PackInfo?, note: CheatNote?) {
        _uiState.update { now ->
            val cheats = now.cheats
            val usesPack = cheats.source == CheatSourceKind.PACK
            now.copy(
                cheats = cheats.copy(
                    packProgress = null,
                    pack = info,
                    indexes = if (usesPack) emptyMap() else cheats.indexes,
                    note = note,
                ),
            )
        }
        if (_uiState.value.cheats.source == CheatSourceKind.PACK) {
            _uiState.value.cheats.system?.let(::loadIndex)
            loadGame()
        }
    }

    private fun problemNote(error: Throwable?): CheatNote? = when (error) {
        null -> null
        is CheatProblem -> CheatNote.Problem(error)
        else -> CheatNote.Problem(CheatProblem.Failed(error.message ?: error.javaClass.simpleName))
    }

    /** The hotkeys in the file were set in the other controller style than the one in use. */
    private fun otherStyle(state: RaUiModel, style: ControllerStyle?): Boolean {
        val current = style?.let { if (it == ControllerStyle.Odin) ControllerStyle.Odin.id else ControllerStyle.Xbox.id }
            ?: if (state.xbox) ControllerStyle.Xbox.id else ControllerStyle.Odin.id
        val setIn = prefs.retroArchHotkeyStyle ?: return false
        return setIn != current && state.anyHotkey
    }

    private data class Read(
        val retroArch: RetroArchState,
        val config: RetroArchConfig?,
        val running: Boolean,
        val style: ControllerStyle,
        val systemDir: String?,
        val systemDirThere: Boolean?,
    )

    private data class Outcome(
        val config: RetroArchConfig? = null,
        val before: RetroArchConfig? = null,
        val note: RaNote? = null,
        val running: Boolean = false,
    )

    private data class CheatsRead(
        val listing: RomListing?,
        val games: List<RomGame>,
        val side: RetroArchSide?,
        val choices: CheatChoices,
        val pack: PackInfo?,
    )
}
