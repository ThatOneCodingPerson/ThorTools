package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.CheatSystem
import io.github.thatonecodingperson.thortools.retroarch.CheatSystems
import io.github.thatonecodingperson.thortools.retroarch.RomFile
import io.github.thatonecodingperson.thortools.tools.RootFiles.Companion.quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RomClassicsTest {
    private val roms = "/storage/EBBF-779E/Roms"
    private val chdman = "/data/app/~~a==/io.github.thatonecodingperson.thortools-b==/lib/arm64/libchdman.so"
    private val sevenZip = "/data/app/~~a==/io.github.thatonecodingperson.thortools-b==/lib/arm64/lib7zz.so"
    private val work = "/data/user/0/io.github.thatonecodingperson.thortools/cache/roms-jobs/5"

    // `7zz l -slt -ba` of real archives, read on a Thor.
    private val gba7z = """
        Path = Mario Kart - Super Circuit (USA).gba
        Size = 4194304
        Packed Size = 1819893
        Modified = 1996-12-24 23:32:00.0000000
        Attributes = N
        CRC = ED316E37
        Encrypted = -
        Method = LZMA2:22
        Block = 0
    """.trimIndent()
    private val ndsZip = """
        Path = Pokemon - Platinum Version (USA) (Rev 1).nds
        Folder = -
        Size = 134217728
        Packed Size = 50777700
        Modified = 2016-11-12 16:45:00
        CRC = 69D628E8
        Method = Deflate

        Path = Vimm's Lair.txt
        Folder = -
        Size = 284
        Packed Size = 200
        CRC = 37C2E8F0
        Method = Deflate
    """.trimIndent()
    private val hdPack = """
        Path = ZeldaHDAlternateGraphics
        Folder = +
        Size = 0
        Packed Size = 0
        Attributes = D
        Comment =
        CRC =
        Method = Store

        Path = ZeldaHDAlternateGraphics/LinkCustomRunningDarkBlueSmallShield.png
        Folder = -
        Size = 45380
        Packed Size = 31291
        Attributes = A
        Comment =
        CRC = 7F7546CC
        Method = Deflate:Maximum
    """.trimIndent()

    private fun file(path: String, size: Long, modified: Long = 0L) = RomFile(path, size, modified)

    @Test
    fun `7-Zip's listings give each file's size and CRC, and tell one game from anything else`() {
        val gba = ArchiveListings.parse(gba7z)!!
        assertEquals(listOf(ArchiveEntry("Mario Kart - Super Circuit (USA).gba", 4_194_304, "ED316E37")), gba.entries)
        val nds = ArchiveListings.parse(ndsZip)!!
        assertEquals("Pokemon - Platinum Version (USA) (Rev 1).nds", ArchiveListings.rom(nds, listOf("nds"))?.name)
        assertEquals(
            "134217728 69D628E8 Pokemon - Platinum Version (USA) (Rev 1).nds\n284 37C2E8F0 Vimm's Lair.txt",
            ArchiveListings.signature(nds),
        )
        val pack = ArchiveListings.parse(hdPack)!!
        assertTrue(pack.entries.first().folder)
        assertNull(pack.entries.first().crc)
        assertNull(ArchiveListings.rom(pack, listOf("nes", "unf", "unif")))
        val two = ArchiveListing(listOf(ArchiveEntry("A.gb", 10, "1"), ArchiveEntry("B.gb", 10, "2")))
        assertNull(ArchiveListings.rom(two, listOf("gb")))
        assertNull(ArchiveListings.parse("ERROR: x.zip : Cannot open the file as archive\n"))
    }

    @Test
    fun `classic consoles are told apart by folder and by their own extensions`() {
        assertEquals(CheatSystem.POKEMON_MINI, CheatSystems.of("$roms/Misc/Pokemon Pinball Mini (Japan).min"))
        assertEquals(CheatSystem.ODYSSEY2, CheatSystems.of("$roms/odyssey2/K.C. Munchkin! (USA).bin"))
        assertEquals(CheatSystem.MEGA_DUCK, CheatSystems.of("$roms/megaduck/Some Game.zip"))
        assertEquals(CheatSystem.N64, CheatSystems.of("$roms/N64/Super Mario 64 (USA).n64.zip"))
        assertTrue(RomConsole.GB.cartridges)
        assertFalse(RomConsole.PS2.cartridges)
        assertEquals(DiscKind.CART, RomConsole.NDS.listKind)
        assertEquals(35, RomConsole.entries.count { it.cartridges })
    }

    private val red = file("$roms/GB/Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb", 1_048_576)
    private val kirby = file("$roms/GB/Kirby's Dream Land (USA, Europe).gb", 262_144, modified = 1)
    private val kirbyZip = file("$roms/GB/Kirby's Dream Land (USA, Europe).zip", 120_000, modified = 2)
    private val gbFiles = listOf(red, kirby, kirbyZip, file("$roms/GB/gb_bios.bin", 256), file("$roms/GB/Saves/Kirby.srm", 8192))
    private val gbListings = mapOf(
        kirbyZip.path to ArchiveListing(listOf(ArchiveEntry("Kirby's Dream Land (USA, Europe).gb", 262_144, "1234ABCD"))),
    )
    private val gb = RomSets.games(RomConsole.GB, gbFiles, emptyMap(), listings = gbListings).associateBy { it.title }

    private val ndsArchive = file("$roms/NDS/Pokemon - Platinum Version (USA) (Rev 1).zip", 50_778_279)
    private val n64Empty = file("$roms/N64/Mario Golf (USA).n64.zip", 0)
    private val n64 = file("$roms/N64/Paper Mario (USA).n64.zip", 33_184_574)
    private val pack = file("$roms/NES/Zelda Remastered HD Pack (v1.3).zip", 187_345_689)
    private val unread = file("$roms/NES/Excitebike.zip", 17_221)

    @Test
    fun `cartridge games are plain ROMs and one-game archives, nothing else`() {
        assertEquals(setOf("Pokemon - Red Version (USA, Europe) (SGB Enhanced)", "Kirby's Dream Land (USA, Europe)"), gb.keys)
        val redGame = gb.getValue("Pokemon - Red Version (USA, Europe) (SGB Enhanced)")
        assertTrue(redGame.canCompress)
        assertEquals(RomFormat.ROM_FILE, redGame.current)
        assertEquals(listOf(RomFormat.ZIP, RomFormat.SEVEN_Z), redGame.targets)
        assertEquals("$roms/GB/Pokemon - Red Version (USA, Europe) (SGB Enhanced).zip", redGame.output(RomFormat.ZIP))
        // The ROM beside its zip: the zip is the game, the ROM its original.
        val kirbyGame = gb.getValue("Kirby's Dream Land (USA, Europe)")
        assertTrue(kirbyGame.originalsLeft)
        assertEquals(kirbyZip, kirbyGame.compressed)

        val nds = RomSets.games(
            RomConsole.NDS,
            listOf(ndsArchive),
            emptyMap(),
            listings = mapOf(ndsArchive.path to ArchiveListings.parse(ndsZip)),
        ).single()
        assertTrue(nds.canConvert)
        assertTrue(nds.notCompressed.not())
        assertEquals(RomFormat.ZIP, nds.current)
        assertEquals(listOf(RomFormat.SEVEN_Z, RomFormat.ROM_FILE), nds.targets)
        assertEquals(134_217_728L, nds.imageSize)
        assertEquals("$roms/NDS/Pokemon - Platinum Version (USA) (Rev 1).nds", nds.output(RomFormat.ROM_FILE))

        val n64Games = RomSets.games(
            RomConsole.N64,
            listOf(n64Empty, n64),
            emptyMap(),
            listings = mapOf(n64Empty.path to null),
        ).associateBy {
            it.title
        }
        assertEquals(GameProblem.Empty, n64Games.getValue("Mario Golf (USA).n64").problem)
        assertEquals(GameProblem.CantRead, n64Games.getValue("Paper Mario (USA).n64").problem)

        val nes = RomSets.games(
            RomConsole.NES,
            listOf(pack, unread),
            emptyMap(),
            listings = mapOf(
                pack.path to ArchiveListings.parse(hdPack),
            ),
        ).associateBy { it.title }
        assertEquals(GameProblem.NotOneGame, nes.getValue("Zelda Remastered HD Pack (v1.3)").problem)
        assertEquals(GameProblem.CantRead, nes.getValue("Excitebike").problem)
        assertFalse(nes.getValue("Zelda Remastered HD Pack (v1.3)").isCompressed)
        assertFalse(n64Games.getValue("Mario Golf (USA).n64").isCompressed)
        assertTrue(nds.isCompressed)
    }

    @Test
    fun `a bin is a ROM only where the console's cores take it, never a BIOS or a sheet's track`() {
        val files = listOf(
            file("$roms/genesis/Sonic (USA).bin", 524_288),
            file("$roms/genesis/Sonic CD (USA).cue", 100),
            file("$roms/genesis/Sonic CD (USA) (Track 1).bin", 30_000_000),
            file("$roms/genesis/bios_MD.bin", 2048),
        )
        val texts = mapOf("$roms/genesis/Sonic CD (USA).cue" to "FILE \"Sonic CD (USA) (Track 1).bin\" BINARY\n  TRACK 01 MODE1/2352\n")
        assertEquals(listOf("Sonic (USA)"), RomSets.games(RomConsole.GENESIS, files, texts).map { it.title })
        assertTrue(RomSets.games(RomConsole.SNES, listOf(file("$roms/SNES/Game.bin", 1_000)), emptyMap()).isEmpty())
    }

    private fun model(console: RomConsole, games: List<DiscGame>, cores: Set<String>?, installed: List<InstalledApp> = emptyList()) =
        RomUiModel(
            shelf = ShelfState(games = mapOf(console to games), installed = installed, cores = cores),
            console = console,
            filter = GameFilter.ALL,
            filterSet = true,
        )

    @Test
    fun `ZIP is the default, the list can make 7z, and what each emulator opens is known`() {
        val state = model(RomConsole.GB, gb.values.toList(), setOf("sameboy_libretro"))
        assertEquals(RomFormat.ZIP, state.listFormat(RomConsole.GB))
        assertEquals(
            RomFormat.ZIP,
            model(
                RomConsole.GB,
                gb.values.toList(),
                null,
                listOf(InstalledApp("com.swordfish.lemuroid", "1.17")),
            ).listFormat(RomConsole.GB),
        )
        val lemuroid = EmulatorChecks.of(RomConsole.GBA, RomFormat.SEVEN_Z, listOf(InstalledApp("com.swordfish.lemuroid", "1.17")), null)
        assertEquals(Support.NO, lemuroid.single().support)
        val oldBoy = EmulatorChecks.of(RomConsole.GB, RomFormat.ZIP, listOf(InstalledApp("com.fastemulator.gbc", "2.0.0")), null).single()
        assertTrue(oldBoy.unlisted)
        val melon = EmulatorChecks.of(
            RomConsole.NDS,
            RomFormat.SEVEN_Z,
            listOf(InstalledApp("me.magnum.melondualds", "0.7.0.rc5")),
            setOf("melondsds_libretro"),
        )
        assertEquals(listOf(Support.DOCUMENTED, Support.DOCUMENTED), melon.map { it.support })
        // 7z of a DS zip is about half of it; a plain ROM's zip follows what Deflate made of it.
        val sevenZ = ShrinkEstimate.cartridgeConverted(50_000_000, 134_217_728, RomFormat.ZIP, RomFormat.SEVEN_Z, RomConsole.NDS)
        assertTrue(sevenZ.toString(), abs(sevenZ.middle - 22_500_000) <= 1)
        assertEquals(
            134_217_728L,
            ShrinkEstimate.cartridgeConverted(50_000_000, 134_217_728, RomFormat.ZIP, RomFormat.ROM_FILE, RomConsole.NDS).middle,
        )
        val zip = ShrinkEstimate.cartridge(1_048_576, 0.35, RomConsole.GB, RomFormat.ZIP)
        assertTrue(zip.toString(), abs(zip.middle - 367_001) <= 1)
        assertTrue(ShrinkEstimate.cartridge(1_048_576, 0.35, RomConsole.GB, RomFormat.SEVEN_Z).middle < zip.middle)
    }

    @Test
    fun `a plain ROM goes into a zip from its own folder, checked by size and CRC before it moves in`() {
        val redGame = gb.getValue("Pokemon - Red Version (USA, Europe) (SGB Enhanced)")
        val job = RomJobs.compress(5, redGame, Originals.KEEP, RomSpeed.NORMAL, Estimate(300_000, 400_000, true), null)!!
        assertEquals(RomFormat.ZIP, job.format)
        val script = RomJobPlan.script(job, chdman, work, sevenZip = sevenZip)
        val temp = "'$roms/GB/.thortools-5'"
        val made = "'$roms/GB/.thortools-5/Pokemon - Red Version (USA, Europe) (SGB Enhanced).zip'"
        assertTrue(script.contains("Z='$sevenZip'"))
        assertTrue(script.contains("finish() { rm -rf $temp; echo"))
        val want = script.indexOf("want=\$(romsig '${red.path}' 'Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb')")
        val folder = script.indexOf("cd '$roms/GB' || finish \"folder 2\"")
        val compress = script.indexOf(
            "\"\$Z\" a -tzip -mx=7 -mmt=6 -bsp2 -bso1 $made -- 'Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb'",
        )
        val test = script.indexOf("\"\$Z\" t -bsp2 -bso1 $made")
        val compare = script.indexOf("[ -n \"\$want\" ] && [ \"\$got\" = \"\$want\" ] || finish \"verify 1\"")
        val move = script.indexOf("mv $made '${job.output}' || finish \"move 1\"")
        assertTrue(
            listOf(want, folder, compress, test, compare, move).toString(),
            want in 0 until folder && folder < compress && compress < test && test < compare && compare < move,
        )
        assertTrue(script.contains("sig() { \"\$Z\" l -slt -ba \"\$1\" 2>/dev/null | awk -F ' = '"))
        assertFalse(script.contains("rm -f '${red.path}'"))
    }

    @Test
    fun `a zip becomes a 7z through its unpacked files, and an archive turns back into its ROM`() {
        val nds = RomSets.games(
            RomConsole.NDS,
            listOf(ndsArchive),
            emptyMap(),
            listings = mapOf(
                ndsArchive.path to ArchiveListings.parse(ndsZip),
            ),
        ).single()
        val job = RomJobs.convert(
            5,
            nds,
            RomFormat.SEVEN_Z,
            Originals.DELETE,
            RomSpeed.NORMAL,
            Estimate(20_000_000, 25_000_000, false),
            null,
        )!!
        assertEquals(134_217_728L + 25_000_000 + RomJobPlan.MARGIN, job.need)
        val script = RomJobPlan.script(job, chdman, work, sevenZip = sevenZip)
        val unpack = script.indexOf("\"\$Z\" x -y -bsp2 -bso1 -o'$roms/NDS/.thortools-5/x' -- '${ndsArchive.path}'")
        val pack = script.indexOf(
            "\"\$Z\" a -t7z -m0=LZMA2 -mx=9 -md=64m -mmt=6 -bsp2 -bso1 '$roms/NDS/.thortools-5/Pokemon - Platinum Version (USA) (Rev 1).7z' -- *",
        )
        val delete = script.indexOf("rm -f '${ndsArchive.path}'")
        assertTrue(listOf(unpack, pack, delete).toString(), unpack in 0 until pack && pack < delete)

        val back = RomJobs.extract(6, nds, Originals.KEEP, RomSpeed.NORMAL, null)!!
        assertEquals(RomFormat.ROM_FILE, back.format)
        assertEquals("$roms/NDS/Pokemon - Platinum Version (USA) (Rev 1).nds", back.output)
        val backScript = RomJobPlan.script(back, chdman, work, sevenZip = sevenZip)
        assertTrue(
            backScript.contains(
                "x -y -bsp2 -bso1 -o'$roms/NDS/.thortools-6/x' -- '${ndsArchive.path}' 'Pokemon - Platinum Version (USA) (Rev 1).nds'",
            ),
        )
        assertTrue(backScript.contains("sig '${ndsArchive.path}' | grep -F -x -- \"\$got\" >/dev/null || finish \"verify 1\""))
    }

    @Test
    fun `cleaning up the plain ROM checks it is in the archive as it is first`() {
        val kirbyGame = gb.getValue("Kirby's Dream Land (USA, Europe)")
        val job = RomJobs.originals(5, kirbyGame, Originals.DELETE, RomSpeed.NORMAL, null)!!
        val script = RomJobPlan.script(job, chdman, work, sevenZip = sevenZip)
        val check = script.indexOf("grep -F -x -- \"\$(romsig ${quote(kirby.path)} ${quote(kirby.name)})\"")
        val delete = script.indexOf("rm -f ${quote(kirby.path)}")
        assertTrue(listOf(check, delete).toString(), check in 0 until delete)
    }

    @Test
    fun `kept listings survive a restart`() {
        val listings = mapOf(
            "${ndsArchive.path}\t50778279\t7" to ArchiveListings.parse(ndsZip),
            "${n64Empty.path}\t0\t0" to null,
        )
        assertEquals(listings, RomShelf.decodeListings(RomShelf.encodeListings(listings)))
    }

    @Test
    fun `7-Zip's progress is read through its backspaces, packing, checking and unpacking`() {
        val wipe = "\b".repeat(39) + " ".repeat(39) + "\b".repeat(39)
        val packing = "  0M Scan\b\b\b\b\b\b\b\b\b         \b\b\b\b\b\b\b\b\b  0%\b\b\b\b    \b\b\b\b  1% + c.bin$wipe" +
            " 97% 1 + New Super Mario Bros. U (US).wua$wipe 99% 2 + c.bin$wipe"
        assertEquals(ChdProgress(ChdProgress.Phase.COMPRESSING, 99.0), ChdTool.progress(packing))
        assertEquals(23.0, ChdTool.progress("  0%\b\b\b\b    \b\b\b\b 23% T New Super Mario Bros. U (US).wua$wipe")!!.percent, 0.0)
        assertEquals(48.0, ChdTool.progress(" 12% - game.gb$wipe 48% - game.gb$wipe")!!.percent, 0.0)
        assertNull(ChdTool.progress("  0M Scan\b\b\b\b\b\b\b\b\b         \b\b\b\b\b\b\b\b\b"))
    }
}
