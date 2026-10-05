package io.github.thatonecodingperson.thortools.wii

import io.github.thatonecodingperson.thortools.hotkeys.PadButton

/**
 * Writes a Wii Remote profile for Dolphin. Every binding names the Thor's pad in both of AYN's controller styles, each
 * with its own code, joined with `|` (Dolphin takes the stronger one), so one profile works in either style: the
 * Xbox style reports the face buttons by position, the standard style by their printed letters.
 */
object DolphinProfile {
    const val XBOX_PAD = "Xbox Wireless Controller"
    const val STANDARD_PAD = "Odin Controller"
    private const val SENSORS = "Android/0/Device Sensors"
    private const val FIRST_PAD = 1

    /** A Dolphin input device: `Android/<index>/<name>`; Android numbers its first game controller 1. */
    data class Device(val index: Int, val name: String) {
        override fun toString() = "Android/$index/$name"
    }

    /** The device Dolphin's first Wii Remote uses in `WiimoteNew.ini`, when it is the Thor's pad. */
    fun deviceIn(wiimoteIni: String?): Device? {
        var inFirst = false
        wiimoteIni.orEmpty().lineSequence().map { it.trim() }.forEach { line ->
            if (line.startsWith("[")) {
                inFirst = line == "[Wiimote1]"
            } else if (inFirst && line.startsWith("Device")) {
                val value = line.substringAfter('=', "").trim()
                val parts = value.split('/', limit = 3)
                if (parts.size == 3 && parts[0] == "Android" && parts[2] in listOf(XBOX_PAD, STANDARD_PAD)) {
                    parts[1].toIntOrNull()?.let { return Device(it, parts[2]) }
                }
            }
        }
        return null
    }

    /** The Thor's pad as Dolphin names it in the current style. */
    fun device(xboxStyle: Boolean, index: Int = FIRST_PAD) = Device(index, if (xboxStyle) XBOX_PAD else STANDARD_PAD)

    /** [vibrates]: the device has a vibration motor for Dolphin's rumble (the Thor's pad has none of its own). */
    fun ini(mapping: WiiMapping, device: Device, vibrates: Boolean = true): String {
        val lines = mutableListOf("[Profile]", "Device = $device")
        mapping.setup.controls.forEach { control ->
            when (control.kind) {
                WiiKind.BUTTON -> mapping.buttons[control]?.let { button ->
                    val expression = expression(listOf(button), device.index)
                    control.keys.forEach { key -> lines += "$key = $expression" }
                }
                WiiKind.STICK -> mapping.sticks[control]?.takeIf { it.isNotEmpty() }?.let { sources ->
                    control.keys.forEachIndexed { direction, key ->
                        lines += "$key = ${expression(sources.sortedBy { it.ordinal }.map { it.directions[direction] }, device.index)}"
                    }
                }
                WiiKind.COMBO -> mapping.combos[control]?.takeUnless { it.first == it.second }?.let { (held, pressed) ->
                    val expression = hotkey(held, pressed, device.index)
                    control.keys.forEach { key -> lines += "$key = $expression" }
                }
            }
        }
        if (mapping.setup.hasPointer && mapping.sticks[WiiControl.POINTER].orEmpty().isNotEmpty() && mapping.relativePointer) {
            lines += "IR/Relative Input = True"
        }
        if (mapping.setup.sideways) lines += "Options/Sideways Wiimote = True"
        lines += "Extension = ${mapping.setup.extension}"
        if (mapping.rumble && vibrates) lines += "Rumble/Motor = `$SENSORS:Motor 0`"
        return lines.joinToString("\n", postfix = "\n")
    }

    /** One binding: each Thor button in both controller styles, the codes joined with `|`. */
    fun expression(buttons: List<PadButton>, index: Int): String = listOf(XBOX_PAD, STANDARD_PAD).flatMap { pad ->
        buttons.flatMap { codes(it, xboxStyle = pad == XBOX_PAD) }.map { "`Android/$index/$pad:$it`" }
    }.distinct().joinToString(" | ")

    /**
     * One of Dolphin's hotkeys, `@(held+pressed)`, for every code of both buttons in both styles. It fires only when
     * [held] is down before [pressed], and while it holds Dolphin keeps [pressed] from its other bindings, so Select + L2
     * doesn't also press what L2 does alone. A hotkey takes single inputs only, so each pair is its own term.
     */
    fun hotkey(held: PadButton, pressed: PadButton, index: Int): String = listOf(XBOX_PAD, STANDARD_PAD).flatMap { pad ->
        val xbox = pad == XBOX_PAD
        codes(held, xbox).flatMap { first ->
            codes(pressed, xbox).map { second -> "@(`Android/$index/$pad:$first`+`Android/$index/$pad:$second`)" }
        }
    }.distinct().joinToString(" | ")

    /**
     * What Dolphin calls a Thor button. The D-pad only reports its hat axes; L2 and R2 send a key in AYN's digital
     * trigger mode and two axes in the analog one (the trigger, and brake or gas).
     */
    fun codes(button: PadButton, xboxStyle: Boolean): List<String> = when (button) {
        PadButton.A -> listOf(if (xboxStyle) "Button B" else "Button A")
        PadButton.B -> listOf(if (xboxStyle) "Button A" else "Button B")
        PadButton.X -> listOf(if (xboxStyle) "Button Y" else "Button X")
        PadButton.Y -> listOf(if (xboxStyle) "Button X" else "Button Y")
        PadButton.L1 -> listOf("Button L1")
        PadButton.R1 -> listOf("Button R1")
        PadButton.L2 -> listOf("Button L2", "Axis 17+", "Axis 23+")
        PadButton.R2 -> listOf("Button R2", "Axis 18+", "Axis 22+")
        PadButton.L3 -> listOf("Button L3")
        PadButton.R3 -> listOf("Button R3")
        PadButton.START -> listOf("Start")
        PadButton.SELECT -> listOf("Select")
        PadButton.DPAD_UP -> listOf("Axis 16-")
        PadButton.DPAD_DOWN -> listOf("Axis 16+")
        PadButton.DPAD_LEFT -> listOf("Axis 15-")
        PadButton.DPAD_RIGHT -> listOf("Axis 15+")
        PadButton.LSTICK_UP -> listOf("Axis 1-")
        PadButton.LSTICK_DOWN -> listOf("Axis 1+")
        PadButton.LSTICK_LEFT -> listOf("Axis 0-")
        PadButton.LSTICK_RIGHT -> listOf("Axis 0+")
        PadButton.RSTICK_UP -> listOf("Axis 14-")
        PadButton.RSTICK_DOWN -> listOf("Axis 14+")
        PadButton.RSTICK_LEFT -> listOf("Axis 11-")
        PadButton.RSTICK_RIGHT -> listOf("Axis 11+")
        else -> emptyList()
    }

    /** A profile name Dolphin and every file system take: letters, digits, spaces, dashes and underscores. */
    fun fileName(name: String, fallback: String): String {
        val clean = name.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }.trim().take(MAX_NAME)
        return clean.ifEmpty { fallback }
    }

    private const val MAX_NAME = 40
}
