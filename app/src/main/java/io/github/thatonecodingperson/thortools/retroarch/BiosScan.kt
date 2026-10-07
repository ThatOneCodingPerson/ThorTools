package io.github.thatonecodingperson.thortools.retroarch

import io.github.thatonecodingperson.thortools.tools.RootFiles

/** A file root listed: its path as root reached it, and its size. */
data class ListedFile(val path: String, val size: Long) {
    val name: String get() = path.substringAfterLast('/')
}

/**
 * One look through the user's BIOS folders and RetroArch's BIOS folder: for each folder asked for, the path root reached
 * it under (null when it isn't there) and how many files it has; every file in them; RetroArch's folder and its files.
 */
data class BiosListing(
    val folders: List<String?>,
    val counts: List<Int>,
    val found: List<ListedFile>,
    val system: String?,
    val systemFiles: List<ListedFile>,
)

/** How a file fits a BIOS file of the table. */
enum class BiosFit {
    /** Its MD5 is one documented for the file, whatever it is called. */
    CHECKED,

    /** The same name, for a file whose contents vary, so there is nothing to check. */
    SAME_NAME,

    /** The same name, but not a documented MD5. */
    OTHER_FILE,

    /** A file a core takes under its own name, recognised by its name and size. */
    NAME_AND_SIZE,
}

/** A file in the user's folders that can be copied for a BIOS file. */
data class BiosCandidate(val path: String, val size: Long, val md5: String?, val fit: BiosFit)

/** The file RetroArch's folder has for a BIOS file; [atPath] false when a core finds it under another name by its contents. */
data class BiosInstalled(val path: String, val md5: String?, val fit: BiosFit, val atPath: Boolean = true)

/** A BIOS file with what RetroArch's folder has for it and the files found that could be copied for it. */
data class BiosEntry(val file: BiosFile, val installed: BiosInstalled?, val candidates: List<BiosCandidate>) {
    val key: String get() = file.key

    /** In RetroArch's folder or found in the user's folders. */
    val seen: Boolean get() = installed != null || candidates.isNotEmpty()
}

/**
 * Lists the files under the user's folders and RetroArch's BIOS folder, picks the few worth hashing and matches them
 * to [BiosTable]: a file is recognised by its MD5 whatever its name, else by its name.
 */
object BiosScan {
    /** Deep enough for `pcsx2/bios/<file>` inside RetroArch's folder. */
    private const val SYSTEM_DEPTH = 3
    private const val HASH_BATCH = 40
    private val MD5_LINE = Regex("^([0-9a-fA-F]{32}) [ *](.+)$")

    /**
     * Lists every file under each of [folders] (each given as the paths root may reach it under, the first one there
     * counts) and under RetroArch's [system] folder, with sizes: `D <i> <dir>` for a folder found, `F <i> <size> <path>`
     * for its files, `T <dir>` and `S <size> <path>` for RetroArch's folder.
     */
    fun listScript(folders: List<List<String>>, system: String?): String = buildList {
        folders.forEachIndexed { index, forms ->
            add(firstDir(forms, "echo \"D $index \$c\"; find \"\$c\" -type f -exec stat -c 'F $index %s %n' {} +"))
        }
        if (system != null) {
            add(firstDir(listOf(system), "echo \"T \$c\"; find \"\$c\" -maxdepth $SYSTEM_DEPTH -type f -exec stat -c 'S %s %n' {} +"))
        }
    }.joinToString("\n")

    /** Prints the first of [forms] that is a folder. */
    fun dirScript(forms: List<String>): String = firstDir(forms, "echo \"\$c\"")

    fun md5Script(paths: List<String>): String =
        paths.chunked(HASH_BATCH).joinToString("\n") { batch -> "md5sum " + batch.joinToString(" ") { RootFiles.quote(it) } }

