package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.RomFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomPspTest {
    private val psp = "/storage/EBBF-779E/Roms/PSP"
    private val ps2 = "/storage/EBBF-779E/Roms/PS2"
    private val chdman = "/data/app/~~a==/io.github.thatonecodingperson.thortools-b==/lib/arm64/libchdman.so"
    private val maxcso = "/data/app/~~a==/io.github.thatonecodingperson.thortools-b==/lib/arm64/libmaxcso.so"
    private val work = "/data/user/0/io.github.thatonecodingperson.thortools/cache/roms-jobs/5"

    // The first 64 bytes of real files, read with `od` on a Thor.
    private val apeEscape = "4d436f6d707248440000007c000000056c7a6d617a6c696268756666666c6163000000006a1d0000000000005b482519" +
        "000000000000007c0000100000000800"
    private val katamari = "4d436f6d707248440000007c000000057a73746400000000000000000000000000000000bc3e8000000000004f505549" +
        "000000000000007c0000400000000800"
    private val dynasty = "4d436f6d707248440000007c0000000563646c7a63647a6c6364666c00000000000000002ac04800000000001a35fe11" +
        "000000000000007c00004c8000000990"
    private val disgaea = "4349534f000000000000ce390000000000080000010000001ce71c002de71c003ee71c004fe71c0060e71c0071e71c00" +
        "82e71c0093e71c00a4e71c00b5e71c00"

    private fun header(hex: String) = PackedHeaders.parse(PackedHeaders.fromHex(hex)!!)!!

    private fun file(path: String, size: Long) = RomFile(path, size, 0L)

    @Test
    fun `headers tell the format, the image's size, the block and the kind`() {
        assertEquals(PackedHeader(RomFormat.CHD_DVD, 1_780_285_440, 4096, DiscKind.DVD), header(apeEscape))
        assertEquals(PackedHeader(RomFormat.CHD_DVD_ZSTD, 3_158_212_608, 16384, DiscKind.DVD), header(katamari))
        assertEquals(PackedHeader(RomFormat.CHD_CD, 717_244_416, 19584, DiscKind.CD), header(dynasty))
        assertEquals(PackedHeader(RomFormat.CSO, 969_801_728, 2048, DiscKind.DVD), header(disgaea))
        assertNull(PackedHeaders.parse(ByteArray(64)))
        assertNull(PackedHeaders.fromHex("4d4"))
        assertEquals(RomFormat.ZSO, PackedHeaders.parse(PackedHeaders.fromHex("5a49534f" + disgaea.drop(8))!!)!!.format)
    }

    private val cso = file("$psp/Disgaea - Afternoon of Darkness (USA).cso", 491_518_752)
    private val files = listOf(
        cso,
        file("$psp/Dissidia - Final Fantasy (USA).cso", 1_376_304_248),
        file("$psp/Lumines (USA).iso", 2048L * 150_000),
        file("$psp/Patapon (USA).chd", 200_000_000),
        file("$psp/Echochrome (USA).pbp", 100_000_000),
        file("$psp/Saves/PSP/SAVEDATA/ULUS10000/DATA.BIN", 2048L * 100),
        file("$psp/Saves/PSP/GAME/NPUH10000/EBOOT.PBP", 50_000_000),
        file("$psp/Saves/PSP/PPSSPP_STATE/ULUS10000_1.00_0.ppst", 30_000_000),
        file("$psp/Saves/PSP/SYSTEM/ppsspp.ini", 9000),
        file("$psp/Stray.bin", 2352L * 1000),
    )
    private val headers = mapOf(cso.path to header(disgaea))
    private val games = RomSets.games(RomConsole.PSP, files, emptyMap(), headers).associateBy { it.title }

    @Test
    fun `PSP games are CSOs with their headers, ISOs, CHDs and eboots, nothing from the memory stick and no CD images`() {
        assertEquals(
            setOf(
                "Disgaea - Afternoon of Darkness (USA)",
                "Dissidia - Final Fantasy (USA)",
                "Lumines (USA)",
                "Patapon (USA)",
                "Echochrome (USA)",
            ),
            games.keys,
        )
        val disgaeaGame = games.getValue("Disgaea - Afternoon of Darkness (USA)")
        assertEquals(RomFormat.CSO, disgaeaGame.current)
        assertEquals(969_801_728L, disgaeaGame.imageSize)
        assertTrue(disgaeaGame.canConvert)
        assertEquals(listOf(RomFormat.CHD_DVD_ZSTD, RomFormat.CHD_DVD, RomFormat.ISO), disgaeaGame.targets)
        assertNull(games.getValue("Dissidia - Final Fantasy (USA)").imageSize)
        val lumines = games.getValue("Lumines (USA)")
        assertTrue(lumines.canCompress)
        assertEquals(RomFormat.ISO, lumines.current)
        assertEquals(listOf(RomFormat.CSO, RomFormat.CHD_DVD_ZSTD, RomFormat.CHD_DVD), lumines.targets)
        assertEquals(DiscKind.DVD, games.getValue("Patapon (USA)").kind)
        assertEquals(GameProblem.NotHandled("pbp"), games.getValue("Echochrome (USA)").problem)
        assertTrue(RomSets.onMemoryStick("$psp/Saves/PSP/SAVEDATA/X/DATA.BIN"))
        assertFalse(RomSets.onMemoryStick("$psp/Disgaea.cso"))
        assertFalse(RomSets.onMemoryStick("/storage/x/PSP/ISO/Game.iso"))
    }

    @Test
    fun `PSP's CHDs use 2 KB hunks as PPSSPP advises and its default follows the installed emulators`() {
        val lumines = games.getValue("Lumines (USA)")
        val job = { format: RomFormat ->
            RomJobs.compress(5, lumines, Originals.KEEP, RomSpeed.NORMAL, Estimate(1, 2, true), null, format = format)!!
        }
        assertTrue(
            RomJobPlan.script(
                job(RomFormat.CHD_DVD_ZSTD),
                chdman,
                work,
            ).contains("createdvd -i '$psp/Lumines (USA).iso' -o '$psp/Lumines (USA).chd.part' -c zstd -hs 2048 -np 6 -f"),
        )
        assertTrue(RomJobPlan.script(job(RomFormat.CHD_DVD), chdman, work).contains("-o '$psp/Lumines (USA).chd.part' -hs 2048 -np 6 -f"))
        assertTrue(RomJobPlan.script(job(RomFormat.CSO), chdman, work, maxcso).contains("--threads=6 '$psp/Lumines (USA).iso' -o"))
        assertNull(RomJobs.compress(5, lumines, Originals.KEEP, RomSpeed.NORMAL, Estimate(1, 2, true), null, format = RomFormat.ISO))
        assertNull(RomJobs.compress(5, lumines, Originals.KEEP, RomSpeed.NORMAL, Estimate(1, 2, true), null, format = RomFormat.ZSO))

        val ppsspp = listOf(InstalledApp("org.ppsspp.ppsspp", "v1.20.4"))
        assertEquals(RomFormat.CSO, EmulatorChecks.defaultFormat(RomConsole.PSP, DiscKind.DVD, ppsspp, null))
        val lemuroid = listOf(InstalledApp("com.swordfish.lemuroid", "1.17.0"))
        assertEquals(RomFormat.CSO, EmulatorChecks.defaultFormat(RomConsole.PSP, DiscKind.DVD, lemuroid, null))
        val old = EmulatorChecks.of(RomConsole.PSP, RomFormat.CHD_DVD_ZSTD, listOf(InstalledApp("org.ppsspp.ppsspp", "v1.17")), null)
        assertEquals("1.17.1", old.single().needs)
    }

    private fun convert(title: String, target: RomFormat, choice: Originals = Originals.KEEP) =
        RomJobs.convert(5, games.getValue(title), target, choice, RomSpeed.NORMAL, Estimate(400_000_000, 450_000_000, false), null)!!

    @Test
    fun `a CSO becomes a CHD through an unpacked image that is checked, then removed however the job ends`() {
        val job = convert("Disgaea - Afternoon of Darkness (USA)", RomFormat.CHD_DVD_ZSTD)
        assertEquals(RomFormat.CSO, job.from)
        assertEquals(969_801_728L, job.temp)
        assertEquals("$psp/Disgaea - Afternoon of Darkness (USA).chd", job.output)
        assertEquals(listOf(cso.path), job.originals)
        assertEquals(969_801_728L + 450_000_000 + RomJobPlan.MARGIN, job.need)
        val script = RomJobPlan.script(job, chdman, work, maxcso)
        val temp = "'$psp/.thortools-5'"
        val iso = "'$psp/.thortools-5/Disgaea - Afternoon of Darkness (USA).iso'"
        assertTrue(script.contains("finish() { rm -rf $temp; echo"))
        val unpack = script.indexOf("--decompress '${cso.path}' -o $iso")
        val check = script.indexOf("dd if=$iso bs=2048 skip=16")
        val make = script.indexOf("createdvd -i $iso -o '$psp/Disgaea - Afternoon of Darkness (USA).chd.part' -c zstd -hs 2048")
        val verify = script.indexOf("verify -i '$psp/Disgaea - Afternoon of Darkness (USA).chd.part'")
        assertTrue(script.indexOf("mkdir -p $temp") in 0 until unpack)
        assertTrue(unpack < check && check < make && make < verify)
        assertTrue(verify < script.indexOf("mv '$psp/Disgaea - Afternoon of Darkness (USA).chd.part'"))
        assertFalse(script.contains("rm -f '${cso.path}'"))
        assertTrue(
            RomJobPlan.script(
                convert("Disgaea - Afternoon of Darkness (USA)", RomFormat.CHD_DVD, Originals.DELETE),
                chdman,
                work,
                maxcso,
            ).contains("rm -f '${cso.path}'"),
        )
        assertEquals(job, RomJobPlan.decode(RomJobPlan.encode(job)))
    }

    @Test
    fun `a CHD becomes a CSO only when it holds a DVD image, and any compressed game can become an ISO again`() {
        val patapon = games.getValue("Patapon (USA)")
        val job = convert("Patapon (USA)", RomFormat.CSO)
        assertEquals(RomFormat.CHD_DVD, job.from)
        val script = RomJobPlan.script(job, chdman, work, maxcso)
        val iso = "'$psp/.thortools-5/Patapon (USA).iso'"
        assertTrue(script.contains("grep -q \"Tag='DVD '\" \"\$W/info.out\" || finish notdvd"))
        assertTrue(
            script.indexOf("extractdvd -i '${patapon.compressed!!.path}' -o $iso -f") < script.indexOf("\"\$M\" --threads=6 $iso -o"),
        )
        assertTrue(script.contains("\"\$M\" --crc $iso"))

        val back = RomJobPlan.script(convert("Disgaea - Afternoon of Darkness (USA)", RomFormat.ISO), chdman, work, maxcso)
        assertTrue(
            back.contains(
                "mv '$psp/.thortools-5/Disgaea - Afternoon of Darkness (USA).iso' '$psp/Disgaea - Afternoon of Darkness (USA).iso'",
            ),
        )
        assertFalse(back.contains("createdvd"))
        assertNull(RomJobs.convert(5, patapon, RomFormat.CHD_DVD_ZSTD, Originals.KEEP, RomSpeed.NORMAL, Estimate(1, 2, false), null))
        assertNull(
            RomJobs.convert(
                5,
                games.getValue("Lumines (USA)"),
                RomFormat.CSO,
                Originals.KEEP,
                RomSpeed.NORMAL,
                Estimate(1, 2, false),
                null,
            ),
        )
    }

    @Test
    fun `a CSO kept beside the CHD made from it is that game's original, cleaned up after the CHD's own check`() {
        val older = RomFile("$psp/Final Fantasy Tactics (USA).cso", 235_270_432, 1_790_000_000)
        val newer = RomFile("$psp/Final Fantasy Tactics (USA).chd", 212_224_851, 1_790_500_000)
        val game = RomSets.games(RomConsole.PSP, listOf(older, newer), emptyMap()).single()
        assertEquals(newer, game.compressed)
        assertEquals(older.path, game.main)
        assertTrue(game.originalsLeft)
        assertFalse(game.canConvert)
        assertEquals(GameFilter.ALL, GameList.firstFilter(listOf(game, games.getValue("Patapon (USA)"))))
        assertEquals(GameFilter.ORIGINALS, GameList.firstFilter(listOf(game)))
        val cleanup = RomJobs.originals(5, game, Originals.DELETE, RomSpeed.NORMAL, null)!!
        val script = RomJobPlan.script(cleanup, chdman, work, maxcso)
        assertTrue(script.indexOf("verify -i '${newer.path}'") < script.indexOf("rm -f '${older.path}'"))

        val chdFirst = RomSets.games(RomConsole.PSP, listOf(newer.copy(modified = 1), older.copy(modified = 2)), emptyMap()).single()
        val back = RomJobPlan.script(RomJobs.originals(5, chdFirst, Originals.DELETE, RomSpeed.NORMAL, null)!!, chdman, work, maxcso)
        assertTrue(back.contains("\"\$M\" --crc '${older.path}'"))
        assertTrue(back.indexOf("grep -q 'CRC32: [0-9a-f]'") < back.indexOf("rm -f '${newer.path}'"))
    }

    @Test
    fun `conversion estimates come from the file's size and what each format usually does`() {
        val estimate = ShrinkEstimate.converted(1_000_000_000, 1_600_000_000, RomFormat.CSO, RomFormat.CHD_DVD)
        assertEquals(880_000_000.0, estimate.middle.toDouble(), 2.0)
        assertFalse(estimate.measured)
        assertEquals(
            Estimate(1_600_000_000, 1_600_000_000, true),
            ShrinkEstimate.converted(1_000_000_000, 1_600_000_000, RomFormat.CSO, RomFormat.ISO),
        )
        assertTrue(ShrinkEstimate.converted(1_000_000_000, 1_050_000_000, RomFormat.CHD_DVD, RomFormat.CSO).high <= 1_050_000_000)
        val start = ByteArray(64 * 1024)
        "\u0001CD001\u0001\u0000PSP GAME".toByteArray().copyInto(start, 16 * 2048)
        assertEquals("PSP GAME", ShrinkEstimate.systemId(start))
        "UMD VIDEO".padEnd(32).toByteArray().copyInto(start, 16 * 2048 + 8)
        assertTrue(RomConsole.PSP.notGames.any { ShrinkEstimate.systemId(start)!!.startsWith(it) })
    }

    @Test
    fun `while a conversion runs it needs room for the unpacked image too, and deleting gives the old file back`() {
        val gb = 1_000_000_000L
        val job = convert("Disgaea - Afternoon of Darkness (USA)", RomFormat.CHD_DVD).copy(need = 2 * gb, temp = gb, inputBytes = gb / 2)
        val free = mapOf("/storage/EBBF-779E" to 5 * gb / 2)
        assertTrue(RomSpace.plan(listOf(job, job), emptyList(), free).fit == 1)
        assertTrue(RomSpace.plan(listOf(job, job).map { it.copy(choice = Originals.DELETE) }, emptyList(), free).fitsAll)
    }

    private fun model(games: List<DiscGame>, ticked: Set<String> = emptySet(), list: RomFormat? = null) = RomUiModel(
        shelf = ShelfState(games = mapOf(RomConsole.PSP to games), installed = listOf(InstalledApp("org.ppsspp.ppsspp", "v1.20.4"))),
        console = RomConsole.PSP,
        filter = GameFilter.ALL,
        filterSet = true,
        ticked = ticked,
        listFormats = list?.let { mapOf("psp" to it) }.orEmpty(),
    )

    @Test
    fun `the list converts compressed games into its format and Select all ticks them when nothing else is to compress`() {
        val all = games.values.toList()
        val cso = games.getValue("Disgaea - Afternoon of Darkness (USA)")
        val chd = games.getValue("Patapon (USA)")
        val iso = games.getValue("Lumines (USA)")
        // PSP's list compresses into CSO unless another format is picked (owner: CSO first).
        val state = model(all)
        assertEquals(RomFormat.CSO, state.listFormat(RomConsole.PSP))
        assertNull(state.convertTarget(cso))
        assertEquals(RomFormat.CSO, state.convertTarget(chd))
        assertEquals(listOf(iso), state.selectable)
        val onlyPacked = model(all - iso)
        assertEquals(setOf(chd), onlyPacked.selectable.toSet())
        assertFalse(onlyPacked.cleanFirst)
        val toChd = model(all - iso, list = RomFormat.CHD_DVD_ZSTD)
        assertEquals(RomFormat.CHD_DVD_ZSTD, toChd.convertTarget(cso))
        assertNull(toChd.convertTarget(chd))
        assertEquals(setOf(cso, games.getValue("Dissidia - Final Fantasy (USA)")), toChd.selectable.toSet())
        val toIso = model(all, ticked = setOf(cso.id, iso.id), list = RomFormat.ISO)
        assertEquals(listOf(cso), toIso.toCompress)
        assertEquals(cso.imageSize, toIso.estimate(cso).middle)
    }
}
