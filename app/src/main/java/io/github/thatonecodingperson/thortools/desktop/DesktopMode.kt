package io.github.thatonecodingperson.thortools.desktop

import io.github.thatonecodingperson.thortools.hotkeys.PadButton

/** Desktop controls' switch, app lists and layout, as stored. */
data class DesktopSettings(val enabled: Boolean, val apps: DesktopApps, val layout: DesktopLayout)

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

    private var holding = false
    private var startsHotkeys = emptySet<DesktopControl>()

    /** Buttons whose press went to the helper and whose release must follow. */
    private val pressed = mutableSetOf<DesktopControl>()

    /** Buttons that start hotkeys, held: true once a combo used one, so letting go does nothing. */
    private val waiting = mutableMapOf<DesktopControl, Boolean>()

    /** Buttons taken from the app whose release must not reach it either. */
    private val swallowUp = mutableSetOf<DesktopControl>()

    val active: Boolean get() = settings.enabled

    fun configure(settings: DesktopSettings) {
        val was = this.settings
        this.settings = settings
        if (!settings.enabled) {
            if (was.enabled) send("stop")
            engaged = false
            ready = false
            paused = false
            dropKeys()
            return
        }
        if (!was.enabled || settings.layout != was.layout) send("config ${settings.layout.encode()}")
        if (!was.enabled) {
            sendStarts()
            if (holding) send("hold 1")
        }
    }

    /** [app] is on the screen that has the controller (null: a home screen), [topScreen] when that screen is the top one. */
    fun update(app: String?, panelOpen: Boolean, topScreen: Boolean, screenOn: Boolean = true) {
        val want = wanted(settings, app, panelOpen, topScreen, screenOn)
        if (want == engaged) return
        engaged = want
        if (!want) {
            ready = false
            dropKeys()
        }
        send(if (want) "on" else "off")
    }

    /** The helper's answer to on and off, and its report when its devices fail. */
    fun onReady(on: Boolean) {
        ready = on && engaged
        if (!on) dropKeys()
    }

    /** After the helper started again: it knows nothing of desktop controls, and whatever it held is gone. */
    fun resend() {
        ready = false
        paused = false
        dropKeys()
        if (!settings.enabled) return
        send("config ${settings.layout.encode()}")
        sendStarts()
        if (holding) send("hold 1")
        if (engaged) send("on")
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

    /** Whether desktop controls want [control]'s presses now: while paused, only Start, to switch back on. */
    fun takes(control: DesktopControl): Boolean {
        if (!engaged || !ready) return false
        val layout = settings.layout
        if (paused) return control == DesktopControl.START && layout.holdStartSwitch
        return control in layout.taken
    }

    /**
     * A key event of [control] the hotkeys passed on (and every release, so nothing stays down); [startsCombos] when the
     * button may still become a combo. Returns whether desktop controls took it from the app.
     */
    fun key(control: DesktopControl, down: Boolean, repeat: Boolean, startsCombos: Boolean): Boolean {
        if (!down) {
            if (pressed.remove(control)) {
                send("key ${control.id} 0")
                return true
            }
            waiting.remove(control)?.let { used ->
                if (!used && takes(control)) send("tap ${control.id}")
                return true
            }
            return swallowUp.remove(control)
        }
        if (repeat) return control in pressed || control in waiting || control in swallowUp
        // A press whose release was lost (its device went away) ends here.
        if (pressed.remove(control)) send("key ${control.id} 0")
        waiting.remove(control)
        swallowUp.remove(control)
        if (!takes(control)) return false
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
    }

    /** The helper lets go of everything it held; the app still never sees the releases of what was taken. */
    private fun dropKeys() {
        swallowUp += pressed
        swallowUp += waiting.keys
        pressed.clear()
        waiting.clear()
    }

    private fun sendStarts() = send("starts ${startsHotkeys.joinToString(",") { it.id }}")

    companion object {
        fun wanted(settings: DesktopSettings, app: String?, panelOpen: Boolean, topScreen: Boolean, screenOn: Boolean = true): Boolean =
            settings.enabled && screenOn && !panelOpen && topScreen && settings.apps.worksFor(app)
    }
}
