package io.github.thatonecodingperson.thortools.hotkeys

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Presses of Home, Back, AYN or a volume key that the hotkeys held back and that turned out to be no hotkey: Android
 * does that button's own job, through its own actions, once per press. No key is pressed again: a key sent by an app
 * comes from a virtual keyboard, not the Thor's controller. Game buttons are never held back on their own
 * ([Hotkey.allowed]), so they never come here. Main thread.
 */
class SystemPress(
    private val service: AccessibilityService,
    /** Home on the screen with the controller, the way the Home button does it. */
    private val goHome: () -> Unit,
    private val openAynDrawer: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val audio = service.getSystemService(AudioManager::class.java)

    fun press(button: PadButton, presses: Int) {
        repeat(presses.coerceAtMost(MAX_PRESSES)) { index -> handler.postDelayed({ ownJob(button) }, index * PRESS_GAP_MS) }
    }

    fun stop() = handler.removeCallbacksAndMessages(null)

    private fun ownJob(button: PadButton) {
        when (button) {
            PadButton.HOME -> goHome()
            PadButton.BACK -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            PadButton.AYN -> openAynDrawer()
            PadButton.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            PadButton.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            else -> Unit
        }
    }

    private fun adjustVolume(direction: Int) =
        audio.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)

    private companion object {
        const val PRESS_GAP_MS = 120L
        const val MAX_PRESSES = 3
    }
}
