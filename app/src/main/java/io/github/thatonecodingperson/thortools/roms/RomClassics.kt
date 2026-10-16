package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.CheatSystem

/** The formats of a classic console's games; ZIP first and the default, since every front end lists it. */
val CART_FORMATS = listOf(RomFormat.ZIP, RomFormat.SEVEN_Z, RomFormat.ROM_FILE)

/**
 * A classic cartridge console's facts: its [id], console in the folder table ([system]), [maker] and [year], plain ROM
 * types, the standalone emulators and RetroArch cores people use for it on Android, and [sevenZip]: how big a 7z of its
 * games usually is next to a zip of them.
 */
data class Classic(
    val id: String,
    val system: CheatSystem,
    val maker: Maker,
    val year: Int,
    val romTypes: List<String>,
    val emulators: List<RomEmulator>,
    val cores: List<RomCore>,
    val sevenZip: Double = 0.9,
    val saving: Map<RomFormat, IntRange> = mapOf(RomFormat.ZIP to 20..60, RomFormat.SEVEN_Z to 25..65, RomFormat.ROM_FILE to 0..0),
)

/** What an emulator does with a zip and a 7z; every one opens the plain ROM. */
private fun archives(zip: Support, sevenZip: Support) =
    listOf(Reads(RomFormat.ZIP, zip), Reads(RomFormat.SEVEN_Z, sevenZip), Reads(RomFormat.ROM_FILE, Support.DOCUMENTED))

private val BOTH = archives(Support.DOCUMENTED, Support.DOCUMENTED)
private val ZIP_ONLY = archives(Support.DOCUMENTED, Support.UNCONFIRMED)
private val UNKNOWN = archives(Support.UNCONFIRMED, Support.UNCONFIRMED)

/** A RetroArch core: RetroArch unpacks zip and 7z for every one of them. */
private fun core(name: String, vararg stems: String) = RomCore(name, stems.map { "${it}_libretro" }, BOTH)

