package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiosTableTest {
    private val files = BiosTable.files

    @Test
    fun `every hash is a lowercase MD5 and every size is positive`() {
        val md5 = Regex("^[0-9a-f]{32}$")
        files.forEach { file ->
            file.md5.forEach { assertTrue("${file.path}: $it", md5.matches(it)) }
            file.size?.let { assertTrue(file.path, it > 0) }
        }
    }

    @Test
    fun `each file is listed once and every console has files`() {
        assertEquals(files.size, files.map { it.key }.distinct().size)
        BiosConsole.entries.forEach { console -> assertTrue(console.name, files.any { it.console == console }) }
    }

    @Test
    fun `a group belongs to one console, and so does a rule for files under their own names`() {
        files.mapNotNull { file ->
            file.group?.let { it to file.console }
        }.groupBy({ it.first }, { it.second }).forEach { (group, consoles) ->
            assertEquals(group.name, 1, consoles.distinct().size)
        }
        BiosTable.loose.forEach { rule ->
            assertTrue(rule.group.name, files.any { it.group == rule.group && it.console == rule.console })
        }
    }

    @Test
    fun `the PS2 BIOS files go where LRPS2 looks, recognised by their contents`() {
        val ps2 = files.filter { it.console == BiosConsole.PS2 }
        assertTrue(ps2.size > 50)
        assertTrue(ps2.all { it.folder == "pcsx2/bios" && it.anyName && it.size == 4L * 1024 * 1024 && it.md5.size == 1 })
        assertEquals("pcsx2/bios", BiosTable.loose.single { it.console == BiosConsole.PS2 }.folder)
    }

    @Test
    fun `the files of common cores are known by their hashes`() {
        fun md5(path: String) = files.single { it.path == path }.md5
        assertEquals(setOf("490f666e1afb15b7362b406ed1cea246"), md5("scph5501.bin"))
        assertEquals(setOf("a860e8c0b6d573d191e4ec7db1b1e4f6"), md5("gba_bios.bin"))
        assertEquals(setOf("ca30b50f880eb660a320674ed365ef7a"), md5("disksys.rom"))
        assertEquals(setOf("df692a80a5b1bc90728bc3dfc76cd948"), md5("bios7.bin"))
        assertEquals(setOf("8d3d9f294b6e174bc7b1d2fd1c727530"), md5("Mupen64plus/IPL.n64"))
        assertEquals(2, md5("bios_CD_U.bin").size)
        assertTrue(md5("firmware.bin").isEmpty())
        assertTrue(md5("fbneo/neogeo.zip").isEmpty())
    }
}
