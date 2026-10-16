package io.github.thatonecodingperson.thortools.roms

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.retroarch.BiosPaths
import io.github.thatonecodingperson.thortools.retroarch.CheatSystem
import io.github.thatonecodingperson.thortools.retroarch.CheatSystems
import io.github.thatonecodingperson.thortools.retroarch.RetroArchConfig
import io.github.thatonecodingperson.thortools.retroarch.RetroArchFiles
import io.github.thatonecodingperson.thortools.retroarch.RetroArchPlaces
import io.github.thatonecodingperson.thortools.retroarch.RetroArchState
import io.github.thatonecodingperson.thortools.retroarch.RomFile
import io.github.thatonecodingperson.thortools.retroarch.RomListing
import io.github.thatonecodingperson.thortools.retroarch.RomScan
import io.github.thatonecodingperson.thortools.tools.RootFiles
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the ROM folders hold, as ROM compression shows it. */
data class ShelfState(
    val folders: List<String> = emptyList(),
    /** Where root reached each folder (null: not there). */
    val reached: List<String?> = emptyList(),
    /** When the folders were last looked through (milliseconds; 0: never). */
    val time: Long = 0L,
    val games: Map<RomConsole, List<DiscGame>> = emptyMap(),
    val others: Map<CheatSystem, Int> = emptyMap(),
    val scanning: Boolean = false,
    /** Root didn't answer: nothing could be looked through. */
    val noRoot: Boolean = false,
    val estimates: Map<String, Estimate> = emptyMap(),
    val measuring: Boolean = false,
    val installed: List<InstalledApp> = emptyList(),
    /** RetroArch's installed cores by stem; null when RetroArch isn't there or they couldn't be read. */
    val cores: Set<String>? = null,
    val history: List<HistoryEntry> = emptyList(),
    /** Folders found on the storage that look like ROM folders, not added yet. */
    val suggestions: List<String> = emptyList(),
    /** Games whose image turned out not to be a game's disc when it was sampled (by id), and why. */
    val marked: Map<String, GameProblem> = emptyMap(),
    /** What the compressed files of the consoles with DVD images hold, by path. */
    val headers: Map<String, PackedHeader> = emptyMap(),
    /** What the cartridge consoles' archives hold, by path (null: the archive couldn't be read). */
    val listings: Map<String, ArchiveListing?> = emptyMap(),
    /** What the 3DS files are, by path. */
    val cards: Map<String, CardInfo?> = emptyMap(),
    /** What the WUA files hold, by path (null: the file couldn't be read). */
    val wuas: Map<String, WuaInfo?> = emptyMap(),
)

/** A finished job as ROM compression remembers it. */
data class HistoryEntry(
    val time: Long,
    val console: String,
    val title: String,
    val kind: JobKind,
    val original: Long,
    val estimated: Long,
    val actual: Long,
    /** The format made (null in entries kept before formats were told apart: a CD CHD). */
    val format: String? = null,
)

/**
 * The ROM folders, the games in them, their estimates and the emulators installed. Everything here reads storage or
 * asks root, so it all runs on its own background thread.
 */
