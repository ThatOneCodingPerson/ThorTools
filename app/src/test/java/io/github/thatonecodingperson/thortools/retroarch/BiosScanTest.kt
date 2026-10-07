package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiosScanTest {
    private val assets = "/storage/EBBF-779E/AssetsEmu/bios"
    private val emuDeck = "/storage/EBBF-779E/bios/EmuDeck_2.3.8_BIOS_Pack"
    private val system = "/data/media/0/Android/media/com.retroarch.aarch64/RetroArch/system"

    private val ps2Europe = "dc752f160044f2ed5fc1f4964db2a095"
    private val unknownPs2 = "0123456789abcdef0123456789abcdef"
    private val ps1Usa = "490f666e1afb15b7362b406ed1cea246"
    private val ps1Europe = "32736f17079d0b2b7024407c39bd3050"

    /** BIOS files on an SD card as they come, a game image among them, and a wrong scph5502.bin in RetroArch's folder. */
    private val output = """
        D 0 $assets
        F 0 4194304 $assets/SCPH-70004.BIN
        F 0 4194304 $assets/SCPH-70008_BIOS_V12_RUS_200.BIN
        F 0 524288 $assets/Milo's psx bios.bin
        F 0 524288 $assets/scph5502.bin
        F 0 734003200 $assets/Crash Bandicoot (USA).bin
        D 1 $emuDeck
        F 1 524288 $emuDeck/scph1000.bin
        F 1 16384 $emuDeck/GBA/gba_bios.bin
        F 1 4194304 $emuDeck/SCPH-70008_BIOS_V12_RUS_200.BIN
        F 1 262144 $emuDeck/firmware.bin
        F 1 16384 $emuDeck/bios7.bin
        F 1 131072 $emuDeck/bios_CD_U.bin
        find: /storage/EBBF-779E/bios/lost+found: Permission denied
        T $system
        S 524288 $system/scph5502.bin
    """.trimIndent()

    private val md5 = mapOf(
        "$assets/SCPH-70004.BIN" to ps2Europe,
        "$assets/SCPH-70008_BIOS_V12_RUS_200.BIN" to unknownPs2,
        "$emuDeck/SCPH-70008_BIOS_V12_RUS_200.BIN" to unknownPs2,
        "$assets/Milo's psx bios.bin" to ps1Usa,
        "$assets/scph5502.bin" to ps1Europe,
        "$emuDeck/scph1000.bin" to "239665b1a3dade1b5a52c06338011044",
        "$emuDeck/GBA/gba_bios.bin" to "a860e8c0b6d573d191e4ec7db1b1e4f6",
        "$emuDeck/firmware.bin" to "ffffffffffffffffffffffffffffffff",
        "$emuDeck/bios7.bin" to "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
        "$emuDeck/bios_CD_U.bin" to "e66fa1dc5820d254611fdcdba0662372",
        "$system/scph5502.bin" to "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    )

    private val listing = BiosScan.parseListing(output, folderCount = 3)
    private val entries = BiosScan.match(listing, md5)

    private fun entry(path: String) = entries.single { it.file.path == path }

    @Test
    fun `the listing gives each folder where root found it and its files, and RetroArch's folder`() {
        assertEquals(listOf(assets, emuDeck, null), listing.folders)
        assertEquals(listOf(5, 6, 0), listing.counts)
        assertEquals(system, listing.system)
        assertEquals(listOf(ListedFile("$system/scph5502.bin", 524288)), listing.systemFiles)
        assertTrue(ListedFile("$assets/Milo's psx bios.bin", 524288) in listing.found)
    }

    @Test
    fun `only files with a BIOS size or name are hashed, never a game image`() {
        val hashed = BiosScan.toHash(listing)
        assertFalse("$assets/Crash Bandicoot (USA).bin" in hashed)
        assertTrue("$assets/SCPH-70004.BIN" in hashed)
        assertTrue("$emuDeck/firmware.bin" in hashed)
        assertTrue("$system/scph5502.bin" in hashed)
    }

    @Test
    fun `md5sum lines give each file's hash, names with spaces too`() {
        val parsed = BiosScan.parseMd5(
            "490F666E1AFB15B7362B406ED1CEA246  $assets/Milo's psx bios.bin\n" +
                "a860e8c0b6d573d191e4ec7db1b1e4f6 *$emuDeck/GBA/gba_bios.bin\n" +
                "md5sum: $assets/gone.bin: No such file or directory",
        )
        assertEquals(
            mapOf("$assets/Milo's psx bios.bin" to ps1Usa, "$emuDeck/GBA/gba_bios.bin" to "a860e8c0b6d573d191e4ec7db1b1e4f6"),
            parsed,
        )
    }

    @Test
    fun `the scripts quote every path`() {
        val script = BiosScan.listScript(listOf(listOf("/storage/X/Milo's bios")), system)
        assertTrue(script.contains("'/storage/X/Milo'\\''s bios'"))
        assertTrue(script.contains("-maxdepth 3"))
        assertEquals("md5sum '/a b.bin' '/c.bin'", BiosScan.md5Script(listOf("/a b.bin", "/c.bin")))
    }

    @Test
    fun `a BIOS is recognised by its hash whatever its name, and ticked`() {
        val usa = entry("scph5501.bin")
        assertNull(usa.installed)
        assertEquals(listOf(BiosCandidate("$assets/Milo's psx bios.bin", 524288, ps1Usa, BiosFit.CHECKED)), usa.candidates)
        assertEquals("$assets/Milo's psx bios.bin", BiosPlan.defaultTick(usa))

        val ps2 = entry("pcsx2/bios/ps2-0200e-20040614.bin")
        assertEquals(BiosFit.CHECKED, ps2.candidates.single().fit)
        assertEquals("$assets/SCPH-70004.BIN", BiosPlan.defaultTick(ps2))
        assertEquals(BiosFit.CHECKED, entry("scph1000.bin").candidates.single().fit)
        assertEquals(BiosFit.CHECKED, entry("gba_bios.bin").candidates.single().fit)
    }

    @Test
    fun `a wrong file in RetroArch's folder shows, and the right one found isn't ticked over it`() {
        val europe = entry("scph5502.bin")
        assertEquals(BiosInstalled("$system/scph5502.bin", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", BiosFit.OTHER_FILE), europe.installed)
        assertEquals(BiosFit.CHECKED, europe.candidates.single().fit)
        assertNull(BiosPlan.defaultTick(europe))
    }

    @Test
    fun `a PS2 BIOS of unknown contents is offered under its own name, found twice, not ticked`() {
        val own = entry("pcsx2/bios/SCPH-70008_BIOS_V12_RUS_200.BIN")
        assertEquals(BiosConsole.PS2, own.file.console)
        assertEquals(BiosGroup.PS2, own.file.group)
        assertEquals(
            listOf("$assets/SCPH-70008_BIOS_V12_RUS_200.BIN", "$emuDeck/SCPH-70008_BIOS_V12_RUS_200.BIN"),
            own.candidates.map { it.path },
        )
        assertTrue(own.candidates.all { it.fit == BiosFit.NAME_AND_SIZE })
        assertNull(BiosPlan.defaultTick(own))
        assertFalse(entries.any { it.file.path == "pcsx2/bios/SCPH-70004.BIN" })
    }

    @Test
    fun `a name alone tells only for files whose contents vary`() {
        assertEquals(BiosFit.SAME_NAME, entry("firmware.bin").candidates.single().fit)
        assertNull(BiosPlan.defaultTick(entry("firmware.bin")))
        assertEquals(BiosFit.OTHER_FILE, entry("bios7.bin").candidates.single().fit)
        // A known file under another file's name belongs where its hash does.
        assertTrue(entry("bios_CD_U.bin").candidates.isEmpty())
        assertEquals("$emuDeck/bios_CD_U.bin", entry("bios_CD_E.bin").candidates.single().path)
    }

    @Test
    fun `a console's card lists a group's files only when seen, and a needed group none of whose files was`() {
        val ps2Rows = BiosPlan.rows(entries.filter { it.file.console == BiosConsole.PS2 })
        assertEquals(
            listOf("pcsx2/bios/ps2-0200e-20040614.bin", "pcsx2/bios/SCPH-70008_BIOS_V12_RUS_200.BIN"),
            ps2Rows.map { (it as BiosRow.File).entry.file.path },
        )
        val nothing = BiosScan.match(BiosScan.parseListing("", 0), emptyMap())
        assertEquals(listOf(BiosRow.Missing(BiosGroup.PS2)), BiosPlan.rows(nothing.filter { it.file.console == BiosConsole.PS2 }))
        val ps1Rows = BiosPlan.rows(nothing.filter { it.file.console == BiosConsole.PS1 })
        assertEquals(listOf("scph5500.bin", "scph5501.bin", "scph5502.bin"), ps1Rows.map { (it as BiosRow.File).entry.file.path })
    }

    @Test
    fun `of PS2 BIOSes, where one is enough, only the newest found is ticked, and none once one is chosen or there`() {
        val ps2 = """
            D 0 $assets
            F 0 4194304 $assets/SCPH-10000.bin
            F 0 4194304 $assets/SCPH-39001.bin
            F 0 4194304 $assets/SCPH-70004.BIN
            T $system
        """.trimIndent()
        val hashes = mapOf(
            "$assets/SCPH-10000.bin" to "acf4730ceb38ac9d8c7d8e21f2614600",
            "$assets/SCPH-39001.bin" to "d5ce2c7d119f563ce04bc04dbc3a323e",
            "$assets/SCPH-70004.BIN" to ps2Europe,
        )
        val found = BiosScan.match(BiosScan.parseListing(ps2, folderCount = 1), hashes).filter { it.file.group == BiosGroup.PS2 }
        val checked = found.filter { entry -> entry.candidates.any { it.fit == BiosFit.CHECKED } }
        assertEquals(3, checked.size)
        val newest = checked.maxBy { it.file.name.lowercase() }

        val ticks = BiosPlan.ticks(found, emptyMap())
        assertEquals(setOf(newest.key), ticks.keys)

        val rows = BiosPlan.folded(found, ticks)
        assertEquals(newest, (rows.first() as BiosRow.File).entry)
        val others = rows.filterIsInstance<BiosRow.Others>().single()
        assertEquals(BiosGroup.PS2, others.group)
        assertEquals(found.count { it.seen } - 1, others.entries.size)
        assertFalse(newest in others.entries)

        val other = checked.first { it != newest }
        val chosen = BiosPlan.ticks(found, mapOf(other.key to other.candidates.first().path))
        assertEquals(setOf(other.key), chosen.keys)
    }

    @Test
    fun `the user's ticks win while the file is still found`() {
        val untouched = BiosPlan.ticks(entries, emptyMap())
        assertEquals("$assets/Milo's psx bios.bin", untouched["scph5501.bin"])
        assertNull(untouched["firmware.bin"])

        val own = "pcsx2/bios/scph-70008_bios_v12_rus_200.bin"
        val chosen = BiosPlan.ticks(
            entries,
            mapOf("scph5501.bin" to null, own to "$emuDeck/SCPH-70008_BIOS_V12_RUS_200.BIN", "gba_bios.bin" to "/gone/gba_bios.bin"),
        )
        assertNull(chosen["scph5501.bin"])
        assertEquals("$emuDeck/SCPH-70008_BIOS_V12_RUS_200.BIN", chosen[own])
        assertEquals("$emuDeck/GBA/gba_bios.bin", chosen["gba_bios.bin"])
    }

    @Test
    fun `copies go under the cores' names, with their subfolders made first`() {
        val ticks = mapOf(
            "scph5501.bin" to "$assets/Milo's psx bios.bin",
            "pcsx2/bios/ps2-0200e-20040614.bin" to "$assets/SCPH-70004.BIN",
            "scph5502.bin" to "$assets/scph5502.bin",
        )
        val copies = BiosPlan.copies(entries, ticks, system, makeRoot = false)
        assertEquals(3, copies.size)
        val usa = copies.single { it.file.path == "scph5501.bin" }
        assertEquals("$system/scph5501.bin", usa.to)
        assertEquals(emptyList<String>(), usa.dirs)
        assertFalse(usa.replaces)
        val ps2 = copies.single { it.file.path.startsWith("pcsx2") }
        assertEquals("$system/pcsx2/bios/ps2-0200e-20040614.bin", ps2.to)
        assertEquals(listOf("$system/pcsx2", "$system/pcsx2/bios"), ps2.dirs)
        assertTrue(copies.single { it.file.path == "scph5502.bin" }.replaces)

        val made = BiosPlan.copies(entries, ticks, system, makeRoot = true).single { it.file.path.startsWith("pcsx2") }
        assertEquals(listOf(system, "$system/pcsx2", "$system/pcsx2/bios"), made.dirs)
    }

    @Test
    fun `a file RetroArch's folder has already is nothing to copy, under any name when the core goes by contents`() {
        val output = """
            D 0 $assets
            F 0 4194304 $assets/SCPH-70004.BIN
            F 0 16384 $assets/gba_bios.bin
            T $system
            S 4194304 $system/pcsx2/bios/SCPH-70004.BIN
            S 16384 $system/GBA_BIOS.BIN
        """.trimIndent()
        val hashes = mapOf(
            "$assets/SCPH-70004.BIN" to ps2Europe,
            "$system/pcsx2/bios/SCPH-70004.BIN" to ps2Europe,
            "$assets/gba_bios.bin" to "a860e8c0b6d573d191e4ec7db1b1e4f6",
            "$system/GBA_BIOS.BIN" to "a860e8c0b6d573d191e4ec7db1b1e4f6",
        )
        val matched = BiosScan.match(BiosScan.parseListing(output, 1), hashes)
        val ps2 = matched.single { it.file.path == "pcsx2/bios/ps2-0200e-20040614.bin" }
        assertEquals(BiosInstalled("$system/pcsx2/bios/SCPH-70004.BIN", ps2Europe, BiosFit.CHECKED, atPath = false), ps2.installed)
        assertTrue(ps2.candidates.isEmpty())
        val gba = matched.single { it.file.path == "gba_bios.bin" }
        assertEquals(BiosFit.CHECKED, gba.installed?.fit)
        assertTrue(gba.candidates.isEmpty())
        assertFalse(matched.any { it.file.path == "pcsx2/bios/SCPH-70004.BIN" })
    }

    @Test
    fun `files inside RetroArch's own folder are never offered as copies`() {
        val output = """
            D 0 $system
            F 0 524288 $system/scph5501.bin
            T $system
            S 524288 $system/scph5501.bin
        """.trimIndent()
        val matched = BiosScan.match(BiosScan.parseListing(output, 1), mapOf("$system/scph5501.bin" to ps1Usa))
        val usa = matched.single { it.file.path == "scph5501.bin" }
        assertEquals(BiosFit.CHECKED, usa.installed?.fit)
        assertTrue(usa.candidates.isEmpty())
    }
}
