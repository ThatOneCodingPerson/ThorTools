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
 * ([pick]; null for none) and its [file], the cheats ticked, the cores whose folders can get it ([targets]) and the
 * ticked ones ([cores]), and the search texts.
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
    val targets: List<CheatTarget> = emptyList(),
    val cores: Set<String> = emptySet(),
    val writing: Boolean = false,
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
}

/**
 * The cheats tab: where cheats come from and the downloaded pack, the ROM folders and what was found in them, what
 * RetroArch has, the console and search the game list shows, each console's cheat list once fetched, the user's choices
 * and the open game.
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
    val side: RetroArchSide? = null,
    val scanning: Boolean = false,
    val system: CheatSystem? = null,
    val search: String = "",
    val indexes: Map<CheatSystem, CheatIndex> = emptyMap(),
    val loading: Set<CheatSystem> = emptySet(),
    val choices: CheatChoices = CheatChoices(),
    val game: OpenGame? = null,
    val note: CheatNote? = null,
) {
    /** The consoles with cheats that games were found for, with how many, in the order of [CheatSystem]. */
    val systems: List<Pair<CheatSystem, Int>>
        get() = games.groupingBy { it.system }.eachCount().filterKeys { it.hasCheats }.toList().sortedBy { it.first.ordinal }

    /** The consoles games were found for that libretro has no cheats for, with how many. */
    val withoutCheats: List<Pair<CheatSystem, Int>>
        get() = games.groupingBy { it.system }.eachCount().filterKeys { !it.hasCheats }.toList().sortedBy { it.first.ordinal }

    /** The games of [system] whose names hold every word of [search], and how many there are in all. */
    fun shownGames(limit: Int): Pair<List<RomGame>, Int> {
        val words = search.lowercase().split(' ').filter { it.isNotBlank() }
        val hits = games.filter { game -> game.system == system && words.all { game.name.lowercase().contains(it) } }
        return hits.take(limit) to hits.size
    }

    /** The cheat files RetroArch has for [game] under the name it loads ([version]: RetroArch's). */
    fun installed(game: RomGame, version: String?): List<InstalledCheats> {
        val name = RomNames.cheatFile(game.name, version)
        return side?.installed.orEmpty().filter { it.name == name }
    }

    /** The cheat file in use for [game]: null while its console's list isn't here, or when none fits or the user chose none. */
    fun pick(game: RomGame): CheatPick? = indexes[game.system]?.let { CheatPlan.pick(game, it, choices) }
}
