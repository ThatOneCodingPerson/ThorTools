package io.github.thatonecodingperson.thortools.retroarch

/**
 * Paths for the BIOS finder: a folder picked in Android's folder picker as a path, the ways root may reach a path, and
 * RetroArch's own system folder. Root sees the shared storage at `/data/media/0` and an SD card at `/mnt/media_rw/<id>`
 * too, besides the `/storage/...` paths the Files app shows.
 */
object BiosPaths {
    const val SHARED = "/storage/emulated/0"
    private const val MEDIA = "/data/media/0"
    private const val SDCARD = "/sdcard"
    private const val STORAGE = "/storage"
    private const val MEDIA_RW = "/mnt/media_rw"
    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
    private const val DOWNLOADS = "com.android.providers.downloads.documents"
    private const val PRIMARY = "primary"
    private const val HOME = "home"
    private const val RAW = "raw:"
    private const val SYSTEM = "system"
    private val VOLUME = Regex("[A-Za-z0-9-]+")
    private val ON_VOLUME = Regex("^$STORAGE/([^/]+)(/.*)?$")
    private val NOT_VOLUMES = setOf("emulated", "self")

    /**
     * The folder a tree from the folder picker stands for, the way the Files app shows it; [documentId] as
     * `DocumentsContract.getTreeDocumentId` gives it (`primary:Download/bios`, `EBBF-779E:bios`). Null for a folder of
     * another app's provider, which has no path.
     */
    fun fromTree(authority: String?, documentId: String): String? {
        if (authority == DOWNLOADS) return documentId.takeIf { it.startsWith("$RAW/") }?.removePrefix(RAW)?.trimEnd('/')
        if (authority != EXTERNAL_STORAGE) return null
        val volume = documentId.substringBefore(':')
        val inside = documentId.substringAfter(':', "").trim('/')
        val base = when {
            volume == PRIMARY -> SHARED
            volume == HOME -> "$SHARED/Documents"
            VOLUME.matches(volume) -> "$STORAGE/$volume"
            else -> return null
        }
        return if (inside.isEmpty()) base else "$base/$inside"
    }

    /** The paths under which root may reach [path], the likeliest first. */
    fun rootForms(path: String): List<String> {
        val clean = path.trimEnd('/')
        listOf(SHARED, SDCARD, MEDIA).firstOrNull { clean == it || clean.startsWith("$it/") }?.let { prefix ->
            val inside = clean.removePrefix(prefix)
            return listOf(MEDIA + inside, SHARED + inside)
        }
        val volume = ON_VOLUME.matchEntire(clean)?.takeIf { it.groupValues[1] !in NOT_VOLUMES }
        return if (volume != null) listOf(clean, "$MEDIA_RW/${volume.groupValues[1]}${volume.groupValues[2]}") else listOf(clean)
    }

    /** [path] as root sees it, the way the Files app shows it. */
    fun shown(path: String): String {
        val clean = path.trimEnd('/')
        listOf(MEDIA, SDCARD).firstOrNull { clean == it || clean.startsWith("$it/") }?.let { return SHARED + clean.removePrefix(it) }
        if (clean.startsWith("$MEDIA_RW/")) return STORAGE + clean.removePrefix(MEDIA_RW)
        return clean
    }

    /** True when [path] is [folder] or inside it, however root reached either. */
    fun isUnder(path: String, folder: String): Boolean {
        val inner = shown(path)
        val outer = shown(folder)
        return inner == outer || inner.startsWith("$outer/")
    }

    /** [folders] without repeats and without folders inside another one of them (their files are found anyway). */
    fun withoutNested(folders: List<String>): List<String> {
        val distinct = folders.map { it.trimEnd('/') }.distinct()
        return distinct.filterNot { folder -> distinct.any { it != folder && isUnder(folder, it) } }
    }

    /** RetroArch's BIOS folder as its settings name it; null when it isn't set or isn't a full path. */
    fun configured(value: String?): String? = value?.trim()?.takeIf { it.startsWith("/") }?.trimEnd('/')?.ifEmpty { null }

    /**
     * Where RetroArch's own system folder may be: next to its cheats folder (`cheat_database_path`), which lies in its
     * media folder, else in its media folder by package name.
     */
    fun ownSystemFolders(cheatPath: String?, packageName: String): List<String> {
        val besideCheats = configured(cheatPath)?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() }?.let { "$it/$SYSTEM" }
        return listOfNotNull(besideCheats, "$SHARED/Android/media/$packageName/RetroArch/$SYSTEM").distinct()
    }
}
