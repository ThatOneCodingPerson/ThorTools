package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.retroarch.BiosTable
import io.github.thatonecodingperson.thortools.retroarch.CheatSystem
import io.github.thatonecodingperson.thortools.retroarch.CheatSystems
import io.github.thatonecodingperson.thortools.retroarch.RomFile
import io.github.thatonecodingperson.thortools.retroarch.RomLibrary

/** A file a game is made of: its path, size, and whether it holds only CD audio. */
data class GameFile(val path: String, val size: Long, val audio: Boolean = false) {
    val name: String get() = path.substringAfterLast('/')
}

/** Why a game can't be compressed (yet). */
sealed class GameProblem {
    /**
     * Its sheet names files that aren't beside it: [names] of the [total] it names; [copyIn] is the folder of a complete
     * copy of the same game, when there is one.
     */
    data class MissingTracks(val names: List<String>, val total: Int = names.size, val copyIn: String? = null) : GameProblem()

    /** Its sheet can't be read. */
    data object UnreadableSheet : GameProblem()

    /** Several `(Track N)` files without the sheet that says where each track starts. */
    data object NeedsSheet : GameProblem()

    /** A kind of file Thor Tools doesn't compress yet. */
    data class NotHandled(val extension: String) : GameProblem()

    /** An `.iso` whose size isn't a whole number of 2048-byte sectors: not a disc image. */
    data object OddSize : GameProblem()

    /** An image with no disc volume in it (no `CD001` in sector 16). */
    data object NotDisc : GameProblem()

    /** A disc that holds no game: a UMD's films or music. */
    data object NotGame : GameProblem()

    /** An NKit image: a shrunk copy, not the whole disc, so it is left as it is. */
    data object NKit : GameProblem()

    /** A file Thor Tools' tools can't read (a GCZ in 16 KB blocks, or one that fails to open). */
    data object CantRead : GameProblem()

    /** A file of 0 bytes: a broken copy. */
    data object Empty : GameProblem()

    /** An archive that isn't one game: no ROM of the console in it, or several (a collection, an HD pack). */
    data object NotOneGame : GameProblem()

    /** An encrypted 3DS ROM: Azahar can't play it, and it wouldn't get smaller. */
    data object Encrypted : GameProblem()

    /** A Wii U disc image: only Cemu makes a WUA of it, with the console's keys. */
    data object NeedsKeys : GameProblem()

    /** A Wii U update or DLC folder without its game beside it, or a second copy of a title. */
    data object LoneTitle : GameProblem()
}

/**
 * A disc game of a console: [title] (the name its compressed file gets), [folder] where its data lies and its
 * compressed file goes, [main] the sheet or lone image (null when only the compressed file is here), [files] what it is
 * made of now, [compressed] its compressed file when there is one, a LibCrypt [sbi] file beside it, the `.m3u`
 * [playlists] that name it, its disc number and any problem. [lone]: an image without a sheet; [elsewhere]: its sheet
 * lies in a folder of its own while the tracks it names lie in [folder], one folder up. [kind]: a CD or a DVD image (null
 * when only a CHD is here, which can be either); [packed]: the format of [compressed] when its extension or header tells;
 * [imageSize]: the size of the image inside [compressed] when its header told it. [parts]: the further files of a split
 * [compressed] (`.wbf1` beside a `.wbfs`), counted in its size. [inner]: a cartridge game's ROM file name (inside its
 * archive, or the plain file's own). [titles]: a Wii U game's title folders inside a WUA (`<title id>_v<version>`), each
 * with its folder: where it is, or where it goes when the WUA turns back into folders.
 */
