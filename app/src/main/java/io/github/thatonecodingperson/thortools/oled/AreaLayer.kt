package io.github.thatonecodingperson.thortools.oled

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.view.Surface
import android.view.SurfaceControl

/**
 * A layer of the root helper's own over one still area of a screen ([box], in the screen's content pixels): its moved
 * still pixels and, for classic still areas, its dim; just under the [DimLayer]. A trusted overlay without an input
 * window, so touches go to whatever is underneath; it disappears with the helper. [area] is the still area itself, in
 * the small copy's pixels; [inner] is the area inside [box].
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "BlockedPrivateApi")
internal class AreaLayer(layerStack: Int, val box: Box, val area: Box, val inner: Box) {
    val control: SurfaceControl = SurfaceControl.Builder()
        .setName("ThorToolsOledArea")
        .setBufferSize(box.right - box.left, box.bottom - box.top)
        .setFormat(PixelFormat.TRANSLUCENT)
        .build()
    private val surface = Surface(control)

    /** The copy's pixels that were still when the layer was last drawn, and when that was. */
    var stillPixels = IntArray(0)
    var drawnAt = 0L

    /** The last full-size copy of [box], for telling the still pixels apart at the next step. */
    var previous: IntArray? = null
    var step = 0
    var nextStep = 0L

    init {
        val transaction = SurfaceControl.Transaction()
        transaction.javaClass.getMethod("setLayerStack", SurfaceControl::class.java, Int::class.javaPrimitiveType)
            .invoke(transaction, control, layerStack)
        // Trusted, or Android counts it as covering the screen and drops every touch underneath.
        transaction.javaClass.getMethod("setTrustedOverlay", SurfaceControl::class.java, Boolean::class.javaPrimitiveType)
            .invoke(transaction, control, true)
        transaction.setLayer(control, Int.MAX_VALUE - 1)
            .setPosition(control, box.left.toFloat(), box.top.toFloat())
            .setVisibility(control, true)
            .apply()
    }

    /** [pixels] (a whole [box], or null for nothing), then black at [dimAlpha] of 255 over the area. */
    fun draw(pixels: IntArray?, dimAlpha: Int = 0) {
        val width = box.right - box.left
        val height = box.bottom - box.top
        val canvas = surface.lockCanvas(null)
        try {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            pixels?.let {
                val bitmap = Bitmap.createBitmap(it, width, height, Bitmap.Config.ARGB_8888)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                bitmap.recycle()
            }
            if (dimAlpha > 0) {
                canvas.save()
                canvas.clipRect(inner.left, inner.top, inner.right, inner.bottom)
                canvas.drawColor(Color.argb(dimAlpha, 0, 0, 0))
                canvas.restore()
            }
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    fun remove() {
        runCatching { SurfaceControl.Transaction().reparent(control, null).apply() }
        surface.release()
        control.release()
    }
}
