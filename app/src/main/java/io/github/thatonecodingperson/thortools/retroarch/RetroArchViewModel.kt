package io.github.thatonecodingperson.thortools.retroarch

import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
    SHADERS(R.string.raShaderTab),
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
    val shaders: RaShaders = RaShaders(),
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

    /** The shader language RetroArch's video driver runs; null when its driver has none Thor Tools offers. */
    val shaderFormat: ShaderFormat? get() = ShaderFormat.forDriver(config?.value(VIDEO_DRIVER))

    companion object {
        const val MENU_DRIVER = "menu_driver"
        const val FAST_FORWARD = "fastforward_ratio"
        const val SLOW_MOTION = "slowmotion_ratio"
        const val SAVE_ON_EXIT = "config_save_on_exit"
        const val QUIT_TWICE = "quit_press_twice"
        const val SYSTEM_DIR = "system_directory"
        const val APPLY_CHEATS = "apply_cheats_after_load"
        const val VIDEO_DRIVER = "video_driver"
        const val SHADERS_ON = "video_shader_enable"
        const val AUTO_SHADERS = "auto_shaders_enable"
        const val SHADER_DIR = "video_shader_dir"

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
    private val shaderLibrary: ShaderLibrary,
    private val shaderLinks: ShaderLinkFiles,
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

    /** Shader scans and writes take turns. */
    private val shaderWork = Mutex()

    /** The open game's save by itself, and whether it is still waiting for its moment. */
    private var saveJob: Job? = null
    private var saveWaiting = false

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
            val tab = _uiState.value.tab
            if (tab == RaTab.CHEATS || tab == RaTab.OVERVIEW || tab == RaTab.SHADERS) loadCheats()
            if (tab == RaTab.SHADERS) loadShaders()
        }
    }

    fun selectTab(tab: RaTab) {
        _uiState.update { it.copy(tab = tab, note = null) }
        val bios = _uiState.value.bios
        if (tab == RaTab.BIOS && bios.scan == null && !bios.scanning) scanBios()
        if (tab == RaTab.CHEATS) {
            val cheats = _uiState.value.cheats
            if (cheats.loaded) cheats.system?.let(::loadIndex) else loadCheats()
        }
        if (tab == RaTab.SHADERS) {
            if (!_uiState.value.cheats.loaded) loadCheats()
            loadShaders()
        }
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
     * Looks at what RetroArch has and the games it started again, and shows the ROM list kept from the last look; with
     * [fullScan], or when the folders changed since, it looks through the ROM folders again (the user's choices stay).
     * The chosen console's cheat list is fetched only while the cheats tab is open.
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
                    val games = roms?.let { RomLibrary.games(it.files) }.orEmpty()
                    val side = places?.let(cheatFiles::scanRetroArch)
                    CheatsRead(
                        listing = roms,
                        games = games,
                        played = side?.let { RomLibrary.played(games, it.history, it.cores) },
                        side = side,
                        choices = cheatFiles.choices(),
                        pack = cheatFiles.pack.info,
                    )
                }
                _uiState.update { now ->
                    val cheats = now.cheats
                    val played = read.played ?: cheats.played
                    val systems = (read.games + played.games).map { it.system }.filter { it.hasCheats }.distinct()
                    val lastPlayed = played.games.firstOrNull { it.system.hasCheats }?.system
                    val note = when {
                        scanRoms && read.listing == null -> CheatNote.ScanFailed
                        cheats.note == CheatNote.ScanFailed -> null
                        else -> cheats.note
                    }
                    val open = cheats.game?.game?.path
                    now.copy(
                        cheats = cheats.copy(
                            loaded = true,
                            scanning = false,
                            listing = read.listing,
                            games = read.games,
                            played = played,
                            side = read.side ?: cheats.side,
                            choices = read.choices,
                            pack = read.pack,
                            system = cheats.system?.takeIf { it in systems } ?: lastPlayed ?: systems.firstOrNull(),
                            game = cheats.game?.takeIf { open in read.games.map { it.path } || open in played.launches },
                            note = note,
                        ),
                    )
                }
            }
            if (_uiState.value.tab == RaTab.CHEATS) _uiState.value.cheats.system?.let(::loadIndex)
            computePicks()
            refreshTargets()
        }
    }

    /** Opens the cheats tab on the console of the first played game whose cheats RetroArch doesn't read. */
    fun showMisplaced() {
        val state = _uiState.value
        val system = state.cheats.misplaced(state.found?.version).firstOrNull()?.system
        closeGame()
        if (system != null) _uiState.update { it.copy(cheats = it.cheats.copy(system = system, search = "")) }
        selectTab(RaTab.CHEATS)
    }

    fun selectCheatSystem(system: CheatSystem) {
        _uiState.update { it.copy(cheats = it.cheats.copy(system = system)) }
        loadIndex(system)
    }

    fun searchGames(text: String) = _uiState.update { it.copy(cheats = it.cheats.copy(search = text)) }

    fun openGame(game: RomGame) {
        flushCheats()
        _uiState.update { it.copy(cheats = it.cheats.copy(game = OpenGame(game))) }
        loadGame()
    }

    fun closeGame() {
        flushCheats()
        _uiState.update { it.copy(cheats = it.cheats.copy(game = null)) }
    }

    /** Uses the cheat file [name] for the open game, or none when null, and remembers that. */
    fun chooseCheatFile(name: String?) = rememberChoice { choices, game -> choices.copy(files = choices.files + (game.path to name)) }

    /** Goes back to the best match for the open game. */
    fun useBestCheatFile() = rememberChoice { choices, game -> choices.copy(files = choices.files - game.path) }

    fun tickCore(core: String, on: Boolean) {
        rememberChoice(reload = false) { choices, game ->
            val off = choices.off[game.path].orEmpty().filterNot { it.equals(core, ignoreCase = true) }.toSet()
            choices.copy(off = choices.off + (game.path to if (on) off else off + core))
        }
        if (on) scheduleSave()
    }

    fun tickCheat(index: Int, on: Boolean) {
        updateOpen { it.copy(ticks = if (on) it.ticks + index else it.ticks - index) }
        scheduleSave()
    }

    fun tickNoCheats() {
        updateOpen { it.copy(ticks = emptySet()) }
        scheduleSave()
    }

    fun searchCheats(text: String) = updateOpen { it.copy(cheatSearch = text) }

    fun searchCheatFiles(text: String) = updateOpen { it.copy(fileSearch = text) }

    /** Turns RetroArch's "apply cheats after game load" on or off, through the same write as every other change. */
    fun setApplyCheats(on: Boolean) = change(mapOf(RaUiModel.APPLY_CHEATS to on.toString()))

    /**
     * Writes the open game's cheat file, with the ticked cheats on, into each ticked core's folder in RetroArch's cheats
     * folder under every name RetroArch loads it as; with [replace], also into the ones holding other cheats. With any
     * cheat ticked, RetroArch is set to switch them on when the game starts.
     */
    fun addCheats(replace: Boolean) {
        cancelWaitingSave()
        val open = _uiState.value.cheats.game ?: return
        viewModelScope.launch { writeCheats(open, replace = replace, auto = false) }
    }

    /** Saves a change still waiting to be saved by itself, at once (when leaving the game or the screen). */
    fun flushCheats() {
        if (!cancelWaitingSave()) return
        val open = _uiState.value.cheats.game ?: return
        viewModelScope.launch { writeCheats(open, replace = false, auto = true) }
    }

    /** Saves the open game's ticks by themselves a moment after the last change, once its cheats are in RetroArch. */
    private fun scheduleSave() {
        val open = _uiState.value.cheats.game ?: return
        if (!open.added || open.autoCores.isEmpty()) return
        cancelWaitingSave()
        updateOpen { it.copy(pending = true, saved = null) }
        saveWaiting = true
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY)
            saveWaiting = false
            _uiState.value.cheats.game?.takeIf { it.game.path == open.game.path }?.let { writeCheats(it, replace = false, auto = true) }
        }
    }

    /** Cancels a save still waiting for its moment; true when there was one. */
    private fun cancelWaitingSave(): Boolean {
        if (!saveWaiting) return false
        saveJob?.cancel()
        saveWaiting = false
        return true
    }

    /**
     * Writes [open]'s cheat file into its ticked cores (with [replace] also those holding other cheats) under each of its
     * names, then shows what is in RetroArch now; [auto] for a save by itself.
     */
    private suspend fun writeCheats(open: OpenGame, replace: Boolean, auto: Boolean) {
        val state = _uiState.value
        val file = open.file ?: return
        val side = state.cheats.side ?: return
        val found = state.found ?: return
        val config = state.config ?: return
        val cores = (if (replace) open.cores else open.autoCores).filter { core -> open.targets.any { it.core == core } }
        if (cores.isEmpty() || open.names.isEmpty()) {
            updateGame(open.game) { it.copy(pending = false) }
            return
        }
        val root = side.cheatsRoot ?: RetroArchPlaces.of(config, found.packageName).cheats.first()
        val ticks = open.ticks
        val cht = file.withEnabled(ticks)
        val (done, failed) = cheatWork.withLock {
            updateGame(open.game) { it.copy(writing = true, pending = false) }
            val results = withContext(Dispatchers.IO) {
                var rootThere = side.cheatsRoot != null
                cores.map { core ->
                    var coreThere = side.cheatFolders.any { it.equals(core, ignoreCase = true) }
                    val result = open.names.map { name ->
                        cheatFiles.install(root, rootThere, core, coreThere, name, cht).onSuccess {
                            rootThere = true
                            coreThere = true
                        }
                    }.firstOrNull { it.isFailure } ?: Result.success(Unit)
                    core to result
                }
            }
            val done = results.filter { (_, result) -> result.isSuccess }.map { (core, _) -> core }
            val failed = results.mapNotNull { (core, result) -> result.exceptionOrNull()?.let { core to (it.message ?: "PServer") } }
            _uiState.update { now ->
                val before = now.cheats.side ?: side
                val names = open.names.map { it.lowercase() }.toSet()
                val written = done.flatMap { core -> open.names.map { InstalledCheats(core, it, cht.cheats.size, ticks.size) } }
                val after = before.copy(
                    cheatsRoot = before.cheatsRoot ?: root.takeIf { done.isNotEmpty() },
                    installed =
                    before.installed.filterNot { it.name.lowercase() in names && done.any { core -> core.equals(it.core, true) } } +
                        written,
                    cheatFolders = before.cheatFolders + done,
                )
                val note = if (auto && failed.isEmpty()) now.cheats.note else CheatNote.Added(open.names.first(), done, ticks.size, failed)
                now.copy(cheats = now.cheats.copy(side = after, note = note))
            }
            updateGame(open.game) {
                it.copy(
                    writing = false,
                    same = it.same + done,
                    other = it.other - done.toSet(),
                    saved = done.takeIf {
                        auto &&
                            it.isNotEmpty()
                    },
                )
            }
            refreshTargets()
            done to failed
        }
        if (failed.isNotEmpty()) Log.w(TAG, "Cheats for ${open.game.name} not written to $failed")
        if (ticks.isNotEmpty() && done.isNotEmpty() && config.flag(RaUiModel.APPLY_CHEATS) != true) setApplyCheats(true)
    }

    fun setCheatSource(kind: CheatSourceKind) {
        if (kind == _uiState.value.cheats.source) return
        prefs.retroArchCheatSource = kind.id
        _uiState.update { it.copy(cheats = it.cheats.copy(source = kind, indexes = emptyMap(), picks = emptyMap(), note = null)) }
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
            if (stale) {
                loadIndex(system)
                return@launch
            }
            computePicks()
            if (_uiState.value.cheats.game?.game?.system == system) loadGame()
        }
    }

    /** Each game's best cheat file in the console lists that are here, worked out once off the main thread. */
    private fun computePicks() {
        val cheats = _uiState.value.cheats
        val indexes = cheats.indexes
        val choices = cheats.choices
        val games = (cheats.games + cheats.played.games).filter { it.system in indexes && it.path !in cheats.picks }.distinctBy { it.path }
        if (games.isEmpty()) return
        viewModelScope.launch {
            val picks = withContext(Dispatchers.Default) {
                games.associate { game -> game.path to CheatPlan.pick(game, indexes.getValue(game.system), choices) }
            }
            _uiState.update { now ->
                val current = now.cheats.choices.files
                val still = picks.filterKeys { path ->
                    (path in current) == (path in choices.files) && current[path] == choices.files[path]
                }
                now.copy(cheats = now.cheats.copy(picks = now.cheats.picks + still))
            }
        }
    }

    /**
     * Works out the open game's names, cores and cheat file; when the file in use changed, reads it from the source and
     * RetroArch's copies of it as root, and takes the ticks of the copy RetroArch loads when it has the same cheats.
     */
    private fun loadGame() {
        val state = _uiState.value
        val cheats = state.cheats
        val open = cheats.game ?: return
        val game = open.game
        val side = cheats.side ?: RetroArchSide()
        val launches = cheats.launches(game)
        val version = state.found?.version
        val names = CheatPlan.names(game, launches, version)
        val targets = CheatPlan.targets(game, side, launches, version)
        val cores = CheatPlan.chosenCores(game, targets, cheats.choices)
        val elsewhere = CheatPlan.elsewhere(game, names, side, targets)
        val index = cheats.indexes[game.system]
        if (index == null) {
            updateOpen { it.copy(names = names, targets = targets, cores = cores, elsewhere = elsewhere) }
            loadIndex(game.system)
            return
        }
        val pick = CheatPlan.pick(game, index, cheats.choices)
        val kept = open.file != null && open.pick?.file == pick?.file
        updateOpen {
            it.copy(
                pick = pick,
                file = it.file.takeIf { kept },
                ticks = if (kept) it.ticks else emptySet(),
                loading = pick != null && !kept,
                names = names,
                targets = targets,
                cores = cores,
                elsewhere = elsewhere,
                same = if (kept) it.same else emptySet(),
                other = if (kept) it.other else emptySet(),
            )
        }
        val kind = cheats.source
        val root = side.cheatsRoot
        viewModelScope.launch {
            val (candidates, similar) = withContext(Dispatchers.Default) {
                val candidates = index.candidates(game.name)
                candidates to if (candidates.isEmpty()) index.similar(game.name) else emptyList()
            }
            updateGame(game) { it.copy(candidates = candidates, similar = similar) }
            if (pick == null || kept) return@launch
            val copies = targets.mapNotNull { target -> target.installed } + elsewhere
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = ChtFile.of(cheatFiles.source(kind).read(game.system, pick.file))
                    val read = if (root ==
                        null
                    ) {
                        emptyMap()
                    } else {
                        copies.associate { it.core to cheatFiles.readInstalled("$root/${it.core}/${it.name}") }
                    }
                    file to read
                }
            }
            _uiState.update { now ->
                val current = now.cheats.game?.takeIf { it.game.path == game.path && it.pick == pick } ?: return@update now
                val (file, read) = result.getOrNull() ?: (null to emptyMap())
                val list = file?.cheats?.map { it.description }
                val matching = read.filterValues { copy -> copy != null && copy.cheats.map { it.description } == list }
                val targetCores = targets.map { it.core }.toSet()
                val same = matching.keys.filter { it in targetCores }.toSet()
                val other = read.filterValues { it != null }.keys.filter { it in targetCores && it !in same }.toSet()
                val last = targets.firstOrNull { it.lastPlayed }?.core
                val from = (listOfNotNull(last) + same + matching.keys).firstNotNullOfOrNull { matching[it] }
                val ticks = when {
                    file == null -> emptySet()
                    from != null -> from.cheats.filter { it.enabled }.map { it.index }.toSet()
                    else -> file.cheats.filter { it.enabled }.map { it.index }.toSet()
                }
                now.copy(
                    cheats = now.cheats.copy(
                        game = current.copy(file = file, loading = false, ticks = ticks, same = same, other = other),
                        note = problemNote(result.exceptionOrNull()) ?: now.cheats.note,
                    ),
                )
            }
        }
    }

    /** The open game's names and cores again, after RetroArch's side or the choices changed; its file and ticks stay. */
    private fun refreshTargets() = _uiState.update { now ->
        val open = now.cheats.game ?: return@update now
        val side = now.cheats.side ?: RetroArchSide()
        val launches = now.cheats.launches(open.game)
        val version = now.found?.version
        val names = CheatPlan.names(open.game, launches, version)
        val targets = CheatPlan.targets(open.game, side, launches, version)
        val cores = CheatPlan.chosenCores(open.game, targets, now.cheats.choices)
        val elsewhere = CheatPlan.elsewhere(open.game, names, side, targets)
        now.copy(cheats = now.cheats.copy(game = open.copy(names = names, targets = targets, cores = cores, elsewhere = elsewhere)))
    }

    private fun updateOpen(change: (OpenGame) -> OpenGame) = _uiState.update { now ->
        val open = now.cheats.game ?: return@update now
        now.copy(cheats = now.cheats.copy(game = change(open)))
    }

    /** Changes the open game while it is still [game]. */
    private fun updateGame(game: RomGame, change: (OpenGame) -> OpenGame) = _uiState.update { now ->
        val open = now.cheats.game?.takeIf { it.game.path == game.path } ?: return@update now
        now.copy(cheats = now.cheats.copy(game = change(open)))
    }

    /** Changes the user's choices for the open game, keeps them, and works the game out again ([reload]) or only its cores. */
    private fun rememberChoice(reload: Boolean = true, change: (CheatChoices, RomGame) -> CheatChoices) {
        val game = _uiState.value.cheats.game?.game ?: return
        _uiState.update { now ->
            val choices = change(now.cheats.choices, game)
            val index = now.cheats.indexes[game.system]
            val picks = if (index != null) now.cheats.picks + (game.path to CheatPlan.pick(game, index, choices)) else now.cheats.picks
            now.copy(cheats = now.cheats.copy(choices = choices, picks = picks))
        }
        viewModelScope.launch(Dispatchers.IO) {
            choiceSaves.withLock { cheatFiles.saveChoices(_uiState.value.cheats.choices) }
        }
        if (reload) loadGame() else refreshTargets()
    }

    /**
     * Looks at RetroArch's shaders again; when the collection is on storage, brings the sorted folders up to date with
     * the installed cores and the console switch by themselves (only Thor Tools' shortcuts change).
     */
    fun loadShaders() {
        val state = _uiState.value
        val found = state.found ?: return
        val config = state.config ?: return
        val format = state.shaderFormat
        if (format == null) {
            _uiState.update { it.copy(shaders = it.shaders.copy(loaded = true)) }
            return
        }
        val places = ShaderPlaces.of(config, found.packageName)
        viewModelScope.launch {
            shaderWork.withLock {
                val (scan, pack) = withContext(Dispatchers.IO) { shaderLibrary.scan(places, format) to shaderLibrary.pack(format) }
                _uiState.update { now ->
                    val note = if (scan == null) ShaderNote.ScanFailed else now.shaders.note.takeUnless { it == ShaderNote.ScanFailed }
                    now.copy(
                        shaders = now.shaders.copy(
                            loaded = true,
                            scan = scan ?: now.shaders.scan,
                            note = note,
                            pack = pack,
                            allConsoles = prefs.retroArchShadersAllConsoles,
                            links = prefs.retroArchShaderLinks,
                        ),
                    )
                }
            }
            if (_uiState.value.shaders.scan?.libraryReady == true) updateShaderFolders(quiet = true)
        }
    }

    /**
     * Gets the shaders into RetroArch's own Shaders menu: copies RetroArch's own collection to its shader folder on
     * storage (or downloads and unpacks libretro's pack when it has none), sorts them into the folders, and points
     * RetroArch's shader folder there.
     */
    fun getShaders() {
        val state = _uiState.value
        val found = state.found ?: return
        val config = state.config ?: return
        val format = state.shaderFormat ?: return
        val scan = state.shaders.scan ?: return
        if (state.shaders.working) return
        val places = ShaderPlaces.of(config, found.packageName)
        viewModelScope.launch {
            val result = shaderWork.withLock {
                _uiState.update { it.copy(shaders = it.shaders.copy(working = true, note = null)) }
                val result = withContext(Dispatchers.IO) {
                    val job = currentCoroutineContext().job
                    val step = { part: Int, parts: Int -> _uiState.update { it.copy(shaders = it.shaders.copy(step = part to parts)) } }
                    runCatching {
                        if (!scan.libraryReady) {
                            if (scan.privateFiles > 0) {
                                shaderLibrary.copyFromRetroArch(places, format, step).getOrThrow()
                            } else {
                                if (shaderLibrary.pack(format) == null) {
                                    shaderLibrary.download(format) { done, total ->
                                        job.ensureActive()
                                        _uiState.update { it.copy(shaders = it.shaders.copy(progress = done to total)) }
                                    }
                                }
                                _uiState.update { it.copy(shaders = it.shaders.copy(progress = null, pack = shaderLibrary.pack(format))) }
                                shaderLibrary.unpack(places, format, step).getOrThrow()
                            }
                        }
                    }
                }
                _uiState.update { it.copy(shaders = it.shaders.copy(working = false, progress = null, step = null)) }
                result
            }
            result.onFailure { error ->
                Log.w(TAG, "Getting the shaders failed", error)
                _uiState.update { it.copy(shaders = it.shaders.copy(note = shaderProblem(error))) }
                return@launch
            }
            updateShaderFolders(quiet = false, ready = true)
            if (!places.menuOnStorage) pointShaderMenu()
        }
    }

    /**
     * Brings the sorted folders up to date: writes the catalogue's shortcuts missing for the chosen consoles and removes
     * Thor Tools' shortcuts in the folders of consoles no longer chosen. [quiet] says nothing when nothing changed.
     */
    private suspend fun updateShaderFolders(quiet: Boolean, ready: Boolean = false) {
        val state = _uiState.value
        val found = state.found ?: return
        val config = state.config ?: return
        val format = state.shaderFormat ?: return
        val places = ShaderPlaces.of(config, found.packageName)
        shaderWork.withLock {
            val scan = withContext(Dispatchers.IO) { shaderLibrary.scan(places, format) } ?: return
            val consoles = ShaderShortcuts.consoles(state.cheats.side?.cores.orEmpty(), prefs.retroArchShadersAllConsoles)
            val changes = ShaderShortcuts.changes(
                ShaderShortcuts.planned(format, consoles),
                scan.presets,
                prefs.retroArchShadersDismissed,
                format,
            )
            if (changes.none && quiet) {
                _uiState.update { it.copy(shaders = it.shaders.copy(scan = scan)) }
                return
            }
            _uiState.update { it.copy(shaders = it.shaders.copy(working = true)) }
            val result = withContext(Dispatchers.IO) {
                shaderLibrary.write(places, changes.write).mapCatching { shaderLibrary.remove(places, changes.remove).getOrThrow() }
            }
            val rescan = withContext(Dispatchers.IO) { shaderLibrary.scan(places, format) }
            val note = result.fold(
                onSuccess = {
                    if (ready) ShaderNote.Ready(rescan?.shortcuts ?: 0) else ShaderNote.Updated(changes.write.size, changes.remove.size)
                },
                onFailure = { shaderProblem(it) },
            )
            if (result.isFailure) Log.w(TAG, "Sorting the shaders failed", result.exceptionOrNull())
            _uiState.update { it.copy(shaders = it.shaders.copy(working = false, scan = rescan ?: scan, note = note)) }
        }
    }

    /** Sorted folders for every console with console-made shaders ([all]), or only for the installed cores' consoles. */
    fun setAllShaderConsoles(all: Boolean) {
        prefs.retroArchShadersAllConsoles = all
        _uiState.update { it.copy(shaders = it.shaders.copy(note = null, allConsoles = all)) }
        viewModelScope.launch { updateShaderFolders(quiet = false) }
    }

    /** Sorts the shaders again: the catalogue's shortcuts missing and the folders of the consoles chosen now. */
    fun sortShadersAgain() {
        if (_uiState.value.shaders.working) return
        viewModelScope.launch { updateShaderFolders(quiet = false) }
    }

    /** RetroArch's own switch for all shaders ("Video Shaders"). */
    fun setShadersOn(on: Boolean) = change(mapOf(RaUiModel.SHADERS_ON to on.toString()))

    fun showShaderGuide(show: Boolean) = _uiState.update { it.copy(shaders = it.shaders.copy(guide = show)) }

    fun openShaderFolder(folder: String?) = _uiState.update { it.copy(shaders = it.shaders.copy(openFolder = folder, chosen = null)) }

    fun chooseShortcut(path: String?) = _uiState.update { it.copy(shaders = it.shaders.copy(chosen = path, note = null)) }

    /** Moves the chosen shortcut to [folder] (a new one there, the old one removed); the catalogue won't make it again where it was. */
    fun moveShortcut(folder: String) = changeShortcut { places, preset ->
        val moved = ShaderShortcuts.moved(preset, folder) ?: return@changeShortcut Result.failure(IllegalStateException("no target"))
        shaderLibrary.write(places, listOf(moved)).mapCatching { shaderLibrary.remove(places, listOf(preset.path)).getOrThrow() }
            .map { ShaderNote.Moved(folder) }
    }

    /**
     * Removes the chosen shortcut (only a Thor Tools one); the catalogue won't make it again. A link's files go with
     * the last shortcut pointing at them.
     */
    fun removeShortcut() = changeShortcut { places, preset ->
        val others = _uiState.value.shaders.scan?.presets.orEmpty().filter { it.path != preset.path }
        shaderLibrary.remove(places, listOf(preset.path)).mapCatching {
            val key = ShaderShortcuts.linkKey(preset.target)
            if (key != null && others.none { ShaderShortcuts.linkKey(it.target) == key }) {
                shaderLibrary.removeLinkFiles(places, key).getOrThrow()
                shaderLinks.forget(key)
            }
            ShaderNote.Removed
        }
    }

    private fun changeShortcut(change: (ShaderPlaces, FoundPreset) -> Result<ShaderNote>) {
        val state = _uiState.value
        val found = state.found ?: return
        val config = state.config ?: return
        val format = state.shaderFormat ?: return
        val preset = state.shaders.scan?.presets?.firstOrNull { it.path == state.shaders.chosen && it.ours } ?: return
        val places = ShaderPlaces.of(config, found.packageName)
        prefs.retroArchShadersDismissed = prefs.retroArchShadersDismissed + preset.path
        viewModelScope.launch {
            shaderWork.withLock {
                _uiState.update { it.copy(shaders = it.shaders.copy(working = true, note = null)) }
                val result = withContext(Dispatchers.IO) { change(places, preset) }
                val rescan = withContext(Dispatchers.IO) { shaderLibrary.scan(places, format) }
                _uiState.update {
                    it.copy(
                        shaders = it.shaders.copy(
                            working = false,
                            scan = rescan ?: it.shaders.scan,
                            chosen = null,
                            note = result.getOrElse { error -> shaderProblem(error) },
                        ),
                    )
                }
            }
        }
    }

    /** Removes the automatic presets an older Thor Tools wrote into RetroArch's menu config folder. */
    fun removeOldLooks() {
        val state = _uiState.value
        val found = state.found ?: return
        val config = state.config ?: return
        val format = state.shaderFormat ?: return
        val looks = state.shaders.scan?.oldLooks.orEmpty()
        if (looks.isEmpty()) return
        val places = ShaderPlaces.of(config, found.packageName)
        viewModelScope.launch {
            shaderWork.withLock {
                val result = withContext(Dispatchers.IO) { shaderLibrary.removeOldLooks(looks) }
                val rescan = withContext(Dispatchers.IO) { shaderLibrary.scan(places, format) }
                _uiState.update {
                    it.copy(
                        shaders = it.shaders.copy(
                            scan = rescan ?: it.shaders.scan,
                            note = result.fold({ ShaderNote.OldLooksRemoved }, { error -> shaderProblem(error) }),
                        ),
                    )
                }
            }
        }
    }

    /** Points RetroArch's shader folder (where its Shaders menu opens) at the sorted folders on storage. */
    fun pointShaderMenu() {
        val state = _uiState.value
        val found = state.found ?: return
        val config = state.config ?: return
        change(mapOf(RaUiModel.SHADER_DIR to ShaderPlaces.of(config, found.packageName).root))
    }

    /** Removes libretro's downloaded pack; the shaders in RetroArch stay. */
    fun deleteShaderPack() {
        val format = _uiState.value.shaderFormat ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { shaderLibrary.deletePack(format) }
            _uiState.update { it.copy(shaders = it.shaders.copy(pack = null)) }
        }
    }

    fun setLinkText(text: String) = _uiState.update { it.copy(shaders = it.shaders.copy(linkText = text)) }

    /**
     * Reads the link typed: the repository's listing (or the preset of a direct link with the files it pulls in), and
     * prepares each preset: its plain name, the folders it is guessed to go to, the file its shortcut points at (the
     * library's copy for libretro's own collection), ticked when it is in the language RetroArch's driver runs and no
     * shortcut points there yet.
     */
    fun lookLink(text: String = _uiState.value.shaders.linkText) {
        val state = _uiState.value
        val format = state.shaderFormat ?: return
        if (state.shaders.linkBusy) return
        val link = ShaderLinks.parse(text)
        if (link == null) {
            _uiState.update { it.copy(shaders = it.shaders.copy(note = ShaderNote.NotALink, linkScan = null)) }
            return
        }
        val shaderScan = state.shaders.scan
        val found = shaderScan?.presets.orEmpty()
        val places = state.config?.let { config -> state.found?.let { ShaderPlaces.of(config, it.packageName) } }
        _uiState.update {
            it.copy(shaders = it.shaders.copy(linkText = text, linkBusy = true, linkScan = null, linkGroup = null, note = null))
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val linkFolder = "${ShaderShortcuts.LINKS}/${ShaderShortcuts.safe(link.label)}"
                    val inLibrary = if (places != null && shaderScan?.libraryReady == true && ShaderShortcuts.isLibrary(link, format)) {
                        shaderLibrary.libraryPresets(places, format)
                    } else {
                        emptySet()
                    }
                    fun preset(path: String, pulled: List<String>, tick: Boolean = true): LinkPreset {
                        val usable = path.endsWith(".${format.preset}", ignoreCase = true)
                        val target = ShaderShortcuts.linkTarget(link, path, format, inLibrary)
                        val already = found.filter { it.target.equals(target, ignoreCase = true) }.map { it.path }
                        return LinkPreset(
                            path = path,
                            name = ShaderGuess.name(path),
                            folders = ShaderGuess.place(path, pulled, linkFolder),
                            ticked = tick && usable && already.isEmpty(),
                            usable = usable,
                            target = target,
                            already = already,
                        )
                    }
                    when (link) {
                        is ShaderLink.Repo -> {
                            val listing = shaderLinks.list(link)
                            val paths = ShaderLinks.presets(listing.files, link.path)
                            val pulled = if (paths.size <= READ_PRESETS) linkNames(link, listing.ref, paths, format) else emptyMap()
                            val presets = paths.map { preset(it, pulled[it].orEmpty(), tick = paths.size <= MANY_PRESETS) }
                            LinkScan(link, listing.ref, listing.files, presets, listing.truncated)
                        }
                        is ShaderLink.Direct -> {
                            val fetched = shaderLinks.fetch(link, null, listOf(link.path), null, format)
                            val pulled = fetched.files.keys.filter { it != link.path } + fetched.library
                            LinkScan(link, null, null, listOf(preset(link.path, pulled)), false)
                        }
                    }
                }
            }
            _uiState.update { now ->
                val scan = result.getOrNull()
                val note = when {
                    result.isFailure -> shaderProblem(result.exceptionOrNull()!!)
                    scan?.presets.isNullOrEmpty() -> ShaderNote.NoPresets
                    else -> null
                }
                now.copy(shaders = now.shaders.copy(linkBusy = false, linkScan = scan?.takeIf { it.presets.isNotEmpty() }, note = note))
            }
        }
    }

    fun tickLinkPreset(path: String, on: Boolean) = updateLinkScan { scan ->
        scan.copy(presets = scan.presets.map { if (it.path == path) it.copy(ticked = on) else it })
    }

    /** Ticks or unticks every usable preset going to the folders of [group] that isn't there yet. */
    fun tickLinkGroup(group: String, on: Boolean) = updateLinkScan { scan ->
        scan.copy(presets = scan.presets.map { if (it.group == group && it.usable && !it.added) it.copy(ticked = on) else it })
    }

    fun chooseLinkGroup(group: String?) = _uiState.update { it.copy(shaders = it.shaders.copy(linkGroup = group)) }

    /** Sends every preset of [group] to [folder] instead. */
    fun moveLinkGroup(group: String, folder: String) {
        updateLinkScan { scan ->
            scan.copy(
                presets = scan.presets.map {
                    if (it.group ==
                        group
                    ) {
                        it.copy(folders = listOf(folder))
                    } else {
                        it
                    }
                },
            )
        }
        chooseLinkGroup(null)
    }

    private fun updateLinkScan(change: (LinkScan) -> LinkScan) = _uiState.update { now ->
        val scan = now.shaders.linkScan ?: return@update now
        now.copy(shaders = now.shaders.copy(linkScan = change(scan)))
    }

    /**
     * Adds the ticked presets of the link read: fetches the ones not on the Thor with every file they pull in, copies
     * them into RetroArch's shader folder and writes the shortcuts into the folders chosen; remembers the link.
     */
    fun addLinkShaders() {
        val state = _uiState.value
        val found = state.found ?: return
        val config = state.config ?: return
        val format = state.shaderFormat ?: return
        val scan = state.shaders.linkScan ?: return
        val ticked = scan.ticked
        if (ticked.isEmpty() || state.shaders.linkBusy) return
        val places = ShaderPlaces.of(config, found.packageName)
        val existing = state.shaders.scan?.presets.orEmpty()
        viewModelScope.launch {
            shaderWork.withLock {
                _uiState.update { it.copy(shaders = it.shaders.copy(linkBusy = true, note = null)) }
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val download = ticked.filterNot { it.onThor }.map { it.path }
                        val fetched = if (download.isEmpty()) {
                            LinkFetch(emptyMap(), emptySet())
                        } else {
                            shaderLinks.fetch(scan.link, scan.ref, download, scan.files, format)
                        }
                        val shortcuts = ShaderShortcuts.forLink(ticked, existing)
                        shaderLinks.install(places, scan.link, fetched.files, shortcuts).getOrThrow()
                        shortcuts to shaderLibrary.missing(places, fetched.library).size
                    }
                }
                val rescan = withContext(Dispatchers.IO) { shaderLibrary.scan(places, format) }
                result.onSuccess {
                    prefs.retroArchShaderLinks =
                        (listOf(state.shaders.linkText.trim()) + prefs.retroArchShaderLinks).distinct().take(MAX_LINKS)
                }
                if (result.isFailure) Log.w(TAG, "Adding shaders from ${scan.link.label} failed", result.exceptionOrNull())
                _uiState.update { now ->
                    val shortcuts = result.getOrNull()?.first
                    now.copy(
                        shaders = now.shaders.copy(
                            linkBusy = false,
                            scan = rescan ?: now.shaders.scan,
                            links = prefs.retroArchShaderLinks,
                            linkScan = if (shortcuts != null) {
                                scan.copy(
                                    presets = scan.presets.map { preset ->
                                        if (preset !in ticked) return@map preset
                                        val made = shortcuts.filter { it.target == preset.target }.map { it.path }
                                        preset.copy(ticked = false, already = (preset.already + made).distinct())
                                    },
                                )
                            } else {
                                now.shaders.linkScan
                            },
                            note = result.fold(
                                onSuccess = { (list, missing) ->
                                    ShaderNote.LinkAdded(list.size, list.map { it.folder }.distinct().size, missing)
                                },
                                onFailure = { shaderProblem(it) },
                            ),
                        ),
                    )
                }
            }
        }
    }

    /**
     * The files each of [paths] (presets of [link]) names, as paths in the repository or in the library, read a few at a
     * time; a preset that can't be read names none.
     */
    private suspend fun linkNames(
        link: ShaderLink.Repo,
        ref: String,
        paths: List<String>,
        format: ShaderFormat,
    ): Map<String, List<String>> = coroutineScope {
        paths.chunked(READ_AT_ONCE).flatMap { chunk ->
            chunk.map { path ->
                async(Dispatchers.IO) {
                    val names = runCatching { shaderLinks.names(link, ref, path) }.getOrDefault(emptyList())
                    path to names.map { name ->
                        LibraryRefs.of(name, format) ?: ShaderDeps.resolve(path.substringBeforeLast('/', ""), name) ?: name
                    }
                }
            }.awaitAll()
        }.toMap()
    }

    private fun shaderProblem(error: Throwable): ShaderNote =
        if (error is CheatProblem) ShaderNote.Problem(error) else ShaderNote.Failed(error.message ?: error.javaClass.simpleName)

    private fun packChanged(info: PackInfo?, note: CheatNote?) {
        _uiState.update { now ->
            val cheats = now.cheats
            val usesPack = cheats.source == CheatSourceKind.PACK
            now.copy(
                cheats = cheats.copy(
                    packProgress = null,
                    pack = info,
                    indexes = if (usesPack) emptyMap() else cheats.indexes,
                    picks = if (usesPack) emptyMap() else cheats.picks,
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
        val played: PlayedGames?,
        val side: RetroArchSide?,
        val choices: CheatChoices,
        val pack: PackInfo?,
    )

    private companion object {
        const val TAG = "RetroArchAssistant"

        /** Links remembered for looking at again. */
        const val MAX_LINKS = 10

        /** A link with more presets than this has none ticked at first: the user picks. */
        const val MANY_PRESETS = 100

        /** A link with this many presets or fewer has them read when it is looked at, so what they pull in helps the guess. */
        const val READ_PRESETS = 40

        /** Presets read at the same time. */
        const val READ_AT_ONCE = 6

        /** How long after the last tick a game's cheats save by themselves. */
        const val SAVE_DELAY = 1500L
    }
}
