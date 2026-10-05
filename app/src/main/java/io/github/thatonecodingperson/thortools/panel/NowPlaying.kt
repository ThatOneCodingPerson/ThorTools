package io.github.thatonecodingperson.thortools.panel

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService

/**
 * Android only shows other apps' media sessions to a notification listener, so Thor Tools has one. It does nothing
 * with notifications; it only has to be allowed (`cmd notification allow_listener`, see [NowPlayingWatch.allowCommand]).
 */
class MediaListener : NotificationListenerService()

/** What the Now playing widget shows. [access] false: the listener isn't allowed yet. */
data class NowPlaying(
    val access: Boolean = true,
    val title: String? = null,
    val artist: String? = null,
    val app: String? = null,
    val playing: Boolean = false,
    val art: Bitmap? = null,
)

enum class MediaCommand { PLAY_PAUSE, NEXT, PREVIOUS }

/**
 * Follows the media session that is playing (or the most recent one) while the panel is open, and sends it play,
 * pause and skip. Main thread.
 */
class NowPlayingWatch(private val context: Context, private val onChange: (NowPlaying) -> Unit) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MediaListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var running = false

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers -> follow(controllers.orEmpty()) }
    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()

        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()

        override fun onSessionDestroyed() = follow(activeSessions())
    }

    val allowed: Boolean
        get() = context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)

    /** Root command that allows the listener: it adds only Thor Tools' own entry to Android's list. */
    val allowCommand: String get() = "cmd notification allow_listener ${component.flattenToString()}"

    fun start() {
        stop()
        if (!allowed) return onChange(NowPlaying(access = false))
        running = true
        runCatching { sessions.addOnActiveSessionsChangedListener(sessionsChanged, component, handler) }
        follow(activeSessions())
    }

    fun stop() {
        if (running) runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsChanged) }
        running = false
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    fun send(command: MediaCommand) {
        val current = controller ?: return
        val controls = current.transportControls
        when (command) {
            MediaCommand.PLAY_PAUSE -> if (current.playbackState?.state ==
                PlaybackState.STATE_PLAYING
            ) {
                controls.pause()
            } else {
                controls.play()
            }
            MediaCommand.NEXT -> controls.skipToNext()
            MediaCommand.PREVIOUS -> controls.skipToPrevious()
        }
    }

    private fun activeSessions(): List<MediaController> = runCatching { sessions.getActiveSessions(component) }.getOrDefault(emptyList())

    /** The playing session, or else the first (Android lists the most recent first). */
    private fun follow(controllers: List<MediaController>) {
        val next = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = next
            next?.registerCallback(controllerCallback, handler)
        }
        publish()
    }

    private fun publish() {
        val current = controller ?: return onChange(NowPlaying())
        val metadata = current.metadata
        onChange(
            NowPlaying(
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
                app = appName(current.packageName),
                playing = current.playbackState?.state == PlaybackState.STATE_PLAYING,
                art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            ),
        )
    }

    private fun appName(packageName: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}
