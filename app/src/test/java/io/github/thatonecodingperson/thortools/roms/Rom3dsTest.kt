package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.RomFile
import io.github.thatonecodingperson.thortools.tools.RootFiles.Companion.quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Rom3dsTest {
    private val roms = "/storage/EBBF-779E/Roms"
    private val chdman = "/data/app/~~a==/io.github.thatonecodingperson.thortools-b==/lib/arm64/libchdman.so"
    private val z3ds = "/data/app/~~a==/io.github.thatonecodingperson.thortools-b==/lib/arm64/libz3ds.so"
    private val work = "/data/user/0/io.github.thatonecodingperson.thortools/cache/roms-jobs/5"

    private val fireEmblem = RomFile("$roms/3DS/Fire Emblem - Awakening (USA).cci", 2_147_483_648, 1)
    private val fireEmblemZ = RomFile("$roms/3DS/Fire Emblem - Awakening (USA).zcci", 1_031_609_217, 2)
    private val mario = RomFile("$roms/3DS/Super Mario 3D Land (USA) (En,Fr,Es) (Rev 1).cci", 536_870_912, 1)
    private val gold = RomFile("$roms/3DS/New Super Mario Bros. 2 Gold.legit-decrypted.3ds", 357_490_688, 1)
    private val locked = RomFile("$roms/3DS/Locked.cci", 536_870_912, 1)
    private val alone = RomFile("$roms/3DS/Pilotwings Resort (USA).zcci", 120_000_000, 1)
    private val cxi = RomFile("$roms/3DS/Homebrew Game.cxi", 4_000_000, 1)
    private val driver = RomFile("$roms/3DS/gpu_drivers/Turnip_Gen8_V36(1).zip", 2_764_598, 1)
    private val sdmc = RomFile("$roms/3DS/sdmc/Nintendo 3DS/title.cci", 1_000_000, 1)

    // `z3ds info` on a Thor (the last two lines made up the same way).
    private val info = """
        NCSD	-	decrypted	2147483648	1154982400	${fireEmblem.path}
        Z3DS	NCSD	-	2147483648	1031609057	${fireEmblemZ.path}
        NCSD	-	decrypted	536870912	335029760	${mario.path}
        NCSD	-	decrypted	357490688	357490688	${gold.path}
        NCSD	-	encrypted	536870912	400000000	${locked.path}
        Z3DS	NCSD	-	250000000	119000000	${alone.path}
        NCCH	-	decrypted	4000000	4000000	${cxi.path}
    """.trimIndent()

    private val cards = N3ds.parseInfo(info)

    private val games = RomSets.games(
        RomConsole.N3DS,
        listOf(fireEmblem, fireEmblemZ, mario, gold, locked, alone, cxi, driver, sdmc),
        emptyMap(),
        cards = cards,
    ).associateBy { it.title }

    @Test
    fun `the tool's info lines tell each file's type, crypto and sizes`() {
        assertEquals(CardInfo("NCSD", null, false, 2_147_483_648, 1_154_982_400), cards[fireEmblem.path])
        assertEquals(CardInfo("Z3DS", "NCSD", null, 2_147_483_648, 1_031_609_057), cards[fireEmblemZ.path])
        assertEquals(true, cards[locked.path]?.encrypted)
        assertTrue(cards.getValue(cxi.path).rom)
        assertTrue(cards.getValue(alone.path).packed)
        assertEquals(cards[fireEmblemZ.path], N3ds.decodeInfo(N3ds.encodeInfo(cards[fireEmblemZ.path])))
        assertNull(N3ds.decodeInfo(N3ds.encodeInfo(null)))
    }

    @Test
    fun `3DS games are plain card images, cxi files and Z3DS files, never Azahar's data or other files`() {
        assertEquals(
            setOf(
                "Fire Emblem - Awakening (USA)",
                "Super Mario 3D Land (USA) (En,Fr,Es) (Rev 1)",
                "New Super Mario Bros. 2 Gold.legit-decrypted",
                "Locked",
                "Pilotwings Resort (USA)",
                "Homebrew Game",
            ),
            games.keys,
        )
        val fire = games.getValue("Fire Emblem - Awakening (USA)")
        assertEquals(fireEmblemZ, fire.compressed)
        assertEquals(RomFormat.Z3DS, fire.packed)
        assertTrue(fire.originalsLeft)
        assertEquals(GameProblem.Encrypted, games.getValue("Locked").problem)
        val mario3d = games.getValue("Super Mario 3D Land (USA) (En,Fr,Es) (Rev 1)")
        assertTrue(mario3d.canCompress)
        assertEquals(listOf(RomFormat.Z3DS, RomFormat.ROM_FILE), mario3d.formats)
        assertEquals("$roms/3DS/Super Mario 3D Land (USA) (En,Fr,Es) (Rev 1).zcci", mario3d.output(RomFormat.Z3DS))
        assertEquals("$roms/3DS/New Super Mario Bros. 2 Gold.legit-decrypted.zcci", games.getValue(base(gold)).output(RomFormat.Z3DS))
        assertEquals("$roms/3DS/Homebrew Game.zcxi", games.getValue("Homebrew Game").output(RomFormat.Z3DS))
        val pilotwings = games.getValue("Pilotwings Resort (USA)")
        assertTrue(pilotwings.canConvert)
        assertEquals(250_000_000L, pilotwings.imageSize)
        assertEquals("$roms/3DS/Pilotwings Resort (USA).cci", pilotwings.output(RomFormat.ROM_FILE))
        assertEquals(listOf(RomFormat.ROM_FILE), pilotwings.targets)
    }

    @Test
    fun `a Z3DS file the tool couldn't read is left alone`() {
        val unread = RomSets.games(RomConsole.N3DS, listOf(alone), emptyMap()).single()
        assertEquals(GameProblem.CantRead, unread.problem)
        assertTrue(N3ds.inEmulatorData("$roms/3DS/nand/data/x.cci"))
        assertFalse(N3ds.inEmulatorData("$roms/3DS/Tomodachi Life (USA) (Rev 1)/Tomodachi Life (USA) (Rev 1).cci"))
    }

    @Test
    fun `a ROM becomes Z3DS in its own folder, and moves in only when it unpacks to the same SHA-1`() {
        val mario3d = games.getValue("Super Mario 3D Land (USA) (En,Fr,Es) (Rev 1)")
        val job = RomJobs.compress(5, mario3d, Originals.KEEP, RomSpeed.NORMAL, Estimate(250_000_000, 300_000_000, true), null)!!
        assertEquals(RomFormat.Z3DS, job.format)
        val script = RomJobPlan.script(job, chdman, work, z3ds = z3ds)
        val made = "'$roms/3DS/.thortools-5/Super Mario 3D Land (USA) (En,Fr,Es) (Rev 1).zcci'"
        assertTrue(script.contains("Y='$z3ds'"))
        assertTrue(script.contains("finish() { rm -rf '$roms/3DS/.thortools-5'; echo"))
        val compress = script.indexOf("\"\$Y\" compress -l 12 -j 6 ${quote(mario.path)} $made")
        val sha1 = script.indexOf("sha1=\$(grep 'SHA-1 *:' \"\$W/compress.out\"")
        val verify = script.indexOf("\"\$Y\" verify -j 6 $made")
        val compare = script.indexOf("[ -n \"\$sha1\" ] && [ \"\$sha1\" = \"\$sha2\" ] || finish \"verify 1\"")
        val move = script.indexOf("mv $made '${job.output}' || finish \"move 1\"")
        assertTrue(
            listOf(compress, sha1, verify, compare, move).toString(),
            compress in 0 until sha1 && sha1 < verify && verify < compare && compare < move,
        )
        assertFalse(script.contains("rm -f ${quote(mario.path)}"))
        assertFalse(script.contains("\"\$Z\""))
    }

    @Test
    fun `a Z3DS file turns back into its plain ROM, checked against what was unpacked`() {
        val pilotwings = games.getValue("Pilotwings Resort (USA)")
        val job = RomJobs.extract(6, pilotwings, Originals.KEEP, RomSpeed.FAST, null)!!
        assertEquals(RomFormat.ROM_FILE, job.format)
        assertEquals(RomFormat.Z3DS, job.from)
        assertEquals(250_000_000L + RomJobPlan.MARGIN, job.need)
        val script = RomJobPlan.script(job, chdman, work, z3ds = z3ds)
        val made = "'$roms/3DS/.thortools-6/Pilotwings Resort (USA).cci'"
        val unpack = script.indexOf("\"\$Y\" decompress -j 8 ${quote(alone.path)} $made")
        val check = script.indexOf("\"\$Y\" sha1 $made")
        val move = script.indexOf("mv $made '$roms/3DS/Pilotwings Resort (USA).cci'")
        assertTrue(listOf(unpack, check, move).toString(), unpack in 0 until check && check < move)
    }

    @Test
    fun `cleaning up a 3DS original checks the Z3DS file unpacks to it first`() {
        val fire = games.getValue("Fire Emblem - Awakening (USA)")
        val job = RomJobs.originals(7, fire, Originals.DELETE, RomSpeed.NORMAL, null)!!
        val script = RomJobPlan.script(job, chdman, work, z3ds = z3ds)
        val verify = script.indexOf("\"\$Y\" verify -j 6 ${quote(fireEmblemZ.path)}")
        val sha1 = script.indexOf("\"\$Y\" sha1 ${quote(fireEmblem.path)}")
        val compare = script.indexOf("= \"\$have\" ] || finish \"verify 1\"")
        val delete = script.indexOf("rm -f ${quote(fireEmblem.path)}")
        assertTrue(listOf(verify, sha1, compare, delete).toString(), verify in 0 until sha1 && sha1 < compare && compare < delete)
    }

    @Test
    fun `Azahar opens Z3DS from 2123 on, the Citra family never does`() {
        val azahar = EmulatorChecks.of(
            RomConsole.N3DS,
            RomFormat.Z3DS,
            listOf(InstalledApp("org.azahar_emu.azahar", "2126.2-vanilla")),
            null,
        )
        assertEquals(true, azahar.single().opens)
        val old = EmulatorChecks.of(RomConsole.N3DS, RomFormat.Z3DS, listOf(InstalledApp("io.github.lime3ds.android", "2119.1")), null)
        assertEquals("2123", old.single().needs)
        val citra = EmulatorChecks.of(RomConsole.N3DS, RomFormat.Z3DS, emptyList(), setOf("citra_libretro"))
        assertEquals(false, citra.single().opens)
        assertEquals(
            RomFormat.Z3DS,
            EmulatorChecks.defaultFormat(RomConsole.N3DS, DiscKind.CART, listOf(InstalledApp("org.azahar_emu.azahar", "2126.2")), null),
        )
    }

    @Test
    fun `a 3DS estimate follows the sampled chunks, and a Z3DS file turns back into the whole ROM`() {
        val sampled = ShrinkEstimate.cartridge(536_870_912, 0.5, RomConsole.N3DS, RomFormat.Z3DS)
        assertTrue(sampled.toString(), sampled.middle in 268_000_000..269_000_000)
        val typical = ShrinkEstimate.cartridge(1_000_000_000, null, RomConsole.N3DS, RomFormat.Z3DS)
        assertTrue(typical.toString(), typical.middle in 740_000_000..760_000_000)
        assertEquals(
            250_000_000L,
            ShrinkEstimate.cartridgeConverted(119_000_000, 250_000_000, RomFormat.Z3DS, RomFormat.ROM_FILE, RomConsole.N3DS).middle,
        )
    }

    private fun base(file: RomFile) = file.name.substringBeforeLast('.')
}
