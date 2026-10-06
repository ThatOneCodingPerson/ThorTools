package io.github.thatonecodingperson.thortools.oled

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import kotlin.random.Random

/**
 * What OLED Safety puts over a screen: a see-through black layer that lets touches through ([dim]), a black cover that a
 * touch takes away ([black]), and the refresher ([refresh]). Accessibility windows that never take focus, so the
 * controller stays with the app underneath; removing one leaves nothing behind. Main thread.
 */
class ScreenCover(private val service: AccessibilityService) {
    enum class Kind { DIM, BLACK, REFRESH }

    private class Shown(val view: View, val manager: WindowManager, val kind: Kind, val animator: ValueAnimator? = null)

    private val shown = mutableMapOf<Int, Shown>()

    fun kindOn(displayId: Int): Kind? = shown[displayId]?.kind

    fun dim(displayId: Int, percent: Int) {
        val alpha = (percent.coerceIn(0, 100) * 255) / 100
        val current = shown[displayId]
        if (current?.kind == Kind.DIM) {
            current.view.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
            return
        }
        show(displayId, Kind.DIM, touchable = false) { context -> View(context).apply { setBackgroundColor(Color.argb(alpha, 0, 0, 0)) } }
    }

    fun black(displayId: Int, onTouch: () -> Unit) {
        if (shown[displayId]?.kind == Kind.BLACK) return
        show(displayId, Kind.BLACK, touchable = true) { context ->
            View(context).apply {
                setBackgroundColor(Color.BLACK)
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) onTouch()
                    true
                }
            }
        }
    }

    /**
     * The refresher's [pattern] for [seconds] ([inverse] is the negative picture for [RefreshPattern.INVERSE]); [label]
     * shows the seconds left. A tap stops it.
     */
    fun refresh(displayId: Int, seconds: Int, pattern: RefreshPattern, inverse: Inverse?, label: (Int) -> String, onDone: () -> Unit) {
        val totalMs = seconds * 1_000L
        lateinit var refresher: RefreshView
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = totalMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                val elapsed = (it.animatedFraction * totalMs).toLong()
                refresher.update(elapsed, label(((totalMs - elapsed + 999) / 1_000).toInt()))
            }
        }
        val stop = {
            if (shown[displayId]?.animator === animator) {
                remove(displayId)
                onDone()
            }
        }
        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = stop()
            },
        )
        show(displayId, Kind.REFRESH, touchable = true, animator = animator) { context ->
            RefreshView(context, pattern, inverse).also { view ->
                refresher = view
                view.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) stop()
                    true
                }
            }
        }
        if (shown[displayId]?.animator === animator) animator.start()
    }

    fun remove(displayId: Int) {
        val current = shown.remove(displayId) ?: return
        current.animator?.let {
            it.removeAllListeners()
            it.cancel()
        }
        runCatching { current.manager.removeViewImmediate(current.view) }
    }

    fun removeAll() = shown.keys.toList().forEach(::remove)

    private fun show(displayId: Int, kind: Kind, touchable: Boolean, animator: ValueAnimator? = null, make: (Context) -> View) {
        remove(displayId)
        val display = service.getSystemService(DisplayManager::class.java).getDisplay(displayId) ?: return
        val context = service.createDisplayContext(display)
        val manager = context.getSystemService(WindowManager::class.java)
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "ThorToolsOled$displayId"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        }
        val view = make(context)
        if (runCatching { manager.addView(view, params) }.isFailure) return
        shown[displayId] = Shown(view, manager, kind, animator)
    }

    /** The refresher's pattern at the time given to [update], and the seconds left in grey. */
    private class RefreshView(context: Context, private val pattern: RefreshPattern, inverse: Inverse?) : View(context) {
        private val bar = Paint().apply { color = Color.WHITE }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textAlign = Paint.Align.CENTER
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SP, context.resources.displayMetrics)
        }
        private val soft = Paint(Paint.FILTER_BITMAP_FLAG)
        private val negative = inverse?.let { Bitmap.createBitmap(it.pixels, it.width, it.height, Bitmap.Config.ARGB_8888) }
        private var noise: Bitmap? = null
        private var noiseAt = -1L
        private var elapsed = 0L
        private var label = ""

        fun update(elapsed: Long, label: String) {
            this.elapsed = elapsed
            this.label = label
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val whole = Rect(0, 0, width, height)
            when (pattern) {
                RefreshPattern.SWEEP -> {
                    canvas.drawColor(Color.BLACK)
                    val barHeight = height / BAR_PARTS
                    val top = (height + barHeight) * ((elapsed % PASS_MS) / PASS_MS.toFloat()) - barHeight
                    canvas.drawRect(0f, top, width.toFloat(), top + barHeight, bar)
                }
                RefreshPattern.NOISE -> canvas.drawBitmap(noiseFor(elapsed / RefreshPattern.NOISE_MS), null, whole, null)
                RefreshPattern.COLOURS -> canvas.drawColor(Color.BLACK or RefreshPattern.colourAt(elapsed))
                RefreshPattern.INVERSE -> negative?.let { canvas.drawBitmap(it, null, whole, soft) } ?: canvas.drawColor(Color.GRAY)
            }
            canvas.drawText(label, width / 2f, height - text.textSize * 2, text)
        }

        /** Black-and-white noise of [NOISE_CELL] px cells, new for each [frame]. */
        private fun noiseFor(frame: Long): Bitmap {
            val columns = (width / NOISE_CELL).coerceAtLeast(1)
            val rows = (height / NOISE_CELL).coerceAtLeast(1)
            val current = noise?.takeIf { it.width == columns && it.height == rows }
            if (current != null && frame == noiseAt) return current
            val pixels = IntArray(columns * rows) { if (Random.nextBoolean()) Color.WHITE else Color.BLACK }
            return Bitmap.createBitmap(pixels, columns, rows, Bitmap.Config.ARGB_8888).also {
                noise?.recycle()
                noise = it
                noiseAt = frame
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            noise?.recycle()
            negative?.recycle()
        }
    }

    private companion object {
        /** One pass of the bar from top to bottom. */
        const val PASS_MS = 2_500L
        const val BAR_PARTS = 8f
        const val NOISE_CELL = 4
        const val TEXT_SP = 14f
    }
}
