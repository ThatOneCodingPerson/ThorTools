package io.github.thatonecodingperson.thortools.retroarch

/**
 * A game found in the ROM folders or in RetroArch's history: [path] as the Files app shows it, its file [name], its
 * console, and [tracks], the paths of the files its disc's sheet lists (a `.cue`'s `.bin` tracks).
 */
data class RomGame(val path: String, val name: String, val system: CheatSystem, val tracks: List<String> = emptyList()) {
    /** The name shown: the file name without its extensions. */
    val title: String get() = RomNames.stem(name)
}

/** The games RetroArch started ([games], newest first) and each game's starts in its history ([launches], by game path). */
data class PlayedGames(val games: List<RomGame> = emptyList(), val launches: Map<String, List<HistoryEntry>> = emptyMap())

/** The games in a ROM listing, and the games RetroArch started. */
object RomLibrary {
    /** Files that list a disc's tracks: the tracks beside them are part of that game, not games of their own. */
    private val SHEETS = setOf("cue", "gdi", "ccd", "toc")
    private val TRACKS = setOf("bin", "img", "raw", "wav", "sub", "iso")

    /** The games among [files], each with its console, by console and name; files of no known console are left out. */
    fun games(files: List<RomFile>): List<RomGame> = files.groupBy { it.path.substringBeforeLast('/') }.values.flatMap { folder ->
        val sheets = folder.filter { extension(it.name) in SHEETS }
        val gdi = sheets.any { extension(it.name) == "gdi" }
        val sheetBases = sheets.map { RomNames.base(it.name).lowercase() }
        fun isTrack(file: RomFile) = extension(file.name) in TRACKS &&
            (gdi || sheetBases.any { RomNames.base(file.name).lowercase().startsWith(it) })
        folder.mapNotNull { file ->
            val extension = extension(file.name) ?: return@mapNotNull null
            if (extension !in CheatSystems.gameExtensions || isTrack(file)) return@mapNotNull null
            val system = CheatSystems.of(file.path) ?: return@mapNotNull null
            val tracks = if (extension in SHEETS) {
                val base = RomNames.base(file.name).lowercase()
                folder.filter { isTrack(it) && (extension == "gdi" || RomNames.base(it.name).lowercase().startsWith(base)) }
                    .map { BiosPaths.shown(it.path) }
            } else {
                emptyList()
            }
            RomGame(BiosPaths.shown(file.path), file.name, system, tracks)
        }
    }.sortedWith(compareBy({ it.system.ordinal }, { it.name.lowercase() }))

    /**
     * The games in RetroArch's [history], newest first: the game of [games] with the same path or a track of it, else a
     * game of its own from the entry (its console from its file, else from its core when that plays one console only).
     * Entries whose console can't be told are left out.
     */
    fun played(games: List<RomGame>, history: List<HistoryEntry>, cores: List<CoreInfo>): PlayedGames {
        val byPath = HashMap<String, RomGame>()
        games.forEach { game ->
            byPath.putIfAbsent(game.path.lowercase(), game)
            game.tracks.forEach { byPath.putIfAbsent(it.lowercase(), game) }
        }
        val order = mutableListOf<RomGame>()
        val launches = LinkedHashMap<String, MutableList<HistoryEntry>>()
        history.forEach { entry ->
            val game = byPath[entry.path.lowercase()] ?: run {
                val system = CheatSystems.of(entry.path) ?: entry.core?.let { stem -> systemOf(cores.firstOrNull { it.stem == stem }) }
                    ?: return@forEach
                RomGame(entry.path, entry.contentName, system).also { byPath[entry.path.lowercase()] = it }
            }
            val starts = launches.getOrPut(game.path) { mutableListOf<HistoryEntry>().also { order += game } }
            starts += entry
        }
        return PlayedGames(order, launches)
    }

    private fun systemOf(core: CoreInfo?): CheatSystem? =
        core?.let { CheatSystem.entries.filter { system -> system.db != null && system.db in it.databases }.singleOrNull() }

    private fun extension(name: String) = RomNames.extensions(name).lastOrNull()
}

/**
 * What the user chose per game (by [RomGame.path]): the cheat file ([files]: a name, or null for none; no entry means
 * the best match), and the cores whose folders don't get it ([off]; every core of the game's console does by default).
 */
data class CheatChoices(val files: Map<String, String?> = emptyMap(), val off: Map<String, Set<String>> = emptyMap()) {
    fun encode(): String = buildString {
        appendLine(HEADER)
        files.forEach { (path, name) -> if (clean(path) && clean(name.orEmpty())) appendLine("file\t$path\t${name.orEmpty()}") }
        off.forEach { (path, cores) ->
            if (clean(path) && cores.all(::clean)) appendLine((listOf("off", path) + cores.sorted()).joinToString("\t"))
        }
    }

    companion object {
        private const val HEADER = "thortools-cheat-choices 2"

        /** Before version 2 the cores ticked were kept, from rules that picked the wrong ones; only the files are kept. */
        private const val HEADER_1 = "thortools-cheat-choices 1"

        fun decode(text: String): CheatChoices {
            val lines = text.lines()
            if (lines.firstOrNull() != HEADER && lines.firstOrNull() != HEADER_1) return CheatChoices()
            val files = mutableMapOf<String, String?>()
            val off = mutableMapOf<String, Set<String>>()
            lines.drop(1).map { it.split('\t') }.forEach { parts ->
                when {
                    parts[0] == "file" && parts.size == 3 -> files[parts[1]] = parts[2].ifEmpty { null }
                    parts[0] == "off" && parts.size >= 2 -> off[parts[1]] = parts.drop(2).toSet()
                }
            }
            return CheatChoices(files, off)
        }

        private fun clean(text: String) = '\t' !in text && '\n' !in text
    }
}

