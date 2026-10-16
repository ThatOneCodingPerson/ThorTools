package io.github.thatonecodingperson.thortools.roms

/** A track of a cue sheet: its number, its type as written (`MODE2/2352`, `AUDIO`) and the file it is in. */
data class CueTrack(val number: Int, val type: String, val file: String) {
    val audio: Boolean get() = type.equals("AUDIO", ignoreCase = true)
}

/** A cue sheet: the files it names, in order, and its tracks. */
data class CueSheet(val files: List<String>, val tracks: List<CueTrack>) {
    /** True when every track in [file] is an audio track. */
    fun isAudio(file: String): Boolean = tracks.filter { it.file == file }.let { inFile -> inFile.isNotEmpty() && inFile.all { it.audio } }

    companion object {
        /** A sector of a raw CD image. */
        const val SECTOR = 2352L

        private val FILE = Regex("""^\s*FILE\s+(?:"([^"]*)"|(\S+))(?:\s+(\S+))?\s*$""", RegexOption.IGNORE_CASE)
        private val TRACK = Regex("""^\s*TRACK\s+(\d+)\s+(\S+)\s*$""", RegexOption.IGNORE_CASE)

        /** The sheet in [text]; null when it names no file or has no track. */
        fun parse(text: String): CueSheet? {
            val files = mutableListOf<String>()
            val tracks = mutableListOf<CueTrack>()
            var current: String? = null
            text.removePrefix("﻿").lineSequence().forEach { line ->
                FILE.matchEntire(line)?.let { match ->
                    val name = match.groupValues[1].ifEmpty { match.groupValues[2] }.replace('\\', '/').substringAfterLast('/')
                    if (name.isNotEmpty()) {
                        current = name
                        if (name !in files) files += name
                    }
                    return@forEach
                }
                TRACK.matchEntire(line)?.let { match ->
                    val file = current ?: return@forEach
                    tracks += CueTrack(match.groupValues[1].toInt(), match.groupValues[2].uppercase(), file)
                }
            }
            if (files.isEmpty() || tracks.isEmpty()) return null
            return CueSheet(files, tracks)
        }

        /** [text] with each FILE line naming its file without any folder, for a copy of the sheet beside links to them. */
        fun withPlainNames(text: String): String = text.removePrefix("﻿").lines().joinToString("\n") { line ->
            val match = FILE.matchEntire(line) ?: return@joinToString line
            val name = match.groupValues[1].ifEmpty { match.groupValues[2] }.replace('\\', '/').substringAfterLast('/')
            "FILE \"$name\" ${match.groupValues[3].ifEmpty { "BINARY" }}"
        }

        /** A one-track sheet for a lone raw image named [fileName] (a PS1 disc: `MODE2/2352`). */
        fun forLoneImage(fileName: String): String = "FILE \"$fileName\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n"

        /** True when [size] bytes can be a raw CD image. */
        fun isRawImage(size: Long): Boolean = size > 0 && size % SECTOR == 0L
    }
}