data class DiscGame(
    val console: RomConsole,
    val title: String,
    val folder: String,
    val main: String?,
    val lone: Boolean,
    val files: List<GameFile>,
    val compressed: RomFile?,
    val sbi: String? = null,
    val playlists: List<String> = emptyList(),
    val disc: Int? = null,
    val problem: GameProblem? = null,
    val elsewhere: Boolean = false,
    val kind: DiscKind? = DiscKind.CD,
    val packed: RomFormat? = null,
    val imageSize: Long? = null,
    val parts: List<String> = emptyList(),
    val inner: String? = null,
    val titles: Map<String, String> = emptyMap(),
) {
    /** The path that names the game: its sheet or image, else its compressed file. */
    val id: String get() = main ?: compressed?.path.orEmpty()

    /** Names the game across compressing and turning back: its folder and title. */
    val key: String get() = "$folder/$title"

    val originalSize: Long get() = files.sumOf { it.size }
    val audioSize: Long get() = files.filter { it.audio }.sumOf { it.size }
    val dataSize: Long get() = originalSize - audioSize

    /** Where its compressed file goes: beside its data, under the title (a plain ROM under its own name). */
    fun output(format: RomFormat): String = when (format) {
        RomFormat.ROM_FILE -> "$folder/${inner ?: title}"
        RomFormat.Z3DS -> "$folder/$title.${N3ds.packedExtension(inner)}"
        RomFormat.FOLDERS -> titles.values.firstOrNull() ?: "$folder/$title"
        else -> "$folder/$title.${format.extension}"
    }

    /** The formats its disc can become; the first is the console's default. */
    val formats: List<RomFormat> get() = console.formatsFor(kind ?: DiscKind.CD)

    val defaultFormat: RomFormat get() = formats.firstOrNull() ?: RomFormat.CHD_CD

    /** The formats it can become from what it is now: never a file with the extension it already has. */
    val targets: List<RomFormat>
        get() {
            if (kind == DiscKind.CART || kind == DiscKind.FOLDER) return formats.filter { it != current }
            val have = (main ?: compressed?.path)?.substringAfterLast('.')?.lowercase()
            val same = if (have in console.otherImages) RomFormat.ISO.extension else have
            return formats.filter { it.extension != same }
        }

    /** What its file is now: a compressed file's format when known, ISO for a plain image; null for a CD's sheet. */
    val current: RomFormat?
        get() = when {
            kind == DiscKind.CART -> if (compressed != null) packed else RomFormat.ROM_FILE
            kind == DiscKind.FOLDER -> if (compressed != null) packed else RomFormat.FOLDERS
            main != null -> RomFormat.ISO.takeIf { kind == DiscKind.DVD }
            else -> packed
        }

    /** The bytes it takes now: its files, or its compressed file. */
    val sizeNow: Long get() = if (main != null) originalSize else compressed?.size ?: 0L

    val canCompress: Boolean get() = main != null && problem == null && compressed == null

    /** Not compressed yet: a plain image, or a file that only leaves out the disc's unused blocks (WBFS, CISO). */
    val notCompressed: Boolean get() = canCompress || (canConvert && packed?.compressed == false)
    val originalsLeft: Boolean get() = compressed != null && main != null && problem == null

    /** In a format that really compresses it (not WBFS or CISO), and readable: an empty file or a stray archive doesn't count. */
    val isCompressed: Boolean get() = compressed != null && packed?.compressed != false && problem == null

    /** A compressed DVD-kind game: it can be unpacked and made into another format, or left as a plain image. */
    val canConvert: Boolean
        get() = main == null &&
            compressed != null &&
            problem == null &&
            (kind == DiscKind.DVD || kind == DiscKind.CART || kind == DiscKind.FOLDER)
}

/** Finds a console's disc games among the files listed in the ROM folders. */
object RomSets {
    private const val CUE = "cue"
    private const val CHD = "chd"
    private const val SBI = "sbi"
    private const val M3U = "m3u"
    private const val ISO = "iso"
    private const val SECTOR = 2048L
    private val RAW = setOf("bin", "img")
    private val NOT_HANDLED = setOf("ccd", "pbp", "ecm", "mds", "mdf", "7z", "zip", ISO, "cdi", "gz")
    private const val NKIT = ".nkit."
    private val WBFS_PART = Regex("""^wbf[1-9]$""")

