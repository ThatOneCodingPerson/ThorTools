package io.github.thatonecodingperson.thortools.retroarch

import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.zip.ZipFile

/** Why cheats couldn't be had. */
sealed class CheatProblem(message: String) : Exception(message) {
    /** No connection to the internet. */
    class Offline : CheatProblem("offline")

    /** GitHub's limit for requests without an account is reached; [resetAt] is when it ends (seconds since 1970), if known. */
    class RateLimited(val resetAt: Long?) : CheatProblem("rate limited")

    class Http(val code: Int) : CheatProblem("HTTP $code")

    /** Something else went wrong on the way: [detail] says what. */
    class Failed(val detail: String) : CheatProblem(detail)

    /** The cheat pack isn't downloaded. */
    class NoPack : CheatProblem("no pack")
}

/** Where cheat files come from. Both sources give libretro-database's files, so everything else works the same with either. */
interface CheatSource {
    /** The console's cheat files (none for a console without cheats); throws [CheatProblem]. */
    fun index(system: CheatSystem): CheatIndex

    /** One cheat file's bytes; throws [CheatProblem]. */
    fun read(system: CheatSystem, file: CheatFileRef): ByteArray
}

enum class CheatSourceKind(val id: String) {
    /** Each console's list and each game's file from libretro-database on GitHub, when needed. */
    GITHUB("github"),

    /** libretro's whole cheat pack, downloaded once. */
    PACK("pack"),
    ;

    companion object {
        fun of(id: String?): CheatSourceKind = entries.find { it.id == id } ?: GITHUB
    }
}

/** The downloaded cheat pack: its size and when it was downloaded. */
data class PackInfo(val size: Long, val time: Long)

/** Plain HTTP GETs with timeouts; every failure comes out as a [CheatProblem]. Blocking: never on the main thread. */
class CheatHttp(private val userAgent: String) {
    class Response(val code: Int, val body: ByteArray, val etag: String?)

    /** [url]'s body; with [etag], a 304 answer when it hasn't changed (empty body). */
    fun get(url: String, etag: String? = null, accept: String? = null): Response {
        val connection = open(url, accept)
        etag?.let { connection.setRequestProperty("If-None-Match", it) }
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return Response(code, ByteArray(0), etag)
            check(connection, code)
            val body = connection.inputStream.use { it.readBytes() }
            return Response(code, body, connection.getHeaderField("ETag"))
        } catch (e: IOException) {
            throw problem(e)
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads [url] into [to], telling [progress] the bytes so far and in all (0 when unknown); [to] is gone on failure. */
    fun download(url: String, to: File, progress: (Long, Long) -> Unit) {
        val connection = open(url, null)
        var done = false
        try {
            check(connection, connection.responseCode)
            val total = connection.contentLengthLong.coerceAtLeast(0)
            to.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                to.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    var read = 0L
                    var told = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (read - told >= PROGRESS_STEP) {
                            progress(read, total)
                            told = read
                        }
                    }
                    progress(read, total)
                }
            }
            done = true
        } catch (e: IOException) {
            throw problem(e)
        } finally {
            connection.disconnect()
            if (!done) to.delete()
        }
    }

    private fun open(url: String, accept: String?): HttpURLConnection = try {
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("User-Agent", userAgent)
            accept?.let { setRequestProperty("Accept", it) }
        }
    } catch (e: IOException) {
        throw problem(e)
    }

    private fun check(connection: HttpURLConnection, code: Int) {
        if (code in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE) return
        val limited = connection.getHeaderField("X-RateLimit-Remaining") == "0" || connection.getHeaderField("Retry-After") != null
        if ((code == HttpURLConnection.HTTP_FORBIDDEN || code == TOO_MANY_REQUESTS) && limited) {
            throw CheatProblem.RateLimited(connection.getHeaderField("X-RateLimit-Reset")?.toLongOrNull())
        }
        throw CheatProblem.Http(code)
    }

    private fun problem(e: IOException): CheatProblem = when (e) {
        is UnknownHostException, is ConnectException, is NoRouteToHostException, is SocketTimeoutException -> CheatProblem.Offline()
        else -> CheatProblem.Failed(e.message ?: e.javaClass.simpleName)
    }

    private companion object {
        const val CONNECT_TIMEOUT = 15_000
        const val READ_TIMEOUT = 30_000
        const val TOO_MANY_REQUESTS = 429
        const val BUFFER = 64 * 1024
        const val PROGRESS_STEP = 512 * 1024L
    }
}

/** libretro-database on GitHub: the addresses, and reading and keeping the API's answers. */
object GitHubCheatsApi {
    private const val REPO = "libretro/libretro-database"
    const val SYSTEMS_URL = "https://api.github.com/repos/$REPO/contents/cht"
    const val JSON = "application/vnd.github+json"
    const val LINK = "https://github.com/$REPO"

