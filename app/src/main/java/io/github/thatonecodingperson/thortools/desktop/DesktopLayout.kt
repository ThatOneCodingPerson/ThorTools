package io.github.thatonecodingperson.thortools.desktop

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.PadButton

/**
 * A Thor button desktop controls can give a job. [raw] is the pad's evdev code in the Standard style; in the Xbox style
 * the face buttons arrive by position, so A and B, X and Y swap. [id] is stored, so it must never change.
 */
enum class DesktopControl(val id: String, val pad: PadButton, val raw: Int) {
    A("a", PadButton.A, 304),
    B("b", PadButton.B, 305),
    X("x", PadButton.X, 307),
    Y("y", PadButton.Y, 308),
    L1("l1", PadButton.L1, 310),
    R1("r1", PadButton.R1, 311),
    L2("l2", PadButton.L2, 312),
    R2("r2", PadButton.R2, 313),
    L3("l3", PadButton.L3, 317),
    R3("r3", PadButton.R3, 318),
    SELECT("select", PadButton.SELECT, 314),
    START("start", PadButton.START, 315),
    ;

    companion object {
        fun byId(id: String?): DesktopControl? = entries.find { it.id == id }

        fun of(pad: PadButton): DesktopControl? = entries.find { it.pad == pad }

        /** The printed button behind a raw key code of AYN's pad; [xbox] when the pad is in its Xbox style. */
        fun ofRaw(code: Int, xbox: Boolean): DesktopControl? {
            val control = entries.find { it.raw == code } ?: return null
            if (!xbox) return control
            return when (control) {
                A -> B
                B -> A
                X -> Y
                Y -> X
                else -> control
            }
        }
    }
}

/**
 * What a button does in desktop controls. [code] is the evdev code Thor Tools' own input device sends: a mouse button
 * ([Kind.MOUSE]), a key ([Kind.KEY], keys Android's generic layout knows), a wheel notch ([Kind.WHEEL], up 1, down -1),
 * or nothing because Thor Tools does it itself ([Kind.APP]); [Kind.HOLD] changes what the pointer stick does while
 * held. [Kind.NONE] leaves the button to the app. [keyCode] is a key's Android key code, for keys sent to one screen.
 */
enum class DesktopJob(val id: String, val kind: Kind, val code: Int, @StringRes val label: Int, val group: Group, val keyCode: Int = 0) {
    NONE("none", Kind.NONE, 0, R.string.desktopJobNone, Group.APP_OWN),
    LEFT_CLICK("left_click", Kind.MOUSE, 272, R.string.desktopJobLeftClick, Group.MOUSE),
    RIGHT_CLICK("right_click", Kind.MOUSE, 273, R.string.desktopJobRightClick, Group.MOUSE),
    MIDDLE_CLICK("middle_click", Kind.MOUSE, 274, R.string.desktopJobMiddleClick, Group.MOUSE),
    MOUSE_BACK("mouse_back", Kind.MOUSE, 275, R.string.desktopJobMouseBack, Group.MOUSE),
    MOUSE_FORWARD("mouse_forward", Kind.MOUSE, 276, R.string.desktopJobMouseForward, Group.MOUSE),
    WHEEL_UP("wheel_up", Kind.WHEEL, 1, R.string.desktopJobWheelUp, Group.MOUSE),
    WHEEL_DOWN("wheel_down", Kind.WHEEL, -1, R.string.desktopJobWheelDown, Group.MOUSE),
    SCROLL_HOLD("scroll_hold", Kind.HOLD, 0, R.string.desktopJobScrollHold, Group.MOUSE),
    ENTER("enter", Kind.KEY, 28, R.string.desktopJobEnter, Group.KEYS, keyCode = 66),
    ESCAPE("escape", Kind.KEY, 1, R.string.desktopJobEscape, Group.KEYS, keyCode = 111),
    TAB("tab", Kind.KEY, 15, R.string.desktopJobTab, Group.KEYS, keyCode = 61),
    SPACE("space", Kind.KEY, 57, R.string.desktopJobSpace, Group.KEYS, keyCode = 62),
    BACKSPACE("backspace", Kind.KEY, 14, R.string.desktopJobBackspace, Group.KEYS, keyCode = 67),
    DELETE("delete", Kind.KEY, 111, R.string.desktopJobDelete, Group.KEYS, keyCode = 112),
    UP("up", Kind.KEY, 103, R.string.desktopJobUp, Group.KEYS, keyCode = 19),
    DOWN("down", Kind.KEY, 108, R.string.desktopJobDown, Group.KEYS, keyCode = 20),
    LEFT("left", Kind.KEY, 105, R.string.desktopJobLeft, Group.KEYS, keyCode = 21),
    RIGHT("right", Kind.KEY, 106, R.string.desktopJobRight, Group.KEYS, keyCode = 22),
    PAGE_UP("page_up", Kind.KEY, 104, R.string.desktopJobPageUp, Group.KEYS, keyCode = 92),
    PAGE_DOWN("page_down", Kind.KEY, 109, R.string.desktopJobPageDown, Group.KEYS, keyCode = 93),
    LINE_START("line_start", Kind.KEY, 102, R.string.desktopJobLineStart, Group.KEYS, keyCode = 122),
    LINE_END("line_end", Kind.KEY, 107, R.string.desktopJobLineEnd, Group.KEYS, keyCode = 123),
    CTRL("ctrl", Kind.KEY, 29, R.string.desktopJobCtrl, Group.KEYS, keyCode = 113),
    ALT("alt", Kind.KEY, 56, R.string.desktopJobAlt, Group.KEYS, keyCode = 57),
    SHIFT("shift", Kind.KEY, 42, R.string.desktopJobShift, Group.KEYS, keyCode = 59),
    BACK("back", Kind.KEY, 158, R.string.desktopJobBack, Group.ANDROID, keyCode = 4),
    HOME("home", Kind.KEY, 172, R.string.desktopJobHome, Group.ANDROID, keyCode = 3),
    RECENTS("recents", Kind.KEY, 580, R.string.desktopJobRecents, Group.ANDROID, keyCode = 187),
    KEYBOARD("keyboard", Kind.APP, 0, R.string.desktopJobKeyboard, Group.ANDROID),
    ;

