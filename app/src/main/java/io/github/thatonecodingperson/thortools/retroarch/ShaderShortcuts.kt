package io.github.thatonecodingperson.thortools.retroarch

/**
 * A shortcut in RetroArch's shader folder: its [path] there (folders and file name with its extension) and the preset it
 * points at ([target], a path in the same shader folder).
 */
data class Shortcut(val path: String, val target: String) {
    val folder: String get() = path.substringBeforeLast('/', "")
}

/** A preset found in the sorted folders: its [path] in the shader folder, what it points at ([target]), and whether Thor Tools made it. */
data class FoundPreset(val path: String, val target: String?, val ours: Boolean) {
    val folder: String get() = path.substringBeforeLast('/', "")
    val name: String get() = path.substringAfterLast('/').substringBeforeLast('.')
}

/**
 * How RetroArch's Shaders menu is sorted: an "Any console" folder with a subfolder per kind, a folder per console, and
 * "From links"; each holding shortcuts, small presets that point at the real files in the library (`shaders_slang`,
 * `.links`) with a path relative to themselves and a marker line, so only Thor Tools' own files are ever changed.
 */
object ShaderShortcuts {
    const val ANY = "Any console"
    const val LINKS = "From links"
    const val LINKS_LIBRARY = ".links"
    const val MARKER = "# Thor Tools shortcut"
    private val BAD = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    /** Consoles the catalogue has shaders for whose cores' info files name them under another database than their cheats'. */
    private val DATABASES = mapOf(CheatSystem.N3DS to "Nintendo - Nintendo 3DS")

    /** [name] as a file or folder name: no characters a file system or RetroArch refuses, no trailing dot. */
    fun safe(name: String): String = name.map { if (it in BAD) '-' else it }.joinToString("").trim().trimEnd('.').ifEmpty { "-" }

    fun consoleFolder(system: CheatSystem): String = safe(system.label.replace(" / ", " - "))

    fun kindFolder(kind: ShaderKind): String = "$ANY/${kind.folder}"

    /** Every folder a shortcut may be moved to: the kinds of "Any console", then the console folders. */
    fun folders(consoles: List<CheatSystem>): List<String> = ShaderKind.entries.map(::kindFolder) + consoles.map(::consoleFolder)

    /** The text of a shortcut at [path] that points at [target], both paths in the shader folder. */
    fun text(path: String, target: String): String = "#reference \"${relative(path, target)}\"\n$MARKER\n"

    /** [to] seen from the folder of [from], both paths in the same folder. */
    fun relative(from: String, to: String): String = "../".repeat(from.count { it == '/' }) + to

    /** The consoles whose folders are made: the ones the installed [cores] play, or with [all] every console the catalogue has. */
    fun consoles(cores: List<CoreInfo>, all: Boolean): List<CheatSystem> {
        if (all) return ShaderCatalog.systems
        return ShaderCatalog.systems.filter { system ->
            val database = system.db ?: DATABASES[system]
            database != null && cores.any { database in it.databases }
        }
    }

    /** The shortcuts the catalogue gives in [format] for [consoles]: "Any console" always, a console's for each of [consoles]. */
    fun planned(format: ShaderFormat, consoles: List<CheatSystem>): List<Shortcut> = ShaderCatalog.entries.flatMap { entry ->
        val path = entry.path(format) ?: return@flatMap emptyList()
        val folders = entry.kind?.let { listOf(kindFolder(it)) } ?: entry.systems.filter { it in consoles }.map(::consoleFolder)
        folders.map { Shortcut("$it/${safe(entry.name)}.${format.preset}", "${format.pack}/$path") }
    }

    /**
     * What makes the sorted folders hold [planned]: the shortcuts missing (unless the user moved or removed them,
     * [dismissed]) and the catalogue's own shortcuts left in the folder of a console no longer chosen (never one from a
     * link or one the user moved there).
     */
    fun changes(planned: List<Shortcut>, found: List<FoundPreset>, dismissed: Set<String>, format: ShaderFormat): Changes {
        val there = found.map { it.path.lowercase() }.toSet()
        val gone = dismissed.map { it.lowercase() }.toSet()
        val write = planned.filter { it.path.lowercase() !in there && it.path.lowercase() !in gone }
        val catalogue = planned(format, ShaderCatalog.systems).associate { it.path.lowercase() to it.target.lowercase() }
        val consoles = ShaderCatalog.systems.map { consoleFolder(it).lowercase() }.toSet()
        val wanted = planned.map { it.folder.lowercase() }.toSet()
        val remove = found.filter { preset ->
            preset.ours &&
                catalogue[preset.path.lowercase()] == preset.target?.lowercase() &&
                preset.folder.lowercase() in consoles &&
                preset.folder.lowercase() !in wanted
        }.map { it.path }
        return Changes(write, remove)
    }

