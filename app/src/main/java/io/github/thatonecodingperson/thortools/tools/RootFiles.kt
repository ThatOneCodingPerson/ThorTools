package io.github.thatonecodingperson.thortools.tools

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Reads and writes files in other apps' folders as root, through PServer. A written file gets the owner of a folder the
 * app already made and the mode apps' files have, so the app keeps reading and replacing it; each write is checked byte
 * for byte. Commands go as scripts, since paths make them longer than PServer takes on one line. A file in shared storage
 * is written through Android's own view of it ([storageView]): written underneath, that view can keep an old size for a
 * file the media scanner looked at while it was being written, and apps reading through it then get a short file.
 * Everything here goes through PServer: never on the main thread.
 */
class RootFiles @Inject constructor(@ApplicationContext private val context: Context, private val executor: ShellExecutor) {
    /**
     * The whole file as text, to the last byte (the shell's output loses its trailing new lines, so an end mark follows
     * the file); null when it isn't there or root can't read it.
     */
    fun read(path: String): String? {
        val quoted = quote(path)
        val output = executor.capture("[ -f $quoted ] && { cat $quoted; echo $END; } || echo $MISSING").getOrNull() ?: return null
        return output.takeIf { it.endsWith(END) }?.removeSuffix(END)
    }

    /** True or false when root could look, null when it couldn't. */
    fun exists(path: String): Boolean? = when (executor.script("[ -e ${quote(path)} ] && echo yes || echo no").getOrNull()) {
        "yes" -> true
        "no" -> false
        else -> null
    }

    /**
     * Writes [bytes] to [target]. The folders in [makeDirs] are made when missing; they and the file get the owner of
     * [ownerOf], folders mode 2770 and the file [RootSaveCheck.FILE_MODE]. Fails with the check line when the file
     * doesn't come out with that owner, mode, size and the same bytes.
     */
    fun write(target: String, bytes: ByteArray, ownerOf: String, makeDirs: List<String> = emptyList()): Result<Unit> {
        val staged = File(File(context.cacheDir, STAGING).apply { mkdirs() }, "${System.nanoTime()}.part")
        runCatching {
            staged.writeBytes(bytes)
            staged.setReadable(true, false)
            staged.parentFile?.setExecutable(true, false)
        }.onFailure { return Result.failure(it) }
        val dirs = makeDirs.joinToString(" ") { quote(it) }
        val script = listOfNotNull(
            "owner=\$(stat -c %u:%g ${quote(ownerOf)}) || exit 1",
            "mkdir -p $dirs || exit 1".takeIf { makeDirs.isNotEmpty() },
            destination(target),
            "cp ${quote(staged.absolutePath)} \"\$dest\" || cp ${quote(staged.absolutePath)} ${quote(target)} || exit 1",
            "chown \"\$owner\" $dirs ${quote(target)}",
            "chmod 2770 $dirs".takeIf { makeDirs.isNotEmpty() },
            "chmod ${RootSaveCheck.FILE_MODE} ${quote(target)}",
            "cmp -s ${quote(staged.absolutePath)} ${quote(target)} && same=${RootSaveCheck.SAME} || same=${RootSaveCheck.DIFFERS}",
            "echo \"\$owner \$(stat -c '%u:%g %a %s' ${quote(target)}) \$same\"",
        ).joinToString("\n")
        val result = executor.script(script)
        staged.delete()
        val line = result.getOrNull()?.trim() ?: return Result.failure(result.exceptionOrNull() ?: IllegalStateException("PServer"))
        return if (RootSaveCheck.passes(line, bytes.size)) Result.success(Unit) else Result.failure(IllegalStateException(line))
    }

