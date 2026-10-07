package io.github.thatonecodingperson.thortools.input

/** AYN's own pad among the input devices, and its raw axes, for the root helper's pad reader. Pure. */
object ThorPad {
    const val VENDOR = 0x2020
    private const val PRODUCT_XBOX_LAYOUT = 0x0112
    private const val PRODUCT_STANDARD_LAYOUT = 0x0111

    const val ABS_X = 0
    const val ABS_Y = 1
    const val ABS_Z = 2
    const val ABS_RZ = 5
    const val ABS_GAS = 9
    const val ABS_BRAKE = 10
    const val ABS_HAT0X = 16
    const val ABS_HAT0Y = 17

    /**
     * The Thor's own pad: vendor 0x2020 with product 0x0112 (Xbox layout), or 0x0111 under a name no other vendor's
     * device has (AYN re-emits an external pad as 2020:0111 under that pad's own name). It has sticks and a D-pad, which
     * leaves out AYN's virtual mouse.
     */
    /** The pad in its Xbox style, where the face buttons arrive by position. */
    fun isXboxStyle(node: InputNode): Boolean = node.product == PRODUCT_XBOX_LAYOUT

    fun pick(nodes: List<InputNode>): InputNode? {
        val foreignNames = nodes.filter { it.vendor != VENDOR }.map { it.name }.toSet()
        return nodes.firstOrNull { node ->
            node.vendor == VENDOR &&
                node.hasAbs(ABS_X) &&
                node.hasAbs(ABS_HAT0X) &&
                (node.product == PRODUCT_XBOX_LAYOUT || (node.product == PRODUCT_STANDARD_LAYOUT && node.name !in foreignNames))
        }
    }

    /** sysfs `capabilities/abs`: hex words, the most significant first; every axis read here is in the last one. */
    fun parseAbs(text: String): Long = text.trim().split(' ').lastOrNull()?.toULongOrNull(16)?.toLong() ?: 0L

    private fun InputNode.hasAbs(code: Int): Boolean = ((abs shr code) and 1L) == 1L
}

/** The pad's axes as its raw events leave them; one reader thread at a time. */
class RawPad {
    private val values = IntArray(ThorPad.ABS_HAT0Y + 1)

    /** An `EV_ABS` event. */
    fun onAbs(code: Int, value: Int) {
        if (code in values.indices) values[code] = value
    }

    /** A new device starts centred: it reports an axis only when it changes. */
    fun reset() = values.fill(0)

    /** As Android reports it: sticks -1..1 (up negative), the D-pad -1, 0 or 1. */
    fun sample() = PadSample(
        hatX = values[ThorPad.ABS_HAT0X].toFloat(),
        hatY = values[ThorPad.ABS_HAT0Y].toFloat(),
        leftX = stick(ThorPad.ABS_X),
        leftY = stick(ThorPad.ABS_Y),
        rightX = stick(ThorPad.ABS_Z),
        rightY = stick(ThorPad.ABS_RZ),
        leftTrigger = trigger(ThorPad.ABS_BRAKE),
        rightTrigger = trigger(ThorPad.ABS_GAS),
    )

    private fun stick(code: Int): Float = (values[code] / STICK_MAX).coerceIn(-1f, 1f)

    private fun trigger(code: Int): Float = (values[code] / STICK_MAX).coerceIn(0f, 1f)

    private companion object {
        const val STICK_MAX = 32767f
    }
}
