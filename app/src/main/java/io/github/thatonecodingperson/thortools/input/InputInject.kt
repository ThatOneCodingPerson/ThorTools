package io.github.thatonecodingperson.thortools.input

import android.annotation.SuppressLint
import android.hardware.input.InputManager
import android.view.InputEvent

/**
 * Sends input events as root, from the root helper only (an app may not inject). With a display, a key goes to that
 * screen's focused window whichever screen has the controller (Home on the bottom screen goes to the bottom screen's
 * home).
 */
// Lint's hidden-API rules are for app processes; this code only ever runs in the root helper's app_process.
@SuppressLint("BlockedPrivateApi", "DiscouragedPrivateApi", "PrivateApi")
internal object InputInject {
    // Android 14 moved the injector to InputManagerGlobal; the Thor's Android 13 still has InputManager.getInstance().
    private val manager: Any by lazy {
        runCatching { Class.forName("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null) }.getOrNull()
            ?: checkNotNull(InputManager::class.java.getMethod("getInstance").invoke(null))
    }
    private val injectMethod by lazy {
        manager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
    }

    // Hidden, so reflected.
    private val setDisplayId by lazy { InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType) }

    fun inject(event: InputEvent, displayId: Int) {
        if (displayId >= 0) setDisplayId.invoke(event, displayId)
        injectMethod.invoke(manager, event, INJECT_ASYNC)
    }

    private const val INJECT_ASYNC = 0
}
