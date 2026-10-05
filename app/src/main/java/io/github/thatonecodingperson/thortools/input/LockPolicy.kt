package io.github.thatonecodingperson.thortools.input

/**
 * The rules of the controller lock. The top screen uses AYN's own lock; the bottom
 * screen has none, so [ControllerLock] sends the controller back after the top screen was touched.
 */
object LockPolicy {
    /** A lock action used while the controller is already locked to [wanted] unlocks; otherwise it locks there. */
    fun toggled(current: Screen?, wanted: Screen): Screen? = if (current == wanted) null else wanted

    /** "Lock to this screen" unlocks when any lock is on, otherwise locks to the screen the controller is on. */
    fun toggledHere(current: Screen?, controllerOn: Screen): Screen? = if (current != null) null else controllerOn

    /**
     * Sending the controller somewhere by hand: with "also lock", a second press on the same screen unlocks.
     * Without it, a lock to the other screen is released, so the move isn't undone.
     */
    fun afterMove(current: Screen?, target: Screen, alsoLock: Boolean): Screen? = when {
        alsoLock -> toggled(current, target)
        current != null && current != target -> null
        else -> current
    }

    /** Whether the last finger leaving [liftedOn] should send the controller back to the locked screen. */
    fun returnsAfterLift(locked: Screen?, liftedOn: Screen, keyboardOnTop: Boolean, paused: Boolean): Boolean =
        locked == Screen.BOTTOM && liftedOn == Screen.TOP && !keyboardOnTop && !paused
}
