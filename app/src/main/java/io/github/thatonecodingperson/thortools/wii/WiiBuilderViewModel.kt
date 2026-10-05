package io.github.thatonecodingperson.thortools.wii

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What the last save (or the folder access) did, for the note under the save card. */
sealed class WiiSaveResult {
    data class InDolphin(val name: String, val route: SaveRoute, val listed: Boolean) : WiiSaveResult()

    /** No way into Dolphin's folder worked yet, but the folder access would; the save waits for it. */
    data class NeedsAccess(val name: String, val detail: String?) : WiiSaveResult()

    /** The profile has to go in by hand; the copy is in Downloads (or not, when [copied] is false). */
    data class ByHand(val name: String, val copied: Boolean, val detail: String?) : WiiSaveResult()

    data object AccessGiven : WiiSaveResult()

    /** The picker came back with another folder than Dolphin's top one. */
    data object WrongFolder : WiiSaveResult()
}

data class WiiUiModel(
    val setup: WiiSetup = WiiSetup.NUNCHUK,
    val mappings: Map<WiiSetup, WiiMapping> = WiiSetup.entries.associateWith { WiiMapping(it) },
    val selected: Map<WiiSetup, WiiControl> = WiiSetup.entries.associateWith { it.controls.first() },
    val names: Map<WiiSetup, String> = emptyMap(),
    /** Null while Dolphin is being looked for. */
    val dolphin: DolphinState? = null,
    /** The user gave Thor Tools access to Dolphin's folder. */
    val access: Boolean = false,
    val existing: Set<String> = emptySet(),
    /** Dolphin has its Wii Remote profile folder already. */
    val profileFolder: Boolean = false,
    /** For a hotkey: 0 while its held button is being picked, 1 for the pressed one. */
    val comboPart: Int = 0,
    val device: DolphinProfile.Device = DolphinProfile.device(xboxStyle = true),
    /** The device has a vibration motor for rumble. */
    val vibrates: Boolean = true,
    val saving: Boolean = false,
    val result: WiiSaveResult? = null,
) {
    val mapping: WiiMapping get() = mappings.getValue(setup)
    val control: WiiControl get() = selected.getValue(setup)
    val name: String get() = names[setup].orEmpty()
    val found: DolphinState.Found? get() = dolphin as? DolphinState.Found
}

