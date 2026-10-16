package io.github.thatonecodingperson.thortools.roms

import io.github.thatonecodingperson.thortools.tools.RootFiles.Companion.quote

/** What happens to the files a game was made of once its new file has passed its check. */
enum class Originals { KEEP, DELETE, MOVE }

/** How hard a job works: chdman's [processors], and whether it moves onto the little cores ([background]). */
enum class RomSpeed(val processors: Int, val background: Boolean) {
    GENTLE(3, true),
    NORMAL(6, false),
    FAST(8, false),
}

enum class JobKind {
    /** Makes the compressed file. */
    COMPRESS,

    /** Turns a compressed file back into a sheet and a track. */
    EXTRACT,

    /** Checks the compressed file again, then deletes or moves the files it was made from. */
    ORIGINALS,
}

/**
 * One job: [input] the sheet or lone image, or the compressed file; [output] the compressed file, or the sheet a
 * compressed file turns back into; [originals] the files [choice] applies to afterwards (moved into [moveTo]); [need]
 * the free bytes it needs where [output] goes; [sbiFrom] copied to [sbiTo] when the LibCrypt file is named after a track
 * instead of the game; [playlists] the `.m3u` files that get [output]'s name. When [sheet] is set, chdman reads that
 * sheet from the job's own folder, beside [links] (file name to the file it stands for): a lone image's made-up sheet,
 * or a copy of a sheet whose tracks lie elsewhere. [emptyFolder] is removed when the originals leave it empty. [format]
 * is the format made, or for turning back and cleaning up the compressed file's (a CHD of either kind counts as
 * [RomFormat.CHD_CD]). [from]: the job turns a compressed file of that format into [format], through an image of [temp]
 * bytes unpacked into a folder of its own beside it.
 */
data class RomJob(
    val id: Long,
    val kind: JobKind,
    val console: RomConsole,
    val title: String,
    val folder: String,
    val input: String,
    val output: String,
    val originals: List<String>,
    val choice: Originals,
    val speed: RomSpeed,
    val inputBytes: Long,
    val estimate: Long,
    val need: Long,
    val sbiFrom: String? = null,
    val sbiTo: String? = null,
    val playlists: List<String> = emptyList(),
    val moveTo: String? = null,
    val sheet: String? = null,
    val links: Map<String, String> = emptyMap(),
    val emptyFolder: String? = null,
    val format: RomFormat = RomFormat.CHD_CD,
    val from: RomFormat? = null,
    val temp: Long = 0L,
) {
    val outputName: String get() = output.substringAfterLast('/')
}

/** How a job ended, from the `result` line its script writes. */
sealed class JobEnd {
    data class Done(val size: Long) : JobEnd()
    data object Cancelled : JobEnd()
    data object Exists : JobEnd()
    data class NoSpace(val free: Long) : JobEnd()
    data class Failed(val step: String, val code: Int?) : JobEnd()

    companion object {
        private const val KB = 1024L

        fun parse(line: String?): JobEnd? {
            val parts = line?.trim()?.split(' ')?.filter { it.isNotEmpty() } ?: return null
            return when (parts.firstOrNull()) {
                "ok" -> Done(parts.getOrNull(1)?.toLongOrNull() ?: 0L)
                "cancelled" -> Cancelled
                "exists" -> Exists
                "space" -> NoSpace(parts.getOrNull(1)?.removeSuffix("k")?.toLongOrNull()?.times(KB) ?: 0L)
                null -> null
                else -> Failed(parts[0], parts.getOrNull(1)?.toIntOrNull())
            }
        }
    }
}

/** The root scripts that carry out jobs, and jobs written to and read from a file. */
object RomJobPlan {
    /** The sheet chdman reads from the job's own folder when the job has one, and a lone image's link beside it. */
    const val LONE_SHEET = "disc.cue"
    const val LONE_IMAGE = "disc.bin"
    const val PROGRESS = "progress.log"
    const val RESULT = "result"
    const val PHASE = "phase"
    const val CANCEL = "cancel"
    const val PAUSED = "paused"
    const val PID = "pid"
    const val CHILD = "cpid"
    const val JOB = "job.txt"

    /** Where the app writes chdman's path when it picks up a running job: an update moves chdman to a new folder. */
    const val ENGINE = "engine"

    /** The same for maxcso. */
    const val ENGINE_MAXCSO = "engine-maxcso"

    /** The same for nod's nodtool. */
    const val ENGINE_NOD = "engine-nod"

    /** The same for 7-Zip's 7zz. */
    const val ENGINE_7ZZ = "engine-7zz"

    /** The same for the z3ds tool. */
    const val ENGINE_Z3DS = "engine-z3ds"

    /** The same for the wua tool. */
    const val ENGINE_WUA = "engine-wua"

    /** Seconds a job waits for chdman to be back after an update before it gives up. */
    private const val ENGINE_WAIT = 120
    private const val NEXT_JOB = "@@job"

    /** Room kept free besides the new file, in bytes. */
    const val MARGIN = 64L * 1024 * 1024
    private const val KB = 1024L

    /** The compressed file while it is being made. */
    fun part(output: String) = "$output.part"

    /** The new contents of [job]'s playlists go into the job's folder as these files, in order. */
    fun playlistFile(index: Int) = "m3u-$index"

    /** A program a job runs: the shell variable holding its path, the function that finds it again, its path file. */
    private enum class Engine(val variable: String, val function: String, val file: String) {
        CHDMAN("C", "engine", ENGINE),
        MAXCSO("M", "engine_maxcso", ENGINE_MAXCSO),
        NOD("N", "engine_nod", ENGINE_NOD),
        SEVENZIP("Z", "engine_7zz", ENGINE_7ZZ),
        Z3DS("Y", "engine_z3ds", ENGINE_Z3DS),
        WUA("X", "engine_wua", ENGINE_WUA),
    }

