package io.github.thatonecodingperson.thortools.wii

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.PadButton

/** How a Wii control is mapped: one Thor button, a stick's four directions, or a held button and a pressed one. */
enum class WiiKind { BUTTON, STICK, COMBO }

/** Four directions on the Thor that can drive a Wii stick, D-pad, pointer or motion. */
enum class StickSource(val id: String, @StringRes val label: Int) {
    DPAD("dpad", R.string.wiiSourceDpad),
    LEFT("left", R.string.wiiSourceLeft),
    RIGHT("right", R.string.wiiSourceRight),
    ;

    /** The Thor's direction buttons in the order up, down, left, right. */
    val directions: List<PadButton>
        get() = when (this) {
            DPAD -> listOf(PadButton.DPAD_UP, PadButton.DPAD_DOWN, PadButton.DPAD_LEFT, PadButton.DPAD_RIGHT)
            LEFT -> listOf(PadButton.LSTICK_UP, PadButton.LSTICK_DOWN, PadButton.LSTICK_LEFT, PadButton.LSTICK_RIGHT)
            RIGHT -> listOf(PadButton.RSTICK_UP, PadButton.RSTICK_DOWN, PadButton.RSTICK_LEFT, PadButton.RSTICK_RIGHT)
        }

    companion object {
        fun byId(id: String): StickSource? = entries.find { it.id == id }
    }
}

/**
 * A Wii control the builder maps. [keys] are its controls in a Dolphin profile: a button's keys all get the same
 * binding (a shake moves on every axis), a stick's are its up, down, left and right; a combo is one of Dolphin's Wii
 * Remote hotkeys. [cap] is the label printed on the Wii controller; the others show their [title].
 */
