package io.github.thatonecodingperson.thortools.hotkeys

/**
 * Turns button events into hotkeys. Pure: the service passes every key event of a [PadButton], swallows the event when
 * [Step.consume] says so, carries out [Step.effects] and calls [onTimer] at [Step.timerAt].
 */
class HotkeyRecognizer(hotkeys: List<Hotkey>, private val timing: Timing = Timing()) {

    data class Timing(val tapGapMs: Long = 300, val holdMs: Long = 1000, val aynHoldMs: Long = 600)

    sealed interface Effect {
        data class Run(val hotkey: Hotkey) : Effect

        /** [presses] of [button] that turned out not to be a hotkey, to be pressed again for the app. */
        data class GiveBack(val button: PadButton, val presses: Int) : Effect
    }

    data class Step(val consume: Boolean, val effects: List<Effect> = emptyList(), val timerAt: Long? = null)

    // A button's own job (Home tap = Home) is no reason to hold its presses back: on its own, the press stays Android's.
    private val singles: Map<PadButton, Map<PressKind, Hotkey>> = hotkeys.filter { it.second == null && !it.isOwnJob }
        .groupBy { it.button }
        .mapValues { (_, list) -> list.associateBy { it.press } }
    private val combos: Map<Pair<PadButton, PadButton>, Map<PressKind, Hotkey>> = hotkeys.filter { it.second != null }
        .groupBy { it.button to it.second!! }
        .mapValues { (_, list) -> list.associateBy { it.press } }
    private val comboFirsts = combos.keys.map { it.first }.toSet()

    /** First buttons with a D-pad or stick combo: while one is held, the joystick motion is watched. */
    private val motionFirsts = combos.keys.filter { it.second.secondOnly }.map { it.first }.toSet()

    /** The button that may start a combo, while it is down. Unless [taken], its events go to the app as well. */
    private class First(val button: PadButton, val downAt: Long, val taken: Boolean, var holdAt: Long?) {
        var comboUsed = false

        /** Another button was used meanwhile, so letting go is not a press. */
        var spoiled = false
        var holdRan = false
    }

    /** Taps of a released button, waiting [until] for one more. */
    private class Taps(val button: PadButton, val count: Int, val until: Long)

    /** The second button of a combo while the first one is held. */
    private class ComboTaps(val second: PadButton, val kinds: Map<PressKind, Hotkey>) {
        var count = 0
        var until: Long? = null
        var down = false
        var holdAt: Long? = null
    }

    private var first: First? = null

    /** A button that may start a combo is held down right now. */
    val holding: Boolean get() = first != null

    /** Some hotkey has a D-pad direction or stick flick as its second button. */
    val usesMotion: Boolean get() = motionFirsts.isNotEmpty()

    /** A taken button with D-pad or stick combos is held: the joystick motion should be watched now. */
    val wantsMotion: Boolean get() = first?.let { it.taken && it.button in motionFirsts } == true

    /** When the held button went down; tells one press from the next. */
    val heldSince: Long? get() = first?.downAt
    private var taps: Taps? = null
    private var combo: ComboTaps? = null
    private val passedDown = mutableSetOf<PadButton>()
    private val swallowUp = mutableSetOf<PadButton>()

    fun onKey(button: PadButton, down: Boolean, repeat: Boolean, time: Long, canceled: Boolean = false): Step {
        val effects = mutableListOf<Effect>()
        val consume = when {
            !down -> release(button, time, canceled, effects)
            repeat -> button !in passedDown && owns(button)
            else -> press(button, time, effects)
        }
        return Step(consume, effects, nextTimer())
    }