    /**
     * The script for [job], run as root in its folder [work] (the app's own folder for it) with chdman at [chdman],
     * maxcso at [maxcso], nod's nodtool at [nod], 7-Zip's 7zz at [sevenZip], the z3ds tool at [z3ds] and the wua tool
     * at [wua]. It writes the files the app watches ([PID], [CHILD], [PHASE], [PROGRESS]) and ends by writing [RESULT].
     */
    fun script(
        job: RomJob,
        chdman: String,
        work: String,
        maxcso: String? = null,
        nod: String? = null,
        sevenZip: String? = null,
        z3ds: String? = null,
        wua: String? = null,
    ): String = buildString {
        appendLine("W=${quote(work)}")
        appendLine("cd \"\$W\" || exit 1")
        appendLine("umask 022")
        appendLine("echo \$\$ > \"\$W/$PID\"")
        val folders = job.console.listKind == DiscKind.FOLDER
        // A job that unpacks an image first, or makes a GameCube or Wii file in a folder of its own, removes that
        // folder however it ends.
        val ownFolder = job.from != null || job.console.check == DiscCheck.NINTENDO || job.console.cartridges || folders
        val dropTemp = if (ownFolder) "rm -rf ${quote(temp(job))}; " else ""
        appendLine("finish() { ${dropTemp}echo \"\$1\" > \"\$W/$RESULT\"; chmod 644 \"\$W\"/* 2>/dev/null; exit 0; }")
        appendLine("C=${quote(chdman)}")
        engineLookup(Engine.CHDMAN, chdman)
        if (maxcso != null) {
            appendLine("M=${quote(maxcso)}")
            engineLookup(Engine.MAXCSO, maxcso)
        }
        if (nod != null) {
            appendLine("N=${quote(nod)}")
            engineLookup(Engine.NOD, nod)
        }
        if (sevenZip != null) {
            appendLine("Z=${quote(sevenZip)}")
            engineLookup(Engine.SEVENZIP, sevenZip)
        }
        if (z3ds != null) {
            appendLine("Y=${quote(z3ds)}")
            engineLookup(Engine.Z3DS, z3ds)
        }
        if (wua != null) {
            appendLine("X=${quote(wua)}")
            engineLookup(Engine.WUA, wua)
        }
        if (job.speed.background) appendLine("echo \$\$ > /dev/cpuset/background/tasks 2>/dev/null")
        val threeDs = job.format.tool == RomTool.Z3DS || job.from?.tool == RomTool.Z3DS
        when {
            folders && job.kind == JobKind.ORIGINALS -> wuaOriginals(job)
            folders && job.from != null -> wuaUnpack(job)
            folders -> wuaPack(job)
            job.console.cartridges && job.kind == JobKind.ORIGINALS -> if (threeDs) z3dsOriginals(job) else cartridgeOriginals(job)
            job.console.cartridges && threeDs -> z3ds(job)
            job.console.cartridges -> cartridge(job)
            job.console.check == DiscCheck.NINTENDO && job.kind != JobKind.ORIGINALS -> nintendo(job)
            job.kind == JobKind.COMPRESS -> compress(job)
            job.kind == JobKind.EXTRACT -> extract(job)
            else -> originalsOnly(job)
        }
    }

    /**
     * A GameCube or Wii job: nod turns the image or compressed file into [RomJob.format] in one go, inside the job's
     * own folder beside the game (nod takes the format from the new file's extension). nod prints the SHA-1 of what it
     * read; the new file is read back and must give the same SHA-1 before it is moved into place.
     */
    private fun StringBuilder.nintendo(job: RomJob) {
        val out = quote(job.output)
        val temp = quote(temp(job))
        val made = quote("${temp(job)}/${job.title}.${job.format.extension}")
        val threads = "-j ${job.speed.processors}"
        appendLine("[ -e $out ] && finish exists")
        spaceCheck(job.folder, "${(job.need + KB - 1) / KB}")
        if (job.from == null) nintendoCheck(job.input)
        appendLine("mkdir -p $temp || finish \"folder 1\"")
        val options = job.console.optionsFor(job.format)
        val arguments = listOf("convert", threads, options, quote(job.input), made).filter { it.isNotEmpty() }.joinToString(" ")
        run("compress", arguments, null, engine = Engine.NOD)
        appendLine("sha1=${sha1Of("compress.out")}")
        run("verify", "verify $threads $made", null, engine = Engine.NOD)
        appendLine("sha2=${sha1Of("verify.out")}")
        appendLine("[ -n \"\$sha1\" ] && [ \"\$sha1\" = \"\$sha2\" ] || finish \"verify 1\"")
        appendLine("[ -e $out ] && finish exists")
        appendLine("mv $made $out || finish \"move 1\"")
        appendLine("rmdir $temp 2>/dev/null")
        appendLine("size=\$(stat -c %s $out)")
        appendLine("echo finish > \"\$W/$PHASE\"")
        job.playlists.forEachIndexed { index, playlist -> appendLine("cat \"\$W/${playlistFile(index)}\" > ${quote(playlist)}") }
        handleOriginals(job)
        appendLine("finish \"ok \$size\"")
    }

    /**
     * A cartridge job with 7-Zip, in the job's own folder beside the game: a plain ROM into an archive, an archive into
     * the other kind (unpacked first, every file kept), or an archive into its plain ROM. Before anything moves, every
     * file in the new archive must have the size, CRC32 and name it had (`sig`), and the archive must test whole.
     */
    private fun StringBuilder.cartridge(job: RomJob) {
        val out = quote(job.output)
        val temp = quote(temp(job))
        val unpacked = quote("${temp(job)}/x")
        val made = quote("${temp(job)}/${job.outputName}")
        val threads = "-mmt=${job.speed.processors}"
        cartridgeHelpers()
        appendLine("[ -e $out ] && finish exists")
        spaceCheck(job.folder, "${(job.need + KB - 1) / KB}")
        appendLine("mkdir -p $temp || finish \"folder 1\"")
        val options = job.console.optionsFor(job.format)
        when {
            job.from == null -> {
                val name = job.input.substringAfterLast('/')
                appendLine("want=\$(romsig ${quote(job.input)} ${quote(name)})")
                appendLine("cd ${quote(job.input.substringBeforeLast('/'))} || finish \"folder 2\"")
                run("compress", "a $options $threads -bsp2 -bso1 $made -- ${quote(name)}", null, engine = Engine.SEVENZIP)
                appendLine("cd \"\$W\"")
                archiveCheck(made)
            }
            job.format == RomFormat.ROM_FILE -> {
                val name = job.outputName
                appendLine("mkdir -p $unpacked || finish \"folder 1\"")
                run("unpack", "x -y -bsp2 -bso1 -o$unpacked -- ${quote(job.input)} ${quote(name)}", null, engine = Engine.SEVENZIP)
                appendLine("got=\$(romsig $unpacked/${quote(name)} ${quote(name)})")
                appendLine("sig ${quote(job.input)} | grep -F -x -- \"\$got\" >/dev/null || finish \"verify 1\"")
                appendLine("[ -e $out ] && finish exists")
                appendLine("mv $unpacked/${quote(name)} $out || finish \"move 1\"")
                cartridgeEnd(job, out)
                return
            }
            else -> {
                appendLine("mkdir -p $unpacked || finish \"folder 1\"")
                run("unpack", "x -y -bsp2 -bso1 -o$unpacked -- ${quote(job.input)}", null, engine = Engine.SEVENZIP)
                appendLine("want=\$(sig ${quote(job.input)})")
                appendLine("cd $unpacked || finish \"folder 2\"")
                run("compress", "a $options $threads -bsp2 -bso1 $made -- *", null, engine = Engine.SEVENZIP)
                appendLine("cd \"\$W\"")
                archiveCheck(made)
            }
        }
        appendLine("[ -e $out ] && finish exists")
        appendLine("mv $made $out || finish \"move 1\"")
        cartridgeEnd(job, out)
    }

