package io.github.thatonecodingperson.thortools.input

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityOptions
import android.hardware.input.InputManager
import android.os.IBinder
import android.os.SystemClock
import android.view.InputEvent
import android.view.KeyEvent
import io.github.thatonecodingperson.thortools.leds.LedFrame
import io.github.thatonecodingperson.thortools.leds.LedLook
import io.github.thatonecodingperson.thortools.leds.LedPlan
import io.github.thatonecodingperson.thortools.leds.LightWriter
import io.github.thatonecodingperson.thortools.panel.PingParser
import io.github.thatonecodingperson.thortools.panel.StatsSampler
import java.io.File

/**
 * What the root input helper does on request. It runs as uid 0 inside `app_process`, so it may call Android's hidden
 * task and display services directly (no hidden-API checks there). Each command returns a short result text or throws.
 */
// Lint's hidden-API rules are for app processes; this code only ever runs in the root helper's app_process.
@SuppressLint("BlockedPrivateApi", "DiscouragedPrivateApi", "PrivateApi")
internal object RootCommands {

    fun run(name: String, args: List<String>): String = when (name) {
        "move" -> move(args.chunked(3).map { AppMove(it[0], it[1].toInt(), it[2].toInt()) })
        "close" -> close(packageName = args[0], displayId = args[1].toInt())
        "cleanup" -> cleanup(keep = BackgroundTasks.decodeKeep(args.getOrNull(0)))
        "front" -> front(packageName = args[0], displayId = args[1].toInt())
        "key" -> key(
            deviceId = args[0].toInt(),
            keyCode = args[1].toInt(),
            scanCode = args[2].toInt(),
            source = args[3].toInt(),
            displayId = args.getOrNull(4)?.toInt() ?: -1,
            holdMs = args.getOrNull(5)?.toLong() ?: 0L,
        )
        "brightness" -> brightness(displays = args[0].split(',').map(String::toInt), up = args[1] == "up")
        "getbrightness" -> args[0].split(',').joinToString(",") { Displays.brightness(it.toInt()).toString() }
        "setbrightness" -> {
            Displays.setBrightness(args[0].toInt(), args[1].toFloat().coerceIn(0f, 1f))
            "ok"
        }
        "stats" -> stats.sample().encode()
        "ping" -> ping(args[0])
        "led" -> LightWriter.once(
            LedFrame(
                left = checkNotNull(LedPlan.parseColour(args[0])),
                right = checkNotNull(LedPlan.parseColour(args[1])),
                brightness = args[2].toInt(),
            ),
        )
        "ledfx" -> if (args.firstOrNull() == "stop") {
            LightWriter.stop()
        } else {
            LightWriter.start(LedLook.decode(args[0]), battery = args[1].toInt(), charging = args[2] == "1")
        }
        else -> error("unknown command $name")
    }

    // The same reader the app uses, for the files SELinux keeps from apps.
    private val stats by lazy {
        StatsSampler(read = { runCatching { File(it).readText() }.getOrNull() }, list = { File(it).list()?.toList().orEmpty() })
    }

