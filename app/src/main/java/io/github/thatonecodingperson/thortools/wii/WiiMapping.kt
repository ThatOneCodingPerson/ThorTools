package io.github.thatonecodingperson.thortools.wii

import io.github.thatonecodingperson.thortools.hotkeys.PadButton

/**
 * One tab's mapping: the Thor button for each Wii button, the Thor sticks for each Wii stick, D-pad, pointer or motion,
 * and the held and pressed buttons of each hotkey (none for a control left out). [rumble] uses the Thor's vibration;
 * [relativePointer] keeps the pointer where a stick leaves it instead of springing back to the middle.
 */
data class WiiMapping(
    val setup: WiiSetup,
    val buttons: Map<WiiControl, PadButton> = setup.suggestedButtons,
    val sticks: Map<WiiControl, Set<StickSource>> = setup.suggestedSticks,
    val combos: Map<WiiControl, Pair<PadButton, PadButton>> = setup.suggestedCombos,
    val rumble: Boolean = true,
    val relativePointer: Boolean = true,
) {
    fun withButton(control: WiiControl, button: PadButton?): WiiMapping =
        copy(buttons = if (button == null) buttons - control else buttons + (control to button))

    fun withSticks(control: WiiControl, sources: Set<StickSource>): WiiMapping =
        copy(sticks = if (sources.isEmpty()) sticks - control else sticks + (control to sources))

    /**
     * [held] then [pressed]. A null part keeps what the hotkey has (or the suggestion's), so one part can be changed at a
     * time; both null leave the hotkey out.
     */
    fun withCombo(control: WiiControl, held: PadButton?, pressed: PadButton?): WiiMapping {
        if (held == null && pressed == null) return copy(combos = combos - control)
        val fallback = combos[control] ?: setup.suggestedCombos[control]
        val first = held ?: fallback?.first ?: pressed!!
        val second = pressed ?: fallback?.second ?: held!!
        return copy(combos = combos + (control to (first to second)))
    }

    /** Whether [control] is mapped as the suggestion has it. */
    fun isSuggested(control: WiiControl): Boolean = when (control.kind) {
        WiiKind.BUTTON -> buttons[control] == setup.suggestedButtons[control]
        WiiKind.STICK -> sticks[control].orEmpty() == setup.suggestedSticks[control].orEmpty()
        WiiKind.COMBO -> combos[control] == setup.suggestedCombos[control]
    }

    fun suggested(control: WiiControl): WiiMapping = when (control.kind) {
        WiiKind.BUTTON -> withButton(control, setup.suggestedButtons[control])
        WiiKind.STICK -> withSticks(control, setup.suggestedSticks[control].orEmpty())
        WiiKind.COMBO -> setup.suggestedCombos[control]?.let { (held, pressed) -> withCombo(control, held, pressed) }
            ?: withCombo(control, null, null)
    }

    /**
     * The Thor buttons [control] takes on its own; a stick counts as its four directions. A hotkey is left out: it is
     * made of buttons that do something else alone.
     */
    fun thorButtons(control: WiiControl): Set<PadButton> = when (control.kind) {
        WiiKind.BUTTON -> setOfNotNull(buttons[control])
        WiiKind.STICK -> sticks[control].orEmpty().flatMap { it.directions }.toSet()
        WiiKind.COMBO -> emptySet()
    }

    /** The other controls on this tab that share a Thor button with [control]. Allowed, but worth a note. */
    fun sharing(control: WiiControl): List<WiiControl> {
        val mine = thorButtons(control)
        if (mine.isEmpty()) return emptyList()
        return setup.controls.filter { it != control && thorButtons(it).any(mine::contains) }
    }

    /** A hotkey whose held and pressed buttons are the same is no hotkey. */
    fun brokenCombo(control: WiiControl): Boolean = combos[control]?.let { it.first == it.second } == true

    /** The controls the suggestion maps but this mapping doesn't write: Dolphin would show them blank. */
    fun leftOut(): List<WiiControl> = setup.controls.filter { control ->
        when (control.kind) {
            WiiKind.BUTTON -> control in setup.suggestedButtons && control !in buttons
            WiiKind.STICK -> control in setup.suggestedSticks && sticks[control].isNullOrEmpty()
            WiiKind.COMBO -> control in setup.suggestedCombos && (control !in combos || brokenCombo(control))
        }
    }

    fun encode(): String = (
        setup.controls.mapNotNull { control ->
            when (control.kind) {
                WiiKind.BUTTON -> buttons[control]?.let { "${control.id}=${it.id}" }
                WiiKind.STICK -> sticks[control]?.takeIf { it.isNotEmpty() }?.let { set ->
                    "${control.id}=${StickSource.entries.filter(set::contains).joinToString("+") { it.id }}"
                }
                WiiKind.COMBO -> combos[control]?.let { (held, pressed) -> "${control.id}=${held.id}+${pressed.id}" }
            }
        } + listOf("$RUMBLE=${flag(rumble)}", "$RELATIVE=${flag(relativePointer)}")
        ).joinToString(";")

    companion object {
        private const val RUMBLE = "rumble"
        private const val RELATIVE = "relative"

        private fun flag(on: Boolean) = if (on) "1" else "0"

        /** A stored mapping for [setup]; nothing stored gives the suggestion, unknown parts are left out. */
        fun decode(setup: WiiSetup, text: String?): WiiMapping {
            if (text.isNullOrBlank()) return WiiMapping(setup)
            val fields = text.split(';').mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (key, value) -> key to value }
            val buttons = mutableMapOf<WiiControl, PadButton>()
            val sticks = mutableMapOf<WiiControl, Set<StickSource>>()
            val combos = mutableMapOf<WiiControl, Pair<PadButton, PadButton>>()
            setup.controls.forEach { control ->
                val value = fields[control.id] ?: return@forEach
                when (control.kind) {
                    WiiKind.BUTTON -> PadButton.entries.find { it.id == value && it in WII_BUTTONS }?.let { buttons[control] = it }
                    WiiKind.STICK -> value.split('+').mapNotNull(StickSource::byId).toSet().takeIf { it.isNotEmpty() }
                        ?.let { sticks[control] = it }
                    WiiKind.COMBO -> value.split('+').map { id -> PadButton.entries.find { it.id == id && it in WII_BUTTONS } }
                        .takeIf { it.size == 2 && it.all { button -> button != null } }
                        ?.let { combos[control] = it[0]!! to it[1]!! }
                }
            }
            return WiiMapping(
                setup = setup,
                buttons = buttons,
                sticks = sticks,
                combos = combos,
                rumble = fields[RUMBLE] != "0",
                relativePointer = fields[RELATIVE] != "0",
            )
        }
    }
}
