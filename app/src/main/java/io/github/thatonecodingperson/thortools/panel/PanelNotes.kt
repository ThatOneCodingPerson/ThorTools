package io.github.thatonecodingperson.thortools.panel

import android.content.Context
import java.io.File
import java.util.UUID

/** A Notes widget's content: text typed in Thor Tools, and what was drawn on the widget. */
data class PanelNote(val text: String = "", val strokes: List<NoteStroke> = emptyList())

/** The Notes widgets' files in the app's own storage, one text and one drawing file per note. Disk: never the main thread. */
class PanelNotes(context: Context) {
    private val dir = File(context.filesDir, "panel_notes")

    fun load(id: String): PanelNote {
        if (!valid(id)) return PanelNote()
        return PanelNote(
            text = runCatching { File(dir, "$id.txt").readText() }.getOrDefault(""),
            strokes = NoteStrokes.decode(runCatching { File(dir, "$id.strokes").readText() }.getOrNull()),
        )
    }

    fun saveText(id: String, text: String) = write(id, "txt", text.take(MAX_TEXT))

    fun saveStrokes(id: String, strokes: List<NoteStroke>) = write(id, "strokes", NoteStrokes.encode(strokes))

    private fun write(id: String, extension: String, content: String) {
        if (!valid(id)) return
        runCatching {
            dir.mkdirs()
            File(dir, "$id.$extension").writeText(content)
        }
    }

    companion object {
        const val MAX_TEXT = 2000

        /** A name for a new note's files. */
        fun newId(): String = "n" + UUID.randomUUID().toString().replace("-", "").take(ID_LENGTH)

        private fun valid(id: String) = id.isNotEmpty() && id.all { it.isLetterOrDigit() }

        private const val ID_LENGTH = 8
    }
}
