package io.github.thatonecodingperson.thortools.panel

import android.Manifest
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import android.util.Size
import io.github.thatonecodingperson.thortools.lid.LidPlan
import io.github.thatonecodingperson.thortools.tools.ShellExecutor

/** The app in front and since when (elapsed ms), for the play timer. */
data class FrontApp(val packageName: String, val since: Long)

data class StorageReading(val usedGb: Float, val totalGb: Float)

enum class NetworkType { WIFI, CELLULAR, ETHERNET, NONE }

/** [signalLevel] 0 to 4 on Wi-Fi; [pingMs] null until a ping came back (or when none did). */
data class NetworkReading(
    val type: NetworkType = NetworkType.NONE,
    val signalLevel: Int? = null,
    val rssi: Int? = null,
    val linkMbps: Int? = null,
    val pingMs: Float? = null,
)

enum class QuickToggle { WIFI, BLUETOOTH, AIRPLANE, DND }

data class Screenshot(val uri: Uri, val thumbnail: Bitmap?)

/**
 * Reads what the newer widgets show: the network, storage, the latest screenshots and the quick toggles' states, and
 * switches those toggles. Everything here touches the disk, a system service or PServer: never on the main thread.
 */
class WidgetSources(private val context: Context, private val executor: ShellExecutor) {

    fun storage(): StorageReading? = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        StorageReading(usedGb = (total - free) / GB, totalGb = total / GB)
    }.getOrNull()

    /** Without the ping, which the root helper does. */
    fun network(): NetworkReading {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { connectivity.getNetworkCapabilities(connectivity.activeNetwork) }.getOrNull()
        val type = when {
            caps == null -> NetworkType.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.NONE
        }
        if (type != NetworkType.WIFI) return NetworkReading(type)
        val wifi = context.getSystemService(WifiManager::class.java)

        // The SSID would need the location permission; only the signal and the speed are shown.
        @Suppress("DEPRECATION")
        val info = runCatching { wifi.connectionInfo }.getOrNull() ?: return NetworkReading(type)
        val level = runCatching { wifi.calculateSignalLevel(info.rssi) * SIGNAL_LEVELS / wifi.maxSignalLevel.coerceAtLeast(1) }.getOrNull()
        return NetworkReading(type, signalLevel = level, rssi = info.rssi, linkMbps = info.linkSpeed.takeIf { it > 0 })
    }

    fun toggles(): Map<QuickToggle, Boolean> {
        val resolver = context.contentResolver
        fun global(key: String): Int? = runCatching { Settings.Global.getInt(resolver, key) }.getOrNull()
        val dnd = runCatching {
            context.getSystemService(NotificationManager::class.java).currentInterruptionFilter !=
                NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrNull()
        return buildMap {
            LidPlan.radioOn(global(Settings.Global.WIFI_ON))?.let { put(QuickToggle.WIFI, it) }
            LidPlan.radioOn(global(Settings.Global.BLUETOOTH_ON))?.let { put(QuickToggle.BLUETOOTH, it) }
            global(Settings.Global.AIRPLANE_MODE_ON)?.let { put(QuickToggle.AIRPLANE, it == 1) }
            dnd?.let { put(QuickToggle.DND, it) }
        }
    }

    /** Through PServer: an app may not switch the radios or Do not disturb itself. */
    fun set(toggle: QuickToggle, on: Boolean) {
        val command = when (toggle) {
            QuickToggle.WIFI -> "cmd wifi set-wifi-enabled ${if (on) "enabled" else "disabled"}"
            QuickToggle.BLUETOOTH -> "cmd bluetooth_manager ${if (on) "enable" else "disable"}"
            QuickToggle.AIRPLANE -> "cmd connectivity airplane-mode ${if (on) "enable" else "disable"}"
            QuickToggle.DND -> "cmd notification set_dnd ${if (on) "on" else "off"}"
        }
        executor.executeAsRoot(command)
    }

    val screenshotsAllowed: Boolean
        get() = context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED

    /** Root grants the photo permission (Android would otherwise ask in a dialog the panel can't show). */
    fun allowScreenshots() {
        executor.executeAsRoot("pm grant ${context.packageName} ${Manifest.permission.READ_MEDIA_IMAGES}")
    }

    /** The newest screenshots, newest first, with small thumbnails. */
    fun screenshots(count: Int = SCREENSHOT_COUNT): List<Screenshot> {
        if (!screenshotsAllowed) return emptyList()
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val args = Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?")
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("%Screenshots%"))
            putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
            putInt(
                android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
            )
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, count)
        }
        return runCatching {
            resolver.query(collection, arrayOf(MediaStore.Images.Media._ID), args, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                        add(Screenshot(uri, runCatching { resolver.loadThumbnail(uri, Size(THUMB_WIDTH, THUMB_HEIGHT), null) }.getOrNull()))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val GB = 1_000_000_000f
        const val SIGNAL_LEVELS = 4
        const val SCREENSHOT_COUNT = 8
        const val THUMB_WIDTH = 320
        const val THUMB_HEIGHT = 180
    }
}