    fun onTimer(time: Long): Step {
        val effects = mutableListOf<Effect>()
        first?.let { held ->
            val at = held.holdAt
            if (at != null && time >= at && held.waitingForHold()) {
                held.holdAt = null
                held.holdRan = true
                singles[held.button]?.get(PressKind.HOLD)?.let { effects += Effect.Run(it) }
            }
        }
        combo?.let { run ->
            val holdAt = run.holdAt
            val until = run.until
            if (holdAt != null && time >= holdAt) {
                run.kinds[PressKind.HOLD]?.let { effects += Effect.Run(it) }
                swallowUp += run.second
                combo = null
            } else if (!run.down && until != null && time >= until) {
                endCombo(effects)
            }
        }
        taps?.let { run ->
            if (first?.button != run.button && time >= run.until) {
                taps = null
                resolveTaps(run.button, run.count, effects)
            }
        }
        return Step(consume = false, effects = effects, timerAt = nextTimer())
    }

    /**
     * A D-pad direction or stick flick ([PadButton.secondOnly]) going down or up while the joystick is watched. It only
     * counts as the second button of a combo of the held button; anything else is ignored, so it never spoils the held
     * button's own press or settles waiting taps.
     */
    fun onMotion(button: PadButton, down: Boolean, time: Long): Step {
        val effects = mutableListOf<Effect>()
        if (down) {
            swallowUp -= button
            val held = first
            if (held != null && combos.containsKey(held.button to button)) pressWhileHolding(held, button, time, effects)
        } else {
            val run = combo
            if (run != null && run.second == button && run.down) releaseSecond(run, time, effects) else swallowUp -= button
        }
        return Step(consume = false, effects = effects, timerAt = nextTimer())
    }

    /**
     * A held button's release may never come: AYN re-creates its pad on a layout switch and on sleep, and the new pad
     * never had the key down. Forgets what is held, so no later press counts as its combo; a release that does come is
     * still swallowed (Android takes a lone Home release for a Home press). Waiting taps stay.
     */
    fun forgetHeld() {
        swallowUp += heldReleases()
        first = null
        combo = null
    }

    /** Releases still to be swallowed, for the recognizer that replaces this one (the hotkey list changed). */
    fun releasesToSwallow(): Set<PadButton> = swallowUp + heldReleases()

    fun swallowReleases(buttons: Set<PadButton>) {
        swallowUp += buttons
    }

    private fun heldReleases(): Set<PadButton> = setOfNotNull(first?.takeIf { it.taken }?.button, combo?.takeIf { it.down }?.second)

    /** Whether a repeat of [button] belongs to a press we swallowed. */
    private fun owns(button: PadButton): Boolean {
        val held = first
        return (held != null && held.button == button && held.taken) ||
            (combo?.let { it.second == button && it.down } == true) ||
            button in swallowUp
    }

    private fun press(button: PadButton, time: Long, effects: MutableList<Effect>): Boolean {
        // A press whose release never came (AYN re-creates its pad on a layout switch) starts over.
        passedDown -= button
        swallowUp -= button
        if (first?.button == button) {
            first = null
            combo = null
        }

        val held = first
        if (held != null) return pressWhileHolding(held, button, time, effects)

        taps?.let { pending ->
            if (pending.button != button) {
                taps = null
                resolveTaps(pending.button, pending.count, effects)
            }
        }
        // Back pressed while a game's own button is held (RetroArch's Select + Back) belongs to the game.
        if (passedDown.any { it.gamepad } && button != PadButton.HOME && button != PadButton.AYN) {
            passedDown += button
            return false
        }

        val taken = takes(button)
        if (!taken && button !in comboFirsts) {
            passedDown += button
            return false
        }
        val startsSequence = taps?.button != button
        val holdBound = singles[button]?.containsKey(PressKind.HOLD) == true
        first = First(button, time, taken, holdAt = if (taken && startsSequence && holdBound) time + holdMs(button) else null)
        if (!taken) passedDown += button
        return taken
    }