    enum class Kind { NONE, MOUSE, KEY, WHEEL, APP, HOLD }

    enum class Group(@StringRes val label: Int) {
        MOUSE(R.string.desktopGroupMouse),
        KEYS(R.string.desktopGroupKeys),
        ANDROID(R.string.desktopGroupAndroid),
        APP_OWN(R.string.desktopGroupAppOwn),
    }

    companion object {
        fun byId(id: String?): DesktopJob? = entries.find { it.id == id }

        /** Every key Thor Tools' own keyboard can send: none are letters, so Android's on-screen keyboard still opens. */
        val keys: List<Int> get() = entries.filter { it.kind == Kind.KEY }.map { it.code }

        val mouseButtons: List<Int> get() = entries.filter { it.kind == Kind.MOUSE }.map { it.code }
    }
}

/** What a stick does: moves the pointer, scrolls like a mouse wheel, or stays the app's. */
enum class StickRole(val id: String, @StringRes val label: Int) {
    POINTER("pointer", R.string.desktopStickPointer),
    SCROLL("scroll", R.string.desktopStickScroll),
    NONE("none", R.string.desktopStickNone),
    ;

    companion object {
        fun byId(id: String?): StickRole? = entries.find { it.id == id }
    }
}

/** How much faster the pointer gets towards the stick's edge: the stick's travel is raised to [exponent]. */
enum class Acceleration(val id: String, val exponent: Float, @StringRes val label: Int) {
    NONE("none", 1f, R.string.desktopAccelNone),
    LIGHT("light", 1.7f, R.string.desktopAccelLight),
    STRONG("strong", 2.6f, R.string.desktopAccelStrong),
    ;

    companion object {
        fun byId(id: String?): Acceleration? = entries.find { it.id == id }
    }
}

/**
 * Everything desktop controls can be set to. [speed] and [scrollSpeed] scale the pointer and the wheel (1 = normal);
 * [deadZone] is the stick's travel that does nothing; [triggerThreshold] how far an analog trigger goes down before it
 * clicks; [holdStartSwitch]: holding Start pauses desktop controls where they are and holding it again resumes them.
 * [precision]: a button that slows the pointer down while held. [aynPointer]: AYN's own mouse mode moves the
 * pointer with the sticks instead of Thor Tools. [bottomScreen]: the controller stays on the bottom screen, where the
 * D-pad and [bottomButtons] work the app as a controller, while the pointer and the other buttons work the top screen.
 */
