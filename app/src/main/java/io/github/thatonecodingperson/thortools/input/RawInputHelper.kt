package io.github.thatonecodingperson.thortools.input

import android.net.LocalSocket
import android.net.LocalSocketAddress
import io.github.thatonecodingperson.thortools.leds.LightWriter
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * Runs as root through `app_process` (started by [RawInputClient]): reports which screen was touched last and when
 * the last finger left it, the pad's D-pad directions and stick flicks while the app watches the pad, the lid opening
 * and closing, and runs the commands an app may not (see [RootCommands]). It never grabs, mutes or injects anything on
 * its own.
 * Arguments: abstract socket name, the app's uid.
 */
object RawInputHelper {
    private const val EV_SYN = 0
    private const val EV_ABS = 3
    private const val EV_SW = 5
    private const val SYN_REPORT = 0
    private const val SW_LID = 0
    private const val EVENT_SIZE = 24
    private const val TOUCH_REPORT_GAP_MS = 300L

    // The pad as its raw events leave it, and the directions while the app watches (null: not watching).
    private val padLock = Any()
    private val pad = RawPad()
    private var padDirections: PadDirections? = null

    @Volatile
    private var screenOn = true
    private lateinit var output: OutputStream

    // Commands talk to system services and can take a moment; the socket loop must keep reading meanwhile.
    private val commands = Executors.newSingleThreadExecutor()

    // A ping can take a second; on its own thread it never holds up a brightness slider.
    private val slowCommands = Executors.newSingleThreadExecutor()

    @JvmStatic
    fun main(args: Array<String>) {
        val socketName = args.getOrNull(0) ?: exitProcess(1)
        val appUid = args.getOrNull(1)?.toIntOrNull() ?: exitProcess(1)
        val socket = connect(socketName) ?: exitProcess(2)
        // Abstract socket names are first-come: only talk to the app we were started for.
        if (socket.peerCredentials.uid != appUid) exitProcess(3)
        output = socket.outputStream
        val input = socket.inputStream.bufferedReader()
        if (input.readLine() != "hello") exitProcess(4)

        startReader("top", { nodes -> nodes.firstOrNull { it.name == "fts_ts" } }) { _, stream -> readTouch(stream, Screen.TOP) }
        startReader("bottom", { nodes -> nodes.firstOrNull { it.name == "fts_ts_3" } }) { _, stream -> readTouch(stream, Screen.BOTTOM) }
        startReader("pad", ThorPad::pick) { _, stream -> readPad(stream) }
        startReader("lid", { nodes -> nodes.firstOrNull { it.name == "hall_switch" } }) { _, stream -> readLid(stream) }

        // The readers can block in read() for hours, so this loop is the one that notices the app went away.
        while (true) {
            val line = input.readLine()
            when {
                line == null || line == "bye" -> exitProcess(0)
                line == "screen on" -> {
                    screenOn = true
                    LightWriter.screen(true)
                }
                line == "screen off" -> {
                    screenOn = false
                    LightWriter.screen(false)
                }
                line == "pad on" -> watchPad(true)
                line == "pad off" -> watchPad(false)
                line.startsWith("c ") -> (
                    if (line.split(' ').getOrNull(2) ==
                        "ping"
                    ) {
                        slowCommands
                    } else {
                        commands
                    }
                    ).execute { runCommand(line) }
            }
        }
    }

    /** `c <id> <name> <args...>`, answered with a result line for the same id. */
    private fun runCommand(line: String) {
        val parts = line.split(' ').filter { it.isNotEmpty() }
        val id = parts.getOrNull(1)?.toIntOrNull() ?: return
        val result = runCatching { RootCommands.run(parts.getOrNull(2).orEmpty(), parts.drop(3)) }
        val text = result.getOrElse { error -> "${error.javaClass.simpleName}: ${(error.cause ?: error).message}" }
        send(HelperMessage.Result(id, result.isSuccess, text))
    }

