package io.github.thatonecodingperson.thortools.navigation

/** Which of Android's navigation swipes work: swipe up for Home and Recents (top screen only), back from the edges. */
data class GestureParts(val homeSwipe: Boolean = true, val backSwipe: Boolean = true) {
    val allWork: Boolean get() = homeSwipe && backSwipe
}

/**
 * The page's choice: gestures on, or off with the parts that go; [offWithDesktop] stops them while desktop controls are
 * in use. At least one part always goes, so "off" never means nothing.
 */
data class GestureChoice(
    val on: Boolean = true,
    val stopHome: Boolean = true,
    val stopBack: Boolean = true,
    val offWithDesktop: Boolean = false,
) {
    fun parts(on: Boolean = this.on): GestureParts = if (on) GestureParts() else GestureParts(homeSwipe = !stopHome, backSwipe = !stopBack)

    /** Unticking [stopHome] or [stopBack] is refused while it is the only one left. */
    fun withStopHome(stop: Boolean): GestureChoice = if (stop || stopBack) copy(stopHome = stop) else this

    fun withStopBack(stop: Boolean): GestureChoice = if (stop || stopHome) copy(stopBack = stop) else this

    companion object {
        /** As stored. Nothing ticked stopped nothing, so it reads as on with both ticked: the swipes keep working. */
        fun stored(on: Boolean, stopHome: Boolean, stopBack: Boolean, offWithDesktop: Boolean): GestureChoice = if (stopHome || stopBack) {
            GestureChoice(on, stopHome, stopBack, offWithDesktop)
        } else {
            GestureChoice(on = true, offWithDesktop = offWithDesktop)
        }
    }
}

/** Who decides the gestures right now. */
enum class GestureSource { PAGE, APP, DESKTOP, ACTION }

/**
 * What should work: the page's [choice], the front app's profile ([forApp], null when it has none), whether desktop
 * controls are in use ([desktop]) and the action pressed while that app is in front ([manual], kept until the app
 * changes). The action wins, then desktop controls (when the page asks for it), then the profile, then the page.
 */
data class GestureWish(
    val choice: GestureChoice = GestureChoice(),
    val forApp: Boolean? = null,
    val manual: Boolean? = null,
    val desktop: Boolean = false,
) {
    private val desktopDecides: Boolean get() = desktop && choice.offWithDesktop

    val on: Boolean get() = manual ?: (if (desktopDecides) false else null) ?: forApp ?: choice.on

    val parts: GestureParts get() = choice.parts(on)

    val source: GestureSource
        get() = when {
            manual != null -> GestureSource.ACTION
            desktopDecides -> GestureSource.DESKTOP
            forApp != null -> GestureSource.APP
            else -> GestureSource.PAGE
        }

    /** The action: where a profile or desktop controls decide, until the app changes; anywhere else it flips the page's switch. */
    fun toggled(): GestureWish = if (source == GestureSource.PAGE) copy(choice = choice.copy(on = !on)) else copy(manual = !on)

    /** Another app came to the front with its profile's wish. */
    fun forApp(on: Boolean?): GestureWish = copy(forApp = on, manual = null)

    /** Desktop controls came into use or stopped. */
    fun withDesktop(inUse: Boolean): GestureWish = copy(desktop = inUse)

    /** The page changed: an action's change ends. */
    fun withChoice(choice: GestureChoice): GestureWish = copy(choice = choice, manual = null)
}
