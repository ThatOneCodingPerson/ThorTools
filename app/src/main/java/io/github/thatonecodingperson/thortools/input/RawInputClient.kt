package io.github.thatonecodingperson.thortools.input

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.thatonecodingperson.thortools.hotkeys.PadButton
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Starts [RawInputHelper] as root through PServer and turns its lines into callbacks on the main thread.
 * The protocol is in [HelperMessage]; a helper that keeps exiting is relaunched a few times a minute at most.
 */
class RawInputClient(private val context: Context, private val executor: ShellExecutor, private val listener: Listener) {

    interface Listener {
        fun onTouch(screen: Screen)

        /** The last finger left [screen]. */
        fun onLift(screen: Screen)

        /** A D-pad direction or stick flick went down or up, read raw from the pad while it is watched ([watchPad]). */
        fun onDirection(button: PadButton, down: Boolean)

        /** The lid closed or opened. */
        fun onLid(closed: Boolean)

        /** The helper connected (again); work that needs it can start. */
        fun onConnected() {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var wanted = false

    @Volatile
    private var screenOn = true

    @Volatile
    private var server: LocalServerSocket? = null

    @Volatile
    private var socket: LocalSocket? = null
    private var lastLaunch = 0L
    private val launches = ArrayDeque<Long>()

    @Volatile
    var state: String = "off"
        private set

    @Volatile
    var devices: List<String> = emptyList()
        private set

    private val relaunch = Runnable { launch() }
    private val nextCommand = AtomicInteger()
    private val pending = ConcurrentHashMap<Int, (Boolean, String) -> Unit>()

    val connected: Boolean get() = socket != null

    /** The helper runs and has found the Thor's own pad, so [watchPad] gets directions. */
    val padReady: Boolean get() = socket != null && devices.any { it.startsWith(PAD_SLOT) }

    /**
     * Asks the root helper to do something (see `RootCommands`). [onResult] runs on the main thread with success and
     * the helper's text. False when the helper isn't connected. Arguments must not contain spaces.
     */
    fun command(name: String, vararg args: String, onResult: (Boolean, String) -> Unit): Boolean {
        if (socket == null) return false
        val id = nextCommand.incrementAndGet()
        pending[id] = onResult
        sendLine(listOf("c", id.toString(), name, *args).joinToString(" "))
        mainHandler.postDelayed({ pending.remove(id)?.invoke(false, "timeout") }, COMMAND_TIMEOUT_MS)
        return true
    }

    fun start() {
        wanted = true
        launch()
    }

    fun stop() {
        wanted = false
        mainHandler.removeCallbacks(relaunch)
        sendLine("bye")
        closeAll()
        state = "off"
    }

    fun setScreenOn(on: Boolean) {
        screenOn = on
        sendLine(if (on) "screen on" else "screen off")
    }

    /** While on, the helper reports the pad's D-pad directions and stick flicks as they change. */
    fun watchPad(on: Boolean) = sendLine(if (on) "pad on" else "pad off")

    /** Single flight, at most one launch every few seconds and a few per minute, so a helper that can't start won't loop. */
    @Synchronized
    private fun launch() {
        if (!wanted || socket != null || server != null) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < LAUNCH_DEBOUNCE_MS) return
        while (launches.isNotEmpty() && now - launches.first() > RELAUNCH_WINDOW_MS) launches.removeFirst()
        if (launches.size >= MAX_LAUNCHES_PER_WINDOW) {
            state = "stopped: the helper keeps exiting"
            return
        }
        lastLaunch = now
        launches.addLast(now)
        state = "starting"

        val name = "thortools.input.${UUID.randomUUID()}"
        val serverSocket = LocalServerSocket(name)
        server = serverSocket
        Thread({ acceptAndRead(serverSocket) }, "input-client").apply { isDaemon = true }.start()
        Thread({ startHelper(name) }, "input-launch").apply { isDaemon = true }.start()
        mainHandler.postDelayed({ giveUpWaiting(serverSocket) }, CONNECT_TIMEOUT_MS)
    }