    fun treeUrl(id: String) = "https://api.github.com/repos/$REPO/git/trees/$id"

    fun rawUrl(db: String, name: String) = "https://raw.githubusercontent.com/$REPO/master/cht/${encode(db)}/${encode(name)}"

    /** The console folders in the contents API's answer for `cht`, each with its git tree id. */
    fun parseSystems(json: String): Map<String, String> {
        val entries = TinyJson.parse(json) as? List<*> ?: throw CheatProblem.Failed("GitHub: not a list")
        return entries.filterIsInstance<Map<*, *>>()
            .filter { it["type"] == "dir" }
            .mapNotNull { entry -> (entry["name"] as? String)?.let { name -> (entry["sha"] as? String)?.let { name to it } } }
            .toMap()
    }

    /** The cheat files in the git trees API's answer for a console's folder. */
    fun parseTree(json: String): List<CheatFileRef> {
        val tree = (TinyJson.parse(json) as? Map<*, *>)?.get("tree") as? List<*> ?: throw CheatProblem.Failed("GitHub: no tree")
        return tree.filterIsInstance<Map<*, *>>().mapNotNull { entry ->
            val path = entry["path"] as? String ?: return@mapNotNull null
            if (entry["type"] != "blob" || '/' in path || !path.endsWith(RomNames.CHT, ignoreCase = true)) return@mapNotNull null
            CheatFileRef(path, (entry["size"] as? Double)?.toLong(), entry["sha"] as? String)
        }
    }

    /** The console folders as kept between visits: when they were asked for, GitHub's ETag for them, and their tree ids. */
    data class Systems(val time: Long, val etag: String?, val folders: Map<String, String>)

    fun encodeSystems(systems: Systems): String = buildString {
        appendLine("time\t${systems.time}")
        appendLine("etag\t${systems.etag.orEmpty()}")
        systems.folders.forEach { (name, id) -> appendLine("dir\t$name\t$id") }
    }

    fun decodeSystems(text: String): Systems? {
        var time: Long? = null
        var etag: String? = null
        val folders = mutableMapOf<String, String>()
        text.lines().map { it.split('\t') }.forEach { parts ->
            when (parts[0]) {
                "time" -> time = parts.getOrNull(1)?.toLongOrNull()
                "etag" -> etag = parts.getOrNull(1)?.ifEmpty { null }
                "dir" -> if (parts.size == 3) folders[parts[1]] = parts[2]
            }
        }
        return time?.let { Systems(it, etag, folders) }
    }

    fun encodeTree(files: List<CheatFileRef>): String =
        files.filter { '\t' !in it.name }.joinToString("\n") { "${it.name}\t${it.size ?: ""}\t${it.id.orEmpty()}" }

    fun decodeTree(text: String): List<CheatFileRef> = text.lines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 3 || parts[0].isEmpty()) null else CheatFileRef(parts[0], parts[1].toLongOrNull(), parts[2].ifEmpty { null })
    }

    private fun encode(part: String) = URLEncoder.encode(part, "UTF-8").replace("+", "%20")
}

/**
 * Cheats from libretro-database on GitHub, fetched when needed and kept in [dir]: the console folders (asked again at
 * most once a day, with their ETag), each console's file list by its tree id (a list never changes under one id) and each
 * file by its git id. GitHub allows 60 API requests an hour without an account; files come from raw.githubusercontent.com,
 * which doesn't count. What is kept is used when GitHub can't be reached. Blocking: never on the main thread.
 */
