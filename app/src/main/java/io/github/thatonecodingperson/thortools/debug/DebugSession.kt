package io.github.thatonecodingperson.thortools.debug

import android.content.Context
import android.os.Build
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.BuildConfig
import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What the service does for the action check; set while the accessibility service runs. */
interface CheckRunner {
    fun start(checks: List<ActionCheck>)

    fun stop()
}

/** A running hotkey check: [runActions] lets recognised hotkeys do their job too; it ends by itself at [until] (elapsed ms). */
data class HotkeyProbe(val runActions: Boolean, val until: Long)

/**
 * The debug toolkit's shared state, between its screen and the accessibility service (one process): the action check's
 * results, its questions and gesture pad, the hotkey check's probe and what it saw, and the results file.
 */
@Singleton
class DebugSession @Inject constructor(@ApplicationContext private val context: Context, private val prefs: SharedPrefsRepo) {

    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _results = MutableStateFlow<Map<ThorAction, CheckResult>>(emptyMap())
    val results: StateFlow<Map<ThorAction, CheckResult>> = _results.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _asking = MutableStateFlow<ThorAction?>(null)

    /** A watch check waiting for "did it open?". */
    val asking: StateFlow<ThorAction?> = _asking.asStateFlow()
    private var answer: CompletableDeferred<Boolean>? = null

    private val _padShown = MutableStateFlow(false)

    /** The swipe checks' full-screen pad is up. */
    val padShown: StateFlow<Boolean> = _padShown.asStateFlow()

    /** The direction the pad saw last; the swipe checks read it. */
    @Volatile
    var lastSwipe: String? = null

    @Volatile
    var runner: CheckRunner? = null

    private val _probe = MutableStateFlow<HotkeyProbe?>(null)
    val probe: StateFlow<HotkeyProbe?> = _probe.asStateFlow()

    private val _events = MutableStateFlow<List<HotkeyEvent>>(emptyList())
    val events: StateFlow<List<HotkeyEvent>> = _events.asStateFlow()

    fun put(result: CheckResult) = _results.update { it + (result.action to result) }

    fun clearResults() = _results.update { emptyMap() }

    fun setBusy(busy: Boolean) {
        _busy.value = busy
    }

    fun showPad(shown: Boolean) {
        _padShown.value = shown
    }

    /** Asks whether [action] did its job and waits for the answer; null when none came. */
    suspend fun ask(action: ThorAction): Boolean? {
        val waiting = CompletableDeferred<Boolean>()
        answer = waiting
        _asking.value = action
        return try {
            withTimeoutOrNull(ASK_TIMEOUT_MS) { waiting.await() }
        } finally {
            _asking.value = null
            answer = null
        }
    }

    fun answer(yes: Boolean) {
        answer?.complete(yes)
    }

    fun startProbe(runActions: Boolean) {
        _events.value = emptyList()
        _probe.value = HotkeyProbe(runActions, SystemClock.elapsedRealtime() + PROBE_MS)
    }

    fun stopProbe() {
        _probe.value = null
    }

    /** The probe while it lasts; it ends by itself after a while, so the hotkeys can never stay paused. */
    fun activeProbe(): HotkeyProbe? {
        val probe = _probe.value ?: return null
        if (SystemClock.elapsedRealtime() < probe.until) return probe
        _probe.value = null
        return null
    }

    fun report(event: HotkeyEvent) = _events.update { (it + event).takeLast(MAX_EVENTS) }

    val reportFile: File? get() = context.getExternalFilesDir(DIR)?.let { File(it, FILE) }

    /** The results file's text, or null when there is none. Disk: never on the main thread. */
    fun reportText(): String? = runCatching { reportFile?.takeIf { it.exists() }?.readText() }.getOrNull()

    /** Puts [section] under [name] in the results file, when the file is switched on. Disk: never on the main thread. */
    fun writeSection(name: String, section: String) {
        if (!prefs.debugReportFile) return
        val file = reportFile ?: return
        runCatching {
            val header = listOf(
                "written: ${now()}",
                "app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                "firmware: ${Build.DISPLAY}",
                "device: ${Build.MANUFACTURER} ${Build.MODEL}",
            )
            file.writeText(DebugReport.merge(reportText(), header, name, section))
        }
    }

    /** [writeSection] on its own thread, for callers that may be going away (a screen being left). */
    fun writeSectionSoon(name: String, section: String) {
        writes.launch { writeSection(name, section) }
    }

    fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private companion object {
        const val DIR = "debug"
        const val FILE = "debug-report.txt"
        const val ASK_TIMEOUT_MS = 180_000L
        const val PROBE_MS = 300_000L
        const val MAX_EVENTS = 60
    }
}
