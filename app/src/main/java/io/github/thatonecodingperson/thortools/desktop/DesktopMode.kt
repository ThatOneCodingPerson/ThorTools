package io.github.thatonecodingperson.thortools.desktop

import io.github.thatonecodingperson.thortools.hotkeys.PadButton

/**
 * Desktop controls' switch, app lists and layout, as stored. [offOnFrontEnds]: off while the top screen shows a home
 * screen or a game front end.
 */
data class DesktopSettings(val enabled: Boolean, val apps: DesktopApps, val layout: DesktopLayout, val offOnFrontEnds: Boolean = true)

/**
 * What desktop controls look at: [controllerOnTop] when the controller (Android's focused display) is on the top screen;
 * the apps on the two screens (null: a home screen or nothing); [bottomShown] when there is a bottom screen and it is
 * on; [topLocked] when the controller is locked to the top screen.
 */
data class DesktopSituation(
    val controllerOnTop: Boolean,
    val topApp: String?,
    val bottomApp: String?,
    val bottomShown: Boolean,
    val panelOpen: Boolean = false,
    val screenOn: Boolean = true,
    val topLocked: Boolean = false,
)

/** [on]: desktop controls work now; [split]: they keep the controller on the bottom screen (also while the panel is open). */
data class DesktopDecision(val on: Boolean, val split: Boolean) {
    companion object {
        val OFF = DesktopDecision(on = false, split = false)
    }
}

/**
 * Desktop controls in the service: Thor Tools' own pointer (run by the root helper, which [send] talks to) is on while
 * the screen is on, the controller is on the top screen (where Android shows a mouse pointer), the quick panel is closed
 * and that screen's app may have desktop controls.
 *
 * Hotkeys come first. The service hands over only the button presses the hotkeys passed on ([key]); a button that starts
 * a hotkey combo does its job when it is let go, and only if no combo used it ([comboRan]). While a button that may
 * start a combo is held, or the hotkey editor records, the helper keeps the sticks and triggers still ([hold]). Main
 * thread.
 */
class DesktopMode(private val send: (String) -> Unit) {
    private var settings = DesktopSettings(false, DesktopApps(), DesktopLayout.DEFAULT)

    /** Desktop controls should be on now. */
    var engaged = false
        private set

    /** The helper said its pointer is up and following the pad. */
    var ready = false
        private set

    /** Switched off by holding Start, until held again; the helper says so. */
    var paused = false

    /** The controller is kept on the bottom screen while the pointer works the top one ([DesktopLayout.bottomScreen]). */
    var split = false
        private set

    private var holding = false

    /** Times in a row the helper couldn't bring its devices up; a few tries, then it waits for the next change. */
    private var failures = 0

    /** On, off and stop requests still to be answered; the helper answers in order, and only the newest answer counts. */
    private var answersDue = 0
    private var startsHotkeys = emptySet<DesktopControl>()

    /** Buttons whose press went to the helper and whose release must follow. */
    private val pressed = mutableSetOf<DesktopControl>()

    /** Buttons that start hotkeys, held: true once a combo used one, so letting go does nothing. */
    private val waiting = mutableMapOf<DesktopControl, Boolean>()

    /** Buttons taken from the app whose release must not reach it either. */
    private val swallowUp = mutableSetOf<DesktopControl>()

    /** Start pressed while paused: the app's, but the helper hears it too, so holding it switches them back on. */
    private var startWatched = false

    val active: Boolean get() = settings.enabled

    val layout: DesktopLayout get() = settings.layout

    fun decide(situation: DesktopSituation): DesktopDecision = decide(settings, situation)

    /** See [Companion.inUse]. */
    fun inUse(situation: DesktopSituation): Boolean = inUse(settings, situation)

    fun configure(settings: DesktopSettings) {
        val was = this.settings
        this.settings = settings
        if (!settings.enabled) {
            if (was.enabled) ask("stop")
            engaged = false
            ready = false
            paused = false
            split = false
            dropKeys()
            return
        }
        if (!was.enabled || settings.layout != was.layout) send("config ${settings.layout.encode()}")
        if (!was.enabled) {
            sendStarts()
            if (holding) send("hold 1")
            if (split) send("split 1")
        }
    }

    /** [app] is on the screen that has the controller (null: a home screen), [topScreen] when that screen is the top one. */
    fun update(app: String?, panelOpen: Boolean, topScreen: Boolean, screenOn: Boolean = true) = update(
        decide(settings, DesktopSituation(topScreen, app, bottomApp = null, bottomShown = false, panelOpen, screenOn)),
    )

    fun update(decision: DesktopDecision) {
        if (decision.split != split) {
            // Keys go another way with the controller on the bottom screen: whatever is held lets go first.
            split = decision.split
            if (settings.enabled) send(if (split) "split 1" else "split 0")
        }
        if (decision.on == engaged) return
        engaged = decision.on
        if (!engaged) {
            ready = false
            dropKeys()
        }
        ask(if (engaged) "on" else "off")
    }

    /**
     * The helper's answer to on and off, and its report when its devices fail. Returns true when desktop controls should
     * be on but the helper couldn't bring them up: the service looks again a moment later, which sends "on" again.
     */
    fun onReady(on: Boolean): Boolean {
        // The answer to an older request: the newest one's is still on its way.
        if (answersDue > 0 && --answersDue > 0) return false
        ready = on && engaged
        if (on) {
            failures = 0
            return false
        }
        dropKeys()
        if (!engaged) return false
        // Not engaged any more as far as the helper knows, so the next check sends "on" again.
        engaged = false
        return ++failures <= MAX_FAILURES
    }

    /** The helper went away: until it is back (and [resend] ran), nothing is taken. */
    fun helperGone() {
        ready = false
        answersDue = 0
        dropKeys()
    }