    fun parseListing(output: String, folderCount: Int): BiosListing {
        val folders = MutableList<String?>(folderCount) { null }
        val counts = MutableList(folderCount) { 0 }
        val found = mutableListOf<ListedFile>()
        var system: String? = null
        val systemFiles = mutableListOf<ListedFile>()
        output.lineSequence().forEach { line ->
            when (line.substringBefore(' ')) {
                "D" -> {
                    val parts = line.split(' ', limit = 3)
                    val index = parts.getOrNull(1)?.toIntOrNull()
                    if (index != null && index in 0 until folderCount && parts.size == 3) folders[index] = parts[2]
                }
                "F" -> {
                    val parts = line.split(' ', limit = 4)
                    val index = parts.getOrNull(1)?.toIntOrNull()
                    val size = parts.getOrNull(2)?.toLongOrNull()
                    if (index != null && index in 0 until folderCount && size != null && parts.size == 4) {
                        found += ListedFile(parts[3], size)
                        counts[index]++
                    }
                }
                "T" -> system = line.substringAfter(' ').ifEmpty { null }
                "S" -> {
                    val parts = line.split(' ', limit = 3)
                    val size = parts.getOrNull(1)?.toLongOrNull()
                    if (size != null && parts.size == 3) systemFiles += ListedFile(parts[2], size)
                }
            }
        }
        return BiosListing(folders, counts, found, system, systemFiles)
    }

    /** Each hashed file's MD5 by its path. */
    fun parseMd5(output: String): Map<String, String> = output.lineSequence().mapNotNull { line ->
        MD5_LINE.matchEntire(line)?.let { it.groupValues[2] to it.groupValues[1].lowercase() }
    }.toMap()

    /**
     * The files worth hashing: the size or the name (any case) of a known BIOS file, so a big game image is never read.
     */
    fun toHash(listing: BiosListing, files: List<BiosFile> = BiosTable.files, loose: List<BiosLoose> = BiosTable.loose): List<String> {
        val sizes = (files.mapNotNull { it.size } + loose.map { it.size }).toSet()
        val names = files.map { it.name.lowercase() }.toSet()
        return (listing.found + listing.systemFiles)
            .filter { it.size in sizes || it.name.lowercase() in names }
            .map { it.path }
            .distinct()
    }

    /**
     * Every table file with what RetroArch's folder has for it and what was found for it (files inside RetroArch's own
     * folder only count as what it has), followed by the files the [loose] rules take under their own names.
     */
    fun match(
        listing: BiosListing,
        md5: Map<String, String>,
        files: List<BiosFile> = BiosTable.files,
        loose: List<BiosLoose> = BiosTable.loose,
    ): List<BiosEntry> {
        val system = listing.system
        val inSystem = if (system == null) emptyMap() else listing.systemFiles.associateBy { relative(it.path, system).lowercase() }
        val sources = listing.found.filterNot { system != null && BiosPaths.isUnder(it.path, system) }.distinctBy { it.path }
        val known = files.flatMap { it.md5 }.toSet()
        val entries = files.map { file ->
            val installed = installed(file, inSystem, md5)
            val candidates = sources.mapNotNull { found ->
                fit(file, found, md5[found.path], known)?.let { BiosCandidate(found.path, found.size, md5[found.path], it) }
            }
            BiosEntry(file, installed, candidates.withoutInstalled(installed))
        }
        val extra = loose.flatMap { looseEntries(it, files, sources, inSystem, md5) }
        return (entries + extra).sortedBy { it.file.console.ordinal }
    }

    private fun installed(file: BiosFile, inSystem: Map<String, ListedFile>, md5: Map<String, String>): BiosInstalled? {
        val exact = inSystem[file.key]
        val byContents = if (exact == null && file.anyName) {
            inSystem.entries.firstOrNull { (path, listed) ->
                path.substringBeforeLast('/', "") == file.folder.lowercase() && md5[listed.path]?.let { it in file.md5 } == true
            }?.value
        } else {
            null
        }
        val listed = exact ?: byContents ?: return null
        val hash = md5[listed.path]
        val fit = when {
            file.md5.isEmpty() -> BiosFit.SAME_NAME
            hash != null && hash in file.md5 -> BiosFit.CHECKED
            else -> BiosFit.OTHER_FILE
        }
        return BiosInstalled(listed.path, hash, fit, atPath = exact != null)
    }

