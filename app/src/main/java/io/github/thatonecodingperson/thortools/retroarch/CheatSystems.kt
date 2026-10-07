package io.github.thatonecodingperson.thortools.retroarch

/**
 * A console a ROM can be for. [db] is its name in libretro's database: the folder of its cheats (`cht/<db>/` on GitHub,
 * `<db>/` in the cheat pack) and the name RetroArch's core info files list under `database`; null for a console the
 * database has no cheats for, which is only recognised so the tab can say so. [label] is the short name shown, [aliases]
 * folder names that mean the console (letters and digits only, lower case) and [extensions] file extensions only its
 * games use.
 */
enum class CheatSystem(val db: String?, val label: String, val aliases: List<String>, val extensions: List<String> = emptyList()) {
    GB("Nintendo - Game Boy", "Game Boy", listOf("gb", "gameboy", "dmg"), listOf("gb", "dmg")),
    GBC("Nintendo - Game Boy Color", "Game Boy Color", listOf("gbc", "gameboycolor", "gameboycolour", "cgb"), listOf("gbc", "cgb")),
    GBA("Nintendo - Game Boy Advance", "Game Boy Advance", listOf("gba", "gameboyadvance", "agb"), listOf("gba", "agb")),
    NES(
        "Nintendo - Nintendo Entertainment System",
        "NES",
        listOf("nes", "famicom", "fc", "nintendoentertainmentsystem"),
        listOf("nes", "unf", "unif"),
    ),
    FDS("Nintendo - Family Computer Disk System", "Famicom Disk System", listOf("fds", "famicomdisksystem", "disksystem"), listOf("fds")),
    SNES(
        "Nintendo - Super Nintendo Entertainment System",
        "Super Nintendo",
        listOf("snes", "sfc", "superfamicom", "supernintendo", "supernes", "supernintendoentertainmentsystem"),
        listOf("sfc", "smc", "swc", "fig"),
    ),
    SATELLAVIEW("Nintendo - Satellaview", "Satellaview", listOf("satellaview", "bsx"), listOf("bs")),
    N64("Nintendo - Nintendo 64", "Nintendo 64", listOf("n64", "nintendo64"), listOf("n64", "z64", "v64")),
    NDS("Nintendo - Nintendo DS", "Nintendo DS", listOf("nds", "ds", "nintendods"), listOf("nds")),
    VIRTUAL_BOY("Nintendo - Virtual Boy", "Virtual Boy", listOf("vb", "virtualboy"), listOf("vb", "vboy")),
    PS1("Sony - PlayStation", "PlayStation", listOf("psx", "ps1", "ps", "playstation", "playstation1", "sonyplaystation")),
    PSP("Sony - PlayStation Portable", "PlayStation Portable", listOf("psp", "playstationportable"), listOf("cso")),
    MEGA_DRIVE(
        "Sega - Mega Drive - Genesis",
        "Mega Drive / Genesis",
        listOf("genesis", "megadrive", "md", "segagenesis", "segamegadrive", "megadrivegenesis"),
        listOf("md", "gen", "smd"),
    ),
    MASTER_SYSTEM(
        "Sega - Master System - Mark III",
        "Master System",
        listOf("sms", "mastersystem", "segamastersystem", "markiii"),
        listOf("sms"),
    ),
    GAME_GEAR("Sega - Game Gear", "Game Gear", listOf("gg", "gamegear", "segagamegear"), listOf("gg")),
    SG1000("Sega - SG-1000", "SG-1000", listOf("sg1000", "sg"), listOf("sg")),
    SEGA_32X("Sega - 32X", "32X", listOf("32x", "sega32x"), listOf("32x")),
    SEGA_CD("Sega - Mega-CD - Sega CD", "Mega-CD / Sega CD", listOf("segacd", "megacd", "scd")),
    SATURN("Sega - Saturn", "Saturn", listOf("saturn", "segasaturn")),
    DREAMCAST("Sega - Dreamcast", "Dreamcast", listOf("dc", "dreamcast", "segadreamcast"), listOf("gdi", "cdi")),
    PC_ENGINE(
        "NEC - PC Engine - TurboGrafx 16",
        "PC Engine / TurboGrafx-16",
        listOf("pce", "pcengine", "tg16", "turbografx", "turbografx16", "tgfx16"),
        listOf("pce"),
    ),
    PC_ENGINE_CD("NEC - PC Engine CD - TurboGrafx-CD", "PC Engine CD", listOf("pcecd", "pcenginecd", "tgcd", "turbografxcd", "tg16cd")),
    SUPERGRAFX("NEC - PC Engine SuperGrafx", "SuperGrafx", listOf("sgx", "supergrafx"), listOf("sgx")),
    PC_FX("NEC - PC-FX", "PC-FX", listOf("pcfx")),
    NEO_GEO_POCKET("SNK - Neo Geo Pocket", "Neo Geo Pocket", listOf("ngp", "neogeopocket"), listOf("ngp")),
    NEO_GEO_POCKET_COLOR(
        "SNK - Neo Geo Pocket Color",
        "Neo Geo Pocket Color",
        listOf("ngpc", "neogeopocketcolor", "neogeopocketcolour"),
        listOf("ngpc", "npc"),
    ),
    WONDERSWAN("Bandai - WonderSwan", "WonderSwan", listOf("ws", "wonderswan"), listOf("ws")),
    WONDERSWAN_COLOR("Bandai - WonderSwan Color", "WonderSwan Color", listOf("wsc", "wonderswancolor"), listOf("wsc")),
    LYNX("Atari - Lynx", "Lynx", listOf("lynx", "atarilynx"), listOf("lnx", "lyx")),
    ATARI_2600("Atari - 2600", "Atari 2600", listOf("atari2600", "a2600", "2600", "vcs"), listOf("a26")),
    ATARI_5200("Atari - 5200", "Atari 5200", listOf("atari5200", "a5200", "5200"), listOf("a52")),
    ATARI_7800("Atari - 7800", "Atari 7800", listOf("atari7800", "a7800", "7800"), listOf("a78")),
    JAGUAR("Atari - Jaguar", "Jaguar", listOf("jaguar", "atarijaguar"), listOf("j64", "jag")),
    ATARI_8BIT(
        "Atari - 8-bit Family",
        "Atari 8-bit",
        listOf("atari800", "atari8bit", "a800", "atarixl"),
        listOf("atr", "xex", "xfd", "atx"),
    ),
    COLECOVISION("Coleco - ColecoVision", "ColecoVision", listOf("coleco", "colecovision"), listOf("col")),
    INTELLIVISION("Mattel - Intellivision", "Intellivision", listOf("intellivision", "intv"), listOf("int")),
    MSX("Microsoft - MSX", "MSX", listOf("msx", "msx1"), listOf("mx1")),
    MSX2("Microsoft - MSX2", "MSX2", listOf("msx2"), listOf("mx2")),
    GX4000("Amstrad - GX4000", "GX4000", listOf("gx4000", "amstradgx4000")),
    ZX_SPECTRUM("Sinclair - ZX Spectrum +3", "ZX Spectrum +3", listOf("zxspectrum", "spectrum", "zx", "zxspectrum3")),
    THOMSON("Thomson - MOTO", "Thomson MO/TO", listOf("moto", "thomson", "to8")),
    ARCADE("FBNeo - Arcade Games", "Arcade (FinalBurn Neo)", listOf("fbneo", "arcade", "fba", "finalburn", "finalburnneo")),
    DOS("DOS", "DOS", listOf("dos", "msdos", "dosbox")),

