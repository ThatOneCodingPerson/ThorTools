package io.github.thatonecodingperson.thortools.retroarch

/** A game found in the ROM folders: [path] as the Files app shows it, its file [name], its console. */
data class RomGame(val path: String, val name: String, val system: CheatSystem) {
    /** The name shown: the file name without its extensions. */
    val title: String get() = RomNames.stem(name)
}

/** The games in a ROM listing. */
object RomLibrary {
    /** Files that list a disc's tracks: the tracks beside them are part of that game, not games of their own. */
    private val SHEETS = setOf("cue", "gdi", "ccd", "toc")
    private val TRACKS = setOf("bin", "img", "raw", "wav", "sub", "iso")

    /** The games among [files], each with its console, by console and name; files of no known console are left out. */
    fun games(files: List<RomFile>): List<RomGame> = files.groupBy { it.path.substringBeforeLast('/') }.values.flatMap { folder ->
        val sheets = folder.filter { extension(it.name) in SHEETS }
        val gdi = sheets.any { extension(it.name) == "gdi" }
        val sheetBases = sheets.map { RomNames.base(it.name).lowercase() }
        folder.mapNotNull { file ->
            val extension = extension(file.name) ?: return@mapNotNull null
            val track = extension in TRACKS && (gdi || sheetBases.any { RomNames.base(file.name).lowercase().startsWith(it) })
            if (extension !in CheatSystems.gameExtensions || track) return@mapNotNull null
            val system = CheatSystems.of(file.path) ?: return@mapNotNull null
            RomGame(BiosPaths.shown(file.path), file.name, system)
        }
    }.sortedWith(compareBy({ it.system.ordinal }, { it.name.lowercase() }))

    private fun extension(name: String) = RomNames.extensions(name).lastOrNull()
}

/**
 * What the user chose per game (by [RomGame.path]): the cheat file ([files]: a name, or null for none; no entry means
 * the best match), and the cores whose folders get it ([cores]; no entry means the default ones).
 */
data class CheatChoices(val files: Map<String, String?> = emptyMap(), val cores: Map<String, Set<String>> = emptyMap()) {
    fun encode(): String = buildString {
        appendLine(HEADER)
        files.forEach { (path, name) -> if (clean(path) && clean(name.orEmpty())) appendLine("file\t$path\t${name.orEmpty()}") }
        cores.forEach { (path, chosen) ->
            if (clean(path) && chosen.all(::clean)) appendLine((listOf("cores", path) + chosen.sorted()).joinToString("\t"))
        }
    }

    companion object {
        private const val HEADER = "thortools-cheat-choices 1"

        fun decode(text: String): CheatChoices {
            val lines = text.lines()
            if (lines.firstOrNull() != HEADER) return CheatChoices()
            val files = mutableMapOf<String, String?>()
            val cores = mutableMapOf<String, Set<String>>()
            lines.drop(1).map { it.split('\t') }.forEach { parts ->
                when {
                    parts[0] == "file" && parts.size == 3 -> files[parts[1]] = parts[2].ifEmpty { null }
                    parts[0] == "cores" && parts.size >= 2 -> cores[parts[1]] = parts.drop(2).toSet()
                }
            }
            return CheatChoices(files, cores)
        }

        private fun clean(text: String) = '\t' !in text && '\n' !in text
    }
}

/**
 * A core whose folder in RetroArch's cheats folder can get the game's cheat file: [core] is the folder's name (the core's
 * own name), [played] when the game's saves or states are in that core's folders, [installed] the cheat file already
 * there.
 */
data class CheatTarget(val core: String, val played: Boolean, val installed: InstalledCheats?)

/** Where a game's cheat file goes, under which name, and how the game stands. */
object CheatPlan {
    /** The cores RetroArch has installed that play [system]. */
    fun coresFor(system: CheatSystem, cores: List<CoreInfo>): List<CoreInfo> =
        cores.filter { system.db != null && system.db in it.databases }

    /** The folder RetroArch uses for [core]: one already there under its name in any case, else its name. */
    fun folderName(core: CoreInfo, folders: Set<String>): String =
        folders.firstOrNull { it.equals(core.name, ignoreCase = true) } ?: core.name

    /**
     * The cores the game's cheat file can go to: the ones its saves or states are in, the ones that have its cheat file
     * already, then every other installed core for its console. An installed core of other consoles only doesn't count
     * for saves of a game of the same name.
     */
    fun targets(game: RomGame, side: RetroArchSide, version: String?): List<CheatTarget> {
        val file = RomNames.cheatFile(game.name, version)
        val base = RomNames.base(game.name)
        val installed = coresFor(game.system, side.cores).map { folderName(it, side.coreFolders) }
        val otherConsoles = side.cores.map { folderName(it, side.coreFolders) }.toSet() - installed.toSet()
        val played = side.played.filter { (core, files) -> core !in otherConsoles && files.any { it.startsWith("$base.") } }.keys.sorted()
        val existing = side.installed.filter { it.name == file }.map { it.core }.sorted()
        return (played + existing + installed).distinct().map { core ->
            CheatTarget(core, core in played, side.installed.firstOrNull { it.core == core && it.name == file })
        }
    }

    /** The cores ticked when the user hasn't chosen: the ones the game was played with and that have its file, else all. */
    fun defaultCores(targets: List<CheatTarget>): Set<String> {
        val used = targets.filter { it.played || it.installed != null }
        return (used.ifEmpty { targets }).map { it.core }.toSet()
    }

    /** The ticked cores: the user's choice among [targets] when there is one, else [defaultCores]. */
    fun chosenCores(game: RomGame, targets: List<CheatTarget>, choices: CheatChoices): Set<String> {
        val chosen = choices.cores[game.path] ?: return defaultCores(targets)
        return chosen.filter { core -> targets.any { it.core == core } }.toSet()
    }

    /**
     * The cheat file for the game: the user's choice when it is still in [index] (null when the user chose none), else the
     * best match; null as well when nothing has the game's title.
     */
    fun pick(game: RomGame, index: CheatIndex, choices: CheatChoices): CheatPick? {
        if (game.path in choices.files) {
            val name = choices.files[game.path] ?: return null
            val chosen = index.find(name)
            if (chosen != null) {
                return index.candidates(game.name).firstOrNull { it.file == chosen } ?: CheatPick(chosen, CheatFit.SIMILAR)
            }
        }
        return index.best(game.name)
    }

    /** The other games of the same console RetroArch gives the same cheat file name, so they would share one file. */
    fun sharing(game: RomGame, games: List<RomGame>, version: String?): List<RomGame> {
        val file = RomNames.cheatFile(game.name, version)
        return games.filter { it.system == game.system && it.path != game.path && RomNames.cheatFile(it.name, version) == file }
    }
}