    /** The pad these presses came from went away: their releases never come, so the helper lets go of them now. */
    fun padGone() {
        pressed.forEach { send("key ${it.id} 0") }
        pressed.clear()
        waiting.clear()
        unwatchStart()
    }

    /** After the helper started again: it knows nothing of desktop controls, and whatever it held is gone. */
    fun resend() {
        ready = false
        paused = false
        answersDue = 0
        dropKeys()
        if (!settings.enabled) return
        send("config ${settings.layout.encode()}")
        sendStarts()
        if (holding) send("hold 1")
        if (split) send("split 1")
        if (engaged) ask("on")
    }

    /** While true, the helper keeps the sticks and triggers still: a hotkey may be under way. */
    fun hold(on: Boolean) {
        if (on == holding) return
        holding = on
        if (settings.enabled) send(if (on) "hold 1" else "hold 0")
    }

    /** The buttons that start hotkey combos now; the helper leaves such a trigger to [key] instead of its axis. */
    fun startsHotkeys(controls: Set<DesktopControl>) {
        if (controls == startsHotkeys) return
        startsHotkeys = controls
        if (settings.enabled) sendStarts()
    }

    /** Whether desktop controls take [control]'s presses from the app now: none while paused. */
    fun takes(control: DesktopControl): Boolean = engaged && ready && !paused && control in settings.layout.takenFor(split)

    /** Paused by holding Start: holding it again switches them back on, so the helper hears Start (the app gets it too). */
    private fun watchesStart(control: DesktopControl): Boolean =
        control == DesktopControl.START && engaged && ready && paused && settings.layout.holdStartSwitch

    /**
     * A key event of [control] the hotkeys passed on (and every release, so nothing stays down); [startsCombos] when the
     * button may still become a combo. Returns whether desktop controls took it from the app.
     */
    fun key(control: DesktopControl, down: Boolean, repeat: Boolean, startsCombos: Boolean, canceled: Boolean = false): Boolean {
        if (!down) {
            if (control == DesktopControl.START && startWatched) {
                unwatchStart()
                return false
            }
            if (pressed.remove(control)) {
                send("key ${control.id} 0")
                return true
            }
            waiting.remove(control)?.let { used ->
                // A canceled press (Android took it back) is no tap.
                if (!used && !canceled && takes(control)) send("tap ${control.id}")
                return true
            }
            return swallowUp.remove(control)
        }
        if (repeat) return control in pressed || control in waiting || control in swallowUp
        // A press whose release was lost (its device went away) ends here.
        if (pressed.remove(control)) send("key ${control.id} 0")
        waiting.remove(control)
        swallowUp.remove(control)
        if (control == DesktopControl.START) unwatchStart()
        if (!takes(control)) {
            if (watchesStart(control)) {
                startWatched = true
                send("key ${control.id} 1")
            }
            return false
        }
        if (startsCombos) {
            waiting[control] = false
        } else {
            pressed += control
            send("key ${control.id} 1")
        }
        return true
    }

    /** A combo ran with [first] held: if it is a button waiting for its release, letting go now does nothing. */
    fun comboRan(first: PadButton) {
        val control = DesktopControl.of(first) ?: return
        if (control in waiting) waiting[control] = true
        // Held for a combo, not to switch desktop controls back on.
        if (control == DesktopControl.START) unwatchStart()
    }

    private fun unwatchStart() {
        if (!startWatched) return
        startWatched = false
        send("key ${DesktopControl.START.id} 0")
    }

    /** The helper lets go of everything it held; the app still never sees the releases of what was taken. */
    private fun dropKeys() {
        swallowUp += pressed
        swallowUp += waiting.keys
        pressed.clear()
        waiting.clear()
    }

    private fun sendStarts() = send("starts ${startsHotkeys.joinToString(",") { it.id }}")

    private fun ask(line: String) {
        answersDue++
        send(line)
    }

    companion object {
        private const val MAX_FAILURES = 3

        fun wanted(settings: DesktopSettings, app: String?, panelOpen: Boolean, topScreen: Boolean, screenOn: Boolean = true): Boolean =
            settings.enabled && screenOn && !panelOpen && topScreen && settings.apps.worksFor(app)

        /**
         * Where desktop controls stand. They work for the top screen's app, the only screen with Android's pointer, and
         * (with [DesktopSettings.offOnFrontEnds]) not on a home screen or front end there (null: a home screen). With
         * [DesktopLayout.bottomScreen] they keep the controller on the bottom screen meanwhile, unless the controller is
         * locked to the top, there is no bottom screen, or the bottom screen's app is a never-in one (a game there has
         * the controller to itself); otherwise they work only while the controller is on the top screen.
         */
        /**
         * Desktop controls are in use for the top screen's app: switched on and working there, though they may pause for
         * the quick panel or while the controller visits the bottom screen.
         */
        fun inUse(settings: DesktopSettings, situation: DesktopSituation): Boolean =
            decide(settings, situation.copy(controllerOnTop = true, panelOpen = false, screenOn = true)).on

        fun decide(settings: DesktopSettings, situation: DesktopSituation): DesktopDecision {
            if (!settings.enabled || !situation.screenOn || !settings.apps.worksFor(situation.topApp)) return DesktopDecision.OFF
            val topApp = situation.topApp
            if (settings.offOnFrontEnds && (topApp == null || DesktopApps.isFrontEnd(topApp))) return DesktopDecision.OFF
            val bottomApp = situation.bottomApp
            val split = settings.layout.bottomScreen &&
                situation.bottomShown &&
                !situation.topLocked &&
                (bottomApp == null || bottomApp !in settings.apps.neverIn)
            return DesktopDecision(on = !situation.panelOpen && (split || situation.controllerOnTop), split = split)
        }
    }
}
