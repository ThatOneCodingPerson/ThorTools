package io.github.thatonecodingperson.thortools.retroarch

/**
 * Where a preset from a link belongs and what to call it, guessed from the words of its file name, its folders and the
 * files it pulls in: a console's folder when it names consoles (the file name counts most), else the "Any console"
 * folder of the kind it names, else its repository's folder in "From links".
 */
object ShaderGuess {
    private val CAMEL = Regex("(?<=[a-z])(?=[A-Z])")
    private val SPLIT = Regex("[^A-Za-z0-9+]+")

    /** Whole words that name a console; short ones only ever match a whole word, so "rgb" never reads as "gb". */
    private val CONSOLE_WORDS: Map<String, List<CheatSystem>> = buildMap {
        fun add(system: CheatSystem, vararg words: String) = words.forEach { put(it, (get(it).orEmpty() + system).distinct()) }
        add(CheatSystem.GB, "gb", "dmg", "gameboy", "pocket", "gbp", "gblight")
        add(CheatSystem.GBC, "gbc", "cgb", "gameboycolor", "gameboycolour", "gambatte", "frontlit")
        add(CheatSystem.GB, "sameboy")
        add(CheatSystem.GBC, "sameboy")
        add(
            CheatSystem.GBA,
            "gba", "agb", "agb001", "ags", "ags001", "sp101", "gbmicro", "gameboyadvance", "gameboyadvanced", "gameboysp", "mgba", "vba",
        )
        add(CheatSystem.NDS, "nds", "ds", "dslite", "dsi", "ndsl", "nintendods", "melonds", "desmume")
        add(CheatSystem.PSP, "psp", "ppsspp")
        add(CheatSystem.N3DS, "3ds", "n3ds", "nintendo3ds", "citra")
        add(CheatSystem.NES, "nes", "famicom", "fc", "fds", "nestopia", "fceumm", "mesen")
        add(CheatSystem.SNES, "snes", "sfc", "superfamicom", "supernes", "supernintendo", "snes9x", "bsnes")
        add(CheatSystem.N64, "n64", "nintendo64", "mupen")
        add(CheatSystem.NEO_GEO_POCKET_COLOR, "neogeopocketcolor", "neogeopocketcolour")
        add(CheatSystem.MEGA_DRIVE, "md", "genesis", "megadrive", "genplusgx", "blastem", "picodrive")
        add(CheatSystem.MASTER_SYSTEM, "sms", "mastersystem")
        add(CheatSystem.GAME_GEAR, "gg", "gamegear")
        add(CheatSystem.SEGA_CD, "segacd", "megacd")
        add(CheatSystem.SATURN, "saturn")
        add(CheatSystem.DREAMCAST, "dreamcast", "flycast")
        add(CheatSystem.PS1, "psx", "ps1", "playstation", "duckstation", "swanstation")
        add(CheatSystem.PC_ENGINE, "pce", "pcengine", "turbografx", "turbografx16", "tg16")
        add(CheatSystem.NEO_GEO_POCKET, "ngp", "neogeopocket")
        add(CheatSystem.NEO_GEO_POCKET_COLOR, "ngpc")
        add(CheatSystem.WONDERSWAN, "ws", "wonderswan")
        add(CheatSystem.WONDERSWAN_COLOR, "wsc")
        add(CheatSystem.LYNX, "lynx")
        add(CheatSystem.VIRTUAL_BOY, "vb", "virtualboy")
    }

