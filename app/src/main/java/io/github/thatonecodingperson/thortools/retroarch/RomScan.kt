package io.github.thatonecodingperson.thortools.retroarch

import io.github.thatonecodingperson.thortools.tools.RootFiles

/** A file root listed under the ROM folders: its path as root reached it, its size, and when it last changed (seconds). */
data class RomFile(val path: String, val size: Long, val modified: Long) {
    val name: String get() = path.substringAfterLast('/')
}

/**
 * One look through the ROM folders: when it was ([time], milliseconds), the [folders] asked for (as the Files app shows
 * them), where root reached each one (null when it wasn't there), and every file under them.
 */
data class RomListing(val time: Long, val folders: List<String>, val reached: List<String?>, val files: List<RomFile>) {
    /** How many files [folder] had; null when it wasn't there or wasn't looked at. */
    fun files(folder: String): Int? {
        val index = folders.indexOf(folder)
        val root = reached.getOrNull(index) ?: return null
        return files.count { it.path.startsWith("$root/") }
    }
}

/**
 * A RetroArch core: [stem] names its files (`snes9x_libretro`), [name] is its info file's `corename` (`Snes9x`; RetroArch's
 * folders may use another name, see [CoreFolders]), [databases] what it plays, [cheats] whether its info file says it takes
 * cheat codes (null when it doesn't say).
 */
data class CoreInfo(val stem: String, val name: String, val databases: List<String>, val cheats: Boolean? = null)

/** A cheat file in RetroArch's cheats folder: the core's folder it is in, its name, how many cheats it has and how many are on. */
data class InstalledCheats(val core: String, val name: String, val count: Int, val on: Int)

/**
 * What RetroArch has: its cheats folder as root reaches it, the cheat files in the cores' folders there and those
 * folders, the folders RetroArch made for its cores in its config, saves and states folders ([made]), each such folder's
 * files in the saves and states folders ([played]), the installed cores, and the games it started ([history], newest
 * first).
 */
data class RetroArchSide(
    val cheatsRoot: String? = null,
    val installed: List<InstalledCheats> = emptyList(),
    val cheatFolders: Set<String> = emptySet(),
    val made: Set<String> = emptySet(),
    val played: Map<String, Set<String>> = emptyMap(),
    val cores: List<CoreInfo> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
) {
    /** Each installed core's folder, by its file stem. */
    val folders: Map<String, String> by lazy { CoreFolders.resolve(cores, made, played, history) }

    /** The folder of the core with file stem [stem], installed or not; null when RetroArch has none for it. */
    fun folderOf(stem: String): String? = CoreFolders.forStem(stem, folders, made)
}

/**
 * Where RetroArch keeps things: its cheats, saves, states and menu config folders, each as the paths root may reach it
 * under (the likeliest first; empty when its settings don't name one), its history file's forms, and its cores' and their
 * info files' folders.
 */
data class RetroArchPlaces(
    val cheats: List<String>,
    val saves: List<String>,
    val states: List<String>,
    val config: List<String>,
    val history: List<String>,
    val cores: String,
    val info: String,
) {
    companion object {
        const val CHEATS = "cheat_database_path"
        private const val SAVES = "savefile_directory"
        private const val STATES = "savestate_directory"
        private const val CONFIG = "rgui_config_directory"
        private const val HISTORY = "content_history_path"
        private const val PLAYLISTS = "playlist_directory"
        private const val CORES = "libretro_directory"
        private const val INFO = "libretro_info_path"
        private const val HISTORY_FILE = "builtin/content_history.lpl"

        /** The folders [config] names; where it names none, RetroArch's own ones on Android. */
        fun of(config: RetroArchConfig, packageName: String): RetroArchPlaces {
            val media = "${BiosPaths.SHARED}/Android/media/$packageName/RetroArch"
            val history = BiosPaths.configured(config.value(HISTORY))
                ?: "${BiosPaths.configured(config.value(PLAYLISTS)) ?: "$media/playlists"}/$HISTORY_FILE"
            return RetroArchPlaces(
                cheats = BiosPaths.rootForms(BiosPaths.configured(config.value(CHEATS)) ?: "$media/cheats"),
                saves = BiosPaths.configured(config.value(SAVES))?.let(BiosPaths::rootForms).orEmpty(),
                states = BiosPaths.configured(config.value(STATES))?.let(BiosPaths::rootForms).orEmpty(),
                config = BiosPaths.rootForms(BiosPaths.configured(config.value(CONFIG)) ?: "$media/config"),
                history = BiosPaths.rootForms(history),
                cores = BiosPaths.configured(config.value(CORES)) ?: "/data/user/0/$packageName/cores",
                info = BiosPaths.configured(config.value(INFO)) ?: "/data/user/0/$packageName/info",
            )
        }
    }
}

