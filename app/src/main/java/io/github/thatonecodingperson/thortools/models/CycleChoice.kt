package io.github.thatonecodingperson.thortools.models

/**
 * Which of three modes the switching (quick settings tile, quick panel, "Next ..." hotkeys) skips: at most one, so at
 * least two always stay to switch between. [skipped] is the stored id, null when none is skipped. Pure.
 */
object CycleChoice {
    /** Whether [id] may be taken out of the switching: only when nothing else is out already. */
    fun canSkip(skipped: String?, id: String): Boolean = skipped == null || skipped == id

    /** The skipped id after [id] is put back in ([include]) or taken out; taking out a second one does nothing. */
    fun toggled(skipped: String?, id: String, include: Boolean): String? = when {
        include -> skipped.takeIf { it != id }
        canSkip(skipped, id) -> id
        else -> skipped
    }
}