    /** Word parts that name a kind; they count inside longer words too ("crtglow", "lcd3x", "superxbr"). */
    private val KIND_PARTS: List<Pair<String, ShaderKind>> = listOf(
        "scanline" to ShaderKind.SCANLINES,
        "crt" to ShaderKind.CRT, "trinitron" to ShaderKind.CRT, "pvm" to ShaderKind.CRT, "bvm" to ShaderKind.CRT,
        "ntsc" to ShaderKind.CRT, "composite" to ShaderKind.CRT, "svideo" to ShaderKind.CRT, "vhs" to ShaderKind.CRT,
        "royale" to ShaderKind.CRT, "lottes" to ShaderKind.CRT, "hyllian" to ShaderKind.CRT, "geom" to ShaderKind.CRT,
        "xbr" to ShaderKind.SMOOTH, "hqx" to ShaderKind.SMOOTH, "hq2x" to ShaderKind.SMOOTH, "hq3x" to ShaderKind.SMOOTH,
        "hq4x" to ShaderKind.SMOOTH, "scalefx" to ShaderKind.SMOOTH, "sabr" to ShaderKind.SMOOTH, "eagle" to ShaderKind.SMOOTH,
        "omniscale" to ShaderKind.SMOOTH, "smooth" to ShaderKind.SMOOTH, "2xsai" to ShaderKind.SMOOTH, "mmpx" to ShaderKind.SMOOTH,
        "lcd" to ShaderKind.LCD, "grid" to ShaderKind.LCD, "dotmatrix" to ShaderKind.LCD, "handheld" to ShaderKind.LCD,
        "sharp" to ShaderKind.SHARP, "bilinear" to ShaderKind.SHARP, "pixel" to ShaderKind.SHARP, "nearest" to ShaderKind.SHARP,
        "bicubic" to ShaderKind.SHARP, "lanczos" to ShaderKind.SHARP, "interpolation" to ShaderKind.SHARP,
    )

    /** Whole words that name a kind, too short to count inside others. */
    private val KIND_WORDS = mapOf(
        "pal" to ShaderKind.CRT,
        "tv" to ShaderKind.CRT,
        "rf" to ShaderKind.CRT,
        "aa" to ShaderKind.SHARP,
        "dot" to ShaderKind.LCD,
        "edge" to ShaderKind.SMOOTH,
    )

    /** The longest run of words joined that names a console is read first ("game boy color"), its words then don't count alone. */
    private const val PHRASE = 3

    /** Folder words every repository has, which say nothing. */
    private val NOISE =
        setOf("shaders", "shader", "presets", "preset", "resources", "res", "include", "src", "slang", "glsl", "main", "master")

    private const val NAME = 3
    private const val FOLDER = 2
    private const val PULLED = 1

    /** The words of [text]: split at anything but letters and digits and at camelCase, lower case. */
    fun words(text: String): List<String> =
        text.split(SPLIT).flatMap { it.split(CAMEL) }.map { it.lowercase() }.filter { it.isNotEmpty() && it !in NOISE }

    /**
     * The folders a preset at [path] (in its repository) goes to: the consoles it names most ([pulled]: the paths of
     * the files it pulls in, counting least), else the kind it names, else [linkFolder].
     */
    fun place(path: String, pulled: List<String> = emptyList(), linkFolder: String): List<String> {
        val name = words(path.substringAfterLast('/').substringBeforeLast('.'))
        val folders = path.substringBeforeLast('/', "").split('/').flatMap(::words)
        val deps = pulled.flatMap { words(it.substringBeforeLast('.')) }
        val consoles = mutableMapOf<CheatSystem, Int>()
        fun count(words: List<String>, weight: Int) = consoleNames(words).distinct().forEach { word ->
            CONSOLE_WORDS[word]?.forEach { consoles[it] = (consoles[it] ?: 0) + weight }
        }
        count(name, NAME)
        count(folders, FOLDER)
        count(deps, PULLED)
        val best = consoles.values.maxOrNull() ?: 0
        if (best >= FOLDER) return consoles.filterValues { it == best }.keys.sortedBy { it.ordinal }.map(ShaderShortcuts::consoleFolder)
        val kinds = mutableMapOf<ShaderKind, Int>()
        fun countKinds(words: List<String>, weight: Int) = words.distinct().forEach { word ->
            val kind = KIND_WORDS[word] ?: KIND_PARTS.firstOrNull { (part, _) -> part in word }?.second
            if (kind != null) kinds[kind] = (kinds[kind] ?: 0) + weight
        }
        countKinds(name, NAME)
        countKinds(folders, FOLDER)
        countKinds(deps, PULLED)
        val kind = kinds.entries.sortedWith(compareByDescending<Map.Entry<ShaderKind, Int>> { it.value }.thenBy { it.key.ordinal })
            .firstOrNull()?.key
        return listOf(kind?.let(ShaderShortcuts::kindFolder) ?: linkFolder)
    }

