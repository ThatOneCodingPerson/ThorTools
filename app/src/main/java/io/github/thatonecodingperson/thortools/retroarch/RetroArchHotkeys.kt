package io.github.thatonecodingperson.thortools.retroarch

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.Hotkey
import io.github.thatonecodingperson.thortools.hotkeys.PadButton

/**
 * A RetroArch hotkey the assistant sets: [key] in `retroarch.cfg` (its `_btn` and `_axis` lines), and the Thor button
 * the suggestion presses with the hotkey enable button held. [id] is stored, so it must never change.
 */
enum class RaHotkey(val id: String, val key: String, @StringRes val title: Int, @StringRes val info: Int, val suggested: PadButton) {
    EXIT("exit", "input_exit_emulator", R.string.raHotkeyExit, R.string.raHotkeyExitInfo, PadButton.START),
    MENU("menu", "input_menu_toggle", R.string.raHotkeyMenu, R.string.raHotkeyMenuInfo, PadButton.X),
    RESET("reset", "input_reset", R.string.raHotkeyReset, R.string.raHotkeyResetInfo, PadButton.A),
    PAUSE("pause", "input_pause_toggle", R.string.raHotkeyPause, R.string.raHotkeyPauseInfo, PadButton.B),
    SCREENSHOT("screenshot", "input_screenshot", R.string.raHotkeyScreenshot, R.string.raHotkeyScreenshotInfo, PadButton.Y),
    SLOT_UP("slot_up", "input_state_slot_increase", R.string.raHotkeySlotUp, R.string.raHotkeySlotUpInfo, PadButton.DPAD_UP),
    SLOT_DOWN("slot_down", "input_state_slot_decrease", R.string.raHotkeySlotDown, R.string.raHotkeySlotDownInfo, PadButton.DPAD_DOWN),
    LOAD_STATE("load_state", "input_load_state", R.string.raHotkeyLoadState, R.string.raHotkeyLoadStateInfo, PadButton.L1),
    SAVE_STATE("save_state", "input_save_state", R.string.raHotkeySaveState, R.string.raHotkeySaveStateInfo, PadButton.R1),
    SLOW_MOTION("slow_motion", "input_hold_slowmotion", R.string.raHotkeySlowMotion, R.string.raHotkeySlowMotionInfo, PadButton.L2),
    FAST_FORWARD("fast_forward", "input_hold_fast_forward", R.string.raHotkeyFastForward, R.string.raHotkeyFastForwardInfo, PadButton.R2),
}

/** What a binding in `retroarch.cfg` is on the Thor. */
sealed class RaBinding {
    data object None : RaBinding()

    data class Thor(val button: PadButton) : RaBinding()

    /** Bound to something that isn't one of the Thor's buttons as Thor Tools writes them; [text] as RetroArch has it. */
    data class Other(val text: String) : RaBinding()
}

/**
 * How RetroArch on Android names the Thor's buttons in a binding: a button by its Android key code (so the face buttons
 * follow the controller style: in the Xbox style the printed X sends `BUTTON_Y`), the D-pad as hat 0 (`h0up`, the pad
 * sends no D-pad keys), and L2/R2 by key code and by axis, so a trigger works in the digital and the analog mode
 * (RetroArch numbers the left trigger axis 6, the right 7, brake 8 and gas 9).
 */
object RetroArchBinds {
    /** The hotkey enable button: while it is held, the other buttons are hotkeys. */
    const val ENABLE = "input_enable_hotkey"

    /** The Thor buttons a RetroArch hotkey can use, in the order they sit on the Thor. */
    val usable: List<PadButton> = listOf(
        PadButton.A, PadButton.B, PadButton.X, PadButton.Y,
        PadButton.L1, PadButton.R1, PadButton.L2, PadButton.R2,
        PadButton.L3, PadButton.R3, PadButton.SELECT, PadButton.START,
        PadButton.DPAD_UP, PadButton.DPAD_DOWN, PadButton.DPAD_LEFT, PadButton.DPAD_RIGHT,
    )

    private val HATS = mapOf(
        PadButton.DPAD_UP to "h0up",
        PadButton.DPAD_DOWN to "h0down",
        PadButton.DPAD_LEFT to "h0left",
        PadButton.DPAD_RIGHT to "h0right",
    )
    private val TRIGGER_AXES = mapOf(PadButton.L2 to "+6", PadButton.R2 to "+7")
    private val AXES_READ = mapOf("+6" to PadButton.L2, "+8" to PadButton.L2, "+7" to PadButton.R2, "+9" to PadButton.R2)

    /** The `_btn` and `_axis` lines that bind [key] to [button], or unbind it when [button] is null. */
    fun changes(key: String, button: PadButton?, xbox: Boolean): Map<String, String> {
        if (button == null) return mapOf("${key}_btn" to RetroArchConfig.NONE, "${key}_axis" to RetroArchConfig.NONE)
        require(button in usable) { "$button can't be a RetroArch hotkey" }
        val btn = HATS[button] ?: button.keyCodeIn(xbox).toString()
        return mapOf("${key}_btn" to btn, "${key}_axis" to (TRIGGER_AXES[button] ?: RetroArchConfig.NONE))
    }

    /** What [key] is bound to in [config], read with the controller style in use now. */
    fun read(config: RetroArchConfig, key: String, xbox: Boolean): RaBinding {
        val btn = config.value("${key}_btn")?.takeUnless { it.isEmpty() || it == RetroArchConfig.NONE }
        val axis = config.value("${key}_axis")?.takeUnless { it.isEmpty() || it == RetroArchConfig.NONE }
        if (btn == null && axis == null) return RaBinding.None
        val fromButton = btn?.let { text ->
            HATS.entries.firstOrNull { it.value == text }?.key
                ?: text.toIntOrNull()?.let { code -> usable.firstOrNull { !it.secondOnly && it.keyCodeIn(xbox) == code } }
        }
        val button = fromButton ?: if (btn == null) axis?.let(AXES_READ::get) else null
        return button?.let(RaBinding::Thor) ?: RaBinding.Other(listOfNotNull(btn, axis).joinToString(" / "))
    }

    /** Thor Tools' suggestion: Select as the hotkey enable button, and each hotkey's suggested button. */
    fun suggestion(xbox: Boolean): Map<String, String> = buildMap {
        putAll(changes(ENABLE, PadButton.SELECT, xbox))
        RaHotkey.entries.forEach { putAll(changes(it.key, it.suggested, xbox)) }
    }

    /** True when every hotkey and the enable button are as the suggestion has them. */
    fun isSuggestion(config: RetroArchConfig, xbox: Boolean): Boolean = suggestion(xbox).all { (key, value) -> config.value(key) == value }

    /**
     * Thor Tools' own hotkeys on the same buttons as a RetroArch hotkey ([enable] held, then the hotkey's button):
     * Thor Tools takes that press, so RetroArch never sees it. [thorHotkeys] are the ones that count in RetroArch.
     */
    fun clashes(enable: PadButton?, binds: Map<RaHotkey, PadButton>, thorHotkeys: List<Hotkey>): List<Pair<RaHotkey, Hotkey>> {
        if (enable == null) return emptyList()
        return binds.flatMap { (hotkey, button) ->
            thorHotkeys.filter { it.button == enable && it.second == button }.map { hotkey to it }
        }
    }
}