    /**
     * A 3DS job with the z3ds tool, in the job's own folder beside the game: a plain ROM into Z3DS (the tool prints the
     * SHA-1 of the ROM it read; the new file is unpacked in memory and must give the same), or a Z3DS file back into its
     * plain ROM (the SHA-1 of what it unpacked must be the written file's).
     */
    private fun StringBuilder.z3ds(job: RomJob) {
        val out = quote(job.output)
        val temp = quote(temp(job))
        val made = quote("${temp(job)}/${job.outputName}")
        val threads = "-j ${job.speed.processors}"
        appendLine("[ -e $out ] && finish exists")
        spaceCheck(job.folder, "${(job.need + KB - 1) / KB}")
        appendLine("mkdir -p $temp || finish \"folder 1\"")
        if (job.from == null) {
            val options = job.console.optionsFor(job.format)
            val arguments = listOf("compress", options, threads, quote(job.input), made).filter { it.isNotEmpty() }.joinToString(" ")
            run("compress", arguments, null, engine = Engine.Z3DS)
            appendLine("sha1=${sha1Of("compress.out")}")
            run("verify", "verify $threads $made", null, engine = Engine.Z3DS)
        } else {
            run("unpack", "decompress $threads ${quote(job.input)} $made", null, engine = Engine.Z3DS)
            appendLine("sha1=${sha1Of("unpack.out")}")
            run("verify", "sha1 $made", null, engine = Engine.Z3DS)
        }
        appendLine("sha2=${sha1Of("verify.out")}")
        appendLine("[ -n \"\$sha1\" ] && [ \"\$sha1\" = \"\$sha2\" ] || finish \"verify 1\"")
        appendLine("[ -e $out ] && finish exists")
        appendLine("mv $made $out || finish \"move 1\"")
        cartridgeEnd(job, out)
    }

    /** Cleaning up a 3DS game's originals: its Z3DS file unpacks to what each plain ROM (or older Z3DS file) holds. */
    private fun StringBuilder.z3dsOriginals(job: RomJob) {
        val threads = "-j ${job.speed.processors}"
        run("verify", "verify $threads ${quote(job.input)}", null, engine = Engine.Z3DS)
        appendLine("have=${sha1Of("verify.out")}")
        appendLine("[ -n \"\$have\" ] || finish \"verify 1\"")
        job.originals.forEach { original ->
            val plain = original.substringAfterLast('.', "").lowercase() in job.console.romTypes
            run("verify", "${if (plain) "sha1" else "verify $threads"} ${quote(original)}", null, engine = Engine.Z3DS)
            appendLine("[ \"${sha1Of("verify.out")}\" = \"\$have\" ] || finish \"verify 1\"")
        }
        appendLine("echo finish > \"\$W/$PHASE\"")
        handleOriginals(job)
        appendLine("finish \"ok \$(stat -c %s ${quote(job.input)})\"")
    }

    /** `NAME=DIR` for each of [titles] (a WUA's title folder and the folder on disk), quoted. */
    private fun titleArguments(titles: Map<String, String>) = titles.entries.joinToString(" ") { (name, dir) -> quote("$name=$dir") }

    /**
     * A Wii U game's title folders into a WUA, in the job's own folder beside them: the wua tool prints one SHA-1 over
     * every file it packed (path, size, contents); the new WUA is read back whole and must give the same one before it
     * moves in. Each folder must still be a title (`code/app.xml`).
     */
    private fun StringBuilder.wuaPack(job: RomJob) {
        val out = quote(job.output)
        val temp = quote(temp(job))
        val made = quote("${temp(job)}/${job.outputName}")
        appendLine("[ -e $out ] && finish exists")
        spaceCheck(job.folder, "${(job.need + KB - 1) / KB}")
        job.links.values.forEach { dir -> appendLine("[ -f ${quote("$dir/code/app.xml")} ] || finish notgame") }
        appendLine("mkdir -p $temp || finish \"folder 1\"")
        run("compress", "pack -j ${job.speed.processors} $made ${titleArguments(job.links)}", null, engine = Engine.WUA)
        appendLine("sha1=${sha1Of("compress.out")}")
        run("verify", "verify $made", null, engine = Engine.WUA)
        appendLine("sha2=${sha1Of("verify.out")}")
        appendLine("[ -n \"\$sha1\" ] && [ \"\$sha1\" = \"\$sha2\" ] || finish \"verify 1\"")
        appendLine("[ -e $out ] && finish exists")
        appendLine("mv $made $out || finish \"move 1\"")
        appendLine("rm -rf $temp")
        appendLine("size=\$(stat -c %s $out)")
        appendLine("echo finish > \"\$W/$PHASE\"")
        folderOriginals(job)
        appendLine("finish \"ok \$size\"")
    }

    /**
     * A WUA back into its title folders: unpacked into the job's own folder (the tool prints the SHA-1 of what it
     * unpacked), the folders read again must give the same, then each moves beside the WUA under its name.
     */
    private fun StringBuilder.wuaUnpack(job: RomJob) {
        val temp = temp(job)
        job.links.values.forEach { target -> appendLine("[ -e ${quote(target)} ] && finish exists") }
        spaceCheck(job.folder, "${(job.need + KB - 1) / KB}")
        appendLine("mkdir -p ${quote(temp)} || finish \"folder 1\"")
        val names = job.links.mapValues { (_, target) -> target.substringAfterLast('/') }
        run("unpack", "extract ${quote(job.input)} ${quote(temp)} ${titleArguments(names)}", null, engine = Engine.WUA)
        appendLine("sha1=${sha1Of("unpack.out")}")
        run("verify", "digest ${titleArguments(names.mapValues { (_, name) -> "$temp/$name" })}", null, engine = Engine.WUA)
        appendLine("sha2=${sha1Of("verify.out")}")
        appendLine("[ -n \"\$sha1\" ] && [ \"\$sha1\" = \"\$sha2\" ] || finish \"verify 1\"")
        job.links.values.forEach { target -> appendLine("[ -e ${quote(target)} ] && finish exists") }
        job.links.values.forEach { target ->
            appendLine("mv ${quote("$temp/${target.substringAfterLast('/')}")} ${quote(target)} || finish \"move 1\"")
        }
        appendLine("rm -rf ${quote(temp)}")
        appendLine("echo finish > \"\$W/$PHASE\"")
        handleOriginals(job)
        appendLine("finish \"ok ${job.temp}\"")
    }

    /** Cleaning up a Wii U game's folders: its WUA, read back whole, must give the SHA-1 the folders give. */
    private fun StringBuilder.wuaOriginals(job: RomJob) {
        run("verify", "verify ${quote(job.input)}", null, engine = Engine.WUA)
        appendLine("have=${sha1Of("verify.out")}")
        appendLine("[ -n \"\$have\" ] || finish \"verify 1\"")
        run("verify", "digest ${titleArguments(job.links)}", null, engine = Engine.WUA)
        appendLine("[ \"${sha1Of("verify.out")}\" = \"\$have\" ] || finish \"verify 1\"")
        appendLine("echo finish > \"\$W/$PHASE\"")
        folderOriginals(job)
        appendLine("finish \"ok \$(stat -c %s ${quote(job.input)})\"")
    }

