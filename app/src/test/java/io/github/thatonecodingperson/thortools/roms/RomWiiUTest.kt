package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.RomFile
import io.github.thatonecodingperson.thortools.tools.RootFiles.Companion.quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomWiiUTest {
    private val wiiu = "/storage/emulated/0/Roms/WiiU"
    private val chdman = "/data/app/~~a==/io.github.thatonecodingperson.thortools-b==/lib/arm64/libchdman.so"
    private val wua = "/data/app/~~a==/io.github.thatonecodingperson.thortools-b==/lib/arm64/libwua.so"
    private val work = "/data/user/0/io.github.thatonecodingperson.thortools/cache/roms-jobs/5"

    private val game = "$wiiu/WARRIORS OROCHI 3 HyperNA [Game] [0005000010110200]"
    private val update = "$wiiu/WARRIORS OROCHI 3 HyperNA [Update] [0005000e10110200]"
    private val lone = "$wiiu/Some DLC [DLC] [0005000c10999900]"

    // `code/app.xml` lines as real game folders have them.
    private fun appXml(id: String, version: String) = """
        <?xml version="1.0" encoding="utf-8"?>
        <app type="complex" access="777">
          <title_id type="hexBinary" length="8">$id</title_id>
          <title_version type="hexBinary" length="2">$version</title_version>
        </app>
    """.trimIndent()

    private val files = listOf(
        RomFile("$game/code/app.xml", 600, 1),
        RomFile("$game/code/OROCHI2_WiiU_EngRelease.rpx", 6_287_808, 1),
        RomFile("$game/content/data.bin", 16_000_000_000, 1),
        RomFile("$game/meta/meta.xml", 4_000, 1),
        RomFile("$update/code/app.xml", 600, 1),
        RomFile("$update/code/OROCHI2_WiiU_EngRelease.rpx", 5_142_592, 1),
        RomFile("$update/content/patch.bin", 115_000_000, 1),
        RomFile("$lone/code/app.xml", 600, 1),
        RomFile("$wiiu/Mario Kart 8 (US).wua", 4_664_266_842, 1),
        RomFile("$wiiu/Broken.wua", 1_000, 1),
        RomFile("$wiiu/Zelda Disc.wud", 25_025_314_816, 1),
        RomFile("$wiiu/Saves/mlc01/usr/title/0005000e/10101d00/code/app.xml", 600, 1),
        RomFile("$wiiu/Saves/mlc01/usr/title/0005000e/10101d00/content/a.bin", 1_000_000, 1),
    )
    private val texts = mapOf(
        "$game/code/app.xml" to appXml("0005000010110200", "0000"),
        "$update/code/app.xml" to appXml("0005000E10110200", "0020"),
        "$lone/code/app.xml" to appXml("0005000C10999900", "0010"),
    )

    // `wua info` of a Mario Kart 8 WUA, read on a Thor.
    private val wuas = WiiU.parseInfo(
        "6490181211\t000500001010ec00_v1,0005000c1010ec00_v80,0005000e1010ec00_v81\t$wiiu/Mario Kart 8 (US).wua\n" +
            "?\t-\t$wiiu/Broken.wua",
    )

    private val games = RomSets.games(RomConsole.WII_U, files, texts, wuas = wuas).associateBy { it.title }

    @Test
    fun `a title folder's app xml gives its title ID and version, and tags come off its name`() {
        assertEquals(WiiUTitle("x", "0005000e10110200", 32), WiiU.parseAppXml("x", appXml("0005000E10110200", "0020")))
        assertNull(WiiU.parseAppXml("x", "<app></app>"))
        assertEquals("WARRIORS OROCHI 3 HyperNA", WiiU.cleanTitle("WARRIORS OROCHI 3 HyperNA [Game] [0005000010110200]"))
        assertEquals("Mario Kart 8 (US) [Update] [0005000e1010ec00]", WiiU.folderName("Mario Kart 8 (US)", "0005000e1010ec00_v81"))
        assertTrue(WiiU.inEmulatorData("$wiiu/Saves/mlc01/usr/title/x"))
        assertEquals(
            WuaInfo(6_490_181_211, listOf("000500001010ec00_v1", "0005000c1010ec00_v80", "0005000e1010ec00_v81")),
            wuas["$wiiu/Mario Kart 8 (US).wua"],
        )
        assertNull(wuas.getValue("$wiiu/Broken.wua"))
        assertEquals(wuas["$wiiu/Mario Kart 8 (US).wua"], WiiU.decodeInfo(WiiU.encodeInfo(wuas["$wiiu/Mario Kart 8 (US).wua"])))
    }

    @Test
    fun `a game folder takes its update beside it, and Cemu's own storage is never a game`() {
        assertEquals(setOf("WARRIORS OROCHI 3 HyperNA", "Some DLC", "Mario Kart 8 (US)", "Broken", "Zelda Disc"), games.keys)
        val orochi = games.getValue("WARRIORS OROCHI 3 HyperNA")
        assertTrue(orochi.canCompress)
        assertEquals(mapOf("0005000010110200_v0" to game, "0005000e10110200_v32" to update), orochi.titles)
        assertEquals(7, orochi.files.size)
        assertEquals(listOf(RomFormat.WUA, RomFormat.FOLDERS), orochi.formats)
        assertEquals("$wiiu/WARRIORS OROCHI 3 HyperNA.wua", orochi.output(RomFormat.WUA))
        assertEquals(GameProblem.LoneTitle, games.getValue("Some DLC").problem)
        assertEquals(GameProblem.NeedsKeys, games.getValue("Zelda Disc").problem)
        assertEquals(GameProblem.CantRead, games.getValue("Broken").problem)
        val kart = games.getValue("Mario Kart 8 (US)")
        assertTrue(kart.canConvert)
        assertEquals(listOf(RomFormat.FOLDERS), kart.targets)
        assertEquals("$wiiu/Mario Kart 8 (US) [Game] [000500001010ec00]", kart.output(RomFormat.FOLDERS))
    }

    @Test
    fun `a game and its WUA of the same name pair up, the folders being the originals`() {
        val packed = RomSets.games(RomConsole.WII_U, files + RomFile("$wiiu/WARRIORS OROCHI 3 HyperNA.wua", 15_870_860_603, 2), texts)
            .single { it.title == "WARRIORS OROCHI 3 HyperNA" }
        assertTrue(packed.originalsLeft)
        assertEquals(RomFormat.WUA, packed.packed)
        val job = RomJobs.originals(7, packed, Originals.DELETE, RomSpeed.NORMAL, null)!!
        val script = RomJobPlan.script(job, chdman, work, wua = wua)
        val verify = script.indexOf("\"\$X\" verify '$wiiu/WARRIORS OROCHI 3 HyperNA.wua'")
        val digest = script.indexOf("\"\$X\" digest ${quote("0005000010110200_v0=$game")} ${quote("0005000e10110200_v32=$update")}")
        val delete = script.indexOf("[ -f ${quote("$game/code/app.xml")} ] && rm -rf ${quote(game)}")
        assertTrue(listOf(verify, digest, delete).toString(), verify in 0 until digest && digest < delete)
    }

    @Test
    fun `packing a game makes the WUA in its own folder, and it moves in only when it reads back the same`() {
        val orochi = games.getValue("WARRIORS OROCHI 3 HyperNA")
        val job = RomJobs.compress(5, orochi, Originals.KEEP, RomSpeed.NORMAL, Estimate(15_000_000_000, 16_000_000_000, true), null)!!
        assertEquals(RomFormat.WUA, job.format)
        assertEquals(listOf(game, update), job.originals)
        val script = RomJobPlan.script(job, chdman, work, wua = wua)
        val made = "'$wiiu/.thortools-5/WARRIORS OROCHI 3 HyperNA.wua'"
        assertTrue(script.contains("X='$wua'"))
        assertTrue(script.contains("finish() { rm -rf '$wiiu/.thortools-5'; echo"))
        val check = script.indexOf("[ -f ${quote("$update/code/app.xml")} ] || finish notgame")
        val pack = script.indexOf("\"\$X\" pack -j 6 $made ${quote("0005000010110200_v0=$game")} ${quote("0005000e10110200_v32=$update")}")
        val verify = script.indexOf("\"\$X\" verify $made")
        val compare = script.indexOf("[ -n \"\$sha1\" ] && [ \"\$sha1\" = \"\$sha2\" ] || finish \"verify 1\"")
        val move = script.indexOf("mv $made '${job.output}' || finish \"move 1\"")
        assertTrue(
            listOf(check, pack, verify, compare, move).toString(),
            check in 0 until pack && pack < verify && verify < compare && compare < move,
        )
        assertFalse(script.contains("rm -rf ${quote(game)}"))

        val decoded = RomJobPlan.decode(RomJobPlan.encode(job))!!
        assertEquals(job.links, decoded.links)
    }

    @Test
    fun `a WUA turns back into one folder per title, checked before they move out`() {
        val kart = games.getValue("Mario Kart 8 (US)")
        val job = RomJobs.extract(6, kart, Originals.KEEP, RomSpeed.NORMAL, null)!!
        assertEquals(RomFormat.FOLDERS, job.format)
        assertEquals(6_490_181_211L + RomJobPlan.MARGIN, job.need)
        val script = RomJobPlan.script(job, chdman, work, wua = wua)
        val temp = "$wiiu/.thortools-6"
        val game = "Mario Kart 8 (US) [Game] [000500001010ec00]"
        val extract = script.indexOf("\"\$X\" extract '$wiiu/Mario Kart 8 (US).wua' '$temp' ${quote("000500001010ec00_v1=$game")}")
        val digest = script.indexOf("\"\$X\" digest ${quote("000500001010ec00_v1=$temp/$game")}")
        val move = script.indexOf("mv ${quote("$temp/$game")} ${quote("$wiiu/$game")} || finish \"move 1\"")
        assertTrue(listOf(extract, digest, move).toString(), extract in 0 until digest && digest < move)
        assertTrue(script.contains("finish \"ok 6490181211\""))
    }

    @Test
    fun `Cemu opens WUA and folders, and a WUA estimate comes from the samples`() {
        val cemu = EmulatorChecks.of(RomConsole.WII_U, RomFormat.WUA, listOf(InstalledApp("info.cemu.cemu", "0.5.1")), null)
        assertEquals(true, cemu.single().opens)
        assertEquals(RomFormat.WUA, EmulatorChecks.defaultFormat(RomConsole.WII_U, DiscKind.FOLDER, emptyList(), null))
        val sampled = ShrinkEstimate.folders(1_000_000_000, 0.9)
        assertTrue(sampled.toString(), sampled.middle in 899_000_000..901_000_000)
        assertTrue(ShrinkEstimate.folders(1_000_000_000, null).middle in 810_000_000..830_000_000)
    }
}
