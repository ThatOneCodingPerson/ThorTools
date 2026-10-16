package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.CheatSystem

/**
 * What a game's disc is: a CD (2352-byte sectors, tracks, often a sheet), a DVD image (one file of 2048-byte sectors),
 * [CART]: a cartridge or card game, one ROM file, maybe in an archive, or [FOLDER]: a game kept as folders of files
 * (a Wii U game, its update and DLC).
 */
enum class DiscKind { CD, DVD, CART, FOLDER }

/** The program that makes a format ([NONE]: the plain image, unpacked). */
enum class RomTool { CHDMAN, MAXCSO, NOD, SEVENZIP, Z3DS, WUA, NONE }

/** Who made a console: the ROM page groups its consoles by maker, in this order. */
enum class Maker { NINTENDO, SONY, SEGA, NEC, SNK, BANDAI, ATARI, OTHER }

/** How a plain image shows it holds a console's disc: an ISO 9660 volume, or a GameCube or Wii disc header. */
enum class DiscCheck { ISO_9660, NINTENDO }

/**
 * A compressed format Thor Tools can make: the extension of its files, the program that makes it, the kind of disc it
 * is for and the options that program is given.
 */
enum class RomFormat(val extension: String, val tool: RomTool, val disc: DiscKind, val options: String = "") {
    /** MAME's compressed disc image of a CD, made with `chdman createcd` and its CD codecs. */
    CHD_CD("chd", RomTool.CHDMAN, DiscKind.CD),

    /** A DVD image as CHD with Zstandard in 16 KB hunks: a little bigger than [CHD_DVD], far less work to read. */
    CHD_DVD_ZSTD("chd", RomTool.CHDMAN, DiscKind.DVD, "-c zstd -hs 16384"),

    /** A DVD image as CHD with chdman's own choice of codecs (LZMA first) in 4 KB hunks: the smallest. */
    CHD_DVD("chd", RomTool.CHDMAN, DiscKind.DVD),

    /** A DVD image as CHD with Deflate only, which the older PCSX2-based emulators (NetherSX2) open too. */
    CHD_DVD_ZLIB("chd", RomTool.CHDMAN, DiscKind.DVD, "-c zlib"),

    /** maxcso's CSO, version 1 (Deflate). */
    CSO("cso", RomTool.MAXCSO, DiscKind.DVD),

    /** maxcso's ZSO (LZ4): the least work to read, the least saved. */
    ZSO("zso", RomTool.MAXCSO, DiscKind.DVD, "--format=zso"),

    /** Dolphin's RVZ, made by nod: Zstandard level 19 in 128 KiB blocks, the discs' padding kept as seeds. */
    RVZ("rvz", RomTool.NOD, DiscKind.DVD, "-c zstd:19"),

    /** A Wii disc as USB loaders keep it (unused blocks left out); read, never made. */
    WBFS("wbfs", RomTool.NOD, DiscKind.DVD),

    /** Wiimms' compressed format, RVZ's older sibling; read, never made. */
    WIA("wia", RomTool.NOD, DiscKind.DVD),

    /** A GameCube or Wii disc with its unused blocks left out (not PSP's CSO); read, never made. */
    CISO("ciso", RomTool.NOD, DiscKind.DVD),

    /** Dolphin's older compressed format (Deflate); read, never made. */
    GCZ("gcz", RomTool.NOD, DiscKind.DVD),

    /** The plain image, not compressed: what a compressed game turns back into. */
    ISO("iso", RomTool.NONE, DiscKind.DVD),

    /** A cartridge game in a ZIP file (Deflate): the one nearly every emulator and front end opens. */
    ZIP("zip", RomTool.SEVENZIP, DiscKind.CART, "-tzip -mx=7"),

    /** A cartridge game in a 7z file, LZMA2 with a 64 MB dictionary: the smallest. */
    SEVEN_Z("7z", RomTool.SEVENZIP, DiscKind.CART, "-t7z -m0=LZMA2 -mx=9 -md=64m"),

    /**
     * Azahar's compressed 3DS ROM: seekable Zstandard (level 12) in 256 KiB frames, `.zcci` for a card image, `.zcxi`
     * for a `.cxi`.
     */
    Z3DS("zcci", RomTool.Z3DS, DiscKind.CART, "-l 12"),

    /** The plain ROM file, not compressed; it keeps the name it has inside its archive. */
    ROM_FILE("", RomTool.NONE, DiscKind.CART),

