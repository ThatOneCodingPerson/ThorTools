package io.github.thatonecodingperson.thortools.retroarch

/** What a shader for any console does: the "Any console" subfolder it is sorted into in RetroArch's Shaders menu. */
enum class ShaderKind(val folder: String) {
    CRT("CRT TVs"),
    SCANLINES("Scanlines"),
    SHARP("Sharp pixels"),
    SMOOTH("Smooth edges"),
    LCD("LCD screens"),
}

/**
 * A shader of libretro's collection, sorted for RetroArch's Shaders menu: [name] in plain words, its preset in each
 * language's collection ([slang]; [glsl], null where the GLSL collection has none) and where it goes: a [kind] of "Any
 * console", or the folders of the consoles it was made for ([systems]).
 */
data class CatalogShader(val name: String, val slang: String, val glsl: String?, val kind: ShaderKind?, val systems: List<CheatSystem>) {
    fun path(format: ShaderFormat): String? = if (format == ShaderFormat.SLANG) slang else glsl
}

/**
 * The shaders Thor Tools sorts into RetroArch's Shaders menu: well-known ones for any console by kind, and the ones made
 * for one console (handhelds' screens and colours, a console's TV signal). Every path is one of libretro's collection.
 */
object ShaderCatalog {
    val entries: List<CatalogShader> = listOf(
        any(ShaderKind.CRT, "Light CRT (quick)", "crt/zfast-crt.slangp", "crt/zfast-crt.glslp"),
        any(ShaderKind.CRT, "Light CRT, composite", "crt/zfast-crt-composite.slangp", null),
        any(ShaderKind.CRT, "Light CRT, curved", "crt/zfast-crt-curvature.slangp", null),
        any(ShaderKind.CRT, "CRT Easymode", "crt/crt-easymode.slangp", "crt/crt-easymode.glslp"),
        any(ShaderKind.CRT, "CRT Lottes", "crt/crt-lottes.slangp", "crt/crt-lottes.glslp"),
        any(ShaderKind.CRT, "CRT Lottes (quick)", "crt/fakelottes.slangp", "crt/fakelottes.glslp"),
        any(ShaderKind.CRT, "CRT Geom, curved", "crt/crt-geom.slangp", "crt/crt-geom.glslp"),
        any(ShaderKind.CRT, "CRT Pi (light)", "crt/crt-pi.slangp", "crt/crt-pi.glslp"),
        any(ShaderKind.CRT, "CRT Aperture", "crt/crt-aperture.slangp", "crt/crt-aperture.glslp"),
        any(ShaderKind.CRT, "CRT Hyllian", "crt/crt-hyllian.slangp", "crt/crt-hyllian.glslp"),
        any(ShaderKind.CRT, "CRT Guest Advanced (quick)", "crt/crt-guest-advanced-fast.slangp", null),
        any(ShaderKind.CRT, "CRT Consumer TV", "crt/crt-consumer.slangp", "crt/crt-consumer.glslp"),
        any(ShaderKind.CRT, "CRT Potato (cool)", "crt/crt-potato-cool.slangp", "crt/crt-potato-cool.glslp"),
        any(ShaderKind.CRT, "Trinitron TV", "crt/crt-yo6-flat-trinitron-tv.slangp", null),
        any(ShaderKind.CRT, "CRT Royale (heavy)", "crt/crt-royale.slangp", "crt/crt-royale.glslp"),
        any(ShaderKind.CRT, "NTSC composite signal", "ntsc/ntsc-adaptive.slangp", "ntsc/ntsc-adaptive.glslp"),
        any(ShaderKind.CRT, "Black and white TV", "presets/black-and-white-tv.slangp", null),
        any(ShaderKind.SCANLINES, "Simple scanlines", "scanlines/scanline.slangp", "scanlines/scanline.glslp"),
        any(ShaderKind.SCANLINES, "Soft scanlines", "scanlines/scanline-fract.slangp", "scanlines/scanline-fract.glslp"),
        any(ShaderKind.SCANLINES, "Integer scanlines", "scanlines/integer-scaling-scanlines.slangp", null),
        any(ShaderKind.SCANLINES, "Sine scanlines", "scanlines/scanlines-sine-abs.slangp", "scanlines/scanlines-sine-abs.glslp"),
        any(ShaderKind.SCANLINES, "OSSC scanlines", "scanlines/ossc.slangp", "scanlines/ossc.glslp"),
        any(ShaderKind.SHARP, "Plain pixels", "nearest.slangp", "nearest.glslp"),
        any(
            ShaderKind.SHARP,
            "Sharp bilinear",
            "pixel-art-scaling/sharp-bilinear-simple.slangp",
            "interpolation/sharp-bilinear-simple.glslp",
        ),
        any(
            ShaderKind.SHARP,
            "Sharp bilinear 2x",
            "pixel-art-scaling/sharp-bilinear-2x-prescale.slangp",
            "interpolation/sharp-bilinear-2x-prescale.glslp",
        ),
        any(ShaderKind.SHARP, "Sharp, no shimmer", "pixel-art-scaling/sharp-shimmerless.slangp", null),
        any(ShaderKind.SHARP, "Pixel AA", "pixel-art-scaling/pixel_aa.slangp", "pixel-art-scaling/pixel_aa.glslp"),
        any(ShaderKind.SHARP, "Pixel AA (quick)", "pixel-art-scaling/pixel_aa_fast.slangp", "pixel-art-scaling/pixel_aa_fast.glslp"),
        any(ShaderKind.SHARP, "Soft pixel art", "pixel-art-scaling/soft-pixel-art.slangp", null),
        any(ShaderKind.SMOOTH, "xBRZ", "edge-smoothing/xbrz/xbrz-freescale.slangp", "xbrz/xbrz-freescale.glslp"),
        any(
            ShaderKind.SMOOTH,
            "xBRZ (multipass)",
            "edge-smoothing/xbrz/xbrz-freescale-multipass.slangp",
            "xbrz/xbrz-freescale-multipass.glslp",
        ),
        any(ShaderKind.SMOOTH, "ScaleFX", "edge-smoothing/scalefx/scalefx.slangp", "scalefx/scalefx.glslp"),
        any(ShaderKind.SMOOTH, "Super xBR", "edge-smoothing/xbr/super-xbr.slangp", null),
        any(ShaderKind.SMOOTH, "xBR level 2", "edge-smoothing/xbr/xbr-lv2.slangp", "xbr/xbr-lv2.glslp"),
        any(ShaderKind.SMOOTH, "HQ2x", "edge-smoothing/hqx/hq2x.slangp", null),
        any(ShaderKind.SMOOTH, "HQ4x", "edge-smoothing/hqx/hq4x.slangp", null),
        any(ShaderKind.SMOOTH, "cleanEdge", "pixel-art-scaling/cleanEdge-rotate.slangp", null),
        any(ShaderKind.LCD, "LCD grid", "handheld/lcd-grid-v2.slangp", "handheld/lcd-grid-v2.glslp"),
        any(ShaderKind.LCD, "LCD (quick)", "handheld/zfast-lcd.slangp", "handheld/zfast-lcd.glslp"),
        any(ShaderKind.LCD, "LCD 3x", "handheld/lcd3x.slangp", "handheld/lcd3x.glslp"),
        any(ShaderKind.LCD, "Dot matrix", "handheld/dot.slangp", "handheld/dot.glslp"),
        any(ShaderKind.LCD, "Retro LCD", "handheld/retro-v2.slangp", "handheld/retro-v2.glslp"),
        any(ShaderKind.LCD, "Bevelled pixels", "handheld/bevel.slangp", "handheld/bevel.glslp"),
        any(ShaderKind.LCD, "LCD with texture", "handheld/simpletex_lcd.slangp", "handheld/simpletex_lcd.glslp"),
        console(listOf(CheatSystem.GB), "Game Boy green", "handheld/gameboy.slangp", "handheld/gameboy.glslp"),
        console(listOf(CheatSystem.GB), "Game Boy Pocket", "handheld/gameboy-pocket.slangp", "handheld/gameboy-pocket.glslp"),
        console(listOf(CheatSystem.GB), "Game Boy Pocket, high contrast", "handheld/gameboy-pocket-high-contrast.slangp", null),
        console(listOf(CheatSystem.GB), "Game Boy Light", "handheld/gameboy-light.slangp", "handheld/gameboy-light.glslp"),
        console(listOf(CheatSystem.GB), "Game Boy, dark mode", "handheld/gameboy-dark-mode.slangp", null),
        console(listOf(CheatSystem.GB), "Game Boy, light mode", "handheld/gameboy-light-mode.slangp", null),
        console(listOf(CheatSystem.GB), "Game Boy colours only", "handheld/gb-palette-dmg.slangp", "handheld/gb-palette-dmg.glslp"),
        console(listOf(CheatSystem.GB), "Pocket colours only", "handheld/gb-palette-pocket.slangp", "handheld/gb-palette-pocket.glslp"),
        console(listOf(CheatSystem.GB), "Light colours only", "handheld/gb-palette-light.slangp", "handheld/gb-palette-light.glslp"),
        console(listOf(CheatSystem.GB), "Game Boy with its border", "handheld/console-border/dmg.slangp", null),
        console(listOf(CheatSystem.GB), "Game Boy Pocket with its border", "handheld/console-border/gb-pocket.slangp", null),
        console(listOf(CheatSystem.GB, CheatSystem.GBC), "SameBoy LCD", "handheld/sameboy-lcd.slangp", "handheld/sameboy-lcd.glslp"),
        console(listOf(CheatSystem.GBC), "Game Boy Color screen", "handheld/authentic_gbc.slangp", "handheld/authentic_gbc.glslp"),
        console(
            listOf(CheatSystem.GBC),
            "Game Boy Color screen (quick)",
            "handheld/authentic_gbc_fast.slangp",
            "handheld/authentic_gbc_fast.glslp",
        ),
        console(listOf(CheatSystem.GBC), "Game Boy Color frontlit", "presets/authentic-gbc-frontlit.slangp", null),
        console(listOf(CheatSystem.GBC), "Game Boy Color dot matrix", "handheld/gameboy-color-dot-matrix.slangp", null),
        console(listOf(CheatSystem.GBC), "Game Boy Color dot matrix, white", "handheld/gameboy-color-dot-matrix-white-bg.slangp", null),
        console(listOf(CheatSystem.GBC), "Game Boy Color colours", "handheld/color-mod/gbc-color.slangp", "handheld/gbc-color.glslp"),
        console(
            listOf(CheatSystem.GBC),
            "Game Boy Color colours (Gambatte)",
            "handheld/color-mod/gbc-gambatte-color.slangp",
            "handheld/gbc-gambatte-color.glslp",
        ),
        console(listOf(CheatSystem.GBC), "Game Boy Color colours (Switch Online)", "handheld/color-mod/NSO-gbc-color.slangp", null),
        console(listOf(CheatSystem.GBC), "Game Boy Color with its border", "handheld/console-border/gbc.slangp", null),
        console(listOf(CheatSystem.GBA), "GBA screen (AGB-001)", "handheld/agb001.slangp", "handheld/agb001.glslp"),
        console(listOf(CheatSystem.GBA), "GBA SP backlit (AGS-001)", "handheld/ags001.slangp", "handheld/ags001.glslp"),
        console(listOf(CheatSystem.GBA), "GBA dot matrix", "handheld/gameboy-advance-dot-matrix.slangp", null),
        console(listOf(CheatSystem.GBA), "GBA dot matrix, sepia", "presets/gameboy-advance-dot-matrix-sepia.slangp", null),
        console(listOf(CheatSystem.GBA), "GBA colours", "handheld/color-mod/gba-color.slangp", "handheld/gba-color.glslp"),
        console(listOf(CheatSystem.GBA), "GBA colours (Switch Online)", "handheld/color-mod/NSO-gba-color.slangp", null),
        console(listOf(CheatSystem.GBA), "GBA SP 101 colours", "handheld/color-mod/sp101-color.slangp", null),
        console(listOf(CheatSystem.GBA), "GBA with its border", "handheld/console-border/gba.slangp", "reshade/gba.glslp"),
        console(listOf(CheatSystem.NDS), "DS LCD grid", "handheld/nds-lcd-grid-v2.slangp", null),
        console(listOf(CheatSystem.NDS), "DS LCD", "handheld/lcd1x_nds.slangp", "handheld/lcd1x_nds.glslp"),
        console(listOf(CheatSystem.NDS), "DS sharp and smooth (SABR)", "handheld/ds-hybrid-sabr.slangp", "handheld/ds-hybrid-sabr.glslp"),
        console(
            listOf(CheatSystem.NDS),
            "DS sharp and smooth (ScaleFX)",
            "handheld/ds-hybrid-scalefx.slangp",
            "handheld/ds-hybrid-scalefx.glslp",
        ),
        console(listOf(CheatSystem.NDS), "DS colours", "handheld/color-mod/nds-color.slangp", "handheld/nds-color.glslp"),
        console(listOf(CheatSystem.NDS), "DS Lite colours", "handheld/color-mod/dslite-color.slangp", null),
        console(listOf(CheatSystem.NDS), "DS with its border", "handheld/console-border/nds-auto-gap.slangp", null),
        console(listOf(CheatSystem.PSP), "PSP LCD", "handheld/lcd1x_psp.slangp", "handheld/lcd1x_psp.glslp"),
        console(listOf(CheatSystem.PSP), "PSP colours", "handheld/color-mod/psp-color.slangp", "handheld/psp-color.glslp"),
        console(listOf(CheatSystem.PSP), "PSP with its border", "handheld/console-border/psp.slangp", null),
        console(listOf(CheatSystem.N3DS), "3DS LCD grid", "handheld/3ds-lcd-grid-v2.slangp", null),
        console(listOf(CheatSystem.N3DS), "3DS with its border", "handheld/console-border/3ds-auto-gap.slangp", null),
        console(listOf(CheatSystem.GAME_GEAR), "Game Gear with its border", "handheld/console-border/gg.slangp", null),
        console(
            listOf(CheatSystem.NEO_GEO_POCKET_COLOR),
            "Neo Geo Pocket Color with its border",
            "handheld/console-border/ngpc.slangp",
            null,
        ),
        console(listOf(CheatSystem.NES), "NES Classic Mini TV", "crt/crt-nes-mini.slangp", "crt/crt-nes-mini.glslp"),
        console(
            listOf(CheatSystem.NES),
            "NTSC NES (needs Raw palette)",
            "nes_raw_palette/ntsc-nes.slangp",
            "nes_raw_palette/ntsc-nes.glslp",
        ),
        console(
            listOf(CheatSystem.NES),
            "Famicom TV (needs Raw palette)",
            "nes_raw_palette/gtu-famicom.slangp",
            "nes_raw_palette/gtu-famicom.glslp",
        ),
        console(
            listOf(CheatSystem.NES),
            "Famicom curved TV (needs Raw palette)",
            "nes_raw_palette/cgwg-famicom-geom.slangp",
            "nes_raw_palette/cgwg-famicom-geom.glslp",
        ),
        console(
            listOf(CheatSystem.NES),
            "PAL NES (needs Raw palette)",
            "nes_raw_palette/pal-r57shell-raw.slangp",
            "nes_raw_palette/pal-r57shell-raw.glslp",
        ),
        console(listOf(CheatSystem.NES), "Mesen colours (needs Raw palette)", "nes_raw_palette/patchy-mesen-raw-palette.slangp", null),
        console(listOf(CheatSystem.MEGA_DRIVE), "Genesis waterfalls (NTSC rainbows)", "ntsc/ntsc-md-rainbows.slangp", null),
        console(listOf(CheatSystem.MEGA_DRIVE), "Genesis NTSC (BlastEm)", "ntsc/ntsc-blastem.slangp", "ntsc/ntsc-blastem.glslp"),
        console(listOf(CheatSystem.MEGA_DRIVE), "Genesis NTSC (Genesis Plus GX)", "ntsc/patchy-genplusgx.slangp", null),
        console(listOf(CheatSystem.SNES), "SNES NTSC", "ntsc/patchy-snes.slangp", null),
    )

    /** The consoles that have shaders made for them. */
    val systems: List<CheatSystem> get() = entries.flatMap { it.systems }.distinct()

    private fun any(kind: ShaderKind, name: String, slang: String, glsl: String?) = CatalogShader(name, slang, glsl, kind, emptyList())

    private fun console(systems: List<CheatSystem>, name: String, slang: String, glsl: String?) =
        CatalogShader(name, slang, glsl, null, systems)
}
