package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShaderFormatTest {
    @Test
    fun `the video driver tells the shader language`() {
        assertEquals(ShaderFormat.SLANG, ShaderFormat.forDriver("vulkan"))
        assertEquals(ShaderFormat.SLANG, ShaderFormat.forDriver("glcore"))
        assertEquals(ShaderFormat.GLSL, ShaderFormat.forDriver("gl"))
        assertNull(ShaderFormat.forDriver("sdl2"))
        assertNull(ShaderFormat.forDriver(null))
    }

    @Test
    fun `a preset pulls in its passes, textures and references, a shader its includes`() {
        val preset = """
            #reference "../base/look.slangp"
            shaders = 2
            shader0 = shaders/zfast_crt/zfast_crt_finemask.slang
            filter_linear0 = true
            shader1 = "../stock.slang"
            textures = "BORDER;COLOR_PALETTE"
            BORDER = "resources/border.png"
            COLOR_PALETTE = resources/palette.png
            scale_type0 = viewport
        """.trimIndent()
        assertEquals(
            listOf(
                "base/look.slangp",
                "crt/shaders/zfast_crt/zfast_crt_finemask.slang",
                "stock.slang",
                "crt/resources/border.png",
                "crt/resources/palette.png",
            ),
            ShaderDeps.of("crt/zfast-crt.slangp", preset),
        )
        val shader = "#version 450\n#include \"../../../include/compat_macros.inc\"\n  #include \"params.inc\"\nvoid main() {}"
        assertEquals(
            listOf("include/compat_macros.inc", "crt/shaders/zfast_crt/params.inc"),
            ShaderDeps.of("crt/shaders/zfast_crt/zfast_crt_finemask.slang", shader),
        )
        assertTrue(ShaderDeps.of("a.slang", "#include \"../../outside.inc\"\n#include \"/abs.inc\"").isEmpty())
        assertTrue(ShaderDeps.isText("x/y.slang"))
        assertTrue(!ShaderDeps.isText("x/y.png"))
    }

    @Test
    fun `names into libretro's collection from a shared preset point at the library, the link's own files stay`() {
        val format = ShaderFormat.SLANG
        assertEquals("shaders_slang/crt/x.slang", LibraryRefs.of("shaders_slang/crt/x.slang", format))
        assertEquals("shaders_slang/crt/x.slang", LibraryRefs.of("../Shaders_Slang/crt/x.slang", format))
        assertEquals("shaders_slang/crt/x.slang", LibraryRefs.of("C:\\RetroArch\\shaders\\shaders_slang\\crt\\x.slang", format))
        assertEquals(
            "shaders_slang/crt/x.slang",
            LibraryRefs.of("/storage/emulated/0/RetroArch/shaders/shaders_slang/crt/x.slang", format),
        )
        assertNull(LibraryRefs.of("crt/shaders/x.slang", format))
        assertNull(LibraryRefs.of("my_shaders_slang/x.slang", format))
        assertNull(LibraryRefs.of("shaders_glsl/x.glsl", format))

        val preset = "shaders = \"2\"\r\nshader0 = \"shaders_slang/motionblur/shaders/response-time.slang\"\r\n" +
            "filter_linear0 = \"false\"\r\nshader1 = \"own/pass.slang\"\r\n"
        val files = setOf("lcd-gameboy.slangp", "own/pass.slang")
        val (text, used) = LibraryRefs.point("lcd-gameboy.slangp", preset, ".links/gitlab-o-r/lcd-gameboy.slangp", format, files)
        assertEquals(
            "shaders = \"2\"\r\nshader0 = \"../../shaders_slang/motionblur/shaders/response-time.slang\"\r\n" +
                "filter_linear0 = \"false\"\r\nshader1 = \"own/pass.slang\"\r\n",
            text,
        )
        assertEquals(setOf("shaders_slang/motionblur/shaders/response-time.slang"), used)
        assertEquals(listOf("own/pass.slang"), ShaderDeps.of("lcd-gameboy.slangp", text))

        val reference = "#reference \"shaders_slang/crt/crt-guest-advanced-ntsc.slangp\"\ncust_fringing = \"1.000000\"\n"
        assertEquals(
            "#reference \"../../../shaders_slang/crt/crt-guest-advanced-ntsc.slangp\"\ncust_fringing = \"1.000000\"\n",
            LibraryRefs.point("crt/ntsc.slangp", reference, ".links/web-x/crt/ntsc.slangp", format, null).first,
        )
        val ownCopy = "#reference \"shaders_slang/crt/a.slangp\"\n"
        val ownFiles = setOf("shaders_slang/crt/a.slangp")
        assertEquals(ownCopy, LibraryRefs.point("b.slangp", ownCopy, ".links/k/b.slangp", format, ownFiles).first)
    }
}
