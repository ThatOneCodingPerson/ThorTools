package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.RomFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomQueueTest {
    private val ps1 = "/storage/EBBF-779E/Roms/Ps1"
    private val disc = 2352L * 200_000

    private fun file(path: String, size: Long = disc) = RomFile(path, size, 0L)

    /** Castlevania and Theme Park, as found on a Thor. */
    private val files = listOf(
        file("$ps1/Castlevania - Symphony of the Night (USA) (Track 1).bin"),
        file("$ps1/Castlevania - Symphony of the Night (USA) (Track 2).bin", 2352L * 19_000),
        file("$ps1/Castlevania - Symphony of the Night (USA)/Castlevania - Symphony of the Night (USA).cue", 282),
        file("$ps1/Theme Park.cue", 2292),
        file("$ps1/Theme Park (Track 01).bin"),
        file("$ps1/Theme Park/Theme Park.cue", 2292),
        file("$ps1/Theme Park/Theme Park (Track 01).bin"),
        file("$ps1/Theme Park/Theme Park (Track 02).bin", 2352L * 1000),
        file("$ps1/Theme Park/Theme Park (Track 03).bin", 2352L * 1000),
    )
    private val castleSheet = "FILE \"Castlevania - Symphony of the Night (USA) (Track 1).bin\" BINARY\n  TRACK 01 MODE2/2352\n" +
        "FILE \"Castlevania - Symphony of the Night (USA) (Track 2).bin\" BINARY\n  TRACK 02 AUDIO\n"
    private val parkSheet = (1..3).joinToString("") { n ->
        "FILE \"Theme Park (Track 0$n).bin\" BINARY\n  TRACK 0$n ${if (n == 1) "MODE2/2352" else "AUDIO"}\n"
    }
    private val texts = mapOf(
        "$ps1/Castlevania - Symphony of the Night (USA)/Castlevania - Symphony of the Night (USA).cue" to castleSheet,
        "$ps1/Theme Park.cue" to parkSheet,
        "$ps1/Theme Park/Theme Park.cue" to parkSheet,
    )
    private val games = RomSets.games(RomConsole.PS1, files, texts)

    @Test
    fun `a sheet in a folder of its own takes its tracks from one folder up, and they aren't games of their own`() {
        val castle = games.single { it.title.startsWith("Castlevania") }
        assertTrue(castle.elsewhere)
        assertEquals(ps1, castle.folder)
        assertTrue(castle.canCompress)
        assertEquals(3, castle.files.size)
        assertEquals("$ps1/Castlevania - Symphony of the Night (USA).chd", castle.output(RomFormat.CHD_CD))
    }

    @Test
    fun `an incomplete copy says how many tracks it misses and where the complete one is`() {
        val parks = games.filter { it.title == "Theme Park" }
        assertEquals(2, parks.size)
        val broken = parks.single { it.folder == ps1 }
        assertEquals(
            GameProblem.MissingTracks(listOf("Theme Park (Track 02).bin", "Theme Park (Track 03).bin"), 3, "$ps1/Theme Park"),
            broken.problem,
        )
        assertTrue(parks.single { it.folder == "$ps1/Theme Park" }.canCompress)
    }

    @Test
    fun `the job for such a sheet copies it with plain names beside links to the tracks, and leaves no empty folder`() {
        val castle = games.single { it.title.startsWith("Castlevania") }
        assertNull(RomJobs.compress(1, castle, Originals.KEEP, RomSpeed.NORMAL, Estimate(1, 2, true), null))
        val job = RomJobs.compress(1, castle, Originals.DELETE, RomSpeed.NORMAL, Estimate(1, 2, true), null, sheetText = castleSheet)!!
        assertEquals(CueSheet.withPlainNames(castleSheet), job.sheet)
        assertEquals(2, job.links.size)
        assertEquals("$ps1/Castlevania - Symphony of the Night (USA)", job.emptyFolder)
        val script = RomJobPlan.script(job, "/x/libchdman.so", "/w")
        assertTrue(
            script.contains(
                "ln -sf '$ps1/Castlevania - Symphony of the Night (USA) (Track 2).bin' \"\$W\"/'Castlevania - Symphony of the Night (USA) (Track 2).bin'",
            ),
        )
        assertTrue(script.contains("createcd -i \"\$W/disc.cue\""))
        assertTrue(script.contains("rmdir '$ps1/Castlevania - Symphony of the Night (USA)' 2>/dev/null"))
        assertEquals(job, RomJobPlan.decode(RomJobPlan.encode(job)))
    }

    @Test
    fun `free space is counted in KB, never multiplied in the shell, which wraps round past two billion`() {
        val park = games.single { it.folder == "$ps1/Theme Park" }
        val job = RomJobs.compress(1, park, Originals.KEEP, RomSpeed.NORMAL, Estimate(1, 600L * 1024 * 1024, true), null)!!
        val script = RomJobPlan.script(job, "/c", "/w")
        assertTrue(script.contains("set -- \$(df -k '$ps1/Theme Park' 2>/dev/null | tail -n 1)"))
        assertTrue(script.contains("[ \"\$free\" -ge ${(job.need + 1023) / 1024} ]"))
        assertFalse(script.contains(" * "))
        assertEquals(JobEnd.NoSpace(31508864L * 1024), JobEnd.parse("space 31508864k"))
        val chd = DiscGame(RomConsole.PS1, "X", ps1, null, false, emptyList(), RomFile("$ps1/X.chd", 1, 0))
        val extract = RomJobPlan.script(RomJobs.extract(2, chd, Originals.KEEP, RomSpeed.NORMAL, null)!!, "/c", "/w")
        assertTrue(extract.contains("logical=\${logical%???}"))
    }

    @Test
    fun `a sheet's file lines lose their folders`() {
        assertEquals(
            "FILE \"a.bin\" BINARY\n  TRACK 01 AUDIO\nFILE \"b.wav\" WAVE",
            CueSheet.withPlainNames("FILE \"x\\a.bin\" BINARY\n  TRACK 01 AUDIO\nFILE dir/b.wav WAVE"),
        )
    }

    @Test
    fun `a stop asked for before chdman starts ends the job at once`() {
        val castle = games.single { it.title.startsWith("Castlevania") }
        val script = RomJobPlan.script(
            RomJobs.compress(1, castle, Originals.KEEP, RomSpeed.NORMAL, Estimate(1, 2, true), null, castleSheet)!!,
            "/c",
            "/w",
        )
        assertTrue(script.indexOf("[ -e \"\$W/cancel\" ]") < script.indexOf("createcd"))
    }

    @Test
    fun `the queue survives being kept in a file`() {
        val park = games.single { it.folder == "$ps1/Theme Park" }
        val jobs = listOf(
            RomJobs.compress(1, park, Originals.KEEP, RomSpeed.NORMAL, Estimate(1, 2, true), null)!!,
            RomJobs.compress(
                2,
                games.single {
                    it.title.startsWith("Castlevania")
                },
                Originals.MOVE,
                RomSpeed.FAST,
                Estimate(3, 4, true),
                null,
                castleSheet,
            )!!,
        )
        assertEquals(jobs, RomJobPlan.decodeQueue(RomJobPlan.encodeQueue(jobs)))
        assertTrue(RomJobPlan.decodeQueue("").isEmpty())
    }

    @Test
    fun `games are shown and compressed in the chosen order, and each knows its place in line`() {
        val a = DiscGame(RomConsole.PS1, "A", ps1, "$ps1/A.bin", true, listOf(GameFile("$ps1/A.bin", 100)), null)
        val b = DiscGame(RomConsole.PS1, "B", ps1, "$ps1/B.bin", true, listOf(GameFile("$ps1/B.bin", 300)), null)
        val c = DiscGame(RomConsole.PS1, "C", ps1, null, false, emptyList(), RomFile("$ps1/C.chd", 5, 0))
        val saving = { g: DiscGame -> g.originalSize / 2 }
        assertEquals(listOf(b, a), GameList.shown(listOf(a, b, c), GameFilter.NOT_COMPRESSED, GameSort.SAVING, "", saving))
        assertEquals(listOf(a, b, c), GameList.shown(listOf(c, b, a), GameFilter.ALL, GameSort.NAME, "", saving))
        assertEquals(listOf(c), GameList.shown(listOf(a, b, c), GameFilter.COMPRESSED, GameSort.NAME, "", saving))
        assertEquals(listOf(b), GameList.shown(listOf(a, b, c), GameFilter.ALL, GameSort.NAME, "b", saving))
        val jobA = RomJobs.compress(1, a, Originals.KEEP, RomSpeed.NORMAL, Estimate(1, 2, true), null)!!
        val jobB = RomJobs.compress(2, b, Originals.KEEP, RomSpeed.NORMAL, Estimate(1, 2, true), null)!!
        val state = RunnerState(current = RunningJob(jobA), queue = listOf(jobB), done = 2)
        assertEquals(0, state.place(a.main!!))
        assertEquals(1, state.place(b.main!!))
        assertNull(state.place("$ps1/other.bin"))
        assertEquals(4, state.total)
    }

    @Test
    fun `time left comes from how fast the running game goes, then from finished ones, and leaves out paused time`() {
        val a = DiscGame(RomConsole.PS1, "A", ps1, "$ps1/A.bin", true, listOf(GameFile("$ps1/A.bin", 100_000_000)), null)
        val job = RomJobs.compress(1, a, Originals.KEEP, RomSpeed.NORMAL, Estimate(1, 2, true), null)!!
        val running = RunningJob(job, "compress", ChdProgress(ChdProgress.Phase.COMPRESSING, 50.0), started = 0L, pausedFor = 40_000L)
        // 50 MB in 60 s of work (100 s minus 40 s paused): about 0.83 MB/s.
        val rate = QueueTime.currentRate(running, 100_000L)!!
        assertEquals(50_000_000.0 / 60, rate, 1.0)
        // Half of it left to compress (60 s) plus its check (0.6 of the 120 s it takes to compress), and the next one.
        val (thisOne, all) = QueueTime.left(RunnerState(current = running, queue = listOf(job)), 100_000L)
        assertEquals(132L, thisOne)
        assertEquals(324L, all)
        val checking = running.copy(phase = "verify", progress = ChdProgress(ChdProgress.Phase.VERIFYING, 50.0))
        assertEquals(36L, QueueTime.left(RunnerState(current = checking, rate = 50_000_000.0 / 60), 100_000L).first)
        val starting = RunningJob(job, "", null, started = 0L)
        assertEquals(null to null, QueueTime.left(RunnerState(current = starting), 1_000L))
        val known = QueueTime.left(RunnerState(current = starting, rate = 1_000_000.0), 1_000L)
        assertEquals(160L, known.first)
        assertFalse(QueueTime.left(RunnerState(), 0L).first != null)
    }
}
