package io.github.thatonecodingperson.thortools.retroarch

import java.net.URI
import java.net.URLEncoder

/** A git host Thor Tools reads shader repositories from. [id] goes into folder names, so it must never change. */
enum class GitHost(val id: String, val site: String) {
    GITHUB("github", "github.com"),
    GITLAB("gitlab", "gitlab.com"),
    CODEBERG("codeberg", "codeberg.org"),
}

/** A link the user gave: a repository (or a folder or file in it), or a preset file anywhere on the web. */
sealed class ShaderLink {
    /** Folder name of the link's files in the library, and its name in "From links". */
    abstract val key: String
    abstract val label: String

    /**
     * A repository on [host]: [owner] (GitLab: the whole group path), [repo], the branch or tag named in the link ([ref],
     * null for the default branch) and the folder or file inside it ([path], "" for all of it).
     */
    data class Repo(val host: GitHost, val owner: String, val repo: String, val ref: String?, val path: String) : ShaderLink() {
        override val key: String get() = ShaderShortcuts.safe("${host.id}-${owner.replace('/', '-')}-$repo")
        override val label: String get() = "$owner/$repo"
    }

    /** A preset file at [url], whose files are fetched next to it. */
    data class Direct(val url: String) : ShaderLink() {
        val host: String get() = runCatching { URI(url).host }.getOrNull().orEmpty()

        /** The file's path on its site, the files it pulls in are found from. */
        val path: String get() = runCatching { URI(url).path }.getOrNull().orEmpty().trim('/')

        override val key: String get() = ShaderShortcuts.safe("web-$host")
        override val label: String get() = url.substringAfterLast('/')
    }
}

/** One page of a repository's listing: the files' paths, and the address of the next page when there is one. */
data class TreePage(val files: List<String>, val next: String?)

/** Reading links: what they point at, the hosts' API addresses, and their answers. */
object ShaderLinks {
    private val PRESET = Regex("""\.(slangp|glslp)$""", RegexOption.IGNORE_CASE)
    private const val PAGE = 100

