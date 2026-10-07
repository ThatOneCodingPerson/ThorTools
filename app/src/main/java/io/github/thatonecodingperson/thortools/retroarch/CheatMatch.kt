package io.github.thatonecodingperson.thortools.retroarch

import java.text.Normalizer
import kotlin.math.abs

/** Game file names, and the names RetroArch derives from them. */
object RomNames {
    private val EXTENSION = Regex("^[A-Za-z0-9]{1,4}$")
    private val VERSION = Regex("""^(\d+)\.(\d+)""")

    /** RetroArch cuts the cheat file's name twice from this version on. */
    private const val CUTS_TWICE_MAJOR = 1
    private const val CUTS_TWICE_MINOR = 20

    const val CHT = ".cht"

    /** The extensions of [fileName] that look like ones, lower case, an inner one first: `[n64, zip]` for `game.n64.zip`. */
    fun extensions(fileName: String): List<String> {
        val last = fileName.substringAfterLast('.', "")
        if (!EXTENSION.matches(last)) return emptyList()
        val inner = fileName.dropLast(last.length + 1).substringAfterLast('.', "").takeIf { EXTENSION.matches(it) }
        return listOfNotNull(inner, last).map { it.lowercase() }
    }

    /** The game's name without its extensions, an inner game extension too: `007 - GoldenEye (USA)` for `....n64.zip`. */
    fun stem(fileName: String): String {
        val extensions = extensions(fileName)
        if (extensions.isEmpty()) return fileName
        val outer = fileName.dropLast(extensions.last().length + 1)
        val inner = extensions.takeIf { it.size == 2 }?.first()?.takeIf { it in CheatSystems.gameExtensions }
        return if (inner != null) outer.dropLast(inner.length + 1) else outer
    }

    /** RetroArch's base name for a game's saves and states: the file name up to its last dot. */
    fun base(fileName: String): String = fileName.substringBeforeLast('.')

    /**
     * The cheat file RetroArch loads for the game file [fileName] from its core's folder: [base] with `.cht`, and from
     * RetroArch 1.20 on [base] cut at its last dot once more (`Castlevania (USA).n64.zip` → `Castlevania (USA).cht`,
     * `Super Mario Bros. 3 (USA).nes` → `Super Mario Bros.cht`). An unknown [version] counts as a new one.
     */
    fun cheatFile(fileName: String, version: String?): String {
        val base = base(fileName)
        return (if (cutsTwice(version)) base(base) else base) + CHT
    }

    private fun cutsTwice(version: String?): Boolean {
        val match = version?.let { VERSION.find(it) } ?: return true
        val major = match.groupValues[1].toInt()
        val minor = match.groupValues[2].toInt()
        return major > CUTS_TWICE_MAJOR || (major == CUTS_TWICE_MAJOR && minor >= CUTS_TWICE_MINOR)
    }
}

/** A region a game's tags name. */
enum class CheatRegion { USA, EUROPE, JAPAN, WORLD, KOREA, CHINA, ASIA, BRAZIL, AUSTRALIA }

/**
 * A game's name taken apart for comparing: [title] its words without tags, case, accents, punctuation, a leading "The"
 * or a moved ", The" and disc numbers; [words] the same words sorted (`GoldenEye 007` and `007 - GoldenEye`); [bare] the
 * title without a leading number (`4273 - Pokemon ...`); the [regions] and [revision] its tags give, and its [flags]
 * (beta, demo, ...).
 */