enum class WiiControl(
    val id: String,
    val kind: WiiKind,
    val keys: List<String>,
    val cap: String?,
    @StringRes val title: Int,
    @StringRes val info: Int,
) {
    REMOTE_A("a", WiiKind.BUTTON, listOf("Buttons/A"), "A", R.string.wiiRemoteA, R.string.wiiRemoteAInfo),
    REMOTE_B("b", WiiKind.BUTTON, listOf("Buttons/B"), "B", R.string.wiiRemoteB, R.string.wiiRemoteBInfo),
    REMOTE_1("1", WiiKind.BUTTON, listOf("Buttons/1"), "1", R.string.wiiRemote1, R.string.wiiRemote12Info),
    REMOTE_2("2", WiiKind.BUTTON, listOf("Buttons/2"), "2", R.string.wiiRemote2, R.string.wiiRemote12Info),
    REMOTE_MINUS("minus", WiiKind.BUTTON, listOf("Buttons/-"), "−", R.string.wiiMinus, R.string.wiiMinusPlusInfo),
    REMOTE_PLUS("plus", WiiKind.BUTTON, listOf("Buttons/+"), "+", R.string.wiiPlus, R.string.wiiMinusPlusInfo),
    REMOTE_HOME("home", WiiKind.BUTTON, listOf("Buttons/Home"), "HOME", R.string.wiiHome, R.string.wiiHomeInfo),
    REMOTE_DPAD("dpad", WiiKind.STICK, directions("D-Pad"), null, R.string.wiiRemoteDpad, R.string.wiiRemoteDpadInfo),
    POINTER("pointer", WiiKind.STICK, directions("IR"), null, R.string.wiiPointer, R.string.wiiPointerInfo),
    RECENTER("recenter", WiiKind.BUTTON, listOf("IR/Recenter"), null, R.string.wiiRecenter, R.string.wiiRecenterInfo),
    SHAKE("shake", WiiKind.BUTTON, listOf("Shake/X", "Shake/Y", "Shake/Z"), null, R.string.wiiShake, R.string.wiiShakeInfo),
    TILT(
        "tilt",
        WiiKind.STICK,
        listOf("Tilt/Forward", "Tilt/Backward", "Tilt/Left", "Tilt/Right"),
        null,
        R.string.wiiTilt,
        R.string.wiiTiltInfo,
    ),
    SWING("swing", WiiKind.STICK, directions("Swing"), null, R.string.wiiSwing, R.string.wiiSwingInfo),
    NUNCHUK_STICK("n_stick", WiiKind.STICK, directions("Nunchuk/Stick"), null, R.string.wiiNunchukStick, R.string.wiiNunchukStickInfo),
    NUNCHUK_C("n_c", WiiKind.BUTTON, listOf("Nunchuk/Buttons/C"), "C", R.string.wiiNunchukC, R.string.wiiNunchukCInfo),
    NUNCHUK_Z("n_z", WiiKind.BUTTON, listOf("Nunchuk/Buttons/Z"), "Z", R.string.wiiNunchukZ, R.string.wiiNunchukZInfo),
    NUNCHUK_SHAKE(
        "n_shake",
        WiiKind.BUTTON,
        listOf("Nunchuk/Shake/X", "Nunchuk/Shake/Y", "Nunchuk/Shake/Z"),
        null,
        R.string.wiiNunchukShake,
        R.string.wiiNunchukShakeInfo,
    ),
    SIDEWAYS_TOGGLE(
        "sideways_toggle",
        WiiKind.COMBO,
        listOf("Hotkeys/Sideways Toggle"),
        null,
        R.string.wiiSidewaysToggle,
        R.string.wiiSidewaysToggleInfo,
    ),
    UPRIGHT_TOGGLE(
        "upright_toggle",
        WiiKind.COMBO,
        listOf("Hotkeys/Upright Toggle"),
        null,
        R.string.wiiUprightToggle,
        R.string.wiiUprightToggleInfo,
    ),
    CLASSIC_A("c_a", WiiKind.BUTTON, listOf("Classic/Buttons/A"), "A", R.string.wiiClassicA, R.string.wiiClassicFaceInfo),
    CLASSIC_B("c_b", WiiKind.BUTTON, listOf("Classic/Buttons/B"), "B", R.string.wiiClassicB, R.string.wiiClassicFaceInfo),
    CLASSIC_X("c_x", WiiKind.BUTTON, listOf("Classic/Buttons/X"), "X", R.string.wiiClassicX, R.string.wiiClassicFaceInfo),
    CLASSIC_Y("c_y", WiiKind.BUTTON, listOf("Classic/Buttons/Y"), "Y", R.string.wiiClassicY, R.string.wiiClassicFaceInfo),
    CLASSIC_L(
        "c_l",
        WiiKind.BUTTON,
        listOf("Classic/Triggers/L", "Classic/Triggers/L-Analog"),
        "L",
        R.string.wiiClassicL,
        R.string.wiiClassicLRInfo,
    ),
    CLASSIC_R(
        "c_r",
        WiiKind.BUTTON,
        listOf("Classic/Triggers/R", "Classic/Triggers/R-Analog"),
        "R",
        R.string.wiiClassicR,
        R.string.wiiClassicLRInfo,
    ),
    CLASSIC_ZL("c_zl", WiiKind.BUTTON, listOf("Classic/Buttons/ZL"), "ZL", R.string.wiiClassicZL, R.string.wiiClassicZInfo),
    CLASSIC_ZR("c_zr", WiiKind.BUTTON, listOf("Classic/Buttons/ZR"), "ZR", R.string.wiiClassicZR, R.string.wiiClassicZInfo),
    CLASSIC_MINUS("c_minus", WiiKind.BUTTON, listOf("Classic/Buttons/-"), "−", R.string.wiiMinus, R.string.wiiMinusPlusInfo),
    CLASSIC_PLUS("c_plus", WiiKind.BUTTON, listOf("Classic/Buttons/+"), "+", R.string.wiiPlus, R.string.wiiMinusPlusInfo),
    CLASSIC_HOME("c_home", WiiKind.BUTTON, listOf("Classic/Buttons/Home"), "HOME", R.string.wiiHome, R.string.wiiHomeInfo),
    CLASSIC_LEFT_STICK(
        "c_lstick",
        WiiKind.STICK,
        directions("Classic/Left Stick"),
        null,
        R.string.wiiClassicLeftStick,
        R.string.wiiClassicStickInfo,
    ),
    CLASSIC_RIGHT_STICK(
        "c_rstick",
        WiiKind.STICK,
        directions("Classic/Right Stick"),
        null,
        R.string.wiiClassicRightStick,
        R.string.wiiClassicStickInfo,
    ),
    CLASSIC_DPAD("c_dpad", WiiKind.STICK, directions("Classic/D-Pad"), null, R.string.wiiClassicDpad, R.string.wiiClassicDpadInfo),
    ;

    companion object {
        fun byId(id: String): WiiControl? = entries.find { it.id == id }
    }
}

