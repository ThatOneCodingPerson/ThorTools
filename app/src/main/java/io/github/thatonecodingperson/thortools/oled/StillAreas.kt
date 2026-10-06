package io.github.thatonecodingperson.thortools.oled

import kotlin.math.abs

/**
 * The bright parts of a screen that stay put while the rest moves: a game's HUD, a logo, a status bar. Fed small copies
 * of the screen one after another, it follows each pixel (a change of more than [PIXEL_CHANGE] in any colour channel
 * counts) and judges blocks of [block] pixels:
 * - a block becomes protected once at least a quarter of its pixels are bright (strongest channel [BRIGHT] or more,
 *   so a pure blue element counts) and unchanged for the asked time;
 * - it stays protected while at least a quarter of its pixels are bright and steady for [KEEP_MS], so a number
 *   updating or a blink doesn't end it, and is let go only after failing that for [RELEASE_MS] in a row (the content
 *   went away);
 * - its dim strength follows how bright it is, white being full strength.
 *
 * While most of the screen would be protected, nothing is shown: that is a still screen, which the shifter and the
 * refresher look after. Pure.
 */
class StillAreas(private val block: Int = 4) {
    var columns = 0
        private set
    var rows = 0
        private set
    private var width = 0
    private var height = 0
    private var previous = IntArray(0)
    private var since = LongArray(0)
    private var guarded = BooleanArray(0)
    private var lostSince = LongArray(0)
    private var strength = FloatArray(0)

    /** Takes the copy [sample] made at [now]; blocks become protected after [stillMs] still. */
    fun update(sample: Sample, now: Long, stillMs: Long) {
        if (sample.width != width || sample.height != height) {
            width = sample.width
            height = sample.height
            columns = (width + block - 1) / block
            rows = (height + block - 1) / block
            previous = sample.pixels.copyOf()
            since = LongArray(width * height) { now }
            guarded = BooleanArray(columns * rows)
            lostSince = LongArray(columns * rows)
            strength = FloatArray(columns * rows)
            return
        }
        for (i in previous.indices) {
            if (difference(previous[i], sample.pixels[i]) > PIXEL_CHANGE) since[i] = now
            previous[i] = sample.pixels[i]
        }
        for (row in 0 until rows) {
            for (column in 0 until columns) judge(row * columns + column, column, row, now, stillMs)
        }
    }

    private fun judge(index: Int, column: Int, row: Int, now: Long, stillMs: Long) {
        var total = 0
        var bright = 0
        var brightStill = 0
        var brightSteady = 0
        var level = 0L
        for (y in row * block until minOf(height, (row + 1) * block)) {
            for (x in column * block until minOf(width, (column + 1) * block)) {
                val i = y * width + x
                total++
                val strongest = strongest(previous[i])
                if (strongest < BRIGHT) continue
                bright++
                level += strongest
                if (now - since[i] >= stillMs) brightStill++
                if (now - since[i] >= KEEP_MS) brightSteady++
            }
        }
        strength[index] = if (bright == 0) 0f else level.toFloat() / bright / MAX_LEVEL
        if (!guarded[index]) {
            if (brightStill * QUARTER >= total) {
                guarded[index] = true
                lostSince[index] = 0
            }
            return
        }
        if (brightSteady * QUARTER >= total) {
            lostSince[index] = 0
        } else if (lostSince[index] == 0L) {
            lostSince[index] = now
        } else if (now - lostSince[index] >= RELEASE_MS) {
            guarded[index] = false
        }
    }

    /** The protected blocks, row by row; none while most of the screen is still. */
    fun mask(): BooleanArray {
        val count = guarded.count { it }
        return if (count * 2 > guarded.size) BooleanArray(guarded.size) else guarded.copyOf()
    }

    /** How strongly block [index] is dimmed, 0 to 1 (white is 1). */
    fun strength(index: Int): Float = strength[index]