    /**
     * Copies [from] to [to] straight from where it is. The folders in [makeDirs] that are missing are made, parent first,
     * with the owner of [ownerOf] and mode 2770; the file gets that owner and [RootSaveCheck.FILE_MODE]. A file already at
     * [to] is copied to [keepAs] first, unless something is there already, so nothing is lost. Fails with the check line
     * (or the step that failed) unless the copy comes out with that owner, the size of [from] and the same bytes.
     */
    fun copy(from: String, to: String, ownerOf: String, makeDirs: List<String> = emptyList(), keepAs: String? = null): Result<Unit> {
        val source = quote(from)
        val target = quote(to)
        val script = buildList {
            add("owner=\$(stat -c %u:%g ${quote(ownerOf)}) || { echo ${RootSaveCheck.FAILED}owner; exit 1; }")
            makeDirs.forEach { dir ->
                val folder = quote(dir)
                add(
                    "[ -d $folder ] || { mkdir $folder || { echo ${RootSaveCheck.FAILED}mkdir; exit 1; }; chown \"\$owner\" $folder; chmod 2770 $folder; }",
                )
            }
            if (keepAs != null) {
                val kept = quote(keepAs)
                add(destination(keepAs, "keep"))
                add("[ ! -e $target ] || [ -e $kept ] || cp $target \"\$keep\" || { echo ${RootSaveCheck.FAILED}keep; exit 1; }")
            }
            add(destination(to))
            add("cp $source \"\$dest\" || cp $source $target || { echo ${RootSaveCheck.FAILED}cp; exit 1; }")
            add("chown \"\$owner\" $target")
            add("chmod ${RootSaveCheck.FILE_MODE} $target")
            add("cmp -s $source $target && same=${RootSaveCheck.SAME} || same=${RootSaveCheck.DIFFERS}")
            add("echo \"\$owner \$(stat -c '%u:%g %a %s' $target) \$same \$(stat -c %s $source)\"")
        }.joinToString("\n")
        val result = executor.script(script)
        val line = result.getOrNull()?.trim() ?: return Result.failure(result.exceptionOrNull() ?: IllegalStateException("PServer"))
        return if (RootSaveCheck.copied(line)) Result.success(Unit) else Result.failure(IllegalStateException(line))
    }

    /** Copies [from] to [to] unless [to] is there already; true when [to] is there afterwards. */
    fun copyOnce(from: String, to: String): Boolean = executor.script(
        "[ -e ${quote(to)} ] || cp -p ${quote(from)} ${quote(to)}; [ -e ${quote(to)} ] && echo yes",
    ).getOrNull() == "yes"

    /** The shell line that sets [variable] to where [path] is written: through [storageView] when root can reach that. */
    private fun destination(path: String, variable: String = "dest"): String {
        val view = storageView(path) ?: return "$variable=${quote(path)}"
        return "$variable=${quote(path)}; [ -d ${quote(view.substringBeforeLast('/'))} ] && $variable=${quote(view)}"
    }

    companion object {
        private const val STAGING = "root-files"
        private const val MISSING = "-thortools-missing-"
        private const val END = "-thortools-end-"
        private const val MEDIA = "/data/media/0"
        private const val SHARED = "/storage/emulated/0"

        /** [path] (as root sees shared storage) in Android's own view of shared storage; null when it lies elsewhere. */
        fun storageView(path: String): String? = path.takeIf { it.startsWith("$MEDIA/") }?.let { SHARED + it.removePrefix(MEDIA) }

        /** [text] as one shell word, whatever quotes it holds. */
        fun quote(text: String): String = "'" + text.replace("'", "'\\''") + "'"
    }
}

/**
 * The one line a root write prints: the owner it should get, then the file's owner, mode and size, then whether its
 * bytes match the staged copy.
 */
object RootSaveCheck {
    const val SAME = "same"
    const val DIFFERS = "differs"
    const val FILE_MODE = "660"

    /** Starts the line of a root copy that stopped, followed by the step. */
    const val FAILED = "failed-"

    fun passes(line: String?, size: Int): Boolean {
        val parts = line?.trim()?.split(' ') ?: return false
        return parts.size == 5 &&
            parts[0] == parts[1] &&
            parts[2] == FILE_MODE &&
            parts[3] == size.toString() &&
            parts[4] == SAME
    }

    /**
     * A root copy's line: as [passes], then the source's size, which the copy's must equal. The mode isn't checked, since
     * an SD card's file system has none of its own.
     */
    fun copied(line: String?): Boolean {
        val parts = line?.trim()?.split(' ') ?: return false
        return parts.size == 6 &&
            parts[0] == parts[1] &&
            parts[3] == parts[5] &&
            parts[4] == SAME
    }
}