    /**
     * What happens to a Wii U game's title folders after the check: kept, deleted (only a folder that still holds
     * `code/app.xml`) or moved whole.
     */
    private fun StringBuilder.folderOriginals(job: RomJob) {
        when (job.choice) {
            Originals.KEEP -> return
            Originals.DELETE -> job.originals.forEach { dir ->
                appendLine("[ -f ${quote("$dir/code/app.xml")} ] && rm -rf ${quote(dir)}")
            }
            Originals.MOVE -> {
                val target = job.moveTo ?: return
                appendLine("mkdir -p ${quote(target)}")
                job.originals.forEach { dir ->
                    val there = quote("$target/${dir.substringAfterLast('/')}")
                    appendLine("[ -e $there ] || mv ${quote(dir)} $there")
                }
            }
        }
    }

    /** Tests the new archive [made] whole, then compares what it holds with `want`. */
    private fun StringBuilder.archiveCheck(made: String) {
        run("verify", "t -bsp2 -bso1 $made", null, engine = Engine.SEVENZIP)
        appendLine("got=\$(sig $made)")
        appendLine("[ -n \"\$want\" ] && [ \"\$got\" = \"\$want\" ] || finish \"verify 1\"")
    }

    private fun StringBuilder.cartridgeEnd(job: RomJob, out: String) {
        appendLine("rm -rf ${quote(temp(job))}")
        appendLine("size=\$(stat -c %s $out)")
        appendLine("echo finish > \"\$W/$PHASE\"")
        handleOriginals(job)
        appendLine("finish \"ok \$size\"")
    }

    /**
     * Cleaning up a cartridge game's originals: its archive tests whole and holds the plain ROM as it is (same size,
     * CRC32 and name), or the same files as each older archive.
     */
    private fun StringBuilder.cartridgeOriginals(job: RomJob) {
        cartridgeHelpers()
        val archive = quote(job.input)
        run("verify", "t -bsp2 -bso1 $archive", null, engine = Engine.SEVENZIP)
        appendLine("have=\$(sig $archive)")
        job.originals.forEach { original ->
            val name = original.substringAfterLast('/')
            if (name.substringAfterLast('.', "").lowercase() in job.console.romTypes) {
                appendLine(
                    "echo \"\$have\" | grep -F -x -- \"\$(romsig ${quote(original)} ${quote(name)})\" >/dev/null || finish \"verify 1\"",
                )
            } else {
                appendLine("[ \"\$(sig ${quote(original)})\" = \"\$have\" ] || finish \"verify 1\"")
            }
        }
        appendLine("echo finish > \"\$W/$PHASE\"")
        handleOriginals(job)
        appendLine("finish \"ok \$(stat -c %s $archive)\"")
    }

    /**
     * `sig <archive>`: one `size CRC32 path` line per file inside, sorted; `romsig <file> <name>`: the same line for a
     * plain file under the name it gets inside an archive.
     */
    private fun StringBuilder.cartridgeHelpers() {
        appendLine(
            "sig() { \"\$Z\" l -slt -ba \"\$1\" 2>/dev/null | " +
                "awk -F ' = ' '/^Path = /{p=\$2} /^Size = /{s=\$2} /^CRC = /{if (\$2 != \"\") print s \" \" toupper(\$2) \" \" p}' | sort; }",
        )
        appendLine(
            "romsig() { c=\$(\"\$Z\" h -scrcCRC32 \"\$1\" 2>/dev/null | awk '/CRC32 +for data/{print toupper(\$NF)}'); " +
                "[ -n \"\$c\" ] && echo \"\$(stat -c %s \"\$1\") \$c \$2\"; }",
        )
    }

    /** The disc's SHA-1 nod printed into [file] in the job's folder (`SHA-1 : <hex>`), or nothing. */
    private fun sha1Of(file: String) = "\$(grep 'SHA-1 *:' \"\$W/$file\" | tail -n 1 | sed 's/^[^:]*: *//' | cut -c1-40)"

    /**
     * Ends the job ("notdisc") unless [image] starts like a GameCube or Wii disc: the Wii's magic number at byte 0x18
     * or the GameCube's at 0x1C.
     */
    private fun StringBuilder.nintendoCheck(image: String) {
        appendLine("magic=\$(dd if=${quote(image)} bs=8 skip=3 count=1 2>/dev/null | od -An -tx1 | tr -d ' \\n')")
        appendLine("case \"\$magic\" in 5d1c9ea3*|????????c2339f3d) ;; *) finish notdisc ;; esac")
    }

    private fun StringBuilder.compress(job: RomJob) {
        val out = quote(job.output)
        val part = quote(part(job.output))
        appendLine("[ -e $out ] && finish exists")
        spaceCheck(job.folder, "${(job.need + KB - 1) / KB}")
        val format = job.format
        val options = job.console.optionsFor(format)
        val from = job.from
        val image = if (from != null) unpack(job, from) else job.input
        when {
            format.tool == RomTool.NONE -> {
                appendLine("[ -e $out ] && finish exists")
                appendLine("mv ${quote(image)} $out || finish \"move 1\"")
                appendLine("size=\$(stat -c %s $out)")
                appendLine("echo finish > \"\$W/$PHASE\"")
                handleOriginals(job)
                appendLine("finish \"ok \$size\"")
                return
            }
            format.tool == RomTool.MAXCSO -> {
                discCheck(image)
                val arguments = listOf("--threads=${job.speed.processors}", options, quote(image), "-o", part)
                run("compress", arguments.filter { it.isNotEmpty() }.joinToString(" "), part, engine = Engine.MAXCSO)
                crcCheck(image, part, removeOnFail = true)
            }
            format.disc == DiscKind.DVD -> {
                discCheck(image)
                val arguments = listOf("createdvd -i", quote(image), "-o", part, options, "-np ${job.speed.processors} -f")
                run("compress", arguments.filter { it.isNotEmpty() }.joinToString(" "), part)
                verify(part)
            }
            else -> {
                val sheet = if (job.sheet != null) {
                    job.links.forEach { (name, target) ->
                        appendLine("ln -sf ${quote(target)} \"\$W\"/${quote(name)} || finish \"link 1\"")
                    }
                    "\"\$W/$LONE_SHEET\""
                } else {
                    quote(job.input)
                }
                run("compress", "createcd -i $sheet -o $part -np ${job.speed.processors} -f", part)
                verify(part)
            }
        }
        appendLine("[ -e $out ] && { rm -f $part; finish exists; }")
        appendLine("mv $part $out || { rm -f $part; finish \"move 1\"; }")
        appendLine("size=\$(stat -c %s $out)")
        appendLine("echo finish > \"\$W/$PHASE\"")
        if (job.sbiFrom != null &&
            job.sbiTo != null
        ) {
            appendLine("[ -e ${quote(job.sbiTo)} ] || cp ${quote(job.sbiFrom)} ${quote(job.sbiTo)}")
        }
        job.playlists.forEachIndexed { index, playlist -> appendLine("cat \"\$W/${playlistFile(index)}\" > ${quote(playlist)}") }
        handleOriginals(job)
        appendLine("finish \"ok \$size\"")
    }