    private fun fit(file: BiosFile, found: ListedFile, hash: String?, known: Set<String>): BiosFit? = when {
        hash != null && hash in file.md5 -> BiosFit.CHECKED
        !found.name.equals(file.name, ignoreCase = true) -> null
        file.md5.isEmpty() -> BiosFit.SAME_NAME
        // A known file under a misleading name is offered where its hash belongs.
        hash != null && hash in known -> null
        else -> BiosFit.OTHER_FILE
    }

    private fun looseEntries(
        rule: BiosLoose,
        files: List<BiosFile>,
        sources: List<ListedFile>,
        inSystem: Map<String, ListedFile>,
        md5: Map<String, String>,
    ): List<BiosEntry> {
        val ofConsole = files.filter { it.console == rule.console }
        val hashes = ofConsole.flatMap { it.md5 }.toSet()
        val tablePaths = ofConsole.map { it.key }.toSet()
        val folder = rule.folder.lowercase()
        fun takes(file: ListedFile) =
            file.size == rule.size && rule.pattern.matches(file.name) && md5[file.path]?.let { it in hashes } != true
        val found = sources.filter(::takes).groupBy { join(rule.folder, it.name).lowercase() }
        val there = inSystem.filter { (path, listed) -> path.substringBeforeLast('/', "") == folder && takes(listed) }
        return (found.keys + there.keys).filterNot { it in tablePaths }.map { key ->
            val name = found[key]?.first()?.name ?: there.getValue(key).name
            val file = BiosFile(
                rule.console,
                join(rule.folder, name),
                rule.group.need,
                rule.size,
                what = listOf(BiosWhat.OTHER_VERSION),
                cores = rule.cores,
                group = rule.group,
            )
            val installed = there[key]?.let { BiosInstalled(it.path, md5[it.path], BiosFit.SAME_NAME) }
            val candidates = found[key].orEmpty().map { BiosCandidate(it.path, it.size, md5[it.path], BiosFit.NAME_AND_SIZE) }
            BiosEntry(file, installed, candidates.withoutInstalled(installed))
        }
    }

    /** A found file that is the same as the one RetroArch's folder has is nothing to copy. */
    private fun List<BiosCandidate>.withoutInstalled(installed: BiosInstalled?): List<BiosCandidate> =
        if (installed?.md5 == null) this else filterNot { it.md5 == installed.md5 }

    private fun relative(path: String, folder: String) = path.removePrefix("$folder/")

    private fun join(folder: String, name: String) = if (folder.isEmpty()) name else "$folder/$name"

    private fun firstDir(forms: List<String>, then: String) =
        "for c in ${forms.joinToString(" ") { RootFiles.quote(it) }}; do if [ -d \"\$c\" ]; then $then; break; fi; done"
}

/** A copy into RetroArch's BIOS folder: [to] as root reaches it, the folders to make first, parent first. */
data class BiosCopy(val file: BiosFile, val from: BiosCandidate, val to: String, val dirs: List<String>, val replaces: Boolean)

/**
 * A line of a console's card: a BIOS file, a needed group none of whose files was seen, or the other files of a group
 * where one is enough, folded under one line.
 */
sealed interface BiosRow {
    data class File(val entry: BiosEntry) : BiosRow

    data class Missing(val group: BiosGroup) : BiosRow

    data class Others(val group: BiosGroup, val entries: List<BiosEntry>) : BiosRow
}

/** What is ticked, what gets copied and what a console's card shows. */
object BiosPlan {
    /** Ticked unless the user says otherwise: the first checked file found, while RetroArch's folder has none. */
    fun defaultTick(entry: BiosEntry): String? =
        if (entry.installed == null) entry.candidates.firstOrNull { it.fit == BiosFit.CHECKED }?.path else null