/** The root scripts that list the ROM folders and what RetroArch has, what they print, and the ROM list's cache file. */
object RomScan {
    private const val CACHE_HEADER = "thortools-roms 1"

    /** Lists every file under each folder (given as the paths root may reach it under): `D <i> <dir>`, `R <i> <size> <time> <path>`. */
    fun romScript(folders: List<List<String>>): String = folders.mapIndexed { index, forms ->
        firstDir(forms, "echo \"D $index \$c\"; find \"\$c\" -type f -exec stat -c 'R $index %s %Y %n' {} +")
    }.joinToString("\n")

    /**
     * Lists what RetroArch has: `T <dir>` for its cheats folder, `P <dir>` for each core's folder there and `H <on>
     * <count> <path>` for each cheat file in one; `Q <dir>` for each core's folder in the saves and states folders and
     * `V <path>` for each file in one; `C <dir>` for each core's folder in the menu config folder; `K <stem>` for each
     * installed core followed by `I <line>` for its info file's name, databases and whether it takes cheats.
     */
    fun retroArchScript(places: RetroArchPlaces): String {
        val cheats = firstDir(
            places.cheats,
            "echo \"T \$c\"; " + coreFolders("P") + "; " +
                "find \"\$c\" -mindepth 2 -maxdepth 2 -type f -name '*.cht' | while IFS= read -r f; do " +
                "on=\$(grep -cE '^[[:space:]]*cheat[0-9]+_enable[[:space:]]*=[[:space:]]*\"?(true|1)' \"\$f\"); " +
                "n=\$(grep -m 1 -E '^[[:space:]]*cheats[[:space:]]*=' \"\$f\" | tr -dc '0-9'); " +
                "echo \"H \${on:-0} \${n:-0} \$f\"; done",
        )
        val played = listOf(places.saves, places.states).filter { it.isNotEmpty() }.map { forms ->
            firstDir(forms, coreFolders("Q") + "; find \"\$c\" -mindepth 2 -maxdepth 2 -type f -exec stat -c 'V %n' {} +")
        }
        val config = places.config.takeIf { it.isNotEmpty() }?.let { firstDir(it, coreFolders("C")) }
        val cores = "for f in ${RootFiles.quote(places.cores)}/*_libretro_android.so; do [ -e \"\$f\" ] || continue; " +
            "s=\${f##*/}; s=\${s%_android.so}; echo \"K \$s\"; i=${RootFiles.quote(places.info)}/\"\$s.info\"; " +
            "[ -f \"\$i\" ] && grep -E '^(corename|database|cheats)[[:space:]]*=' \"\$i\" | while IFS= read -r l; do echo \"I \$l\"; done; done"
        return (listOf(cheats) + played + listOfNotNull(config) + cores).joinToString("\n")
    }

    fun parseRoms(output: String, folders: List<String>, time: Long): RomListing {
        val reached = MutableList<String?>(folders.size) { null }
        val files = mutableListOf<RomFile>()
        output.lineSequence().forEach { line ->
            when (line.substringBefore(' ')) {
                "D" -> {
                    val parts = line.split(' ', limit = 3)
                    val index = parts.getOrNull(1)?.toIntOrNull()
                    if (index != null && index in folders.indices && parts.size == 3) reached[index] = parts[2]
                }
                "R" -> {
                    val parts = line.split(' ', limit = 5)
                    val index = parts.getOrNull(1)?.toIntOrNull()
                    val size = parts.getOrNull(2)?.toLongOrNull()
                    val modified = parts.getOrNull(3)?.toLongOrNull()
                    if (index != null && index in folders.indices && size != null && modified != null && parts.size == 5) {
                        files += RomFile(parts[4], size, modified)
                    }
                }
            }
        }
        return RomListing(time, folders, reached, files.distinctBy { it.path })
    }

