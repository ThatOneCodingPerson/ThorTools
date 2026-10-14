package io.github.thatonecodingperson.thortools.retroarch

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.BuildConfig
import io.github.thatonecodingperson.thortools.tools.RootFiles
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Where RetroArch's shaders are: its shader folder on storage, where Thor Tools sorts them ([root], as the Files app
 * shows it), the shader folder its settings name ([own]), its private shader folder ([private], where its collection is
 * by default) and its menu config folder (the paths root may reach it under), where old automatic presets lie.
 */
data class ShaderPlaces(val root: String, val own: String, val private: String, val config: List<String>) {
    /** RetroArch's Shaders menu opens on the sorted folders. */
    val menuOnStorage: Boolean get() = BiosPaths.shown(own) == BiosPaths.shown(root)

    companion object {
        private const val SHADER_DIR = "video_shader_dir"
        private const val CONFIG = "rgui_config_directory"

        fun of(config: RetroArchConfig, packageName: String): ShaderPlaces {
            val media = BiosPaths.configured(config.value(RetroArchPlaces.CHEATS))?.substringBeforeLast('/')?.ifEmpty { null }
                ?: "${BiosPaths.SHARED}/Android/media/$packageName/RetroArch"
            val root = "${BiosPaths.shown(media)}/shaders"
            val private = "/data/user/0/$packageName/shaders"
            val own = BiosPaths.configured(config.value(SHADER_DIR)) ?: private
            return ShaderPlaces(
                root = root,
                own = own,
                private = if (BiosPaths.shown(own) == root) private else own,
                config = BiosPaths.rootForms(BiosPaths.configured(config.value(CONFIG)) ?: "$media/config"),
            )
        }
    }
}

/**
 * A look at RetroArch's shaders in one language: how many files of the collection are in the shader folder on storage
 * ([library]) and in RetroArch's private one ([privateFiles], 0 when it has none), the presets in the sorted folders, and
 * the automatic presets an older Thor Tools wrote ([oldLooks], as root reached them).
 */
data class ShaderScan(val library: Int, val privateFiles: Int, val presets: List<FoundPreset>, val oldLooks: List<String>) {
    /** The collection is on storage, all of it when RetroArch has its own to compare with. */
    val libraryReady: Boolean get() = library > 0 && library >= privateFiles

    val shortcuts: Int get() = presets.count { it.ours }
}

/**
 * RetroArch's shaders as root: looking at them, copying RetroArch's own collection to its shader folder on storage (or
 * unpacking libretro's pack there when it has none), and writing, moving and removing Thor Tools' shortcuts. Files go
 * through Android's view of shared storage, where they get RetroArch's owner by themselves. Everything here is blocking
 * and goes through PServer or the network: never on the main thread.
 */
class ShaderLibrary @Inject constructor(@ApplicationContext private val context: Context, private val executor: ShellExecutor) {
    private val dir by lazy { File(context.filesDir, DIR) }
    private val http = CheatHttp("ThorTools/${BuildConfig.VERSION_NAME} (RetroArch assistant)")

    private fun zip(format: ShaderFormat) = File(dir, "${format.pack}.zip")

    /** libretro's downloaded pack, when it is here. */
    fun pack(format: ShaderFormat): PackInfo? = zip(format).takeIf { it.isFile }?.let { PackInfo(it.length(), it.lastModified()) }

    fun deletePack(format: ShaderFormat) {
        zip(format).delete()
    }

    fun scan(places: ShaderPlaces, format: ShaderFormat): ShaderScan? {
        val root = RootFiles.quote(places.root)
        val pack = format.pack
        val library = RootFiles.quote(RootFiles.underneath("${places.root}/$pack") ?: "${places.root}/$pack")
        val private = RootFiles.quote("${places.private}/$pack")
        val skip = "grep -v -e \"^${places.root}/$pack/\" -e \"^${places.root}/${ShaderShortcuts.LINKS_LIBRARY}/\""
        val each = "echo \"S \$f\"; grep -m 1 '^#reference' \"\$f\" | sed 's/^/R /'; grep -q '^${ShaderShortcuts.MARKER}' \"\$f\" && echo M"
        val script = listOf(
            "[ -d $library ] && echo \"L \$(find $library -type f | wc -l)\"",
            "[ -d $private ] && echo \"Q \$(find $private -type f | wc -l)\"",
            "[ -d $root ] && find $root -maxdepth 3 -type f -name '*.${format.preset}' | $skip | while IFS= read -r f; do $each; done",
            "for c in ${places.config.joinToString(" ") { RootFiles.quote(it) }}; do if [ -d \"\$c\" ]; then " +
                "find \"\$c\" -maxdepth 2 -type f \\( -name '*.slangp' -o -name '*.glslp' \\) | while IFS= read -r f; do " +
                "grep -q '^$OLD_MARKER' \"\$f\" && echo \"O \$f\"; done; break; fi; done",
            "true",
        ).joinToString("\n")
        val output = executor.capture(script).getOrNull() ?: return null
        val count = { tag: String ->
            output.lineSequence().firstOrNull { it.startsWith("$tag ") }?.substring(2)?.trim()?.toIntOrNull() ?: 0
        }
        return ShaderScan(
            library = count("L"),
            privateFiles = count("Q"),
            presets = ShaderShortcuts.parse(output, places.root),
            oldLooks = output.lines().filter { it.startsWith("O ") }.map { it.substring(2) },
        )
    }