    PS2(null, "PlayStation 2", listOf("ps2", "playstation2")),
    GAMECUBE(null, "GameCube", listOf("gc", "gamecube", "ngc", "gcn"), listOf("gcm", "gcz")),
    WII(null, "Wii", listOf("wii"), listOf("wbfs", "wad")),
    WII_U(null, "Wii U", listOf("wiiu"), listOf("wua", "wux", "rpx")),
    N3DS(null, "Nintendo 3DS", listOf("3ds", "n3ds", "nintendo3ds"), listOf("3ds", "cia", "cci", "cxi", "3dsx")),
    SWITCH(null, "Switch", listOf("switch", "nswitch", "nintendoswitch"), listOf("nsp", "xci")),
    PS3(null, "PlayStation 3", listOf("ps3", "playstation3")),
    PS_VITA(null, "PS Vita", listOf("psvita", "vita")),
    XBOX(null, "Xbox", listOf("xbox")),
    ;

    /** libretro's cheat database has a list for this console. */
    val hasCheats: Boolean get() = db != null
}

/** Which console a ROM is for: from its file extension where only one console uses it, else from the folders it lies in. */
object CheatSystems {
    /** Extensions any console's games may have: archives and disc images. */
    private val SHARED = setOf(
        "zip", "7z", "chd", "iso", "cue", "m3u", "bin", "img", "pbp", "ccd", "toc", "mds", "ecm", "rom", "rvz", "elf",
    )

    private val byExtension: Map<String, CheatSystem> =
        CheatSystem.entries.flatMap { system -> system.extensions.map { it to system } }.toMap()

    /** The aliases first, then each console's database name and label written as an alias; the first one given wins. */
    private val byAlias: Map<String, CheatSystem> = buildMap {
        CheatSystem.entries.forEach { system -> system.aliases.forEach { getOrPut(it) { system } } }
        CheatSystem.entries.forEach { system -> listOfNotNull(system.db, system.label).forEach { getOrPut(key(it)) { system } } }
    }

    /** Every extension a game file may have; anything else in the folders (pictures, saves, notes) isn't a game. */
    val gameExtensions: Set<String> = SHARED + byExtension.keys

    /** The console a folder named [name] is for, or null. */
    fun forFolder(name: String): CheatSystem? = byAlias[key(name)]

    /** The console only [extension] (any case) is used by, or null. */
    fun forExtension(extension: String): CheatSystem? = byExtension[extension.lowercase()]

    /**
     * The console of the game at [path]: an extension only one console uses (also the inner one of `.n64.zip`), else the
     * nearest folder above it with a console's name.
     */
    fun of(path: String): CheatSystem? {
        val name = path.substringAfterLast('/')
        RomNames.extensions(name).firstNotNullOfOrNull(::forExtension)?.let { return it }
        return path.substringBeforeLast('/', "").split('/').asReversed().firstNotNullOfOrNull { folder -> forFolder(folder) }
    }

    /** A name as aliases are written: letters and digits only, lower case. */
    fun key(name: String): String = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
}
