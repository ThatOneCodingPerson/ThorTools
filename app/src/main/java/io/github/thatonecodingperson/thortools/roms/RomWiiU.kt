package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.CheatSystems
import io.github.thatonecodingperson.thortools.retroarch.RomFile

/** What the bundled wua tool says of a WUA file: its unpacked [size] and its title folders ([names]). */
data class WuaInfo(val size: Long, val names: List<String>)

/**
 * A Wii U title kept as a folder ([root], holding `code/`, `content/`, `meta/`), with its [titleId] (16 hex digits,
 * lower case) and [version] from `code/app.xml`.
 */
data class WiiUTitle(val root: String, val titleId: String, val version: Int) {
    /** The folder it gets inside a WUA, the way Cemu names them: `0005000e10110200_v32`. */
    val wuaName: String get() = "${titleId}_v$version"

    /** A game, not its update or DLC. */
    val game: Boolean get() = titleId.startsWith(GAME)

    /** The game it belongs to: its title ID's last 8 digits (an update's and a DLC's are the game's). */
    val group: String get() = titleId.substring(8)

    companion object {
        const val GAME = "00050000"
        const val UPDATE = "0005000e"
        const val DLC = "0005000c"
    }
}

/** The Wii U: its emulators, its game folders and WUA files. */
object WiiU {
    private val READS = listOf(Reads(RomFormat.WUA, Support.DOCUMENTED), Reads(RomFormat.FOLDERS, Support.DOCUMENTED))

    val EMULATORS = listOf(RomEmulator("Cemu", listOf("info.cemu.cemu", "info.cemu.Cemu"), READS))
    val CORES = listOf(RomCore("Cemu", listOf("cemu_libretro"), READS))

    /** Disc images, which only Cemu turns into WUA (it needs the console's keys for them). */
    private val DISC_IMAGES = setOf("wud", "wux")
    private const val APP_XML = "/code/app.xml"
    private const val WUA = "wua"
    private val TAG = Regex("""\s*\[(?:Game|Update|DLC|[0-9A-Fa-f]{16})]""", RegexOption.IGNORE_CASE)
    private val TITLE_ID = Regex("""<title_id[^>]*>\s*([0-9A-Fa-f]{16})\s*</title_id>""")
    private val TITLE_VERSION = Regex("""<title_version[^>]*>\s*([0-9A-Fa-f]{1,8})\s*</title_version>""")

    /** True for a file in Cemu's own storage (its `mlc01`: installed games, updates, saves), never a game. */
    fun inEmulatorData(path: String): Boolean = path.lowercase().split('/').contains("mlc01")

    /** A title's `code/app.xml`, outside Cemu's storage. */
    fun isAppXml(path: String): Boolean = path.endsWith(APP_XML, ignoreCase = true) && !inEmulatorData(path)

    /** The title of the folder [root] from its `code/app.xml` [text]; null when it says no title ID. */
    fun parseAppXml(root: String, text: String): WiiUTitle? {
        val id = TITLE_ID.find(text)?.groupValues?.get(1)?.lowercase() ?: return null
        val version = TITLE_VERSION.find(text)?.groupValues?.get(1)?.toIntOrNull(16) ?: 0
        return WiiUTitle(root, id, version)
    }

    /** A game folder's name without the tags dumps add (`[Game]`, `[Update]`, `[DLC]`, the title ID). */
    fun cleanTitle(name: String): String = TAG.replace(name, "").trim()

    /** The folder a WUA's title [wuaName] turns back into: `<title> [Game] [0005000010110200]` (Update, DLC). */
    fun folderName(title: String, wuaName: String): String {
        val id = wuaName.substringBefore("_v")
        val kind = when (id.take(8).lowercase()) {
            WiiUTitle.GAME -> "Game"
            WiiUTitle.UPDATE -> "Update"
            WiiUTitle.DLC -> "DLC"
            else -> "Title"
        }
        return "$title [$kind] [$id]"
    }

    /** Reads `wua info`'s lines (unpacked size, title folders, path; tab-separated), by path; null: not readable. */
    fun parseInfo(output: String): Map<String, WuaInfo?> = output.lineSequence().mapNotNull { line ->
        val parts = line.split('\t', limit = 3)
        if (parts.size < 3 || parts[2].isEmpty()) return@mapNotNull null
        parts[2] to decodeInfo(parts[0] + "\t" + parts[1])
    }.toMap()

    fun encodeInfo(info: WuaInfo?): String = if (info == null) "?\t-" else "${info.size}\t${info.names.joinToString(",").ifEmpty { "-" }}"

    fun decodeInfo(text: String): WuaInfo? {
        val parts = text.split('\t')
        val size = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val names = parts.getOrNull(1)?.takeIf { it != "-" }?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
        return WuaInfo(size, names)
    }