data class GameTitle(
    val title: String,
    val words: String,
    val bare: String?,
    val regions: Set<CheatRegion>,
    val revision: Int,
    val flags: Set<String>,
) {
    val tokens: Set<String> get() = title.split(' ').filter { it.isNotEmpty() }.toSet()

    companion object {
        private val ROUND = Regex("""\(([^)]*)\)""")
        private val SQUARE = Regex("""\[([^\]]*)]""")
        private val MARKS = Regex("""\p{M}+""")
        private val NOT_WORD = Regex("[^a-z0-9]+")
        private val LEADING_THE = Regex("""^\s*the\s+""")
        private val MOVED_ARTICLE = Regex(""",\s*(the|a|an)\b""")
        private val DISC = Regex("""\b(disc|disk)\s*([0-9]+|[ivx]+)\b""")
        private val REVISION = Regex("""^rev\s*([0-9]+|[a-z])$""")
        private val GOOD_VERSION = Regex("""^v(\d+)\.(\d+)$""")
        private val UNWANTED = setOf("beta", "proto", "prototype", "demo", "sample", "kiosk", "hack", "pirate", "unl", "unlicensed")

        private val REGIONS: Map<String, Set<CheatRegion>> = buildMap {
            fun region(regions: Set<CheatRegion>, vararg names: String) = names.forEach { this[it] = regions }
            region(setOf(CheatRegion.USA), "usa", "u", "us", "america", "northamerica", "ntscu", "canada")
            region(
                setOf(CheatRegion.EUROPE),
                "europe", "e", "eu", "eur", "pal", "uk", "unitedkingdom", "germany", "france", "spain", "italy", "netherlands",
                "sweden", "scandinavia",
            )
            region(setOf(CheatRegion.JAPAN), "japan", "j", "jp", "jpn", "ntscj")
            region(setOf(CheatRegion.WORLD), "world", "w")
            region(setOf(CheatRegion.KOREA), "korea", "k", "kr")
            region(setOf(CheatRegion.CHINA), "china", "ch", "hongkong", "taiwan")
            region(setOf(CheatRegion.ASIA), "asia")
            region(setOf(CheatRegion.BRAZIL), "brazil", "b", "br")
            region(setOf(CheatRegion.AUSTRALIA), "australia", "a", "au")
            region(setOf(CheatRegion.USA, CheatRegion.EUROPE), "ue")
            region(setOf(CheatRegion.JAPAN, CheatRegion.USA), "ju", "uj")
            region(setOf(CheatRegion.JAPAN, CheatRegion.EUROPE), "je", "ej")
            region(setOf(CheatRegion.JAPAN, CheatRegion.USA, CheatRegion.EUROPE), "jue", "jeu", "uje", "uej", "eju", "euj")
        }

        /** [stem] is a game's name without its extensions. */
        fun of(stem: String): GameTitle {
            val regions = mutableSetOf<CheatRegion>()
            var revision = 0
            val flags = mutableSetOf<String>()
            // Square brackets hold dump flags ([a1], [b], [!]) besides regions some sets write there ([US], [JP]).
            val tags = ROUND.findAll(stem).map { it.groupValues[1] to true } + SQUARE.findAll(stem).map { it.groupValues[1] to false }
            tags.forEach { (tag, round) ->
                tag.split(',').forEach { part ->
                    val item = part.trim().lowercase()
                    val key = CheatSystems.key(item)
                    val region = REGIONS[key]?.takeIf { round || key.length > 1 }
                    val rev = REVISION.matchEntire(item)
                    val good = GOOD_VERSION.matchEntire(item)
                    when {
                        region != null -> regions += region
                        rev != null -> revision = rev.groupValues[1].let { it.toIntOrNull() ?: (it[0] - 'a' + 1) }
                        good != null -> revision = (good.groupValues[1].toInt() - 1) * 10 + good.groupValues[2].toInt()
                        item in UNWANTED -> flags += item
                    }
                }
            }
            val outside = SQUARE.replace(ROUND.replace(stem, " "), " ")
            val plain = Normalizer.normalize(outside, Normalizer.Form.NFD).replace(MARKS, "").lowercase()
            val text = DISC.replace(MOVED_ARTICLE.replace(LEADING_THE.replace(plain, ""), " "), " ")
                .replace("&", " and ")
                .replace("'", "")
                .replace("’", "")
            val tokens = text.split(NOT_WORD).filter { it.isNotEmpty() }
            val bare = tokens.takeIf { it.size > 1 && it.first().all(Char::isDigit) }?.drop(1)?.joinToString(" ")
            return GameTitle(tokens.joinToString(" "), tokens.sorted().joinToString(" "), bare, regions, revision, flags)
        }

        /** 0 when the regions agree (World agrees with any), 1 when either doesn't say, 2 when they differ. */
        fun regionFit(rom: Set<CheatRegion>, cheats: Set<CheatRegion>): Int = when {
            rom.isEmpty() || cheats.isEmpty() -> 1
            CheatRegion.WORLD in rom || CheatRegion.WORLD in cheats || rom.any { it in cheats } -> 0
            else -> 2
        }
    }
}

/** A cheat file of libretro's database: its file name (with `.cht`), its size when known, and its git id on GitHub. */
data class CheatFileRef(val name: String, val size: Long? = null, val id: String? = null) {
    val stem: String get() = name.removeSuffix(RomNames.CHT)
}

/** How a cheat file fits a game, best first. */
enum class CheatFit {
    /** The same name as the game file. */
    SAME_NAME,

    /** The same game, and a region of the game's. */
    SAME_REGION,

