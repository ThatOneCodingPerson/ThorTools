package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.CheatSystem
import io.github.thatonecodingperson.thortools.retroarch.CheatSystems
import io.github.thatonecodingperson.thortools.retroarch.RomFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomNintendoTest {
    private val gc = "/storage/EBBF-779E/Roms/gamecube"
    private val wii = "/storage/EBBF-779E/Roms/Wii"
    private val chdman = "/data/app/~~a==/io.github.thatonecodingperson.thortools-b==/lib/arm64/libchdman.so"
    private val nod = "/data/app/~~a==/io.github.thatonecodingperson.thortools-b==/lib/arm64/libnodtool.so"
    private val work = "/data/user/0/io.github.thatonecodingperson.thortools/cache/roms-jobs/5"

    // The first bytes of real files, read with `od` on a Thor.
    private val crazyTaxi = "52565a010100000000030000000000dc53b2d0dc30a5760fa80b4734bb63596e3c31d06d000000005705800000000000" +
        "0594a2189690d0314f389aed64773d9861837cd5f18920c6000000010000000500000013000200004743544535310000"
    private val galaxy = "5742465300689000091501000100000000000000000000000000000000000000"

    // A GCZ in 16 KiB blocks (NKit's, old Dolphin's): magic, sub type, compressed size, disc size, block size, blocks.
    private val oldGcz = "01c00bb100000000" + "0000000000000000" + "0080055700000000" + "0040000000000000"

    private fun bytes(hex: String) = PackedHeaders.fromHex(hex)!!

    private fun file(path: String, size: Long) = RomFile(path, size, 0L)

    @Test
    fun `RVZ, WBFS, GCZ and CISO headers tell the format, the disc's size and the block`() {
        assertEquals(
            PackedHeader(RomFormat.RVZ, RomConsole.GAMECUBE_DISC, 0x20000, DiscKind.DVD),
            PackedHeaders.parse(bytes(crazyTaxi), "rvz"),
        )
        assertEquals(PackedHeader(RomFormat.WBFS, 0L, 0x200000, DiscKind.DVD), PackedHeaders.parse(bytes(galaxy), "wbfs"))
        assertEquals(PackedHeader(RomFormat.GCZ, RomConsole.GAMECUBE_DISC, 0x4000, DiscKind.DVD), PackedHeaders.parse(bytes(oldGcz), "gcz"))
        val ciso = bytes("4349534f00002000" + "00".repeat(24))
        // A GameCube or Wii CISO and PSP's CSO start alike: the extension tells them apart.
        assertEquals(PackedHeader(RomFormat.CISO, 0L, 0x200000, DiscKind.DVD), PackedHeaders.parse(ciso, "ciso"))
        assertEquals(RomFormat.CSO, PackedHeaders.parse(ciso, "cso")!!.format)
        assertEquals(RomFormat.WIA, PackedHeaders.parse(bytes("57494101" + crazyTaxi.drop(8)), "wia")!!.format)
        assertNull(PackedHeaders.parse(bytes(crazyTaxi.take(80)), "rvz"))
    }

    @Test
    fun `GameCube and Wii file types go by their folder where both consoles use them`() {
        assertEquals(CheatSystem.WII, CheatSystems.of("$wii/Game.gcz"))
        assertEquals(CheatSystem.GAMECUBE, CheatSystems.of("$gc/Game.gcz"))
        assertEquals(CheatSystem.WII, CheatSystems.of("$wii/Game.ciso"))
        assertEquals(CheatSystem.GAMECUBE, CheatSystems.of("$gc/Game.wia"))
        assertEquals(CheatSystem.WII, CheatSystems.of("$wii/Game.wbf1"))
    }

    private val taxi = file("$gc/Crazy Taxi (USA).rvz", 93_626_904)
    private val gcFiles = listOf(
        taxi,
        file("$gc/Luigi's Mansion (USA).rvz", 157_576_576),
        file("$gc/Kururin Squash (Japan).iso", RomConsole.GAMECUBE_DISC),
        file("$gc/Kururin Squash (Japan).rvz", 105_730_496),
        file("$gc/Tales of Symphonia (USA) (Disc 1).gcm", RomConsole.GAMECUBE_DISC),
        file("$gc/Old (USA).nkit.iso", 300_000_000),
        file("$gc/Older (USA).gcz", 600_000_000),
        file("$gc/MemoryCardA.USA.raw", 16_777_216),
        file("$gc/Tales of Symphonia (USA).m3u", 60),
    )
    private val gcHeaders = mapOf(
        taxi.path to PackedHeaders.parse(bytes(crazyTaxi), "rvz")!!,
        "$gc/Older (USA).gcz" to PackedHeaders.parse(bytes(oldGcz), "gcz")!!,
    )
    private val gcTexts = mapOf(
        "$gc/Tales of Symphonia (USA).m3u" to "Tales of Symphonia (USA) (Disc 1).gcm\nTales of Symphonia (USA) (Disc 2).gcm\n",
    )
    private val gcGames = RomSets.games(RomConsole.GAMECUBE, gcFiles, gcTexts, gcHeaders).associateBy { it.title }

    private val nsmbwIso = file("$wii/New Super Mario Bros. Wii (USA) (En,Fr,Es) (Rev 2).iso", RomConsole.WII_DISC)
    private val galaxyWbfs = file("$wii/Super Mario Galaxy (USA, Canada) (En,Fr,Es).wbfs", 3_508_535_296)
    private val wiiFiles = listOf(
        nsmbwIso,
        galaxyWbfs,
        file("$wii/Super Smash Bros. Brawl (USA).wbfs", 4_000_000_000),
        file("$wii/Super Smash Bros. Brawl (USA).wbf1", 3_000_000_000),
        file("$wii/Wii Sports (USA).wia", 250_000_000),
        file("$wii/Wii Fit (USA).chd", 900_000_000),
    )
    private val wiiGames = RomSets.games(
        RomConsole.WII,
        wiiFiles,
        emptyMap(),
        mapOf(galaxyWbfs.path to PackedHeaders.parse(bytes(galaxy), "wbfs")!!),
    ).associateBy { it.title }

    @Test
    fun `GameCube games are images and compressed files, NKit and old GCZs are left, nothing else is a game`() {
        assertEquals(
            setOf(
                "Crazy Taxi (USA)",
                "Luigi's Mansion (USA)",
                "Kururin Squash (Japan)",
                "Tales of Symphonia (USA) (Disc 1)",
                "Old (USA).nkit",
                "Older (USA)",
            ),
            gcGames.keys,
        )
        val crazy = gcGames.getValue("Crazy Taxi (USA)")
        assertEquals(RomFormat.RVZ, crazy.current)
        assertEquals(RomConsole.GAMECUBE_DISC, crazy.imageSize)
        assertTrue(crazy.canConvert)
        assertFalse(crazy.notCompressed)
        assertEquals(listOf(RomFormat.ISO), crazy.targets)
        // Without a header the extension tells the format, and a GameCube disc is always the same size.
        assertEquals(RomConsole.GAMECUBE_DISC, gcGames.getValue("Luigi's Mansion (USA)").imageSize)
        val kururin = gcGames.getValue("Kururin Squash (Japan)")
        assertTrue(kururin.originalsLeft)
        assertEquals("$gc/Kururin Squash (Japan).rvz", kururin.compressed?.path)
        val tales = gcGames.getValue("Tales of Symphonia (USA) (Disc 1)")
        assertTrue(tales.canCompress)
        assertEquals(listOf(RomFormat.RVZ), tales.targets)
        assertEquals(1, tales.disc)
        assertEquals(listOf("$gc/Tales of Symphonia (USA).m3u"), tales.playlists)
        assertEquals(GameProblem.NKit, gcGames.getValue("Old (USA).nkit").problem)
        assertEquals(GameProblem.CantRead, gcGames.getValue("Older (USA)").problem)
    }

    @Test
    fun `Wii games are an ISO to compress, WBFS files to turn into RVZ and a split WBFS counted whole, never a CHD`() {
        assertEquals(
            setOf(
                "New Super Mario Bros. Wii (USA) (En,Fr,Es) (Rev 2)",
                "Super Mario Galaxy (USA, Canada) (En,Fr,Es)",
                "Super Smash Bros. Brawl (USA)",
                "Wii Sports (USA)",
            ),
            wiiGames.keys,
        )
        val nsmbw = wiiGames.getValue("New Super Mario Bros. Wii (USA) (En,Fr,Es) (Rev 2)")
        assertTrue(nsmbw.canCompress)
        assertEquals(listOf(RomFormat.RVZ), nsmbw.targets)
        val galaxyGame = wiiGames.getValue("Super Mario Galaxy (USA, Canada) (En,Fr,Es)")
        assertEquals(RomFormat.WBFS, galaxyGame.current)
        assertTrue(galaxyGame.canConvert)
        assertTrue(galaxyGame.notCompressed)
        assertEquals(RomConsole.WII_DISC, galaxyGame.imageSize)
        assertEquals(listOf(RomFormat.RVZ, RomFormat.ISO), galaxyGame.targets)
        val brawl = wiiGames.getValue("Super Smash Bros. Brawl (USA)")
        assertEquals(listOf("$wii/Super Smash Bros. Brawl (USA).wbf1"), brawl.parts)
        assertEquals(7_000_000_000L, brawl.sizeNow)
        assertEquals(RomConsole.WII_DUAL_LAYER, brawl.imageSize)
        val sports = wiiGames.getValue("Wii Sports (USA)")
        assertEquals(RomFormat.WIA, sports.current)
        assertFalse(sports.notCompressed)
    }

    private fun model(console: RomConsole, games: List<DiscGame>, list: RomFormat? = null, dolphin: String = "2609a") = RomUiModel(
        shelf = ShelfState(games = mapOf(console to games), installed = listOf(InstalledApp("org.dolphinemu.dolphinemu", dolphin))),
        console = console,
        filter = GameFilter.ALL,
        filterSet = true,
        listFormats = list?.let { mapOf(console.id to it) }.orEmpty(),
    )

    @Test
    fun `RVZ is the default, WBFS games are ticked with the ISO, an RVZ can be unpacked through the list`() {
        val wiiState = model(RomConsole.WII, wiiGames.values.toList())
        assertEquals(RomFormat.RVZ, wiiState.listFormat(RomConsole.WII))
        assertEquals(GameFilter.NOT_COMPRESSED, GameList.firstFilter(wiiGames.values.toList()))
        assertEquals(
            setOf(
                "New Super Mario Bros. Wii (USA) (En,Fr,Es) (Rev 2)",
                "Super Mario Galaxy (USA, Canada) (En,Fr,Es)",
                "Super Smash Bros. Brawl (USA)",
            ),
            wiiState.selectable.map { it.title }.toSet(),
        )
        val gcGamesList = gcGames.values.toList()
        assertEquals(GameFilter.NOT_COMPRESSED, GameList.firstFilter(gcGamesList))
        val crazy = gcGames.getValue("Crazy Taxi (USA)")
        assertNull(model(RomConsole.GAMECUBE, gcGamesList).convertTarget(crazy))
        val toIso = model(RomConsole.GAMECUBE, gcGamesList, list = RomFormat.ISO)
        assertEquals(RomFormat.ISO, toIso.convertTarget(crazy))
        assertEquals(RomConsole.GAMECUBE_DISC, toIso.estimate(crazy).middle)
    }

    @Test
    fun `Dolphin opens RVZ from 5_0-12188 on, Ishiiruka never, and the default skips the plain image`() {
        val current = EmulatorChecks.of(RomConsole.WII, RomFormat.RVZ, listOf(InstalledApp("org.dolphinemu.dolphinemu", "2609a")), null)
        assertEquals(listOf(EmulatorCheck("Dolphin", "2609a", Support.DOCUMENTED)), current)
        val old = EmulatorChecks.of(
            RomConsole.GAMECUBE,
            RomFormat.RVZ,
            listOf(InstalledApp("org.dolphinemu.dolphinemu", "5.0-11991")),
            null,
        )
        assertEquals(EmulatorCheck("Dolphin", "5.0-11991", Support.NO, needs = "5.0-12188"), old.single())
        val ishiiruka = listOf(InstalledApp("org.dolphin.ishiirukadark", "1.0"))
        assertFalse(EmulatorChecks.anyOpens(EmulatorChecks.of(RomConsole.WII, RomFormat.RVZ, ishiiruka, null)))
        assertEquals(RomFormat.RVZ, EmulatorChecks.defaultFormat(RomConsole.WII, DiscKind.DVD, ishiiruka, null))
        val core = EmulatorChecks.of(RomConsole.GAMECUBE, RomFormat.RVZ, emptyList(), setOf("dolphin_libretro"))
        assertEquals(Support.DOCUMENTED, core.single().support)
    }

    @Test
    fun `an image becomes RVZ in one nod run inside the job's folder, checked by SHA-1 before it moves in`() {
        val nsmbw = wiiGames.getValue("New Super Mario Bros. Wii (USA) (En,Fr,Es) (Rev 2)")
        val job = RomJobs.compress(5, nsmbw, Originals.KEEP, RomSpeed.NORMAL, Estimate(400_000_000, 540_000_000, true), null)!!
        assertEquals(RomFormat.RVZ, job.format)
        assertEquals("$wii/New Super Mario Bros. Wii (USA) (En,Fr,Es) (Rev 2).rvz", job.output)
        val script = RomJobPlan.script(job, chdman, work, nod = nod)
        val temp = "'$wii/.thortools-5'"
        val made = "'$wii/.thortools-5/New Super Mario Bros. Wii (USA) (En,Fr,Es) (Rev 2).rvz'"
        assertTrue(script.contains("N='$nod'"))
        assertTrue(script.contains("finish() { rm -rf $temp; echo"))
        val check = script.indexOf("case \"\$magic\" in 5d1c9ea3*|????????c2339f3d) ;; *) finish notdisc ;; esac")
        val convert = script.indexOf("\"\$N\" convert -j 6 -c zstd:19 '${nsmbwIso.path}' $made > \"\$W/compress.out\"")
        val verify = script.indexOf("\"\$N\" verify -j 6 $made > \"\$W/verify.out\"")
        val compare = script.indexOf("[ -n \"\$sha1\" ] && [ \"\$sha1\" = \"\$sha2\" ] || finish \"verify 1\"")
        val move = script.indexOf("mv $made '${job.output}' || finish \"move 1\"")
        assertTrue(
            listOf(check, convert, verify, compare, move).toString(),
            check in 0 until convert && convert < verify && verify < compare && compare < move,
        )
        assertTrue(script.contains("sha1=\$(grep 'SHA-1 *:' \"\$W/compress.out\" | tail -n 1 | sed 's/^[^:]*: *//' | cut -c1-40)"))
        assertFalse(script.contains("createdvd"))
        assertFalse(script.contains("CD001"))
        assertFalse(script.contains("rm -f '${nsmbwIso.path}'"))
    }

    @Test
    fun `a WBFS turns into RVZ straight from itself, and its originals go only after the check`() {
        val galaxyGame = wiiGames.getValue("Super Mario Galaxy (USA, Canada) (En,Fr,Es)")
        val job = RomJobs.convert(
            5,
            galaxyGame,
            RomFormat.RVZ,
            Originals.DELETE,
            RomSpeed.GENTLE,
            Estimate(3_000_000_000, 3_400_000_000, true),
            null,
        )!!
        assertEquals(RomFormat.WBFS, job.from)
        assertEquals(0L, job.temp)
        assertEquals(3_400_000_000 + RomJobPlan.MARGIN, job.need)
        val script = RomJobPlan.script(job, chdman, work, nod = nod)
        assertFalse(script.contains("magic="))
        assertTrue(
            script.contains(
                "\"\$N\" convert -j 3 -c zstd:19 '${galaxyWbfs.path}' '$wii/.thortools-5/Super Mario Galaxy (USA, Canada) (En,Fr,Es).rvz'",
            ),
        )
        assertTrue(script.indexOf("finish \"verify 1\"") < script.indexOf("rm -f '${galaxyWbfs.path}'"))
        val brawl = RomJobs.convert(
            6,
            wiiGames.getValue("Super Smash Bros. Brawl (USA)"),
            RomFormat.RVZ,
            Originals.DELETE,
            RomSpeed.NORMAL,
            Estimate(1, 2, true),
            null,
        )!!
        assertEquals(listOf("$wii/Super Smash Bros. Brawl (USA).wbfs", "$wii/Super Smash Bros. Brawl (USA).wbf1"), brawl.originals)
    }

    @Test
    fun `turning an RVZ back makes an ISO with nod, counted as the whole disc`() {
        val crazy = gcGames.getValue("Crazy Taxi (USA)")
        val job = RomJobs.extract(5, crazy, Originals.KEEP, RomSpeed.NORMAL, null)!!
        assertEquals(JobKind.COMPRESS, job.kind)
        assertEquals(RomFormat.ISO, job.format)
        assertEquals("$gc/Crazy Taxi (USA).iso", job.output)
        assertEquals(RomConsole.GAMECUBE_DISC, job.temp)
        assertEquals(RomConsole.GAMECUBE_DISC + RomJobPlan.MARGIN, job.need)
        val script = RomJobPlan.script(job, chdman, work, nod = nod)
        assertTrue(script.contains("\"\$N\" convert -j 6 '${taxi.path}' '$gc/.thortools-5/Crazy Taxi (USA).iso'"))
        assertFalse(script.contains("extractdvd"))
        // The space it takes while it runs is the whole image.
        val plan = RomSpace.plan(listOf(job), emptyList(), mapOf("/storage/EBBF-779E" to RomConsole.GAMECUBE_DISC + RomJobPlan.MARGIN))
        assertTrue(plan.fitsAll)
    }

    @Test
    fun `cleaning up a GameCube image compares the RVZ's SHA-1 with the image's`() {
        val kururin = gcGames.getValue("Kururin Squash (Japan)")
        val job = RomJobs.originals(5, kururin, Originals.DELETE, RomSpeed.NORMAL, null)!!
        val script = RomJobPlan.script(job, chdman, work, nod = nod)
        val rvz = script.indexOf("\"\$N\" verify -j 6 '$gc/Kururin Squash (Japan).rvz'")
        val iso = script.indexOf("\"\$N\" verify -j 6 '$gc/Kururin Squash (Japan).iso'")
        val delete = script.indexOf("rm -f '$gc/Kururin Squash (Japan).iso'")
        assertTrue(listOf(rvz, iso, delete).toString(), rvz in 0 until iso && iso < delete)
        assertFalse(script.contains("verify -i"))
    }

    @Test
    fun `RVZ estimates come from the bytes of the disc's files`() {
        assertEquals(2_186_976L, ShrinkEstimate.listedBytes("903136\tct/poldc1.all\n1283840\tct/texdc3.all\nLoading x\n\n"))
        // New Super Mario Bros. Wii: 539,629,112 bytes of files on a 4.7 GB disc became a 439,397,124-byte RVZ.
        val estimate = ShrinkEstimate.rvz(539_629_112, RomConsole.WII_DISC)
        assertTrue(estimate.toString(), 439_397_124 in estimate.low..estimate.high)
        assertEquals(RomConsole.GAMECUBE_DISC, ShrinkEstimate.rvz(5_000_000_000, RomConsole.GAMECUBE_DISC).high)
    }

    @Test
    fun `nod's progress lines are read`() {
        val progress = ChdTool.progress("progress 0.0% ratio 0.0%\nprogress 45.3% ratio 12.1%\n")
        assertEquals(45.3, progress!!.percent, 0.001)
        assertEquals(12.1, progress.ratio!!, 0.001)
    }
}
