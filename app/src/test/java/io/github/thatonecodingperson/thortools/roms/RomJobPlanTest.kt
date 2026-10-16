package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.RomFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomJobPlanTest {
    private val folder = "/storage/EBBF-779E/Roms/Ps1"
    private val chdman = "/data/app/~~x/io.github.thatonecodingperson.thortools-y/lib/arm64/libchdman.so"
    private val work = "/data/user/0/io.github.thatonecodingperson.thortools/cache/roms-jobs/7"

    private fun game(title: String, lone: Boolean = false, sbi: String? = null, playlists: List<String> = emptyList()): DiscGame {
        val main = if (lone) "$folder/$title.bin" else "$folder/$title.cue"
        val files = if (lone) {
            listOf(
                GameFile(main, 700_000_000),
            )
        } else {
            listOf(GameFile(main, 200), GameFile("$folder/$title (Track 1).bin", 600_000_000))
        }
        return DiscGame(RomConsole.PS1, title, folder, main, lone, files, null, sbi = sbi, playlists = playlists)
    }

    private val estimate = Estimate(300_000_000, 400_000_000, measured = true)

    @Test
    fun `a compress job writes the CHD beside the game and needs the estimate's top plus room`() {
        val job = RomJobs.compress(
            7,
            game("Tom Clancy's Run (USA) & More"),
            Originals.KEEP,
            RomSpeed.NORMAL,
            estimate,
            "/storage/EBBF-779E/Roms",
        )!!
        assertEquals("$folder/Tom Clancy's Run (USA) & More.chd", job.output)
        assertEquals(400_000_000 + RomJobPlan.MARGIN, job.need)
        assertEquals(350_000_000, job.estimate)
        assertEquals("/storage/EBBF-779E/Originals (Thor Tools)/Ps1", job.moveTo)
        assertEquals(2, job.originals.size)
    }

    @Test
    fun `the compress script quotes every name, checks room, verifies before the file gets its name and keeps the originals`() {
        val job = RomJobs.compress(7, game("Tom Clancy's Run (USA) & More"), Originals.KEEP, RomSpeed.NORMAL, estimate, null)!!
        val script = RomJobPlan.script(job, chdman, work)
        val out = "'$folder/Tom Clancy'\\''s Run (USA) & More.chd'"
        val part = "'$folder/Tom Clancy'\\''s Run (USA) & More.chd.part'"
        assertTrue(script.contains("[ -e $out ] && finish exists"))
        assertTrue(script.contains("df -k '$folder'"))
        assertTrue(script.contains("createcd -i '$folder/Tom Clancy'\\''s Run (USA) & More.cue' -o $part -np 6 -f"))
        assertTrue(script.contains("verify -i $part"))
        assertTrue(script.indexOf("verify -i $part") < script.indexOf("mv $part $out"))
        assertTrue(script.contains("nice -n 10"))
        assertFalse(script.contains("rm -f '$folder/Tom Clancy'\\''s Run (USA) & More.cue'"))
        assertFalse(script.contains("/dev/cpuset/background"))
        assertTrue(script.trimEnd().endsWith("finish \"ok \$size\""))
    }

    @Test
    fun `delete removes only the game's own files after the check, move puts them outside the ROM folders`() {
        val delete = RomJobPlan.script(
            RomJobs.compress(7, game("Racer"), Originals.DELETE, RomSpeed.NORMAL, estimate, null)!!,
            chdman,
            work,
        )
        assertTrue(delete.contains("rm -f '$folder/Racer.cue'"))
        assertTrue(delete.contains("rm -f '$folder/Racer (Track 1).bin'"))
        assertTrue(delete.indexOf("mv '$folder/Racer.chd.part' '$folder/Racer.chd'") < delete.indexOf("rm -f '$folder/Racer.cue'"))
        val move = RomJobPlan.script(
            RomJobs.compress(7, game("Racer"), Originals.MOVE, RomSpeed.GENTLE, estimate, "/storage/EBBF-779E/Roms")!!,
            chdman,
            work,
        )
        assertTrue(move.contains("mkdir -p '/storage/EBBF-779E/Originals (Thor Tools)/Ps1'"))
        assertTrue(move.contains("[ -e '/storage/EBBF-779E/Originals (Thor Tools)/Ps1/Racer.cue' ] || mv '$folder/Racer.cue'"))
        assertTrue(move.contains("echo \$\$ > /dev/cpuset/background/tasks"))
        assertTrue(move.contains("-np 3"))
    }

    @Test
    fun `a lone image is compressed through the made-up sheet beside a link to it`() {
        val script = RomJobPlan.script(
            RomJobs.compress(7, game("Crash", lone = true), Originals.KEEP, RomSpeed.FAST, estimate, null)!!,
            chdman,
            work,
        )
        assertTrue(script.contains("ln -sf '$folder/Crash.bin' \"\$W\"/'disc.bin'"))
        assertTrue(script.contains("createcd -i \"\$W/disc.cue\""))
        assertTrue(script.contains("-np 8"))
    }

    @Test
    fun `a LibCrypt file named after the track is copied to the game's name, and playlists are rewritten`() {
        val job = RomJobs.compress(
            7,
            game("Spyro (Europe)", sbi = "$folder/Spyro (Europe) (Track 1).sbi", playlists = listOf("$folder/Spyro.m3u")),
            Originals.KEEP,
            RomSpeed.NORMAL,
            estimate,
            null,
        )!!
        assertEquals("$folder/Spyro (Europe).sbi", job.sbiTo)
        val script = RomJobPlan.script(job, chdman, work)
        assertTrue(
            script.contains(
                "[ -e '$folder/Spyro (Europe).sbi' ] || cp '$folder/Spyro (Europe) (Track 1).sbi' '$folder/Spyro (Europe).sbi'",
            ),
        )
        assertTrue(script.contains("cat \"\$W/m3u-0\" > '$folder/Spyro.m3u'"))
        val sameName = RomJobs.compress(
            7,
            game("Spyro (Europe)", sbi = "$folder/Spyro (Europe).sbi"),
            Originals.KEEP,
            RomSpeed.NORMAL,
            estimate,
            null,
        )!!
        assertNull(sameName.sbiFrom)
    }

    @Test
    fun `turning back and removing originals need a compressed file`() {
        val chd = RomFile("$folder/Spyro.chd", 300_000_000, 0)
        val only = DiscGame(RomConsole.PS1, "Spyro", folder, null, false, emptyList(), chd)
        val extract = RomJobs.extract(7, only, Originals.KEEP, RomSpeed.NORMAL, null)!!
        assertEquals("$folder/Spyro.cue", extract.output)
        val script = RomJobPlan.script(extract, chdman, work)
        assertTrue(script.contains("extractcd -i '$folder/Spyro.chd' -o 'Spyro.cue' -ob 'Spyro.bin' -f"))
        assertTrue(script.contains("mv '$folder/.thortools-7/Spyro.bin' '$folder'/"))
        assertNull(RomJobs.extract(7, game("Racer"), Originals.KEEP, RomSpeed.NORMAL, null))

        val both = game("Racer").copy(compressed = RomFile("$folder/Racer.chd", 1, 0))
        val originals = RomJobs.originals(7, both, Originals.DELETE, RomSpeed.NORMAL, null)!!
        val removal = RomJobPlan.script(originals, chdman, work)
        assertTrue(removal.indexOf("verify -i '$folder/Racer.chd'") < removal.indexOf("rm -f '$folder/Racer.cue'"))
        assertFalse(removal.contains("rm -f '$folder/Racer.chd'"))
        assertNull(RomJobs.originals(7, both, Originals.KEEP, RomSpeed.NORMAL, null))
    }

    @Test
    fun `a job survives being written to its folder and read back`() {
        val job = RomJobs.compress(
            7,
            game("Spyro (Europe)", sbi = "$folder/x.sbi", playlists = listOf("$folder/a.m3u", "$folder/b.m3u")),
            Originals.MOVE,
            RomSpeed.GENTLE,
            estimate,
            "/storage/EBBF-779E/Roms",
        )!!
        assertEquals(job, RomJobPlan.decode(RomJobPlan.encode(job)))
        assertNull(RomJobPlan.decode("id\tnot a number\n"))
    }

    @Test
    fun `the result line tells how a job ended`() {
        assertEquals(JobEnd.Done(123), JobEnd.parse("ok 123\n"))
        assertEquals(JobEnd.Cancelled, JobEnd.parse("cancelled"))
        assertEquals(JobEnd.Exists, JobEnd.parse("exists"))
        assertEquals(JobEnd.NoSpace(99L * 1024), JobEnd.parse("space 99k"))
        assertEquals(JobEnd.Failed("verify", 1), JobEnd.parse("verify 1"))
        assertEquals(JobEnd.Failed("compress", 143), JobEnd.parse("compress 143"))
        assertNull(JobEnd.parse(""))
    }

    @Test
    fun `before each chdman run the script finds chdman again, which an update moves to a new folder`() {
        assertEquals(
            "/data/app/*/io.github.thatonecodingperson.thortools-*/lib/arm64/libchdman.so",
            RomJobPlan.installedPattern(chdman),
        )
        assertNull(RomJobPlan.installedPattern("/x/libchdman.so"))
        val script = RomJobPlan.script(RomJobs.compress(7, game("Racer"), Originals.KEEP, RomSpeed.NORMAL, estimate, null)!!, chdman, work)
        assertTrue(script.contains("for f in /data/app/*/io.github.thatonecodingperson.thortools-*/lib/arm64/libchdman.so; do"))
        assertTrue(script.contains("C=\$(cat \"\$W/engine\")"))
        val engine = "engine || { rm -f '$folder/Racer.chd.part'; finish \"engine 1\"; }"
        assertTrue(script.indexOf(engine) < script.indexOf("createcd"))
        assertTrue(script.indexOf(engine, script.indexOf("createcd")) < script.indexOf("verify -i"))
        assertEquals(JobEnd.Failed("engine", 1), JobEnd.parse("engine 1"))
    }

    @Test
    fun `moved originals keep their folders under the storage's top`() {
        assertEquals(
            "/storage/emulated/0/Originals (Thor Tools)/psx/Game",
            RomJobs.keptFolder("/storage/emulated/0/ROMs/psx/Game", "/storage/emulated/0/ROMs"),
        )
        assertEquals("/storage/EBBF-779E/Originals (Thor Tools)/Ps1", RomJobs.keptFolder("/storage/EBBF-779E/Roms/Ps1", null))
    }
}
