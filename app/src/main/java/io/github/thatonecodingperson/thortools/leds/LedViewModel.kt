package io.github.thatonecodingperson.thortools.leds

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class LedSide { LEFT, RIGHT }

data class LedUiModel(
    val look: LedLook = LedLook(),
    /** Both sticks take the same colour. */
    val same: Boolean = true,
    /** The stick a colour goes to while they differ. */
    val editing: LedSide = LedSide.LEFT,
    val helperRunning: Boolean = true,
    val serviceRunning: Boolean = true,
    val sticksFound: Boolean = true,
    val battery: Int = 50,
    val charging: Boolean = false,
)

@HiltViewModel
class LedViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPrefsRepo,
    private val status: ServiceStatus,
    private val executor: ShellExecutor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(prefs.ledLook.let { LedUiModel(look = it, same = it.left == it.right) })
    val uiState: StateFlow<LedUiModel> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.update { it.copy(helperRunning = status.rawInput?.connected == true, serviceRunning = status.connected) }
        viewModelScope.launch {
            // Apps may not look into /sys; PServer can. Unknown (no PServer) counts as found.
            val found = withContext(Dispatchers.IO) { executor.executeAsRoot(STICKS_CHECK).getOrNull()?.trim() != "no" }
            val battery = withContext(Dispatchers.IO) {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val state = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            _uiState.update {
                it.copy(
                    sticksFound = found,
                    battery = if (level < 0 || scale <= 0) it.battery else level * 100 / scale,
                    charging = state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL,
                )
            }
        }
    }

    fun setMode(mode: LedMode) = save { it.copy(mode = mode) }

    fun pickColour(rgb: Int) {
        val state = _uiState.value
        save {
            when {
                state.same -> it.copy(left = rgb, right = rgb)
                state.editing == LedSide.LEFT -> it.copy(left = rgb)
                else -> it.copy(right = rgb)
            }
        }
    }

    fun setSame(same: Boolean) {
        _uiState.update { it.copy(same = same) }
        if (same) save { it.copy(right = it.left) }
    }

    fun edit(side: LedSide) = _uiState.update { it.copy(editing = side) }

    fun setBrightness(value: Float) = save { it.copy(brightness = value) }

    fun setSpeed(value: Float) = save { it.copy(speed = value) }

    fun setChargingBreathe(on: Boolean) = save { it.copy(chargingBreathe = on) }

    /** The service picks the change up from the preferences and shows it on the sticks. */
    private fun save(change: (LedLook) -> LedLook) {
        val look = change(_uiState.value.look)
        prefs.ledLook = look
        _uiState.update { it.copy(look = look) }
    }

    private companion object {
        const val STICKS_CHECK = "[ -e /sys/class/sn3112l/led/brightness ] && [ -e /sys/class/sn3112r/led/brightness ] && echo yes " +
            "|| echo no"
    }
}