    /**
     * The ticked file of each entry, by the entry's key: the user's choice ([choices]: a path, or null for none) while
     * that file is still found, else the default.
     */
    fun ticks(entries: List<BiosEntry>, choices: Map<String, String?>): Map<String, String> {
        val ticked = entries.mapNotNull { entry ->
            val chosen = if (entry.key in choices) {
                val choice = choices[entry.key]
                if (choice == null || entry.candidates.any { it.path == choice }) choice else defaultTick(entry)
            } else {
                defaultTick(entry)
            }
            chosen?.let { entry.key to it }
        }.toMap()
        // Where one file of a group is enough, the defaults come down to one: none while RetroArch's folder has one or the
        // user ticked one, else the newest version found.
        val dropped = entries.filter { it.file.group?.oneEnough == true }.groupBy { it.file.group }.values.flatMap { group ->
            val defaults = group.filter { it.key !in choices && it.key in ticked }
            val covered = group.any { it.installed != null || choices[it.key] != null }
            val keep = if (covered) null else defaults.maxByOrNull { it.file.name.lowercase() }
            defaults.filter { it != keep }.map { it.key }
        }
        return ticked - dropped.toSet()
    }

    /** The copies of the ticked files into [root] (RetroArch's BIOS folder as root reaches it), made first when [makeRoot]. */
    fun copies(entries: List<BiosEntry>, ticks: Map<String, String>, root: String, makeRoot: Boolean): List<BiosCopy> =
        entries.mapNotNull { entry ->
            val from = entry.candidates.firstOrNull { it.path == ticks[entry.key] } ?: return@mapNotNull null
            val folders = entry.file.folder.split('/').filter { it.isNotEmpty() }.runningReduce { parent, child -> "$parent/$child" }
            BiosCopy(
                file = entry.file,
                from = from,
                to = "$root/${entry.file.path}",
                dirs = listOfNotNull(root.takeIf { makeRoot }) + folders.map { "$root/$it" },
                replaces = entry.installed?.atPath == true,
            )
        }

    /**
     * The lines of one console's card: every file outside a group; of a group only the files seen, or one missing line
     * when none was and the group is needed.
     */
    /**
     * [rows] with each group where one file is enough, or that is optional, cut down to the files that count, the ones in
     * RetroArch's folder and the ticked ones (else, where one is enough, its first), and the rest folded into one
     * [BiosRow.Others] after them.
     */
    fun folded(entries: List<BiosEntry>, ticks: Map<String, String>): List<BiosRow> {
        val rows = rows(entries)
        val placed = mutableSetOf<BiosGroup>()
        return rows.flatMap { row ->
            val group = (row as? BiosRow.File)?.entry?.file?.group?.takeIf { it.oneEnough || it.need == BiosNeed.OPTIONAL }
                ?: return@flatMap listOf(row)
            if (!placed.add(group)) return@flatMap emptyList()
            val files = rows.filterIsInstance<BiosRow.File>().map { it.entry }.filter { it.file.group == group }
            val counted = files.filter { it.installed != null || it.key in ticks }
            val shown = if (counted.isEmpty() && group.oneEnough) files.take(1) else counted
            val others = files - shown.toSet()
            shown.map { BiosRow.File(it) } + listOfNotNull(BiosRow.Others(group, others).takeIf { others.isNotEmpty() })
        }
    }

    fun rows(entries: List<BiosEntry>): List<BiosRow> = buildList {
        val done = mutableSetOf<BiosGroup>()
        entries.forEach { entry ->
            val group = entry.file.group
            when {
                group == null -> add(BiosRow.File(entry))
                !done.add(group) -> Unit
                else -> {
                    val seen = entries.filter { it.file.group == group && it.seen }
                    if (seen.isNotEmpty()) {
                        seen.forEach { add(BiosRow.File(it)) }
                    } else if (group.need != BiosNeed.OPTIONAL) {
                        add(BiosRow.Missing(group))
                    }
                }
            }
        }
    }
}