/**
 * A core whose folder in RetroArch's cheats folder can get the game's cheat file: [core] is that folder's name (the
 * name RetroArch uses), [lastPlayed] when RetroArch's history says the game was last started with it, [installed] the
 * game's cheat file already there, [takesCheats] what the core's info file says about cheat codes.
 */
data class CheatTarget(val core: String, val lastPlayed: Boolean, val installed: InstalledCheats?, val takesCheats: Boolean? = null)

/**
 * How a game stands in RetroArch: the [core] it was last played with (null when never), the game's cheat file in that
 * core's folder (else in another target's when never played) as [file], a cheat file of its name only in a folder
 * RetroArch doesn't read for it ([elsewhere]), and [noCore] when no installed core plays its console.
 */
data class CheatStatus(val core: String?, val file: InstalledCheats?, val elsewhere: String?, val noCore: Boolean)

/** Where a game's cheat file goes, under which names, and how the game stands. */
object CheatPlan {
    /** The cores RetroArch has installed that play [system]. */
    fun coresFor(system: CheatSystem, cores: List<CoreInfo>): List<CoreInfo> =
        cores.filter { system.db != null && system.db in it.databases }

    /**
     * The names RetroArch loads the game's cheat file under: the one of its last start first, then its file's, then those
     * of its other starts (a disc started by its track looks for the track's name).
     */
    fun names(game: RomGame, launches: List<HistoryEntry>, version: String?): List<String> =
        (launches.map { RomNames.cheatFile(it.contentName, version) } + RomNames.cheatFile(game.name, version))
            .distinctBy { it.lowercase() }

    /**
     * The cores the game's cheat file can go to: the installed core it was last started with, the other installed cores
     * it was started with, then every other installed core of its console; each in the folder RetroArch uses for it.
     */
    fun targets(game: RomGame, side: RetroArchSide, launches: List<HistoryEntry>, version: String?): List<CheatTarget> {
        val name = names(game, launches, version).first()
        val installedStems = side.cores.map { it.stem }.toSet()
        val started = launches.mapNotNull { it.core }.filter { it in installedStems }.distinct()
        val stems = (started + coresFor(game.system, side.cores).map { it.stem }).distinct()
        return stems.mapNotNull { stem -> side.folders[stem]?.let { stem to it } }
            .distinctBy { (_, folder) -> folder.lowercase() }
            .map { (stem, folder) ->
                CheatTarget(
                    core = folder,
                    lastPlayed = stem == started.firstOrNull() && launches.firstOrNull()?.core == stem,
                    installed = side.installed.firstOrNull { it.core.equals(folder, true) && it.name.equals(name, true) },
                    takesCheats = side.cores.firstOrNull { it.stem == stem }?.cheats,
                )
            }
    }

    /** The ticked cores: every target but the ones the user unticked for this game. */
    fun chosenCores(game: RomGame, targets: List<CheatTarget>, choices: CheatChoices): Set<String> {
        val off = choices.off[game.path].orEmpty().map { it.lowercase() }.toSet()
        return targets.map { it.core }.filter { it.lowercase() !in off }.toSet()
    }

    /**
     * Cheat files under the game's names in core folders that aren't [targets]' (where RetroArch doesn't look for it):
     * folders named after a core of the game's console, or after no installed core. A folder of a core of other consoles
     * only holds a game of the same name there.
     */
    fun elsewhere(game: RomGame, names: List<String>, side: RetroArchSide, targets: List<CheatTarget>): List<InstalledCheats> {
        val folders = targets.map { it.core.lowercase() }.toSet()
        val wanted = names.map { it.lowercase() }.toSet()
        val console = coresFor(game.system, side.cores).map { it.stem }.toSet()
        fun owners(folder: String) = side.cores.filter { core ->
            side.folders[core.stem].equals(folder, ignoreCase = true) ||
                CheatSystems.key(core.name) == CheatSystems.key(folder) ||
                CheatSystems.key(core.stem.removeSuffix("_libretro")) == CheatSystems.key(folder)
        }
        return side.installed.filter { file ->
            file.core.lowercase() !in folders &&
                file.name.lowercase() in wanted &&
                owners(file.core).let { cores -> cores.isEmpty() || cores.any { it.stem in console } }
        }
    }

    fun status(game: RomGame, side: RetroArchSide, launches: List<HistoryEntry>, version: String?): CheatStatus {
        val targets = targets(game, side, launches, version)
        val last = targets.firstOrNull { it.lastPlayed }
        val file = if (last != null) last.installed else targets.firstNotNullOfOrNull { it.installed }
        val elsewhere = if (file == null) {
            elsewhere(game, names(game, launches, version), side, targets).firstOrNull()?.core
                ?: targets.firstOrNull { it.installed != null && it != last }?.core
        } else {
            null
        }
        return CheatStatus(last?.core, file, elsewhere, targets.isEmpty())
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