@HiltViewModel
class WiiBuilderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPrefsRepo,
    private val executor: ShellExecutor,
    private val files: DolphinFiles,
    private val folderAccess: DolphinAccess,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        WiiUiModel(
            mappings = loadMappings(prefs),
            names = WiiSetup.entries.associateWith { prefs.wiiName(it.id) ?: context.getString(it.defaultName) },
        ),
    )
    val uiState: StateFlow<WiiUiModel> = _uiState.asStateFlow()

    /** Looks for Dolphin and its Wii Remote setup again, e.g. after coming back from Dolphin. */
    fun refresh() {
        viewModelScope.launch {
            val vibrates = withContext(Dispatchers.IO) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.hasVibrator() == true
            }
            val (dolphin, tree, device) = withContext(Dispatchers.IO) {
                val dolphin = files.find()
                val xbox = ControllerStyle.getStyle(executor) != ControllerStyle.Odin
                val found = dolphin as? DolphinState.Found
                val tree = found?.authority?.let(folderAccess::granted)
                found?.userDir?.let(files::moveMisplaced)
                val ini = tree?.let { runCatching { folderAccess.wiimoteIni(it) }.getOrNull() } ?: found?.userDir?.let(files::wiimoteIni)
                val index = DolphinProfile.deviceIn(ini)?.index
                Triple(dolphin, tree, if (index != null) DolphinProfile.device(xbox, index) else DolphinProfile.device(xbox))
            }
            _uiState.update { it.copy(dolphin = dolphin, access = tree != null, device = device, vibrates = vibrates) }
            readProfiles()
        }
    }

    fun selectSetup(setup: WiiSetup) = _uiState.update { it.copy(setup = setup, result = null) }

    fun select(control: WiiControl) = _uiState.update { it.copy(selected = it.selected + (it.setup to control), comboPart = 0) }

    fun selectComboPart(part: Int) = _uiState.update { it.copy(comboPart = part) }

    /** The chosen control's Thor button; for a hotkey the part being picked, after which the other part is next. */
    fun pickButton(button: PadButton?) {
        val state = _uiState.value
        val control = state.control
        if (control.kind != WiiKind.COMBO) return change { it.withButton(control, button) }
        if (button == null) return change { it.withCombo(control, null, null) }
        val held = state.comboPart == 0
        change { it.withCombo(control, button.takeIf { held }, button.takeUnless { held }) }
        _uiState.update { it.copy(comboPart = if (held) 1 else 0) }
    }

    /** Dolphin's top folder in Android's Files app, or null when this Dolphin can't be shown there. */
    fun folderIntent(): Intent? = _uiState.value.found?.authority?.let(files::folderIntent)

    /** Where the folder picker starts, for the folder access. */
    fun pickerStart(): Uri? = _uiState.value.found?.authority?.let(files::topFolder)

    /** The profile folder's full path, for the guide and the clipboard. */
    fun profilePath(): String = files.profilePath(_uiState.value.found?.shownDir ?: "Android/data/${DolphinFiles.OFFICIAL}/files")

    fun toggleSource(source: StickSource) {
        val control = _uiState.value.control
        change { mapping ->
            val current = mapping.sticks[control].orEmpty()
            mapping.withSticks(control, if (source in current) current - source else current + source)
        }
    }

    fun useSuggested() = change { it.suggested(_uiState.value.control) }

    fun setRumble(on: Boolean) = change { it.copy(rumble = on) }

    fun setRelative(on: Boolean) = change { it.copy(relativePointer = on) }

    fun resetSetup() {
        val setup = _uiState.value.setup
        prefs.setWiiMapping(setup.id, null)
        _uiState.update { it.copy(mappings = it.mappings + (setup to WiiMapping(setup)), result = null) }
    }

    fun setName(name: String) {
        val setup = _uiState.value.setup
        prefs.setWiiName(setup.id, name)
        _uiState.update { it.copy(names = it.names + (setup to name), result = null) }
    }

    /**
     * Into Dolphin's profile folder: through Dolphin's own folder access, else as root. When neither works the user is
     * asked for the folder access once, and only when that isn't possible does a copy go to Downloads with a guide.
     */
    fun saveToDolphin() {
        val state = _uiState.value
        if (state.saving) return
        val (fileName, ini) = profile(state)
        _uiState.update { it.copy(saving = true, result = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { intoDolphin(state.found, fileName, ini) }
            _uiState.update { it.copy(saving = false, result = result) }
            readProfiles()
        }
    }

    fun saveCopy() {
        val state = _uiState.value
        if (state.saving) return
        val (fileName, ini) = profile(state)
        _uiState.update { it.copy(saving = true, result = null) }
        viewModelScope.launch {
            val copied = withContext(Dispatchers.IO) { files.saveCopy(fileName, ini) }
            _uiState.update { it.copy(saving = false, result = WiiSaveResult.ByHand(fileName, copied, null)) }
        }
    }

    /** The folder picker came back: [tree] is the folder the user chose, null when they backed out. */
    fun onAccessPicked(tree: Uri?) {
        val authority = _uiState.value.found?.authority ?: return
        val waiting = _uiState.value.result as? WiiSaveResult.NeedsAccess
        viewModelScope.launch {
            val accepted = tree != null && withContext(Dispatchers.IO) { folderAccess.accept(tree, authority) }
            when {
                accepted -> {
                    _uiState.update { it.copy(access = true, result = if (waiting == null) WiiSaveResult.AccessGiven else it.result) }
                    if (waiting != null) saveToDolphin() else readProfiles()
                }
                tree != null -> _uiState.update { it.copy(result = WiiSaveResult.WrongFolder) }
                waiting != null -> byHand(waiting)
            }
        }
    }

    fun removeAccess() {
        val authority = _uiState.value.found?.authority ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { folderAccess.granted(authority)?.let(folderAccess::release) }
            _uiState.update { it.copy(access = false, result = null) }
            readProfiles()
        }
    }

    private fun intoDolphin(dolphin: DolphinState.Found?, fileName: String, ini: String): WiiSaveResult {
        val tree = dolphin?.authority?.let(folderAccess::granted)
        val userDir = dolphin?.userDir
        val results = mutableListOf<DolphinSave>()
        for (route in SaveRoute.order(access = tree != null, root = userDir != null)) {
            val save = when (route) {
                SaveRoute.DOLPHIN_ACCESS -> folderAccess.save(checkNotNull(tree), fileName, ini)
                SaveRoute.ROOT -> files.save(checkNotNull(userDir), fileName, ini)
            }
            results += save
            if (save is DolphinSave.Saved) break
        }
        return when (val end = SaveRoute.settle(results, canAskAccess = dolphin?.authority != null && tree == null)) {
            is SaveEnd.InDolphin -> WiiSaveResult.InDolphin(fileName, end.save.route, end.save.listed)
            is SaveEnd.NeedsAccess -> WiiSaveResult.NeedsAccess(fileName, end.detail)
            is SaveEnd.ByHand -> WiiSaveResult.ByHand(fileName, files.saveCopy(fileName, ini), end.detail)
        }
    }

    /** A save that waited for the folder access the user didn't give: the copy and the guide after all. */
    private suspend fun byHand(waiting: WiiSaveResult.NeedsAccess) {
        val (fileName, ini) = profile(_uiState.value)
        val copied = withContext(Dispatchers.IO) { files.saveCopy(fileName, ini) }
        _uiState.update { it.copy(result = WiiSaveResult.ByHand(waiting.name, copied, waiting.detail)) }
    }

    /** The profiles Dolphin has, read the way Dolphin sees its folder when the access allows, else as root. */
    private suspend fun readProfiles() {
        val found = _uiState.value.found ?: return
        val existing = withContext(Dispatchers.IO) {
            val tree = found.authority?.let(folderAccess::granted)
            tree?.let { runCatching { folderAccess.profiles(it) }.getOrNull() } ?: found.userDir?.let(files::profiles)
        }
        _uiState.update { it.copy(existing = existing.orEmpty(), profileFolder = existing != null) }
    }

    private fun profile(state: WiiUiModel): Pair<String, String> {
        val fileName = DolphinProfile.fileName(state.name, context.getString(state.setup.defaultName))
        return fileName to DolphinProfile.ini(state.mapping, state.device, state.vibrates)
    }

    private companion object {
        /** Raised when the suggestions change, so drafts made with the old ones start from the new ones. */
        const val SUGGESTIONS = 4

        fun loadMappings(prefs: SharedPrefsRepo): Map<WiiSetup, WiiMapping> {
            if (prefs.wiiSuggestions < SUGGESTIONS) {
                WiiSetup.entries.forEach { prefs.setWiiMapping(it.id, null) }
                prefs.wiiSuggestions = SUGGESTIONS
            }
            return WiiSetup.entries.associateWith { WiiMapping.decode(it, prefs.wiiMapping(it.id)) }
        }
    }

    private fun change(update: (WiiMapping) -> WiiMapping) {
        val setup = _uiState.value.setup
        val mapping = update(_uiState.value.mapping)
        prefs.setWiiMapping(setup.id, mapping.encode())
        _uiState.update { it.copy(mappings = it.mappings + (setup to mapping), result = null) }
    }
}