    private fun connect(name: String): LocalSocket? {
        repeat(6) {
            val socket = LocalSocket()
            try {
                socket.connect(LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT))
                return socket
            } catch (_: Exception) {
                socket.close()
                Thread.sleep(200)
            }
        }
        return null
    }

    private fun send(message: HelperMessage) {
        val line = HelperMessage.format(message) + "\n"
        synchronized(this) {
            try {
                output.write(line.toByteArray())
                output.flush()
            } catch (_: Exception) {
                exitProcess(0)
            }
        }
    }

    /** One daemon thread per slot. A failing read means the device went away, so look again. */
    private fun startReader(slot: String, pick: (List<InputNode>) -> InputNode?, read: (InputNode, FileInputStream) -> Unit) {
        Thread {
            var reported: String? = null
            while (true) {
                val node = pick(scanNodes())
                if (node == null) {
                    Thread.sleep(1000)
                    continue
                }
                if (node.path != reported) {
                    send(HelperMessage.Status("$slot ${node.path} ${node.name}"))
                    reported = node.path
                }
                try {
                    FileInputStream(node.path).use { read(node, it) }
                } catch (_: Exception) {
                    Thread.sleep(300)
                }
            }
        }.apply {
            isDaemon = true
            name = "input-$slot"
        }.start()
    }

    fun scanNodes(): List<InputNode> = File("/sys/class/input").listFiles { file -> file.name.startsWith("event") }
        .orEmpty()
        .sortedBy { it.name.removePrefix("event").toIntOrNull() ?: Int.MAX_VALUE }
        .mapNotNull { dir ->
            runCatching {
                val device = File(dir, "device")
                InputNode(
                    path = "/dev/input/${dir.name}",
                    name = File(device, "name").readText().trim(),
                    vendor = File(device, "id/vendor").readText().trim().toInt(16),
                    product = File(device, "id/product").readText().trim().toInt(16),
                    abs = runCatching { ThorPad.parseAbs(File(device, "capabilities/abs").readText()) }.getOrDefault(0L),
                )
            }.getOrNull()
        }

    private inline fun forEachEvent(stream: FileInputStream, crossinline handle: (type: Int, code: Int, value: Int) -> Unit) {
        val buffer = ByteArray(EVENT_SIZE * 64)
        while (true) {
            val count = stream.read(buffer)
            if (count <= 0) return
            val events = ByteBuffer.wrap(buffer, 0, count).order(ByteOrder.LITTLE_ENDIAN)
            while (events.remaining() >= EVENT_SIZE) {
                events.position(events.position() + 16)
                val type = events.short.toInt() and 0xffff
                val code = events.short.toInt() and 0xffff
                val value = events.int
                handle(type, code, value)
            }
        }
    }

    /**
     * From now on, the D-pad directions and stick flicks as they change; anything already pushed counts only once it has
     * come back (the app asks while a button with such combos is held).
     */
    private fun watchPad(on: Boolean) = synchronized(padLock) {
        padDirections = if (on) PadDirections().also { it.start(pad.sample()) } else null
    }

    /** Follows the pad's axes all the time, so watching can start from how the pad is at that moment. */
    private fun readPad(stream: FileInputStream) {
        synchronized(padLock) { pad.reset() }
        forEachEvent(stream) { type, code, value ->
            when {
                type == EV_ABS -> synchronized(padLock) { pad.onAbs(code, value) }
                type == EV_SYN && code == SYN_REPORT -> {
                    val changes = synchronized(padLock) { padDirections?.update(pad.sample()) }
                    changes?.forEach { send(HelperMessage.Direction(it.button, it.down)) }
                }
            }
        }
    }

    /** The lid sensor, as the lid closes (1) and opens (0). */
    private fun readLid(stream: FileInputStream) {
        forEachEvent(stream) { type, code, value ->
            if (type == EV_SW && code == SW_LID) send(HelperMessage.Lid(closed = value == 1))
        }
    }

    private fun readTouch(stream: FileInputStream, screen: Screen) {
        var lastReport = 0L
        val slots = TouchSlots()
        forEachEvent(stream) { type, code, value ->
            // Lifts are not reported while the screens are off, so fingers seen before that are forgotten.
            if (!screenOn) return@forEachEvent slots.reset()
            if (type != EV_ABS) return@forEachEvent
            when (slots.onEvent(code, value)) {
                TouchSlots.Change.NEW_FINGER -> {
                    val now = System.currentTimeMillis()
                    if (now - lastReport < TOUCH_REPORT_GAP_MS) return@forEachEvent
                    lastReport = now
                    send(HelperMessage.Touch(screen))
                }
                // The bottom-screen controller lock waits for this before taking the controller back.
                TouchSlots.Change.ALL_LIFTED -> send(HelperMessage.Lift(screen))
                null -> Unit
            }
        }
    }
}
