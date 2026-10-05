package io.github.thatonecodingperson.thortools.leds

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import java.util.concurrent.Executors

/**
 * Keeps the stick lights as chosen, for the accessibility service. A fixed look (a colour, off, the battery colour) goes
 * through AYN's own light settings, which AYN's settings app draws and keeps after a restart; an animated look runs in
 * the root input helper. AYN's settings as they were before Thor Tools first changed them are saved, and "AYN's own"
 * puts them back. A profile's look takes the place of the chosen one while its app is in front. All work runs on one
 * background thread; calls come from the main thread.
 */
class LedController(
    private val context: Context,
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
    private val helper: (name: String, args: List<String>, onResult: (Boolean, String) -> Unit) -> Boolean,
) {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "lights").apply { isDaemon = true } }

    @Volatile
    private var chosen = prefs.ledLook

    @Volatile
    private var forApp: LedLook? = null

    @Volatile
    private var battery = 50

    @Volatile
    private var charging = false

    /** What the helper runs now, so an unchanged look isn't restarted on every battery or app change. */
    @Volatile
    private var running: String? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val newBattery = if (level < 0 || scale <= 0) battery else level * 100 / scale
            val newCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            if (newBattery == battery && newCharging == charging) return
            battery = newBattery
            charging = newCharging
            val look = forApp ?: chosen
            if (look.mode == LedMode.BATTERY || look.chargingBreathe) apply()
        }
    }

    fun start() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        runCatching { context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED) }
        apply()
    }

    fun stop() {
        runCatching { context.unregisterReceiver(batteryReceiver) }
        // An animation can't run without the service: AYN's own look takes its place until the service is back.
        runCatching {
            worker.execute {
                runCatching { if (stopEffect()) (restoreAyn() ?: aynNow())?.let(::drawOnce) }
            }
        }
        worker.shutdown()
    }

    /** The look chosen on the lights screen. */
    fun setChosen(look: LedLook) {
        chosen = look
        apply()
    }

    /** A profile's look while its app is in front; null when it has none. */
    fun setForApp(look: LedLook?) {
        if (look == forApp) return
        forApp = look
        apply()
    }

    /** The helper came up: an animated look that couldn't start before can now. */
    fun onHelperConnected() {
        running = null
        apply()
    }

    private fun apply() {
        runCatching { worker.execute { runCatching { show(forApp ?: chosen) } } }
    }

    private fun show(look: LedLook) {
        val frame = LedPlan.frame(look, 0, battery, charging)
        if (frame == null) {
            val wasRunning = stopEffect()
            val restored = restoreAyn()
            // AYN only redraws when one of its settings changes, which may not happen here: after an animation its own
            // look is drawn once.
            if (wasRunning) (restored ?: aynNow())?.let(::drawOnce)
            return
        }
        saveAyn()
        if (LedPlan.animated(look, charging, battery)) {
            // Animated frames don't depend on the level, only on whether the battery is full (then it isn't animated).
            val key = "${look.encode()}|$charging"
            if (key == running) return
            // AYN redraws its own look when the screen comes on and whenever its settings change. At brightness 0 that is
            // black with both rings switched on, so it never shows between the animation's frames.
            put(KEY_ENABLED, ENABLED_BOTH)
            put(KEY_BRIGHTNESS, DARK)
            val args = listOf(look.encode(), battery.toString(), if (charging) "1" else "0")
            if (helper("ledfx", args) { ok, _ -> if (!ok) running = null }) {
                running = key
                return
            }
            // Without the helper the look's colours stay, steady, until it is back.
            LedPlan.still(look, battery, charging)?.let(::writeAyn)
            return
        }
        val wasRunning = stopEffect()
        writeAyn(frame)
        if (wasRunning) drawOnce(frame)
    }

    /** Stops an animation; true when one was running. */
    private fun stopEffect(): Boolean {
        if (running == null) return false
        helper("ledfx", listOf("stop")) { _, _ -> }
        running = null
        return true
    }

    private fun drawOnce(frame: LedFrame) {
        helper("led", listOf(LedPlan.hex(frame.left), LedPlan.hex(frame.right), frame.brightness.toString())) { _, _ -> }
    }

    /** A fixed frame through AYN's settings; AYN's settings app draws it at once and again after a restart. */
    private fun writeAyn(frame: LedFrame) {
        put(KEY_ENABLED, ENABLED_BOTH)
        put(KEY_COLOURS, LedPlan.aynColours(frame.left, frame.right))
        put(KEY_BRIGHTNESS, LedPlan.decimal(frame.brightness / 255f))
    }

    private fun saveAyn() {
        if (prefs.ledAynSaved != null) return
        val current = listOf(read(KEY_ENABLED), read(KEY_COLOURS), read(KEY_BRIGHTNESS))
        prefs.ledAynSaved = current.joinToString(SAVED_SEPARATOR) { it.orEmpty() }
    }

    /** Puts AYN's own settings back; the look they make, or null when nothing was saved. */
    private fun restoreAyn(): LedFrame? {
        val saved = prefs.ledAynSaved?.split(SAVED_SEPARATOR) ?: return null
        listOf(KEY_ENABLED, KEY_COLOURS, KEY_BRIGHTNESS).zip(saved).forEach { (key, value) -> if (value.isNotEmpty()) put(key, value) }
        prefs.ledAynSaved = null
        return LedPlan.aynFrame(saved.getOrNull(0), saved.getOrNull(1), saved.getOrNull(2))
    }

    private fun aynNow(): LedFrame? = LedPlan.aynFrame(read(KEY_ENABLED), read(KEY_COLOURS), read(KEY_BRIGHTNESS))

    private fun read(key: String): String? = runCatching { Settings.System.getString(context.contentResolver, key) }.getOrNull()
        ?: executor.getStringSystemSetting(key, "").ifEmpty { null }

    /** Only writes what changed: each change makes AYN's settings app redraw the lights. */
    private fun put(key: String, value: String) {
        if (read(key) == value) return
        executor.executeAsRoot("settings put system $key '$value'")
    }

    private companion object {
        const val KEY_ENABLED = "joystick_light_enabled"
        const val KEY_COLOURS = "joystick_led_light_picker_color"
        const val KEY_BRIGHTNESS = "led_light_brightness_percent"
        const val ENABLED_BOTH = "1,1"
        const val DARK = "0.00"
        const val SAVED_SEPARATOR = "|"
    }
}