    /** Cemu's WUA: a Wii U game, its update and DLC in one ZArchive file (Zstandard in 64 KiB blocks). */
    WUA("wua", RomTool.WUA, DiscKind.FOLDER),

    /** A Wii U game's folders (code, content, meta), not compressed. */
    FOLDERS("", RomTool.NONE, DiscKind.FOLDER),
    ;

    /** False for the formats that only leave out a disc's unused blocks (WBFS, CISO) and for a plain image or ROM. */
    val compressed: Boolean get() = this != WBFS && this != CISO && this != ISO && this != ROM_FILE && this != FOLDERS
}

/** How sure it is that an emulator opens a format. */
enum class Support {
    /** Tried on a Thor. */
    CONFIRMED,

    /** The emulator's own docs, changelog or code say so. */
    DOCUMENTED,

    /** Nothing found either way. */
    UNCONFIRMED,

    /** It doesn't. */
    NO,
}

/** An emulator opens [format] ([support]); from version [since] on when that is known (null: every version). */
data class Reads(val format: RomFormat, val support: Support, val since: String? = null)

/**
 * A standalone emulator: its [packages] (any one of them installed counts) and what it opens. [unlisted]: Cocoon or
 * Daijisho don't list its compressed games, though it opens them.
 */
data class RomEmulator(val name: String, val packages: List<String>, val reads: List<Reads>, val unlisted: Boolean = false)

/** A RetroArch core by the stems of its files (`swanstation_libretro`), and what it opens. */
data class RomCore(val name: String, val stems: List<String>, val reads: List<Reads>)

/** A front end (a games launcher) whose list of a console's games leaves out the files of [misses]. */
data class RomFrontEnd(val name: String, val packages: List<String>, val misses: List<RomFormat>)

/** Every listed format [support]ed the same way, [except] some. */
private fun readsAll(formats: List<RomFormat>, support: Support, except: Map<RomFormat, Reads> = emptyMap()): List<Reads> =
    formats.map { except[it] ?: Reads(it, support) }

private val PS2_FORMATS = listOf(
    RomFormat.CHD_CD,
    RomFormat.CHD_DVD_ZSTD,
    RomFormat.CHD_DVD,
    RomFormat.CHD_DVD_ZLIB,
    RomFormat.CSO,
    RomFormat.ZSO,
    RomFormat.ISO,
)
private val PSP_FORMATS = listOf(RomFormat.CSO, RomFormat.CHD_DVD_ZSTD, RomFormat.CHD_DVD, RomFormat.ISO)

private val NINTENDO_FORMATS = listOf(RomFormat.RVZ, RomFormat.ISO)

/** What GameCube and Wii games may come in besides a plain image: nod reads them all. */
private val NINTENDO_SOURCES = listOf(RomFormat.WBFS, RomFormat.WIA, RomFormat.CISO, RomFormat.GCZ)

/** Opens RVZ only when it is new enough or known to: everything opens a plain image. */
private fun rvz(support: Support, since: String? = null) =
    listOf(Reads(RomFormat.RVZ, support, since), Reads(RomFormat.ISO, Support.DOCUMENTED))

/** Dolphin and its forks on Android: the same for GameCube and Wii. */
private val DOLPHINS = listOf(
    RomEmulator("Dolphin", listOf("org.dolphinemu.dolphinemu", "org.dolphinemu.dolphinemu.debug"), rvz(Support.DOCUMENTED, "5.0-12188")),
    RomEmulator("Dolphin MMJR2", listOf("org.dolphinemu.mmjr"), rvz(Support.DOCUMENTED)),
    RomEmulator("PrimeHack", listOf("org.shiiion.primehack", "org.dolphinemu.primehack"), rvz(Support.DOCUMENTED)),
    RomEmulator("Dolphin MMJ / MMJR", listOf("org.mm.j", "org.mm.jr"), rvz(Support.UNCONFIRMED)),
    RomEmulator("Dolphin Ishiiruka", listOf("org.dolphin.ishiirukadark"), rvz(Support.NO)),
    RomEmulator("Dolphin Handheld", listOf("org.dolphinemu.handheld"), rvz(Support.UNCONFIRMED)),
    RomEmulator("Dolphin MMJR3", listOf("org.dolphinemu.mmjr3"), rvz(Support.UNCONFIRMED)),
    RomEmulator("DolphinCS", listOf("com.joeyos.dolphinemu"), rvz(Support.UNCONFIRMED)),
)

