package io.github.thatonecodingperson.thortools.roms

/** One file or folder inside an archive, as `7zz l -slt` lists it: [crc] is the CRC32 in hex (null for a folder). */
data class ArchiveEntry(val path: String, val size: Long, val crc: String?, val folder: Boolean = false) {
    val name: String get() = path.substringAfterLast('/')

    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

/** What an archive holds. */
data class ArchiveListing(val entries: List<ArchiveEntry>) {
    val files: List<ArchiveEntry> get() = entries.filter { !it.folder }

    /** The size of everything inside. */
    val size: Long get() = files.sumOf { it.size }
}

/** Reads 7-Zip's technical listings and tells whether an archive is one cartridge game. */
object ArchiveListings {
    /** Files that may come along with a game's ROM in its archive: notes, links, a scan of the box. */
    private val EXTRAS = setOf("txt", "url", "nfo", "diz", "htm", "html", "jpg", "jpeg", "png", "pdf", "xml", "md5", "sfv")

    /** The entries of one archive in `7zz l -slt -ba` (blocks of `Key = value` lines); null when it lists nothing. */
    fun parse(text: String): ArchiveListing? {
        val entries = mutableListOf<ArchiveEntry>()
        var fields = mutableMapOf<String, String>()
        fun end() {
            val path = fields["Path"]
            if (path != null) {
                val folder = fields["Folder"] == "+" || fields["Attributes"]?.startsWith("D") == true
                val crc = fields["CRC"]?.takeIf { it.isNotEmpty() }?.uppercase()
                entries += ArchiveEntry(path, fields["Size"]?.toLongOrNull() ?: 0L, crc, folder)
            }
            fields = mutableMapOf()
        }
        text.lineSequence().forEach { raw ->
            val line = raw.trimEnd('\r')
            val at = line.indexOf(" = ")
            when {
                line.startsWith("Path = ") -> {
                    end()
                    fields["Path"] = line.removePrefix("Path = ")
                }
                at > 0 -> fields[line.substring(0, at)] = line.substring(at + 3)
                line.endsWith(" =") -> fields[line.removeSuffix(" =")] = ""
            }
        }
        end()
        return entries.takeIf { it.isNotEmpty() }?.let(::ArchiveListing)
    }

    /**
     * The ROM of the game [listing] holds, when it holds exactly one file of [romTypes] and nothing else but small
     * extras; null otherwise (a collection, an HD pack, an empty archive).
     */
    fun rom(listing: ArchiveListing, romTypes: Collection<String>): ArchiveEntry? {
        val files = listing.files
        val roms = files.filter { it.extension in romTypes }
        if (roms.size != 1) return null
        val others = files - roms.toSet()
        return roms.single().takeIf { others.all { it.extension in EXTRAS } && listing.entries.none { it.folder } }
    }

    /** What an archive's files are, to compare two archives: one `size crc path` line per file, sorted. */
    fun signature(listing: ArchiveListing): String =
        listing.files.map { "${it.size} ${it.crc.orEmpty()} ${it.path}" }.sorted().joinToString("\n")
}