    /** The presets of the collection on storage, as paths inside it in lower case (storage ignores case). */
    fun libraryPresets(places: ShaderPlaces, format: ShaderFormat): Set<String> {
        val folder = "${places.root}/${format.pack}"
        val pack = RootFiles.quote(RootFiles.underneath(folder) ?: folder)
        val output = executor.capture("cd $pack 2>/dev/null && find . -type f -name '*.${format.preset}'").getOrNull() ?: return emptySet()
        return output.lineSequence().map { it.trim().removePrefix("./").lowercase() }.filter { it.isNotEmpty() }.toSet()
    }

    /** Which of [paths] (in the shader folder) aren't there; all of them when root can't look. */
    fun missing(places: ShaderPlaces, paths: Collection<String>): Set<String> {
        if (paths.isEmpty()) return emptySet()
        val checks = paths.joinToString("\n") { path ->
            "[ -e ${RootFiles.quote("${places.root}/$path")} ] || echo ${RootFiles.quote("X $path")}"
        }
        val output = executor.capture("$checks\ntrue").getOrNull() ?: return paths.toSet()
        return output.lines().filter { it.startsWith("X ") }.map { it.substring(2) }.toSet()
    }

    /** Removes the files of a link ([key], its folder in `.links`) from the shader folder. */
    fun removeLinkFiles(places: ShaderPlaces, key: String): Result<Unit> = runCatching {
        require(key.isNotBlank() && '/' !in key && key != "." && key != "..") { "bad link folder: $key" }
        val links = RootFiles.quote("${places.root}/${ShaderShortcuts.LINKS_LIBRARY}")
        val folder = RootFiles.quote("${places.root}/${ShaderShortcuts.LINKS_LIBRARY}/$key")
        check(executor.script("rm -rf $folder && { rmdir $links 2>/dev/null; echo done; }").getOrNull()?.trim() == "done") { "PServer" }
    }

    /**
     * Copies RetroArch's own collection from its private shader folder to its shader folder on storage, a top folder at a
     * time so root's turns stay short ([step] hears each), each checked by its files' names and sizes. Gives how many
     * parts were copied.
     */
    fun copyFromRetroArch(places: ShaderPlaces, format: ShaderFormat, step: (Int, Int) -> Unit): Result<Int> = runCatching {
        val source = "${places.private}/${format.pack}"
        val names = executor.capture("ls -A ${RootFiles.quote(source)}").getOrThrow().lines().map { it.trim() }.filter { it.isNotEmpty() }
        check(names.isNotEmpty()) { "RetroArch's own shaders aren't there" }
        val dest = "${places.root}/${format.pack}"
        names.forEachIndexed { index, name ->
            step(index + 1, names.size)
            val from = RootFiles.quote("$source/$name")
            val list = { folder: String ->
                "\$(cd ${RootFiles.quote(folder)} && find ${RootFiles.quote(name)} -type f -exec stat -c '%s %n' {} + | sort | md5sum)"
            }
            val script = listOf(
                "mkdir -p ${RootFiles.quote(dest)} || exit 1",
                "cp -r $from ${RootFiles.quote(dest)}/ || { echo failed-cp; exit 1; }",
                "a=${list(source)}",
                "b=${list(dest)}",
                "[ \"\$a\" = \"\$b\" ] && echo same || echo differs",
            ).joinToString("\n")
            val line = executor.script(script).getOrNull()?.trim()
            check(line == "same") { "$name: ${line ?: "PServer"}" }
        }
        Log.i(TAG, "Copied RetroArch's ${format.pack} to $dest: ${names.size} parts")
        names.size
    }

    /** Downloads libretro's pack (the one RetroArch's Online Updater gets); the one there stays until the new one opens as a zip. */
    fun download(format: ShaderFormat, progress: (Long, Long) -> Unit) {
        val file = zip(format)
        val part = File(file.path + PART)
        http.download("$PACKS/${format.pack}.zip", part, progress)
        val entries = try {
            ZipFile(part).use { it.size() }
        } catch (e: IOException) {
            part.delete()
            throw CheatProblem.Failed(e.message ?: "zip")
        }
        if (entries == 0 || !part.renameTo(file)) {
            part.delete()
            throw CheatProblem.Failed("zip")
        }
    }

