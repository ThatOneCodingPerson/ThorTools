package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.CheatSystem
import io.github.thatonecodingperson.thortools.retroarch.CheatSystems
import io.github.thatonecodingperson.thortools.retroarch.RomFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomPs2Test {
    private val ps2 = "/storage/EBBF-779E/Roms/PS2"
    private val chdman = "/data/app/~~a==/io.github.thatonecodingperson.thortools-b==/lib/arm64/libchdman.so"
    private val maxcso = "/data/app/~~a==/io.github.thatonecodingperson.thortools-b==/lib/arm64/libmaxcso.so"
    private val work = "/data/user/0/io.github.thatonecodingperson.thortools/cache/roms-jobs/9"

    /** 4,660,396,032 bytes: Tony Hawk's Underground, over 2^31. */
    private val big = 2048L * 2_275_584

    private fun file(path: String, size: Long) = RomFile(path, size, 0L)

    /** Shaped like a real PS2 folder, with the other kinds of files a PS2 folder may hold. */
    private val files = listOf(
        file("$ps2/Katamari Damacy (USA).iso", 2048L * 700_000),
        file("$ps2/Tony Hawk's Underground (USA).iso", big),
        file("$ps2/Dynasty Warriors 2.bin", 2352L * 300_000),
        file("$ps2/SCPH-70004.BIN", 4L * 1024 * 1024),
        file("$ps2/Okami (USA).iso", 2048L * 1_000_000),
        file("$ps2/Okami (USA).chd", 1_500_000_000),
        file("$ps2/Kingdom Hearts (USA).cso", 1_200_000_000),
        file("$ps2/Shadow of the Colossus (USA).zso", 1_900_000_000),
        file("$ps2/Ico (USA).iso.gz", 400_000_000),
        file("$ps2/Broken.iso", 2048L * 1000 + 7),
        file("/storage/EBBF-779E/Roms/PSP/Daxter (USA).cso", 500_000_000),
    )
    private val games = RomSets.games(RomConsole.PS2, files, emptyMap()).associateBy { it.title }

    private val armsx2 = listOf(InstalledApp("com.armsx2", "2.8.3"))

    @Test
    fun `DVD images and a raw CD image are games, the BIOS isn't, CSO ZSO and GZ games are listed for what they are`() {
        val katamari = games.getValue("Katamari Damacy (USA)")
        assertEquals(DiscKind.DVD, katamari.kind)
        assertTrue(katamari.canCompress)
        assertEquals(RomConsole.PS2.formatsFor(DiscKind.DVD).toSet(), katamari.formats.toSet())
        assertEquals(
            setOf(RomFormat.CHD_DVD_ZSTD, RomFormat.CHD_DVD, RomFormat.CHD_DVD_ZLIB, RomFormat.CSO, RomFormat.ZSO, RomFormat.ISO),
            katamari.formats.toSet(),
        )
        assertEquals(RomFormat.CHD_DVD_ZSTD, katamari.defaultFormat)
        assertEquals(big, games.getValue("Tony Hawk's Underground (USA)").originalSize)

        val dynasty = games.getValue("Dynasty Warriors 2")
        assertEquals(DiscKind.CD, dynasty.kind)
        assertTrue(dynasty.lone)
        assertEquals(listOf(RomFormat.CHD_CD), dynasty.formats)
        assertNull(games["SCPH-70004"])

        assertTrue(games.getValue("Okami (USA)").originalsLeft)
        val kingdom = games.getValue("Kingdom Hearts (USA)")
        assertEquals(RomFormat.CSO, kingdom.packed)
        assertEquals(DiscKind.DVD, kingdom.kind)
        assertEquals(RomFormat.ZSO, games.getValue("Shadow of the Colossus (USA)").packed)
        assertEquals(GameProblem.NotHandled("gz"), games.getValue("Ico (USA)").problem)
        assertEquals(GameProblem.OddSize, games.getValue("Broken").problem)
        assertNull(games["Daxter (USA)"])
    }

    @Test
    fun `a CSO goes by its folder, a loose one stays PSP, and Sony - PlayStation 2 is a PS2 folder`() {
        assertEquals(CheatSystem.PS2, CheatSystems.of("$ps2/Kingdom Hearts (USA).cso"))
        assertEquals(CheatSystem.PSP, CheatSystems.of("/storage/EBBF-779E/Roms/PSP/Daxter (USA).cso"))
        assertEquals(CheatSystem.PSP, CheatSystems.of("/storage/emulated/0/Download/Daxter (USA).cso"))
        assertEquals(CheatSystem.PS2, CheatSystems.of("/storage/emulated/0/ROMs/Sony - PlayStation 2/Ico (USA).zso"))
    }

    @Test
    fun `the default follows the installed emulators and old versions say what they need`() {
        val console = RomConsole.PS2
        assertEquals(RomFormat.CHD_DVD_ZSTD, EmulatorChecks.defaultFormat(console, DiscKind.DVD, armsx2, null))
        val nether = listOf(InstalledApp("xyz.aethersx2.android", "2.1"))
        assertEquals(RomFormat.CHD_DVD_ZLIB, EmulatorChecks.defaultFormat(console, DiscKind.DVD, nether, null))
        assertEquals(RomFormat.CHD_DVD_ZSTD, EmulatorChecks.defaultFormat(console, DiscKind.DVD, emptyList(), null))
        assertEquals(RomFormat.CHD_CD, EmulatorChecks.defaultFormat(console, DiscKind.CD, armsx2, null))

        val play = EmulatorChecks.of(console, RomFormat.CHD_DVD_ZSTD, listOf(InstalledApp("com.virtualapplications.play", "0.70")), null)
        assertEquals("0.71", play.single().needs)
        assertEquals(false, play.single().opens)
        val oldArmsx2 = EmulatorChecks.of(console, RomFormat.CSO, listOf(InstalledApp("come.nanodata.armsx2", "1.0.2")), null)
        assertEquals("1.0.3", oldArmsx2.single().needs)
        assertEquals(false, EmulatorChecks.of(console, RomFormat.ZSO, nether, null).single().opens)
        val cores = EmulatorChecks.of(console, RomFormat.ZSO, emptyList(), setOf("armsx2_libretro", "play_libretro"))
        assertEquals(listOf(true, false), cores.map { it.opens })

        val esde = listOf(InstalledApp("org.es_de.frontend", "3.4"))
        assertEquals(listOf("ES-DE"), EmulatorChecks.frontEndsMissing(console, RomFormat.ZSO, esde))
        assertTrue(EmulatorChecks.frontEndsMissing(console, RomFormat.CHD_DVD_ZSTD, esde).isEmpty())
        assertTrue("org.es_de.frontend" in EmulatorChecks.knownPackages)
        val cocoon = listOf(InstalledApp("rip.moth.cocoonshell", "3.07"))
        assertEquals(listOf("Cocoon"), EmulatorChecks.frontEndsMissing(console, RomFormat.ZSO, cocoon))
        assertTrue(EmulatorChecks.frontEndsMissing(console, RomFormat.CSO, cocoon).isEmpty())
    }

    @Test
    fun `empty files say so on a disc console, and an empty CHD never stands for a game's compressed file`() {
        val found = RomSets.games(
            RomConsole.PS2,
            listOf(
                file("$ps2/Blank.iso", 0),
                file("$ps2/Lost.cso", 0),
                file("$ps2/Katamari Damacy (USA).iso", 2048L * 700_000),
                file("$ps2/Katamari Damacy (USA).chd", 0),
            ),
            emptyMap(),
        )
        assertEquals(GameProblem.Empty, found.single { it.title == "Blank" }.problem)
        assertEquals(GameProblem.Empty, found.single { it.title == "Lost" }.problem)
        val katamari = found.filter { it.title == "Katamari Damacy (USA)" }
        assertEquals(2, katamari.size)
        assertTrue(katamari.any { it.canCompress && it.compressed == null })
        assertEquals(GameProblem.Empty, katamari.single { it.compressed != null }.problem)

        val psx = "/storage/EBBF-779E/Roms/PS1"
        val cue = file("$psx/Crash (USA).cue", 80)
        val sheet = "FILE \"Crash (USA).bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n"
        val crash = RomSets.games(RomConsole.PS1, listOf(cue, file("$psx/Crash (USA).bin", 0)), mapOf(cue.path to sheet)).single()
        assertEquals(GameProblem.Empty, crash.problem)
    }

    @Test
    fun `games with the same name show where each one is`() {
        val n3ds = "/storage/EBBF-779E/Roms/3DS"
        val found = RomSets.games(
            RomConsole.N3DS,
            listOf(
                file("$n3ds/Tomodachi Life (USA) (Rev 1).cci", 536_870_912),
                file("$n3ds/Tomodachi Life (USA) (Rev 1)/Tomodachi Life (USA) (Rev 1).cci", 536_870_912),
                file("$n3ds/Super Mario 3D Land (USA).cci", 536_870_912),
                file("/storage/emulated/0/Roms/3DS/Super Mario 3D Land (USA).cci", 536_870_912),
                file("$n3ds/Pilotwings Resort (USA).cci", 268_435_456),
            ),
            emptyMap(),
        )
        val places = GameList.places(found, "Internal storage", "SD card")
        assertEquals(
            mapOf(
                "$n3ds/Tomodachi Life (USA) (Rev 1).cci" to "3DS",
                "$n3ds/Tomodachi Life (USA) (Rev 1)/Tomodachi Life (USA) (Rev 1).cci" to "Tomodachi Life (USA) (Rev 1)",
                "$n3ds/Super Mario 3D Land (USA).cci" to "SD card/Roms/3DS",
                "/storage/emulated/0/Roms/3DS/Super Mario 3D Land (USA).cci" to "Internal storage/Roms/3DS",
            ),
            places,
        )
    }

    private fun job(title: String, format: RomFormat?, choice: Originals = Originals.KEEP) =
        RomJobs.compress(9, games.getValue(title), choice, RomSpeed.NORMAL, Estimate(1_000, 2_000_000_000, true), null, format = format)!!

    @Test
    fun `a DVD image goes to createdvd with its format's options, after the disc check, then chdman's own check`() {
        val iso = "'$ps2/Katamari Damacy (USA).iso'"
        val part = "'$ps2/Katamari Damacy (USA).chd.part'"
        val light = RomJobPlan.script(job("Katamari Damacy (USA)", null), chdman, work, maxcso)
        assertTrue(light.contains("createdvd -i $iso -o $part -c zstd -hs 16384 -np 6 -f"))
        assertTrue(light.indexOf("= CD001 ] || finish notdisc") < light.indexOf("createdvd"))
        assertTrue(light.contains("dd if=$iso bs=2048 skip=16 count=1"))
        assertTrue(light.contains("verify -i $part"))
        assertFalse(light.contains("disc.cue"))
        val smallest = RomJobPlan.script(job("Katamari Damacy (USA)", RomFormat.CHD_DVD), chdman, work)
        assertTrue(smallest.contains("createdvd -i $iso -o $part -np 6 -f"))
        assertTrue(RomJobPlan.script(job("Katamari Damacy (USA)", RomFormat.CHD_DVD_ZLIB), chdman, work).contains("-c zlib -np 6"))
        assertNull(
            RomJobs.compress(
                9,
                games.getValue("Katamari Damacy (USA)"),
                Originals.KEEP,
                RomSpeed.NORMAL,
                Estimate(1, 2, true),
                null,
                format = RomFormat.CHD_CD,
            ),
        )
    }

    @Test
    fun `CSO and ZSO are made by maxcso and checked by comparing CRCs with the image`() {
        val iso = "'$ps2/Katamari Damacy (USA).iso'"
        val cso = RomJobPlan.script(job("Katamari Damacy (USA)", RomFormat.CSO), chdman, work, maxcso)
        val part = "'$ps2/Katamari Damacy (USA).cso.part'"
        assertTrue(cso.contains("M='$maxcso'"))
        assertTrue(cso.contains("engine_maxcso() {"))
        assertTrue(cso.contains("nice -n 10 \"\$M\" --threads=6 $iso -o $part"))
        assertTrue(cso.contains("\"\$M\" --crc $iso"))
        assertTrue(cso.contains("\"\$M\" --crc $part"))
        assertTrue(cso.contains("[ -n \"\$crc1\" ] && [ \"\$crc1\" = \"\$crc2\" ] || { rm -f $part; finish \"verify 1\"; }"))
        assertTrue(cso.contains("grep -o 'CRC32: [0-9a-f]*'"))
        assertFalse(cso.contains("createdvd"))
        val zso = RomJobPlan.script(job("Katamari Damacy (USA)", RomFormat.ZSO), chdman, work, maxcso)
        assertTrue(zso.contains("--threads=6 --format=zso $iso -o '$ps2/Katamari Damacy (USA).zso.part'"))
    }

    @Test
    fun `a raw CD image of a PS2 game becomes a CD CHD through a made-up sheet`() {
        val script = RomJobPlan.script(job("Dynasty Warriors 2", null), chdman, work)
        assertTrue(script.contains("createcd -i \"\$W/disc.cue\""))
        assertFalse(script.contains("CD001"))
    }

    @Test
    fun `turning back reads the CHD's kind, and a CSO is decompressed by maxcso`() {
        val okamiChd = DiscGame(RomConsole.PS2, "Okami (USA)", ps2, null, false, emptyList(), file("$ps2/Okami (USA).chd", 1), kind = null)
        val chdJob = RomJobs.extract(9, okamiChd, Originals.KEEP, RomSpeed.NORMAL, null)!!
        assertEquals("$ps2/Okami (USA).iso", chdJob.output)
        val script = RomJobPlan.script(chdJob, chdman, work, maxcso)
        assertTrue(script.contains("if grep -q \"Tag='DVD '\" \"\$W/info.out\"; then"))
        assertTrue(script.indexOf("extractdvd -i '$ps2/Okami (USA).chd' -o 'Okami (USA).iso' -f") < script.indexOf("fi\n"))
        assertTrue(script.indexOf("extractcd -i") > script.indexOf("fi\n"))
        assertTrue(script.contains("logical=\${logical%???}"))

        val kingdom = games.getValue("Kingdom Hearts (USA)")
        val csoJob = RomJobs.extract(9, kingdom, Originals.KEEP, RomSpeed.NORMAL, null)!!
        assertEquals(RomFormat.CSO, csoJob.format)
        val back = RomJobPlan.script(csoJob, chdman, work, maxcso)
        assertTrue(back.contains("\"\$M\" --decompress '$ps2/Kingdom Hearts (USA).cso' -o 'Kingdom Hearts (USA).iso'"))
        assertTrue(back.contains("od -An -t u8"))
        assertFalse(back.contains("extractdvd"))

        val ps1Chd = DiscGame(RomConsole.PS1, "Spyro", "/x", null, false, emptyList(), file("/x/Spyro.chd", 1))
        assertFalse(
            RomJobPlan.script(RomJobs.extract(9, ps1Chd, Originals.KEEP, RomSpeed.NORMAL, null)!!, chdman, work).contains("extractdvd"),
        )
    }

    @Test
    fun `cleaning up after a CSO compares it with its image, after a CHD runs chdman's check`() {
        val both = games.getValue(
            "Katamari Damacy (USA)",
        ).copy(compressed = file("$ps2/Katamari Damacy (USA).cso", 1), packed = RomFormat.CSO)
        val script = RomJobPlan.script(RomJobs.originals(9, both, Originals.DELETE, RomSpeed.NORMAL, null)!!, chdman, work, maxcso)
        assertTrue(script.contains("--crc '$ps2/Katamari Damacy (USA).iso'"))
        assertTrue(script.indexOf("crc2=") < script.indexOf("rm -f '$ps2/Katamari Damacy (USA).iso'"))
        val okami = games.getValue("Okami (USA)")
        assertTrue(
            RomJobPlan.script(RomJobs.originals(9, okami, Originals.DELETE, RomSpeed.NORMAL, null)!!, chdman, work).contains("verify -i"),
        )
    }

    @Test
    fun `a job keeps its format in its file, and a job written before formats existed is a CD CHD`() {
        val cso = job("Katamari Damacy (USA)", RomFormat.CSO)
        assertEquals(cso, RomJobPlan.decode(RomJobPlan.encode(cso)))
        val old = RomJobPlan.encode(job("Dynasty Warriors 2", null)).lines().filterNot { it.startsWith("format\t") }.joinToString("\n")
        assertEquals(RomFormat.CHD_CD, RomJobPlan.decode(old)!!.format)
    }

    @Test
    fun `sizes over two billion bytes stay in Kotlin, the shell only sees KB`() {
        val thug = RomJobs.compress(
            9,
            games.getValue("Tony Hawk's Underground (USA)"),
            Originals.KEEP,
            RomSpeed.NORMAL,
            Estimate(3_000_000_000, 3_700_000_000, true),
            null,
        )!!
        val script = RomJobPlan.script(thug, chdman, work)
        assertTrue(script.contains("[ \"\$free\" -ge ${(3_700_000_000 + RomJobPlan.MARGIN + 1023) / 1024} ]"))
        assertFalse(script.contains(" * "))
        assertEquals(big, thug.inputBytes)
    }

    @Test
    fun `maxcso's progress and final lines are read like chdman's`() {
        assertEquals(
            67.0,
            ChdTool.progress("test.iso:  14%, ratio=101%, speed= 6.36 MB/s\rtest.iso:  67%, ratio= 51%, speed=18.29 MB/s\r")!!.percent,
            0.0,
        )
        val done = ChdTool.progress("test.iso:  99%, ratio= 40%, speed=20.00 MB/s\rtest.iso: 24576000 -> 8520028 bytes (35%)\n")!!
        assertEquals(ChdProgress.Phase.DONE, done.phase)
        assertEquals(35.0, done.ratio!!, 0.0)
        assertEquals(12.5, ChdTool.progress("Compressing, 12.5% complete... (ratio=40.0%)\r")!!.percent, 0.0)
    }

    @Test
    fun `a DVD image is estimated in its format's blocks`() {
        assertEquals(16 * 1024, ShrinkEstimate.blockSize(RomFormat.CHD_DVD_ZSTD, 1))
        assertEquals(4096, ShrinkEstimate.blockSize(RomFormat.CHD_DVD, big))
        assertEquals(2048, ShrinkEstimate.blockSize(RomFormat.CSO, 1_000_000_000))
        assertEquals(16 * 1024, ShrinkEstimate.blockSize(RomFormat.ZSO, big))
        val zeros = ByteArray(64 * 1024)
        val noise = ByteArray(64 * 1024).also { java.util.Random(7).nextBytes(it) }
        assertTrue(ShrinkEstimate.blockRatio(listOf(zeros), 2048)!! < 0.05)
        assertEquals(1.0, ShrinkEstimate.blockRatio(listOf(noise), 2048)!!, 0.0)
        val estimate = ShrinkEstimate.image(big, RomFormat.CHD_DVD, 0.5)
        assertTrue(estimate.low < big / 2 && estimate.high <= big)
        assertTrue(ShrinkEstimate.image(big, RomFormat.ZSO, 0.5).middle > estimate.middle)
        assertEquals(big, ShrinkEstimate.image(big, RomFormat.ZSO, 1.0).high)

        val start = ByteArray(64 * 1024)
        "CD001".toByteArray().copyInto(start, 16 * 2048 + 1)
        assertTrue(ShrinkEstimate.holdsDisc(start))
        assertFalse(ShrinkEstimate.holdsDisc(ByteArray(64 * 1024)))
        assertFalse(ShrinkEstimate.holdsDisc(null))
    }

    @Test
    fun `a run that won't fit says how many do, and deleting the originals as it goes makes them fit`() {
        val gb = 1_000_000_000L
        val iso = DiscGame(RomConsole.PS2, "A", ps2, "$ps2/A.iso", false, listOf(GameFile("$ps2/A.iso", 4 * gb)), null, kind = DiscKind.DVD)
        val jobs = (1..27).map { n ->
            RomJobs.compress(
                n.toLong(),
                iso.copy(title = "G$n", main = "$ps2/G$n.iso"),
                Originals.KEEP,
                RomSpeed.NORMAL,
                Estimate(
                    2 * gb,
                    3 * gb,
                    true,
                ),
                null,
            )!!
        }
        val free = mapOf("/storage/EBBF-779E" to 33 * gb)
        val keep = RomSpace.plan(jobs, emptyList(), free)
        assertEquals(10, keep.fit)
        assertFalse(keep.fitsAll)
        assertEquals(33 * gb, keep.freeBytes)
        val delete = RomSpace.plan(jobs.map { it.copy(choice = Originals.DELETE) }, emptyList(), free)
        assertTrue(delete.fitsAll)
        assertEquals(9, RomSpace.plan(jobs, jobs.take(1), free).fit)
        assertEquals(0, RomSpace.plan(jobs, emptyList(), mapOf("/storage/EBBF-779E" to gb)).fit)
        assertTrue(RomSpace.plan(jobs, emptyList(), emptyMap()).fitsAll)
        assertEquals(setOf("/storage/EBBF-779E"), RomSpace.volumes(jobs))
    }

    @Test
    fun `estimates and history keep their format, older lines still load`() {
        val katamari = games.getValue("Katamari Damacy (USA)")
        assertEquals("$ps2/Katamari Damacy (USA).iso|${2048L * 700_000}|CSO", RomShelf.key(katamari, RomFormat.CSO))
        val dynasty = games.getValue("Dynasty Warriors 2")
        assertEquals("$ps2/Dynasty Warriors 2.bin|${2352L * 300_000}", RomShelf.key(dynasty))
        val entry = HistoryEntry(1, "ps2", "Katamari", JobKind.COMPRESS, 10, 5, 4, "CSO")
        assertEquals(entry, RomShelf.decodeHistory(RomShelf.encodeHistory(entry).trimEnd()))
        assertNull(RomShelf.decodeHistory("1\tps1\tSpyro\tCOMPRESS\t10\t5\t4")!!.format)
    }
}
