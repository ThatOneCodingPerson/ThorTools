package io.github.thatonecodingperson.thortools.tools

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.tools.DeviceType.ODIN2
import io.github.thatonecodingperson.thortools.tools.DeviceType.OTHER
import io.github.thatonecodingperson.thortools.tools.DeviceType.RP4
import io.github.thatonecodingperson.thortools.tools.DeviceType.THOR
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class DeviceUtils @Inject constructor(@ApplicationContext private val context: Context, private val executor: ShellExecutor) {

    /** Read through PServer, so never on the main thread; remembered once read (a property can't change until reboot). */
    fun getDeviceVersion(): String = property(SettingsRepo.KEY_BUILD_VERSION).ifEmpty { Build.DISPLAY }

    fun getDeviceCodename(): String = property(SettingsRepo.KEY_VENDOR_NAME)

    private fun property(key: String): String = properties[key] ?: executor.getStringProperty(key, "").also { value ->
        // Empty while PServer isn't up yet: asked again next time.
        if (value.isNotEmpty()) properties[key] = value
    }

    fun getDeviceType(): DeviceType = detect(getDeviceCodename(), Build.MODEL, Build.DEVICE, internalDisplayCount())

    /** Built-in panels only; an HDMI or DP screen on an Odin 2 is an external display and doesn't count. */
    private fun internalDisplayCount(): Int {
        val getType = runCatching { Display::class.java.getMethod("getType") }.getOrNull() ?: return 1
        return context.getSystemService(DisplayManager::class.java).displays.count { display ->
            runCatching { getType.invoke(display) as Int }.getOrNull() == TYPE_INTERNAL
        }
    }

    companion object {
        private const val TYPE_INTERNAL = 1
        private val properties = ConcurrentHashMap<String, String>()

        /**
         * The Thor's firmware grew out of the Odin 2's, so its codename may well be the Odin 2's too. Two built-in screens
         * only exist on the Thor, so that is checked before the codename.
         */
        fun detect(codename: String, model: String, device: String, internalDisplays: Int = 1): DeviceType = when {
            internalDisplays >= 2 -> THOR
            listOf(codename, model, device).any { it.contains("thor", ignoreCase = true) } -> THOR
            codename == "Q9" -> ODIN2
            codename == "4.0" || codename == "4.0P" -> RP4
            else -> OTHER
        }
    }
}

enum class DeviceType {
    ODIN2,
    RP4,
    THOR,
    OTHER,
    ;

    val isSupported: Boolean get() = this == THOR || this == ODIN2
}
