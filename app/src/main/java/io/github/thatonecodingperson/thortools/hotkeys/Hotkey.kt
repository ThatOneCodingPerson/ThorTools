package io.github.thatonecodingperson.thortools.hotkeys

import android.view.KeyEvent
import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.ThorAction

/** The key code of a button that sends no key event (D-pad directions, stick flicks). */
private const val NO_KEY = KeyEvent.KEYCODE_UNKNOWN

/** How the editor groups the buttons. */
enum class ButtonGroup(@StringRes val label: Int) {
    THOR(R.string.buttonGroupThor),
    FACE(R.string.buttonGroupFace),
    SHOULDERS(R.string.buttonGroupShoulders),
    STICKS(R.string.buttonGroupSticks),
    MENU(R.string.buttonGroupMenu),
    VOLUME(R.string.buttonGroupVolume),
    DPAD(R.string.buttonGroupDpad),
    LEFT_STICK(R.string.buttonGroupLeftStick),
    RIGHT_STICK(R.string.buttonGroupRightStick),
    ;

    val buttons: List<PadButton> get() = PadButton.entries.filter { it.group == this }
}

/**
 * A button of the Thor. [id] is stored in the hotkey list, so it must never change. [label] is for sentences, [keyLabel]
 * for the button drawn as a key. [secondOnly]: a D-pad direction or stick flick, which sends no key event; it is read
 * from joystick motion while a combo's first button is held (`JoystickCatcher`), so it can only be a combo's second
 * button.
 */