data class DesktopLayout(
    val jobs: Map<DesktopControl, DesktopJob>,
    val leftStick: StickRole,
    val rightStick: StickRole,
    val speed: Float = 1f,
    val acceleration: Acceleration = Acceleration.LIGHT,
    val deadZone: Float = 0.12f,
    val invertY: Boolean = false,
    val scrollSpeed: Float = 1f,
    val naturalScroll: Boolean = false,
    val horizontalScroll: Boolean = true,
    val triggerThreshold: Float = 0.35f,
    val holdStartSwitch: Boolean = false,
    val precision: DesktopControl? = null,
    val aynPointer: Boolean = false,
    val bottomScreen: Boolean = false,
    val bottomButtons: Set<DesktopControl> = DEFAULT_BOTTOM_BUTTONS,
) {
    fun job(control: DesktopControl): DesktopJob = jobs[control] ?: DesktopJob.NONE

    /** The buttons desktop controls take from the app while they are on. */
    val taken: Set<DesktopControl>
        get() = DesktopControl.entries.filter { control ->
            job(control) != DesktopJob.NONE ||
                (control == precision && !aynPointer) ||
                (control == DesktopControl.START && holdStartSwitch)
        }.toSet()

    /** [taken] while the controller is kept on the bottom screen ([split]): the bottom screen's buttons stay its app's. */
    fun takenFor(split: Boolean): Set<DesktopControl> = if (split) taken - bottomButtons else taken

    /**
     * What the left and right stick do now. With the controller kept on the bottom screen ([split]) the right stick
     * points and the left one is the bottom screen's: Android turns the left stick into D-pad presses in the app with the
     * controller, never the right one. While a Scroll while held button is down ([scrollHeld]) the pointer stick scrolls.
     */
    fun sticksFor(split: Boolean, scrollHeld: Boolean = false): Pair<StickRole, StickRole> {
        val (left, right) = if (split) StickRole.NONE to StickRole.POINTER else leftStick to rightStick
        fun StickRole.now() = if (scrollHeld && this == StickRole.POINTER) StickRole.SCROLL else this
        return left.now() to right.now()
    }

    fun with(control: DesktopControl, job: DesktopJob) = copy(jobs = jobs + (control to job))

    /** The preset whose buttons and sticks this layout has, when it has one's exactly. */
    val preset: DesktopPreset? get() = DesktopPreset.entries.find { sameButtons(it.layout) }

    /** [preset]'s buttons and sticks; this layout's speeds and feel stay. */
    fun withButtonsOf(preset: DesktopPreset): DesktopLayout {
        val other = preset.layout
        return copy(
            jobs = other.jobs,
            leftStick = other.leftStick,
            rightStick = other.rightStick,
            holdStartSwitch = other.holdStartSwitch,
            precision = other.precision,
        )
    }

    private fun sameButtons(other: DesktopLayout): Boolean = DesktopControl.entries.all { job(it) == other.job(it) } &&
        leftStick == other.leftStick &&
        rightStick == other.rightStick &&
        holdStartSwitch == other.holdStartSwitch &&
        precision == other.precision

    fun encode(): String = buildList {
        DesktopControl.entries.forEach { add("${it.id}=${job(it).id}") }
        add("ls=${leftStick.id}")
        add("rs=${rightStick.id}")
        add("sp=${speed.fmt()}")
        add("ac=${acceleration.id}")
        add("dz=${deadZone.fmt()}")
        add("iy=${invertY.bit()}")
        add("ss=${scrollSpeed.fmt()}")
        add("ns=${naturalScroll.bit()}")
        add("hs=${horizontalScroll.bit()}")
        add("tt=${triggerThreshold.fmt()}")
        add("hp=${holdStartSwitch.bit()}")
        add("pr=${precision?.id ?: "none"}")
        add("ap=${aynPointer.bit()}")
        add("bs=${bottomScreen.bit()}")
        add("bb2=${bottomButtons.sortedBy { it.ordinal }.joinToString(",") { it.id }}")
    }.joinToString(";")

    companion object {
        val DEFAULT: DesktopLayout get() = DesktopPreset.STEAM_CONTROLLER.layout

        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 3f
        const val MIN_DEAD_ZONE = 0.04f
        const val MAX_DEAD_ZONE = 0.4f
        const val MIN_THRESHOLD = 0.1f
        const val MAX_THRESHOLD = 0.9f

        /** Only the D-pad (always the app's) works the bottom screen until buttons are chosen for it. */
        val DEFAULT_BOTTOM_BUTTONS = emptySet<DesktopControl>()

        /** Text from [encode]; anything missing or unknown comes from [DEFAULT], so older settings keep working. */
        fun decode(text: String?): DesktopLayout {
            val base = DEFAULT
            if (text.isNullOrBlank()) return base
            val values = text.split(';').mapNotNull { part ->
                val key = part.substringBefore('=', "")
                if (key.isEmpty()) null else key to part.substringAfter('=')
            }.toMap()
            val jobs = DesktopControl.entries.associateWith { control ->
                DesktopJob.byId(values[control.id]) ?: base.job(control)
            }
            return DesktopLayout(
                jobs = jobs,
                leftStick = StickRole.byId(values["ls"]) ?: base.leftStick,
                rightStick = StickRole.byId(values["rs"]) ?: base.rightStick,
                speed = values["sp"]?.toFloatOrNull()?.coerceIn(MIN_SPEED, MAX_SPEED) ?: base.speed,
                acceleration = Acceleration.byId(values["ac"]) ?: base.acceleration,
                deadZone = values["dz"]?.toFloatOrNull()?.coerceIn(MIN_DEAD_ZONE, MAX_DEAD_ZONE) ?: base.deadZone,
                invertY = values["iy"]?.let { it == "1" } ?: base.invertY,
                scrollSpeed = values["ss"]?.toFloatOrNull()?.coerceIn(MIN_SPEED, MAX_SPEED) ?: base.scrollSpeed,
                naturalScroll = values["ns"]?.let { it == "1" } ?: base.naturalScroll,
                horizontalScroll = values["hs"]?.let { it == "1" } ?: base.horizontalScroll,
                triggerThreshold = values["tt"]?.toFloatOrNull()?.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD) ?: base.triggerThreshold,
                holdStartSwitch = values["hp"]?.let { it == "1" } ?: base.holdStartSwitch,
                precision = if (values.containsKey("pr")) DesktopControl.byId(values["pr"]) else base.precision,
                aynPointer = values["ap"]?.let { it == "1" } ?: base.aynPointer,
                bottomScreen = values["bs"]?.let { it == "1" } ?: base.bottomScreen,
                bottomButtons = values["bb2"]?.let { ids -> ids.split(',').mapNotNull(DesktopControl::byId).toSet() }
                    ?: base.bottomButtons,
            )
        }

        private fun Float.fmt(): String = "%.2f".format(java.util.Locale.US, this)

        private fun Boolean.bit(): String = if (this) "1" else "0"
    }
}

