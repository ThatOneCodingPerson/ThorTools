package io.github.thatonecodingperson.thortools.hotkeys

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class LaunchableApp(val packageName: String, val name: String, val icon: Drawable)

/** The apps with a launcher icon, for the hotkey screens' app pickers. Slow: call it off the main thread. */
class LaunchableApps @Inject constructor(@ApplicationContext private val context: Context) {
    fun load(): List<LaunchableApp> {
        val packageManager = context.packageManager
        return packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                LaunchableApp(
                    packageName = info.packageName,
                    name = packageManager.getApplicationLabel(info).toString(),
                    icon = packageManager.getApplicationIcon(info),
                )
            }
            .sortedBy { it.name.lowercase() }
    }
}
