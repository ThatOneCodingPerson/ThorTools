package io.github.thatonecodingperson.thortools.input

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One running task as the root input helper sees it. */
data class TaskSnapshot(val taskId: Int, val displayId: Int, val visible: Boolean, val packageName: String?)

/**
 * "Close background apps": like swiping each app away in Recent apps, for every task that isn't showing on a screen.
 * Tasks of [keep] packages stay (the launchers, Thor Tools itself, the keyboard, system and AYN apps, the apps on the
 * screens), and so does any task that is visible. Pure.
 */
object BackgroundTasks {
    fun toClose(tasks: List<TaskSnapshot>, keep: Set<String>): List<TaskSnapshot> = tasks
        .filter { task -> !task.visible && task.packageName != null && task.packageName !in keep }
        .distinctBy { it.taskId }

    /** The keep list as one helper argument: package names never contain commas or spaces. */
    fun encodeKeep(keep: Set<String>): String = keep.filter { it.isNotBlank() }.sorted().joinToString(",").ifEmpty { NONE }

    fun decodeKeep(arg: String?): Set<String> = arg.orEmpty().split(',').filter { it.isNotBlank() && it != NONE }.toSet()

    private const val NONE = "-"
}

/** One window as the accessibility service sees it, top-most first per display. */
data class WindowSnapshot(val displayId: Int, val isApplication: Boolean, val packageName: String?)

data class AppMove(val packageName: String, val fromDisplay: Int, val toDisplay: Int)

enum class SwapResult { SWAPPED, MOVED_DOWN, MOVED_UP, SAME_APP, NOTHING }

/** Which app shows on which screen, read from the window list rather than the task list. */
object ScreenApps {
    /**
     * The top-most application window per display that belongs to a real app. [passThrough] packages (permission
     * dialogs, Android's own choosers) sit inside another app's task, so the window under them counts instead.
     */
    fun onScreens(windows: List<WindowSnapshot>, excluded: Set<String>, passThrough: Set<String>): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        for (window in windows) {
            if (window.displayId in result || !window.isApplication) continue
            val pkg = window.packageName ?: continue
            if (pkg in passThrough) continue
            // A launcher (or anything excluded) on top means no app is in front on that screen.
            if (pkg in excluded) {
                result[window.displayId] = ""
                continue
            }
            result[window.displayId] = pkg
        }
        return result.filterValues { it.isNotEmpty() }
    }
}

/**
 * "Swap screens": the apps on the two screens trade places, or the only app moves to the other screen. The app
 * going to the top screen moves first, so it covers the one leaving and the screen never flashes what lies beneath.
 */
object ScreenSwap {
    fun plan(topApp: String?, bottomApp: String?, top: Int, bottom: Int): Pair<SwapResult, List<AppMove>> = when {
        topApp != null && topApp == bottomApp -> SwapResult.SAME_APP to emptyList()
        topApp != null && bottomApp != null ->
            SwapResult.SWAPPED to listOf(AppMove(bottomApp, bottom, top), AppMove(topApp, top, bottom))
        topApp != null -> SwapResult.MOVED_DOWN to listOf(AppMove(topApp, top, bottom))
        bottomApp != null -> SwapResult.MOVED_UP to listOf(AppMove(bottomApp, bottom, top))
        else -> SwapResult.NOTHING to emptyList()
    }

    /**
     * The task to move for [packageName]. An app can have several tasks, and the task list is grouped by display, so
     * the first match can be a hidden task elsewhere: prefer the visible one on the screen the app was seen on.
     */
    fun pickTask(tasks: List<TaskSnapshot>, packageName: String, from: Int, to: Int): TaskSnapshot? {
        val mine = tasks.filter { it.packageName == packageName }
        return mine.firstOrNull { it.displayId == from && it.visible }
            ?: mine.firstOrNull { it.displayId == from }
            ?: mine.firstOrNull { it.displayId != to && it.visible }
            ?: mine.firstOrNull { it.displayId != to }
    }
}

/** Brightness steps that feel even: the step is taken on the square root of the panel's linear 0..1 value. */
/** Slider positions for levels are written in 5 % steps, so a drag only sends a write when the step changes. */
object LevelSteps {
    private const val STEPS = 20

    fun snap(position: Float): Float = (position.coerceIn(0f, 1f) * STEPS).roundToInt() / STEPS.toFloat()

    fun volumeIndex(position: Float, max: Int): Int = (position.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)
}

object BrightnessStep {
    private const val STEP = 0.1f
    private const val FLOOR = 0.05f

    fun next(current: Float, up: Boolean): Float {
        val perceived = sqrt(current.coerceIn(0f, 1f)) + if (up) STEP else -STEP
        val clamped = perceived.coerceIn(FLOOR, 1f)
        return clamped * clamped
    }

    fun percent(value: Float): Int = (sqrt(value.coerceIn(0f, 1f)) * 100).toInt()

    /** A slider position (as the eye sees it, 0..1) for a linear brightness, and back. */
    fun toPerceived(linear: Float): Float = sqrt(linear.coerceIn(0f, 1f))

    fun toLinear(perceived: Float): Float {
        val clamped = perceived.coerceIn(FLOOR, 1f)
        return clamped * clamped
    }
}
