package io.github.thatonecodingperson.thortools.oled

import kotlin.math.abs

/**
 * Still areas as first built (the default; [StillAreas] is the experimental one): the bright parts of a screen
 * that stay put while the rest moves: a game's HUD, a logo, a status bar. Fed small copies
 * of the screen one after another, it follows each pixel (a change above [PIXEL_CHANGE] of 255 counts), then looks at
 * blocks of [block] pixels: a block where at least a quarter of the pixels are bright and unchanged for the asked time
 * counts, and touching blocks join into one area. When most of the screen is still, there are no areas: that is a
 * still screen, which the shifter and the refresher look after. Pure.
 */
class ClassicAreas(private val block: Int = 8) {
    private var width = 0
    private var height = 0
    private var luma = IntArray(0)
    private var since = LongArray(0)

    fun update(sample: Sample, now: Long) {
        if (sample.width != width || sample.height != height) {
            width = sample.width
            height = sample.height
            luma = IntArray(width * height) { StillMap.luma(sample.pixels[it]) }
            since = LongArray(width * height) { now }
            return
        }
        for (i in luma.indices) {
            val next = StillMap.luma(sample.pixels[i])
            if (abs(next - luma[i]) > PIXEL_CHANGE) since[i] = now
            luma[i] = next
        }
    }

    /** The areas still for [stillMs] at [now], in the copy's pixels, biggest first, at most [MAX_AREAS]. */
    fun areas(now: Long, stillMs: Long): List<Box> {
        if (width == 0) return emptyList()
        val columns = (width + block - 1) / block
        val rows = (height + block - 1) / block
        val marked = BooleanArray(columns * rows)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                var count = 0
                var total = 0
                for (y in row * block until minOf(height, (row + 1) * block)) {
                    for (x in column * block until minOf(width, (column + 1) * block)) {
                        val i = y * width + x
                        total++
                        if (luma[i] >= MIN_BRIGHTNESS && now - since[i] >= stillMs) count++
                    }
                }
                marked[row * columns + column] = count * BLOCK_SHARE_DIVISOR >= total
            }
        }
        val areas = joined(marked, columns, rows).map { box ->
            Box(box.left * block, box.top * block, minOf(width, box.right * block), minOf(height, box.bottom * block))
        }
        val covered = areas.sumOf { (it.right - it.left) * (it.bottom - it.top) }
        if (covered * 2 > width * height) return emptyList()
        return areas.sortedByDescending { (it.right - it.left) * (it.bottom - it.top) }.take(MAX_AREAS)
    }

    /** The copy's pixels inside [box] that are bright and have been still for [stillMs] at [now]. */
    fun stillPixels(box: Box, now: Long, stillMs: Long): IntArray {
        val found = mutableListOf<Int>()
        for (y in box.top.coerceAtLeast(0) until box.bottom.coerceAtMost(height)) {
            for (x in box.left.coerceAtLeast(0) until box.right.coerceAtMost(width)) {
                val i = y * width + x
                if (luma[i] >= MIN_BRIGHTNESS && now - since[i] >= stillMs) found += i
            }
        }
        return found.toIntArray()
    }

    /** Whether any of [pixels] (from [stillPixels]) changed after [after]. */
    fun anyChanged(pixels: IntArray, after: Long): Boolean = pixels.any { it < since.size && since[it] > after }

    /** Touching marked blocks (sideways and corners) joined, as boxes of blocks. */
    private fun joined(marked: BooleanArray, columns: Int, rows: Int): List<Box> {
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
            boxes += Box(left, top, right, bottom)
        }
        return boxes
    }

    companion object {
        const val PIXEL_CHANGE = 6
        const val MIN_BRIGHTNESS = 64
        const val MAX_AREAS = 12

        /** A block counts when at least 1 / [BLOCK_SHARE_DIVISOR] of its pixels are bright and still. */
        const val BLOCK_SHARE_DIVISOR = 4
    }
}
