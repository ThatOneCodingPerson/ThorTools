package io.github.thatonecodingperson.thortools.retroarch

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.tools.RootFiles
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import javax.inject.Inject

/** RetroArch on this device, as far as the assistant needs it. */
sealed class RetroArchState {
    data object NotInstalled : RetroArchState()

    /**
     * [configPath] is `retroarch.cfg` as root sees it; null when root can't look ([rootWorks] false) or RetroArch hasn't
     * written its settings yet (it does the first time it runs).
     */
    data class Found(val packageName: String, val label: String, val version: String?, val configPath: String?, val rootWorks: Boolean) :
        RetroArchState()
}

/**
 * Finds RetroArch and reads and writes its settings file as root. RetroArch keeps `retroarch.cfg` in its folder under
 * Android/data (else in its private folder), which apps can't open, and its own document provider shows only its private
 * folder, so root is the way in. Everything here touches PServer: never on the main thread.
 */
class RetroArchFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val executor: ShellExecutor,
    private val rootFiles: RootFiles,
) {
    fun find(): RetroArchState {
        val packageManager = context.packageManager
        val packages = runCatching { packageManager.getInstalledPackages(0) }.getOrDefault(emptyList())
            .filter { it.packageName == UNIVERSAL || it.packageName.startsWith("$UNIVERSAL.") }
            .sortedBy { PREFERRED.indexOf(it.packageName).takeIf { index -> index >= 0 } ?: PREFERRED.size }
        if (packages.isEmpty()) return RetroArchState.NotInstalled
        var rootWorks = false
        packages.forEach { info ->
            configPaths(info.packageName).forEach { path ->
                when (rootFiles.exists(path)) {
                    true -> return found(info.packageName, info.versionName, path, rootWorks = true)
                    false -> rootWorks = true
                    null -> Unit
                }
            }
        }
        val first = packages.first()
        return found(first.packageName, first.versionName, null, rootWorks)
    }

    /** True while RetroArch runs, when it would write its own settings over the file on quitting; null when root can't tell. */
    fun running(packageName: String): Boolean? {
        val pids = executor.executeAsRoot("pidof $packageName || echo $NOT_RUNNING").getOrNull() ?: return null
        return pids.trim() != NOT_RUNNING
    }

    /** Stops RetroArch the way swiping it away does; true when it is gone. */
    fun close(packageName: String): Boolean {
        executor.executeAsRoot("am force-stop $packageName")
        return running(packageName) == false
    }

    fun read(path: String): RetroArchConfig? = rootFiles.read(path)?.let(::RetroArchConfig)

    /**
     * Writes [config] over RetroArch's settings file, after keeping the file as it was before Thor Tools' first change
     * next to it ([BACKUP_SUFFIX]); the file keeps its owner and mode.
     */
    fun write(path: String, config: RetroArchConfig): Result<Unit> {
        if (!rootFiles.copyOnce(path, path + BACKUP_SUFFIX)) return Result.failure(IllegalStateException("backup"))
        return rootFiles.write(path, config.text.toByteArray(), ownerOf = path)
    }

    /** True or false when root could look, null when it couldn't. */
    fun exists(path: String): Boolean? = rootFiles.exists(path)

    private fun found(packageName: String, version: String?, path: String?, rootWorks: Boolean) = RetroArchState.Found(
        packageName = packageName,
        label = runCatching {
            val packageManager = context.packageManager
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName),
        version = version,
        configPath = path,
        rootWorks = rootWorks,
    )

    /** Where RetroArch looks for its settings, in its order: its folder under Android/data, then its private folder. */
    private fun configPaths(packageName: String) = listOf(
        "$MEDIA/Android/data/$packageName/files/$CONFIG",
        "$STORAGE/Android/data/$packageName/files/$CONFIG",
        "/data/user/0/$packageName/files/$CONFIG",
    )

    companion object {
        const val BACKUP_SUFFIX = ".thortools.bak"
        private const val UNIVERSAL = "com.retroarch"
        private val PREFERRED = listOf("com.retroarch.aarch64", UNIVERSAL, "com.retroarch.ra32")
        private const val MEDIA = "/data/media/0"
        private const val STORAGE = "/sdcard"
        private const val CONFIG = "retroarch.cfg"
        private const val NOT_RUNNING = "-"
        private const val SHARED = "/storage/emulated/0"

        /** [path] as root sees it, the way the Files app and a PC show it. */
        fun shown(path: String): String {
            val prefix = listOf(MEDIA, STORAGE).firstOrNull { path.startsWith("$it/") } ?: return path
            return SHARED + path.removePrefix(prefix)
        }
    }
}