    /** The smallest GCZ block nod reads: one 32 KiB Wii sector. */
    private const val GCZ_MIN_BLOCK = 0x8000
    private val CLONE_CD_PARTS = setOf("img", "sub")
    private val TRACK_NAME = Regex("""^(.*?)\s*\(Track \d+\)$""", RegexOption.IGNORE_CASE)
    private val DISC = Regex("""\((?:Disc|CD)\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val DISC_PART = Regex("""\s*\((?:Disc|CD)\s*\d+[^)]*\)""", RegexOption.IGNORE_CASE)

    /**
     * The games of [console] among [files] (paths as the Files app shows them), with [texts] holding the contents of
     * the `.cue` and `.m3u` files by path. Anything that isn't part of a game (BIOS files, memory cards, saves, pictures)
     * is left out. A sheet whose tracks aren't beside it but all lie one folder up (a sheet moved into a folder of its
     * own) takes them from there. [headers] (by path) tell what compressed files hold, [listings] what archives hold,
     * [cards] what 3DS files are, [wuas] what WUA files hold.
     */
    fun games(
        console: RomConsole,
        files: List<RomFile>,
        texts: Map<String, String>,
        headers: Map<String, PackedHeader> = emptyMap(),
        listings: Map<String, ArchiveListing?> = emptyMap(),
        cards: Map<String, CardInfo?> = emptyMap(),
        wuas: Map<String, WuaInfo?> = emptyMap(),
    ): List<DiscGame> {
        if (console.listKind == DiscKind.FOLDER) return WiiU.games(console, files, texts, wuas)
        if (console.cartridges) return cartridges(console, files, texts, listings, cards)
        val mine = files.filter {
            CheatSystems.of(it.path) == console.system && "/${RomJobs.KEPT_FOLDER}/" !in it.path && !onMemoryStick(it.path)
        }
        val cds = console.formatsFor(DiscKind.CD).isNotEmpty()
        val byPath = mine.associateBy { it.path.lowercase() }
        val claimed = mutableSetOf<String>()
        val games = mutableListOf<DiscGame>()

        mine.filter { cds && extension(it.name) == CUE }.forEach { sheetFile ->
            claimed += sheetFile.path.lowercase()
            games += sheetGame(console, sheetFile, texts[sheetFile.path]?.let(CueSheet::parse), byPath, claimed)
        }

        if (console.takesIso) {
            val images = listOf(ISO) + console.otherImages
            mine.filter { extension(it.name) in images && it.path.lowercase() !in claimed }.forEach { iso ->
                claimed += iso.path.lowercase()
                games += DiscGame(
                    console,
                    base(iso.name),
                    folderOf(iso.path),
                    iso.path,
                    false,
                    listOf(GameFile(iso.path, iso.size)),
                    null,
                    problem = when {
                        iso.size <= 0 -> GameProblem.Empty
                        NKIT in iso.name.lowercase() -> GameProblem.NKit
                        iso.size % SECTOR != 0L -> GameProblem.OddSize
                        else -> null
                    },
                    kind = DiscKind.DVD,
                )
            }
        }

        val notHandled = if (console.takesIso) NOT_HANDLED - ISO else NOT_HANDLED
        mine.filter { extension(it.name) in notHandled && it.path.lowercase() !in claimed }.forEach { file ->
            if (file.path.lowercase() in claimed) return@forEach
            val ext = extension(file.name).orEmpty()
            val parts = if (ext == "ccd") {
                mine.filter { other ->
                    folderOf(other.path) == folderOf(file.path) &&
                        base(other.name).equals(base(file.name), true) &&
                        extension(other.name) in CLONE_CD_PARTS
                }
            } else {
                emptyList()
            }
            (parts + file).forEach { claimed += it.path.lowercase() }
            games += DiscGame(
                console,
                base(file.name).let { if (ext == "gz" && extension(it) == ISO) base(it) else it },
                folderOf(file.path),
                file.path,
                false,
                (listOf(file) + parts).map { GameFile(it.path, it.size) },
                null,
                problem = GameProblem.NotHandled(ext),
            )
        }

        mine.filter { cds && extension(it.name) in RAW && it.path.lowercase() !in claimed && CueSheet.isRawImage(it.size) }
            .groupBy { folderOf(it.path).lowercase() + "/" + (trackPrefix(base(it.name)) ?: base(it.name)).lowercase() }
            .values.forEach { group ->
                val first = group.minBy { it.name.lowercase() }
                val title = trackPrefix(base(first.name)) ?: base(first.name)
                games += if (group.size > 1) {
                    DiscGame(
                        console,
                        title,
                        folderOf(first.path),
                        first.path,
                        false,
                        group.map { GameFile(it.path, it.size) },
                        null,
                        problem = GameProblem.NeedsSheet,
                    )
                } else {
                    DiscGame(console, title, folderOf(first.path), first.path, true, listOf(GameFile(first.path, first.size)), null)
                }
            }

        // Compressed files: a CHD first, then the other formats this console's games come in; each joins its game by name.
        val packedExtensions = console.packedFormats.map {
            it.extension
        }.let { list -> (listOf(CHD).filter { cds || it in list } + list).distinct() }
        val packedFiles = mine.filter { extension(it.name) in packedExtensions }
            .sortedBy { packedExtensions.indexOf(extension(it.name)) }
        val used = mutableSetOf<RomFile>()
        val withChd = games.map { game ->
            val packed = packedFiles.firstOrNull {
                it !in used && it.size > 0 && (folderOf(it.path) + "/" + base(it.name)).equals(game.key, true)
            }
                ?: return@map game
            used += packed
            val header = headers[packed.path]
            game.copy(compressed = packed, packed = header?.format ?: packedFormat(packed.name), imageSize = header?.imageSize)
        }
        // A split WBFS (`.wbf1` and on, for FAT32 cards) is part of its `.wbfs`
        val wbfsParts = mine.filter { extension(it.name)?.let(WBFS_PART::matches) == true }
            .groupBy { (folderOf(it.path) + "/" + base(it.name)).lowercase() }
        // Compressed files of the same game (a CSO turned into a CHD with the originals kept): the newest is the game,
        // the older ones are its originals, there to clean up.
        val alone = packedFiles.filter { it !in used }
            .groupBy { (folderOf(it.path) + "/" + base(it.name)).lowercase() }
            .values.map { group ->
                val ordered = group.sortedByDescending { it.modified }
                val file = ordered.first()
                val older = ordered.drop(1)
                val header = headers[file.path]
                val format = header?.format ?: packedFormat(file.name)
                val split = wbfsParts[(folderOf(file.path) + "/" + base(file.name)).lowercase()].orEmpty()
                    .takeIf { format == RomFormat.WBFS }.orEmpty().sortedBy { it.name.lowercase() }
                val whole = file.size + split.sumOf { it.size }
                DiscGame(
                    console,
                    base(file.name),
                    folderOf(file.path),
                    older.firstOrNull()?.path,
                    false,
                    older.map { GameFile(it.path, it.size) },
                    if (split.isEmpty()) file else file.copy(size = whole),
                    problem = when {
                        file.size <= 0 -> GameProblem.Empty
                        NKIT in file.name.lowercase() -> GameProblem.NKit
                        format == RomFormat.GCZ && header != null && header.block < GCZ_MIN_BLOCK -> GameProblem.CantRead
                        else -> null
                    },
                    kind = header?.kind ?: format?.disc ?: if (console.takesIso && cds) null else console.formats.keys.singleOrNull(),
                    packed = format,
                    imageSize = header?.imageSize?.takeIf { it > 0 } ?: console.fullDisc(whole),
                    parts = split.map { it.path },
                )
            }

        val sbis = mine.filter { console.usesSbi && extension(it.name) == SBI }
            .associateBy { (folderOf(it.path) + "/" + base(it.name)).lowercase() }
        val playlists = mine.filter { extension(it.name) == M3U }.map { it.path to playlistNames(texts[it.path].orEmpty()) }
        val all = withChd + alone
        return all.map { game ->
            val names = (game.files.map { it.name } + listOfNotNull(game.compressed?.name)).map { it.lowercase() }.toSet()
            val sbiKeys = (listOf(game.title) + game.files.map { base(it.name) }).map { (game.folder + "/" + it).lowercase() }
            game.copy(
                sbi = sbiKeys.firstNotNullOfOrNull { sbis[it]?.path },
                playlists = playlists.filter { (path, listed) ->
                    folderOf(path) == game.folder && listed.any { it in names }
                }.map { it.first },
                disc = DISC.find(game.title)?.groupValues?.get(1)?.toIntOrNull(),
                problem = withCopy(game.problem, game, all),
            )
        }.sortedWith(compareBy({ it.title.lowercase() }, { it.folder.lowercase() }))
    }

    /** File names (lower case) of BIOS files, never games. */
    private val BIOS_NAMES: Set<String> by lazy { BiosTable.files.map { it.name.lowercase() }.toSet() + Classics.EXTRA_BIOS }

    /**
     * The games of a cartridge [console]: its plain ROMs, and its compressed files: the archives (zip, 7z) that hold
     * exactly one of them, as [listings] (by path; null when the archive couldn't be read) tell, or 3DS's Z3DS files,
     * as [cards] tell. A ROM beside its compressed file (the originals kept) and older compressed files of the same
     * game pair up with the newest one. BIOS files, the `.bin` tracks a sheet names and Azahar's own data are never
     * games; an encrypted 3DS ROM is left as it is.
     */
    private fun cartridges(
        console: RomConsole,
        files: List<RomFile>,
        texts: Map<String, String>,
        listings: Map<String, ArchiveListing?>,
        cards: Map<String, CardInfo?>,
    ): List<DiscGame> {
        val threeDs = RomFormat.Z3DS in console.allFormats
        val mine = files.filter {
            CheatSystems.of(it.path) == console.system &&
                "/${RomJobs.KEPT_FOLDER}/" !in it.path &&
                !(threeDs && N3ds.inEmulatorData(it.path))
        }
        val tracks = mine.filter { extension(it.name) == CUE }.flatMap { sheet ->
            CueSheet.parse(texts[sheet.path].orEmpty())?.files.orEmpty().map { (folderOf(sheet.path) + "/" + it).lowercase() }
        }.toSet()
        val roms = mine.filter { file ->
            extension(file.name) in console.romTypes &&
                file.name.lowercase() !in BIOS_NAMES &&
                file.path.lowercase() !in tracks
        }
        val packedTypes = console.packedTypes
        val archives = mine.filter { extension(it.name) in packedTypes && it.name.lowercase() !in BIOS_NAMES }
        val byKey = archives.groupBy { (folderOf(it.path) + "/" + base(it.name)).lowercase() }
            .mapValues { (_, group) -> group.sortedByDescending { it.modified } }
        val used = mutableSetOf<String>()
        val plain = roms.map { rom ->
            val key = (folderOf(rom.path) + "/" + base(rom.name)).lowercase()
            val packed = byKey[key]?.firstOrNull()
            if (packed != null) used += key
            val card = cards[rom.path]
            val game = DiscGame(
                console = console,
                title = base(rom.name),
                folder = folderOf(rom.path),
                main = rom.path,
                lone = false,
                files = listOf(GameFile(rom.path, rom.size)),
                compressed = null,
                problem = when {
                    rom.size <= 0 -> GameProblem.Empty
                    threeDs && card?.encrypted == true -> GameProblem.Encrypted
                    threeDs && card != null && !card.rom -> GameProblem.CantRead
                    else -> null
                },
                kind = DiscKind.CART,
                imageSize = rom.size,
                inner = rom.name,
            )
            if (packed == null) game else game.copy(compressed = packed, packed = packedTypes[extension(packed.name)], problem = null)
        }
        val packedGames = byKey.filterKeys { it !in used }.values.map { group ->
            val file = group.first()
            val older = group.drop(1)
            val title = base(file.name)
            val listing = listings[file.path]
            val card = cards[file.path]?.takeIf { it.packed }
            val rom = listing?.let { ArchiveListings.rom(it, console.romTypes) }
            DiscGame(
                console = console,
                title = title,
                folder = folderOf(file.path),
                main = older.firstOrNull()?.path,
                lone = false,
                files = older.map { GameFile(it.path, it.size) },
                compressed = file,
                problem = when {
                    file.size <= 0 -> GameProblem.Empty
                    threeDs -> GameProblem.CantRead.takeIf { card == null }
                    listing == null -> GameProblem.CantRead
                    rom == null -> GameProblem.NotOneGame
                    else -> null
                },
                kind = DiscKind.CART,
                packed = packedTypes[extension(file.name)],
                imageSize = if (threeDs) card?.size else rom?.size,
                inner = if (threeDs) card?.let { N3ds.romName(title, it.inner) } else rom?.name,
            )
        }
        return (plain + packedGames).map { game -> game.copy(disc = DISC.find(game.title)?.groupValues?.get(1)?.toIntOrNull()) }
            .sortedWith(compareBy({ it.title.lowercase() }, { it.folder.lowercase() }))
    }

    /** The game a sheet makes, claiming the tracks it takes. */
    private fun sheetGame(
        console: RomConsole,
        sheetFile: RomFile,
        sheet: CueSheet?,
        byPath: Map<String, RomFile>,
        claimed: MutableSet<String>,
    ): DiscGame {
        val folder = folderOf(sheetFile.path)
        val title = base(sheetFile.name)
        val sheetPart = GameFile(sheetFile.path, sheetFile.size)
        if (sheet ==
            null
        ) {
            return DiscGame(
                console,
                title,
                folder,
                sheetFile.path,
                false,
                listOf(sheetPart),
                null,
                problem = GameProblem.UnreadableSheet,
            )
        }
        val beside = sheet.files.map { byPath["$folder/$it".lowercase()] }
        val up = folderOf(folder)
        val above = if (beside.any { it == null } && up.isNotEmpty()) sheet.files.map { byPath["$up/$it".lowercase()] } else null
        val elsewhere = above != null && above.all { it != null }
        val found = if (elsewhere) above.orEmpty() else beside
        val tracks = sheet.files.zip(found).mapNotNull { (name, file) -> file?.let { GameFile(it.path, it.size, sheet.isAudio(name)) } }
        tracks.forEach { claimed += it.path.lowercase() }
        val missing = sheet.files.zip(found).filter { it.second == null }.map { it.first }
        return DiscGame(
            console = console,
            title = title,
            folder = if (elsewhere) up else folder,
            main = sheetFile.path,
            lone = false,
            files = listOf(sheetPart) + tracks,
            compressed = null,
            problem = missing.takeIf { it.isNotEmpty() }?.let { GameProblem.MissingTracks(it, sheet.files.size) }
                ?: GameProblem.Empty.takeIf { tracks.any { it.size <= 0 } },
            elsewhere = elsewhere,
        )
    }

    /** Missing tracks name the folder of a complete copy of the same game, when [all] has one. */
    private fun withCopy(problem: GameProblem?, game: DiscGame, all: List<DiscGame>): GameProblem? {
        if (problem !is GameProblem.MissingTracks) return problem
        val copy = all.firstOrNull {
            it !== game && it.title.equals(game.title, ignoreCase = true) && it.problem == null && it.main != null
        }
        return problem.copy(copyIn = copy?.folder)
    }

    /**
     * Consoles whose games never come as a loose `.bin` or `.img`: such files in their folders are an emulator's data
     * (Cemu keeps a thousand `.bin` files beside Wii U games), not games.
     */
    private val NO_LOOSE_IMAGES = setOf(
        CheatSystem.PSP,
        CheatSystem.GAMECUBE,
        CheatSystem.WII,
        CheatSystem.WII_U,
        CheatSystem.N3DS,
        CheatSystem.SWITCH,
        CheatSystem.PS3,
        CheatSystem.PS_VITA,
        CheatSystem.XBOX,
    )
    private val LOOSE_IMAGES = setOf("bin", "img")

    /** Recognised folders of consoles Thor Tools can't compress yet, with how many games each holds. */
    fun otherConsoles(files: List<RomFile>): Map<CheatSystem, Int> {
        val added = RomConsole.entries.map { it.system }.toSet()
        return RomLibrary.games(files)
            .filter { it.system !in added }
            .filterNot { it.system in NO_LOOSE_IMAGES && it.name.substringAfterLast('.', "").lowercase() in LOOSE_IMAGES }
            .groupingBy { it.system }.eachCount()
            .toList().sortedBy { it.first.ordinal }.toMap()
    }

    /** The file names an `.m3u` list names, lower case. */
    fun playlistNames(text: String): List<String> = text.lineSequence().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { it.replace('\\', '/').substringAfterLast('/').lowercase() }.toList()

    /** [text] of an `.m3u` with every line naming [oldName] naming [newName] instead; null when no line named it. */
    fun rewritePlaylist(text: String, oldName: String, newName: String): String? {
        var changed = false
        val lines = text.lines().map { line ->
            val trimmed = line.trim()
            val name = trimmed.replace('\\', '/').substringAfterLast('/')
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && name.equals(oldName, ignoreCase = true)) {
                changed = true
                line.substring(0, line.length - line.trimStart().length) + trimmed.dropLast(name.length) + newName
            } else {
                line
            }
        }
        return if (changed) lines.joinToString(if ("\r\n" in text) "\r\n" else "\n") else null
    }

    /** An `.m3u` list of [discs] (file names), in disc order. */
    fun playlist(discs: List<String>): String = discs.joinToString("\n", postfix = "\n")

    /** The name of the `.m3u` for a game whose discs are titled like [title]: the title without its disc number. */
    fun playlistTitle(title: String): String = DISC_PART.replace(title, "").trim()

    /** True for a file in one of PPSSPP's memory stick folders (save data, states, homebrew): never a game. */
    fun onMemoryStick(path: String): Boolean {
        val parts = path.lowercase().split('/')
        return parts.indices.any { i -> parts[i] == "psp" && i + 2 < parts.size && parts[i + 1] in MEMORY_STICK_FOLDERS }
    }

    /** The format a compressed file's extension tells; null for a CHD, which can hold a CD or a DVD. */
    private fun packedFormat(name: String): RomFormat? = extension(name)?.let { ext ->
        RomFormat.entries.firstOrNull { it.extension == ext && it.extension != CHD && it.tool != RomTool.NONE }
    }

    private fun trackPrefix(base: String): String? = TRACK_NAME.matchEntire(base)?.groupValues?.get(1)?.trim()

    private fun base(name: String) = name.substringBeforeLast('.')

    private fun extension(name: String): String? = name.substringAfterLast('.', "").lowercase().ifEmpty { null }

    private fun folderOf(path: String) = path.substringBeforeLast('/', "")
}