    /**
     * Unpacks [job]'s compressed file (in [from]) into its temporary folder and gives the image's path there. A CHD
     * must hold a DVD image; a CSO or ZSO always does.
     */
    private fun StringBuilder.unpack(job: RomJob, from: RomFormat): String {
        val image = "${temp(job)}/${job.title}.iso"
        appendLine("mkdir -p ${quote(temp(job))} || finish \"folder 1\"")
        if (from.tool == RomTool.MAXCSO) {
            run("unpack", "--decompress ${quote(job.input)} -o ${quote(image)}", null, engine = Engine.MAXCSO)
        } else {
            appendLine("engine || finish \"engine 1\"")
            appendLine("\"\$C\" info -i ${quote(job.input)} > \"\$W/info.out\" 2>/dev/null")
            appendLine("grep -q \"Tag='DVD '\" \"\$W/info.out\" || finish notdvd")
            run("unpack", "extractdvd -i ${quote(job.input)} -o ${quote(image)} -f", null)
        }
        return image
    }

    private fun StringBuilder.extract(job: RomJob) {
        if (job.format.tool == RomTool.MAXCSO) {
            appendLine("[ -e ${quote(isoOf(job))} ] && finish exists")
            // A CSO or ZSO keeps the image's size at byte 8 of its header (64 bits, little-endian).
            appendLine("logical=\$(dd if=${quote(job.input)} bs=1 skip=8 count=8 2>/dev/null | od -An -t u8 | tr -dc 0-9)")
            extractImage(job, "--decompress ${quote(job.input)} -o ${quote(isoOf(job).substringAfterLast('/'))}", Engine.MAXCSO)
            return
        }
        appendLine("engine || finish \"engine 1\"")
        appendLine("\"\$C\" info -i ${quote(job.input)} > \"\$W/info.out\" 2>/dev/null")
        appendLine("logical=\$(grep 'Logical size' \"\$W/info.out\" | tr -dc 0-9)")
        if (job.console.takesIso) {
            // A DVD image ends the job inside this branch; a CD goes on below.
            appendLine("if grep -q \"Tag='DVD '\" \"\$W/info.out\"; then")
            appendLine("[ -e ${quote(isoOf(job))} ] && finish exists")
            extractImage(job, "extractdvd -i ${quote(job.input)} -o ${quote(isoOf(job).substringAfterLast('/'))} -f", Engine.CHDMAN)
            appendLine("fi")
        }
        val temp = temp(job)
        val sheetName = job.title + ".cue"
        val binName = job.title + ".bin"
        val bin = quote("${job.folder}/$binName")
        appendLine("[ -e ${quote("${job.folder}/$sheetName")} ] && finish exists")
        appendLine("[ -e $bin ] && finish exists")
        roomFor(job)
        appendLine("mkdir -p ${quote(temp)} && cd ${quote(temp)} || finish \"folder 1\"")
        val arguments = "extractcd -i ${quote(job.input)} -o ${quote(sheetName)} -ob ${quote(binName)} -f"
        run("extract", arguments, null, cleanup = "rm -rf ${quote(temp)}")
        val moved = "mv ${quote("$temp/$binName")} ${quote(job.folder)}/ && mv ${quote("$temp/$sheetName")} ${quote(job.folder)}/"
        appendLine("$moved || { rm -rf ${quote(temp)}; finish \"move 1\"; }")
        appendLine("rmdir ${quote(temp)}")
        appendLine("cd \"\$W\"")
        appendLine("size=\$(stat -c %s $bin)")
        appendLine("echo finish > \"\$W/$PHASE\"")
        handleOriginals(job)
        appendLine("finish \"ok \$size\"")
    }

    /** Turns [job]'s compressed file back into one image (its `logical` size set before) with [arguments]. */
    private fun StringBuilder.extractImage(job: RomJob, arguments: String, engine: Engine) {
        val temp = temp(job)
        val iso = isoOf(job)
        val name = iso.substringAfterLast('/')
        roomFor(job)
        appendLine("mkdir -p ${quote(temp)} && cd ${quote(temp)} || finish \"folder 1\"")
        run("extract", arguments, null, cleanup = "rm -rf ${quote(temp)}", engine = engine)
        appendLine("mv ${quote("$temp/$name")} ${quote(job.folder)}/ || { rm -rf ${quote(temp)}; finish \"move 1\"; }")
        appendLine("rmdir ${quote(temp)}")
        appendLine("cd \"\$W\"")
        appendLine("size=\$(stat -c %s ${quote(iso)})")
        appendLine("echo finish > \"\$W/$PHASE\"")
        handleOriginals(job)
        appendLine("finish \"ok \$size\"")
    }

    /** Ends the job unless the storage has room for the image of `logical` bytes, plus [MARGIN]. */
    private fun StringBuilder.roomFor(job: RomJob) {
        // Bytes over a thousand: the shell counts only to about two billion, so a disc's size is cut to (a bit over) KB.
        appendLine("logical=\${logical%???}")
        spaceCheck(job.folder, "\$(( \${logical:-0} + ${MARGIN / KB} ))")
    }

    private fun temp(job: RomJob) = "${job.folder}/.thortools-${job.id}"

    private fun isoOf(job: RomJob) = "${job.folder}/${job.title}.iso"

    private fun StringBuilder.originalsOnly(job: RomJob) {
        if (job.format.tool == RomTool.NOD) {
            // The new file and, when it is still here, the image it was made from must give the same SHA-1.
            val image = job.originals.firstOrNull { original ->
                (listOf(RomFormat.ISO.extension) + job.console.otherImages).any { original.endsWith(".$it", ignoreCase = true) }
            }
            run("verify", "verify -j ${job.speed.processors} ${quote(job.input)}", null, engine = Engine.NOD)
            appendLine("sha1=${sha1Of("verify.out")}")
            if (image != null) {
                run("verify", "verify -j ${job.speed.processors} ${quote(image)}", null, engine = Engine.NOD)
                appendLine("sha2=${sha1Of("verify.out")}")
                appendLine("[ -n \"\$sha1\" ] && [ \"\$sha1\" = \"\$sha2\" ] || finish \"verify 1\"")
            } else {
                appendLine("[ -n \"\$sha1\" ] || finish \"verify 1\"")
            }
        } else if (job.format.tool == RomTool.MAXCSO) {
            val image = job.originals.firstOrNull { it.endsWith(".iso", ignoreCase = true) }
            if (image != null) crcCheck(image, quote(job.input), removeOnFail = false) else decodeCheck(quote(job.input))
        } else {
            verify(quote(job.input), removeOnFail = false)
        }
        appendLine("echo finish > \"\$W/$PHASE\"")
        handleOriginals(job)
        appendLine("finish \"ok \$(stat -c %s ${quote(job.input)})\"")
    }