private val LEMUROID = RomEmulator("Lemuroid", listOf("com.swordfish.lemuroid"), archives(Support.DOCUMENTED, Support.NO))
private val NES_EMU = RomEmulator("NES.emu", listOf("com.explusalpha.NesEmu"), BOTH, unlisted = true)
private val SNES9X_EX = RomEmulator("Snes9x EX+", listOf("com.explusalpha.Snes9xPlus"), BOTH, unlisted = true)
private val GBC_EMU = RomEmulator("GBC.emu", listOf("com.explusalpha.GbcEmu"), BOTH, unlisted = true)
private val GBA_EMU = RomEmulator("GBA.emu", listOf("com.explusalpha.GbaEmu"), BOTH, unlisted = true)
private val MD_EMU = RomEmulator("MD.emu", listOf("com.explusalpha.MdEmu"), BOTH, unlisted = true)
private val PCE_EMU = RomEmulator("PCE.emu", listOf("com.PceEmu"), BOTH, unlisted = true)
private val NGP_EMU = RomEmulator("NGP.emu", listOf("com.explusalpha.NgpEmu"), BOTH)
private val SWAN_EMU = RomEmulator("Swan.emu", listOf("com.explusalpha.SwanEmu"), BOTH)
private val LYNX_EMU = RomEmulator("Lynx.emu", listOf("com.explusalpha.LynxEmu"), BOTH)
private val A2600_EMU = RomEmulator("2600.emu", listOf("com.explusalpha.A2600Emu"), BOTH, unlisted = true)
private val MSX_EMU = RomEmulator("MSX.emu", listOf("com.explusalpha.MsxEmu"), BOTH)
private val PIZZA_BOY_C = RomEmulator("Pizza Boy C", listOf("it.dbtecno.pizzaboy", "it.dbtecno.pizzaboypro"), BOTH)
private val PIZZA_BOY_A = RomEmulator("Pizza Boy A", listOf("it.dbtecno.pizzaboygba", "it.dbtecno.pizzaboygbapro"), BOTH)
private val PIZZA_BOY_SC = RomEmulator("Pizza Boy SC", listOf("it.dbtecno.pizzaboyscbasic", "it.dbtecno.pizzaboyscpro"), ZIP_ONLY)
private val MY_OLDBOY = RomEmulator("My OldBoy!", listOf("com.fastemulator.gbc", "com.fastemulator.gbcfree"), ZIP_ONLY, unlisted = true)
private val MY_BOY = RomEmulator("My Boy!", listOf("com.fastemulator.gba", "com.fastemulator.gbafree"), ZIP_ONLY, unlisted = true)
private val SKYEMU = RomEmulator("SkyEmu", listOf("com.sky.SkyEmu"), ZIP_ONLY)
private val LINKBOY_GB = RomEmulator("Linkboy", listOf("com.pixelrespawn.linkboy"), UNKNOWN)
private val LINKBOY_GBA = RomEmulator("Linkboy", listOf("com.pixelrespawn.linkboy"), ZIP_ONLY)
private val M64PLUS_FZ = RomEmulator(
    "M64Plus FZ",
    listOf("org.mupen64plusae.v3.fzurita.pro", "org.mupen64plusae.v3.fzurita", "org.mupen64plusae.v3.alpha"),
    BOTH,
)
private val MUPEN64PLUS_AE = RomEmulator("Mupen64Plus AE", listOf("paulscode.android.mupen64plusae"), BOTH, unlisted = true)
private val MELONDS = RomEmulator("melonDS", listOf("me.magnum.melonds", "me.magnum.melonds.nightly", "me.magnum.melonds.dev"), BOTH)
private val MELONDUALDS = RomEmulator("MelonDualDS", listOf("me.magnum.melondualds"), BOTH)
private val DRASTIC = RomEmulator("DraStic", listOf("com.dsemu.drastic"), UNKNOWN)
private val NOODS = RomEmulator("NooDS", listOf("com.hydra.noods"), UNKNOWN)
private val VVB = RomEmulator("Virtual Virtual Boy", listOf("com.simongellis.vvb"), ZIP_ONLY)
private val IRATA_JAGUAR = RomEmulator("IrataJaguar", listOf("ru.vastness.altmer.iratajaguar"), BOTH)

private val MESEN = core("Mesen", "mesen")
private val MESEN_CE = core("MesenCE", "mesen2")
private val MESEN_S = core("Mesen-S", "mesen-s")
private val GPGX = core("Genesis Plus GX", "genesis_plus_gx", "genesis_plus_gx_wide")
private val GEARSYSTEM = core("Gearsystem", "gearsystem")
private val PICODRIVE = core("PicoDrive", "picodrive")
private val SMSPLUS = core("SMS Plus GX", "smsplus")
private val SKYEMU_CORE = core("SkyEmu", "skyemu")
private val GB_CORES = listOf(
    core("Gambatte", "gambatte"),
    core("SameBoy", "sameboy"),
    core("Gearboy", "gearboy"),
    core("TGB Dual", "tgbdual"),
    core("DoubleCherryGB", "doublecherrygb"),
    core("mGBA", "mgba"),
    core("VBA-M", "vbam"),
    MESEN_S,
    MESEN_CE,
    SKYEMU_CORE,
)
private val PCE_CORES = listOf(
    core("Beetle PCE", "mednafen_pce"),
    core("Beetle PCE FAST", "mednafen_pce_fast"),
    core("Beetle SuperGrafx", "mednafen_supergrafx"),
    core("Geargrafx", "geargrafx"),
    MESEN_CE,
)
private val NGP_CORES = listOf(core("Beetle NeoPop", "mednafen_ngp"), core("RACE", "race"))
private val WS_CORES = listOf(core("Beetle Cygne", "mednafen_wswan"), MESEN_CE)
private val GB_EMULATORS = listOf(PIZZA_BOY_C, MY_OLDBOY, GBC_EMU, SKYEMU, LINKBOY_GB, LEMUROID)

