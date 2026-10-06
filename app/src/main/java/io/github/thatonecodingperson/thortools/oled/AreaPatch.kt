package io.github.thatonecodingperson.thortools.oled

import kotlin.math.abs

/**
 * Moves only the still part of an area: from two full-size copies of the same [width] x [height] patch taken a step
 * apart, the pixels inside [inner] that stayed the same (the HUD, not the moving game behind it) are blacked out where
 * they are and drawn again [offset] away. Everything else stays see-through. Returns ARGB pixels for a layer over the
 * patch; null when the copies don't match in size. Pure.
 */
object AreaPatch {
    const val TOLERANCE = 6

    fun build(current: IntArray, previous: IntArray, width: Int, height: Int, inner: Box, offset: Offset): IntArray? {
        if (current.size != width * height || previous.size != current.size) return null
        val out = IntArray(current.size)
        val still = BooleanArray(current.size)
        for (y in inner.top.coerceAtLeast(0) until inner.bottom.coerceAtMost(height)) {
            for (x in inner.left.coerceAtLeast(0) until inner.right.coerceAtMost(width)) {
                val i = y * width + x
                if (abs(StillMap.luma(current[i]) - StillMap.luma(previous[i])) <= TOLERANCE) {
                    still[i] = true
                    out[i] = BLACK
                }
            }
        }
        for (i in still.indices) {
            if (!still[i]) continue
            val x = i % width + offset.x
            val y = i / width + offset.y
            if (x in 0 until width && y in 0 until height) out[y * width + x] = current[i] or OPAQUE
        }
        return out
    }

    private const val BLACK = 0xFF000000.toInt()
    private const val OPAQUE = 0xFF000000.toInt()
}
