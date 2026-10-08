package io.github.thatonecodingperson.thortools.panel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.input.BrightnessStep
import io.github.thatonecodingperson.thortools.input.LevelSteps
import io.github.thatonecodingperson.thortools.input.Screen
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.FanMode
import io.github.thatonecodingperson.thortools.models.L2R2Style
import io.github.thatonecodingperson.thortools.models.PerfMode
import io.github.thatonecodingperson.thortools.models.RefreshRate
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class PanelUiState(
    val battery: Int = -1,
    val charging: Boolean = false,
    @StringRes val controllerStyle: Int? = null,
    @StringRes val l2r2: Int? = null,
    @StringRes val performance: Int? = null,
    @StringRes val fan: Int? = null,
    val refreshHz: Int? = null,
    val bottomScreenOn: Boolean = true,
    val stayAwake: Boolean = false,
    /** Android's navigation swipes are meant to work. */
    val gestures: Boolean = true,
    val aynMouse: Boolean = false,
    val desktop: Boolean = false,
    /** Where the controller is locked, if anywhere. */
    val lockedTo: Screen? = null,
    val volume: Float = 0f,
    /** Slider positions as the eye sees brightness, 0..1. */
    val topBrightness: Float = 0f,
    /** Null when the bottom screen's brightness can't be set (the root helper isn't running). */
    val bottomBrightness: Float? = null,
    val stats: StatsReading = StatsReading(),
    val powerW: Float? = null,
    val batteryTemp: Float? = null,
    /** Android's estimate while charging, or the time left at today's drain otherwise. */
    val toFullMinutes: Int? = null,
    val leftMinutes: Int? = null,
    /** The performance graph's last minute. */
    val history: List<GraphPoint> = emptyList(),
    val media: NowPlaying = NowPlaying(),
    /** Name and icon of every app on an App shortcuts widget, by package. */
    val apps: Map<String, PanelApp> = emptyMap(),
    /** Every Notes widget's note, by its id. */
    val notes: Map<String, PanelNote> = emptyMap(),
    val front: FrontApp? = null,
    /** The apps opened last, newest first. */
    val recent: List<String> = emptyList(),
    /** Null while unknown. */
    val controllerOnTop: Boolean? = null,
    val timer: PanelTimer = PanelTimer(),
    val screenshots: List<Screenshot> = emptyList(),
    val screenshotsAllowed: Boolean = true,
    val storage: StorageReading? = null,
    val network: NetworkReading = NetworkReading(),
    val toggles: Map<QuickToggle, Boolean> = emptyMap(),
)

/** What the clock widget's buttons ask for. [Set]: a timer of that many minutes, or the stopwatch for null. */
sealed interface TimerCommand {
    data object Start : TimerCommand

    data object Pause : TimerCommand

    data object Reset : TimerCommand

    data class Set(val minutes: Int?) : TimerCommand
}

/** An app on an App shortcuts widget. */
data class PanelApp(val name: String, val icon: Drawable)

/** Sends a command to the root input helper; false when it isn't running. Results arrive on the main thread. */
fun interface HelperCommand {
    fun send(name: String, args: List<String>, onResult: (Boolean, String) -> Unit): Boolean
}