    /** The link in [text]; null when it is none Thor Tools reads. */
    fun parse(text: String): ShaderLink? {
        val trimmed = text.trim().substringBefore('#').substringBefore('?').trimEnd('/')
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = runCatching { URI(withScheme.replace(" ", "%20")) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val parts = (uri.rawPath ?: "").split('/').filter { it.isNotEmpty() }.map(::decode)
        return when (host) {
            "github.com" -> github(parts)
            "raw.githubusercontent.com" -> if (parts.size >= 4) repo(GitHost.GITHUB, parts[0], parts[1], parts[2], parts.drop(3)) else null
            "gitlab.com" -> gitlab(parts)
            "codeberg.org" -> codeberg(parts)
            else -> cdn(host, parts)
                ?: if (PRESET.containsMatchIn(withScheme) && uri.scheme == "https") ShaderLink.Direct(withScheme) else null
        }
    }

    /**
     * Sites that serve GitHub repositories' files (jsDelivr `gh/<owner>/<repo>@<ref>/<path>`, statically, githack):
     * read as the repository itself, so its listing and libretro's collection are known.
     */
    private fun cdn(host: String, parts: List<String>): ShaderLink? = when (host) {
        "cdn.jsdelivr.net" -> if (parts.size >= 3 && parts[0] == "gh") {
            val ref = parts[2].substringAfter('@', "").takeUnless { it.isEmpty() || it == "latest" }
            repo(GitHost.GITHUB, parts[1], parts[2].substringBefore('@'), ref, parts.drop(3))
        } else {
            null
        }
        "cdn.statically.io" -> if (parts.size >= 4 &&
            parts[0] == "gh"
        ) {
            repo(GitHost.GITHUB, parts[1], parts[2], parts[3], parts.drop(4))
        } else {
            null
        }
        "raw.githack.com", "rawcdn.githack.com" -> if (parts.size >=
            3
        ) {
            repo(GitHost.GITHUB, parts[0], parts[1], parts[2], parts.drop(3))
        } else {
            null
        }
        else -> null
    }

    private fun github(parts: List<String>): ShaderLink? {
        if (parts.size < 2) return null
        val repo = parts[1].removeSuffix(".git")
        val kind = parts.getOrNull(2)
        return when {
            kind == null -> repo(GitHost.GITHUB, parts[0], repo, null, emptyList())
            (kind == "tree" || kind == "blob" || kind == "raw") && parts.size >= 4 -> repo(
                GitHost.GITHUB,
                parts[0],
                repo,
                parts[3],
                parts.drop(4),
            )
            else -> null
        }
    }

    private fun gitlab(parts: List<String>): ShaderLink? {
        val dash = parts.indexOf("-")
        val project = if (dash >= 0) parts.take(dash) else parts
        if (project.size < 2) return null
        val repo = project.last().removeSuffix(".git")
        val owner = project.dropLast(1).joinToString("/")
        if (dash < 0) return repo(GitHost.GITLAB, owner, repo, null, emptyList())
        val kind = parts.getOrNull(dash + 1)
        val ref = parts.getOrNull(dash + 2)
        return if (kind in setOf("tree", "blob", "raw") &&
            ref != null
        ) {
            repo(GitHost.GITLAB, owner, repo, ref, parts.drop(dash + 3))
        } else {
            null
        }
    }

    private fun codeberg(parts: List<String>): ShaderLink? {
        if (parts.size < 2) return null
        val repo = parts[1].removeSuffix(".git")
        val kind = parts.getOrNull(2) ?: return repo(GitHost.CODEBERG, parts[0], repo, null, emptyList())
        // src/branch/<ref>/<path>, raw/branch/<ref>/<path> (or tag, commit)
        if ((kind == "src" || kind == "raw") && parts.size >= 5 && parts[3] in setOf("branch", "tag", "commit")) {
            return repo(GitHost.CODEBERG, parts[0], repo, parts[4], parts.drop(5))
        }
        return null
    }

    private fun repo(host: GitHost, owner: String, repo: String, ref: String?, path: List<String>) =
        ShaderLink.Repo(host, owner, repo, ref, path.joinToString("/"))

    /** The address of the repository's own page in the host's API, which names its default branch. */
    fun repoApi(link: ShaderLink.Repo): String = when (link.host) {
        GitHost.GITHUB -> "https://api.github.com/repos/${link.owner}/${link.repo}"
        GitHost.GITLAB -> "https://gitlab.com/api/v4/projects/${encode("${link.owner}/${link.repo}")}"
        GitHost.CODEBERG -> "https://codeberg.org/api/v1/repos/${link.owner}/${link.repo}"
    }

    /** The first page of the repository's whole listing at [ref]. */
    fun treeApi(link: ShaderLink.Repo, ref: String): String = when (link.host) {
        GitHost.GITHUB -> "https://api.github.com/repos/${link.owner}/${link.repo}/git/trees/${encode(ref)}?recursive=1"
        GitHost.GITLAB -> "${repoApi(link)}/repository/tree?recursive=true&per_page=$PAGE&pagination=keyset&ref=${encode(ref)}"
        GitHost.CODEBERG -> "${repoApi(link)}/git/trees/${encode(ref)}?recursive=true&per_page=$PAGE&page=1"
    }

    /** A file of the repository, as its host serves it raw (not counted by GitHub's API limit). */
    fun rawUrl(link: ShaderLink.Repo, ref: String, path: String): String {
        val file = path.split('/').joinToString("/") { segment(it) }
        return when (link.host) {
            GitHost.GITHUB -> "https://raw.githubusercontent.com/${link.owner}/${link.repo}/${segment(ref)}/$file"
            GitHost.GITLAB -> "https://gitlab.com/${link.owner}/${link.repo}/-/raw/${segment(ref)}/$file"
            GitHost.CODEBERG -> "https://codeberg.org/${link.owner}/${link.repo}/raw/branch/${segment(ref)}/$file"
        }
    }

    /** A file next to a direct link's preset: [path] is its path on the same site. */
    fun directUrl(link: ShaderLink.Direct, path: String): String {
        val uri = URI(link.url)
        return "${uri.scheme}://${uri.rawAuthority}/" + path.split('/').joinToString("/") { segment(it) }
    }

    /** Characters a path may hold as they are, which some sites refuse escaped ("@" in a CDN's "repo@branch"). */
    private val PATH_SAFE = Regex("%(40|3A|21|24|26|27|28|29|2A|2C|3B|3D)")

    /** [part] as one segment of a path: escaped where it must be, nothing more. */
    private fun segment(part: String) = PATH_SAFE.replace(encode(part)) { java.net.URLDecoder.decode(it.value, "UTF-8") }

    /** The default branch in the answer of [repoApi]. */
    fun defaultBranch(json: String): String? = ((TinyJson.parse(json) as? Map<*, *>)?.get("default_branch") as? String)?.ifEmpty { null }

    /**
     * One page of a listing: [json] the answer, [link] its `Link` header (GitLab pages through it), [url] the page's own
     * address (Codeberg pages by number while `truncated`).
     */
    fun parseTree(host: GitHost, json: String, link: String?, url: String): TreePage {
        val parsed = TinyJson.parse(json)
        // Codeberg answers a page past the end with "tree": null.
        val items = when (parsed) {
            is Map<*, *> -> if ("tree" in parsed) parsed["tree"] as? List<*> ?: emptyList<Any>() else null
            is List<*> -> parsed
            else -> null
        } ?: throw CheatProblem.Failed("no listing")
        val files = items.filterIsInstance<Map<*, *>>().filter { it["type"] == "blob" }.mapNotNull { it["path"] as? String }
        val next = when (host) {
            GitHost.GITHUB -> null
            GitHost.GITLAB -> link?.split(',')?.firstOrNull { "rel=\"next\"" in it }?.substringAfter('<')?.substringBefore('>')
            GitHost.CODEBERG -> {
                // "truncated" stays true on the last page that has files; the total says where the listing ends.
                val map = parsed as? Map<*, *>
                val truncated = map?.get("truncated") == true
                val total = (map?.get("total_count") as? Number)?.toInt()
                val page = Regex("""[?&]page=(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull()
                val perPage = Regex("""[?&]per_page=(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: PAGE
                val more = total == null || page == null || page * perPage < total
                if (truncated && page != null && items.isNotEmpty() && more) {
                    url.replace(Regex("""([?&])page=\d+"""), "$1page=${page + 1}")
                } else {
                    null
                }
            }
        }
        return TreePage(files, next)
    }

    /** GitHub says its listing was cut short (very large repositories). */
    fun truncated(json: String): Boolean = (TinyJson.parse(json) as? Map<*, *>)?.get("truncated") == true

    /** The presets among [files] inside [path] (a folder, a file or "" for all), in [format] first. */
    fun presets(files: Collection<String>, path: String): List<String> = files.filter { file ->
        PRESET.containsMatchIn(file) && (path.isEmpty() || file == path || file.startsWith("$path/"))
    }.sorted()

    fun isPreset(path: String): Boolean = PRESET.containsMatchIn(path)

    private fun encode(part: String) = URLEncoder.encode(part, "UTF-8").replace("+", "%20")

    private fun decode(part: String) = runCatching { java.net.URLDecoder.decode(part.replace("+", "%2B"), "UTF-8") }.getOrDefault(part)
}
