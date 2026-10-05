package io.github.thatonecodingperson.thortools.wii

/** The ways a profile can reach Dolphin's profile folder. */
enum class SaveRoute {
    /** Through Dolphin's own document provider, with the folder access the user gave: Dolphin writes the file itself. */
    DOLPHIN_ACCESS,

    /** As root through PServer; the file then gets Dolphin's owner and mode. */
    ROOT,
    ;

    companion object {
        /** Dolphin's own access first: it writes into the folder Dolphin really uses, whichever that is. */
        fun order(access: Boolean, root: Boolean): List<SaveRoute> = buildList {
            if (access) add(DOLPHIN_ACCESS)
            if (root) add(ROOT)
        }

        /**
         * What a save into Dolphin comes to, from each route's result in the order they were tried. With no route
         * working, the user is asked for the folder access when [canAskAccess], else the profile has to go by hand.
         */
        fun settle(results: List<DolphinSave>, canAskAccess: Boolean): SaveEnd {
            results.filterIsInstance<DolphinSave.Saved>().firstOrNull()?.let { return SaveEnd.InDolphin(it) }
            val detail = results.filterIsInstance<DolphinSave.Failed>().joinToString("; ") { it.detail }.ifEmpty { null }
            return if (canAskAccess) SaveEnd.NeedsAccess(detail) else SaveEnd.ByHand(detail)
        }
    }
}

/** What one route did. */
sealed class DolphinSave {
    /** [listed]: Dolphin's own view of its profile folder shows the file at its full size. */
    data class Saved(val route: SaveRoute, val listed: Boolean) : DolphinSave()

    data class Failed(val route: SaveRoute, val detail: String) : DolphinSave()
}

sealed class SaveEnd {
    data class InDolphin(val save: DolphinSave.Saved) : SaveEnd()

    /** No route could write, but Dolphin's folder access would; [detail] says why the others failed. */
    data class NeedsAccess(val detail: String?) : SaveEnd()

    data class ByHand(val detail: String?) : SaveEnd()
}

/**
 * The one line the root save prints: the owner of Dolphin's Config folder, then the new file's owner, mode and size,
 * then whether its bytes match the staged copy.
 */
object RootSaveCheck {
    const val SAME = "same"
    const val DIFFERS = "differs"

    fun passes(line: String?, size: Int): Boolean {
        val parts = line?.trim()?.split(' ') ?: return false
        return parts.size == 5 &&
            parts[0] == parts[1] &&
            parts[2] == FILE_MODE &&
            parts[3] == size.toString() &&
            parts[4] == SAME
    }

    const val FILE_MODE = "660"
}
