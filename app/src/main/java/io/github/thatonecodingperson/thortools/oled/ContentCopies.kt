package io.github.thatonecodingperson.thortools.oled

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.SurfaceControl

/**
 * Copies of what the windows of one display show, without anything drawn outside the window tree, such as the root
 * helper's own [AreaLayer]s. The window manager hands out a mirror of the display's windows (`mirrorDisplay`); it is
 * kept under a container layer on a layer stack no screen shows, so it is never on screen, and copied with
 * SurfaceFlinger's layer capture. Coordinates are the display's content pixels.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "BlockedPrivateApi")
internal class ContentCopies(private val displayId: Int, private val screen: Box) {
    private var container: SurfaceControl? = null
    private var mirror: SurfaceControl? = null

    /** [crop] of the windows (null: the whole [screen]; a container has no size of its own), scaled by [scale]. */
    fun take(crop: Box?, scale: Float): Sample? {
        val root = container ?: open() ?: error("no mirror of display $displayId")
        val builderClass = Class.forName("android.view.SurfaceControl\$LayerCaptureArgs\$Builder")
        val builder = builderClass.getConstructor(SurfaceControl::class.java).newInstance(root)
        val area = crop ?: screen
        builderClass.getMethod("setSourceCrop", Rect::class.java).invoke(builder, Rect(area.left, area.top, area.right, area.bottom))
        builderClass.getMethod("setFrameScale", Float::class.javaPrimitiveType).invoke(builder, scale)
        val args = builderClass.getMethod("build").invoke(builder)
        val argsClass = Class.forName("android.view.SurfaceControl\$LayerCaptureArgs")
        val shot = SurfaceControl::class.java.getMethod("captureLayers", argsClass).invoke(null, args) ?: error("no layer copy")
        val hardware = shot.javaClass.getMethod("asBitmap").invoke(shot) as? Bitmap ?: error("no bitmap")
        val bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        hardware.recycle()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val sample = Sample(bitmap.width, bitmap.height, pixels)
        bitmap.recycle()
        return sample
    }

    fun close() {
        val transaction = SurfaceControl.Transaction()
        mirror?.let { transaction.reparent(it, null) }
        container?.let { transaction.reparent(it, null) }
        runCatching { transaction.apply() }
        mirror?.release()
        container?.release()
        mirror = null
        container = null
    }

    private fun open(): SurfaceControl? {
        val windowManager = Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null)
        val out = SurfaceControl::class.java.getConstructor().newInstance()
        val mirrored = windowManager.javaClass.getMethod("mirrorDisplay", Int::class.javaPrimitiveType, SurfaceControl::class.java)
            .invoke(windowManager, displayId, out) as? Boolean ?: false
        if (!mirrored || !out.isValid) return null
        val builder = SurfaceControl.Builder().setName("ThorToolsOledContent")
        builder.javaClass.getMethod("setContainerLayer").invoke(builder)
        val root = builder.build()
        val transaction = SurfaceControl.Transaction()
        transaction.javaClass.getMethod("setLayerStack", SurfaceControl::class.java, Int::class.javaPrimitiveType)
            .invoke(transaction, root, UNSHOWN_LAYER_STACK)
        transaction.reparent(out, root).setVisibility(out, true).setVisibility(root, true).apply()
        mirror = out
        container = root
        return root
    }

    private companion object {
        /** A layer stack no display uses: what is there is kept up to date but never composed onto a screen. */
        const val UNSHOWN_LAYER_STACK = 7_771
    }
}