    /** The same game; the game or the file doesn't name its region. */
    NO_REGION,

    /** The same game, for another region: its codes may not work. */
    OTHER_REGION,

    /** Only a similar name: the user has to check it. */
    SIMILAR,
}

data class CheatPick(val file: CheatFileRef, val fit: CheatFit)

/** One console's cheat files, each name taken apart once, so a game is matched without going through all of them. */
class CheatIndex(val system: CheatSystem, val files: List<CheatFileRef>) {
    private val titles: List<GameTitle> = files.map { GameTitle.of(it.stem) }
    private val byTitle: Map<String, List<Int>> = files.indices.groupBy { titles[it].title }
    private val byWords: Map<String, List<Int>> = files.indices.groupBy { titles[it].words }
    private val byName: Map<String, CheatFileRef> = files.associateBy { it.name.lowercase() }

    fun find(name: String): CheatFileRef? = byName[name.lowercase()]

    /** The files for the game file [romName], best first: its name, then the same title in the same region, and so on. */
    fun candidates(romName: String): List<CheatPick> {
        val stem = RomNames.stem(romName)
        val rom = GameTitle.of(stem)
        if (rom.title.isEmpty()) return emptyList()
        // How closely the title matched: in order, in any order, without a leading number.
        val level = linkedMapOf<Int, Int>()
        fun add(hits: List<Int>?, value: Int) = hits?.forEach { if (it !in level) level[it] = value }
        add(byTitle[rom.title], 0)
        add(byWords[rom.words], 1)
        rom.bare?.let { bare ->
            add(byTitle[bare], 2)
            add(byWords[GameTitle.of(bare).words], 2)
        }
        return level.map { (index, titleLevel) -> rank(index, titleLevel, stem, rom) }.sortedWith(RANKING).map { it.pick }
    }

    /** The best file for the game; null when none has its title. */
    fun best(romName: String): CheatPick? = candidates(romName).firstOrNull()

    /**
     * Files whose names share most words with the game's (at least half of all their words together), the closest first,
     * leaving out [candidates]; for a game none has the title of.
     */
    fun similar(romName: String, limit: Int = SIMILAR_LIMIT): List<CheatPick> {
        val rom = GameTitle.of(RomNames.stem(romName)).tokens
        if (rom.isEmpty()) return emptyList()
        val same = candidates(romName).map { it.file }.toSet()
        return files.indices.asSequence()
            .filter { files[it] !in same }
            .map { index ->
                val tokens = titles[index].tokens
                val shared = tokens.count { it in rom }
                index to shared.toDouble() / (tokens.size + rom.size - shared).coerceAtLeast(1)
            }
            .filter { (_, score) -> score >= SIMILAR_SCORE }
            .sortedByDescending { (_, score) -> score }
            .take(limit)
            .map { (index, _) -> CheatPick(files[index], CheatFit.SIMILAR) }
            .toList()
    }

    /** The files whose names hold every word of [text] (any case), in order, and how many there are in all. */
    fun search(text: String, limit: Int): Pair<List<CheatFileRef>, Int> {
        val words = text.lowercase().split(' ').filter { it.isNotBlank() }
        val hits = files.filter { file -> words.all { file.name.lowercase().contains(it) } }
        return hits.take(limit) to hits.size
    }

    private fun rank(index: Int, titleLevel: Int, stem: String, rom: GameTitle): Ranked {
        val file = files[index]
        val title = titles[index]
        val exact = file.stem.equals(stem, ignoreCase = true)
        val region = GameTitle.regionFit(rom.regions, title.regions)
        val fit = when {
            exact -> CheatFit.SAME_NAME
            region == 0 -> CheatFit.SAME_REGION
            region == 1 -> CheatFit.NO_REGION
            else -> CheatFit.OTHER_REGION
        }
        val unwanted = (title.flags - rom.flags).isNotEmpty()
        return Ranked(CheatPick(file, fit), exact, region, unwanted, titleLevel, abs(title.revision - rom.revision))
    }

    private data class Ranked(
        val pick: CheatPick,
        val exact: Boolean,
        val region: Int,
        val unwanted: Boolean,
        val titleLevel: Int,
        val revisionGap: Int,
    )

    private companion object {
        const val SIMILAR_LIMIT = 8
        const val SIMILAR_SCORE = 0.5

        val RANKING = compareBy<Ranked>(
            { !it.exact },
            { it.region },
            { it.unwanted },
            { it.titleLevel },
            { it.revisionGap },
            { it.pick.file.name.length },
            { it.pick.file.name },
        )
    }
}
