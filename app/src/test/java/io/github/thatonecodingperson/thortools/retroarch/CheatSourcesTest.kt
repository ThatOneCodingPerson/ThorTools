package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CheatSourcesTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** The shape of GitHub's contents API answer for `cht` (cut down). */
    private val systemsJson = """
        [
          {"name": "Nintendo - Nintendo 64", "path": "cht/Nintendo - Nintendo 64", "sha": "e23e632db6b4c481e9b62bc84562e23ad97e475a",
           "size": 0, "download_url": null, "type": "dir", "_links": {"self": "x"}},
          {"name": "README.md", "path": "cht/README.md", "sha": "abc", "size": 10, "type": "file"}
        ]
    """.trimIndent()

    /** The shape of GitHub's git trees API answer for a console's folder (cut down). */
    private val treeJson = """
        {"sha": "e23e632db6b4c481e9b62bc84562e23ad97e475a", "url": "x", "tree": [
          {"path": "007 - The World Is Not Enough (Europe) (En,Fr,De).cht", "mode": "100644", "type": "blob",
           "sha": "3fced6b340d53ed9fec180415279b8cec4faf6a5", "size": 191878, "url": "y"},
          {"path": "Pokémon Snap (USA).cht", "mode": "100644", "type": "blob", "sha": "1111", "size": 190, "url": "y"},
          {"path": "README.md", "mode": "100644", "type": "blob", "sha": "2222", "size": 12, "url": "y"},
          {"path": "sub", "mode": "040000", "type": "tree", "sha": "3333", "url": "y"}
        ], "truncated": false}
    """.trimIndent()

    @Test
    fun `GitHub's answers give the console folders and their cheat files`() {
        assertEquals(
            mapOf("Nintendo - Nintendo 64" to "e23e632db6b4c481e9b62bc84562e23ad97e475a"),
            GitHubCheatsApi.parseSystems(systemsJson),
        )
        val files = GitHubCheatsApi.parseTree(treeJson)
        assertEquals(
            listOf(
                CheatFileRef("007 - The World Is Not Enough (Europe) (En,Fr,De).cht", 191878, "3fced6b340d53ed9fec180415279b8cec4faf6a5"),
                CheatFileRef("Pokémon Snap (USA).cht", 190, "1111"),
            ),
            files,
        )
        assertEquals(files, GitHubCheatsApi.decodeTree(GitHubCheatsApi.encodeTree(files)))
    }

    @Test
    fun `raw file addresses are encoded part by part`() {
        assertEquals(
            "https://raw.githubusercontent.com/libretro/libretro-database/master/cht/" +
                "Nintendo%20-%20Nintendo%2064/Castlevania%20%28USA%29.cht",
            GitHubCheatsApi.rawUrl("Nintendo - Nintendo 64", "Castlevania (USA).cht"),
        )
    }

    @Test
    fun `the console folders are kept with their time and ETag`() {
        val systems = GitHubCheatsApi.Systems(1759700000000, "\"f145be71\"", mapOf("Sony - PlayStation" to "bf0ad08b"))
        assertEquals(systems, GitHubCheatsApi.decodeSystems(GitHubCheatsApi.encodeSystems(systems)))
    }

    @Test
    fun `what was kept is used without asking GitHub again`() {
        val dir = temp.newFolder("github")
        val now = 1759700000000
        val systems = GitHubCheatsApi.Systems(now - 1000, null, mapOf("Nintendo - Nintendo 64" to "t1"))
        File(dir, "systems.txt").writeText(GitHubCheatsApi.encodeSystems(systems))
        File(dir, "trees").mkdirs()
        File(dir, "trees/t1.txt").writeText(GitHubCheatsApi.encodeTree(listOf(CheatFileRef("Castlevania (USA).cht", 5, "b1"))))
        File(dir, "files").mkdirs()
        File(dir, "files/b1.cht").writeText("cheats = 0\n")
        // The folder list is less than a day old and the console's list and file are kept, so nothing is fetched.
        val github = GitHubCheats(dir, CheatHttp("test"), clock = { now })
        val index = github.index(CheatSystem.N64)
        assertEquals(listOf("Castlevania (USA).cht"), index.files.map { it.name })
        assertEquals("cheats = 0\n", String(github.read(CheatSystem.N64, index.files.single())))
        assertTrue(github.index(CheatSystem.PS2).files.isEmpty())
    }

    @Test
    fun `the pack's entries become each console's files, READMEs and folders left out`() {
        val grouped = CheatPack.group(
            sequenceOf(
                "Nintendo - Nintendo 64/Castlevania (USA).cht" to 100L,
                "Nintendo - Nintendo 64/README.md" to 5L,
                "Nintendo - Game Boy Advance/Pokemon Unbound" to 5L,
                "Sony - PlayStation/Crash Bandicoot (USA).cht" to -1L,
                "stray.cht" to 1L,
            ),
        )
        assertEquals(listOf(CheatFileRef("Castlevania (USA).cht", 100)), grouped["Nintendo - Nintendo 64"])
        assertEquals(listOf(CheatFileRef("Crash Bandicoot (USA).cht", null)), grouped["Sony - PlayStation"])
        assertEquals(setOf("Nintendo - Nintendo 64", "Sony - PlayStation"), grouped.keys)
    }

    @Test
    fun `the pack is read from the zip where it is`() {
        val zip = temp.newFile("cheats.zip")
        val content = "cheats = 1\ncheat0_desc = \"Infinite Health\"\n".toByteArray()
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("Nintendo - Nintendo 64/"))
            out.closeEntry()
            out.putNextEntry(ZipEntry("Nintendo - Nintendo 64/Castlevania (USA).cht"))
            out.write(content)
            out.closeEntry()
        }
        val pack = CheatPack(zip, CheatHttp("test"))
        val index = pack.index(CheatSystem.N64)
        assertEquals(listOf("Castlevania (USA).cht"), index.files.map { it.name })
        assertArrayEquals(content, pack.read(CheatSystem.N64, index.files.single()))
        assertTrue(pack.index(CheatSystem.SNES).files.isEmpty())
    }

    @Test(expected = CheatProblem.NoPack::class)
    fun `without the pack there is nothing to read`() {
        CheatPack(File(temp.root, "missing.zip"), CheatHttp("test")).index(CheatSystem.N64)
    }
}
