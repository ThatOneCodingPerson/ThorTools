package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.RomFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomCleanupTest {
    private val ps1 = "/storage/EBBF-779E/Roms/Ps1"

    private fun open(title: String, size: Long) =
        DiscGame(RomConsole.PS1, title, ps1, "$ps1/$title.bin", true, listOf(GameFile("$ps1/$title.bin", size)), null)

    private fun both(title: String, size: Long) = open(title, size).copy(compressed = RomFile("$ps1/$title.chd", size / 2, 0))

    private fun done(title: String) = DiscGame(RomConsole.PS1, title, ps1, null, false, emptyList(), RomFile("$ps1/$title.chd", 1, 0))

    private val toCompress = open("Crash", 7000)
    private val small = both("Ape Escape", 300)
    private val big = both("Tactics", 500)
    private val finished = done("Spyro")

    private fun model(games: List<DiscGame>, filter: GameFilter, ticked: Set<String> = emptySet(), runner: RunnerState = RunnerState()) =
        RomUiModel(
            shelf = ShelfState(games = mapOf(RomConsole.PS1 to games)),
            runner = runner,
            console = RomConsole.PS1,
            filter = filter,
            filterSet = true,
            sort = GameSort.SAVING,
            ticked = ticked,
        )

    @Test
    fun `games whose originals are still here have a list of their own, sorted by what cleaning them up frees`() {
        val all = listOf(toCompress, small, big, finished)
        assertEquals(listOf(big, small), model(all, GameFilter.ORIGINALS).shown)
        assertFalse(GameList.matches(small, GameFilter.ATTENTION))
        assertEquals(listOf(toCompress, big, small), model(all, GameFilter.ALL).shown.take(3))
    }

    @Test
    fun `a console page opens on games to compress, else on originals to clean up`() {
        assertEquals(GameFilter.NOT_COMPRESSED, GameList.firstFilter(listOf(toCompress, small)))
        assertEquals(GameFilter.ORIGINALS, GameList.firstFilter(listOf(small, finished)))
        assertEquals(GameFilter.ALL, GameList.firstFilter(listOf(finished)))
    }

    @Test
    fun `select all takes the games to compress first, and the games with originals when there are none`() {
        val all = listOf(toCompress, small, big, finished)
        assertEquals(listOf(toCompress), model(all, GameFilter.ALL).selectable)
        assertEquals(setOf(big, small), model(all, GameFilter.ORIGINALS).selectable.toSet())
        assertEquals(setOf(toCompress, small, big), model(all, GameFilter.ALL).tickable.toSet())
    }

    @Test
    fun `the card offers cleaning up for ticked games with originals, both bars when both kinds are ticked`() {
        val all = listOf(toCompress, small, big, finished)
        assertFalse(model(all, GameFilter.NOT_COMPRESSED).cleanFirst)
        assertTrue(model(all, GameFilter.ORIGINALS).cleanFirst)
        val one = model(all, GameFilter.ALL, ticked = setOf(small.id))
        assertTrue(one.cleanFirst)
        assertEquals(listOf(small), one.toClean)
        val mixed = model(all, GameFilter.ALL, ticked = setOf(small.id, toCompress.id))
        assertFalse(mixed.cleanFirst)
        assertEquals(listOf(toCompress), mixed.toCompress)
        assertEquals(listOf(small), mixed.toClean)
    }

    @Test
    fun `a game whose clean-up waits can't be ticked again`() {
        val job = RomJobs.originals(1, small, Originals.DELETE, RomSpeed.NORMAL, null)!!
        val state = model(listOf(small, big), GameFilter.ORIGINALS, ticked = setOf(small.id), runner = RunnerState(queue = listOf(job)))
        assertEquals(listOf(big), state.tickable)
        assertTrue(state.toClean.isEmpty())
        assertEquals(1, state.place(small))
        assertNull(RomJobs.originals(2, small, Originals.KEEP, RomSpeed.NORMAL, null))
    }

    @Test
    fun `a clean-up's time left comes from how fast its check goes`() {
        val job = RomJobs.originals(1, both("A", 800_000_000), Originals.DELETE, RomSpeed.NORMAL, null)!!
        val running = RunningJob(job, "verify", ChdProgress(ChdProgress.Phase.VERIFYING, 40.0), started = 0L)
        // 40 % of 400 MB checked in 10 s: 16 MB/s.
        assertEquals(16_000_000.0, QueueTime.currentCheckRate(running, 10_000L)!!, 1.0)
        assertNull(QueueTime.currentRate(running, 10_000L))
        assertEquals(15L to 40L, QueueTime.left(RunnerState(current = running, queue = listOf(job)), 10_000L))
        val extract = RomJobs.extract(2, done("B"), Originals.KEEP, RomSpeed.NORMAL, null)!!
        assertEquals(15L to null, QueueTime.left(RunnerState(current = running, queue = listOf(extract)), 10_000L))
        assertFalse(QueueTime.canTell(RunnerState(current = RunningJob(extract))))
        assertTrue(QueueTime.canTell(RunnerState(current = running)))
    }
}
