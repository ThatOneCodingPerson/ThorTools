package io.github.thatonecodingperson.thortools.retroarch

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.BuildConfig
import java.io.File
import javax.inject.Inject

/** A repository's listing: the branch or tag read ([ref]), every file in it, and whether the host cut the listing short. */
data class LinkListing(val ref: String, val files: Set<String>, val truncated: Boolean)

/**
 * A link's presets fetched: their [files] by path (names into libretro's collection pointed at the library) and the
 * library's files they name ([library]).
 */
data class LinkFetch(val files: Map<String, ByteArray>, val library: Set<String>)

/**
 * Shaders from links: a repository's listing from its host, a link's presets fetched with everything they pull in (kept
 * in the app's files, so a second look downloads nothing), and installed as root into RetroArch's shader folder on
 * storage, `.links/<link>/` keeping their paths, with shortcuts into the sorted folders. Blocking: never on the main
 * thread.
 */
class ShaderLinkFiles @Inject constructor(@ApplicationContext private val context: Context, private val library: ShaderLibrary) {
    private val dir by lazy { File(context.filesDir, DIR) }
    private val http = CheatHttp("ThorTools/${BuildConfig.VERSION_NAME} (RetroArch assistant)")

    /** The whole listing of [link]'s repository at its branch (the default one when the link names none). */
    fun list(link: ShaderLink.Repo): LinkListing {
        val ref = link.ref
            ?: ShaderLinks.defaultBranch(http.get(ShaderLinks.repoApi(link), accept = JSON).body.decodeToString())
            ?: throw CheatProblem.Failed("no default branch")
        var url: String? = ShaderLinks.treeApi(link, ref)
        val files = mutableSetOf<String>()
        var truncated = false
        var pages = 0
        while (url != null && pages < MAX_PAGES) {
            val answer = http.get(url, accept = JSON)
            val text = answer.body.decodeToString()
            if (link.host == GitHost.GITHUB && ShaderLinks.truncated(text)) truncated = true
            val page = ShaderLinks.parseTree(link.host, text, answer.link, url)
            files += page.files
            url = page.next
            pages++
        }
        return LinkListing(ref, files, truncated || url != null)
    }

    /**
     * [presets] of [link] (paths in its repository, or on its site for a direct link) and every file they pull in, as
     * paths with their bytes; for a repository only files its listing ([files]) has are followed. Files of libretro's
     * collection in [format] that a file names and the link hasn't are not fetched: the name points at the library.
     */
    fun fetch(link: ShaderLink, ref: String?, presets: List<String>, files: Set<String>?, format: ShaderFormat): LinkFetch {
        val fetched = LinkedHashMap<String, ByteArray>()
        val library = mutableSetOf<String>()
        val queue = ArrayDeque(presets)
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            if (path in fetched) continue
            check(fetched.size < MAX_FILES) { "too many files" }
            var bytes = fileOf(link, ref, path)
            if (ShaderDeps.isText(path)) {
                val installed = "${ShaderShortcuts.LINKS_LIBRARY}/${link.key}/$path"
                val (text, used) = LibraryRefs.point(path, bytes.decodeToString(), installed, format, files)
                if (used.isNotEmpty()) bytes = text.encodeToByteArray()
                library += used
                queue += ShaderDeps.of(path, text).filter { it !in fetched && (files == null || it in files) }
            }
            fetched[path] = bytes
        }
        return LinkFetch(fetched, library)
    }

    /** The files the preset at [path] of [link] names, as written (the preset is kept, so adding it later fetches nothing). */
    fun names(link: ShaderLink, ref: String?, path: String): List<String> = ShaderDeps.named(path, fileOf(link, ref, path).decodeToString())

    /**
     * Copies [fetched] (none when everything is on the Thor already) into `.links/<link>/` in RetroArch's shader folder,
     * then writes [shortcuts].
     */
    fun install(places: ShaderPlaces, link: ShaderLink, fetched: Map<String, ByteArray>, shortcuts: List<Shortcut>): Result<Unit> =
        runCatching {
            if (fetched.isNotEmpty()) {
                val stage = File(context.cacheDir, STAGE).apply { deleteRecursively() }
                val root = "${ShaderShortcuts.LINKS_LIBRARY}/${link.key}"
                val staged = fetched.map { (path, bytes) ->
                    val relative = "$root/$path"
                    val file = File(stage, relative)
                    check(file.canonicalPath.startsWith(stage.canonicalPath + "/")) { "bad path: $path" }
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                    relative
                }
                library.copyStaged(stage, staged, places.root) { _, _ -> }
                stage.deleteRecursively()
            }
            library.write(places, shortcuts).getOrThrow()
            Log.i(TAG, "Added ${shortcuts.size} shortcuts from ${link.label}: ${fetched.size} files")
        }

    /** Forgets the files kept for a link ([key]) once they are gone from RetroArch's shader folder. */
    fun forget(key: String) {
        File(dir, key).takeIf { it.canonicalPath.startsWith(dir.canonicalPath + "/") }?.deleteRecursively()
    }

    private fun fileOf(link: ShaderLink, ref: String?, path: String): ByteArray {
        val kept = File(dir, "${link.key}/${ref.orEmpty()}/$path")
        if (kept.isFile) return kept.readBytes()
        val url = when (link) {
            is ShaderLink.Repo -> ShaderLinks.rawUrl(link, ref ?: "HEAD", path)
            is ShaderLink.Direct -> ShaderLinks.directUrl(link, path)
        }
        val bytes = http.get(url).body
        runCatching {
            kept.parentFile?.mkdirs()
            kept.writeBytes(bytes)
        }
        return bytes
    }

    private companion object {
        const val TAG = "ShaderLinks"
        const val DIR = "retroarch-shaders/links"
        const val STAGE = "shader-stage-links"
        const val JSON = "application/json"

        /** Beyond any shader repository's listing (GitLab and Codeberg page 100 files at a time). */
        const val MAX_PAGES = 100

        /** Beyond what the presets of one link pull in. */
        const val MAX_FILES = 2000
    }
}
