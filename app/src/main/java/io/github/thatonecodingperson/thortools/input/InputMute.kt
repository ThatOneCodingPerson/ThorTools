package io.github.thatonecodingperson.thortools.input

import io.github.thatonecodingperson.thortools.lid.InputGroup
import java.io.File

/** Which input devices each [InputGroup] mutes. The power key and the lid sensor are never among them. Pure. */
object MuteTargets {
    /** Never muted, whatever is asked: the power key is the way out, the lid sensor wakes the Thor when it opens. */
    val NEVER = setOf("pmic_pwrkey", "hall_switch")

    private val BUTTONS = setOf("gpio-keys", "pmic_resin")
    private val TOUCH = setOf("fts_ts", "fts_ts_3")

    fun pick(nodes: List<InputNode>, groups: Set<InputGroup>): List<InputNode> {
        val pad = ThorPad.pick(nodes)
        return nodes.filter { node ->
            node.name !in NEVER &&
                (
                    (InputGroup.BUTTONS in groups && node.name in BUTTONS) ||
                        (InputGroup.TOUCH in groups && node.name in TOUCH) ||
                        (InputGroup.CONTROLLER in groups && node == pad)
                    )
        }
    }

    /** The kernel's switch for muting a device (Linux 5.11+): `/sys/class/input/eventN/device/inhibited`. */
    fun inhibitedFile(node: InputNode): String = "/sys/class/input/${File(node.path).name}/device/inhibited"
}

/**
 * Mutes and unmutes input devices for the lid sandbox, inside the root input helper (only root may write the switch).
 * Unmuting covers every device of every group, not only those this helper muted, so a mute left by an earlier helper
 * never sticks.
 */
internal object InputMute {
    private var mutedGroups: Set<InputGroup> = emptySet()

    @Synchronized
    fun mute(nodes: List<InputNode>, groups: Set<InputGroup>): String {
        mutedGroups = groups
        val muted = MuteTargets.pick(nodes, groups).filter { write(it, true) }
        return muted.joinToString(",") { it.name }.ifEmpty { "-" }
    }

    @Synchronized
    fun unmuteAll(nodes: List<InputNode>): String {
        mutedGroups = emptySet()
        val unmuted = MuteTargets.pick(nodes, InputGroup.entries.toSet()).filter { write(it, false) }
        return unmuted.joinToString(",") { it.name }.ifEmpty { "-" }
    }

    /** AYN re-creates its pad on sleep; a new pad while the controller is muted is muted too. */
    @Synchronized
    fun padAppeared(pad: InputNode) {
        if (InputGroup.CONTROLLER in mutedGroups) write(pad, true)
    }

    private fun write(node: InputNode, mute: Boolean): Boolean =
        runCatching { File(MuteTargets.inhibitedFile(node)).writeText(if (mute) "1" else "0") }.isSuccess
}
