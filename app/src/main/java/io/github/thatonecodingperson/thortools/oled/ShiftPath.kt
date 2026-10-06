package io.github.thatonecodingperson.thortools.oled

/** A pixel offset of the whole picture. */
data class Offset(val x: Int, val y: Int)

/**
 * Every whole-pixel offset within [radius] of the middle, in the order the picture visits them: row by row, each row in
 * the opposite direction of the one before, down and back up again, so each step moves the picture by about one pixel
 * and it never jumps across the circle. Pure.
 */
class ShiftPath(radius: Int) {
    val points: List<Offset>

    init {
        val r = radius.coerceAtLeast(0)
        val down = (-r..r).mapIndexed { row, y ->
            val half = halfWidth(r, y)
            val xs = (-half..half).toList()
            (if (row % 2 == 0) xs else xs.reversed()).map { x -> Offset(x, y) }
        }.flatten()
        // Back up the same way, without visiting the turning points twice.
        points = if (down.size <= 1) down else down + down.reversed().subList(1, down.size - 1)
    }

    val size: Int get() = points.size

    /** Where the top screen starts: the middle, on the way down. */
    val topStart: Int get() = points.indexOf(Offset(0, 0))

    /** Where the bottom screen starts: the middle, on the way back up, so the two screens move apart. */
    val bottomStart: Int get() = points.lastIndexOf(Offset(0, 0))

    fun at(index: Int): Offset = points[Math.floorMod(index, size)]

    private fun halfWidth(r: Int, y: Int): Int {
        var x = 0
        while ((x + 1) * (x + 1) + y * y <= r * r) x++
        return x
    }
}
