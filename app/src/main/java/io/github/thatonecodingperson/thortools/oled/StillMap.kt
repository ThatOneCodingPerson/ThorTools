package io.github.thatonecodingperson.thortools.oled

import kotlin.math.abs

/**
 * Which areas of one screen keep the same picture, from tiny copies taken one after another. The copy is split into
 * square cells of [cell] pixels; a cell whose brightness moves by more than [CELL_CHANGE] has changed. The screen as a
 * whole counts as still while at most [MOVING_SHARE] of its cells (at least one) change, so a ticking clock or a
 * blinking cursor doesn't keep it "moving". Pure.
 */
class StillMap(private val cell: Int = 4) {
    var columns = 0
        private set
    var rows = 0
        private set
    private var brightness = IntArray(0)
    private var since = LongArray(0)

    /** Takes the copy [sample] made at [now]; true when the screen stayed still since the last one. */
    fun update(sample: Sample, now: Long): Boolean {
        val next = cells(sample)
        if (next.size != brightness.size) {
            brightness = next
            since = LongArray(next.size) { now }
            return false
        }
        var changed = 0
        for (i in next.indices) {
            if (abs(next[i] - brightness[i]) > CELL_CHANGE) {
                changed++
                since[i] = now
            }
        }
        brightness = next
        return changed <= maxOf(1, (next.size * MOVING_SHARE).toInt())
    }

    /** Since when cell [index] (row by row) has shown the same picture. */
    fun stillSince(index: Int): Long = since[index]

    /** Cell [index]'s brightness, 0 to 255. */
    fun brightness(index: Int): Int = brightness[index]

    private fun cells(sample: Sample): IntArray {
        columns = (sample.width + cell - 1) / cell
        rows = (sample.height + cell - 1) / cell
        val sums = LongArray(columns * rows)
        val counts = IntArray(columns * rows)
        for (y in 0 until sample.height) {
            for (x in 0 until sample.width) {
                val pixel = sample.pixels[y * sample.width + x]
                val index = (y / cell) * columns + x / cell
                sums[index] += luma(pixel)
                counts[index]++
            }
        }
        return IntArray(sums.size) { (sums[it] / counts[it].coerceAtLeast(1)).toInt() }
    }

    companion object {
        const val CELL_CHANGE = 3
        const val MOVING_SHARE = 0.01

        /** Perceived brightness of an ARGB pixel, 0 to 255. */
        fun luma(pixel: Int): Int = (((pixel shr 16) and 0xFF) * 299 + ((pixel shr 8) and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000
    }
}
