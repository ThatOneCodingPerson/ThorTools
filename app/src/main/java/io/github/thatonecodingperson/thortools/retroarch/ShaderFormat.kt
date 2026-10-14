package io.github.thatonecodingperson.thortools.retroarch

/**
 * The shader language RetroArch's video driver runs: [preset] is its presets' extension, [repo] libretro's repository
 * of those shaders and [pack] the folder RetroArch's Online Updater unpacks them into.
 */
enum class ShaderFormat(val preset: String, val repo: String, val pack: String) {
    SLANG("slangp", "libretro/slang-shaders", "shaders_slang"),
    GLSL("glslp", "libretro/glsl-shaders", "shaders_glsl"),
    ;

    companion object {
        /** The format [driver] (`video_driver`) runs; null for a driver without shaders Thor Tools offers. */
        fun forDriver(driver: String?): ShaderFormat? = when (driver?.lowercase()) {
            "vulkan", "glcore", "d3d11", "d3d12", "metal" -> SLANG
            "gl", "gles" -> GLSL
            else -> null
        }
    }
}

/** The files a shader file pulls in, so a shader can be fetched or copied with everything it needs. */
object ShaderDeps {
    private val INCLUDE = Regex("""^\s*#include\s+"([^"]+)"""")
    private val REFERENCE = Regex("""^\s*#reference\s+"([^"]+)"""")
    private val KEY = Regex("""^\s*([A-Za-z0-9_]+)\s*=\s*"?([^"]*?)"?\s*$""")
    private val SHADER = Regex("""^shader\d+$""")
    private val TEXT = setOf("slangp", "glslp", "slang", "glsl", "inc", "h", "hlsl", "params", "fx")

    /** True when [path] is a text file whose lines may name more files. */
    fun isText(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in TEXT

    /**
     * The files [text] (the file at [path] in its repository) pulls in, as paths in the repository: a preset's
     * `#reference`, `shaderN` and its textures, a shader's `#include`s. Paths that leave the repository are left out.
     */
    fun of(path: String, text: String): List<String> {
        val dir = path.substringBeforeLast('/', "")
        return named(path, text).filter { it.isNotBlank() && !it.startsWith("/") && ':' !in it }.mapNotNull { resolve(dir, it) }.distinct()
    }

    /** The files [text] (the file at [path]) names, as they are written. */
    fun named(path: String, text: String): List<String> {
        val lines = text.lines()
        if (!isPreset(path)) return lines.mapNotNull { INCLUDE.find(it)?.groupValues?.get(1) }
        val values = presetValues(lines)
        val textures = values["textures"].orEmpty().split(';').map { it.trim() }.filter { it.isNotEmpty() }
        return lines.mapNotNull { REFERENCE.find(it)?.groupValues?.get(1) } +
            values.filterKeys { SHADER.matches(it) }.values +
            textures.mapNotNull { values[it] }
    }

    /**
     * [text] of the file at [path] with every file it names changed by [change] (given the name as written, giving the
     * new one or null to keep it); the same text when nothing changed.
     */
    fun rewrite(path: String, text: String, change: (String) -> String?): String {
        val named = named(path, text).toSet()
        if (named.isEmpty()) return text
        var changed = false
        val lines = text.split('\n').map { line ->
            val body = line.removeSuffix("\r")
            val match = REFERENCE.find(body) ?: INCLUDE.find(body) ?: KEY.matchEntire(body.trim())?.takeIf { isPreset(path) }
            val value = match?.groupValues?.last()?.takeIf { it in named } ?: return@map line
            val new = change(value) ?: return@map line
            changed = true
            val rewritten = if (match.value.trimStart().startsWith("#")) {
                body.replace("\"$value\"", "\"$new\"")
            } else {
                "${match.groupValues[1]} = \"$new\""
            }
            rewritten + if (line.endsWith("\r")) "\r" else ""
        }
        return if (changed) lines.joinToString("\n") else text
    }

    private fun isPreset(path: String) = path.endsWith(".slangp", true) || path.endsWith(".glslp", true)

    private fun presetValues(lines: List<String>): Map<String, String> =
        lines.mapNotNull { line -> KEY.matchEntire(line.trim())?.let { it.groupValues[1] to it.groupValues[2] } }.toMap()

    /** [relative] seen from [dir], with `.` and `..` worked out; null when it leaves the repository. */
    fun resolve(dir: String, relative: String): String? {
        val parts = ArrayDeque<String>()
        (dir.split('/') + relative.replace('\\', '/').split('/')).forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/").ifEmpty { null }
    }
}

/**
 * Presets people share often name libretro's collection the way their own RetroArch had it: "shaders_slang/crt/…"
 * next to the preset, "../shaders_slang/…", or a whole path from their device ("C:\RetroArch\shaders\shaders_slang\…").
 * Those files aren't in the link; they are pointed at the library in the shader folder instead.
 */
object LibraryRefs {
    /** The path in the shader folder that [name] (as a file names it) has inside the collection of [format]; null for none. */
    fun of(name: String, format: ShaderFormat): String? {
        val clean = name.replace('\\', '/')
        val at = Regex("(?:^|/)(${Regex.escape(format.pack)}/.+)$", RegexOption.IGNORE_CASE).find(clean) ?: return null
        return format.pack + at.groupValues[1].substring(format.pack.length)
    }

    /**
     * The file at [path] in its link, to be installed at [installed] in the shader folder: each name in [text] that is
     * in the collection and not among the link's [files] (null when they aren't known) points at the library, relative to
     * [installed]. Gives the new text and the library paths named.
     */
    fun point(path: String, text: String, installed: String, format: ShaderFormat, files: Set<String>?): Pair<String, Set<String>> {
        val dir = path.substringBeforeLast('/', "")
        val used = mutableSetOf<String>()
        val up = "../".repeat(installed.count { it == '/' })
        val rewritten = ShaderDeps.rewrite(path, text) { name ->
            val library = of(name, format) ?: return@rewrite null
            val inLink = ShaderDeps.resolve(dir, name.replace('\\', '/'))
            if (files != null && inLink != null && inLink in files) return@rewrite null
            used += library
            up + library
        }
        return rewritten to used
    }
}