    /** The console names among [words], runs of words joined first: "game", "boy", "color" gives "gameboycolor" alone. */
    private fun consoleNames(words: List<String>): List<String> {
        val found = mutableListOf<String>()
        var at = 0
        while (at < words.size) {
            val run = (PHRASE downTo 1).firstNotNullOfOrNull { length ->
                words.subList(at, minOf(at + length, words.size)).takeIf { it.size == length }?.joinToString("")
                    ?.takeIf { it in CONSOLE_WORDS }?.let { it to length }
            }
            if (run != null) {
                found += run.first
                at += run.second
            } else {
                at++
            }
        }
        return found
    }

    /** Words kept as they are written best. */
    private val SPELLING = mapOf(
        "gameboy" to "Game Boy", "gb" to "GB", "gbc" to "GBC", "gba" to "GBA", "nds" to "DS", "ds" to "DS", "dsi" to "DSi",
        "3ds" to "3DS", "psp" to "PSP", "psx" to "PSX", "ps1" to "PS1", "n64" to "N64", "nes" to "NES", "snes" to "SNES",
        "sms" to "SMS", "gg" to "GG", "md" to "MD", "crt" to "CRT", "lcd" to "LCD", "ntsc" to "NTSC", "pal" to "PAL",
        "rgb" to "RGB", "vga" to "VGA", "hd" to "HD", "4k" to "4K", "aa" to "AA", "fx" to "FX", "dmg" to "DMG",
        "xbr" to "xBR", "xbrz" to "xBRZ", "hq2x" to "HQ2x", "hq3x" to "HQ3x", "hq4x" to "HQ4x", "scalefx" to "ScaleFX",
        "sabr" to "SABR", "pvm" to "PVM", "bvm" to "BVM", "vhs" to "VHS", "tv" to "TV", "agb001" to "AGB-001",
        "ags001" to "AGS-001", "mmpx" to "MMPX", "ossc" to "OSSC", "gpu" to "GPU", "lut" to "LUT", "sgb" to "SGB",
        "hdr" to "HDR", "svideo" to "S-Video", "nintendo" to "Nintendo", "sega" to "Sega", "sony" to "Sony",
    )

    /** Names written as their makers write them, whatever case the file name has. */
    private val PHRASES =
        listOf("Mega Bezel", "Super Nintendo", "Game Boy", "Game Gear", "Mega Drive", "Master System", "Neo Geo", "PC Engine")

    /** Two numbers of two digits at most after each other ("4-3", "10-9") are an aspect ratio. */
    private val RATIO = Regex("""\d{1,2}""")

    /**
     * A plain name for the preset at [path]: its file name in words, the first one capitalised, known ones spelled
     * right, aspect ratios as "4x3".
     */
    fun name(path: String): String {
        val stem = path.substringAfterLast('/').substringBeforeLast('.')
        val parts = stem.split(Regex("[-_ ]+")).filter { it.isNotEmpty() }
        val joined = mutableListOf<String>()
        parts.forEach { part ->
            val last = joined.lastOrNull()
            if (last != null && RATIO.matches(last) && RATIO.matches(part)) joined[joined.lastIndex] = "${last}x$part" else joined += part
        }
        val words = joined.mapIndexed { index, part ->
            SPELLING[part.lowercase()] ?: if (index == 0) part.replaceFirstChar { it.uppercaseChar() } else part
        }
        return PHRASES.fold(words.joinToString(" ")) { name, phrase ->
            name.replace(Regex("\\b${Regex.escape(phrase)}\\b", RegexOption.IGNORE_CASE), phrase)
        }.ifEmpty { stem }
    }
}
