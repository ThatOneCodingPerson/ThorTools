package io.github.thatonecodingperson.thortools.wii

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.tools.RootFiles
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import javax.inject.Inject

/** Dolphin on this device, as far as the builder needs it. */
sealed class DolphinState {
    data object NotInstalled : DolphinState()

    /** Installed, but its folder isn't there yet: Dolphin makes it the first time it runs. */
    data class NeverOpened(val packageName: String, val label: String) : DolphinState()

    /**
     * [userDir] as root sees it, null when root can't reach it; [shownDir] as a file manager or a PC shows it;
     * [authority] Dolphin's document provider, null when this Dolphin has none.
     */
    data class Found(val packageName: String, val label: String, val userDir: String?, val shownDir: String, val authority: String?) :
        DolphinState()
}

/**
 * Finds Dolphin, reads its Wii Remote setup and puts profiles into its profile folder as root. Another app's folder
 * under Android/data is closed to apps, so the writes go through PServer, and the file is then given Dolphin's owner
 * and mode so Dolphin can read and replace it. Everything here touches the disk or PServer: never on the main thread.
 */
class DolphinFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val executor: ShellExecutor,
    private val rootFiles: RootFiles,
    private val access: DolphinAccess,
) {
    fun find(): DolphinState {
        val packageManager = context.packageManager
        val packages = runCatching { packageManager.getInstalledPackages(0) }.getOrDefault(emptyList())
            .map { it.packageName }
            .filter { it.startsWith(PACKAGE_PREFIX) }
            .sortedBy { if (it == OFFICIAL) 0 else 1 }
        if (packages.isEmpty()) return DolphinState.NotInstalled
        var rootLooked = false
        packages.forEach { name ->
            folders(name).forEach { (root, shown) ->
                when (hasConfig(root)) {
                    true -> return DolphinState.Found(name, label(name), root, shown, authority(name))
                    false -> rootLooked = true
                    null -> Unit
                }
            }
        }
        // Without root, the Dolphin the user gave folder access to comes first.
        val name = packages.firstOrNull { pkg -> authority(pkg)?.let(access::granted) != null } ?: packages.first()
        val authority = authority(name)
        if (rootLooked && authority?.let(access::granted) == null) return DolphinState.NeverOpened(name, label(name))
        return DolphinState.Found(name, label(name), null, shownDir(name), authority)
    }

    /** Dolphin's own Wii Remote setup, for the device line it uses. */
    fun wiimoteIni(userDir: String): String? = executor.capture("cat '$userDir/$CONFIG/WiimoteNew.ini'").getOrNull()

    /** The Wii Remote profiles already in Dolphin, without `.ini`; null when Dolphin has no profile folder yet. */
    fun profiles(userDir: String): Set<String>? {
        val listing = executor.capture("[ -d '$userDir/$PROFILES' ] && ls '$userDir/$PROFILES' || echo $MISSING")
            .getOrNull() ?: return null
        if (listing.trim() == MISSING) return null
        return listing.lines().map { it.trim() }.filter { it.endsWith(INI) }.map { it.removeSuffix(INI) }.toSet()
    }

    /**
     * Profiles an earlier version saved one folder off (`Config/Profile`, where Dolphin doesn't look) move to Dolphin's
     * profile folder; one that would replace a profile there stays where it is.
     */
    fun moveMisplaced(userDir: String) {
        val config = "$userDir/$CONFIG"
        val old = "$config/$MISPLACED"
        val target = "$userDir/$PROFILES"
        val script = listOf(
            "[ -d '$old' ] || exit 0",
            "owner=\$(stat -c %u:%g '$config') || exit 1",
            "mkdir -p '$target' && chown \"\$owner\" '$config/Profiles' '$target' && chmod 2770 '$config/Profiles' '$target'",
            "for f in '$old'/*$INI; do [ -e \"\$f\" ] || continue; n=\$(basename \"\$f\"); " +
                "[ -e '$target'/\"\$n\" ] || mv \"\$f\" '$target'/\"\$n\"; done",
            "rmdir '$old' 2>/dev/null && rmdir '$config/Profile' 2>/dev/null",
            "echo moved",
        ).joinToString("\n")
        executor.script(script)
    }

    /**
     * Opens Dolphin's top folder in Android's Files app, through the document provider Dolphin offers for its own
     * files; null when no Files app can. The Files app can't open a folder deeper inside it (Dolphin's provider can't
     * tell it the way down), so the user goes on from the top: Config, Profiles, Wiimote.
     */
    fun folderIntent(authority: String): Intent? {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(topFolder(authority), DocumentsContract.Root.MIME_TYPE_ITEM)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Only Android's own Files app may read another app's document provider, so it is asked for by name.
        val files = FILES_APPS.firstOrNull { name ->
            runCatching { packageManager.resolveActivity(Intent(intent).setPackage(name), 0) }.getOrNull() != null
        } ?: return null
        return intent.setPackage(files)
    }

    /** Where Android's folder picker starts: Dolphin's top folder, so the user only confirms it. */
    fun topFolder(authority: String): Uri = DocumentsContract.buildRootUri(authority, DOCUMENT_ROOT)

    /** The profile folder's full path, for a file manager or a PC. */
    fun profilePath(shownDir: String): String {
        val storage = runCatching {
            context.getSystemService(StorageManager::class.java).primaryStorageVolume.directory?.absolutePath
        }.getOrNull() ?: STORAGE
        return "$storage/$shownDir/$PROFILES"
    }

    fun save(userDir: String, fileName: String, ini: String): DolphinSave {
        val config = "$userDir/$CONFIG"
        val wiimoteDir = "$userDir/$PROFILES"
        val target = "$wiimoteDir/$fileName$INI"
        return rootFiles.write(target, ini.toByteArray(), ownerOf = config, makeDirs = listOf("$config/Profiles", wiimoteDir))
            .fold(
                onSuccess = { DolphinSave.Saved(SaveRoute.ROOT, listed = false) },
                onFailure = { DolphinSave.Failed(SaveRoute.ROOT, it.message ?: "PServer") },
            )
    }

    /** A copy in Download/Thor Tools, replacing an older copy of the same name. True when it was written. */
    fun saveCopy(fileName: String, ini: String): Boolean = runCatching {
        val resolver = context.contentResolver
        val name = "$fileName$INI"
        val folder = "${Environment.DIRECTORY_DOWNLOADS}/$COPY_FOLDER/"
        resolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(name, folder),
        )
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, folder)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        checkNotNull(resolver.openOutputStream(uri)).use { it.write(ini.toByteArray()) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        true
    }.getOrDefault(false)

    /** True or false when root could look, null when it couldn't. */
    private fun hasConfig(dir: String): Boolean? =
        when (executor.executeAsRoot("[ -d '$dir/$CONFIG' ] && echo yes || echo no").getOrNull()) {
            "yes" -> true
            "no" -> false
            else -> null
        }

    private fun label(packageName: String): String = runCatching {
        val packageManager = context.packageManager
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /** Dolphin's document provider, looked up the way the Files app finds providers. */
    private fun authority(packageName: String): String? {
        val packageManager = context.packageManager
        val listed = runCatching {
            packageManager.queryIntentContentProviders(Intent(DocumentsContract.PROVIDER_INTERFACE), 0)
                .mapNotNull { it.providerInfo }
                .firstOrNull { it.packageName == packageName }
                ?.authority
                ?.substringBefore(';')
        }.getOrNull()
        return listed ?: runCatching { packageManager.resolveContentProvider("$packageName$DOCUMENTS_SUFFIX", 0)?.authority }.getOrNull()
    }

    /** Where a Dolphin build keeps its user folder: root's path without the storage layer, and the path people see. */
    private fun folders(packageName: String) = listOf(MEDIA, STORAGE).flatMap { base ->
        listOf("$base/${shownDir(packageName)}" to shownDir(packageName), "$base/$LEGACY_FOLDER" to LEGACY_FOLDER)
    }

    private fun shownDir(packageName: String) = "Android/data/$packageName/files"

    companion object {
        const val OFFICIAL = "org.dolphinemu.dolphinemu"
        const val COPY_FOLDER = "Thor Tools"
        private const val PACKAGE_PREFIX = "org.dolphinemu"
        private const val MEDIA = "/data/media/0"
        private const val STORAGE = "/sdcard"
        private const val LEGACY_FOLDER = "dolphin-emu"
        private const val CONFIG = "Config"
        private const val PROFILES = "Config/Profiles/Wiimote"
        private const val MISPLACED = "Profile/Wiimote"
        private const val MISSING = "-"
        private const val DOCUMENTS_SUFFIX = ".user"
        private const val DOCUMENT_ROOT = "root"
        private val FILES_APPS = listOf("com.android.documentsui", "com.google.android.documentsui")
        private const val INI = ".ini"

        /** Where a profile goes inside Dolphin's folder, for the guide. */
        const val PROFILE_FOLDER = PROFILES
    }
}