/** The classic consoles Thor Tools compresses, by maker and year. */
object Classics {
    private val NES_TYPES = listOf("nes", "unf", "unif")

    val NES = Classic(
        "nes",
        CheatSystem.NES,
        Maker.NINTENDO,
        1983,
        NES_TYPES,
        listOf(NES_EMU, LEMUROID),
        listOf(
            MESEN,
            MESEN_CE,
            core("Nestopia", "nestopia"),
            core("FCEUmm", "fceumm"),
            core("QuickNES", "quicknes"),
            core("RustyNES", "rustynes"),
            core("bnes", "bnes"),
        ),
    )
    val FDS = Classic(
        "fds",
        CheatSystem.FDS,
        Maker.NINTENDO,
        1986,
        listOf("fds"),
        listOf(NES_EMU),
        listOf(MESEN, MESEN_CE, core("Nestopia", "nestopia"), core("FCEUmm", "fceumm"), core("RustyNES", "rustynes")),
    )
    val SNES = Classic(
        "snes",
        CheatSystem.SNES,
        Maker.NINTENDO,
        1990,
        listOf("sfc", "smc", "swc", "fig"),
        listOf(SNES9X_EX, LEMUROID),
        listOf(
            core("Snes9x", "snes9x", "snes9x2010", "snes9x2005_plus", "snes9x2005", "snes9x2002"),
            core("bsnes", "bsnes", "bsnes_hd_beta", "bsnes_mercury_accuracy", "bsnes_mercury_balanced", "bsnes_mercury_performance"),
            core("Beetle Supafaust", "mednafen_supafaust"),
            MESEN_S,
            MESEN_CE,
        ),
    )
    val SATELLAVIEW = Classic(
        "satellaview",
        CheatSystem.SATELLAVIEW,
        Maker.NINTENDO,
        1995,
        listOf("bs"),
        listOf(SNES9X_EX),
        listOf(core("Snes9x", "snes9x", "snes9x2010", "snes9x2005_plus"), core("bsnes", "bsnes"), MESEN_S, MESEN_CE),
    )
    val N64 = Classic(
        "n64",
        CheatSystem.N64,
        Maker.NINTENDO,
        1996,
        listOf("z64", "n64", "v64"),
        listOf(M64PLUS_FZ, MUPEN64PLUS_AE, LEMUROID),
        listOf(
            core("Mupen64Plus-Next", "mupen64plus_next_gles3", "mupen64plus_next_gles2", "mupen64plus_next"),
            core("ParaLLEl N64", "parallel_n64"),
        ),
        sevenZip = 0.75,
    )
    val VIRTUAL_BOY = Classic(
        "vb",
        CheatSystem.VIRTUAL_BOY,
        Maker.NINTENDO,
        1995,
        listOf("vb", "vboy"),
        listOf(VVB),
        listOf(core("Beetle VB", "mednafen_vb")),
    )
    val GB = Classic("gb", CheatSystem.GB, Maker.NINTENDO, 1989, listOf("gb", "dmg", "sgb"), GB_EMULATORS, GB_CORES)
    val GBC = Classic("gbc", CheatSystem.GBC, Maker.NINTENDO, 1998, listOf("gbc", "cgb"), GB_EMULATORS, GB_CORES)
    val GBA = Classic(
        "gba",
        CheatSystem.GBA,
        Maker.NINTENDO,
        2001,
        listOf("gba", "agb"),
        listOf(PIZZA_BOY_A, MY_BOY, GBA_EMU, SKYEMU, LINKBOY_GBA, NOODS, LEMUROID),
        listOf(
            core("mGBA", "mgba"),
            core("VBA-M", "vbam"),
            core("VBA Next", "vba_next"),
            core("gpSP", "gpsp"),
            core("Beetle GBA", "mednafen_gba"),
            core("Meteor", "meteor"),
            SKYEMU_CORE,
            MESEN_CE,
        ),
        sevenZip = 0.85,
    )
    val NDS = Classic(
        "nds",
        CheatSystem.NDS,
        Maker.NINTENDO,
        2004,
        listOf("nds", "dsi", "ids"),
        listOf(MELONDS, MELONDUALDS, DRASTIC, NOODS, SKYEMU, LEMUROID),
        listOf(
            core("melonDS DS", "melondsds"),
            core("melonDS", "melonds"),
            core("DeSmuME", "desmume", "desmume2015"),
            core("NooDS", "noods"),
            SKYEMU_CORE,
        ),
        sevenZip = 0.45,
    )
    val POKEMON_MINI = Classic(
        "pokemini",
        CheatSystem.POKEMON_MINI,
        Maker.NINTENDO,
        2001,
        listOf("min"),
        emptyList(),
        listOf(core("PokeMini", "pokemini")),
    )
    val SG1000 = Classic(
        "sg1000",
        CheatSystem.SG1000,
        Maker.SEGA,
        1983,
        listOf("sg"),
        emptyList(),
        listOf(GPGX, GEARSYSTEM, core("blueMSX", "bluemsx")),
    )
    val MASTER_SYSTEM = Classic(
        "sms",
        CheatSystem.MASTER_SYSTEM,
        Maker.SEGA,
        1985,
        listOf("sms"),
        listOf(MD_EMU, PIZZA_BOY_SC, LEMUROID),
        listOf(GPGX, SMSPLUS, GEARSYSTEM, PICODRIVE, MESEN_CE),
    )
    val GENESIS = Classic(
        "genesis",
        CheatSystem.MEGA_DRIVE,
        Maker.SEGA,
        1988,
        listOf("md", "gen", "smd", "bin"),
        listOf(MD_EMU, PIZZA_BOY_SC, LEMUROID),
        listOf(GPGX, PICODRIVE, core("BlastEm", "blastem"), core("ClownMDEmu", "clownmdemu")),
    )
    val SEGA_32X = Classic("32x", CheatSystem.SEGA_32X, Maker.SEGA, 1994, listOf("32x"), emptyList(), listOf(PICODRIVE))
    val GAME_GEAR = Classic(
        "gamegear",
        CheatSystem.GAME_GEAR,
        Maker.SEGA,
        1990,
        listOf("gg"),
        listOf(PIZZA_BOY_SC, LEMUROID),
        listOf(GPGX, GEARSYSTEM, SMSPLUS, PICODRIVE, MESEN_CE),
    )
    val PC_ENGINE = Classic("pce", CheatSystem.PC_ENGINE, Maker.NEC, 1987, listOf("pce"), listOf(PCE_EMU, LEMUROID), PCE_CORES)
    val SUPERGRAFX = Classic("sgx", CheatSystem.SUPERGRAFX, Maker.NEC, 1989, listOf("sgx"), listOf(PCE_EMU), PCE_CORES)
    val NEO_GEO_POCKET = Classic(
        "ngp",
        CheatSystem.NEO_GEO_POCKET,
        Maker.SNK,
        1998,
        listOf("ngp"),
        listOf(NGP_EMU, LEMUROID),
        NGP_CORES,
    )
    val NEO_GEO_POCKET_COLOR = Classic(
        "ngpc",
        CheatSystem.NEO_GEO_POCKET_COLOR,
        Maker.SNK,
        1999,
        listOf("ngc", "ngpc", "npc"),
        listOf(NGP_EMU, LEMUROID),
        NGP_CORES,
    )
    val WONDERSWAN = Classic(
        "ws",
        CheatSystem.WONDERSWAN,
        Maker.BANDAI,
        1999,
        listOf("ws", "pc2"),
        listOf(SWAN_EMU, LEMUROID),
        WS_CORES,
    )
    val WONDERSWAN_COLOR = Classic(
        "wsc",
        CheatSystem.WONDERSWAN_COLOR,
        Maker.BANDAI,
        2000,
        listOf("wsc"),
        listOf(SWAN_EMU, LEMUROID),
        WS_CORES,
    )
    val ATARI_2600 = Classic(
        "a2600",
        CheatSystem.ATARI_2600,
        Maker.ATARI,
        1977,
        listOf("a26", "bin"),
        listOf(A2600_EMU, LEMUROID),
        listOf(core("Stella", "stella", "stella2014", "stella2023")),
    )
    val ATARI_5200 = Classic(
        "a5200",
        CheatSystem.ATARI_5200,
        Maker.ATARI,
        1982,
        listOf("a52", "car", "bin"),
        emptyList(),
        listOf(core("a5200", "a5200"), core("Atari800", "atari800")),
    )
    val ATARI_7800 = Classic(
        "a7800",
        CheatSystem.ATARI_7800,
        Maker.ATARI,
        1986,
        listOf("a78", "bin"),
        listOf(LEMUROID),
        listOf(core("ProSystem", "prosystem")),
    )
    val LYNX = Classic(
        "lynx",
        CheatSystem.LYNX,
        Maker.ATARI,
        1989,
        listOf("lnx", "lyx"),
        listOf(LYNX_EMU, LEMUROID),
        listOf(core("Handy", "handy"), core("Beetle Lynx", "mednafen_lynx"), core("Holani", "holani"), core("Gearlynx", "gearlynx")),
    )
    val JAGUAR = Classic(
        "jaguar",
        CheatSystem.JAGUAR,
        Maker.ATARI,
        1993,
        listOf("j64", "jag"),
        listOf(IRATA_JAGUAR),
        listOf(core("Virtual Jaguar", "virtualjaguar")),
    )
    val COLECOVISION = Classic(
        "coleco",
        CheatSystem.COLECOVISION,
        Maker.OTHER,
        1982,
        listOf("col", "cv"),
        listOf(MSX_EMU),
        listOf(core("blueMSX", "bluemsx"), core("Gearcoleco", "gearcoleco")),
    )
    val INTELLIVISION = Classic(
        "intv",
        CheatSystem.INTELLIVISION,
        Maker.OTHER,
        1979,
        listOf("int", "bin", "rom"),
        emptyList(),
        listOf(core("FreeIntv", "freeintv")),
    )
    val VECTREX = Classic(
        "vectrex",
        CheatSystem.VECTREX,
        Maker.OTHER,
        1982,
        listOf("vec", "gam", "bin"),
        emptyList(),
        listOf(core("vecx", "vecx")),
    )
    val ODYSSEY2 = Classic("odyssey2", CheatSystem.ODYSSEY2, Maker.OTHER, 1978, listOf("bin"), emptyList(), listOf(core("O2EM", "o2em")))
    val CHANNEL_F = Classic(
        "channelf",
        CheatSystem.CHANNEL_F,
        Maker.OTHER,
        1976,
        listOf("chf", "bin"),
        emptyList(),
        listOf(core("FreeChaF", "freechaf")),
    )
    val SUPERVISION = Classic(
        "supervision",
        CheatSystem.SUPERVISION,
        Maker.OTHER,
        1992,
        listOf("sv", "bin"),
        emptyList(),
        listOf(core("Potator", "potator")),
    )
    val MEGA_DUCK = Classic(
        "megaduck",
        CheatSystem.MEGA_DUCK,
        Maker.OTHER,
        1993,
        listOf("bin"),
        emptyList(),
        listOf(core("SameDuck", "sameduck")),
    )

    /** BIOS files of these consoles the BIOS table doesn't name, never listed as games. */
    val EXTRA_BIOS = setOf(
        "o2rom.bin", "c52.bin", "g7400.bin", "jopac.bin", "sl31253.bin", "sl31254.bin", "sl90025.bin",
        "syscard1.pce", "syscard2.pce", "syscard3.pce", "gexpress.pce",
    )
}
