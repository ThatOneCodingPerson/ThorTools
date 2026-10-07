package io.github.thatonecodingperson.thortools.actions

/**
 * The Recent apps provider's command queue (Launcher3 Quickstep's `OverviewCommandHelper`), read from its service
 * dump. A Recent apps request is queued there and the next one starts only once it is done; a request whose animation
 * never finishes holds the queue, and once it holds three, new requests are dropped: Recent apps then does nothing until
 * the provider restarts. Pure.
 */
data class RecentsQueue(val pending: Int, val animating: Boolean) {
    /** Requests are waiting with nothing under way: the first one will never finish. */
    val stuck: Boolean get() = pending > 0 && !animating

    companion object {
        const val PACKAGE = "com.android.launcher3"
        const val SERVICE = "$PACKAGE/com.android.quickstep.TouchInteractionService"

        /** The dump's lines (`mPendingCommands=3`, `isRecentsAnimationRunning=false`); null when there are none. */
        fun parse(dump: String?): RecentsQueue? {
            val text = dump ?: return null
            val pending = Regex("""mPendingCommands=(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val animating = Regex("""isRecentsAnimationRunning=(true|false)""").find(text)?.groupValues?.get(1) == "true"
            return RecentsQueue(pending, animating)
        }
    }
}
