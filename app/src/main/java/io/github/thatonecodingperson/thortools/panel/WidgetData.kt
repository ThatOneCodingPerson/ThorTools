package io.github.thatonecodingperson.thortools.panel

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A point of a drawn note, as a fraction of the drawing's width and height (0..1), so it survives a resize. */
data class NotePoint(val x: Float, val y: Float)

/** One pen stroke: [color] is a palette slot (0 = the text colour, 1 to 4 the chart colours). */
data class NoteStroke(val color: Int, val points: List<NotePoint>)

/** A note's drawing as text, one stroke per line: `color:x,y x,y ...`. Pure. */
object NoteStrokes {
    const val MAX_STROKES = 300
    const val MAX_POINTS = 1500
    const val COLORS = 5

    fun encode(strokes: List<NoteStroke>): String = strokes.joinToString("\n") { stroke ->
        "${stroke.color}:" + stroke.points.joinToString(" ") { "${round(it.x)},${round(it.y)}" }
    }

    /** Unreadable lines and points are skipped. */
    fun decode(text: String?): List<NoteStroke> = text.orEmpty().lines().mapNotNull { line ->
        val color = line.substringBefore(':', "").toIntOrNull()?.takeIf { it in 0 until COLORS } ?: return@mapNotNull null
        val points = line.substringAfter(':').split(' ').mapNotNull { pair ->
            val (x, y) = pair.split(',').takeIf { it.size == 2 }?.map { it.toFloatOrNull() ?: return@mapNotNull null }
                ?: return@mapNotNull null
            NotePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }
        NoteStroke(color, points.take(MAX_POINTS)).takeIf { points.isNotEmpty() }
    }.takeLast(MAX_STROKES)

    /** Adds [stroke], dropping the oldest when there are too many. */
    fun add(strokes: List<NoteStroke>, stroke: NoteStroke): List<NoteStroke> =
        if (stroke.points.isEmpty()) strokes else (strokes + stroke.copy(points = stroke.points.take(MAX_POINTS))).takeLast(MAX_STROKES)

    /** The eraser takes away every stroke that passes within [radius] of ([x], [y]) (all as fractions of the width). */
    fun erase(strokes: List<NoteStroke>, x: Float, y: Float, radius: Float): List<NoteStroke> =
        strokes.filterNot { stroke -> stroke.points.any { hypot(it.x - x, it.y - y) <= radius } }

    private fun round(value: Float): String = ((value * PRECISION).roundToInt() / PRECISION).toString()

    private const val PRECISION = 1000f
}

/** One second of the performance graph; null where a value couldn't be read. */
data class GraphPoint(val time: Long, val cpuLoad: Float?, val gpuLoad: Float?, val cpuTemp: Float?)

/** The last minute of [GraphPoint]s, oldest first. Pure. */
class StatsHistory(private val windowMs: Long = WINDOW_MS) {
    private val points = ArrayDeque<GraphPoint>()

    fun add(point: GraphPoint): List<GraphPoint> {
        points.addLast(point)
        while (points.isNotEmpty() && point.time - points.first().time > windowMs) points.removeFirst()
        return points.toList()
    }

    companion object {
        const val WINDOW_MS = 60_000L

        /** Temperatures are drawn on this scale, in °C. */
        const val TEMP_MIN = 30f
        const val TEMP_MAX = 90f

        fun tempFraction(celsius: Float): Float = ((celsius - TEMP_MIN) / (TEMP_MAX - TEMP_MIN)).coerceIn(0f, 1f)
    }
}

/** Time estimates for the battery widget. Pure. */
object BatteryEstimate {
    /** Android's own estimate to full ([ms], -1 when it has none), in minutes. */
    fun toFullMinutes(ms: Long): Int? = ms.takeIf { it > 0 }?.let { (it / 60_000).toInt().coerceAtLeast(1) }

    /**
     * Minutes left at today's drain: the charge left ([chargeCounter], µAh) over the current ([current], µA; its sign
     * differs between devices). Some drivers report mA, which [StatsParser.watts] handles the same way.
     */
    fun leftMinutes(chargeCounter: Long, current: Long): Int? {
        if (chargeCounter <= 0 || current == 0L || current == Long.MIN_VALUE) return null
        val microAmps = if (abs(current) < 20_000) abs(current) * 1000 else abs(current)
        return (chargeCounter * 60 / microAmps).toInt().takeIf { it in 1..MAX_MINUTES }
    }

    private const val MAX_MINUTES = 48 * 60
}