    /**
     * Ends the job ("notdisc") unless [image] holds an ISO 9660 volume (`CD001` at the start of sector 16), so a file
     * that only looks like a disc image by its name and size is never compressed.
     */
    private fun StringBuilder.discCheck(image: String) {
        appendLine(
            "[ \"\$(dd if=${quote(image)} bs=2048 skip=16 count=1 2>/dev/null | head -c 6 | tail -c 5)\" = CD001 ] || finish notdisc",
        )
    }

    /**
     * Checks that a CSO or ZSO ([packed], already quoted) unpacks whole, when there is no image to compare it with (it
     * was made from another compressed file, which the job that made it compared it with).
     */
    private fun StringBuilder.decodeCheck(packed: String) {
        run("verify", "--crc $packed", null, engine = Engine.MAXCSO)
        appendLine("tr '\\r' '\\n' < \"\$W/$PROGRESS\" | grep -q 'CRC32: [0-9a-f]' || finish \"verify 1\"")
    }

    /**
     * Checks a CSO or ZSO ([packed], already quoted) against the image it was made from: maxcso reads both and their
     * CRC32s must be equal (the format keeps no checksum of its own).
     */
    private fun StringBuilder.crcCheck(image: String, packed: String, removeOnFail: Boolean) {
        val remove = if (removeOnFail) "rm -f $packed" else ":"
        val crc = "\$(tr '\\r' '\\n' < \"\$W/$PROGRESS\" | grep -o 'CRC32: [0-9a-f]*' | tail -n 1)"
        run("verify", "--crc ${quote(image)}", if (removeOnFail) packed else null, engine = Engine.MAXCSO)
        appendLine("crc1=$crc")
        run("verify", "--crc $packed", if (removeOnFail) packed else null, engine = Engine.MAXCSO)
        appendLine("crc2=$crc")
        appendLine("[ -n \"\$crc1\" ] && [ \"\$crc1\" = \"\$crc2\" ] || { $remove; finish \"verify 1\"; }")
    }

    /**
     * Ends the job when the storage of [folder] has less than [needKb] KB free. Everything is counted in KB: Android's
     * shell counts only to about two billion, so free bytes of a large card would wrap round to a negative number.
     */
    private fun StringBuilder.spaceCheck(folder: String, needKb: String) {
        appendLine("set -- \$(df -k ${quote(folder)} 2>/dev/null | tail -n 1)")
        appendLine("free=\${4:-0}")
        appendLine("[ \"\$free\" -ge $needKb ] 2>/dev/null || finish \"space \${free}k\"")
    }

    /**
     * Defines `engine`, run before each use of chdman: an update of Thor Tools moves chdman to a new folder and removes
     * the old one while a job may still be running. It then takes the path the restarted app wrote into [ENGINE], else
     * the installed copy, and waits up to [ENGINE_WAIT] seconds for the update to finish; it fails when there is none.
     */
    private fun StringBuilder.engineLookup(engine: Engine, path: String) {
        val v = engine.variable
        appendLine("${engine.function}() {")
        appendLine("  n=0")
        appendLine("  while [ ! -x \"\$$v\" ] && [ \$n -lt $ENGINE_WAIT ]; do")
        appendLine("    [ -r \"\$W/${engine.file}\" ] && $v=\$(cat \"\$W/${engine.file}\")")
        installedPattern(path)?.let { pattern ->
            appendLine("    [ -x \"\$$v\" ] || for f in $pattern; do [ -x \"\$f\" ] && $v=\$f; done")
        }
        appendLine("    [ -x \"\$$v\" ] || { sleep 1; n=\$((n + 1)); }")
        appendLine("  done")
        appendLine("  [ -x \"\$$v\" ]")
        appendLine("}")
    }

    /**
     * A shell pattern for [chdman]'s path after an update (the two random folder names in `/data/app` change), or null
     * when the path isn't an installed app's.
     */
    fun installedPattern(chdman: String): String? {
        val match = Regex("^/data/app/[^/]+/([A-Za-z0-9_.]+)-[^/]+/(lib/[A-Za-z0-9_]+/[A-Za-z0-9_.]+)$").find(chdman) ?: return null
        return "/data/app/*/${match.groupValues[1]}-*/${match.groupValues[2]}"
    }

    /**
     * Runs chdman with [arguments] under nice in the background so the app can pause or stop it, then checks how it
     * ended. A stop asked for before it starts ends the job at once.
     */
    private fun StringBuilder.run(
        phase: String,
        arguments: String,
        part: String?,
        cleanup: String? = null,
        engine: Engine = Engine.CHDMAN,
    ) {
        val remove = listOfNotNull(part?.let { "rm -f $it" }, cleanup).joinToString("; ").ifEmpty { ":" }
        appendLine("[ -e \"\$W/$CANCEL\" ] && { $remove; finish cancelled; }")
        appendLine("${engine.function} || { $remove; finish \"engine 1\"; }")
        appendLine("echo $phase > \"\$W/$PHASE\"")
        appendLine("nice -n 10 \"\$${engine.variable}\" $arguments > \"\$W/$phase.out\" 2> \"\$W/$PROGRESS\" &")
        appendLine("echo \$! > \"\$W/$CHILD\"")
        appendLine("wait \$!")
        appendLine("rc=\$?")
        appendLine("[ -e \"\$W/$CANCEL\" ] && { $remove; finish cancelled; }")
        appendLine("[ \$rc -eq 0 ] || { $remove; finish \"$phase \$rc\"; }")
    }

    private fun StringBuilder.verify(chd: String, removeOnFail: Boolean = true) {
        val remove = if (removeOnFail) "rm -f $chd" else ":"
        appendLine("[ -e \"\$W/$CANCEL\" ] && { $remove; finish cancelled; }")
        appendLine("engine || { $remove; finish \"engine 1\"; }")
        appendLine("echo verify > \"\$W/$PHASE\"")
        appendLine("nice -n 10 \"\$C\" verify -i $chd > \"\$W/verify.out\" 2> \"\$W/$PROGRESS\" &")
        appendLine("echo \$! > \"\$W/$CHILD\"")
        appendLine("wait \$!")
        appendLine("rc=\$?")
        appendLine("[ -e \"\$W/$CANCEL\" ] && { $remove; finish cancelled; }")
        appendLine(
            "[ \$rc -eq 0 ] && grep -q 'Overall SHA1 verification successful' \"\$W/verify.out\" || { $remove; finish \"verify \$rc\"; }",
        )
    }