private fun directions(group: String) = listOf("$group/Up", "$group/Down", "$group/Left", "$group/Right")

/**
 * The four ways to hold the Wii controls, one tab each: which controls they have, the extension and options Dolphin
 * needs, and the Thor buttons that come closest to the real thing.
 */
enum class WiiSetup(
    val id: String,
    @StringRes val title: Int,
    @StringRes val info: Int,
    @StringRes val defaultName: Int,
    val extension: String,
    val sideways: Boolean,
    val suggestedButtons: Map<WiiControl, PadButton>,
    val suggestedSticks: Map<WiiControl, Set<StickSource>>,
    val suggestedCombos: Map<WiiControl, Pair<PadButton, PadButton>> = emptyMap(),
) {
    NUNCHUK(
        "nunchuk",
        R.string.wiiSetupNunchuk,
        R.string.wiiSetupNunchukInfo,
        R.string.wiiNameNunchuk,
        extension = "Nunchuk",
        sideways = false,
        suggestedButtons = remoteButtons() + mapOf(
            WiiControl.RECENTER to PadButton.R3,
            WiiControl.NUNCHUK_C to PadButton.L1,
            WiiControl.NUNCHUK_Z to PadButton.L2,
            WiiControl.NUNCHUK_SHAKE to PadButton.L3,
        ),
        suggestedSticks = mapOf(
            WiiControl.NUNCHUK_STICK to setOf(StickSource.LEFT),
            WiiControl.REMOTE_DPAD to setOf(StickSource.DPAD),
            WiiControl.POINTER to setOf(StickSource.RIGHT),
        ),
        suggestedCombos = toggles(),
    ),
    SIDEWAYS(
        "sideways",
        R.string.wiiSetupSideways,
        R.string.wiiSetupSidewaysInfo,
        R.string.wiiNameSideways,
        extension = "None",
        sideways = true,
        suggestedButtons = remoteButtons() + (WiiControl.REMOTE_HOME to PadButton.L3),
        suggestedSticks = mapOf(
            WiiControl.REMOTE_DPAD to setOf(StickSource.DPAD, StickSource.LEFT),
            WiiControl.TILT to setOf(StickSource.RIGHT),
        ),
        suggestedCombos = toggles(),
    ),
    POINTING(
        "pointing",
        R.string.wiiSetupPointing,
        R.string.wiiSetupPointingInfo,
        R.string.wiiNamePointing,
        extension = "None",
        sideways = false,
        suggestedButtons = remoteButtons() + mapOf(WiiControl.RECENTER to PadButton.R3, WiiControl.REMOTE_HOME to PadButton.L3),
        suggestedSticks = mapOf(
            WiiControl.REMOTE_DPAD to setOf(StickSource.DPAD),
            WiiControl.POINTER to setOf(StickSource.RIGHT),
        ),
        suggestedCombos = toggles(),
    ),
    CLASSIC(
        "classic",
        R.string.wiiSetupClassic,
        R.string.wiiSetupClassicInfo,
        R.string.wiiNameClassic,
        extension = "Classic",
        sideways = false,
        suggestedButtons = mapOf(
            WiiControl.CLASSIC_A to PadButton.A,
            WiiControl.CLASSIC_B to PadButton.B,
            WiiControl.CLASSIC_X to PadButton.X,
            WiiControl.CLASSIC_Y to PadButton.Y,
            WiiControl.CLASSIC_ZL to PadButton.L1,
            WiiControl.CLASSIC_ZR to PadButton.R1,
            WiiControl.CLASSIC_L to PadButton.L2,
            WiiControl.CLASSIC_R to PadButton.R2,
            WiiControl.CLASSIC_MINUS to PadButton.SELECT,
            WiiControl.CLASSIC_PLUS to PadButton.START,
            WiiControl.CLASSIC_HOME to PadButton.L3,
        ),
        suggestedSticks = mapOf(
            WiiControl.CLASSIC_LEFT_STICK to setOf(StickSource.LEFT),
            WiiControl.CLASSIC_RIGHT_STICK to setOf(StickSource.RIGHT),
            WiiControl.CLASSIC_DPAD to setOf(StickSource.DPAD),
        ),
    ),
    ;

    /** The controls on this tab, in the order the screen lists them. */
    val controls: List<WiiControl>
        get() = when (this) {
            NUNCHUK -> REMOTE + listOf(WiiControl.POINTER, WiiControl.RECENTER) + MOTION + NUNCHUK_CONTROLS + HOTKEYS
            SIDEWAYS -> REMOTE + MOTION + HOTKEYS
            POINTING -> REMOTE + listOf(WiiControl.POINTER, WiiControl.RECENTER) + MOTION + HOTKEYS
            CLASSIC -> WiiControl.entries.filter { it.name.startsWith("CLASSIC_") }
        }

    val hasPointer: Boolean get() = WiiControl.POINTER in controls

    companion object {
        private val REMOTE = listOf(
            WiiControl.REMOTE_A,
            WiiControl.REMOTE_B,
            WiiControl.REMOTE_1,
            WiiControl.REMOTE_2,
            WiiControl.REMOTE_MINUS,
            WiiControl.REMOTE_PLUS,
            WiiControl.REMOTE_HOME,
            WiiControl.REMOTE_DPAD,
        )
        private val MOTION = listOf(WiiControl.SHAKE, WiiControl.TILT, WiiControl.SWING)
        private val NUNCHUK_CONTROLS =
            listOf(WiiControl.NUNCHUK_STICK, WiiControl.NUNCHUK_C, WiiControl.NUNCHUK_Z, WiiControl.NUNCHUK_SHAKE)
        private val HOTKEYS = listOf(WiiControl.SIDEWAYS_TOGGLE, WiiControl.UPRIGHT_TOGGLE)

        fun byId(id: String?): WiiSetup? = entries.find { it.id == id }

        /** RetroPup's Dolphin setup guide for the AYN Thor, which the suggested layouts follow. */
        const val LAYOUT_CREDIT_URL = "https://www.youtube.com/watch?v=my5XRGNShqA"
    }
}

