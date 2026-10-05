package io.github.thatonecodingperson.thortools.input

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundTasksTest {
    private val launcher = "com.android.launcher3"
    private val thorTools = "io.github.thatonecodingperson.thortools"

    private fun task(id: Int, pkg: String?, visible: Boolean = false, display: Int = 0) = TaskSnapshot(id, display, visible, pkg)

    @Test
    fun `apps that aren't on a screen close, the ones on either screen stay`() {
        val game = task(1, "org.example.game", visible = true, display = 0)
        val guide = task(2, "org.example.browser", visible = true, display = 4)
        val music = task(3, "org.example.music")
        val shop = task(4, "org.example.shop")
        assertEquals(listOf(music, shop), BackgroundTasks.toClose(listOf(game, guide, music, shop), keep = emptySet()))
    }

    @Test
    fun `kept packages stay even in the background`() {
        val tasks = listOf(task(1, launcher), task(2, thorTools), task(3, "org.example.music"))
        assertEquals(listOf(task(3, "org.example.music")), BackgroundTasks.toClose(tasks, keep = setOf(launcher, thorTools)))
    }

    @Test
    fun `another task of an app on a screen stays when the app is kept`() {
        val tasks = listOf(task(1, "org.example.game", visible = true), task(2, "org.example.game"))
        assertEquals(emptyList<TaskSnapshot>(), BackgroundTasks.toClose(tasks, keep = setOf("org.example.game")))
    }

    @Test
    fun `a task listed twice (running and in Recent apps) closes once, one without a package never`() {
        val music = task(3, "org.example.music")
        assertEquals(listOf(music), BackgroundTasks.toClose(listOf(music, music, task(5, null)), keep = emptySet()))
    }

    @Test
    fun `the keep list travels as one helper argument`() {
        val keep = setOf(thorTools, launcher, "com.android.systemui")
        assertEquals(keep, BackgroundTasks.decodeKeep(BackgroundTasks.encodeKeep(keep)))
        assertEquals("-", BackgroundTasks.encodeKeep(emptySet()))
        assertEquals(emptySet<String>(), BackgroundTasks.decodeKeep("-"))
        assertEquals(emptySet<String>(), BackgroundTasks.decodeKeep(null))
    }
}