    private fun StringBuilder.handleOriginals(job: RomJob) {
        when (job.choice) {
            Originals.KEEP -> return
            Originals.DELETE -> job.originals.forEach { appendLine("rm -f ${quote(it)}") }
            Originals.MOVE -> {
                val target = job.moveTo ?: return
                appendLine("mkdir -p ${quote(target)}")
                job.originals.forEach { original ->
                    val there = quote("$target/${original.substringAfterLast('/')}")
                    appendLine("[ -e $there ] || mv ${quote(original)} $there")
                }
            }
        }
        job.emptyFolder?.let { appendLine("rmdir ${quote(it)} 2>/dev/null") }
    }

    /** [job] as a file keeps it, so a job still running is found again after Thor Tools restarts. */
    fun encode(job: RomJob): String = buildString {
        appendLine("id\t${job.id}")
        appendLine("kind\t${job.kind.name}")
        appendLine("console\t${job.console.id}")
        appendLine("title\t${job.title}")
        appendLine("folder\t${job.folder}")
        appendLine("input\t${job.input}")
        appendLine("output\t${job.output}")
        job.originals.forEach { appendLine("original\t$it") }
        appendLine("choice\t${job.choice.name}")
        appendLine("speed\t${job.speed.name}")
        appendLine("inputBytes\t${job.inputBytes}")
        appendLine("estimate\t${job.estimate}")
        appendLine("need\t${job.need}")
        job.sbiFrom?.let { appendLine("sbiFrom\t$it") }
        job.sbiTo?.let { appendLine("sbiTo\t$it") }
        job.playlists.forEach { appendLine("playlist\t$it") }
        job.moveTo?.let { appendLine("moveTo\t$it") }
        job.sheet?.lines()?.forEach { appendLine("sheet\t$it") }
        job.links.forEach { (name, target) -> appendLine("link\t$name\t$target") }
        job.emptyFolder?.let { appendLine("emptyFolder\t$it") }
        appendLine("format\t${job.format.name}")
        job.from?.let { appendLine("from\t${it.name}") }
        if (job.temp > 0) appendLine("temp\t${job.temp}")
    }

    fun decode(text: String): RomJob? = runCatching {
        val values = mutableMapOf<String, String>()
        val originals = mutableListOf<String>()
        val playlists = mutableListOf<String>()
        val sheet = mutableListOf<String>()
        val links = LinkedHashMap<String, String>()
        text.lineSequence().forEach { line ->
            val key = line.substringBefore('\t')
            val value = line.substringAfter('\t', "")
            when (key) {
                "original" -> originals += value
                "playlist" -> playlists += value
                "sheet" -> sheet += value
                "link" -> links[value.substringBefore('\t')] = value.substringAfter('\t')
                "" -> Unit
                else -> values[key] = value
            }
        }
        RomJob(
            id = values.getValue("id").toLong(),
            kind = JobKind.valueOf(values.getValue("kind")),
            console = RomConsole.byId(values["console"]) ?: return null,
            title = values.getValue("title"),
            folder = values.getValue("folder"),
            input = values.getValue("input"),
            output = values.getValue("output"),
            originals = originals,
            choice = Originals.valueOf(values.getValue("choice")),
            speed = RomSpeed.valueOf(values.getValue("speed")),
            inputBytes = values["inputBytes"]?.toLongOrNull() ?: 0L,
            estimate = values["estimate"]?.toLongOrNull() ?: 0L,
            need = values["need"]?.toLongOrNull() ?: 0L,
            sbiFrom = values["sbiFrom"],
            sbiTo = values["sbiTo"],
            playlists = playlists,
            moveTo = values["moveTo"],
            sheet = sheet.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            links = links,
            emptyFolder = values["emptyFolder"],
            format = values["format"]?.let { runCatching { RomFormat.valueOf(it) }.getOrNull() } ?: RomFormat.CHD_CD,
            from = values["from"]?.let { runCatching { RomFormat.valueOf(it) }.getOrNull() },
            temp = values["temp"]?.toLongOrNull() ?: 0L,
        )
    }.getOrNull()

    /** The waiting jobs as their file keeps them, so the queue goes on after Thor Tools restarts. */
    fun encodeQueue(jobs: List<RomJob>): String = jobs.joinToString("") { "$NEXT_JOB\n" + encode(it) }

    fun decodeQueue(text: String): List<RomJob> = text.split("$NEXT_JOB\n").filter { it.isNotBlank() }.mapNotNull(::decode)
}

/** Builds the jobs for a game. */
object RomJobs {
    /** Where moved originals go, on the same storage as the game but outside the ROM folders. */
    const val KEPT_FOLDER = "Originals (Thor Tools)"

    /**
     * Compressing [game] into [format] (by default the first its console makes for its kind of disc); [sheetText] is the
     * text of its sheet, needed when its tracks lie apart from it.
     */
    fun compress(
        id: Long,
        game: DiscGame,
        choice: Originals,
        speed: RomSpeed,
        estimate: Estimate,
        romFolder: String?,
        sheetText: String? = null,
        format: RomFormat? = null,
    ): RomJob? {
        val main = game.main ?: return null
        if (!game.canCompress) return null
        val kind = game.kind ?: DiscKind.CD
        val made = format ?: game.targets.firstOrNull() ?: return null
        if (made.disc != kind || made !in game.targets || made.tool == RomTool.NONE) return null
        val sbiTo = "${game.folder}/${game.title}.sbi"
        val sbiFrom = game.sbi?.takeIf { !it.equals(sbiTo, ignoreCase = true) }
        val (sheet, links) = when {
            kind == DiscKind.FOLDER -> null to game.titles
            kind != DiscKind.CD -> null to emptyMap()
            game.lone -> CueSheet.forLoneImage(RomJobPlan.LONE_IMAGE) to mapOf(RomJobPlan.LONE_IMAGE to main)
            game.elsewhere -> {
                val text = sheetText ?: return null
                CueSheet.withPlainNames(text) to game.files.filter { it.path != main }.associate { it.name to it.path }
            }
            else -> null to emptyMap()
        }
        return RomJob(
            id = id,
            kind = JobKind.COMPRESS,
            console = game.console,
            title = game.title,
            folder = game.folder,
            input = main,
            output = game.output(made),
            originals = if (kind == DiscKind.FOLDER) game.titles.values.toList() else game.files.map { it.path },
            choice = choice,
            speed = speed,
            inputBytes = game.originalSize,
            estimate = estimate.middle,
            need = estimate.high + RomJobPlan.MARGIN,
            sbiFrom = sbiFrom,
            sbiTo = sbiFrom?.let { sbiTo },
            playlists = game.playlists,
            moveTo = keptFolder(game.folder, romFolder),
            sheet = sheet,
            links = links,
            emptyFolder = main.substringBeforeLast('/').takeIf { game.elsewhere },
            format = made,
        )
    }

