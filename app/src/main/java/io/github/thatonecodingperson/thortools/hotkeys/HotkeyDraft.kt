package io.github.thatonecodingperson.thortools.hotkeys

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.ThorAction

/** The editor's two button slots: the button (the held one, in a combo), and the one pressed while holding it. */
enum class DraftSlot { FIRST, SECOND }

/**
 * The editor's press choices: the four presses of one button, or holding it and pressing another. In a combo the first
 * button is only held; tap, double, triple or hold is the second button's.
 */
enum class PressChoice(val single: PressKind?, @StringRes val label: Int) {
    TAP(PressKind.TAP, R.string.pressTapTitle),
    DOUBLE(PressKind.DOUBLE, R.string.pressDoubleTitle),
    TRIPLE(PressKind.TRIPLE, R.string.pressTripleTitle),
    HOLD(PressKind.HOLD, R.string.pressHoldTitle),
    COMBO(null, R.string.hotkeyPressCombo),
}

/**
 * A hotkey being made ([editing] null) or changed, for one [action]. [press] is a single button's press; with [combo],
 * [secondPress] is the second button's. [second] is kept when switching away from a combo, so switching back restores
 * it. Pure: the view model only calls these transitions.
 */
data class HotkeyDraft(
    val editing: Hotkey?,
    val action: ThorAction,
    val arg: String? = null,
    val button: PadButton? = null,
    val second: PadButton? = null,
    val press: PressKind = PressKind.TAP,
    val combo: Boolean = false,
    val secondPress: PressKind = PressKind.TAP,
    val slot: DraftSlot = DraftSlot.FIRST,
    val showText: Boolean = true,
    val lock: Boolean = false,
    val cleanMemory: Boolean = false,
    val apps: Set<String> = emptySet(),
) {
    val choice: PressChoice get() = if (combo) PressChoice.COMBO else PressChoice.entries.first { it.single == press }

    /** The second button as the hotkey sees it: only in a combo. */
    val comboSecond: PadButton? get() = second.takeIf { combo }

    /** The press the hotkey reacts to: the second button's in a combo. */
    val effectivePress: PressKind get() = if (combo) secondPress else press

    val allowed: Boolean get() = button != null && (!combo || second != null) && Hotkey.allowed(button, comboSecond)

    fun toHotkey(): Hotkey? {
        val first = button?.takeIf { allowed } ?: return null
        return Hotkey(
            button = first,
            second = comboSecond,
            press = effectivePress,
            action = action,
            arg = arg,
            showText = showText,
            lock = lock && Hotkey.canLock(action),
            cleanMemory = cleanMemory && Hotkey.canCleanMemory(action),
            apps = apps,
        )
    }

    /** A combo opens the empty second slot right away; a single press goes back to the first slot. */
    fun withChoice(choice: PressChoice): HotkeyDraft {
        val single = choice.single ?: return copy(combo = true, slot = if (button != null && second == null) DraftSlot.SECOND else slot)
        return copy(combo = false, press = single, slot = DraftSlot.FIRST)
    }

    /** What was pressed on the Thor: two buttons are a combo, one is a single press. */
    fun withRecorded(first: PadButton, recordedSecond: PadButton?): HotkeyDraft =
        copy(button = first, second = recordedSecond ?: second, combo = recordedSecond != null, slot = DraftSlot.FIRST)

    companion object {
        fun of(hotkey: Hotkey): HotkeyDraft {
            val combo = hotkey.second != null
            return HotkeyDraft(
                editing = hotkey,
                action = hotkey.action,
                arg = hotkey.arg,
                button = hotkey.button,
                second = hotkey.second,
                press = if (combo) PressKind.TAP else hotkey.press,
                combo = combo,
                secondPress = if (combo) hotkey.press else PressKind.TAP,
                showText = hotkey.showText,
                lock = hotkey.lock,
                cleanMemory = hotkey.cleanMemory,
                apps = hotkey.apps,
            )
        }
    }
}
