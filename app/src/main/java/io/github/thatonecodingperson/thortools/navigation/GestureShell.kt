package io.github.thatonecodingperson.thortools.navigation

/**
 * The back swipe's three settings as text, null when unset: the edge width scales for the left and right edge (both
 * screens) and AYN's switch for the bottom screen's back swipe.
 */
data class BackValues(val left: String?, val right: String?, val bottom: String?) {
    /** Thor Tools' own values are in place on all three. */
    val ours: Boolean get() = isZero(left) && isZero(right) && bottom == GestureShell.BOTTOM_OFF

    fun encode(): String = listOf(left, right, bottom).joinToString(SEPARATOR) { it ?: UNSET }

    companion object {
        /** Android's and AYN's defaults: no scale set, the bottom back swipe on. */
        val DEFAULT = BackValues(null, null, "0")

        private const val SEPARATOR = "|"
        private const val UNSET = "null"

        fun decode(text: String?): BackValues? {
            val parts = text?.split(SEPARATOR)?.takeIf { it.size == 3 } ?: return null
            val values = parts.map { it.takeUnless { value -> value == UNSET } }
            return BackValues(values[0], values[1], values[2])
        }

        fun isZero(value: String?): Boolean = value?.toFloatOrNull() == 0f
    }
}

/** The navigation state as read back from the system. */
data class GestureReading(
    val parts: GestureParts,
    val back: BackValues,
    /** AYN's home bar switch for the top screen. */
    val topBarHidden: Boolean,
    /** Thor Tools' see-through handle overlays are both enabled. */
    val handleHidden: Boolean,
)

/** The root commands behind the gesture switches, and reading them back. */
object GestureShell {
    const val KEY_SCALE_LEFT = "back_gesture_inset_scale_left"
    const val KEY_SCALE_RIGHT = "back_gesture_inset_scale_right"
    const val KEY_BOTTOM_BACK = "second_disable_back_gesture"
    const val KEY_TOP_BAR = "hide_nav_bar"
    const val BOTTOM_OFF = "1"

    /** Status bar disable flags (`View.STATUS_BAR_DISABLE_HOME` and `_RECENT`): with both set Launcher won't start the swipe. */
    const val DISABLE_HOME = 0x00200000L
    const val DISABLE_RECENT = 0x01000000L

    private const val SYSTEMUI = "com.android.systemui"
    private const val HANDLE = "thortools_handle_"
    private val HANDLE_COLORS = listOf("light", "dark")

    /** A value read from the settings goes back into a command only if it is a plain number. */
    private val NUMBER = Regex("[0-9.eE+-]{1,16}")

    fun home(works: Boolean): String = "cmd statusbar send-disable-flag " + if (works) "none" else "home recents"

    /** No edge left to swipe from on either screen, and AYN's own switch for the bottom one (which hides its bar too). */
    val BACK_OFF = listOf(
        "settings put secure $KEY_SCALE_LEFT 0",
        "settings put secure $KEY_SCALE_RIGHT 0",
        "settings put global $KEY_BOTTOM_BACK $BOTTOM_OFF",
    ).joinToString("\n")

    /** Puts [saved] back key by key, skipping a key the user changed since (it no longer holds Thor Tools' value). */
    fun backRestore(saved: BackValues, now: BackValues): String = buildList {
        if (BackValues.isZero(now.left)) add(put("secure", KEY_SCALE_LEFT, saved.left))
        if (BackValues.isZero(now.right)) add(put("secure", KEY_SCALE_RIGHT, saved.right))
        if (now.bottom == BOTTOM_OFF) add(put("global", KEY_BOTTOM_BACK, saved.bottom))
    }.joinToString("\n")

    fun topBar(hidden: Boolean): String = "settings put global $KEY_TOP_BAR ${if (hidden) 1 else 0}"

    /** Thor Tools' own overlays that colour SystemUI's home handle see-through; fabricated by root, owned by the shell. */
    fun handle(hidden: Boolean): String = if (hidden) {
        HANDLE_COLORS.flatMap { color ->
            listOf(
                "cmd overlay fabricate --target $SYSTEMUI --name $HANDLE$color " +
                    "$SYSTEMUI:color/navigation_bar_home_handle_${color}_color 0x1c 0x00000000",
                "cmd overlay enable com.android.shell:$HANDLE$color",
            )
        }
    } else {
        HANDLE_COLORS.map { color -> "cmd overlay disable com.android.shell:$HANDLE$color" }
    }.joinToString("\n")

    /** For [parse]: the four settings, display 0's disable flags and the handle overlays. */
    val READ = listOf(
        "printf 'left='; settings get secure $KEY_SCALE_LEFT",
        "printf 'right='; settings get secure $KEY_SCALE_RIGHT",
        "printf 'bottom='; settings get global $KEY_BOTTOM_BACK",
        "printf 'bar='; settings get global $KEY_TOP_BAR",
        "dumpsys statusbar | grep -A1 -E '^ *displayId=0$'",
        "cmd overlay list $SYSTEMUI | grep $HANDLE",
    ).joinToString("\n")

    /** Null when the output isn't what [READ] prints (no root, or the shell failed). */
    fun parse(output: String): GestureReading? {
        val lines = output.lines().map { it.trim() }
        val names = listOf("left", "right", "bottom", "bar")
        if (names.any { name -> lines.none { it.startsWith("$name=") } }) return null
        fun value(name: String): String? = lines.first { it.startsWith("$name=") }.substringAfter('=').takeUnless {
            it.isEmpty() ||
                it == "null"
        }
        val display0 = lines.indexOf("displayId=0")
        val flags = lines.getOrNull(display0 + 1)
            ?.takeIf { display0 >= 0 && it.startsWith("mDisabled1=0x") }
            ?.substringAfter("0x")
            ?.toLongOrNull(16) ?: 0L
        val back = BackValues(value("left"), value("right"), value("bottom"))
        return GestureReading(
            parts = GestureParts(
                homeSwipe = (flags and DISABLE_HOME) == 0L || (flags and DISABLE_RECENT) == 0L,
                backSwipe = !(BackValues.isZero(back.left) && BackValues.isZero(back.right)),
            ),
            back = back,
            topBarHidden = value("bar") == "1",
            handleHidden = HANDLE_COLORS.all { color -> lines.any { it.startsWith("[x]") && it.endsWith(":$HANDLE$color") } },
        )
    }

    /** The debug toolkit's reading: which parts work. */
    fun probe(reading: GestureReading?): String? = reading?.parts?.let { "home=${onOff(it.homeSwipe)},back=${onOff(it.backSwipe)}" }

    private fun onOff(works: Boolean) = if (works) "on" else "off"

    private fun put(namespace: String, key: String, value: String?): String =
        if (value == null || !value.matches(NUMBER)) "settings delete $namespace $key" else "settings put $namespace $key $value"
}