    /** Unpacks the downloaded pack into the shader folder on storage, a top folder at a time ([step]); gives how many files. */
    fun unpack(places: ShaderPlaces, format: ShaderFormat, step: (Int, Int) -> Unit): Result<Int> = runCatching {
        val stage = File(context.cacheDir, STAGE).apply { deleteRecursively() }
        val files = try {
            ZipFile(zip(format)).use { zip ->
                val root = stage.canonicalPath
                zip.entries().asSequence().filter { !it.isDirectory }.map { entry ->
                    val file = File(stage, entry.name)
                    check(file.canonicalPath.startsWith("$root/")) { "bad name in the pack: ${entry.name}" }
                    file.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> file.outputStream().use { input.copyTo(it) } }
                    entry.name
                }.toList()
            }
        } catch (e: IOException) {
            throw CheatProblem.Failed(e.message ?: "zip")
        }
        copyStaged(stage, files, "${places.root}/${format.pack}", step)
        stage.deleteRecursively()
        files.size
    }

    /** Writes [shortcuts] (with their text for [format]) into the shader folder on storage. */
    fun write(places: ShaderPlaces, shortcuts: List<Shortcut>): Result<Unit> = runCatching {
        if (shortcuts.isEmpty()) return@runCatching
        val stage = File(context.cacheDir, "$STAGE-shortcuts").apply { deleteRecursively() }
        shortcuts.forEach { shortcut ->
            val file = File(stage, shortcut.path)
            file.parentFile?.mkdirs()
            file.writeText(ShaderShortcuts.text(shortcut.path, shortcut.target))
        }
        copyStaged(stage, shortcuts.map { it.path }, places.root) { _, _ -> }
        stage.deleteRecursively()
    }

    /** Removes the shortcuts at [paths] (only files with Thor Tools' marker), then folders left empty. */
    fun remove(places: ShaderPlaces, paths: List<String>): Result<Unit> = runCatching {
        if (paths.isEmpty()) return@runCatching
        val script = paths.joinToString("\n") { path ->
            val file = RootFiles.quote("${places.root}/$path")
            val folder = path.substringBeforeLast('/', "")
            val empties = generateSequence(folder.takeIf { it.isNotEmpty() }) { it.substringBeforeLast('/', "").ifEmpty { null } }
                .joinToString("; ") { "rmdir ${RootFiles.quote("${places.root}/$it")} 2>/dev/null" }
            "[ -f $file ] && grep -q '^${ShaderShortcuts.MARKER}' $file && rm $file; $empties"
        } + "\necho done"
        check(executor.script(script).getOrNull()?.trim() == "done") { "PServer" }
    }

    /**
     * Removes the automatic presets an older Thor Tools wrote ([paths], as root reached them; only ones with its marker),
     * putting back a preset kept before its first write.
     */
    fun removeOldLooks(paths: List<String>): Result<Unit> = runCatching {
        val script = paths.joinToString("\n") { path ->
            val shown = RootFiles.storageView(path) ?: path
            val file = RootFiles.quote(shown)
            val kept = RootFiles.quote(shown + RetroArchFiles.BACKUP_SUFFIX)
            "[ -f $file ] && grep -q '^$OLD_MARKER' $file && rm $file; [ ! -e $file ] && [ -f $kept ] && mv $kept $file"
        } + "\necho done"
        check(executor.script(script).getOrNull()?.trim() == "done") { "PServer" }
    }

    /**
     * Copies the [files] staged in [stage] (paths inside it) to [target] as root, a top folder at a time ([step]), each
     * file checked by its size afterwards.
     */
    internal fun copyStaged(stage: File, files: List<String>, target: String, step: (Int, Int) -> Unit) {
        stage.walk().forEach { it.setReadable(true, false) }
        val dest = RootFiles.quote(target)
        val folders = files.groupBy { it.substringBefore('/', "") }
        folders.entries.forEachIndexed { index, (folder, inFolder) ->
            step(index + 1, folders.size)
            val list = File(context.cacheDir, "$STAGE-$index.txt")
            list.writeText(inFolder.joinToString("") { "${File(stage, it).length()}\t$it\n" })
            list.setReadable(true, false)
            val copy = if (folder.isEmpty()) {
                inFolder.joinToString("\n") { "cp ${RootFiles.quote(File(stage, it).path)} $dest/ || exit 1" }
            } else {
                "cp -r ${RootFiles.quote(File(stage, folder).path)} $dest/ || exit 1"
            }
            val check = "[ \"\$(stat -c %s $dest/\"\$p\" 2>/dev/null)\" = \"\$s\" ] || bad=\$((bad+1))"
            val script = listOf(
                "mkdir -p $dest || exit 1",
                copy,
                "bad=0",
                "while IFS='\t' read -r s p; do $check; done < ${RootFiles.quote(list.path)}",
                "echo \"copied \$bad\"",
            ).joinToString("\n")
            val line = executor.script(script).getOrNull()?.trim()
            list.delete()
            check(line == "copied 0") { "${folder.ifEmpty { "/" }}: ${line ?: "PServer"}" }
        }
    }

    private companion object {
        const val TAG = "ShaderLibrary"
        const val DIR = "retroarch-shaders"
        const val STAGE = "shader-stage"
        const val PART = ".part"
        const val PACKS = "https://buildbot.libretro.com/assets/frontend"

        /** The marker of the automatic presets an older Thor Tools wrote into RetroArch's menu config folder. */
        const val OLD_MARKER = "# Thor Tools look:"
    }
}