private val DOLPHIN_CORES = listOf(RomCore("Dolphin", listOf("dolphin_libretro"), readsAll(NINTENDO_FORMATS, Support.DOCUMENTED)))

/**
 * The folders of PPSSPP's memory stick (under a `PSP` folder): save data, states, homebrew and the like, never games
 * to compress, wherever the memory stick lies.
 */
val MEMORY_STICK_FOLDERS = setOf(
    "cheats", "game", "savedata", "screenshot", "system", "ppsspp_state", "textures", "plugins", "video", "audio", "nand",
    "shaders", "themes",
)

/**
 * A console Thor Tools can compress games for: its console in the folder table ([system]), the formats it can make for
 * each kind of disc (the first is the default), the usual saving in percent per format, the emulators and RetroArch
 * cores people use for it on Android, and front ends known to leave a format out. [takesIso]: `.iso` files are its
 * games (DVD images), and so are files with [otherImages]; [check]: what such an image must hold; [sources]: compressed
 * formats its games come in that Thor Tools reads but doesn't make; [usesSbi]: LibCrypt `.sbi` files sit beside its
 * games; [playlists]: its emulators open `.m3u` lists of discs.
 */
enum class RomConsole(
    val id: String,
    val system: CheatSystem,
    val formats: Map<DiscKind, List<RomFormat>>,
    val typicalSaving: Map<RomFormat, IntRange>,
    val emulators: List<RomEmulator>,
    val cores: List<RomCore>,
    val frontEnds: List<RomFrontEnd> = emptyList(),
    val takesIso: Boolean = false,
    /** Options a format gets for this console instead of its own (PSP: 2 KB hunks, as PPSSPP advises). */
    val formatOptions: Map<RomFormat, String> = emptyMap(),
    /** System identifiers (sector 16, offset 8) of discs that aren't games: a UMD's films and music. */
    val notGames: List<String> = emptyList(),
    val usesSbi: Boolean = false,
    val playlists: Boolean = false,
    val otherImages: List<String> = emptyList(),
    val check: DiscCheck = DiscCheck.ISO_9660,
    val sources: List<RomFormat> = emptyList(),
    /** Who made it and when it came out: the ROM page's order. */
    val maker: Maker = Maker.SONY,
    val year: Int = 0,
    /** A cartridge console's plain ROM types (lower case). */
    val romTypes: List<String> = emptyList(),
    /** How big a 7z of its games usually is next to a zip of them. */
    val sevenZipRatio: Double = 0.9,
) {
    PS1(
        id = "ps1",
        system = CheatSystem.PS1,
        formats = mapOf(DiscKind.CD to listOf(RomFormat.CHD_CD)),
        typicalSaving = mapOf(RomFormat.CHD_CD to 40..60),
        emulators = listOf(
            RomEmulator("ePSXe", listOf("com.epsxe.ePSXe"), listOf(Reads(RomFormat.CHD_CD, Support.DOCUMENTED, since = "2.0.16"))),
            RomEmulator("DuckStation", listOf("com.github.stenzek.duckstation"), listOf(Reads(RomFormat.CHD_CD, Support.DOCUMENTED))),
            RomEmulator("Lemuroid", listOf("com.swordfish.lemuroid"), listOf(Reads(RomFormat.CHD_CD, Support.DOCUMENTED))),
            RomEmulator(
                "FPse",
                listOf("com.emulator.fpse", "com.emulator.fpse64"),
                listOf(Reads(RomFormat.CHD_CD, Support.UNCONFIRMED)),
            ),
        ),
        cores = listOf(
            RomCore("SwanStation", listOf("swanstation_libretro"), listOf(Reads(RomFormat.CHD_CD, Support.DOCUMENTED))),
            RomCore("PCSX ReARMed", listOf("pcsx_rearmed_libretro"), listOf(Reads(RomFormat.CHD_CD, Support.DOCUMENTED))),
            RomCore(
                "Beetle PSX",
                listOf("mednafen_psx_libretro", "mednafen_psx_hw_libretro"),
                listOf(Reads(RomFormat.CHD_CD, Support.DOCUMENTED)),
            ),
        ),
        usesSbi = true,
        playlists = true,
        year = 1994,
    ),
    PS2(
        id = "ps2",
        system = CheatSystem.PS2,
        formats = mapOf(
            DiscKind.DVD to listOf(
                RomFormat.CHD_DVD_ZSTD,
                RomFormat.CHD_DVD,
                RomFormat.CHD_DVD_ZLIB,
                RomFormat.CSO,
                RomFormat.ZSO,
                RomFormat.ISO,
            ),
            DiscKind.CD to listOf(RomFormat.CHD_CD),
        ),
        typicalSaving = mapOf(
            RomFormat.CHD_DVD_ZSTD to 17..42,
            RomFormat.CHD_DVD to 20..45,
            RomFormat.CHD_DVD_ZLIB to 15..40,
            RomFormat.CSO to 15..40,
            RomFormat.ZSO to 10..35,
            RomFormat.CHD_CD to 20..45,
            RomFormat.ISO to 0..0,
        ),
        emulators = listOf(
            RomEmulator(
                "ARMSX2",
                listOf("com.armsx2", "com.armsx2.nightly", "come.nanodata.armsx2", "come.nanodata.armsx2.debug"),
                readsAll(
                    PS2_FORMATS,
                    Support.DOCUMENTED,
                    mapOf(RomFormat.CSO to Reads(RomFormat.CSO, Support.DOCUMENTED, since = "1.0.3")),
                ),
            ),
            RomEmulator("PSX2", listOf("com.izzy2lost.psx2"), readsAll(PS2_FORMATS, Support.DOCUMENTED)),
            RomEmulator("EmuCoreX", listOf("com.sbro.emucorex"), readsAll(PS2_FORMATS, Support.DOCUMENTED)),
            RomEmulator(
                "NetherSX2",
                listOf("xyz.aethersx2.android", "xyz.aethersx2.custom", "xyz.aethersx2.tturnip"),
                listOf(
                    Reads(RomFormat.CHD_CD, Support.DOCUMENTED),
                    Reads(RomFormat.CHD_DVD_ZLIB, Support.DOCUMENTED),
                    Reads(RomFormat.CHD_DVD, Support.NO),
                    Reads(RomFormat.CHD_DVD_ZSTD, Support.NO),
                    Reads(RomFormat.CSO, Support.UNCONFIRMED),
                    Reads(RomFormat.ZSO, Support.NO),
                    Reads(RomFormat.ISO, Support.DOCUMENTED),
                ),
            ),
            RomEmulator(
                "Play!",
                listOf("com.virtualapplications.play"),
                listOf(
                    Reads(RomFormat.CHD_CD, Support.DOCUMENTED),
                    Reads(RomFormat.CHD_DVD, Support.DOCUMENTED, since = "0.62"),
                    Reads(RomFormat.CHD_DVD_ZLIB, Support.DOCUMENTED, since = "0.62"),
                    Reads(RomFormat.CHD_DVD_ZSTD, Support.DOCUMENTED, since = "0.71"),
                    Reads(RomFormat.CSO, Support.DOCUMENTED),
                    Reads(RomFormat.ZSO, Support.NO),
                    Reads(RomFormat.ISO, Support.DOCUMENTED),
                ),
            ),
        ),
        cores = listOf(
            RomCore("ARMSX2", listOf("armsx2_libretro"), readsAll(PS2_FORMATS, Support.DOCUMENTED)),
            RomCore(
                "Play!",
                listOf("play_libretro"),
                readsAll(PS2_FORMATS, Support.DOCUMENTED, mapOf(RomFormat.ZSO to Reads(RomFormat.ZSO, Support.NO))),
            ),
            RomCore("LRPS2", listOf("pcsx2_libretro"), readsAll(PS2_FORMATS, Support.UNCONFIRMED)),
        ),
        frontEnds = listOf(
            RomFrontEnd("ES-DE", listOf("org.es_de.frontend", "org.es_de.frontend.galaxy"), listOf(RomFormat.ZSO)),
            RomFrontEnd("Daijisho", listOf("com.magneticchen.daijishou"), listOf(RomFormat.CSO, RomFormat.ZSO)),
            RomFrontEnd("Cocoon", listOf("rip.moth.cocoonshell"), listOf(RomFormat.ZSO)),
        ),
        takesIso = true,
        year = 2000,
    ),
    PSP(
        id = "psp",
        system = CheatSystem.PSP,
        formats = mapOf(DiscKind.DVD to listOf(RomFormat.CSO, RomFormat.CHD_DVD_ZSTD, RomFormat.CHD_DVD, RomFormat.ISO)),
        typicalSaving = mapOf(
            RomFormat.CHD_DVD_ZSTD to 28..43,
            RomFormat.CHD_DVD to 30..45,
            RomFormat.CSO to 22..37,
            RomFormat.ISO to 0..0,
        ),
        emulators = listOf(
            RomEmulator(
                "PPSSPP",
                listOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold", "org.ppsspp.ppsspplegacy", "org.ppsspp.ppssppvr"),
                listOf(
                    Reads(RomFormat.CHD_DVD_ZSTD, Support.DOCUMENTED, since = "1.17.1"),
                    Reads(RomFormat.CHD_DVD, Support.DOCUMENTED, since = "1.17"),
                    Reads(RomFormat.CSO, Support.DOCUMENTED),
                    Reads(RomFormat.ISO, Support.DOCUMENTED),
                ),
            ),
            RomEmulator("EmuCoreA", listOf("com.sbro.emucorea"), readsAll(PSP_FORMATS, Support.DOCUMENTED)),
            RomEmulator(
                "Lemuroid",
                listOf("com.swordfish.lemuroid"),
                listOf(
                    Reads(RomFormat.CHD_DVD_ZSTD, Support.NO),
                    Reads(RomFormat.CHD_DVD, Support.NO),
                    Reads(RomFormat.CSO, Support.DOCUMENTED),
                    Reads(RomFormat.ISO, Support.DOCUMENTED),
                ),
            ),
            RomEmulator(
                "Rocket PSP",
                listOf("com.emultech.rocketpsp"),
                listOf(Reads(RomFormat.CSO, Support.DOCUMENTED), Reads(RomFormat.ISO, Support.DOCUMENTED)),
            ),
        ),
        cores = listOf(RomCore("PPSSPP", listOf("ppsspp_libretro"), readsAll(PSP_FORMATS, Support.DOCUMENTED))),
        takesIso = true,
        formatOptions = mapOf(RomFormat.CHD_DVD_ZSTD to "-c zstd -hs 2048", RomFormat.CHD_DVD to "-hs 2048"),
        notGames = listOf("UMD VIDEO", "UMD AUDIO"),
        year = 2004,
    ),
    GAMECUBE(
        id = "gc",
        system = CheatSystem.GAMECUBE,
        formats = mapOf(DiscKind.DVD to NINTENDO_FORMATS),
        typicalSaving = mapOf(RomFormat.RVZ to 20..90, RomFormat.ISO to 0..0),
        emulators = DOLPHINS,
        cores = DOLPHIN_CORES,
        takesIso = true,
        playlists = true,
        otherImages = listOf("gcm"),
        check = DiscCheck.NINTENDO,
        sources = NINTENDO_SOURCES,
        maker = Maker.NINTENDO,
        year = 2001,
    ),
    WII(
        id = "wii",
        system = CheatSystem.WII,
        formats = mapOf(DiscKind.DVD to NINTENDO_FORMATS),
        typicalSaving = mapOf(RomFormat.RVZ to 10..90, RomFormat.ISO to 0..0),
        emulators = DOLPHINS,
        cores = DOLPHIN_CORES,
        takesIso = true,
        otherImages = listOf("gcm"),
        check = DiscCheck.NINTENDO,
        sources = NINTENDO_SOURCES,
        maker = Maker.NINTENDO,
        year = 2006,
    ),
    N3DS(
        id = "3ds",
        system = CheatSystem.N3DS,
        formats = mapOf(DiscKind.CART to listOf(RomFormat.Z3DS, RomFormat.ROM_FILE)),
        typicalSaving = mapOf(RomFormat.Z3DS to 15..55, RomFormat.ROM_FILE to 0..0),
        emulators = N3ds.EMULATORS,
        cores = N3ds.CORES,
        maker = Maker.NINTENDO,
        year = 2011,
        romTypes = N3ds.ROM_TYPES,
    ),
    WII_U(
        id = "wiiu",
        system = CheatSystem.WII_U,
        formats = mapOf(DiscKind.FOLDER to listOf(RomFormat.WUA, RomFormat.FOLDERS)),
        typicalSaving = mapOf(RomFormat.WUA to 10..30, RomFormat.FOLDERS to 0..0),
        emulators = WiiU.EMULATORS,
        cores = WiiU.CORES,
        maker = Maker.NINTENDO,
        year = 2012,
    ),
    NES(Classics.NES),
    FDS(Classics.FDS),
    SNES(Classics.SNES),
    SATELLAVIEW(Classics.SATELLAVIEW),
    N64(Classics.N64),
    VIRTUAL_BOY(Classics.VIRTUAL_BOY),
    GB(Classics.GB),
    GBC(Classics.GBC),
    GBA(Classics.GBA),
    NDS(Classics.NDS),
    POKEMON_MINI(Classics.POKEMON_MINI),
    SG1000(Classics.SG1000),
    MASTER_SYSTEM(Classics.MASTER_SYSTEM),
    GENESIS(Classics.GENESIS),
    SEGA_32X(Classics.SEGA_32X),
    GAME_GEAR(Classics.GAME_GEAR),
    PC_ENGINE(Classics.PC_ENGINE),
    SUPERGRAFX(Classics.SUPERGRAFX),
    NEO_GEO_POCKET(Classics.NEO_GEO_POCKET),
    NEO_GEO_POCKET_COLOR(Classics.NEO_GEO_POCKET_COLOR),
    WONDERSWAN(Classics.WONDERSWAN),
    WONDERSWAN_COLOR(Classics.WONDERSWAN_COLOR),
    ATARI_2600(Classics.ATARI_2600),
    ATARI_5200(Classics.ATARI_5200),
    ATARI_7800(Classics.ATARI_7800),
    LYNX(Classics.LYNX),
    JAGUAR(Classics.JAGUAR),
    COLECOVISION(Classics.COLECOVISION),
    INTELLIVISION(Classics.INTELLIVISION),
    VECTREX(Classics.VECTREX),
    ODYSSEY2(Classics.ODYSSEY2),
    CHANNEL_F(Classics.CHANNEL_F),
    SUPERVISION(Classics.SUPERVISION),
    MEGA_DUCK(Classics.MEGA_DUCK),
    ;

    /** A classic cartridge console, its facts in [classic]. */
    constructor(classic: Classic) : this(
        id = classic.id,
        system = classic.system,
        formats = mapOf(DiscKind.CART to CART_FORMATS),
        typicalSaving = classic.saving,
        emulators = classic.emulators,
        cores = classic.cores,
        maker = classic.maker,
        year = classic.year,
        romTypes = classic.romTypes,
        sevenZipRatio = classic.sevenZip,
    )

    /** A cartridge console: its games are ROM files, plain or in an archive. */
    val cartridges: Boolean get() = formats.containsKey(DiscKind.CART)

    /** The extensions of its compressed games, each with its format: the classic consoles' zip and 7z, 3DS's Z3DS. */
    val packedTypes: Map<String, RomFormat>
        get() = if (RomFormat.Z3DS in allFormats) N3ds.PACKED_TYPES else mapOf("zip" to RomFormat.ZIP, "7z" to RomFormat.SEVEN_Z)

    /**
     * The kind of game its list's "Compress into" is for: cartridges, folder games, else DVD images (CDs always get the
     * CD format).
     */
    val listKind: DiscKind
        get() = when {
            cartridges -> DiscKind.CART
            formats.containsKey(DiscKind.FOLDER) -> DiscKind.FOLDER
            else -> DiscKind.DVD
        }

    /** The options [format] gets for this console's games. */
    fun optionsFor(format: RomFormat): String = formatOptions[format] ?: format.options

    /** The formats a disc of [kind] can become; the first is the default. */
    fun formatsFor(kind: DiscKind): List<RomFormat> = formats[kind].orEmpty()

    /** Every format this console's games can be in once compressed. */
    val allFormats: List<RomFormat> get() = formats.values.flatten().distinct()

    /** The compressed formats its games may be found in: the ones it makes, then the ones it only reads. */
    val packedFormats: List<RomFormat> get() = (allFormats + sources).filter { it.tool != RomTool.NONE }.distinct()

    /** The size of a whole disc of this console holding [size] bytes or more; null when discs vary (PS1, PS2, PSP). */
    fun fullDisc(size: Long): Long? = when (this) {
        GAMECUBE -> GAMECUBE_DISC
        WII -> if (size > WII_DISC) WII_DUAL_LAYER else WII_DISC
        else -> null
    }

    /** The usual saving in percent of [format]. */
    fun saving(format: RomFormat): IntRange = typicalSaving[format] ?: typicalSaving.values.first()

    companion object {
        /** A GameCube disc, a Wii disc and a dual-layer Wii disc, in bytes. */
        const val GAMECUBE_DISC = 1_459_978_240L
        const val WII_DISC = 4_699_979_776L
        const val WII_DUAL_LAYER = 8_511_160_320L

        fun byId(id: String?): RomConsole? = entries.firstOrNull { it.id == id }

        fun of(system: CheatSystem?): RomConsole? = entries.firstOrNull { it.system == system }
    }
}

