package io.github.thatonecodingperson.thortools.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTasksTest {

    private val launcher = "com.android.launcher3"
    private val assistant = "com.odin.dualscreen.assistant"
    private val excluded = setOf(launcher, assistant, "com.android.systemui")
    private val dialogs = setOf("com.android.permissioncontroller")

    @Test
    fun `the top-most real app per screen is found, launchers mean no app`() {
        val windows = listOf(
            WindowSnapshot(0, isApplication = false, packageName = "com.android.systemui"),
            WindowSnapshot(0, isApplication = true, packageName = "game"),
            WindowSnapshot(0, isApplication = true, packageName = "older"),
            WindowSnapshot(2, isApplication = true, packageName = launcher),
            WindowSnapshot(2, isApplication = true, packageName = "behind the launcher"),
        )
        assertEquals(mapOf(0 to "game"), ScreenApps.onScreens(windows, excluded, dialogs))
    }

    @Test
    fun `a permission dialog counts as the app underneath and AYN's assistant never counts`() {
        val windows = listOf(
            WindowSnapshot(0, isApplication = true, packageName = "com.android.permissioncontroller"),
            WindowSnapshot(0, isApplication = true, packageName = "camera"),
            WindowSnapshot(2, isApplication = true, packageName = assistant),
            WindowSnapshot(2, isApplication = true, packageName = "browser"),
        )
        assertEquals(mapOf(0 to "camera"), ScreenApps.onScreens(windows, excluded, dialogs))
    }

    @Test
    fun `swapping moves the app going to the top screen first`() {
        assertEquals(
            SwapResult.SWAPPED to listOf(AppMove("browser", 2, 0), AppMove("game", 0, 2)),
            ScreenSwap.plan(topApp = "game", bottomApp = "browser", top = 0, bottom = 2),
        )
    }

    @Test
    fun `a single app moves to the other screen`() {
        assertEquals(SwapResult.MOVED_DOWN to listOf(AppMove("game", 0, 2)), ScreenSwap.plan("game", null, top = 0, bottom = 2))
        assertEquals(SwapResult.MOVED_UP to listOf(AppMove("browser", 2, 0)), ScreenSwap.plan(null, "browser", top = 0, bottom = 2))
        assertEquals(SwapResult.NOTHING to emptyList<AppMove>(), ScreenSwap.plan(null, null, top = 0, bottom = 2))
        assertEquals(SwapResult.SAME_APP to emptyList<AppMove>(), ScreenSwap.plan("app", "app", top = 0, bottom = 2))
    }

    @Test
    fun `the visible task on the screen the app was seen on is the one that moves`() {
        val tasks = listOf(
            TaskSnapshot(30, 2, visible = false, packageName = "settings"),
            TaskSnapshot(31, 0, visible = false, packageName = "settings"),
            TaskSnapshot(32, 0, visible = true, packageName = "settings"),
        )
        assertEquals(32, ScreenSwap.pickTask(tasks, "settings", from = 0, to = 2)?.taskId)
        assertEquals(31, ScreenSwap.pickTask(tasks.dropLast(1), "settings", from = 0, to = 2)?.taskId)
        assertNull(ScreenSwap.pickTask(tasks, "missing", from = 0, to = 2))
    }

    @Test
    fun `slider positions snap to 5 percent steps and volume to its index`() {
        assertEquals(0.35f, LevelSteps.snap(0.34f), 0.0001f)
        assertEquals(0.35f, LevelSteps.snap(0.36f), 0.0001f)
        assertEquals(1f, LevelSteps.snap(1.2f), 0.0001f)
        assertEquals(0f, LevelSteps.snap(-0.1f), 0.0001f)
        assertEquals(8, LevelSteps.volumeIndex(0.5f, 15))
        assertEquals(15, LevelSteps.volumeIndex(1f, 15))
        assertEquals(0, LevelSteps.volumeIndex(0f, 15))
    }

    @Test
    fun `brightness steps evenly and stays in range`() {
        assertEquals(0.36f, BrightnessStep.next(0.25f, up = true), 0.001f)
        assertEquals(1f, BrightnessStep.next(0.98f, up = true), 0.001f)
        assertTrue(BrightnessStep.next(0.001f, up = false) > 0f)
        assertEquals(50, BrightnessStep.percent(0.25f))
        assertEquals(0.5f, BrightnessStep.toPerceived(0.25f), 0.001f)
        assertEquals(0.25f, BrightnessStep.toLinear(0.5f), 0.001f)
        assertTrue(BrightnessStep.toLinear(0f) > 0f)
    }
}