enum class PadButton(
    val id: String,
    val keyCode: Int,
    @StringRes val label: Int,
    val group: ButtonGroup,
    @StringRes val keyLabel: Int = label,
    val gamepad: Boolean = true,
    val secondOnly: Boolean = false,
) {
    HOME("home", KeyEvent.KEYCODE_HOME, R.string.buttonHome, ButtonGroup.THOR, gamepad = false),
    BACK("back", KeyEvent.KEYCODE_BACK, R.string.buttonBack, ButtonGroup.THOR, gamepad = false),
    AYN("ayn", KeyEvent.KEYCODE_HOME, R.string.buttonAyn, ButtonGroup.THOR, gamepad = false),
    A("a", KeyEvent.KEYCODE_BUTTON_A, R.string.buttonA, ButtonGroup.FACE),
    B("b", KeyEvent.KEYCODE_BUTTON_B, R.string.buttonB, ButtonGroup.FACE),
    X("x", KeyEvent.KEYCODE_BUTTON_X, R.string.buttonX, ButtonGroup.FACE),
    Y("y", KeyEvent.KEYCODE_BUTTON_Y, R.string.buttonY, ButtonGroup.FACE),
    L1("l1", KeyEvent.KEYCODE_BUTTON_L1, R.string.buttonL1, ButtonGroup.SHOULDERS),
    R1("r1", KeyEvent.KEYCODE_BUTTON_R1, R.string.buttonR1, ButtonGroup.SHOULDERS),
    L2("l2", KeyEvent.KEYCODE_BUTTON_L2, R.string.buttonL2, ButtonGroup.SHOULDERS),
    R2("r2", KeyEvent.KEYCODE_BUTTON_R2, R.string.buttonR2, ButtonGroup.SHOULDERS),
    L3("l3", KeyEvent.KEYCODE_BUTTON_THUMBL, R.string.buttonL3, ButtonGroup.STICKS),
    R3("r3", KeyEvent.KEYCODE_BUTTON_THUMBR, R.string.buttonR3, ButtonGroup.STICKS),
    SELECT("select", KeyEvent.KEYCODE_BUTTON_SELECT, R.string.buttonSelect, ButtonGroup.MENU),
    START("start", KeyEvent.KEYCODE_BUTTON_START, R.string.buttonStart, ButtonGroup.MENU),
    VOLUME_UP("volume_up", KeyEvent.KEYCODE_VOLUME_UP, R.string.buttonVolumeUp, ButtonGroup.VOLUME, R.string.keyVolumeUp, gamepad = false),
    VOLUME_DOWN(
        "volume_down",
        KeyEvent.KEYCODE_VOLUME_DOWN,
        R.string.buttonVolumeDown,
        ButtonGroup.VOLUME,
        R.string.keyVolumeDown,
        gamepad = false,
    ),
    DPAD_UP("dpad_up", NO_KEY, R.string.buttonDpadUp, ButtonGroup.DPAD, R.string.keyDpadUp, gamepad = false, secondOnly = true),
    DPAD_DOWN("dpad_down", NO_KEY, R.string.buttonDpadDown, ButtonGroup.DPAD, R.string.keyDpadDown, gamepad = false, secondOnly = true),
    DPAD_LEFT("dpad_left", NO_KEY, R.string.buttonDpadLeft, ButtonGroup.DPAD, R.string.keyDpadLeft, gamepad = false, secondOnly = true),
    DPAD_RIGHT("dpad_right", NO_KEY, R.string.buttonDpadRight, ButtonGroup.DPAD, R.string.keyDpadRight, gamepad = false, secondOnly = true),
    LSTICK_UP(
        "lstick_up",
        NO_KEY,
        R.string.buttonLstickUp,
        ButtonGroup.LEFT_STICK,
        R.string.keyLstickUp,
        gamepad = false,
        secondOnly = true,
    ),
    LSTICK_DOWN(
        "lstick_down",
        NO_KEY,
        R.string.buttonLstickDown,
        ButtonGroup.LEFT_STICK,
        R.string.keyLstickDown,
        gamepad = false,
        secondOnly = true,
    ),
    LSTICK_LEFT(
        "lstick_left",
        NO_KEY,
        R.string.buttonLstickLeft,
        ButtonGroup.LEFT_STICK,
        R.string.keyLstickLeft,
        gamepad = false,
        secondOnly = true,
    ),
    LSTICK_RIGHT(
        "lstick_right",
        NO_KEY,
        R.string.buttonLstickRight,
        ButtonGroup.LEFT_STICK,
        R.string.keyLstickRight,
        gamepad = false,
        secondOnly = true,
    ),
    RSTICK_UP(
        "rstick_up",
        NO_KEY,
        R.string.buttonRstickUp,
        ButtonGroup.RIGHT_STICK,
        R.string.keyRstickUp,
        gamepad = false,
        secondOnly = true,
    ),
    RSTICK_DOWN(
        "rstick_down",
        NO_KEY,
        R.string.buttonRstickDown,
        ButtonGroup.RIGHT_STICK,
        R.string.keyRstickDown,
        gamepad = false,
        secondOnly = true,
    ),
    RSTICK_LEFT(
        "rstick_left",
        NO_KEY,
        R.string.buttonRstickLeft,
        ButtonGroup.RIGHT_STICK,
        R.string.keyRstickLeft,
        gamepad = false,
        secondOnly = true,
    ),
    RSTICK_RIGHT(
        "rstick_right",
        NO_KEY,
        R.string.buttonRstickRight,
        ButtonGroup.RIGHT_STICK,
        R.string.keyRstickRight,
        gamepad = false,
        secondOnly = true,
    ),
    ;

    /** The button with the other label in the same place: AYN's Xbox layout reports face buttons by position. */
    private fun swappedFace(): PadButton = when (this) {
        A -> B
        B -> A
        X -> Y
        Y -> X
        else -> this
    }

    /** The key code Android reports for this button in the current layout. */
    fun keyCodeIn(xboxLayout: Boolean): Int = if (xboxLayout) swappedFace().keyCode else keyCode

    companion object {
        /** The AYN button reports Home's key code; only its scan code tells it apart. */
        const val AYN_SCAN_CODE = 194

        fun byId(id: String?): PadButton? = entries.find { it.id == id }

        /** The printed button behind a key event; [xboxLayout] when AYN's pad is in its Xbox layout. */
        fun of(keyCode: Int, scanCode: Int, xboxLayout: Boolean): PadButton? {
            if (scanCode == AYN_SCAN_CODE) return AYN
            val button = entries.find { it != AYN && !it.secondOnly && it.keyCode == keyCode } ?: return null
            return if (xboxLayout) button.swappedFace() else button
        }
    }
}

