package io.github.thatonecodingperson.thortools.input

import io.github.thatonecodingperson.thortools.hotkeys.PadButton

/** An input device as sysfs lists it; [abs] is its `EV_ABS` capability bits (axes 0 to 63). */
data class InputNode(val path: String, val name: String, val vendor: Int, val product: Int, val abs: Long = 0L, val version: Int = 0) {
    /** sysfs's own directory for this node, `/sys/class/input/eventN`. */
    val sysfs: String get() = "/sys/class/input/" + path.substringAfterLast('/')
}

enum class Screen { TOP, BOTTOM }

/** Follows a multi-touch screen's slots (protocol B), to tell a new finger and the moment the last one leaves. */
class TouchSlots {
    enum class Change { NEW_FINGER, ALL_LIFTED }

    private var slot = 0
    private val down = mutableSetOf<Int>()

    /** [code] and [value] of an `EV_ABS` event; other axes give null. */
    fun onEvent(code: Int, value: Int): Change? = when (code) {
        ABS_MT_SLOT -> {
            slot = value
            null
        }
        ABS_MT_TRACKING_ID -> if (value >= 0) {
            down += slot
            Change.NEW_FINGER
        } else if (down.remove(slot) && down.isEmpty()) {
            Change.ALL_LIFTED
        } else {
            null
        }
        else -> null
    }

    fun reset() {
        slot = 0
        down.clear()
    }

    companion object {
        const val ABS_MT_SLOT = 47
        const val ABS_MT_TRACKING_ID = 57
    }
}

/** Lines between the root input helper and the app. */
sealed interface HelperMessage {
    data class Touch(val screen: Screen) : HelperMessage

    /** The last finger left that screen. */
    data class Lift(val screen: Screen) : HelperMessage

    data class Status(val text: String) : HelperMessage

    /** The answer to the app's command number [id]. */
    data class Result(val id: Int, val ok: Boolean, val text: String) : HelperMessage

    /** A D-pad direction or stick flick of the Thor's pad went down or up, while the app watches the pad. */
    data class Direction(val button: PadButton, val down: Boolean) : HelperMessage

    /** The lid closed or opened (`hall_switch`, `SW_LID`). */
    data class Lid(val closed: Boolean) : HelperMessage

    /** The picture of display [displayId] went still, or started moving again (OLED Safety's watch). */
    data class Picture(val displayId: Int, val still: Boolean) : HelperMessage

    /** The share of display [displayId] protected as still areas now, in percent (OLED Safety). */
    data class Areas(val displayId: Int, val percent: Int) : HelperMessage

    /** Desktop controls were switched off ([paused]) or on again by holding Start. */
    data class DesktopPaused(val paused: Boolean) : HelperMessage

    /** A button with the Keyboard job was pressed in desktop controls. */
    data object DesktopKeyboard : HelperMessage

    /** Desktop controls' devices are up and following the pad ([on]), or not (off, or the uinput tool failed). */
    data class DesktopReady(val on: Boolean) : HelperMessage

    /** Desktop controls let go of a mouse button while keeping the controller on the bottom screen. */
    data object DesktopClicked : HelperMessage

    /** Something went wrong in desktop controls, for the app's log. */
    data class DesktopProblem(val text: String) : HelperMessage

    companion object {
        fun parse(line: String): HelperMessage? {
            val trimmed = line.trim()
            if (trimmed.startsWith("r ")) return parseResult(trimmed)
            val parts = trimmed.split(' ', limit = 3)
            return when (parts.getOrNull(0)) {
                "t" -> Screen.entries.find { it.name.equals(parts.getOrNull(1), ignoreCase = true) }?.let(::Touch)
                "u" -> Screen.entries.find { it.name.equals(parts.getOrNull(1), ignoreCase = true) }?.let(::Lift)
                "s" -> Status(parts.drop(1).joinToString(" "))
                "d" -> parseDirection(parts)
                "l" -> when (parts.getOrNull(1)) {
                    "1" -> Lid(closed = true)
                    "0" -> Lid(closed = false)
                    else -> null
                }
                "p" -> parsePicture(trimmed.split(' '))
                "dp" -> when (parts.getOrNull(1)) {
                    "1" -> DesktopPaused(paused = true)
                    "0" -> DesktopPaused(paused = false)
                    else -> null
                }
                "dk" -> DesktopKeyboard
                "dr" -> when (parts.getOrNull(1)) {
                    "1" -> DesktopReady(on = true)
                    "0" -> DesktopReady(on = false)
                    else -> null
                }
                "de" -> DesktopProblem(trimmed.substringAfter(' ', ""))
                "dc" -> DesktopClicked
                "a" -> trimmed.split(' ').let { words ->
                    val id = words.getOrNull(1)?.toIntOrNull()
                    val percent = words.getOrNull(2)?.toIntOrNull()
                    if (id != null && percent != null) Areas(id, percent.coerceIn(0, 100)) else null
                }
                else -> null
            }
        }

        private fun parseDirection(parts: List<String>): Direction? {
            val button = PadButton.byId(parts.getOrNull(1))?.takeIf { it.secondOnly } ?: return null
            return when (parts.getOrNull(2)) {
                "1" -> Direction(button, down = true)
                "0" -> Direction(button, down = false)
                else -> null
            }
        }

        private fun parsePicture(parts: List<String>): Picture? {
            val id = parts.getOrNull(1)?.toIntOrNull() ?: return null
            return when (parts.getOrNull(2)) {
                "1" -> Picture(id, still = true)
                "0" -> Picture(id, still = false)
                else -> null
            }
        }

        private fun parseResult(line: String): Result? {
            val parts = line.split(' ', limit = 4)
            val id = parts.getOrNull(1)?.toIntOrNull() ?: return null
            val ok = when (parts.getOrNull(2)) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            return Result(id, ok, parts.getOrNull(3).orEmpty())
        }

        fun format(message: HelperMessage): String = when (message) {
            is Touch -> "t ${message.screen.name.lowercase()}"
            is Lift -> "u ${message.screen.name.lowercase()}"
            is Status -> "s ${message.text.replace('\n', ' ')}"
            is Result -> "r ${message.id} ${if (message.ok) 1 else 0} ${message.text.replace('\n', ' ')}"
            is Direction -> "d ${message.button.id} ${if (message.down) 1 else 0}"
            is Lid -> "l ${if (message.closed) 1 else 0}"
            is Picture -> "p ${message.displayId} ${if (message.still) 1 else 0}"
            is Areas -> "a ${message.displayId} ${message.percent}"
            is DesktopPaused -> "dp ${if (message.paused) 1 else 0}"
            DesktopKeyboard -> "dk"
            is DesktopReady -> "dr ${if (message.on) 1 else 0}"
            is DesktopProblem -> "de ${message.text.replace('\n', ' ').replace('\r', ' ')}"
            DesktopClicked -> "dc"
        }
    }
}