@Singleton
class RomShelf @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPrefsRepo,
    private val executor: ShellExecutor,
    private val rootFiles: RootFiles,
    private val retroArchFiles: RetroArchFiles,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val work = Mutex()
    private val folder = File(context.filesDir, "roms")
    private var files: List<RomFile> = emptyList()
    private var texts: Map<String, String> = emptyMap()

    private val _state = MutableStateFlow(ShelfState())
    val state: StateFlow<ShelfState> = _state.asStateFlow()

    /** Fills the state from the kept list once, without looking through the folders (the main menu's card). */
    fun peek() = scope.launch { work.withLock { if (_state.value.time == 0L) loadKept() } }

    /** Fills the state from the kept list, then looks through the folders again when [scan] or when that list is old. */
    fun load(scan: Boolean = false) = scope.launch {
        work.withLock {
            if (_state.value.time == 0L) loadKept()
            readEmulators()
            suggest()
            if (scan || System.currentTimeMillis() - _state.value.time > STALE_MS) scanNow()
        }
    }

    fun rescan() = load(scan = true)

    /** Looks through the folders again, nothing else (after a job ended). */
    fun refreshList() = scope.launch { work.withLock { scanNow() } }

    fun addFolder(path: String) {
        prefs.romFolders = BiosPaths.withoutNested(prefs.romFolders + path)
        _state.update { it.copy(suggestions = it.suggestions.filterNot { s -> BiosPaths.isUnder(s, path) || BiosPaths.isUnder(path, s) }) }
        rescan()
    }

    fun removeFolder(path: String) {
        prefs.romFolders = prefs.romFolders - path
        rescan()
    }

    /** Adds the RetroArch assistant's ROM folders. */
    fun addRetroArchFolders() {
        prefs.romFolders = BiosPaths.withoutNested(prefs.romFolders + prefs.retroArchRomFolders)
        rescan()
    }

    fun dismissSuggestions() = _state.update { it.copy(suggestions = emptyList()) }

    /**
     * Measures the estimates of [console]'s games that don't have one yet, for every format their disc can become (and,
     * for GameCube and Wii, of compressed games too: what they become is measured the same way).
     */
    fun measure(console: RomConsole) = scope.launch {
        work.withLock {
            val games = _state.value.games[console].orEmpty().filter { game ->
                val measurable = game.canCompress || (game.canConvert && console.check == DiscCheck.NINTENDO)
                measurable && game.targets.any { it.tool != RomTool.NONE && key(game, it) !in _state.value.estimates }
            }
            if (games.isEmpty()) return@withLock
            _state.update { it.copy(measuring = true) }
            games.forEach { game ->
                val estimates = sample(game) ?: return@forEach
                _state.update { it.copy(estimates = it.estimates + estimates) }
            }
            _state.update { it.copy(measuring = false) }
            saveEstimates()
        }
    }

    /** The estimate of [game] in [format]: measured when there is one, else the console's usual saving. */
    fun estimate(game: DiscGame, format: RomFormat = game.defaultFormat): Estimate =
        _state.value.estimates[key(game, format)] ?: ShrinkEstimate.typical(game.originalSize, game.console, format)

    /** Free bytes on each of [volumes] (storage tops), read as root; a storage that can't be read is left out. Blocking. */
    fun freeBytes(volumes: Set<String>): Map<String, Long> {
        val list = volumes.toList()
        if (list.isEmpty()) return emptyMap()
        val script = list.mapIndexed { index, volume ->
            "set -- \$(df -k ${RootFiles.quote(volume)} 2>/dev/null | tail -n 1); echo \"$index \${4:-}\""
        }.joinToString("\n")
        val output = executor.capture(script).getOrNull() ?: return emptyMap()
        return output.lines().mapNotNull { line ->
            val parts = line.trim().split(' ')
            val volume = parts.getOrNull(0)?.toIntOrNull()?.let(list::getOrNull) ?: return@mapNotNull null
            val kb = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            volume to kb * 1024
        }.toMap()
    }

    /** The current text of [path] as the last look read it (a sheet or a playlist). */
    fun text(path: String): String? = texts[path]

    /** Forgets the finished jobs: the saving counted so far and what corrects the estimates. */
    fun clearHistory() = scope.launch {
        _state.update { it.copy(history = emptyList()) }
        runCatching { File(folder, HISTORY).delete() }
    }

    fun recordResult(job: RomJob, size: Long) = scope.launch {
        val entry = HistoryEntry(
            System.currentTimeMillis(),
            job.console.id,
            job.title,
            job.kind,
            job.inputBytes,
            job.estimate,
            size,
            job.format.name,
        )
        _state.update { it.copy(history = it.history + entry) }
        runCatching {
            folder.mkdirs()
            File(folder, HISTORY).appendText(encodeHistory(entry))
        }
    }

    private fun history(console: RomConsole, format: RomFormat): List<ShrinkResult> = _state.value.history
        .filter {
            it.console == console.id &&
                it.kind == JobKind.COMPRESS &&
                it.estimated > 0 &&
                (it.format ?: RomFormat.CHD_CD.name) == format.name
        }
        .map { ShrinkResult(it.original, it.estimated, it.actual) }

    private fun loadKept() {
        val kept = runCatching { RomScan.decode(File(folder, FILES).readText()) }.getOrNull()
        val keptTexts = runCatching { decodeTexts(File(folder, TEXTS).readText()) }.getOrDefault(emptyMap())
        val estimates = runCatching { decodeEstimates(File(folder, ESTIMATES).readText()) }.getOrDefault(emptyMap())
        val history = runCatching { File(folder, HISTORY).readLines().mapNotNull(::decodeHistory) }.getOrDefault(emptyList())
        listingCache = runCatching { decodeListings(File(folder, LISTINGS).readText()) }.getOrDefault(emptyMap())
        cardCache = runCatching { decodeCards(File(folder, CARDS).readText()) }.getOrDefault(emptyMap())
        wuaCache = runCatching { decodeWuas(File(folder, WUAS).readText()) }.getOrDefault(emptyMap())
        _state.update {
            it.copy(
                estimates = estimates,
                history = history,
                folders = prefs.romFolders,
                listings = listingCache.mapKeys { (key, _) -> key.substringBefore('\t') },
                cards = cardCache.mapKeys { (key, _) -> key.substringBefore('\t') },
                wuas = wuaCache.mapKeys { (key, _) -> key.substringBefore('\t') },
            )
        }
        if (kept != null && kept.folders == prefs.romFolders) {
            texts = keptTexts
            show(kept)
        }
    }

    private fun scanNow() {
        val folders = prefs.romFolders
        _state.update { it.copy(scanning = true, folders = folders) }
        if (folders.isEmpty()) {
            files = emptyList()
            texts = emptyMap()
            show(RomListing(System.currentTimeMillis(), emptyList(), emptyList(), emptyList()))
            _state.update { it.copy(scanning = false) }
            return
        }
        val output = executor.capture(RomScan.romScript(folders.map(BiosPaths::rootForms))).getOrNull()
        if (output == null) {
            _state.update { it.copy(scanning = false, noRoot = true) }
            return
        }
        val raw = RomScan.parseRoms(output, folders, System.currentTimeMillis())
        val listing = raw.copy(files = raw.files.map { it.copy(path = BiosPaths.shown(it.path)) })
        texts = readTexts(listing.files)
        val headers = readHeaders(listing.files)
        val listings = readListings(listing.files)
        val cards = readCards(listing.files)
        val wuas = readWuas(listing.files)
        _state.update { it.copy(headers = headers, listings = listings, cards = cards, wuas = wuas) }
        runCatching {
            folder.mkdirs()
            File(folder, FILES).writeText(RomScan.encode(listing))
            File(folder, TEXTS).writeText(encodeTexts(texts))
            File(folder, LISTINGS).writeText(encodeListings(listingCache))
            File(folder, CARDS).writeText(encodeCards(cardCache))
            File(folder, WUAS).writeText(encodeWuas(wuaCache))
        }
        show(listing)
        _state.update { it.copy(scanning = false, noRoot = false) }
    }

    private fun show(listing: RomListing) {
        files = listing.files
        val marked = _state.value.marked
        val headers = _state.value.headers
        val listings = _state.value.listings
        val cards = _state.value.cards
        val wuas = _state.value.wuas
        val games = RomConsole.entries.associateWith { console ->
            RomSets.games(console, files, texts, headers, listings, cards, wuas).map { game ->
                marked[game.id]?.let { game.copy(problem = it) } ?: game
            }
        }
        _state.update {
            it.copy(
                folders = listing.folders,
                reached = listing.reached,
                time = listing.time,
                games = games,
                others = RomSets.otherConsoles(files),
            )
        }
    }

    /**
     * The first bytes of every compressed file of the consoles with DVD images, read in one root call (`od` prints them
     * as hex, one line per file), and what they say (the extension tells a GameCube or Wii CISO from PSP's CSO).
     */
    private fun readHeaders(files: List<RomFile>): Map<String, PackedHeader> {
        val systems = RomConsole.entries.filter { it.takesIso }.map { it.system }.toSet()
        val wanted = files.filter { file ->
            file.name.substringAfterLast('.', "").lowercase() in PACKED && CheatSystems.of(file.path) in systems
        }
        if (wanted.isEmpty()) return emptyMap()
        val script = wanted.mapIndexed { index, file ->
            "printf '%s ' $index; od -An -tx1 -N${PackedHeaders.SIZE} ${RootFiles.quote(file.path)} 2>/dev/null | tr -d ' \\n'; echo"
        }.joinToString("\n")
        val output = executor.capture(script).getOrNull() ?: return emptyMap()
        return output.lines().mapNotNull { line ->
            val file = line.substringBefore(' ').toIntOrNull()?.let(wanted::getOrNull) ?: return@mapNotNull null
            val extension = file.name.substringAfterLast('.', "").lowercase()
            val bytes = PackedHeaders.fromHex(line.substringAfter(' ', "")) ?: return@mapNotNull null
            val header = PackedHeaders.parse(bytes, extension) ?: return@mapNotNull null
            file.path to header
        }.toMap()
    }

    /** The sheets and playlists, and the Wii U titles' `code/app.xml`, of the consoles Thor Tools compresses, read in one root call. */
    private fun readTexts(files: List<RomFile>): Map<String, String> {
        val systems = RomConsole.entries.map { it.system }.toSet()
        val wanted = files.filter { file ->
            val ext = file.name.substringAfterLast('.', "").lowercase()
            (ext in TEXT_FILES || WiiU.isAppXml(file.path)) && file.size < MAX_TEXT && CheatSystems.of(file.path) in systems
        }
        if (wanted.isEmpty()) return emptyMap()
        val script = wanted.joinToString("\n") { "echo ${RootFiles.quote(MARK + it.path)}; cat ${RootFiles.quote(it.path)}; echo" }
        val output = executor.capture(script).getOrNull() ?: return emptyMap()
        return splitTexts(output)
    }

    /**
     * Reads chunks spread over [game]'s data tracks as root and estimates from how well they compress, for each format
     * its disc can become. A DVD image whose first chunk holds no disc volume is marked instead.
     */
    private fun sample(game: DiscGame): Map<String, Estimate>? {
        if (game.console.check == DiscCheck.NINTENDO) return listed(game)
        if (game.kind == DiscKind.CART) return cartridgeSample(game)
        if (game.kind == DiscKind.FOLDER) return folderSample(game)
        val data = game.files.filter { !it.audio && (game.lone || !it.name.endsWith(".cue", ignoreCase = true)) }
        val total = data.sumOf { it.size }
        if (total <= 0) return null
        val reads = data.flatMap { file ->
            val count = maxOf(1, (SAMPLES * file.size / total).toInt())
            ShrinkEstimate.sampleOffsets(file.size, CHUNK, count).map { offset -> file.path to offset / CHUNK }
        }
        val script = reads.joinToString("\n") { (path, block) ->
            "dd if=${RootFiles.quote(path)} bs=$CHUNK skip=$block count=1 2>/dev/null"
        }
        val bytes = executor.captureBytes(script).getOrNull() ?: return null
        val chunks = (bytes.indices step CHUNK).map { start -> bytes.copyOfRange(start, minOf(bytes.size, start + CHUNK)) }
        if (game.kind == DiscKind.DVD) {
            val start = chunks.firstOrNull()
            val id = ShrinkEstimate.systemId(start)
            val problem = when {
                !ShrinkEstimate.holdsDisc(start) -> GameProblem.NotDisc
                id != null && game.console.notGames.any { id.startsWith(it) } -> GameProblem.NotGame
                else -> null
            }
            if (problem != null) {
                mark(game, problem)
                return null
            }
            return game.targets.associate { format ->
                val ratio = ShrinkEstimate.blockRatio(chunks, ShrinkEstimate.blockSize(format, game.originalSize)) ?: 1.0
                key(game, format) to ShrinkEstimate.image(game.originalSize, format, ratio, history(game.console, format))
            }
        }
        val ratio = ShrinkEstimate.deflateRatio(chunks) ?: return null
        val format = game.defaultFormat
        return mapOf(key(game, format) to ShrinkEstimate.measured(game.dataSize, game.audioSize, ratio, history(game.console, format)))
    }

    /**
     * A GameCube or Wii game's RVZ estimate from the bytes of the files on its disc, which nod lists as root (any format
     * nod reads). An image nod can't read is marked: not a disc when it is a plain image, unreadable otherwise.
     */
    private fun listed(game: DiscGame): Map<String, Estimate>? {
        val nod = nod ?: return null
        val path = game.main ?: game.compressed?.path ?: return null
        val output = executor.capture("${RootFiles.quote(nod)} list -s ${RootFiles.quote(path)} 2>/dev/null").getOrNull() ?: return null
        val used = ShrinkEstimate.listedBytes(output)
        if (used <= 0) {
            mark(game, if (game.main != null) GameProblem.NotDisc else GameProblem.CantRead)
            return null
        }
        val disc = game.imageSize ?: game.console.fullDisc(game.originalSize)
        return game.targets.filter { it.tool == RomTool.NOD }.associate { format ->
            key(game, format) to ShrinkEstimate.rvz(used, disc, history(game.console, format))
        }
    }

    /**
     * A plain ROM's estimates: chunks spread over it (all of a small one) are compressed with Deflate, which is what a
     * zip of it does; its 7z is the zip times its console's usual ratio.
     */
    private fun cartridgeSample(game: DiscGame): Map<String, Estimate>? {
        val path = game.main ?: return null
        val size = game.originalSize
        if (size <= 0) return null
        val offsets = ShrinkEstimate.sampleOffsets(
            size,
            CHUNK,
            if (size <=
                CHUNK * CART_SAMPLES
            ) {
                (size / CHUNK + 1).toInt()
            } else {
                CART_SAMPLES
            },
        )
        val script = offsets.joinToString("\n") { "dd if=${RootFiles.quote(path)} bs=$CHUNK skip=${it / CHUNK} count=1 2>/dev/null" }
        val bytes = executor.captureBytes(script).getOrNull() ?: return null
        val chunks = (bytes.indices step CHUNK).map { start -> bytes.copyOfRange(start, minOf(bytes.size, start + CHUNK)) }
        val ratio = ShrinkEstimate.deflateRatio(chunks) ?: return null
        return game.targets.filter { it.tool != RomTool.NONE }.associate { format ->
            key(game, format) to ShrinkEstimate.cartridge(size, ratio, game.console, format, history(game.console, format))
        }
    }

    /**
     * A Wii U game's WUA estimate: chunks at even steps through all its files, as if they were one (so big files get
     * more), compressed with Deflate, about what the WUA's Zstandard does.
     */
    private fun folderSample(game: DiscGame): Map<String, Estimate>? {
        if (game.main == null) return null
        val total = game.originalSize
        if (total <= 0) return null
        val reads = ShrinkEstimate.sampleOffsets(total, CHUNK, CART_SAMPLES).mapNotNull { at ->
            var left = at
            game.files.firstOrNull { file -> (left < file.size).also { if (!it) left -= file.size } }?.let { it.path to left }
        }
        val script = reads.joinToString("\n") { (path, offset) ->
            "dd if=${RootFiles.quote(path)} bs=$CHUNK skip=${offset / CHUNK} count=1 2>/dev/null"
        }
        val bytes = executor.captureBytes(script).getOrNull() ?: return null
        val chunks = (bytes.indices step CHUNK).map { start -> bytes.copyOfRange(start, minOf(bytes.size, start + CHUNK)) }
        val ratio = ShrinkEstimate.deflateRatio(chunks) ?: return null
        return game.targets.filter { it.tool != RomTool.NONE }.associate { format ->
            key(game, format) to ShrinkEstimate.folders(total, ratio, history(game.console, format))
        }
    }

    /**
     * What the cartridge consoles' archives hold, read as root with the bundled 7zz in one call; an archive whose size
     * and date haven't changed since it was last read keeps its listing.
     */
    private fun readListings(files: List<RomFile>): Map<String, ArchiveListing?> {
        val systems = RomConsole.entries.filter { it.cartridges }.map { it.system }.toSet()
        val archives = files.filter { file ->
            file.name.substringAfterLast('.', "").lowercase() in ARCHIVE_TYPES && file.size > 0 && CheatSystems.of(file.path) in systems
        }
        val keys = archives.associateWith { "${it.path}\t${it.size}\t${it.modified}" }
        val fresh = archives.filter { keys.getValue(it) !in listingCache }
        val sevenZip = sevenZip
        if (fresh.isNotEmpty() && sevenZip != null) {
            val script = fresh.mapIndexed { index, file ->
                "echo '$LIST_MARK$index'; ${RootFiles.quote(
                    sevenZip,
                )} l -slt -ba -- ${RootFiles.quote(file.path)} 2>/dev/null; echo \"$LIST_END\$?\""
            }.joinToString("\n")
            executor.capture(script).getOrNull()?.let { output ->
                output.split(LIST_MARK).drop(1).forEach { block ->
                    val index = block.substringBefore('\n').trim().toIntOrNull() ?: return@forEach
                    val file = fresh.getOrNull(index) ?: return@forEach
                    val ok = block.substringAfterLast(LIST_END).trim() == "0"
                    listingCache =
                        listingCache + (keys.getValue(file) to if (ok) ArchiveListings.parse(block.substringBefore(LIST_END)) else null)
                }
            }
        }
        listingCache = listingCache.filterKeys { it in keys.values }
        return archives.mapNotNull { file ->
            keys.getValue(file).takeIf { it in listingCache }?.let { file.path to listingCache[it] }
        }.toMap()
    }

    /** Listings by `path\tsize\tmodified`, kept between scans. */
    private var listingCache: Map<String, ArchiveListing?> = emptyMap()

    /**
     * What the 3DS files are (a plain ROM's type and whether it is encrypted, what a Z3DS file holds), read as root
     * with the bundled z3ds tool in one call; a file whose size and date haven't changed keeps what was read.
     */
    private fun readCards(files: List<RomFile>): Map<String, CardInfo?> {
        val systems = RomConsole.entries.filter { RomFormat.Z3DS in it.allFormats }.map { it.system }.toSet()
        val types = RomConsole.N3DS.romTypes + RomConsole.N3DS.packedTypes.keys
        val wanted = files.filter { file ->
            file.name.substringAfterLast('.', "").lowercase() in types &&
                file.size > 0 &&
                CheatSystems.of(file.path) in systems &&
                !N3ds.inEmulatorData(file.path)
        }
        val keys = wanted.associateWith { "${it.path}\t${it.size}\t${it.modified}" }
        val fresh = wanted.filter { keys.getValue(it) !in cardCache }
        val tool = z3ds
        if (fresh.isNotEmpty() && tool != null) {
            val script = "${RootFiles.quote(tool)} info ${fresh.joinToString(" ") { RootFiles.quote(it.path) }} 2>/dev/null"
            executor.capture(script).getOrNull()?.let { output ->
                val read = N3ds.parseInfo(output)
                fresh.forEach { file -> cardCache = cardCache + (keys.getValue(file) to read[file.path]) }
            }
        }
        cardCache = cardCache.filterKeys { it in keys.values }
        return wanted.mapNotNull { file -> keys.getValue(file).takeIf { it in cardCache }?.let { file.path to cardCache[it] } }.toMap()
    }

    /** What 3DS files are, by `path\tsize\tmodified`, kept between scans. */
    private var cardCache: Map<String, CardInfo?> = emptyMap()

    /**
     * What the WUA files hold (their unpacked size and title folders), read as root with the bundled wua tool in one
     * call; a file whose size and date haven't changed keeps what was read.
     */
    private fun readWuas(files: List<RomFile>): Map<String, WuaInfo?> {
        val systems = RomConsole.entries.filter { it.listKind == DiscKind.FOLDER }.map { it.system }.toSet()
        val wanted = files.filter { file ->
            file.name.endsWith(".wua", ignoreCase = true) &&
                file.size > 0 &&
                CheatSystems.of(file.path) in systems &&
                !WiiU.inEmulatorData(file.path)
        }
        val keys = wanted.associateWith { "${it.path}\t${it.size}\t${it.modified}" }
        val fresh = wanted.filter { keys.getValue(it) !in wuaCache }
        val tool = wuaTool
        if (fresh.isNotEmpty() && tool != null) {
            val script = "${RootFiles.quote(tool)} info ${fresh.joinToString(" ") { RootFiles.quote(it.path) }} 2>/dev/null"
            executor.capture(script).getOrNull()?.let { output ->
                val read = WiiU.parseInfo(output)
                fresh.forEach { file -> wuaCache = wuaCache + (keys.getValue(file) to read[file.path]) }
            }
        }
        wuaCache = wuaCache.filterKeys { it in keys.values }
        return wanted.mapNotNull { file -> keys.getValue(file).takeIf { it in wuaCache }?.let { file.path to wuaCache[it] } }.toMap()
    }

    /** What WUA files hold, by `path\tsize\tmodified`, kept between scans. */
    private var wuaCache: Map<String, WuaInfo?> = emptyMap()

    /** The bundled wua tool; null when this device has no copy. */
    private val wuaTool: String? get() = File(context.applicationInfo.nativeLibraryDir, ChdTool.WUA_FILE).takeIf {
        it.exists()
    }?.absolutePath

    /** The bundled z3ds tool; null when this device has no copy. */
    private val z3ds: String? get() = File(context.applicationInfo.nativeLibraryDir, ChdTool.Z3DS_FILE).takeIf { it.exists() }?.absolutePath

    /** The bundled 7zz; null when this device has no copy. */
    private val sevenZip: String?
        get() = File(context.applicationInfo.nativeLibraryDir, ChdTool.SEVENZIP_FILE).takeIf { it.exists() }?.absolutePath

    /** Marks [game] as having [problem], found while measuring, until the folders are looked through again. */
    private fun mark(game: DiscGame, problem: GameProblem) = _state.update { state ->
        state.copy(
            marked = state.marked + (game.id to problem),
            games = state.games.mapValues { (_, list) -> list.map { if (it.id == game.id) it.copy(problem = problem) else it } },
        )
    }

    /** The bundled nodtool; null when this device has no copy. */
    private val nod: String? get() = File(context.applicationInfo.nativeLibraryDir, ChdTool.NOD_FILE).takeIf { it.exists() }?.absolutePath

    private fun readEmulators() {
        val known = EmulatorChecks.knownPackages
        val installed = runCatching { context.packageManager.getInstalledPackages(0) }.getOrDefault(emptyList())
            .filter { it.packageName in known }
            .map { InstalledApp(it.packageName, it.versionName) }
        val cores = when (val retroArch = retroArchFiles.find()) {
            is RetroArchState.Found -> {
                val config = retroArch.configPath?.let { rootFiles.read(it) }?.let(::RetroArchConfig)
                val coresFolder =
                    config?.let { RetroArchPlaces.of(it, retroArch.packageName).cores } ?: "/data/user/0/${retroArch.packageName}/cores"
                executor.capture("ls -1 ${RootFiles.quote(coresFolder)}").getOrNull()
                    ?.lines()?.filter { it.endsWith(CORE_SUFFIX) }?.map { it.removeSuffix(CORE_SUFFIX) }?.toSet()
            }
            else -> null
        }
        _state.update { it.copy(installed = installed, cores = cores) }
    }

    /** Looks once for folders named like ROM folders at the top of each storage. */
    private fun suggest() {
        val script = "for v in /storage/emulated/0 /storage/*; do [ -d \"\$v\" ] || continue; " +
            "ls -1 \"\$v\" 2>/dev/null | while IFS= read -r n; do echo \"\$v/\$n\"; done; done; " +
            "ls -1 /storage/emulated/0/Emulation 2>/dev/null | while IFS= read -r n; do echo \"/storage/emulated/0/Emulation/\$n\"; done"
        val found = executor.capture(script).getOrNull()?.lines().orEmpty()
            .filter { it.substringAfterLast('/').lowercase() in FOLDER_NAMES }
            .distinctBy { it.lowercase() }
        val retroArch = prefs.retroArchRomFolders
        val current = prefs.romFolders
        val suggestions = (found + retroArch).distinct().filter { candidate ->
            current.none {
                BiosPaths.isUnder(candidate, it) ||
                    BiosPaths.isUnder(it, candidate)
            }
        }
        _state.update { it.copy(suggestions = BiosPaths.withoutNested(suggestions)) }
    }

    private fun saveEstimates() = runCatching {
        folder.mkdirs()
        File(folder, ESTIMATES).writeText(_state.value.estimates.entries.joinToString("") { (key, e) -> "$key\t${e.low}\t${e.high}\n" })
    }

    companion object {
        private const val FILES = "files.txt"
        private const val STALE_MS = 2 * 60 * 1000L
        private const val TEXTS = "texts.txt"
        private const val ESTIMATES = "estimates.txt"
        private const val LISTINGS = "listings.txt"
        private const val CARDS = "cards.txt"
        private const val WUAS = "wuas.txt"
        private const val LIST_MARK = "@@thortools-archive "
        private const val LIST_END = "@@thortools-rc "
        private const val CART_SAMPLES = 16
        private val ARCHIVE_TYPES = setOf("zip", "7z")
        private const val HISTORY = "history.txt"
        private const val MARK = "@@thortools-file "
        private const val CHUNK = 64 * 1024
        private const val SAMPLES = 24
        private const val MAX_TEXT = 256 * 1024L
        private const val CORE_SUFFIX = "_android.so"
        private val TEXT_FILES = setOf("cue", "m3u")
        private val PACKED = setOf("chd", "cso", "zso", "rvz", "wia", "wbfs", "ciso", "gcz")
        private val FOLDER_NAMES = setOf("roms", "rom")

        /** The key an estimate is kept under: the game's main file, its size and (but for a CD CHD) the format. */
        fun key(game: DiscGame, format: RomFormat = game.defaultFormat): String =
            if (format == RomFormat.CHD_CD) "${game.id}|${game.originalSize}" else "${game.id}|${game.originalSize}|${format.name}"

        fun splitTexts(output: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            var path: String? = null
            val body = StringBuilder()
            fun end() {
                path?.let { result[it] = body.toString().trimEnd('\n', '\r') }
                body.setLength(0)
            }
            output.lineSequence().forEach { line ->
                if (line.startsWith(MARK)) {
                    end()
                    path = line.removePrefix(MARK)
                } else if (path != null) {
                    body.append(line).append('\n')
                }
            }
            end()
            return result
        }

        /** Kept listings: a key line (`@@key\t<path\tsize\tmodified>`), then one `size\tcrc\tfolder\tpath` line per entry. */
        /** What WUA files hold, one per line: `path size modified` (the key), then what was read, tab-separated. */
        fun encodeWuas(wuas: Map<String, WuaInfo?>): String =
            wuas.entries.joinToString("") { (key, info) -> "$key\t${WiiU.encodeInfo(info)}\n" }

        fun decodeWuas(text: String): Map<String, WuaInfo?> = text.lineSequence().filter { it.isNotEmpty() }.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 5) return@mapNotNull null
            parts.take(3).joinToString("\t") to WiiU.decodeInfo(parts.drop(3).joinToString("\t"))
        }.toMap()

        /** What 3DS files are, one per line: `path size modified` (the key), then what was read, tab-separated. */
        fun encodeCards(cards: Map<String, CardInfo?>): String =
            cards.entries.joinToString("") { (key, info) -> "$key\t${N3ds.encodeInfo(info)}\n" }

        fun decodeCards(text: String): Map<String, CardInfo?> = text.lineSequence().filter { it.isNotEmpty() }.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 4) return@mapNotNull null
            parts.take(3).joinToString("\t") to N3ds.decodeInfo(parts.drop(3).joinToString("\t"))
        }.toMap()

        fun encodeListings(listings: Map<String, ArchiveListing?>): String = buildString {
            listings.forEach { (key, listing) ->
                append("@@key\t").append(key).append(if (listing == null) "\t-" else "").append('\n')
                listing?.entries?.forEach { e ->
                    append(e.size).append('\t').append(e.crc.orEmpty()).append('\t').append(if (e.folder) "+" else "-")
                        .append('\t').append(e.path).append('\n')
                }
            }
        }

        fun decodeListings(text: String): Map<String, ArchiveListing?> {
            val result = LinkedHashMap<String, ArchiveListing?>()
            var key: String? = null
            var entries = mutableListOf<ArchiveEntry>()
            var unreadable = false
            fun end() {
                key?.let { result[it] = if (unreadable) null else ArchiveListing(entries) }
                entries = mutableListOf()
            }
            text.lineSequence().forEach { line ->
                if (line.startsWith("@@key\t")) {
                    end()
                    val parts = line.removePrefix("@@key\t").split('\t')
                    unreadable = parts.size == 4 && parts[3] == "-"
                    key = parts.take(3).joinToString("\t")
                } else if (line.isNotEmpty()) {
                    val parts = line.split('\t', limit = 4)
                    if (parts.size ==
                        4
                    ) {
                        entries += ArchiveEntry(parts[3], parts[0].toLongOrNull() ?: 0L, parts[1].ifEmpty { null }, parts[2] == "+")
                    }
                }
            }
            end()
            return result
        }

        fun encodeTexts(texts: Map<String, String>): String = texts.entries.joinToString("") { (path, text) -> "$MARK$path\n$text\n" }

        fun decodeTexts(text: String): Map<String, String> = splitTexts(text)

        fun decodeEstimates(text: String): Map<String, Estimate> = text.lines().mapNotNull { line ->
            val parts = line.split('\t')
            val low = parts.getOrNull(1)?.toLongOrNull()
            val high = parts.getOrNull(2)?.toLongOrNull()
            if (parts.size == 3 && low != null && high != null) parts[0] to Estimate(low, high, measured = true) else null
        }.toMap()

        fun encodeHistory(entry: HistoryEntry): String = listOf(
            entry.time,
            entry.console,
            entry.title.replace('\t', ' '),
            entry.kind.name,
            entry.original,
            entry.estimated,
            entry.actual,
            entry.format.orEmpty(),
        )
            .joinToString("\t", postfix = "\n")

        fun decodeHistory(line: String): HistoryEntry? {
            val parts = line.split('\t')
            if (parts.size != 7 && parts.size != 8) return null
            return HistoryEntry(
                time = parts[0].toLongOrNull() ?: return null,
                console = parts[1],
                title = parts[2],
                kind = runCatching { JobKind.valueOf(parts[3]) }.getOrNull() ?: return null,
                original = parts[4].toLongOrNull() ?: return null,
                estimated = parts[5].toLongOrNull() ?: return null,
                actual = parts[6].toLongOrNull() ?: return null,
                format = parts.getOrNull(7)?.ifEmpty { null },
            )
        }
    }
}