    fun parseRetroArch(output: String): RetroArchSide {
        var cheatsRoot: String? = null
        val installed = mutableListOf<InstalledCheats>()
        val cheatFolders = mutableSetOf<String>()
        val made = mutableSetOf<String>()
        val played = mutableMapOf<String, MutableSet<String>>()
        val cores = mutableListOf<CoreInfo>()
        var stem: String? = null
        var name: String? = null
        var databases = emptyList<String>()
        var takesCheats: Boolean? = null
        fun endCore() {
            val coreStem = stem
            val coreName = name
            if (coreStem != null && coreName != null) cores += CoreInfo(coreStem, coreName, databases, takesCheats)
            stem = null
            name = null
            databases = emptyList()
            takesCheats = null
        }
        output.lineSequence().forEach { line ->
            val tag = line.substringBefore(' ')
            val rest = line.substringAfter(' ', "")
            when (tag) {
                "T" -> cheatsRoot = rest.ifEmpty { null }
                "P", "Q", "C" -> rest.substringAfterLast('/').takeIf { it.isNotEmpty() }?.let { folder ->
                    if (tag == "P") cheatFolders += folder else made += folder
                }
                "H" -> {
                    val parts = line.split(' ', limit = 4)
                    val on = parts.getOrNull(1)?.toIntOrNull()
                    val count = parts.getOrNull(2)?.toIntOrNull()
                    val path = parts.getOrNull(3)
                    if (on != null && count != null && path != null) {
                        val core = path.substringBeforeLast('/').substringAfterLast('/')
                        installed += InstalledCheats(core, path.substringAfterLast('/'), count, on)
                    }
                }
                "V" -> {
                    val core = rest.substringBeforeLast('/', "").substringAfterLast('/')
                    if (core.isNotEmpty()) played.getOrPut(core) { mutableSetOf() } += rest.substringAfterLast('/')
                }
                "K" -> {
                    endCore()
                    stem = rest.ifEmpty { null }
                }
                "I" -> {
                    val key = rest.substringBefore('=').trim()
                    val value = rest.substringAfter('=', "").trim().removeSurrounding("\"")
                    when (key) {
                        "corename" -> name = value.ifEmpty { null }
                        "database" -> databases = value.split('|').map { it.trim() }.filter { it.isNotEmpty() }
                        "cheats" -> takesCheats = value.toBooleanStrictOrNull()
                    }
                }
            }
        }
        endCore()
        return RetroArchSide(cheatsRoot, installed, cheatFolders, made, played, cores)
    }

    /** How many lines of each kind [retroArchScript] printed, the cores it found, and lines it didn't expect (errors). */
    fun summary(output: String): String {
        val lines = output.lines().filter { it.isNotBlank() }
        val tags = setOf("T", "P", "H", "Q", "V", "C", "K", "I")
        val counts = lines.groupingBy { it.substringBefore(' ') }.eachCount().filterKeys { it in tags }
        val cores = lines.filter { it.startsWith("K ") }.joinToString(",") { it.removePrefix("K ") }
        val other = lines.filter { it.substringBefore(' ') !in tags }.take(SUMMARY_OTHER)
        return "RetroArch scan: $counts; cores: $cores; other: $other"
    }

    private const val SUMMARY_OTHER = 5

    /** The ROM list as the cache file keeps it: one value per line, tab-separated. */
    fun encode(listing: RomListing): String = buildString {
        appendLine(CACHE_HEADER)
        appendLine("time\t${listing.time}")
        listing.folders.forEachIndexed { index, folder -> appendLine("folder\t$folder\t${listing.reached[index].orEmpty()}") }
        listing.files.filter { '\t' !in it.path && '\n' !in it.path }.forEach { file ->
            appendLine("file\t${file.size}\t${file.modified}\t${file.path}")
        }
    }

    /** The cached ROM list; null when [text] isn't one. */
    fun decode(text: String): RomListing? {
        val lines = text.lines()
        if (lines.firstOrNull() != CACHE_HEADER) return null
        var time = 0L
        val folders = mutableListOf<String>()
        val reached = mutableListOf<String?>()
        val files = mutableListOf<RomFile>()
        lines.drop(1).forEach { line ->
            val parts = line.split('\t')
            when (parts[0]) {
                "time" -> time = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                "folder" -> if (parts.size == 3) {
                    folders += parts[1]
                    reached += parts[2].ifEmpty { null }
                }
                "file" -> {
                    val size = parts.getOrNull(1)?.toLongOrNull()
                    val modified = parts.getOrNull(2)?.toLongOrNull()
                    if (parts.size == 4 && size != null && modified != null) files += RomFile(parts[3], size, modified)
                }
            }
        }
        return RomListing(time, folders, reached, files)
    }

    /** Lists the core folders in the folder the loop found, each on a line starting with [tag]. */
    private fun coreFolders(tag: String) = "find \"\$c\" -mindepth 1 -maxdepth 1 -type d -exec stat -c '$tag %n' {} +"

    private fun firstDir(forms: List<String>, then: String) =
        "for c in ${forms.joinToString(" ") { RootFiles.quote(it) }}; do if [ -d \"\$c\" ]; then $then; break; fi; done"
}
