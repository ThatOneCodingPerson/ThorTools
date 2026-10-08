package io.github.thatonecodingperson.thortools.input

/**
 * Thor Tools' copy of AYN's pad, which apps see while desktop controls keep sticks from them: how it is described to
 * Android's uinput tool, which events it passes on, and when the real pad may be taken. Pure.
 *
 * The copy has AYN's own name, vendor and product, so Android gives it the pad's key layout and AYN's software leaves
 * it alone (AYN hides any other new gamepad and sends it on as one of its own). The uinput tool always makes version
 * [VERSION], which tells the copy from AYN's pad (version 0).
 */
object PadCopyPlan {
    const val VERSION = 1
    const val COPY_ID = 1

    private const val EV_SYN = 0
    private const val EV_KEY = 1
    private const val EV_ABS = 3
    private const val SYN_REPORT = 0
    private const val UI_SET_EVBIT = 100
    private const val UI_SET_KEYBIT = 101
    private const val UI_SET_ABSBIT = 103
    private const val WORD_BITS = 64

    /** An axis's range, as `getevent -p` prints it. */
    data class AbsInfo(val min: Int, val max: Int, val fuzz: Int = 0, val flat: Int = 0, val resolution: Int = 0)

    /** What AYN's pad reports, for a `getevent -p` that can't be read. */
    val KNOWN_AXES: Map<Int, AbsInfo> = mapOf(
        ThorPad.ABS_X to AbsInfo(-32767, 32767, flat = 15),
        ThorPad.ABS_Y to AbsInfo(-32767, 32767, flat = 15),
        ThorPad.ABS_Z to AbsInfo(-32767, 32767, flat = 15),
        ThorPad.ABS_RZ to AbsInfo(-32767, 32767, flat = 15),
        ThorPad.ABS_GAS to AbsInfo(0, 32767),
        ThorPad.ABS_BRAKE to AbsInfo(0, 32767),
        ThorPad.ABS_HAT0X to AbsInfo(-1, 1),
        ThorPad.ABS_HAT0Y to AbsInfo(-1, 1),
    )

    /** The stick axes kept from apps: the left stick's, the right stick's, or both. */
    fun hiddenAxes(left: Boolean, right: Boolean): Set<Int> = buildSet {
        if (left) addAll(listOf(ThorPad.ABS_X, ThorPad.ABS_Y))
        if (right) addAll(listOf(ThorPad.ABS_Z, ThorPad.ABS_RZ))
    }

    /** The codes set in a sysfs capability bitmap (`capabilities/key`): hex words, the most significant first. */
    fun bits(capability: String): List<Int> {
        val words = capability.trim().split(' ').filter { it.isNotEmpty() }.reversed()
        return words.flatMapIndexed { index, word ->
            val value = word.toULongOrNull(16) ?: 0UL
            (0 until WORD_BITS).filter { bit -> (value shr bit) and 1UL == 1UL }.map { bit -> index * WORD_BITS + bit }
        }
    }

    /** The axes and their ranges in `getevent -p <node>` output. */
    fun axes(getevent: String): Map<Int, AbsInfo> = AXIS_LINE.findAll(getevent).associate { match ->
        val v = match.groupValues
        v[1].toInt(16) to
            AbsInfo(min = v[3].toInt(), max = v[4].toInt(), fuzz = v[5].toInt(), flat = v[6].toInt(), resolution = v[7].toInt())
    }

    /** The uinput tool's `register` line for a copy named [name] with [keys] and [axes]. */
    fun register(name: String, vendor: Int, product: Int, keys: List<Int>, axes: Map<Int, AbsInfo>): String {
        val codes = axes.keys.sorted()
        val config = buildList {
            add("{\"type\":$UI_SET_EVBIT,\"data\":[$EV_KEY,$EV_ABS]}")
            add("{\"type\":$UI_SET_KEYBIT,\"data\":${keys.sorted().joinToString(",", "[", "]")}}")
            add("{\"type\":$UI_SET_ABSBIT,\"data\":${codes.joinToString(",", "[", "]")}}")
        }.joinToString(",")
        val absInfo = codes.joinToString(",", "[", "]") { code ->
            val a = axes.getValue(code)
            "{\"code\":$code,\"info\":{\"value\":0,\"minimum\":${a.min},\"maximum\":${a.max},\"fuzz\":${a.fuzz}," +
                "\"flat\":${a.flat},\"resolution\":${a.resolution}}}"
        }
        return "{\"id\":$COPY_ID,\"command\":\"register\",\"name\":\"${json(name)}\",\"vid\":$vendor,\"pid\":$product," +
            "\"bus\":\"usb\",\"configuration\":[$config],\"abs_info\":$absInfo}\n"
    }

