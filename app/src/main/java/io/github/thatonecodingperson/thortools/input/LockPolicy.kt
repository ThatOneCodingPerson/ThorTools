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
     * Sending the controller somewhere by hand: with "also lock", a second press on the same screen unlocks, unless the
     * controller has drifted off that screen ([onTarget] false): then it is locked there again, which moves it back.
     * Without it, a lock to the other screen is released, so the move isn't undone.
     */
    fun afterMove(current: Screen?, target: Screen, alsoLock: Boolean, onTarget: Boolean = true): Screen? = when {
        alsoLock && current == target && !onTarget -> target
        alsoLock -> toggled(current, target)
        current != null && current != target -> null
        else -> current
    }

    /** Whether the last finger leaving [liftedOn] should send the controller back to the locked screen. */
    fun returnsAfterLift(locked: Screen?, liftedOn: Screen, paused: Boolean): Boolean =
        locked == Screen.BOTTOM && liftedOn == Screen.TOP && !paused

    /**
     * Whether a return that came due waits and looks again: the keyboard types into the app that has the controller, so
     * moving it would take the keyboard's text field away, whichever screen the keyboard shows on. [keyboardShown]: a
     * keyboard window is up; [editableFocused]: a text field has input focus, which only counts on the first look (the
     * keyboard may still be on its way), so a field left focused after the keyboard closed doesn't hold the lock off.
     */
    fun waitsForTyping(keyboardShown: Boolean, editableFocused: Boolean, firstLook: Boolean): Boolean =
        keyboardShown || (firstLook && editableFocused)
}
