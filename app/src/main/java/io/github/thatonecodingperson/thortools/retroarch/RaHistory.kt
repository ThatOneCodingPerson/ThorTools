package io.github.thatonecodingperson.thortools.retroarch

import java.io.ByteArrayOutputStream

/**
 * A game RetroArch started, from its history: [path] the file as the Files app shows it (an archive's own path for
 * `game.zip#inner.nes`), [contentName] the name RetroArch names its saves, states and cheat file after (the inner file's
 * for such an archive), and [core] the core's file stem (`mesen2_libretro`), null when the entry names none.
 */
data class HistoryEntry(val path: String, val contentName: String, val core: String?) {
    val name: String get() = path.substringAfterLast('/')
}

/** RetroArch's content history (`content_history.lpl`): which games it started, newest first, and with which core. */
object RaHistory {
    private const val SAF = "saf://"
    private const val CONTENT = "content://"
    private const val TREE = "/tree/"
    private const val CORE_SUFFIX = "_android.so"

    /** The entries of the history file [json], newest first; empty when it isn't one. */
    fun parse(json: String): List<HistoryEntry> {
        val items = (runCatching { TinyJson.parse(json) }.getOrNull() as? Map<*, *>)?.get("items") as? List<*> ?: return emptyList()
        return items.filterIsInstance<Map<*, *>>().mapNotNull { item ->
            val raw = (item["path"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val archive = raw.substringBefore('#')
            val path = shownPath(archive) ?: return@mapNotNull null
            val inner = raw.substringAfter('#', "").substringAfterLast('/').takeIf { it.isNotEmpty() }
            HistoryEntry(path, inner ?: path.substringAfterLast('/'), (item["core_path"] as? String)?.let(::coreStem))
        }
    }

    /**
     * A content path from the history as the Files app shows it. RetroArch writes a game opened through Android's folder
     * picker as `saf://` + the tree's URI encoded once + `/` + the path inside the tree; null for a tree of another app's
     * provider, which has no path.
     */
    fun shownPath(path: String): String? {
        if (!path.startsWith(SAF)) return BiosPaths.shown(path)
        val rest = path.removePrefix(SAF)
        val uri = percentDecode(rest.substringBefore('/'))
        val inside = rest.substringAfter('/', "").trim('/')
        if (!uri.startsWith(CONTENT) || TREE !in uri) return null
        val authority = uri.removePrefix(CONTENT).substringBefore('/')
        val treeId = percentDecode(uri.substringAfter(TREE).substringBefore('/'))
        val base = BiosPaths.fromTree(authority, treeId) ?: return null
        return if (inside.isEmpty()) base else "$base/$inside"
    }

    /** The core's file stem in a `core_path`: `.../cores/mesen2_libretro_android.so` → `mesen2_libretro`. */
    fun coreStem(corePath: String): String? = corePath.substringAfterLast('/').takeIf { it.endsWith(CORE_SUFFIX) }
        ?.removeSuffix(CORE_SUFFIX)?.ifEmpty { null }

    /** `%XX` sequences as UTF-8 bytes; everything else, a `+` too, stays as it is. */
    private fun percentDecode(text: String): String {
        if ('%' !in text) return text
        val bytes = ByteArrayOutputStream()
        var at = 0
        while (at < text.length) {
            val pair = text.substring(at + 1, minOf(at + 3, text.length))
            if (text[at] == '%' && pair.length == 2 && pair.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) {
                bytes.write(pair.toInt(HEX))
                at += 3
            } else {
                val end = text.indexOf('%', at + 1).takeIf { it >= 0 } ?: text.length
                bytes.write(text.substring(at, end).toByteArray(Charsets.UTF_8))
                at = end
            }
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private const val HEX = 16
}