    /** The uinput tool's `inject` line for one frame of events (type, code, value triplets), ending it with a report. */
    fun inject(events: List<Int>): String =
        "{\"id\":$COPY_ID,\"command\":\"inject\",\"events\":${(events + listOf(EV_SYN, SYN_REPORT, 0)).joinToString(",", "[", "]")}}\n"

    /**
     * The real pad may be taken only while nothing on it is in use: Android keeps the last state it saw from the real
     * pad, so a key held or a stick pushed then would stay down there (a stick held past half its way keeps an app's
     * focus moving).
     */
    fun atRest(keysDown: Set<Int>, pad: PadSample): Boolean = keysDown.isEmpty() &&
        pad.hatX == 0f &&
        pad.hatY == 0f &&
        maxOf(kotlin.math.abs(pad.leftX), kotlin.math.abs(pad.leftY), kotlin.math.abs(pad.rightX), kotlin.math.abs(pad.rightY)) < REST

    private fun json(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")

    /** A stick this far out (of 1) is at rest: Android turns the left stick into D-pad presses only past half its way. */
    private const val REST = 0.25f

    private val AXIS_LINE =
        Regex("""([0-9a-f]{4})\s+:\s+value (-?\d+), min (-?\d+), max (-?\d+), fuzz (-?\d+), flat (-?\d+), resolution (-?\d+)""")
}

/**
 * The real pad's events on their way to the copy: everything but the [hidden] axes is passed on at each frame's
 * report, and what the copy has down is followed (going off gently waits until no key is down). One thread at a time
 * (the caller locks).
 */
class CopyFrame {
    private val events = mutableListOf<Int>()

    /** The keys the copy has down now. */
    val keysDown = mutableSetOf<Int>()

    /** The axes the copy holds away from rest. */
    val axesOut = mutableSetOf<Int>()

    /** The axes kept from the copy. */
    var hidden: Set<Int> = emptySet()
        private set

    /** Every axis's last value on the real pad, hidden or not. */
    private val last = mutableMapOf<Int, Int>()

    /**
     * Keeps [axes] from the copy from now on. A stick held still sends nothing, so the change can't wait for the next
     * event: the line returned (null when nothing changes) puts each newly hidden axis back to rest on the copy and gives
     * each newly shown one the real pad's value.
     */
    fun hide(axes: Set<Int>): String? {
        val frame = buildList {
            (axes - hidden).filter { it in axesOut }.sorted().forEach { axis ->
                addAll(listOf(EV_ABS, axis, 0))
                axesOut -= axis
            }
            (hidden - axes).sorted().forEach { axis ->
                val value = last[axis] ?: 0
                if (value != 0) {
                    addAll(listOf(EV_ABS, axis, value))
                    axesOut += axis
                }
            }
        }
        hidden = axes
        return if (frame.isEmpty()) null else PadCopyPlan.inject(frame)
    }

    /** One raw event; at a report, the frame's line for the uinput tool, or null when there is nothing to pass on. */
    fun onEvent(type: Int, code: Int, value: Int): String? {
        when (type) {
            EV_SYN -> return when (code) {
                SYN_REPORT -> if (events.isEmpty()) null else PadCopyPlan.inject(events.toList()).also { events.clear() }
                // Events were lost: what was gathered so far may be half a frame.
                SYN_DROPPED -> null.also { events.clear() }
                else -> null
            }
            EV_KEY -> {
                // A repeat (2) carries nothing a press didn't; the copy makes its own.
                if (value == 2) return null
                if (value == 1) keysDown += code else keysDown -= code
            }
            EV_ABS -> {
                last[code] = value
                if (code in hidden) return null
                if (value == 0) axesOut -= code else axesOut += code
            }
            else -> return null
        }
        events += listOf(type, code, value)
        return null
    }

    private companion object {
        const val EV_SYN = 0
        const val EV_KEY = 1
        const val EV_ABS = 3
        const val SYN_REPORT = 0
        const val SYN_DROPPED = 3
    }
}
