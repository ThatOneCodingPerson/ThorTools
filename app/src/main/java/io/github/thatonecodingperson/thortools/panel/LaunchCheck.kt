package io.github.thatonecodingperson.thortools.panel

/**
 * What to do after the panel's Thor Tools tile started the app, given where the app is showing now. Pure: the panel
 * checks a little later each time.
 */
object LaunchCheck {
    enum class Next {
        /** It is showing on the screen it was meant for. */
        DONE,

        /** It is showing on the other screen: move its task over (root helper). */
        MOVE,

        /** It isn't showing anywhere: start it again (it comes forward, no second copy). */
        START_AGAIN,

        /** Stop trying; hand the controller back the usual way. */
        GIVE_UP,
    }

    const val MAX_ATTEMPTS = 3

    fun next(attempt: Int, shownOn: Int?, target: Int): Next = when {
        shownOn == target -> Next.DONE
        attempt >= MAX_ATTEMPTS -> Next.GIVE_UP
        shownOn != null -> Next.MOVE
        else -> Next.START_AGAIN
    }
}
