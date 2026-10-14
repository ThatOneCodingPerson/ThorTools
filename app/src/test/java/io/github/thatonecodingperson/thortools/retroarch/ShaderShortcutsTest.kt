package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShaderShortcutsTest {
    @Test
    fun `the catalogue has a name once per folder, a place for each shader and slang presets everywhere`() {
        val entries = ShaderCatalog.entries
        assertTrue(entries.all { it.slang.endsWith(".slangp") && (it.glsl == null || it.glsl.endsWith(".glslp")) })
        assertTrue(entries.all { (it.kind == null) != it.systems.isEmpty() })
        val planned = ShaderShortcuts.planned(ShaderFormat.SLANG, ShaderCatalog.systems)
        assertEquals(planned.size, planned.map { it.path.lowercase() }.distinct().size)
        assertTrue(planned.all { shortcut -> shortcut.path.split('/').all { ShaderShortcuts.safe(it) == it } })
        assertTrue(ShaderKind.entries.all { kind -> entries.count { it.kind == kind } >= 5 })
        assertTrue(ShaderShortcuts.planned(ShaderFormat.GLSL, ShaderCatalog.systems).all { it.path.endsWith(".glslp") })
    }

    @Test
    fun `a shortcut points at the library from where it is`() {
        val path = "Any console/CRT TVs/Light CRT (quick).slangp"
        assertEquals(
            "#reference \"../../shaders_slang/crt/zfast-crt.slangp\"\n# Thor Tools shortcut\n",
            ShaderShortcuts.text(path, "shaders_slang/crt/zfast-crt.slangp"),
        )
        assertEquals(
            "../shaders_slang/handheld/gameboy.slangp",
            ShaderShortcuts.relative("Game Boy/Game Boy green.slangp", "shaders_slang/handheld/gameboy.slangp"),
        )
        assertEquals("Mega Drive - Genesis", ShaderShortcuts.consoleFolder(CheatSystem.MEGA_DRIVE))
        assertEquals("a-b-c", ShaderShortcuts.safe("a/b:c."))
    }

    private val cores = listOf(
        CoreInfo("mgba_libretro", "mGBA", listOf("Nintendo - Game Boy", "Nintendo - Game Boy Color", "Nintendo - Game Boy Advance")),
        CoreInfo("genesis_plus_gx_libretro", "Genesis Plus GX", listOf("Sega - Mega Drive - Genesis", "Sega - Game Gear")),
        CoreInfo("mednafen_psx_hw_libretro", "Beetle PSX HW", listOf("Sony - PlayStation")),
    )

    @Test
    fun `console folders are the cores' consoles with shaders made for them, or all of them`() {
        assertEquals(
            listOf(CheatSystem.GB, CheatSystem.GBC, CheatSystem.GBA, CheatSystem.GAME_GEAR, CheatSystem.MEGA_DRIVE).sortedBy { it.ordinal },
            ShaderShortcuts.consoles(cores, all = false).sortedBy { it.ordinal },
        )
        assertEquals(ShaderCatalog.systems, ShaderShortcuts.consoles(cores, all = true))
        assertTrue(
            CheatSystem.N3DS in
                ShaderShortcuts.consoles(listOf(CoreInfo("citra_libretro", "Citra", listOf("Nintendo - Nintendo 3DS"))), all = false),
        )
    }

    @Test
    fun `sorting writes what is missing and takes away the folders of consoles no longer chosen`() {
        val format = ShaderFormat.SLANG
        val planned = ShaderShortcuts.planned(format, listOf(CheatSystem.GB))
        val found = listOf(
            FoundPreset("Any console/CRT TVs/Light CRT (quick).slangp", "shaders_slang/crt/zfast-crt.slangp", true),
            FoundPreset("NES/NES Classic Mini TV.slangp", "shaders_slang/crt/crt-nes-mini.slangp", true),
            FoundPreset("NES/My own.slangp", "shaders_slang/crt/crt-nes-mini.slangp", false),
            FoundPreset("Game Boy/From a link.slangp", ".links/github-a-b/gb.slangp", true),
            FoundPreset("Some folder/Thing.slangp", "shaders_slang/crt/crt-pi.slangp", true),
            FoundPreset("NES/Xbr from libretro.slangp", "shaders_slang/edge-smoothing/xbr/xbr-lv2.slangp", true),
            FoundPreset("NES/Game Boy green.slangp", "shaders_slang/handheld/gameboy.slangp", true),
        )
        val dismissed = setOf("Game Boy/Game Boy green.slangp")
        val changes = ShaderShortcuts.changes(planned, found, dismissed, format)
        assertTrue(changes.write.none { it.path == "Any console/CRT TVs/Light CRT (quick).slangp" })
        assertTrue(changes.write.none { it.path in dismissed })
        assertTrue(changes.write.any { it.path == "Game Boy/Game Boy Pocket.slangp" })
        assertEquals(listOf("NES/NES Classic Mini TV.slangp"), changes.remove)
        val all = ShaderShortcuts.changes(ShaderShortcuts.planned(format, ShaderCatalog.systems), found, emptySet(), format)
        assertTrue(all.remove.isEmpty())
    }

    @Test
    fun `link shaders get a shortcut in each folder chosen, numbered when another file has the name`() {
        val link = ShaderLinks.parse("https://github.com/someone/shaders") as ShaderLink.Repo
        fun preset(path: String, name: String, folders: List<String>) =
            LinkPreset(path, name, folders, true, true, ShaderShortcuts.linkTarget(link, path, ShaderFormat.SLANG, emptySet()))
        val presets = listOf(
            preset("gb/dmg-look.slangp", "Dmg look", listOf("Game Boy", "Game Boy Color")),
            preset("crt/tv.slangp", "Tv", listOf("Any console/CRT TVs")),
            preset("crt2/tv.slangp", "Tv", listOf("Any console/CRT TVs")),
        )
        val existing = listOf(
            FoundPreset("Game Boy/Dmg look.slangp", null, false),
            FoundPreset("Game Boy Color/Dmg look.slangp", ".links/github-someone-shaders/gb/dmg-look.slangp", true),
        )
        assertEquals(
            listOf(
                Shortcut("Game Boy/Dmg look (2).slangp", ".links/github-someone-shaders/gb/dmg-look.slangp"),
                Shortcut("Game Boy Color/Dmg look.slangp", ".links/github-someone-shaders/gb/dmg-look.slangp"),
                Shortcut("Any console/CRT TVs/Tv.slangp", ".links/github-someone-shaders/crt/tv.slangp"),
                Shortcut("Any console/CRT TVs/Tv (2).slangp", ".links/github-someone-shaders/crt2/tv.slangp"),
            ),
            ShaderShortcuts.forLink(presets, existing),
        )
    }

    @Test
    fun `libretro's own collection points at the library's copy when it has the preset, other links at their folder`() {
        val format = ShaderFormat.SLANG
        val libretro = ShaderLinks.parse("https://github.com/libretro/slang-shaders/tree/master/scanlines")!!
        val other = ShaderLinks.parse("https://github.com/someone/slang-shaders")!!
        val inLibrary = setOf("scanlines/ossc.slangp")
        assertTrue(ShaderShortcuts.isLibrary(libretro, format))
        assertFalse(ShaderShortcuts.isLibrary(libretro, ShaderFormat.GLSL))
        assertFalse(ShaderShortcuts.isLibrary(other, format))
        assertEquals(
            "shaders_slang/scanlines/OSSC.slangp",
            ShaderShortcuts.linkTarget(libretro, "scanlines/OSSC.slangp", format, inLibrary),
        )
        assertEquals(
            ".links/github-libretro-slang-shaders/scanlines/new.slangp",
            ShaderShortcuts.linkTarget(libretro, "scanlines/new.slangp", format, inLibrary),
        )
        assertEquals(
            ".links/github-someone-slang-shaders/scanlines/ossc.slangp",
            ShaderShortcuts.linkTarget(other, "scanlines/ossc.slangp", format, inLibrary),
        )
        assertEquals("github-a-b", ShaderShortcuts.linkKey(".links/github-a-b/crt/x.slangp"))
        assertNull(ShaderShortcuts.linkKey("shaders_slang/crt/x.slangp"))
        assertNull(ShaderShortcuts.linkKey(null))
    }

    @Test
    fun `a scan's lines give the presets with their targets and Thor Tools' marks, read in menu order`() {
        val root = "/storage/emulated/0/Android/media/com.retroarch.aarch64/RetroArch/shaders"
        val output = """
            L 5742
            S $root/Game Boy/Game Boy green.slangp
            R #reference "../shaders_slang/handheld/gameboy.slangp"
            M
            S $root/Any console/CRT TVs/Light CRT (quick).slangp
            R #reference "../../shaders_slang/crt/zfast-crt.slangp"
            M
            S $root/Mine/custom.slangp
            S $root/From links/repo/x.slangp
            R #reference "../../.links/github-a-b/x.slangp"
            M
            S $root/retroarch.slangp
            R #reference "Any console/CRT TVs/CRT Hyllian.slangp"
        """.trimIndent()
        val found = ShaderShortcuts.parse(output, root)
        assertEquals(
            listOf(
                FoundPreset("Game Boy/Game Boy green.slangp", "shaders_slang/handheld/gameboy.slangp", true),
                FoundPreset("Any console/CRT TVs/Light CRT (quick).slangp", "shaders_slang/crt/zfast-crt.slangp", true),
                FoundPreset("Mine/custom.slangp", null, false),
                FoundPreset("From links/repo/x.slangp", ".links/github-a-b/x.slangp", true),
                FoundPreset("retroarch.slangp", "Any console/CRT TVs/CRT Hyllian.slangp", false),
            ),
            found,
        )
        assertEquals(listOf("Any console/CRT TVs", "Game Boy", "From links/repo", "Mine"), ShaderShortcuts.byFolder(found).map { it.first })
        assertEquals(
            Shortcut("Game Boy Color/Light CRT (quick).slangp", "shaders_slang/crt/zfast-crt.slangp"),
            ShaderShortcuts.moved(found[1], "Game Boy Color"),
        )
    }
}