    /** A helper that never connects would otherwise keep the server socket, and so the next launch, blocked. */
    private fun giveUpWaiting(serverSocket: LocalServerSocket) {
        if (socket != null || server !== serverSocket) return
        state = "the helper didn't connect"
        closeAll()
        scheduleRelaunch()
    }

    private fun startHelper(socketName: String) {
        val script = File(context.cacheDir, "input-helper-${SystemClock.elapsedRealtime()}.sh")
        // PServer reaps its shell as soon as it returns, which would kill a child that hasn't been reparented yet.
        script.writeText(
            "CLASSPATH=${context.applicationInfo.sourceDir} app_process /system/bin ${RawInputHelper::class.java.name} " +
                "$socketName ${context.applicationInfo.uid} > /dev/null 2>&1 &\nsleep 1\n",
        )
        script.setReadable(true, false)
        val result = executor.executeAsRoot("sh ${script.absolutePath}")
        script.delete()
        if (result.isFailure) {
            state = "PServer unavailable"
            closeAll()
            scheduleRelaunch()
        }
    }

    private fun acceptAndRead(serverSocket: LocalServerSocket) {
        val client = try {
            serverSocket.accept()
        } catch (_: Exception) {
            return
        }
        if (client.peerCredentials.uid != 0) {
            client.close()
            state = "refused a helper that isn't root"
            closeAll()
            scheduleRelaunch()
            return
        }
        socket = client
        devices = emptyList()
        state = "connected"
        sendLine("hello")
        sendLine(if (screenOn) "screen on" else "screen off")
        mainHandler.post { listener.onConnected() }
        try {
            client.inputStream.bufferedReader().forEachLine { line ->
                when (val message = HelperMessage.parse(line)) {
                    is HelperMessage.Touch -> mainHandler.post { listener.onTouch(message.screen) }
                    is HelperMessage.Lift -> mainHandler.post { listener.onLift(message.screen) }
                    is HelperMessage.Direction -> mainHandler.post { listener.onDirection(message.button, message.down) }
                    is HelperMessage.Lid -> mainHandler.post { listener.onLid(message.closed) }
                    is HelperMessage.Status ->
                        devices =
                            (devices.filterNot { it.startsWith(message.text.substringBefore(' ')) } + message.text)
                    is HelperMessage.Result -> pending.remove(message.id)?.let { callback ->
                        mainHandler.post { callback(message.ok, message.text) }
                    }
                    null -> Unit
                }
            }
        } catch (_: Exception) {
            // Handled below: the helper is gone either way.
        }
        mainHandler.post {
            pending.keys.toList().forEach { id -> pending.remove(id)?.invoke(false, "helper stopped") }
        }
        closeAll()
        if (wanted) {
            state = "restarting"
            scheduleRelaunch()
        }
    }

    private fun scheduleRelaunch() {
        if (wanted) mainHandler.postDelayed(relaunch, RELAUNCH_DELAY_MS)
    }

    private fun sendLine(line: String) {
        val target = socket ?: return
        runCatching {
            synchronized(target) {
                target.outputStream.write("$line\n".toByteArray())
                target.outputStream.flush()
            }
        }
    }

    @Synchronized
    private fun closeAll() {
        runCatching { socket?.close() }
        runCatching { server?.close() }
        socket = null
        server = null
    }

    private companion object {
        const val PAD_SLOT = "pad "
        const val LAUNCH_DEBOUNCE_MS = 3000L
        const val RELAUNCH_DELAY_MS = 3200L
        const val RELAUNCH_WINDOW_MS = 60_000L
        const val MAX_LAUNCHES_PER_WINDOW = 5
        const val CONNECT_TIMEOUT_MS = 8000L
        const val COMMAND_TIMEOUT_MS = 5000L
    }
}
