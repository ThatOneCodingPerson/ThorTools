package io.github.thatonecodingperson.thortools.retroarch

import io.github.thatonecodingperson.thortools.tools.RootFiles
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import javax.inject.Inject

/**
 * RetroArch's BIOS folder: [configured] as its settings name it (null when they don't) and [configuredRoot], where root
 * reaches it (null when it isn't there); [own], RetroArch's own system folder in its media folder, and [ownParentRoot],
 * where root reaches the folder that holds it (null when there is none).
 */
data class BiosTarget(val configured: String?, val configuredRoot: String?, val own: String?, val ownParentRoot: String?) {
    val there: Boolean get() = configuredRoot != null

    /** The folder the settings name isn't there, and RetroArch's own one can be used instead. */
    val offerOwn: Boolean get() = configuredRoot == null && own != null && ownParentRoot != null && !namesOwn

    /** Where the copies go, as root reaches it: the folder the settings name, once it is there or can be made. */
    val copyRoot: String? get() = configuredRoot ?: ownRoot?.takeIf { namesOwn }

    /** [copyRoot] has to be made first. */
    val makeRoot: Boolean get() = configuredRoot == null

    /** Whose owner the copies get: RetroArch's folder, or the folder it is made in. */
    val ownerOf: String? get() = configuredRoot ?: ownParentRoot?.takeIf { namesOwn }

    private val namesOwn: Boolean get() = configured != null && own != null && BiosPaths.shown(configured) == BiosPaths.shown(own)

    private val ownRoot: String? get() = if (own != null && ownParentRoot != null) "$ownParentRoot/${own.substringAfterLast('/')}" else null
}

/** A look through the BIOS folders: the folders it looked in, in order, what it listed and what it recognised. */
data class BiosScanResult(val folders: List<String>, val listing: BiosListing, val entries: List<BiosEntry>) {
    /** How many files [folder] has; null when it isn't there (an SD card that is out). */
    fun files(folder: String): Int? {
        val index = folders.indexOf(folder)
        if (index < 0 || listing.folders.getOrNull(index) == null) return null
        return listing.counts[index]
    }
}

/**
 * Finds BIOS files in the user's folders and copies them into RetroArch's BIOS folder, as root: the user's folders may
 * be on an SD card and RetroArch's lies in its media folder, which apps can't write. Everything here goes through
 * PServer: never on the main thread.
 */
class BiosFiles @Inject constructor(private val executor: ShellExecutor, private val rootFiles: RootFiles) {
    fun target(packageName: String, config: RetroArchConfig): BiosTarget {
        val configured = BiosPaths.configured(config.value(RaUiModel.SYSTEM_DIR))
        val own = BiosPaths.ownSystemFolders(config.value(CHEATS), packageName).firstNotNullOfOrNull { folder ->
            firstDir(BiosPaths.rootForms(folder.substringBeforeLast('/')))?.let { folder to it }
        }
        return BiosTarget(configured, configured?.let { firstDir(BiosPaths.rootForms(it)) }, own?.first, own?.second)
    }

    /**
     * Lists and recognises the BIOS files in [folders] and in RetroArch's folder ([system], as root reaches it); null
     * when root can't look.
     */
    fun scan(folders: List<String>, system: String?): BiosScanResult? {
        val listed = executor.capture(BiosScan.listScript(folders.map(BiosPaths::rootForms), system)).getOrNull() ?: return null
        val listing = BiosScan.parseListing(listed, folders.size)
        val toHash = BiosScan.toHash(listing)
        val md5 = if (toHash.isEmpty()) {
            emptyMap()
        } else {
            executor.capture(BiosScan.md5Script(toHash)).getOrNull()?.let(BiosScan::parseMd5) ?: return null
        }
        return BiosScanResult(folders, listing, BiosScan.match(listing, md5))
    }

    /** Copies one file; a file it replaces is kept next to it with [RetroArchFiles.BACKUP_SUFFIX]. */
    fun copy(copy: BiosCopy, ownerOf: String): Result<Unit> =
        rootFiles.copy(copy.from.path, copy.to, ownerOf, copy.dirs, keepAs = copy.to + RetroArchFiles.BACKUP_SUFFIX)

    private fun firstDir(forms: List<String>): String? =
        executor.script(BiosScan.dirScript(forms)).getOrNull()?.trim()?.takeIf { it in forms }

    private companion object {
        const val CHEATS = "cheat_database_path"
    }
}
