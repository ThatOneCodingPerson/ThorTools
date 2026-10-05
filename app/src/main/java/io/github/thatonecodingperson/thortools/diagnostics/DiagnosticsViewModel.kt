package io.github.thatonecodingperson.thortools.diagnostics

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.view.InputDevice
import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** One key the accessibility service (or the key tester) saw; [outcome] is what Thor Tools did with it. */
data class KeyLogEntry(
    val down: Boolean,
    val keyCode: Int,
    val scanCode: Int,
    val source: Int,
    val device: String,
    val repeat: Int = 0,
    val flags: Int = 0,
    val deviceId: Int = -1,
    val vendorProduct: String = "",
    val outcome: String = "",
) {
    override fun toString(): String = "${if (down) "down" else "up  "} ${KeyEvent.keyCodeToString(keyCode)} ($keyCode)" +
        " scan=$scanCode source=0x${Integer.toHexString(source)} device=\"$device\" id=$deviceId $vendorProduct" +
        (if (repeat > 0) " repeat=$repeat" else "") +
        (if (flags and KeyEvent.FLAG_CANCELED != 0) " canceled" else "") +
        (if (flags and KeyEvent.FLAG_FALLBACK != 0) " fallback" else "") +
        (if (outcome.isNotEmpty()) " -> $outcome" else "")

    companion object {
        /** A D-pad direction or stick flick the joystick catcher turned into a press or release. */
        fun motion(id: String, down: Boolean, outcome: String) = KeyLogEntry(
            down = down,
            keyCode = KeyEvent.KEYCODE_UNKNOWN,
            scanCode = 0,
            source = InputDevice.SOURCE_JOYSTICK,
            device = "motion $id",
            outcome = outcome,
        )

        fun of(event: KeyEvent, outcome: String = "") = KeyLogEntry(
            down = event.action == KeyEvent.ACTION_DOWN,
            keyCode = event.keyCode,
            scanCode = event.scanCode,
            source = event.source,
            device = event.device?.name.orEmpty(),
            repeat = event.repeatCount,
            flags = event.flags,
            deviceId = event.deviceId,
            vendorProduct = event.device?.let { "%04x:%04x".format(it.vendorId, it.productId) }.orEmpty(),
            outcome = outcome,
        )
    }
}

data class DiagnosticsUiModel(
    val loading: Boolean = true,
    val report: String = "",
    val keys: List<KeyLogEntry> = emptyList(),
    val savedAs: String? = null,
    val debugMode: Boolean = false,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val collector: DiagnosticsCollector,
    private val prefs: SharedPrefsRepo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiModel(debugMode = prefs.debugMode))
    val uiState: StateFlow<DiagnosticsUiModel> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) { collector.collect() }
            _uiState.update { it.copy(loading = false, report = report) }
        }
    }

    fun setDebugMode(on: Boolean) {
        prefs.debugMode = on
        _uiState.update { it.copy(debugMode = on) }
    }

    fun onKey(event: KeyEvent) {
        if (event.repeatCount > 0) return
        _uiState.update { it.copy(keys = (listOf(KeyLogEntry.of(event)) + it.keys).take(KEY_LOG_SIZE)) }
    }

    fun fullReport(): String = buildString {
        append(_uiState.value.report.trimEnd())
        appendLine()
        appendLine()
        appendLine("[Key tester, newest first]")
        _uiState.value.keys.forEach { appendLine(it.toString()) }
    }

    fun saveToDownloads() {
        val text = fullReport()
        viewModelScope.launch {
            val name = "ThorTools-diagnostics-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt"
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    }
                    val resolver = context.contentResolver
                    val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
                    resolver.openOutputStream(uri).use { checkNotNull(it).write(text.toByteArray()) }
                }.isSuccess
            }
            _uiState.update { it.copy(savedAs = if (saved) "Download/$name" else "") }
        }
    }

    private companion object {
        const val KEY_LOG_SIZE = 24
    }
}
