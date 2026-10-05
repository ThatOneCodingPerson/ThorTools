package io.github.thatonecodingperson.thortools.service

import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import io.github.thatonecodingperson.thortools.input.RawInputClient
import javax.inject.Inject
import javax.inject.Singleton

/** What the accessibility service is doing right now, for the app's screens and the diagnostics report. */
@Singleton
class ServiceStatus @Inject constructor() {
    /** The live service instance. Android can start a new one before the old one is destroyed. */
    @Volatile
    private var owner: Any? = null

    val connected: Boolean get() = owner != null

    internal fun attach(service: Any) {
        owner = service
    }

    internal fun detach(service: Any) {
        if (owner !== service) return
        owner = null
        rawInput = null
        toggleQuickPanel = null
        onAccessChanged = null
    }

    @Volatile
    var keysSeen = 0
        internal set

    @Volatile
    var rawInput: RawInputClient? = null
        internal set

    /** Called after the user fixed a permission, so the service can show what it couldn't before. */
    @Volatile
    var onAccessChanged: (() -> Unit)? = null
        internal set

    /** Opens or closes the quick panel from the app's settings; null while the service isn't running. */
    @Volatile
    var toggleQuickPanel: (() -> Unit)? = null
        internal set

    /**
     * Set while the hotkey editor records buttons: it gets every button press (true) and release (false) on the main
     * thread, and the presses go nowhere else.
     */
    @Volatile
    var buttonRecorder: ((PadButton, Boolean) -> Unit)? = null

    /** The display Thor Tools' own screen (MainActivity) is showing on; null while it isn't in front. */
    @Volatile
    var appShownOn: Int? = null
}
