package io.github.thatonecodingperson.thortools.oled

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.view.Surface
import android.view.SurfaceControl

/**
 * The root helper's one dimming layer over a screen: a buffer of [columns] x [rows] (one pixel per block of still
 * areas) stretched over the whole [screen], so each block gets its own see-through black and the edges between them
 * are soft. Top-most, no input window (touches go through), hidden while nothing is dimmed, gone with the helper.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "BlockedPrivateApi")
internal class DimLayer(layerStack: Int, screen: Box, private val columns: Int, private val rows: Int) {
    private val control: SurfaceControl = SurfaceControl.Builder()
        .setName("ThorToolsOledDim")
        .setBufferSize(columns, rows)
        .setFormat(PixelFormat.TRANSLUCENT)
        .build()
    private val surface = Surface(control)
    private var drawn: IntArray? = null
    private var visible = false

    init {
        val transaction = SurfaceControl.Transaction()
        transaction.javaClass.getMethod("setLayerStack", SurfaceControl::class.java, Int::class.javaPrimitiveType)
            .invoke(transaction, control, layerStack)
        // Trusted, or Android counts it as covering the screen and drops every touch underneath.
        transaction.javaClass.getMethod("setTrustedOverlay", SurfaceControl::class.java, Boolean::class.javaPrimitiveType)
            .invoke(transaction, control, true)
        transaction.setLayer(control, Int.MAX_VALUE)
            .setPosition(control, screen.left.toFloat(), screen.top.toFloat())
            .setScale(control, (screen.right - screen.left).toFloat() / columns, (screen.bottom - screen.top).toFloat() / rows)
            .setVisibility(control, false)
            .apply()
    }

    /** Each block's dim, 0 to 255 of black, row by row; redrawn only when something changed. */
    fun draw(alphas: IntArray) {
        if (alphas.size != columns * rows || alphas.contentEquals(drawn)) return
        drawn = alphas.copyOf()
        val show = alphas.any { it > 0 }
        if (show) {
            val pixels = IntArray(alphas.size) { Color.argb(alphas[it], 0, 0, 0) }
            val bitmap = Bitmap.createBitmap(pixels, columns, rows, Bitmap.Config.ARGB_8888)
            val canvas = surface.lockCanvas(null)
            try {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
            } finally {
                surface.unlockCanvasAndPost(canvas)
                bitmap.recycle()
            }
        }
        if (show != visible) {
            visible = show
            SurfaceControl.Transaction().setVisibility(control, show).apply()
        }
    }

    fun remove() {
        runCatching { SurfaceControl.Transaction().reparent(control, null).apply() }
        surface.release()
        control.release()
    }
}
