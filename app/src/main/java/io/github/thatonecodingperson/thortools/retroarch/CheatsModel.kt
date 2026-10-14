package io.github.thatonecodingperson.thortools.retroarch

/** The note after the cheats tab did something. */
sealed class CheatNote {
    /** Getting cheats failed: no internet, GitHub's limit, no pack, ... */
    data class Problem(val problem: CheatProblem) : CheatNote()

    /** Root couldn't look through the folders. */
    data object ScanFailed : CheatNote()

    /** The folder picked is another app's, with no path root could read. */
    data object Unusable : CheatNote()

    /** The cheat file went into the folders of [cores], [on] cheats switched on; each core it didn't go to, with why. */
    data class Added(val name: String, val cores: List<String>, val on: Int, val failed: List<Pair<String, String>>) : CheatNote()

    data object PackReady : CheatNote()
}

/**
 * The game open on the cheats tab: the cheat files that may be its ([candidates], else [similar] ones), the one in use
 * ([pick]; null for none) and its [file], the cheats ticked, the names RetroArch loads it under ([names]), the cores
 * whose folders can get it ([targets]) and the ticked ones ([cores]), and the search texts. Of RetroArch's copies:
 * [same] the cores whose file has the same cheats as [file], [other] those whose file has other cheats (another game's,
 * or ones saved in RetroArch), [elsewhere] copies in folders RetroArch doesn't read for this game.
 */
data class OpenGame(
    val game: RomGame,
    val candidates: List<CheatPick> = emptyList(),
    val similar: List<CheatPick> = emptyList(),
    val pick: CheatPick? = null,
    val file: ChtFile? = null,
    val loading: Boolean = false,
    val ticks: Set<Int> = emptySet(),
    val cheatSearch: String = "",
    val fileSearch: String = "",
    val names: List<String> = emptyList(),
    val targets: List<CheatTarget> = emptyList(),
    val cores: Set<String> = emptySet(),
    val same: Set<String> = emptySet(),
    val other: Set<String> = emptySet(),
    val elsewhere: List<InstalledCheats> = emptyList(),
    val writing: Boolean = false,
    /** A change waits to be saved by itself. */
    val pending: Boolean = false,
    /** The cores the last save by itself went to. */
    val saved: List<String>? = null,
) {
    /** The cheats whose description holds every word of [cheatSearch], and how many there are in all. */
    fun shownCheats(limit: Int): Pair<List<Cheat>, Int> {
        val cheats = file?.cheats.orEmpty()
        val words = cheatSearch.lowercase().split(' ').filter { it.isNotBlank() }
        val hits = if (words.isEmpty()) cheats else cheats.filter { cheat -> words.all { cheat.description.lowercase().contains(it) } }
        return hits.take(limit) to hits.size
    }

    /** The ticked cores' cheat files already in RetroArch. */
    val installed: List<InstalledCheats> get() = targets.filter { it.core in cores }.mapNotNull { it.installed }

    /** The game's cheats are in RetroArch, so changes save by themselves (into the ticked cores without other cheats). */
    val added: Boolean get() = file != null && same.isNotEmpty()

    /** The ticked cores a save by itself goes to. */
    val autoCores: Set<String> get() = cores - other
}

/** The games a console's list shows: the ones RetroArch played, then the others, and how many there are in all. */
data class ShownGames(val played: List<RomGame>, val others: List<RomGame>, val total: Int) {
    val count: Int get() = played.size + others.size
}

/**
 * The cheats tab: where cheats come from and the downloaded pack, the ROM folders and what was found in them, what
 * RetroArch has and the games it started, the console and search the game list shows, each console's cheat list once
 * fetched with each game's best file in it ([picks], by game path), the user's choices and the open game.
 */
data class RaCheats(
    val loaded: Boolean = false,
    val source: CheatSourceKind = CheatSourceKind.GITHUB,
    val pack: PackInfo? = null,
    /** Bytes so far and in all while the pack downloads. */
    val packProgress: Pair<Long, Long>? = null,
    val folders: List<String> = emptyList(),
    val listing: RomListing? = null,
    val games: List<RomGame> = emptyList(),
    val played: PlayedGames = PlayedGames(),
    val side: RetroArchSide? = null,
    val scanning: Boolean = false,
    val system: CheatSystem? = null,
    val search: String = "",
    val indexes: Map<CheatSystem, CheatIndex> = emptyMap(),
    val picks: Map<String, CheatPick?> = emptyMap(),
    val loading: Set<CheatSystem> = emptySet(),
    val choices: CheatChoices = CheatChoices(),
    val game: OpenGame? = null,
    val note: CheatNote? = null,
) {
    /** The games found in the folders, and the ones RetroArch played that aren't in them. */
    val allGames: List<RomGame> by lazy {
        val paths = games.map { it.path }.toSet()
        games + played.games.filter { it.path !in paths }
    }

    /** The consoles with cheats that games were found or played for, with how many, in the order of [CheatSystem]. */
    val systems: List<Pair<CheatSystem, Int>>
        get() = allGames.groupingBy { it.system }.eachCount().filterKeys { it.hasCheats }.toList().sortedBy { it.first.ordinal }

    /** The consoles games were found or played for that libretro has no cheats for, with how many. */
    val withoutCheats: List<Pair<CheatSystem, Int>>
        get() = allGames.groupingBy { it.system }.eachCount().filterKeys { !it.hasCheats }.toList().sortedBy { it.first.ordinal }

    /**
     * The games of [system] whose names hold every word of [search]: the ones RetroArch played first (the last played
     * first), then the others by name, together at most [limit]; and how many there are in all.
     */
    fun shownGames(limit: Int): ShownGames {
        val words = search.lowercase().split(' ').filter { it.isNotBlank() }
        fun fits(game: RomGame) = game.system == system && words.all { game.name.lowercase().contains(it) }
        val playedHere = played.games.filter(::fits)
        val playedPaths = playedHere.map { it.path }.toSet()
        val others = games.filter { fits(it) && it.path !in playedPaths }
        val shownPlayed = playedHere.take(limit)
        return ShownGames(shownPlayed, others.take(limit - shownPlayed.size), playedHere.size + others.size)
    }

    fun launches(game: RomGame): List<HistoryEntry> = played.launches[game.path].orEmpty()

    /** How [game] stands in RetroArch ([version]: RetroArch's); null until RetroArch's side is read. */
    fun status(game: RomGame, version: String?): CheatStatus? = side?.let { CheatPlan.status(game, it, launches(game), version) }

    /** The played games whose cheats lie only in folders RetroArch doesn't read for them. */
    fun misplaced(version: String?): List<RomGame> =
        played.games.filter { game -> status(game, version)?.let { it.file == null && it.elsewhere != null } == true }

    /** The cheat file in use for [game]: null while its console's list isn't here, or when none fits or the user chose none. */
    fun pick(game: RomGame): CheatPick? = if (game.path in
        picks
    ) {
        picks[game.path]
    } else {
        indexes[game.system]?.let { CheatPlan.pick(game, it, choices) }
    }
}