    private fun pressWhileHolding(held: First, button: PadButton, time: Long, effects: MutableList<Effect>): Boolean {
        val kinds = combos[held.button to button]
        if (kinds == null) {
            held.spoiled = true
            // Home and Back together are never a hotkey; neither reaches the app.
            if (held.taken && setOf(held.button, button) == setOf(PadButton.HOME, PadButton.BACK)) {
                swallowUp += button
                return true
            }
            passedDown += button
            return false
        }
        held.comboUsed = true
        val current = combo
        if (current != null && current.second != button) endCombo(effects)
        if (kinds.keys == setOf(PressKind.TAP)) {
            // Nothing else is bound for this pair, so there is nothing to wait for.
            effects += Effect.Run(kinds.getValue(PressKind.TAP))
            swallowUp += button
            return true
        }
        val run = combo ?: ComboTaps(button, kinds).also { combo = it }
        run.down = true
        run.until = null
        run.holdAt = if (run.count == 0 && PressKind.HOLD in kinds) time + holdMs(button) else null
        return true
    }

    private fun release(button: PadButton, time: Long, canceled: Boolean, effects: MutableList<Effect>): Boolean {
        val held = first
        if (button in passedDown) {
            passedDown -= button
            if (held != null && held.button == button) letGoOfFirst(held, time, canceled, effects)
            return false
        }
        if (held != null && held.button == button) {
            letGoOfFirst(held, time, canceled, effects)
            return true
        }
        val run = combo
        if (run != null && run.second == button && run.down) {
            releaseSecond(run, time, effects)
            return true
        }
        return swallowUp.remove(button)
    }

    private fun letGoOfFirst(held: First, time: Long, canceled: Boolean, effects: MutableList<Effect>) {
        first = null
        combo?.let { run -> if (run.down) swallowUp += run.second }
        endCombo(effects)
        val looked = time - held.downAt >= holdMs(held.button) && held.button in comboFirsts
        // Used for a combo, spoiled, already held, or held just to read its combos: letting go does nothing.
        if (!held.taken || held.comboUsed || held.spoiled || canceled || held.holdRan || looked) {
            taps = null
            return
        }
        val count = (taps?.takeIf { it.button == held.button }?.count ?: 0) + 1
        if (count >= maxTaps(singles[held.button])) {
            taps = null
            resolveTaps(held.button, count, effects)
        } else {
            taps = Taps(held.button, count, time + timing.tapGapMs)
        }
    }

    private fun releaseSecond(run: ComboTaps, time: Long, effects: MutableList<Effect>) {
        run.down = false
        run.holdAt = null
        run.count++
        if (run.count >= maxTaps(run.kinds)) endCombo(effects) else run.until = time + timing.tapGapMs
    }

    /** Runs the combo the counted taps make, if one is bound; a combo's second button is never given back. */
    private fun endCombo(effects: MutableList<Effect>) {
        val run = combo ?: return
        combo = null
        PressKind.forTaps(run.count)?.let { run.kinds[it] }?.let { effects += Effect.Run(it) }
    }

    private fun resolveTaps(button: PadButton, count: Int, effects: MutableList<Effect>) {
        val hotkey = PressKind.forTaps(count)?.let { singles[button]?.get(it) }
        effects += if (hotkey != null) Effect.Run(hotkey) else Effect.GiveBack(button, count)
    }

    private fun First.waitingForHold() = !comboUsed && !spoiled && !holdRan

    /** Buttons with a press of their own are ours; Home, Back, AYN and volume also when they only start combos. */
    fun takes(button: PadButton) = button in singles || (button in comboFirsts && !button.gamepad)

    private fun maxTaps(kinds: Map<PressKind, Hotkey>?): Int = kinds?.keys?.mapNotNull { it.taps }?.maxOrNull() ?: 0

    /** AYN's drawer waits less, unless AYN starts combos and needs the time to show them. */
    private fun holdMs(button: PadButton) = if (button == PadButton.AYN && button !in comboFirsts) timing.aynHoldMs else timing.holdMs

    private fun nextTimer(): Long? {
        val held = first
        val run = combo
        return listOfNotNull(
            held?.takeIf { it.waitingForHold() }?.holdAt,
            run?.holdAt,
            run?.takeIf { !it.down }?.until,
            taps?.takeIf { it.button != held?.button }?.until,
        ).minOrNull()
    }
}