/** Ready-made layouts to start from. */
enum class DesktopPreset(val id: String, @StringRes val label: Int, @StringRes val info: Int) {
    /** As close as the Thor gets to a Steam Controller's desktop layout: its sticks stand in for the trackpads. */
    STEAM_CONTROLLER("steam_controller", R.string.desktopPresetSteam, R.string.desktopPresetSteamInfo),

    /** The same face buttons, triggers and sticks as a Steam Deck's desktop layout; the shoulders and L3 stay the app's. */
    STEAM_DECK("steam_deck", R.string.desktopPresetDeck, R.string.desktopPresetDeckInfo),

    /** Android's own ways: B goes back, the shoulders go back and forward a page, Start shows the recent apps. */
    ANDROID("android", R.string.desktopPresetAndroid, R.string.desktopPresetAndroidInfo),
    ;

    val layout: DesktopLayout
        get() = when (this) {
            STEAM_CONTROLLER -> DesktopLayout(
                jobs = mapOf(
                    DesktopControl.A to DesktopJob.ENTER,
                    DesktopControl.B to DesktopJob.ESCAPE,
                    DesktopControl.X to DesktopJob.KEYBOARD,
                    DesktopControl.Y to DesktopJob.SPACE,
                    DesktopControl.L1 to DesktopJob.CTRL,
                    DesktopControl.R1 to DesktopJob.ALT,
                    DesktopControl.L2 to DesktopJob.RIGHT_CLICK,
                    DesktopControl.R2 to DesktopJob.LEFT_CLICK,
                    DesktopControl.L3 to DesktopJob.MIDDLE_CLICK,
                    DesktopControl.R3 to DesktopJob.LEFT_CLICK,
                    DesktopControl.SELECT to DesktopJob.TAB,
                    DesktopControl.START to DesktopJob.ESCAPE,
                ),
                leftStick = StickRole.SCROLL,
                rightStick = StickRole.POINTER,
            )
            STEAM_DECK -> STEAM_CONTROLLER.layout.copy(
                jobs = STEAM_CONTROLLER.layout.jobs + mapOf(
                    DesktopControl.L1 to DesktopJob.NONE,
                    DesktopControl.R1 to DesktopJob.NONE,
                    DesktopControl.L3 to DesktopJob.NONE,
                ),
            )
            ANDROID -> STEAM_CONTROLLER.layout.copy(
                jobs = STEAM_CONTROLLER.layout.jobs + mapOf(
                    DesktopControl.B to DesktopJob.BACK,
                    DesktopControl.L1 to DesktopJob.MOUSE_BACK,
                    DesktopControl.R1 to DesktopJob.MOUSE_FORWARD,
                    DesktopControl.START to DesktopJob.RECENTS,
                ),
            )
        }

    companion object {
        fun byId(id: String?): DesktopPreset? = entries.find { it.id == id }
    }
}