/** [label] for sentences ("double tap"), [title] for choices ("Double tap"), [short] beside a key ("double"). */
enum class PressKind(val id: String, val taps: Int?, @StringRes val label: Int, @StringRes val title: Int, @StringRes val short: Int) {
    TAP("tap", 1, R.string.pressTap, R.string.pressTapTitle, R.string.pressTapShort),
    DOUBLE("double", 2, R.string.pressDouble, R.string.pressDoubleTitle, R.string.pressDoubleShort),
    TRIPLE("triple", 3, R.string.pressTriple, R.string.pressTripleTitle, R.string.pressTripleShort),
    HOLD("hold", null, R.string.pressHold, R.string.pressHoldTitle, R.string.pressHoldShort),
    ;

    companion object {
        fun byId(id: String?): PressKind? = entries.find { it.id == id }

        fun forTaps(count: Int): PressKind? = entries.find { it.taps == count }
    }
}

/**
 * One hotkey: a [press] of [button], or, with [second], holding [button] and a [press] of [second]. [arg] is the
 * action's argument: an encoded [AppLaunch] for [ThorAction.LAUNCH_APP], a [CloseAppArg] for [ThorAction.CLOSE_APP].
 * [showText]: a short note on the screen says what it did. [lock]: a controller move also locks the controller there
 * (see [canLock]). [cleanMemory]: closing background apps also cleans memory (see [canCleanMemory]). [apps]: only in these
 * apps (package names), where it takes the place of a hotkey for every app with the same trigger; empty: in every app.
 */
data class Hotkey(
    val button: PadButton,
    val second: PadButton?,
    val press: PressKind,
    val action: ThorAction,
    val arg: String? = null,
    val showText: Boolean = true,
    val lock: Boolean = false,
    val cleanMemory: Boolean = false,
    val apps: Set<String> = emptySet(),
) {
    fun sameTrigger(other: Hotkey) = button == other.button && second == other.second && press == other.press

    /**
     * Both can't be kept: the same trigger, and both for every app or both for some app in common. A hotkey for chosen
     * apps next to one for every app is fine: it wins in its apps.
     */
    fun clashes(other: Hotkey) =
        sameTrigger(other) && apps.isEmpty() == other.apps.isEmpty() && (apps.isEmpty() || apps.any { it in other.apps })

    /** The same trigger doing the same thing in the same apps, whatever its switches. */
    fun sameJob(other: Hotkey) = sameTrigger(other) && action == other.action && arg == other.arg && apps == other.apps

    /**
     * A single tap doing what the button does anyway (Home = Home, Back = Back): the button's own job, listed so it can be
     * seen and changed. It never makes the button ours, so on its own the press stays Android's, untouched.
     */
    val isOwnJob: Boolean get() = second == null && press == PressKind.TAP && ownJob(button) == action

    /** Home held back by the hotkeys (its own job alone doesn't hold it back). */
    val usesHome: Boolean get() = !isOwnJob && (button == PadButton.HOME || second == PadButton.HOME)

    companion object {
        /**
         * Home and Back held together are never a hotkey. A game button is only ever part of a combo: a press of its own
         * would have to be held back from the game, and a press sent again afterwards comes from a virtual keyboard, not
         * the controller. A D-pad direction or stick flick is only a second button, after Home, Back, AYN or a volume key:
         * those are always taken over when they start combos, so the joystick catcher never goes up while a game button is
         * merely held for the game.
         */
        fun allowed(button: PadButton, second: PadButton?): Boolean = second != button &&
            setOf(button, second) != setOf(PadButton.HOME, PadButton.BACK) &&
            !button.secondOnly &&
            !(second == null && button.gamepad) &&
            !(second?.secondOnly == true && button.gamepad)

        /** What a button does by itself, as an action; null for game buttons. */
        fun ownJob(button: PadButton): ThorAction? = when (button) {
            PadButton.HOME -> ThorAction.HOME
            PadButton.BACK -> ThorAction.BACK
            PadButton.AYN -> ThorAction.AYN_DRAWER
            PadButton.VOLUME_UP -> ThorAction.LOUDER
            PadButton.VOLUME_DOWN -> ThorAction.QUIETER
            else -> null
        }

        /** Actions that can also lock the controller to the screen they send it to. */
        fun canLock(action: ThorAction) = action == ThorAction.CONTROLLER_TO_TOP || action == ThorAction.CONTROLLER_TO_BOTTOM

        fun canCleanMemory(action: ThorAction) = action == ThorAction.CLEAR_BACKGROUND

        /** The buttons a combo is best held on: they aren't game buttons, so holding them gets in nobody's way. */
        fun goodComboFirst(button: PadButton) = button == PadButton.HOME || button == PadButton.BACK || button == PadButton.AYN
    }
}