    /**
     * Each block's dim, 0 to [full], row by row: each joined part of protected blocks evenly as dark as its brightest
     * block, so a part is dimmed as a whole, and the ring of blocks around it at half that, so the soft edge fades out
     * outside the part instead of showing inside it.
     */
    fun dimAlphas(full: Int): IntArray {
        val mask = mask()
        val grown = BooleanArray(mask.size)
        for (i in mask.indices) {
            if (!mask[i]) continue
            neighbours(i) { grown[it] = true }
            grown[i] = true
        }
        val alphas = IntArray(mask.size)
        val seen = BooleanArray(mask.size)
        for (start in grown.indices) {
            if (!grown[start] || seen[start]) continue
            val part = mutableListOf<Int>()
            val queue = ArrayDeque(listOf(start))
            seen[start] = true
            while (queue.isNotEmpty()) {
                val at = queue.removeFirst()
                part += at
                neighbours(at) { next ->
                    if (grown[next] && !seen[next]) {
                        seen[next] = true
                        queue.addLast(next)
                    }
                }
            }
            val brightest = part.filter { mask[it] }.maxOfOrNull { strength[it] } ?: 0f
            val alpha = (full * brightest).toInt().coerceIn(0, full)
            part.forEach { alphas[it] = if (mask[it]) alpha else alpha / 2 }
        }
        return alphas
    }

    /** The up to eight blocks around block [index]. */
    private inline fun neighbours(index: Int, action: (Int) -> Unit) {
        val x = index % columns
        val y = index / columns
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until columns && ny in 0 until rows) action(ny * columns + nx)
            }
        }
    }

    /** The share of the screen that is protected now, in percent. */
    fun protectedPercent(): Int {
        val shown = mask()
        return if (shown.isEmpty()) 0 else shown.count { it } * 100 / shown.size
    }

    /** Touching protected blocks joined into boxes (copy pixels), biggest first, at most [MAX_AREAS]. */
    fun components(): List<Box> {
        val marked = mask()
        val seen = BooleanArray(marked.size)
        val boxes = mutableListOf<Box>()
        for (start in marked.indices) {
            if (!marked[start] || seen[start]) continue
            var left = columns
            var top = rows
            var right = 0
            var bottom = 0
            val queue = ArrayDeque(listOf(start))
            seen[start] = true
            while (queue.isNotEmpty()) {
                val at = queue.removeFirst()
                val x = at % columns
                val y = at / columns
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x + 1)
                bottom = maxOf(bottom, y + 1)
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until columns || ny !in 0 until rows) continue
                        val next = ny * columns + nx
                        if (marked[next] && !seen[next]) {
                            seen[next] = true
                            queue.addLast(next)
                        }
                    }
                }
            }
            boxes += Box(left * block, top * block, minOf(width, right * block), minOf(height, bottom * block))
        }
        return boxes.sortedByDescending { (it.right - it.left) * (it.bottom - it.top) }.take(MAX_AREAS)
    }

    /** The copy's pixels inside [box] that are bright and have been still for [stillMs] at [now]. */
    fun stillPixels(box: Box, now: Long, stillMs: Long): IntArray {
        val found = mutableListOf<Int>()
        for (y in box.top.coerceAtLeast(0) until box.bottom.coerceAtMost(height)) {
            for (x in box.left.coerceAtLeast(0) until box.right.coerceAtMost(width)) {
                val i = y * width + x
                if (strongest(previous[i]) >= BRIGHT && now - since[i] >= stillMs) found += i
            }
        }
        return found.toIntArray()
    }

    /** Whether any of [pixels] (from [stillPixels]) changed after [after]. */
    fun anyChanged(pixels: IntArray, after: Long): Boolean = pixels.any { it < since.size && since[it] > after }

    companion object {
        const val PIXEL_CHANGE = 6
        const val BRIGHT = 80
        const val KEEP_MS = 3_000L
        const val RELEASE_MS = 5_000L
        const val MAX_AREAS = 16
        private const val QUARTER = 4
        private const val MAX_LEVEL = 255f

        /** The strongest colour channel of an ARGB pixel, 0 to 255. */
        fun strongest(pixel: Int): Int = maxOf((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)

        /** The biggest change of any colour channel between two ARGB pixels. */
        fun difference(a: Int, b: Int): Int = maxOf(
            abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
            abs((a and 0xFF) - (b and 0xFF)),
        )
    }
}
