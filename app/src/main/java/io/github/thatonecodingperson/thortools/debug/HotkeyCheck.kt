package io.github.thatonecodingperson.thortools.debug

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.Hotkey
import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import io.github.thatonecodingperson.thortools.hotkeys.PressKind

/** The hotkey check's categories. */
enum class HotkeyType(@StringRes val label: Int) {
    TAPS(R.string.debugTypeTaps),
    DOUBLES(R.string.debugTypeDoubles),
    TRIPLES(R.string.debugTypeTriples),
    HOLDS(R.string.debugTypeHolds),
    COMBOS(R.string.debugTypeCombos),
    DIRECTIONS(R.string.debugTypeDirections),
    ALL(R.string.debugTypeAll),
}

/** What the hotkeys made of a press, as the service reports it while a hotkey check runs. */
sealed interface HotkeyEvent {
    val time: Long

    /** A hotkey was recognised; [ran]: its action also ran. */
    data class Matched(val hotkey: Hotkey, val ran: Boolean, override val time: Long) : HotkeyEvent

    /** A press the hotkeys held back and gave back as the button's own job. */
    data class GivenBack(val button: PadButton, val presses: Int, override val time: Long) : HotkeyEvent

    /** A press no hotkey took. */
    data class Unmatched(val button: PadButton, override val time: Long) : HotkeyEvent
}

/** One expected hotkey; [seenAt] once it was recognised. A hotkey for chosen apps only works in those apps. */
data class HotkeyRow(val hotkey: Hotkey, val seenAt: Long? = null) {
    val onlyInApps: Boolean get() = hotkey.apps.isNotEmpty()
}

/** The hotkey check's rules. Pure. */
object HotkeyCheck {
    fun belongs(type: HotkeyType, hotkey: Hotkey): Boolean {
        val single = hotkey.second == null
        return when (type) {
            HotkeyType.TAPS -> single && hotkey.press == PressKind.TAP
            HotkeyType.DOUBLES -> single && hotkey.press == PressKind.DOUBLE
            HotkeyType.TRIPLES -> single && hotkey.press == PressKind.TRIPLE
            HotkeyType.HOLDS -> single && hotkey.press == PressKind.HOLD
            HotkeyType.COMBOS -> hotkey.second?.secondOnly == false
            HotkeyType.DIRECTIONS -> hotkey.second?.secondOnly == true
            HotkeyType.ALL -> true
        }
    }

    fun rows(type: HotkeyType, hotkeys: List<Hotkey>): List<HotkeyRow> = hotkeys.filter { belongs(type, it) }.map(::HotkeyRow)

    /** The rows with the one [event] stands for ticked (the first not yet seen, else the first that fits). */
    fun record(rows: List<HotkeyRow>, event: HotkeyEvent): List<HotkeyRow> {
        val fits = rows.indices.filter { fits(rows[it].hotkey, event) }
        val index = fits.firstOrNull { rows[it].seenAt == null } ?: fits.firstOrNull() ?: return rows
        return rows.mapIndexed { i, row -> if (i == index) row.copy(seenAt = event.time) else row }
    }

    /** Whether [event] is a press of [hotkey]: recognised as it, or, for a button's own job, given back as one press. */
    fun fits(hotkey: Hotkey, event: HotkeyEvent): Boolean = when (event) {
        is HotkeyEvent.Matched -> hotkey.sameTrigger(event.hotkey) && hotkey.action == event.hotkey.action
        is HotkeyEvent.GivenBack -> hotkey.isOwnJob && hotkey.button == event.button && event.presses == 1
        is HotkeyEvent.Unmatched -> false
    }

    /** Recognised and expected, leaving out hotkeys for chosen apps (they don't work on the debug screen). */
    fun summary(rows: List<HotkeyRow>): Pair<Int, Int> {
        val counted = rows.filterNot { it.onlyInApps }
        return counted.count { it.seenAt != null } to counted.size
    }

    /** "double home", "hold home + tap r1": ids, for the results file. */
    fun describe(hotkey: Hotkey): String = hotkey.second?.let { "hold ${hotkey.button.id} + ${hotkey.press.id} ${it.id}" }
        ?: "${hotkey.press.id} ${hotkey.button.id}"

    fun describe(event: HotkeyEvent): String = when (event) {
        is HotkeyEvent.Matched -> "${describe(event.hotkey)} -> ${event.hotkey.action.id}" + if (event.ran) " (ran)" else " (not run)"
        is HotkeyEvent.GivenBack -> "${event.presses}x ${event.button.id} -> given back as the button's own job"
        is HotkeyEvent.Unmatched -> "${event.button.id} -> no hotkey"
    }
}