/**
 * The stored form: one hotkey per line, `button;second;press;action;arg;switches[;apps]` where switches lists `text`,
 * `lock` and `memory`, and apps the package names a hotkey is limited to (left out for every app). Lines from 0.14.0 and
 * earlier have no switches field; unknown switches are ignored.
 */
object HotkeyList {
    /** A tap of the AYN button opens or closes the Thor Tools quick panel. */
    val aynPanel = Hotkey(PadButton.AYN, null, PressKind.TAP, ThorAction.OPEN_QUICK_PANEL)

    /** A tap of Home and of Back do their own job ([Hotkey.isOwnJob]), shown in the list so they can be changed. */
    val ownPresses = listOf(
        Hotkey(PadButton.HOME, null, PressKind.TAP, ThorAction.HOME, showText = false),
        Hotkey(PadButton.BACK, null, PressKind.TAP, ThorAction.BACK, showText = false),
    )

    /** What the AYN button did before hotkeys existed (tap: quick panel, hold: AYN's drawer), Home and Back as they are. */
    val defaults = listOf(aynPanel, Hotkey(PadButton.AYN, null, PressKind.HOLD, ThorAction.AYN_DRAWER)) + ownPresses

    /** [hotkeys] with [aynPanel] added when nothing is bound to a single tap of AYN; a tap bound elsewhere stays. */
    fun withAynPanel(hotkeys: List<Hotkey>): List<Hotkey> =
        if (hotkeys.any { it.clashes(aynPanel) }) hotkeys else listOf(aynPanel) + hotkeys

    /** [hotkeys] with [ownPresses] added where nothing is bound to that single tap; a tap bound elsewhere stays. */
    fun withOwnPresses(hotkeys: List<Hotkey>): List<Hotkey> = addFreeSuggestions(hotkeys, ownPresses)

    fun encode(hotkeys: List<Hotkey>): String = hotkeys.joinToString("\n") { hotkey ->
        val switches = listOfNotNull(
            SWITCH_TEXT.takeIf { hotkey.showText },
            SWITCH_LOCK.takeIf { hotkey.lock },
            SWITCH_MEMORY.takeIf { hotkey.cleanMemory },
        ).joinToString(",")
        val apps = hotkey.apps.sorted().joinToString(",").takeIf { it.isNotEmpty() }
        listOfNotNull(
            hotkey.button.id,
            hotkey.second?.id.orEmpty(),
            hotkey.press.id,
            hotkey.action.id,
            hotkey.arg.orEmpty(),
            switches,
            apps,
        )
            .joinToString(";")
    }

    /**
     * Nothing stored yet means the defaults; lines this version can't read are dropped. Lines without switches show
     * text when [textByDefault] (0.14.0 had one Messages switch for every hotkey).
     */
    fun decode(text: String?, textByDefault: Boolean = true): List<Hotkey> {
        if (text == null) return defaults
        return text.lines().mapNotNull { line ->
            val parts = line.split(';', limit = 7)
            val switches = parts.getOrNull(5)?.split(',')
            if (parts.size < 4) return@mapNotNull null
            val button = PadButton.byId(parts[0]) ?: return@mapNotNull null
            val second = if (parts[1].isEmpty()) null else PadButton.byId(parts[1]) ?: return@mapNotNull null
            if (!Hotkey.allowed(button, second)) return@mapNotNull null
            Hotkey(
                button = button,
                second = second,
                press = PressKind.byId(parts[2]) ?: return@mapNotNull null,
                action = ThorAction.byId(parts[3]) ?: return@mapNotNull null,
                arg = parts.getOrNull(4)?.takeIf { it.isNotEmpty() },
                showText = switches?.contains(SWITCH_TEXT) ?: textByDefault,
                lock = switches?.contains(SWITCH_LOCK) == true,
                cleanMemory = switches?.contains(SWITCH_MEMORY) == true,
                apps = parts.getOrNull(6)?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty(),
            )
        }
    }

