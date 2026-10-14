package io.github.thatonecodingperson.thortools.retroarch

/** The note after the shaders tab did something. */
sealed class ShaderNote {
    /** The shaders are in RetroArch's shader folder, sorted: [shortcuts] shortcuts in the sorted folders. */
    data class Ready(val shortcuts: Int) : ShaderNote()

    /** The sorted folders follow the consoles again: [added] shortcuts added, [removed] removed. */
    data class Updated(val added: Int, val removed: Int) : ShaderNote()

    data class Moved(val folder: String) : ShaderNote()

    data object Removed : ShaderNote()

    /** The automatic presets an older Thor Tools wrote are gone. */
    data object OldLooksRemoved : ShaderNote()

    data class Problem(val problem: CheatProblem) : ShaderNote()

    data class Failed(val detail: String) : ShaderNote()

    /** Root couldn't look at RetroArch's shaders. */
    data object ScanFailed : ShaderNote()

    /** What was typed isn't a link Thor Tools reads. */
    data object NotALink : ShaderNote()

    /** The link has no shader presets. */
    data object NoPresets : ShaderNote()

    /** [shortcuts] shortcuts from a link went into [folders] folders; [missing] files of libretro's collection they name aren't there. */
    data class LinkAdded(val shortcuts: Int, val folders: Int, val missing: Int = 0) : ShaderNote()
}

/**
 * A preset a link has: its [path] there, its plain [name], the [folders] its shortcut goes to, whether it is [ticked],
 * whether it is in the language RetroArch's driver runs ([usable]), the file in the shader folder its shortcut points at
 * ([target]) and the shortcuts already pointing there ([already]).
 */
data class LinkPreset(
    val path: String,
    val name: String,
    val folders: List<String>,
    val ticked: Boolean,
    val usable: Boolean,
    val target: String,
    val already: List<String> = emptyList(),
) {
    /** The presets going to the same folders are shown and moved together. */
    val group: String get() = folders.joinToString(" + ")

    val added: Boolean get() = already.isNotEmpty()

    /** Its files are on the Thor already (in the library): nothing to download. */
    val onThor: Boolean get() = ShaderShortcuts.linkKey(target) == null
}

/** What a link holds: the branch read ([ref]), the repository's files (null for a direct link), its presets. */
data class LinkScan(
    val link: ShaderLink,
    val ref: String?,
    val files: Set<String>?,
    val presets: List<LinkPreset>,
    val truncated: Boolean,
) {
    val ticked: List<LinkPreset> get() = presets.filter { it.ticked && it.usable }
}

/**
 * The shaders tab: what is in RetroArch's shader folders ([scan]), work under way (the pack's download in bytes, the
 * part being copied of how many), the guide shown, the folder opened in "Your shaders" and the shortcut chosen there.
 */
data class RaShaders(
    val loaded: Boolean = false,
    val scan: ShaderScan? = null,
    val working: Boolean = false,
    val progress: Pair<Long, Long>? = null,
    val step: Pair<Int, Int>? = null,
    val note: ShaderNote? = null,
    val guide: Boolean = false,
    val openFolder: String? = null,
    val chosen: String? = null,
    val pack: PackInfo? = null,
    /** Folders for every console with console-made shaders, not only the installed cores' consoles. */
    val allConsoles: Boolean = false,
    /** The link typed, what it holds once read, while it is read or its shaders added, and the group being moved. */
    val linkText: String = "",
    val linkScan: LinkScan? = null,
    val linkBusy: Boolean = false,
    val linkGroup: String? = null,
    /** The links shaders were added from, to look at again. */
    val links: List<String> = emptyList(),
)