/** An installed app: its package and version name. */
data class InstalledApp(val packageName: String, val version: String?)

/** What an installed emulator or core can do with a format: [opens] is null when it isn't known. */
data class EmulatorCheck(
    val name: String,
    val version: String?,
    val support: Support,
    val needs: String? = null,
    /** Cocoon or Daijisho don't list its compressed games, though it opens them. */
    val unlisted: Boolean = false,
) {
    val opens: Boolean? get() = when (support) {
        Support.CONFIRMED, Support.DOCUMENTED -> true
        Support.NO -> false
        Support.UNCONFIRMED -> null
    }
}

object EmulatorChecks {
    /**
     * The console's emulators found among [installed] and RetroArch's installed [coreStems] (null: RetroArch isn't
     * there or its cores couldn't be read), each with whether it opens [format]. An emulator older than the version that
     * added the format says so in [EmulatorCheck.needs].
     */
    fun of(console: RomConsole, format: RomFormat, installed: List<InstalledApp>, coreStems: Set<String>?): List<EmulatorCheck> {
        val apps = console.emulators.mapNotNull { emulator ->
            val app = installed.firstOrNull { it.packageName in emulator.packages } ?: return@mapNotNull null
            val reads = emulator.reads.firstOrNull { it.format == format }
                ?: return@mapNotNull EmulatorCheck(emulator.name, app.version, Support.UNCONFIRMED, unlisted = emulator.unlisted)
            val since = reads.since
            if (since != null && app.version != null && compareVersions(app.version, since) < 0) {
                EmulatorCheck(emulator.name, app.version, Support.NO, needs = since, unlisted = emulator.unlisted)
            } else {
                EmulatorCheck(emulator.name, app.version, reads.support, unlisted = emulator.unlisted)
            }
        }
        val cores = coreStems.orEmpty().let { stems ->
            console.cores.filter { core -> core.stems.any { it in stems } }.map { core ->
                EmulatorCheck(
                    "RetroArch: ${core.name}",
                    null,
                    core.reads.firstOrNull { it.format == format }?.support ?: Support.UNCONFIRMED,
                )
            }
        }
        return apps + cores
    }