    /** Actions whose hotkeys are set on their own screen (Desktop controls' on/off), not in the Hotkeys menu. */
    val SET_ELSEWHERE = setOf(ThorAction.TOGGLE_DESKTOP)

    /** The hotkeys the Hotkeys menu lists: all but those set elsewhere, which still count and still clash. */
    fun listed(hotkeys: List<Hotkey>): List<Hotkey> = hotkeys.filterNot { it.action in SET_ELSEWHERE }

    /** The hotkeys that count in [app]: its own, and every hotkey for all apps whose trigger it has no own one for. */
    fun forApp(hotkeys: List<Hotkey>, app: String?): List<Hotkey> {
        val own = if (app == null) emptyList() else hotkeys.filter { app in it.apps }
        if (own.isEmpty()) return hotkeys.filter { it.apps.isEmpty() }
        return hotkeys.filter { hotkey -> hotkey.apps.isEmpty() && own.none { it.sameTrigger(hotkey) } } + own
    }

    /** [hotkey] added, replacing [replacing] and any hotkey it [Hotkey.clashes] with. */
    fun put(hotkeys: List<Hotkey>, hotkey: Hotkey, replacing: Hotkey? = null): List<Hotkey> =
        hotkeys.filterNot { it == replacing || it.clashes(hotkey) } + hotkey

    /** [hotkeys] plus every one of [suggestions] whose trigger is still free; nothing is replaced. */
    fun addFreeSuggestions(hotkeys: List<Hotkey>, suggestions: List<Hotkey>): List<Hotkey> =
        hotkeys + suggestions.filter { suggestion -> hotkeys.none { it.clashes(suggestion) } }

    private const val SWITCH_TEXT = "text"
    private const val SWITCH_LOCK = "lock"
    private const val SWITCH_MEMORY = "memory"
}

/** Where [ThorAction.LAUNCH_APP] opens its app. [id] is stored in the hotkey's argument. */
enum class LaunchScreen(val id: String, @StringRes val label: Int) {
    HERE("here", R.string.launchScreenHere),
    TOP("top", R.string.launchScreenTop),
    BOTTOM("bottom", R.string.launchScreenBottom),
}

/**
 * The argument of [ThorAction.CLOSE_APP]: the screen whose app closes (`here`, `top`, `bottom`). None means the app
 * that came up last on either screen, as before 0.16.0, so hotkeys saved earlier behave as they did.
 */
object CloseAppArg {
    fun decode(arg: String?): LaunchScreen? = LaunchScreen.entries.find { it.id == arg }

    fun encode(screen: LaunchScreen?): String? = screen?.id
}

/** The argument of [ThorAction.LAUNCH_APP]: `package`, or `package@top` / `package@bottom`. */
data class AppLaunch(val packageName: String, val screen: LaunchScreen = LaunchScreen.HERE) {
    fun encode(): String = if (screen == LaunchScreen.HERE) packageName else "$packageName@${screen.id}"

    companion object {
        fun decode(arg: String?): AppLaunch? {
            val packageName = arg?.substringBefore('@')?.takeIf { it.isNotBlank() } ?: return null
            val screen = LaunchScreen.entries.find { it.id == arg.substringAfter('@', "") } ?: LaunchScreen.HERE
            return AppLaunch(packageName, screen)
        }
    }
}

/**
 * Records a hotkey's buttons as they are pressed on the Thor: the first button down, and a second one pressed while
 * the first is still held. [onKey] gives the result once every button is up again.
 */
class ButtonRecorder {
    data class Recorded(val button: PadButton, val second: PadButton?)

    private var first: PadButton? = null
    private var second: PadButton? = null
    private val down = mutableSetOf<PadButton>()

    fun onKey(button: PadButton, pressed: Boolean): Recorded? {
        if (pressed) {
            val held = first
            // A D-pad direction or stick flick can't start a hotkey.
            if (held == null && button.secondOnly) return null
            when {
                held == null -> first = button
                second == null && button != held && held in down -> second = button
            }
            down += button
            return null
        }
        down -= button
        val recorded = first ?: return null
        return if (down.isEmpty()) Recorded(recorded, second) else null
    }
}