    /** One ping, as root (an app has no internet permission); the round trip in ms, or "-" when nothing came back. */
    private fun ping(host: String): String {
        require(host.matches(Regex("[A-Za-z0-9.:-]+"))) { "bad host" }
        val process = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "1", host).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        return PingParser.millis(output)?.toString() ?: "-"
    }

    /** All tasks are picked first and then moved back to back, so a swap lands within one frame or two. */
    private fun move(moves: List<AppMove>): String {
        val tasks = Tasks.list()
        val picked = moves.map { move ->
            checkNotNull(ScreenSwap.pickTask(tasks, move.packageName, move.fromDisplay, move.toDisplay)) {
                "no task for ${move.packageName}"
            } to move
        }
        picked.forEach { (task, move) -> Tasks.move(task.taskId, move.toDisplay) }
        return "moved ${picked.size}"
    }

    private fun close(packageName: String, displayId: Int): String {
        val task = ScreenSwap.pickTask(Tasks.list(), packageName, from = displayId, to = -1) ?: return "none"
        Tasks.remove(task.taskId)
        return packageName
    }

    /**
     * Closes every task [BackgroundTasks.toClose] picks, running or only listed in Recent apps, the way swiping it away
     * there does (its process goes too once nothing else of it is in use). Answers how many apps closed.
     */
    private fun cleanup(keep: Set<String>): String {
        val closing = BackgroundTasks.toClose(Tasks.list() + Tasks.recent(), keep)
        val closed = closing.filter { task -> runCatching { Tasks.remove(task.taskId) }.isSuccess }
        return closed.mapNotNull { it.packageName }.distinct().size.toString()
    }

    /** Brings the app's task on that display to the front, as Recent apps does: the same task, no new intent. */
    private fun front(packageName: String, displayId: Int): String {
        val task = Tasks.list().firstOrNull { it.packageName == packageName && it.displayId == displayId } ?: return "none"
        Tasks.front(task.taskId, displayId)
        return "front ${task.taskId}"
    }

    /**
     * Presses and releases a key, for one display when [displayId] >= 0 (Home on one screen). Root may inject input.
     * Android gives every injected key the virtual keyboard's device id, whatever [deviceId] says.
     */
    private fun key(deviceId: Int, keyCode: Int, scanCode: Int, source: Int, displayId: Int, holdMs: Long): String {
        val downTime = SystemClock.uptimeMillis()
        Input.inject(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, 0, deviceId, scanCode, 0, source), displayId)
        if (holdMs > 0) Thread.sleep(holdMs)
        Input.inject(
            KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0, 0, deviceId, scanCode, 0, source),
            displayId,
        )
        return "ok"
    }

    /** Steps every display in [displays] and reports the first one's new level in percent. */
    private fun brightness(displays: List<Int>, up: Boolean): String {
        val levels = displays.map { display ->
            BrightnessStep.next(Displays.brightness(display), up).also { Displays.setBrightness(display, it) }
        }
        return BrightnessStep.percent(levels.first()).toString()
    }

    private fun service(name: String, stub: String): Any {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, name) as IBinder
        return checkNotNull(Class.forName(stub).getMethod("asInterface", IBinder::class.java).invoke(null, binder)) { "$name unavailable" }
    }

    private object Tasks {
        private val atm by lazy { service("activity_task", "android.app.IActivityTaskManager\$Stub") }
        private val taskInfo = Class.forName("android.app.TaskInfo")
        private val displayField = runCatching { taskInfo.getDeclaredField("displayId").apply { isAccessible = true } }.getOrNull()
        private val visibleField = runCatching { taskInfo.getDeclaredField("isVisible").apply { isAccessible = true } }.getOrNull()

        /** `getTasks` changes its parameters between Android versions: the first int is the count, other ints mean "all". */
        fun list(): List<TaskSnapshot> {
            val getTasks = atm.javaClass.methods.first { it.name == "getTasks" }
            var countGiven = false
            val args: List<Any?> = getTasks.parameterTypes.map { type ->
                when (type) {
                    Int::class.javaPrimitiveType -> if (countGiven) -1 else 50.also { countGiven = true }
                    Boolean::class.javaPrimitiveType -> false
                    else -> null
                }
            }
            return (getTasks.invoke(atm, *args.toTypedArray()) as List<*>)
                .filterIsInstance<ActivityManager.RunningTaskInfo>()
                .map { task ->
                    TaskSnapshot(
                        taskId = task.taskId,
                        displayId = displayField?.getInt(task) ?: 0,
                        visible = visibleField?.getBoolean(task) ?: true,
                        packageName = (task.topActivity ?: task.baseActivity)?.packageName,
                    )
                }
        }

        /**
         * The tasks Recent apps lists, including ones whose app isn't running any more (none when Android's method can't
         * be called). `getRecentTasks(maxNum, flags, userId)`; flag 2 leaves out tasks whose app is gone.
         */
        fun recent(): List<TaskSnapshot> = runCatching {
            val getRecent = atm.javaClass.methods.first { it.name == "getRecentTasks" && it.parameterTypes.size == 3 }
            val slice = getRecent.invoke(atm, RECENT_MAX, RECENT_IGNORE_UNAVAILABLE, 0) ?: return emptyList()
            (slice.javaClass.getMethod("getList").invoke(slice) as List<*>)
                .filterIsInstance<ActivityManager.RecentTaskInfo>()
                .map { task ->
                    TaskSnapshot(
                        taskId = task.taskId,
                        displayId = displayField?.getInt(task) ?: 0,
                        visible = visibleField?.getBoolean(task) ?: true,
                        // Declared non-null, but a task restored from disk can lack it: one such task mustn't end the list.
                        packageName = (runCatching { task.baseIntent.component }.getOrNull() ?: task.topActivity ?: task.baseActivity)
                            ?.packageName,
                    )
                }
        }.getOrDefault(emptyList())

        fun move(taskId: Int, displayId: Int) {
            val move = atm.javaClass.methods.first { it.name == "moveRootTaskToDisplay" || it.name == "moveStackToDisplay" }
            move.invoke(atm, taskId, displayId)
        }

        fun front(taskId: Int, displayId: Int) {
            val fromRecents = atm.javaClass.methods.first { it.name == "startActivityFromRecents" && it.parameterTypes.size == 2 }
            fromRecents.invoke(atm, taskId, ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())
        }

        fun remove(taskId: Int) {
            atm.javaClass.methods.first { it.name == "removeTask" && it.parameterTypes.size == 1 }.invoke(atm, taskId)
        }

        private const val RECENT_MAX = 50
        private const val RECENT_IGNORE_UNAVAILABLE = 2
    }

    private object Input {
        // Android 14 moved the injector to InputManagerGlobal; the Thor's Android 13 still has InputManager.getInstance().
        private val manager: Any by lazy {
            runCatching { Class.forName("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null) }.getOrNull()
                ?: checkNotNull(InputManager::class.java.getMethod("getInstance").invoke(null))
        }
        private val injectMethod by lazy {
            manager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        }

        // Hidden, so reflected: a key for one display (Home on the bottom screen goes to the bottom screen's home).
        private val setDisplayId by lazy { InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType) }

        fun inject(event: InputEvent, displayId: Int) {
            if (displayId >= 0) setDisplayId.invoke(event, displayId)
            injectMethod.invoke(manager, event, INJECT_ASYNC)
        }

        private const val INJECT_ASYNC = 0
    }

    private object Displays {
        private val global by lazy {
            checkNotNull(Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null))
        }

        fun brightness(displayId: Int): Float = runCatching {
            global.javaClass.getMethod("getBrightness", Int::class.javaPrimitiveType).invoke(global, displayId) as Float
        }.getOrElse {
            val info = checkNotNull(global.javaClass.getMethod("getBrightnessInfo", Int::class.javaPrimitiveType).invoke(global, displayId))
            info.javaClass.getField("brightness").getFloat(info)
        }

        fun setBrightness(displayId: Int, value: Float) {
            global.javaClass.getMethod("setBrightness", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                .invoke(global, displayId, value)
        }
    }
}
