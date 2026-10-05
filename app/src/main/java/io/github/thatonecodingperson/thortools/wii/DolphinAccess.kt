package io.github.thatonecodingperson.thortools.wii

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Dolphin's folder through Dolphin's own document provider, with the folder access the user gives once in Android's
 * folder picker. Dolphin's process does the reading and writing, so a profile lands in the folder Dolphin really uses,
 * with Dolphin's own owner, and no root is needed. Every call here goes into another app: never on the main thread.
 * Reads throw when Dolphin can't be reached, so a caller can try root instead.
 */
class DolphinAccess @Inject constructor(@ApplicationContext private val context: Context) {
    private val resolver get() = context.contentResolver

    /** The folder access the user gave for Dolphin's provider [authority], or null. */
    fun granted(authority: String): Uri? = runCatching {
        resolver.persistedUriPermissions.firstOrNull {
            it.uri.authority == authority && it.isReadPermission && it.isWritePermission && DocumentsContract.isTreeUri(it.uri)
        }?.uri
    }.getOrNull()

    /**
     * Keeps the access to [tree] when it is Dolphin's top folder (the one with Config in it). Any other folder isn't
     * kept, and the picker's one-off access to it lapses on its own.
     */
    fun accept(tree: Uri, authority: String): Boolean {
        if (tree.authority != authority || !DocumentsContract.isTreeUri(tree)) return false
        val top = runCatching { child(tree, topId(tree), CONFIG) != null }.getOrDefault(false)
        if (top) runCatching { resolver.takePersistableUriPermission(tree, FLAGS) }.onFailure { return false }
        return top
    }

    fun release(tree: Uri) {
        runCatching { resolver.releasePersistableUriPermission(tree, FLAGS) }
    }

    /** Writes the profile (replacing one of the same name), then reads it back and looks for it in the folder. */
    fun save(tree: Uri, fileName: String, ini: String): DolphinSave = runCatching {
        val folder = folder(tree, create = true) ?: error("no profile folder")
        val name = "$fileName$INI"
        val bytes = ini.toByteArray()
        // Dolphin numbers a new file whose name is taken, so an existing profile is written over instead.
        val document = child(tree, folder, name)?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.id) }
            ?: DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, folder), MIME_INI, name)
            ?: error("Dolphin didn't create the file")
        checkNotNull(resolver.openOutputStream(document, "wt")) { "Dolphin didn't open the file" }.use { it.write(bytes) }
        val back = checkNotNull(resolver.openInputStream(document)) { "Dolphin didn't open the file" }.use { it.readBytes() }
        check(back.contentEquals(bytes)) { "the file read back differs" }
        DolphinSave.Saved(SaveRoute.DOLPHIN_ACCESS, listed = child(tree, folder, name)?.size == bytes.size.toLong())
    }.getOrElse { DolphinSave.Failed(SaveRoute.DOLPHIN_ACCESS, it.message ?: it.javaClass.simpleName) }

    /** The Wii Remote profiles in Dolphin, without `.ini`; null when Dolphin has no profile folder yet. */
    fun profiles(tree: Uri): Set<String>? {
        val folder = folder(tree, create = false) ?: return null
        return children(tree, folder).map { it.name }.filter { it.endsWith(INI) }.map { it.removeSuffix(INI) }.toSet()
    }

    /** Dolphin's own Wii Remote setup, for the device line it uses. */
    fun wiimoteIni(tree: Uri): String? {
        val config = child(tree, topId(tree), CONFIG) ?: return null
        val file = child(tree, config.id, WIIMOTE_INI) ?: return null
        return resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, file.id))?.use { it.readBytes().decodeToString() }
    }

    private data class Entry(val id: String, val name: String, val size: Long)

    private fun topId(tree: Uri): String = DocumentsContract.getTreeDocumentId(tree)

    private fun children(tree: Uri, parent: String): List<Entry> {
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE)
        val cursor = resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent), columns, null, null, null)
        return checkNotNull(cursor) { "Dolphin didn't answer" }.use {
            buildList {
                while (it.moveToNext()) add(Entry(it.getString(0), it.getString(1), if (it.isNull(2)) -1 else it.getLong(2)))
            }
        }
    }

    private fun child(tree: Uri, parent: String, name: String): Entry? = children(tree, parent).firstOrNull { it.name == name }

    /** The profile folder's document id, found by name from the top; with [create], missing folders are made. */
    private fun folder(tree: Uri, create: Boolean): String? {
        var id = topId(tree)
        PROFILE_PATH.forEach { name ->
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, id)
            id = child(tree, id, name)?.id
                ?: if (create) {
                    DocumentsContract.createDocument(resolver, parent, Document.MIME_TYPE_DIR, name)
                        ?.let(DocumentsContract::getDocumentId)
                        ?: error("Dolphin didn't make $name")
                } else {
                    return null
                }
        }
        return id
    }

    private companion object {
        const val CONFIG = "Config"
        val PROFILE_PATH = listOf(CONFIG, "Profiles", "Wiimote")
        const val WIIMOTE_INI = "WiimoteNew.ini"
        const val INI = ".ini"
        const val MIME_INI = "application/octet-stream"
        const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