    /**
     * Turning [game]'s compressed file into [target] (another format, or a plain ISO) through an image unpacked beside
     * it; the compressed file is the original the [choice] applies to.
     */
    fun convert(
        id: Long,
        game: DiscGame,
        target: RomFormat,
        choice: Originals,
        speed: RomSpeed,
        estimate: Estimate,
        romFolder: String?,
    ): RomJob? {
        val packed = game.compressed ?: return null
        if (!game.canConvert || target !in game.targets) return null
        // A CHD whose header wasn't read counts as a DVD image; the job checks it before unpacking.
        val from = game.packed ?: RomFormat.CHD_DVD
        val image = game.imageSize ?: (packed.size * 2)
        if (game.kind == DiscKind.FOLDER) {
            // A WUA unpacks into the job's folder, then each title folder moves beside it.
            val unpacked = game.imageSize ?: return null
            if (game.titles.isEmpty()) return null
            return RomJob(
                id = id,
                kind = JobKind.COMPRESS,
                console = game.console,
                title = game.title,
                folder = game.folder,
                input = packed.path,
                output = game.output(target),
                originals = listOf(packed.path),
                choice = choice,
                speed = speed,
                inputBytes = packed.size,
                estimate = estimate.middle,
                need = unpacked + RomJobPlan.MARGIN,
                moveTo = keptFolder(game.folder, romFolder),
                links = game.titles,
                format = target,
                from = game.packed,
                temp = unpacked,
            )
        }
        if (game.kind == DiscKind.CART) {
            // The archive is unpacked into the job's folder first: the ROM's size is taken there too.
            val rom = game.imageSize ?: return null
            if (target == RomFormat.ROM_FILE && game.inner == null) return null
            return RomJob(
                id = id,
                kind = JobKind.COMPRESS,
                console = game.console,
                title = game.title,
                folder = game.folder,
                input = packed.path,
                output = game.output(target),
                originals = listOf(packed.path),
                choice = choice,
                speed = speed,
                inputBytes = packed.size,
                estimate = estimate.middle,
                need = rom + (if (target.tool == RomTool.NONE) 0L else estimate.high) + RomJobPlan.MARGIN,
                moveTo = keptFolder(game.folder, romFolder),
                format = target,
                from = game.packed,
                temp = rom,
            )
        }
        if (game.console.check == DiscCheck.NINTENDO) {
            // nod reads every format: the new file is made straight from the old one, no image in between.
            val made = if (target.tool == RomTool.NONE) image else estimate.high
            return RomJob(
                id = id,
                kind = JobKind.COMPRESS,
                console = game.console,
                title = game.title,
                folder = game.folder,
                input = packed.path,
                output = game.output(target),
                originals = listOf(packed.path) + game.parts,
                choice = choice,
                speed = speed,
                inputBytes = packed.size,
                estimate = estimate.middle,
                need = made + RomJobPlan.MARGIN,
                playlists = game.playlists,
                moveTo = keptFolder(game.folder, romFolder),
                format = target,
                from = from,
                temp = if (target.tool == RomTool.NONE) image else 0L,
            )
        }
        return RomJob(
            id = id,
            kind = JobKind.COMPRESS,
            console = game.console,
            title = game.title,
            folder = game.folder,
            input = packed.path,
            output = game.output(target),
            originals = listOf(packed.path),
            choice = choice,
            speed = speed,
            inputBytes = packed.size,
            estimate = estimate.middle,
            need = image + (if (target.tool == RomTool.NONE) 0L else estimate.high) + RomJobPlan.MARGIN,
            moveTo = keptFolder(game.folder, romFolder),
            format = target,
            from = from,
            temp = image,
        )
    }

    fun extract(id: Long, game: DiscGame, choice: Originals, speed: RomSpeed, romFolder: String?): RomJob? {
        val chd = game.compressed ?: return null
        if (game.main != null) return null
        if (game.console.check == DiscCheck.NINTENDO) {
            val image = game.imageSize ?: return null
            return convert(id, game, RomFormat.ISO, choice, speed, Estimate(image, image, true), romFolder)
        }
        if (game.kind == DiscKind.CART) {
            val rom = game.imageSize ?: return null
            return convert(id, game, RomFormat.ROM_FILE, choice, speed, Estimate(rom, rom, true), romFolder)
        }
        if (game.kind == DiscKind.FOLDER) {
            val unpacked = game.imageSize ?: return null
            return convert(id, game, RomFormat.FOLDERS, choice, speed, Estimate(unpacked, unpacked, true), romFolder)
        }
        val image = game.kind == DiscKind.DVD || game.packed?.tool == RomTool.MAXCSO || (game.console.takesIso && game.kind == null)
        return RomJob(
            id = id,
            kind = JobKind.EXTRACT,
            console = game.console,
            title = game.title,
            folder = game.folder,
            input = chd.path,
            output = "${game.folder}/${game.title}.${if (image) "iso" else "cue"}",
            originals = listOf(chd.path),
            choice = choice,
            speed = speed,
            inputBytes = chd.size,
            estimate = 0L,
            need = 0L,
            moveTo = keptFolder(game.folder, romFolder),
            format = game.packed ?: RomFormat.CHD_CD,
        )
    }

    fun originals(id: Long, game: DiscGame, choice: Originals, speed: RomSpeed, romFolder: String?): RomJob? {
        val chd = game.compressed ?: return null
        val main = game.main ?: return null
        if (!game.originalsLeft || choice == Originals.KEEP) return null
        return RomJob(
            id = id,
            kind = JobKind.ORIGINALS,
            console = game.console,
            title = game.title,
            folder = game.folder,
            input = chd.path,
            output = chd.path,
            originals = if (game.kind == DiscKind.FOLDER) game.titles.values.toList() else game.files.map { it.path },
            choice = choice,
            speed = speed,
            inputBytes = chd.size,
            estimate = 0L,
            need = 0L,
            moveTo = keptFolder(game.folder, romFolder),
            links = if (game.kind == DiscKind.FOLDER) game.titles else emptyMap(),
            emptyFolder = main.substringBeforeLast('/').takeIf { game.elsewhere },
            format = game.packed ?: RomFormat.CHD_CD,
        )
    }

    /**
     * Where [folder]'s originals are moved: [KEPT_FOLDER] at the top of the same storage, then the same folders as
     * under the ROM folder (`/storage/X/Roms/Ps1/Game` → `/storage/X/Originals (Thor Tools)/Ps1/Game`).
     */
    fun keptFolder(folder: String, romFolder: String?): String {
        val inside = if (romFolder != null &&
            folder.startsWith("$romFolder/")
        ) {
            folder.removePrefix("$romFolder/")
        } else {
            folder.substringAfterLast('/')
        }
        return "${volumeRoot(folder)}/$KEPT_FOLDER/$inside"
    }

    /** The top of the storage [path] is on. */
    fun volumeRoot(path: String): String = when {
        path == SHARED || path.startsWith("$SHARED/") -> SHARED
        path.startsWith("/storage/") -> "/storage/" + path.removePrefix("/storage/").substringBefore('/')
        else -> path.substringBeforeLast('/')
    }

    private const val SHARED = "/storage/emulated/0"
}