    /**
     * The Wii U games among [files]: each game folder (found by its `code/app.xml`, whose text is in [texts]) with the
     * update and DLC folders of the same game beside it, paired with the WUA of the same name when there is one; WUA
     * files on their own ([wuas] tells what each holds); disc images, which are left alone. Cemu's own storage and
     * moved originals are never looked at.
     */
    fun games(console: RomConsole, files: List<RomFile>, texts: Map<String, String>, wuas: Map<String, WuaInfo?>): List<DiscGame> {
        val mine = files.filter {
            CheatSystems.of(it.path) == console.system && "/${RomJobs.KEPT_FOLDER}/" !in it.path && !inEmulatorData(it.path)
        }
        val found = mine.filter { isAppXml(it.path) }.map { xml ->
            val root = xml.path.dropLast(APP_XML.length)
            root to texts[xml.path]?.let { parseAppXml(root, it) }
        }
        val roots = found.map { it.first }
        fun under(root: String) = mine.filter { it.path.startsWith("$root/") }
        fun inRoot(path: String) = roots.any { path.startsWith("$it/") }

        val games = mutableListOf<DiscGame>()
        val usedWuas = mutableSetOf<String>()
        found.filter { it.second == null }.forEach { (root, _) ->
            games += folderGame(console, root, under(root), GameProblem.CantRead)
        }
        found.mapNotNull { it.second }.groupBy { folderOf(it.root).lowercase() + "|" + it.group }.values.forEach { group ->
            val base = group.filter { it.game }.maxByOrNull { it.version }
            val picked = if (base == null) emptyList() else group.groupBy { it.titleId }.values.map { same -> same.maxBy { it.version } }
            group.filter { it !in picked }.forEach { lone ->
                games +=
                    folderGame(console, lone.root, under(lone.root), GameProblem.LoneTitle)
            }
            if (base == null) return@forEach
            val ordered = listOf(base) + picked.filter { it != base }.sortedBy { it.titleId }
            val title = cleanTitle(nameOf(base.root)).ifEmpty { base.titleId }
            val parent = folderOf(base.root)
            val gameFiles = ordered.flatMap { under(it.root) }
            val wua = mine.firstOrNull { it.path.equals("$parent/$title.$WUA", ignoreCase = true) }
            if (wua != null) usedWuas += wua.path
            games += DiscGame(
                console = console,
                title = title,
                folder = parent,
                main = base.root,
                lone = false,
                files = gameFiles.map { GameFile(it.path, it.size) },
                compressed = wua,
                kind = DiscKind.FOLDER,
                packed = wua?.let { RomFormat.WUA },
                imageSize = gameFiles.sumOf { it.size },
                titles = ordered.associate { it.wuaName to it.root },
            )
        }
        mine.filter { extension(it.name) == WUA && it.path !in usedWuas && !inRoot(it.path) }.forEach { file ->
            val info = wuas[file.path]
            val title = file.name.substringBeforeLast('.')
            val parent = folderOf(file.path)
            games += DiscGame(
                console = console,
                title = title,
                folder = parent,
                main = null,
                lone = false,
                files = emptyList(),
                compressed = file,
                problem = when {
                    file.size <= 0 -> GameProblem.Empty
                    info == null || info.names.isEmpty() -> GameProblem.CantRead
                    else -> null
                },
                kind = DiscKind.FOLDER,
                packed = RomFormat.WUA,
                imageSize = info?.size,
                titles = info?.names.orEmpty().associateWith { "$parent/${folderName(title, it)}" },
            )
        }
        mine.filter { extension(it.name) in DISC_IMAGES && it.size > 0 && !inRoot(it.path) }.forEach { image ->
            games += DiscGame(
                console = console,
                title = image.name.substringBeforeLast('.'),
                folder = folderOf(image.path),
                main = image.path,
                lone = false,
                files = listOf(GameFile(image.path, image.size)),
                compressed = null,
                problem = GameProblem.NeedsKeys,
                kind = DiscKind.FOLDER,
            )
        }
        return games.sortedWith(compareBy({ it.title.lowercase() }, { it.folder.lowercase() }))
    }

    /** A title folder that is left as it is, with the [problem] why. */
    private fun folderGame(console: RomConsole, root: String, files: List<RomFile>, problem: GameProblem) = DiscGame(
        console = console,
        title = cleanTitle(nameOf(root)).ifEmpty { nameOf(root) },
        folder = folderOf(root),
        main = root,
        lone = false,
        files = files.map { GameFile(it.path, it.size) },
        compressed = null,
        problem = problem,
        kind = DiscKind.FOLDER,
        imageSize = files.sumOf { it.size },
    )

    private fun nameOf(path: String) = path.substringAfterLast('/')

    private fun folderOf(path: String) = path.substringBeforeLast('/', "")

    private fun extension(name: String) = name.substringAfterLast('.', "").lowercase()
}