    data class Changes(val write: List<Shortcut>, val remove: List<String>) {
        val none: Boolean get() = write.isEmpty() && remove.isEmpty()
    }

    /** [link] is libretro's own collection in [format], the one the library is a copy of. */
    fun isLibrary(link: ShaderLink, format: ShaderFormat): Boolean =
        link is ShaderLink.Repo && link.host == GitHost.GITHUB && "${link.owner}/${link.repo}".equals(format.repo, ignoreCase = true)

    /**
     * Where the shortcut of [link]'s preset at [path] points: the library's copy when the link is libretro's collection
     * and the library has the preset ([inLibrary]: its presets' paths in lower case), else the link's own folder.
     */
    fun linkTarget(link: ShaderLink, path: String, format: ShaderFormat, inLibrary: Set<String>): String =
        if (isLibrary(link, format) && path.lowercase() in inLibrary) "${format.pack}/$path" else "$LINKS_LIBRARY/${link.key}/$path"

    /** The folder in `.links` that [target] is in; null for one outside it. */
    fun linkKey(target: String?): String? =
        target?.takeIf { it.startsWith("$LINKS_LIBRARY/") }?.removePrefix("$LINKS_LIBRARY/")?.substringBefore('/', "")?.ifEmpty { null }

    /**
     * The shortcuts for [presets] from a link: one in each folder a preset goes to, pointing at its target, named in
     * plain words, with a number added while another file there ([existing], or one made just before) has the name; one
     * pointing at the same file is written over.
     */
    fun forLink(presets: List<LinkPreset>, existing: List<FoundPreset>): List<Shortcut> {
        val taken = existing.associate { it.path.lowercase() to it.target }.toMutableMap()
        return presets.flatMap { preset ->
            val target = preset.target
            val extension = preset.path.substringAfterLast('.')
            preset.folders.map { folder ->
                val path = generateSequence(1) { it + 1 }.map { number ->
                    "$folder/${safe(preset.name)}${if (number == 1) "" else " ($number)"}.$extension"
                }.first { candidate -> candidate.lowercase() !in taken || taken[candidate.lowercase()] == target }
                taken[path.lowercase()] = target
                Shortcut(path, target)
            }
        }
    }

    /** [preset] moved to [folder]: the same file name there, pointing at the same file. */
    fun moved(preset: FoundPreset, folder: String): Shortcut? =
        preset.target?.let { Shortcut("$folder/${preset.path.substringAfterLast('/')}", it) }

    /**
     * The presets found, by folder, in the order RetroArch's menu is meant to be read: "Any console"'s kinds, the
     * consoles, "From links", then anything else.
     */
    fun byFolder(found: List<FoundPreset>): List<Pair<String, List<FoundPreset>>> {
        fun rank(folder: String) = when {
            folder.startsWith("$ANY/") || folder == ANY -> 0
            folder.startsWith(LINKS) -> 2
            ShaderCatalog.systems.any { consoleFolder(it).equals(folder, ignoreCase = true) } -> 1
            else -> 3
        }
        // A preset at the top of the shader folder is in no folder: RetroArch keeps the shader in use there (retroarch.slangp).
        return found.filter { it.folder.isNotEmpty() }.groupBy { it.folder }.toList()
            .sortedWith(compareBy({ rank(it.first) }, { it.first.lowercase() }))
            .map { (folder, presets) -> folder to presets.sortedBy { it.name.lowercase() } }
    }

    /**
     * The presets in a scan's output: `S <path>` for each (as root found it, inside [root]), then `R <#reference line>`
     * when it has one and `M` when it carries Thor Tools' marker. Targets are worked out to paths in the shader folder.
     */
    fun parse(output: String, root: String): List<FoundPreset> {
        val found = mutableListOf<FoundPreset>()
        output.lineSequence().forEach { line ->
            when {
                line.startsWith("S $root/") -> found += FoundPreset(line.removePrefix("S $root/"), null, false)
                line.startsWith("R ") && found.isNotEmpty() -> {
                    val last = found.last()
                    val reference = REFERENCE.find(line.removePrefix("R "))?.groupValues?.get(1)
                    val target = reference?.let { ShaderDeps.resolve(last.folder, it) }
                    found[found.lastIndex] = last.copy(target = target)
                }
                line == "M" && found.isNotEmpty() -> found[found.lastIndex] = found.last().copy(ours = true)
            }
        }
        return found
    }

    private val REFERENCE = Regex("""^\s*#reference\s+"([^"]+)"""")
}
