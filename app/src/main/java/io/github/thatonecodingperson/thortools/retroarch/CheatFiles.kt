package io.github.thatonecodingperson.thortools.retroarch

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.BuildConfig
import io.github.thatonecodingperson.thortools.tools.RootFiles
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import java.io.File
import javax.inject.Inject

/**
 * The cheats' files: the ROM folders and what RetroArch has, listed as root (the folders may be on an SD card and
 * RetroArch's lie in its media and private folders); the ROM list and the user's choices kept in the app's own files;
 * the two cheat sources; and RetroArch's cheat files, read and written as root. Everything here is blocking and most of
 * it goes through PServer: never on the main thread.
 */
class CheatFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val executor: ShellExecutor,
    private val rootFiles: RootFiles,
) {
    // Asking for the app's files folder may make it, so it waits until the first use, which is off the main thread.
    private val dir by lazy { File(context.filesDir, DIR) }
    private val http = CheatHttp("ThorTools/${BuildConfig.VERSION_NAME} (RetroArch assistant)")
    private val github by lazy { GitHubCheats(File(dir, GITHUB), http) }
    val pack by lazy { CheatPack(File(dir, PACK), http) }

    fun source(kind: CheatSourceKind): CheatSource = if (kind == CheatSourceKind.PACK) pack else github

    /** The ROM list kept from the last look; null when there is none. */
    fun keptRoms(): RomListing? = runCatching { File(dir, ROMS).takeIf { it.isFile }?.readText()?.let(RomScan::decode) }.getOrNull()

    /** Lists the ROM folders as root and keeps the list; null when root can't look. */
    fun scanRoms(folders: List<String>): RomListing? {
        val output = executor.capture(RomScan.romScript(folders.map(BiosPaths::rootForms))).getOrNull() ?: return null
        val listing = RomScan.parseRoms(output, folders, System.currentTimeMillis())
        runCatching {
            dir.mkdirs()
            File(dir, ROMS).writeText(RomScan.encode(listing))
        }
        return listing
    }

    /** What RetroArch has (cheat files, core folders, saves and states, installed cores); null when root can't look. */
    fun scanRetroArch(places: RetroArchPlaces): RetroArchSide? {
        val output = executor.capture(RomScan.retroArchScript(places)).getOrNull() ?: return null
        Log.i(TAG, RomScan.summary(output))
        return RomScan.parseRetroArch(output)
    }

    fun choices(): CheatChoices =
        runCatching { File(dir, CHOICES).takeIf { it.isFile }?.readText()?.let(CheatChoices::decode) }.getOrNull() ?: CheatChoices()

    fun saveChoices(choices: CheatChoices) {
        runCatching {
            dir.mkdirs()
            File(dir, CHOICES).writeText(choices.encode())
        }
    }

    /** RetroArch's cheat file at [path] (as root reaches it); null when it isn't there or root can't read it. */
    fun readInstalled(path: String): ChtFile? = rootFiles.read(path)?.let { ChtFile.of(it.toByteArray(Charsets.UTF_8)) }

    /**
     * Writes [cht] as [name] into [core]'s folder in RetroArch's cheats folder ([cheatsRoot], as root reaches it). A
     * missing core folder is made with the owner of the cheats folder, and a missing cheats folder with the owner of the
     * folder it is in; the file gets the owner of its folder. A file already there is copied once to its name with
     * [RetroArchFiles.BACKUP_SUFFIX] first; nothing is deleted.
     */
    fun install(cheatsRoot: String, cheatsThere: Boolean, core: String, coreThere: Boolean, name: String, cht: ChtFile): Result<Unit> {
        val folder = "$cheatsRoot/$core"
        val target = "$folder/$name"
        if (rootFiles.exists(target) == true) {
            val shown = RootFiles.storageView(target) ?: target
            if (!rootFiles.copyOnce(shown, shown + RetroArchFiles.BACKUP_SUFFIX)) return Result.failure(IllegalStateException("backup"))
        }
        val makeDirs = listOfNotNull(cheatsRoot.takeUnless { cheatsThere }, folder.takeUnless { coreThere })
        val ownerOf = when {
            !cheatsThere -> cheatsRoot.substringBeforeLast('/')
            !coreThere -> cheatsRoot
            else -> folder
        }
        return rootFiles.write(target, cht.bytes, ownerOf = ownerOf, makeDirs = makeDirs)
    }

    private companion object {
        const val TAG = "CheatFiles"
        const val DIR = "retroarch-cheats"
        const val GITHUB = "github"
        const val PACK = "cheats.zip"
        const val ROMS = "roms.txt"
        const val CHOICES = "choices.txt"
    }
}