/**
 * The Wii Remote's buttons as every Wii Remote tab suggests them: letters to letters, the shake on R2. A function, not
 * a value, like [toggles]: building [WiiControl] calls into this file ([directions]), so a value here naming a
 * WiiControl could be built before the constants after the first stick exist, and those would be missing.
 */
private fun remoteButtons(): Map<WiiControl, PadButton> = mapOf(
    WiiControl.REMOTE_A to PadButton.A,
    WiiControl.REMOTE_B to PadButton.B,
    WiiControl.REMOTE_1 to PadButton.X,
    WiiControl.REMOTE_2 to PadButton.Y,
    WiiControl.REMOTE_MINUS to PadButton.SELECT,
    WiiControl.REMOTE_PLUS to PadButton.START,
    WiiControl.SHAKE to PadButton.R2,
)

/** Turning the Wii Remote sideways and upright while playing: hold Select, press L2 or R2. */
private fun toggles(): Map<WiiControl, Pair<PadButton, PadButton>> = mapOf(
    WiiControl.SIDEWAYS_TOGGLE to (PadButton.SELECT to PadButton.L2),
    WiiControl.UPRIGHT_TOGGLE to (PadButton.SELECT to PadButton.R2),
)

/** The Thor buttons a Wii button can take, grouped as they sit on the Thor (the Thor's own and volume keys are left out). */
val WII_BUTTONS: List<PadButton> = PadButton.entries.filter { it.gamepad || it.secondOnly }