/** The panel's live values. Reads go through PServer or sysfs, so they run on [scope], never the main thread. */
class QuickPanelModel(
    private val context: Context,
    private val executor: ShellExecutor,
    private val scope: CoroutineScope,
    private val helper: HelperCommand,
    private val stayAwakeOn: () -> Boolean,
    private val desktopOn: () -> Boolean,
    private val gesturesOn: () -> Boolean = { true },
    private val bottomDisplay: () -> Int?,
    private val lockedTo: () -> Screen?,
    private val frontApp: () -> FrontApp? = { null },
    private val recentApps: () -> List<String> = { emptyList() },
    private val controllerDisplay: () -> Int? = { null },
    private val onTimerDone: () -> Unit = {},
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val sampler =
        StatsSampler(read = { runCatching { File(it).readText() }.getOrNull() }, list = { File(it).list()?.toList().orEmpty() })
    private val _state = MutableStateFlow(PanelUiState())
    val state: StateFlow<PanelUiState> = _state.asStateFlow()
    private val history = StatsHistory()
    private val notes = PanelNotes(context)
    private val noteSaves = mutableMapOf<String, Job>()
    private val nowPlaying = NowPlayingWatch(context) { media -> _state.update { it.copy(media = media) } }
    private val sources = WidgetSources(context, executor)
    private var timerDone: Job? = null
    private var ticks = 0L

    // Which of the newer widgets the panel shows: only those are kept up to date while it is open.
    @Volatile
    private var wanted: Set<WidgetType> = emptySet()
    private var sampling: Job? = null
    private var pendingRefresh: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Whether brightness goes through the helper (both screens) or the top screen's setting.
    @Volatile
    private var helperBrightness = false

    private val volumeWriter = LevelWriter<Int> { index -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0) }
    private val topWriter = LevelWriter<Float> { position ->
        if (helperBrightness) {
            helperCall("setbrightness", listOf(Display.DEFAULT_DISPLAY.toString(), BrightnessStep.toLinear(position).toString()))
        } else {
            val value = (position * 255).roundToInt().coerceIn(1, 255)
            executor.executeAsRoot("settings put system screen_brightness_mode 0; settings put system screen_brightness $value")
        }
    }
    private val bottomWriter = LevelWriter<Float> { position ->
        val display = bottomDisplay() ?: return@LevelWriter
        helperCall("setbrightness", listOf(display.toString(), BrightnessStep.toLinear(position).toString()))
    }

    // AYN's own buttons and other apps change these while the panel is open.
    private val settingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) = refreshCoalesced()
    }
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC || volumeWriter.recentlyTouched) return
            _state.update { it.copy(volume = musicVolume()) }
        }
    }

    fun refresh() {
        scope.launch {
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val volume = musicVolume()
            val controllerStyle = ControllerStyle.getStyle(executor).textRes
            val l2r2 = L2R2Style.getStyle(executor).textRes
            val performance = PerfMode.getMode(executor).textRes
            val fan = FanMode.getMode(executor).textRes
            val refreshHz = RefreshRate.peak(executor).roundToInt()
            val toggles = if (WidgetType.TOGGLES in wanted) sources.toggles() else null
            _state.update {
                it.copy(
                    battery = if (level < 0 || scale <= 0) -1 else level * 100 / scale,
                    charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                    controllerStyle = controllerStyle,
                    l2r2 = l2r2,
                    performance = performance,
                    fan = fan,
                    refreshHz = refreshHz,
                    bottomScreenOn = systemInt(KEY_SCREEN_MODE) != 1,
                    stayAwake = stayAwakeOn(),
                    gestures = gesturesOn(),
                    aynMouse = systemInt(KEY_AYN_MOUSE) == 1,
                    desktop = desktopOn(),
                    lockedTo = lockedTo(),
                    volume = if (volumeWriter.recentlyTouched) it.volume else volume,
                    controllerOnTop = controllerDisplay()?.let { display -> display == Display.DEFAULT_DISPLAY },
                    toggles = toggles ?: it.toggles,
                )
            }
        }
        if (!topWriter.recentlyTouched && !bottomWriter.recentlyTouched) readBrightness()
    }

    private fun systemInt(key: String): Int = runCatching { Settings.System.getInt(context.contentResolver, key, 0) }.getOrDefault(0)

    private fun musicVolume(): Float =
        audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    private fun refreshCoalesced() {
        pendingRefresh?.cancel()
        pendingRefresh = scope.launch {
            delay(CHANGE_COALESCE_MS)
            refresh()
        }
    }

    /** Re-reads after an action had time to land. */
    fun refreshSoon() {
        scope.launch {
            delay(ACTION_SETTLE_MS)
            refresh()
        }
    }

    /** The apps' names and icons, the notes and the screenshots the [layout]'s widgets show. */
    fun load(layout: PanelLayout) {
        val widgets = layout.pages.flatMap { it.widgets }
        wanted = widgets.map { it.type }.toSet()
        val recent = if (WidgetType.RECENT in wanted) recentApps() else emptyList()
        val front = frontApp()
        val packages = (
            widgets.filter {
                it.type == WidgetType.APPS
            }.flatMap { it.apps } + recent + listOfNotNull(front?.packageName)
            ).distinct()
        val noteIds = widgets.filter { it.type == WidgetType.NOTES }.map { it.note }.filter { it.isNotEmpty() }.distinct()
        _state.update { it.copy(recent = recent, front = front) }
        scope.launch {
            val apps = packages.mapNotNull(::appEntry).toMap()
            val loaded = noteIds.associateWith(notes::load)
            _state.update { it.copy(apps = it.apps + apps, notes = loaded) }
            if (WidgetType.SCREENSHOTS in wanted) loadScreenshots()
            if (WidgetType.STORAGE in wanted) _state.update { it.copy(storage = sources.storage()) }
        }
    }

    private fun appEntry(name: String): Pair<String, PanelApp>? = runCatching {
        val packageManager = context.packageManager
        val info = packageManager.getApplicationInfo(name, 0)
        name to PanelApp(packageManager.getApplicationLabel(info).toString(), packageManager.getApplicationIcon(info))
    }.getOrNull()

    private fun loadScreenshots() {
        val allowed = sources.screenshotsAllowed
        _state.update { it.copy(screenshotsAllowed = allowed, screenshots = if (allowed) sources.screenshots() else emptyList()) }
    }

    /** Root grants the photo permission, then the shelf fills. */
    fun allowScreenshots() {
        scope.launch {
            sources.allowScreenshots()
            loadScreenshots()
        }
    }

    /** A quick toggle tapped: shown switched at once, switched through PServer, then read back. */
    fun toggle(toggle: QuickToggle) {
        val on = _state.value.toggles[toggle] != true
        _state.update { it.copy(toggles = it.toggles + (toggle to on)) }
        scope.launch {
            sources.set(toggle, on)
            delay(TOGGLE_SETTLE_MS)
            _state.update { it.copy(toggles = it.toggles + sources.toggles()) }
        }
    }

    /** The clock's stopwatch and timer keep running while the panel is closed; a timer at zero calls [onTimerDone]. */
    fun timer(command: TimerCommand) {
        val now = SystemClock.elapsedRealtime()
        val current = _state.value.timer
        val next = when (command) {
            TimerCommand.Start -> current.start(now)
            TimerCommand.Pause -> current.pause(now)
            TimerCommand.Reset -> current.reset()
            is TimerCommand.Set -> PanelTimer(countdownMs = command.minutes?.let { it * 60_000L })
        }
        _state.update { it.copy(timer = next) }
        timerDone?.cancel()
        val endsAt = next.endsAt() ?: return
        timerDone = scope.launch {
            delay(endsAt - now)
            _state.update { it.copy(timer = it.timer.pause(SystemClock.elapsedRealtime())) }
            mainHandler.post(onTimerDone)
        }
    }

    /** A stroke drawn or erased on a Notes widget: shown at once, written to disk a moment later. */
    fun drawNote(id: String, strokes: List<NoteStroke>) {
        _state.update { it.copy(notes = it.notes + (id to (it.notes[id] ?: PanelNote()).copy(strokes = strokes))) }
        noteSaves.remove(id)?.cancel()
        noteSaves[id] = scope.launch {
            delay(NOTE_SAVE_DELAY_MS)
            notes.saveStrokes(id, strokes)
        }
    }

    fun media(command: MediaCommand) = nowPlaying.send(command)

    /** Allows Thor Tools' media listener as root, then follows the media sessions. */
    fun allowMedia() {
        scope.launch {
            executor.executeAsRoot(nowPlaying.allowCommand)
            delay(ALLOW_SETTLE_MS)
            mainHandler.post { if (sampling != null) nowPlaying.start() }
        }
    }

    /** While the panel is open: live stats once a second, slider writers, and watching for changes made elsewhere. */
    fun start() {
        sampling?.cancel()
        sampling = scope.launch {
            while (isActive) {
                sample()
                delay(SAMPLE_MS)
            }
        }
        listOf(volumeWriter, topWriter, bottomWriter).forEach { it.start() }
        WATCHED_KEYS.forEach { key ->
            runCatching { context.contentResolver.registerContentObserver(Settings.System.getUriFor(key), false, settingsObserver) }
        }
        context.registerReceiver(volumeReceiver, IntentFilter(VOLUME_CHANGED_ACTION), Context.RECEIVER_NOT_EXPORTED)
        nowPlaying.start()
    }

    fun stop() {
        sampling?.cancel()
        sampling = null
        nowPlaying.stop()
        pendingRefresh?.cancel()
        listOf(volumeWriter, topWriter, bottomWriter).forEach { it.stop() }
        context.contentResolver.unregisterContentObserver(settingsObserver)
        runCatching { context.unregisterReceiver(volumeReceiver) }
    }

    private fun sample() {
        val reading = sampler.sample()
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val current = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val power = StatsParser.watts(current, battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0)
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val toFull = if (charging) BatteryEstimate.toFullMinutes(batteryManager.computeChargeTimeRemaining()) else null
        val left = if (charging) {
            null
        } else {
            BatteryEstimate.leftMinutes(batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER), current)
        }
        ticks++
        val front = frontApp()
        if (front?.packageName != null && front.packageName !in _state.value.apps) {
            appEntry(front.packageName)?.let { entry -> _state.update { it.copy(apps = it.apps + entry) } }
        }
        val network = if (WidgetType.NETWORK in wanted && ticks % NETWORK_EVERY == 1L) sources.network() else null
        if (network != null && network.type != NetworkType.NONE) {
            helper.send("ping", listOf(PING_HOST)) { ok, text ->
                _state.update { it.copy(network = it.network.copy(pingMs = text.toFloatOrNull().takeIf { ok })) }
            }
        }
        val storage = if (WidgetType.STORAGE in wanted && ticks % STORAGE_EVERY == 0L) sources.storage() else null
        val toggles = if (WidgetType.TOGGLES in wanted && ticks % TOGGLES_EVERY == 0L) sources.toggles() else null
        _state.update {
            val stats = reading.orElse(it.stats)
            it.copy(
                front = front,
                network = network?.copy(pingMs = it.network.pingMs) ?: it.network,
                storage = storage ?: it.storage,
                toggles = toggles ?: it.toggles,
                controllerOnTop = controllerDisplay()?.let { display -> display == Display.DEFAULT_DISPLAY } ?: it.controllerOnTop,
                stats = stats,
                powerW = power,
                batteryTemp = temp,
                toFullMinutes = toFull,
                leftMinutes = left,
                history = history.add(GraphPoint(SystemClock.uptimeMillis(), stats.cpuLoad, stats.gpuLoad, stats.cpuTemp)),
            )
        }
        if (!reading.incomplete) return
        // SELinux keeps apps out of some of these files on some firmware; the helper reads them as root.
        helper.send("stats", emptyList()) { ok, text ->
            if (ok) _state.update { it.copy(stats = reading.orElse(StatsReading.decode(text))) }
        }
    }

    // The slider follows the finger at once; the level itself is written in steps (see LevelWriter).
    fun setVolume(fraction: Float) {
        _state.update { it.copy(volume = fraction) }
        volumeWriter.set(LevelSteps.volumeIndex(fraction, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)))
    }

    fun setTopBrightness(position: Float) {
        _state.update { it.copy(topBrightness = position) }
        topWriter.set(LevelSteps.snap(position))
    }

    fun setBottomBrightness(position: Float) {
        if (bottomDisplay() == null) return
        _state.update { it.copy(bottomBrightness = position) }
        bottomWriter.set(LevelSteps.snap(position))
    }

    /** A helper command that suspends until its answer, so a slider never queues more than one write. */
    private suspend fun helperCall(name: String, args: List<String>): Boolean = suspendCancellableCoroutine { continuation ->
        val sent = helper.send(name, args) { ok, _ -> if (continuation.isActive) continuation.resume(ok) }
        if (!sent && continuation.isActive) continuation.resume(false)
    }

    /**
     * Writes one level. The newest value wins and each write finishes before the next starts, so a drag is written
     * at once without piling up (a StateFlow drops values that are equal or overtaken).
     */
    private inner class LevelWriter<T : Any>(private val write: suspend (T) -> Unit) {
        private val target = MutableStateFlow<T?>(null)
        private var job: Job? = null

        @Volatile
        private var touchedAt = 0L

        /** Fresh values from elsewhere must not yank a slider out from under the finger. */
        val recentlyTouched: Boolean get() = SystemClock.uptimeMillis() - touchedAt < TOUCH_HOLD_MS

        fun set(value: T) {
            touchedAt = SystemClock.uptimeMillis()
            target.value = value
        }

        fun start() {
            job?.cancel()
            job = scope.launch { target.filterNotNull().collect { runCatching { write(it) } } }
        }

        fun stop() {
            job?.cancel()
            job = null
            target.value = null
        }
    }

    private fun readBrightness() {
        val displays = listOfNotNull(Display.DEFAULT_DISPLAY, bottomDisplay())
        val sent = helper.send("getbrightness", listOf(displays.joinToString(","))) { ok, text ->
            val levels = text.split(',').mapNotNull(String::toFloatOrNull)
            if (!ok || levels.size != displays.size) return@send readTopBrightnessSetting()
            helperBrightness = true
            _state.update {
                it.copy(
                    topBrightness = BrightnessStep.toPerceived(levels[0]),
                    bottomBrightness = levels.getOrNull(1)?.let(BrightnessStep::toPerceived),
                )
            }
        }
        if (!sent) readTopBrightnessSetting()
    }

    private fun readTopBrightnessSetting() {
        helperBrightness = false
        val level = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)
        _state.update { it.copy(topBrightness = level, bottomBrightness = null) }
    }

    private companion object {
        const val KEY_SCREEN_MODE = "dual_screen_display_mode"
        const val KEY_AYN_MOUSE = "global_gamepad_to_mouse_mode"
        const val KEY_FOCUS_LOCK = "screen_focus_lock"
        const val ACTION_SETTLE_MS = 400L
        const val CHANGE_COALESCE_MS = 150L
        const val TOUCH_HOLD_MS = 600L
        const val SAMPLE_MS = 1000L
        const val NOTE_SAVE_DELAY_MS = 500L
        const val TOGGLE_SETTLE_MS = 1200L
        const val NETWORK_EVERY = 5L
        const val STORAGE_EVERY = 10L
        const val TOGGLES_EVERY = 3L
        const val PING_HOST = "1.1.1.1"
        const val ALLOW_SETTLE_MS = 500L

        // Hidden in AudioManager, but sent to every app.
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

        val WATCHED_KEYS = listOf(
            "peak_refresh_rate",
            "min_refresh_rate",
            "performance_mode",
            "fan_mode",
            "temp_abxy_layout_mode",
            "trigger_input_mode",
            KEY_SCREEN_MODE,
            KEY_AYN_MOUSE,
            KEY_FOCUS_LOCK,
            Settings.System.SCREEN_BRIGHTNESS,
        )
    }
}