class GitHubCheats(private val dir: File, private val http: CheatHttp, private val clock: () -> Long = System::currentTimeMillis) :
    CheatSource {
    override fun index(system: CheatSystem): CheatIndex {
        val db = system.db ?: return CheatIndex(system, emptyList())
        val id = systems()[db] ?: return CheatIndex(system, emptyList())
        val kept = File(dir, "$TREES/$id.txt")
        if (kept.isFile) return CheatIndex(system, GitHubCheatsApi.decodeTree(kept.readText()))
        val answer = http.get(GitHubCheatsApi.treeUrl(id), accept = GitHubCheatsApi.JSON)
        val files = GitHubCheatsApi.parseTree(answer.body.decodeToString())
        runCatching {
            kept.parentFile?.mkdirs()
            kept.writeText(GitHubCheatsApi.encodeTree(files))
        }
        return CheatIndex(system, files)
    }

    override fun read(system: CheatSystem, file: CheatFileRef): ByteArray {
        val db = system.db ?: throw CheatProblem.Failed("no cheats for ${system.label}")
        val kept = file.id?.let { File(dir, "$FILES/$it$EXTENSION") }
        if (kept?.isFile == true) return kept.readBytes()
        val bytes = http.get(GitHubCheatsApi.rawUrl(db, file.name)).body
        kept?.let {
            runCatching {
                it.parentFile?.mkdirs()
                it.writeBytes(bytes)
            }
        }
        return bytes
    }

    /** Each console folder's name with its tree id. */
    private fun systems(): Map<String, String> {
        val file = File(dir, SYSTEMS)
        val kept = file.takeIf { it.isFile }?.let { runCatching { GitHubCheatsApi.decodeSystems(it.readText()) }.getOrNull() }
        if (kept != null && clock() - kept.time in 0 until DAY) return kept.folders
        val response = try {
            http.get(GitHubCheatsApi.SYSTEMS_URL, etag = kept?.etag, accept = GitHubCheatsApi.JSON)
        } catch (problem: CheatProblem) {
            if (kept != null) return kept.folders
            throw problem
        }
        val fresh = if (response.code == HttpURLConnection.HTTP_NOT_MODIFIED && kept != null) {
            kept.copy(time = clock())
        } else {
            GitHubCheatsApi.Systems(clock(), response.etag, GitHubCheatsApi.parseSystems(response.body.decodeToString()))
        }
        runCatching {
            dir.mkdirs()
            file.writeText(GitHubCheatsApi.encodeSystems(fresh))
            // Lists of folders that changed are of no more use.
            File(dir, TREES).listFiles()?.filter { it.name.removeSuffix(".txt") !in fresh.folders.values }?.forEach { it.delete() }
        }
        return fresh.folders
    }

    private companion object {
        const val SYSTEMS = "systems.txt"
        const val TREES = "trees"
        const val FILES = "files"
        const val EXTENSION = ".cht"
        const val DAY = 24 * 60 * 60 * 1000L
    }
}

/**
 * libretro's whole cheat pack (the one RetroArch's Online Updater downloads): a zip of `<console>/<game>.cht`, kept as
 * [file] and read from where it is, never unpacked. Blocking: never on the main thread.
 */
class CheatPack(private val file: File, private val http: CheatHttp) : CheatSource {
    /** Each console's files with the time of the download they were read from, read from the zip once per download. */
    private var listed: Pair<Long, Map<String, List<CheatFileRef>>>? = null

    val info: PackInfo? get() = file.takeIf { it.isFile }?.let { PackInfo(it.length(), it.lastModified()) }

    @Synchronized
    override fun index(system: CheatSystem): CheatIndex {
        val all = consoles()
        return CheatIndex(system, system.db?.let { all[it] }.orEmpty())
    }

    private fun consoles(): Map<String, List<CheatFileRef>> {
        if (!file.isFile) throw CheatProblem.NoPack()
        val stamp = file.lastModified()
        listed?.takeIf { it.first == stamp }?.let { return it.second }
        val read = try {
            ZipFile(file).use { zip -> group(zip.entries().asSequence().filter { !it.isDirectory }.map { it.name to it.size }) }
        } catch (e: IOException) {
            throw CheatProblem.Failed(e.message ?: "zip")
        }
        listed = stamp to read
        return read
    }

    override fun read(system: CheatSystem, file: CheatFileRef): ByteArray {
        if (!this.file.isFile) throw CheatProblem.NoPack()
        return try {
            ZipFile(this.file).use { zip ->
                val entry = zip.getEntry("${system.db}/${file.name}") ?: throw CheatProblem.Failed("not in the pack: ${file.name}")
                zip.getInputStream(entry).use { it.readBytes() }
            }
        } catch (e: IOException) {
            throw CheatProblem.Failed(e.message ?: "zip")
        }
    }

    /** Downloads the pack again; the one there stays until the new one is complete and opens as a zip. */
    fun download(progress: (Long, Long) -> Unit) {
        val part = File(file.path + PART)
        http.download(PACK_URL, part, progress)
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
        synchronized(this) { listed = null }
    }

    fun delete() {
        file.delete()
        synchronized(this) { listed = null }
    }

    companion object {
        const val PACK_URL = "https://buildbot.libretro.com/assets/frontend/cheats.zip"
        private const val PART = ".part"

        /** The pack's entries (name and size) as each console's cheat files; anything else in it (READMEs) is left out. */
        fun group(entries: Sequence<Pair<String, Long>>): Map<String, List<CheatFileRef>> = entries
            .filter { (name, _) -> name.count { it == '/' } == 1 && name.endsWith(RomNames.CHT, ignoreCase = true) }
            .groupBy(
                { (name, _) -> name.substringBefore('/') },
                { (name, size) -> CheatFileRef(name.substringAfter('/'), size.takeIf { it >= 0 }) },
            )
    }
}
