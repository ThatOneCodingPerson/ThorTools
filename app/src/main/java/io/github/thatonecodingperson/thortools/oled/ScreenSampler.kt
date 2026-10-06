package io.github.thatonecodingperson.thortools.oled

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.ImageReader
import android.os.IBinder
import android.view.SurfaceControl

/** A tiny copy of one screen's picture: [width] x [height] ARGB pixels. */
class Sample(val width: Int, val height: Int, val pixels: IntArray)

/**
 * Takes tiny copies of a screen's picture inside the root helper, for telling whether it changes. The picture is read in
 * the screen's own content space, so the pixel shifter's moves don't count as changes. First SurfaceFlinger's one-shot
 * capture; if a copy can't be read that way, a small mirror of the screen that is only attached while a copy is taken.
 * Copies stay in memory.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "BlockedPrivateApi", "WrongConstant")
internal class ScreenSampler(private val projection: DisplayProjection, private val width: Int, private val height: Int) {
    private val surfaceControl = Class.forName("android.view.SurfaceControl")
    private var useMirror = false
    private var mirror: Mirror? = null

    fun take(): Sample? {
        if (!useMirror) {
            val shot = runCatching { Captures.take(projection.physicalId, width, height, null) }.getOrNull()
            if (shot != null) return shot
            useMirror = true
        }
        return runCatching { (mirror ?: Mirror().also { mirror = it }).take() }.getOrNull()
    }

    fun close() {
        mirror?.close()
        mirror = null
    }

    val method: String get() = if (useMirror) "mirror" else "capture"

    /** A virtual display showing the screen's content at [width] x [height]; composed only while a surface is attached. */
    private inner class Mirror {
        private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        private val token = surfaceControl.getMethod("createDisplay", String::class.java, Boolean::class.javaPrimitiveType)
            .invoke(null, "ThorToolsOledMirror", false) as IBinder

        init {
            val layerStack = projection.layerStack.let { Rect(it.left, it.top, it.right, it.bottom) }
            transaction {
                surfaceControl.getMethod(
                    "setDisplayProjection",
                    IBinder::class.java,
                    Int::class.javaPrimitiveType,
                    Rect::class.java,
                    Rect::class.java,
                ).invoke(null, token, 0, layerStack, Rect(0, 0, width, height))
                surfaceControl.getMethod("setDisplayLayerStack", IBinder::class.java, Int::class.javaPrimitiveType)
                    .invoke(null, token, projection.layerStackId)
            }
        }

        fun take(): Sample? {
            reader.acquireLatestImage()?.close()
            attach(reader.surface)
            try {
                val deadline = System.currentTimeMillis() + FRAME_WAIT_MS
                while (System.currentTimeMillis() < deadline) {
                    reader.acquireLatestImage()?.use { image ->
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val pixels = IntArray(width * height)
                        for (y in 0 until height) {
                            for (x in 0 until width) {
                                val at = y * plane.rowStride + x * plane.pixelStride
                                val r = buffer.get(at).toInt() and 0xFF
                                val g = buffer.get(at + 1).toInt() and 0xFF
                                val b = buffer.get(at + 2).toInt() and 0xFF
                                pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                            }
                        }
                        return Sample(width, height, pixels)
                    }
                    Thread.sleep(FRAME_POLL_MS)
                }
                return null
            } finally {
                attach(null)
            }
        }

        fun close() {
            runCatching { surfaceControl.getMethod("destroyDisplay", IBinder::class.java).invoke(null, token) }
            reader.close()
        }

        private fun attach(surface: android.view.Surface?) = transaction {
            surfaceControl.getMethod("setDisplaySurface", IBinder::class.java, android.view.Surface::class.java)
                .invoke(null, token, surface)
        }
    }

    private fun transaction(block: () -> Unit) {
        surfaceControl.getMethod("openTransaction").invoke(null)
        try {
            block()
        } finally {
            surfaceControl.getMethod("closeTransaction").invoke(null)
        }
    }

    private companion object {
        const val FRAME_WAIT_MS = 500L
        const val FRAME_POLL_MS = 20L
    }
}

/** SurfaceFlinger's one-shot copies of a screen, read into plain pixels. */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "BlockedPrivateApi")
internal object Captures {
    /** Display [physicalId] scaled to [width] x [height] (null: full size), only [crop] of it (content pixels; null: all). */
    fun take(physicalId: Long, width: Int?, height: Int?, crop: Box?): Sample? {
        val surfaceControl = SurfaceControl::class.java
        val token = surfaceControl.getMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType)
            .invoke(null, physicalId) as? IBinder ?: return null
        val builderClass = Class.forName("android.view.SurfaceControl\$DisplayCaptureArgs\$Builder")
        val builder = builderClass.getConstructor(IBinder::class.java).newInstance(token)
        if (width != null && height != null) {
            builderClass.getMethod("setSize", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(builder, width, height)
        }
        crop?.let { builderClass.getMethod("setSourceCrop", Rect::class.java).invoke(builder, Rect(it.left, it.top, it.right, it.bottom)) }
        val args = builderClass.getMethod("build").invoke(builder)
        val argsClass = Class.forName("android.view.SurfaceControl\$DisplayCaptureArgs")
        val shot = surfaceControl.getMethod("captureDisplay", argsClass).invoke(null, args) ?: return null
        val hardware = shot.javaClass.getMethod("asBitmap").invoke(shot) as? Bitmap ?: return null
        val bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        hardware.recycle()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val sample = Sample(bitmap.width, bitmap.height, pixels)
        bitmap.recycle()
        return sample
    }
}