    /** True when at least one of [checks] is known to open the format. */
    fun anyOpens(checks: List<EmulatorCheck>): Boolean = checks.any { it.opens == true }

    /**
     * The format a disc of [kind] gets by default: the first of the console's formats one of the installed emulators is
     * known to open, else the first.
     */
    fun defaultFormat(console: RomConsole, kind: DiscKind, installed: List<InstalledApp>, coreStems: Set<String>?): RomFormat? {
        val formats = console.formatsFor(kind)
        return formats.filter { it.tool != RomTool.NONE }.firstOrNull { anyOpens(of(console, it, installed, coreStems)) }
            ?: formats.firstOrNull()
    }

    /** The installed front ends whose list of [console]'s games leaves [format] out. */
    fun frontEndsMissing(console: RomConsole, format: RomFormat, installed: List<InstalledApp>): List<String> =
        console.frontEnds.filter { end -> format in end.misses && installed.any { it.packageName in end.packages } }.map { it.name }

    /** Every package name the consoles know: emulators and front ends. */
    val knownPackages: Set<String>
        get() = RomConsole.entries.flatMap { console ->
            console.emulators.flatMap { it.packages } + console.frontEnds.flatMap { it.packages }
        }.toSet()

    /** Compares version names by their numbers (`v1.20.4`, `2.0.15`); missing parts count as 0. */
    fun compareVersions(a: String, b: String): Int {
        val left = numbers(a)
        val right = numbers(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val difference = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    private fun numbers(version: String): List<Int> =
        version.dropWhile { !it.isDigit() }.split('.', '-', ' ', '_').map { part -> part.takeWhile { it.isDigit() } }
            .takeWhile { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }
}
