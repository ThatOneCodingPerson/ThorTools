package io.github.thatonecodingperson.thortools.roms

/** What chdman is doing, from its progress lines: the phase, how far (percent) and, while compressing, the ratio so far. */
data class ChdProgress(val phase: Phase, val percent: Double, val ratio: Double? = null) {
    enum class Phase { COMPRESSING, VERIFYING, EXTRACTING, DONE }
}

/**
 * chdman's command lines and the progress chdman, maxcso (each line ended by a carriage return), nod's nodtool and 7-Zip
 * (`  12% + name`, wiped with backspaces) print on stderr.
 */
object ChdTool {
    /** The program inside the app's native library folder. */
    const val FILE = "libchdman.so"

    /** maxcso, which makes CSO and ZSO files, beside it. */
    const val MAXCSO_FILE = "libmaxcso.so"

    /** nod's nodtool, which reads and makes GameCube and Wii files, beside it. */
    const val NOD_FILE = "libnodtool.so"

    /** 7-Zip's 7zz, which makes the classic consoles' ZIP and 7z files, beside it. */
    const val SEVENZIP_FILE = "lib7zz.so"

    /** The z3ds tool, which makes and unpacks the 3DS games' Z3DS files, beside it. */
    const val Z3DS_FILE = "libz3ds.so"

    /** The wua tool, which packs Wii U game folders into WUA files and unpacks them, beside it. */
    const val WUA_FILE = "libwua.so"

    private val COMPRESSING = Regex("""Compressing,\s+([\d.]+)% complete\.\.\.\s*\(ratio=([\d.]+)%\)""")
    private val VERIFYING = Regex("""Verifying,\s+([\d.]+)% complete""")
    private val EXTRACTING = Regex("""Extracting,\s+([\d.]+)% complete""")
    private val COMPLETE = Regex("""Compression complete \.\.\. final ratio = ([\d.]+)%""")
    private val MAXCSO = Regex("""(\d+)%, ratio=\s*(\d+)%""")
    private val MAXCSO_DONE = Regex("""(\d+) -> (\d+) bytes \((\d+)%\)""")
    private val NOD = Regex("""^progress ([\d.]+)% ratio ([\d.]+)%""")
    private val SEVENZIP = Regex("""^(\d{1,3})%(\s|$)""")

    fun createCd(sheet: String, output: String, processors: Int?): List<String> =
        listOf("createcd", "-i", sheet, "-o", output) + (processors?.let { listOf("-np", it.toString()) } ?: emptyList())

    fun verify(chd: String): List<String> = listOf("verify", "-i", chd)

    fun extractCd(chd: String, sheet: String, binary: String): List<String> = listOf("extractcd", "-i", chd, "-o", sheet, "-ob", binary)

    /** The newest progress in [text] (everything chdman wrote so far); null when there is none yet. */
    fun progress(text: String): ChdProgress? {
        val last = text.split('\r', '\n', '\b').map { it.trim() }.lastOrNull {
            it.contains("complete") ||
                MAXCSO.containsMatchIn(it) ||
                MAXCSO_DONE.containsMatchIn(it) ||
                NOD.containsMatchIn(it) ||
                SEVENZIP.containsMatchIn(it)
        } ?: return null
        NOD.find(last)?.let {
            return ChdProgress(ChdProgress.Phase.COMPRESSING, it.groupValues[1].toDouble(), it.groupValues[2].toDoubleOrNull())
        }
        COMPLETE.find(last)?.let { return ChdProgress(ChdProgress.Phase.DONE, 100.0, it.groupValues[1].toDoubleOrNull()) }
        MAXCSO_DONE.find(last)?.let { return ChdProgress(ChdProgress.Phase.DONE, 100.0, it.groupValues[3].toDoubleOrNull()) }
        MAXCSO.find(last)?.let {
            return ChdProgress(ChdProgress.Phase.COMPRESSING, it.groupValues[1].toDouble(), it.groupValues[2].toDoubleOrNull())
        }
        COMPRESSING.find(last)?.let {
            return ChdProgress(ChdProgress.Phase.COMPRESSING, it.groupValues[1].toDouble(), it.groupValues[2].toDoubleOrNull())
        }
        VERIFYING.find(last)?.let { return ChdProgress(ChdProgress.Phase.VERIFYING, it.groupValues[1].toDouble()) }
        EXTRACTING.find(last)?.let { return ChdProgress(ChdProgress.Phase.EXTRACTING, it.groupValues[1].toDouble()) }
        SEVENZIP.find(last)?.let { return ChdProgress(ChdProgress.Phase.COMPRESSING, it.groupValues[1].toDouble()) }
        return null
    }

    /** The final ratio chdman printed, in percent; null when it didn't finish. */
    fun finalRatio(text: String): Double? = COMPLETE.findAll(text).lastOrNull()?.groupValues?.get(1)?.toDoubleOrNull()

    /** True when `chdman verify` printed that every check passed. */
    fun verified(output: String): Boolean = "Overall SHA1 verification successful" in output
}
