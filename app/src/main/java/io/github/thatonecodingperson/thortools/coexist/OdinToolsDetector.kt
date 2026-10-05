package io.github.thatonecodingperson.thortools.coexist

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.tools.SharedList
import javax.inject.Inject

/**
 * [leftoverThorTools] is an old Thor Tools build (0.1.0) that used OdinTools' package name. It is signed with our
 * key, so it is not OdinTools and does not count as [installed].
 */
data class OdinToolsState(
    val installed: Boolean,
    val serviceEnabled: Boolean,
    val versionName: String?,
    val leftoverThorTools: Boolean = false,
) {
    companion object {
        val Absent = OdinToolsState(installed = false, serviceEnabled = false, versionName = null)
    }
}

class OdinToolsDetector @Inject constructor(@ApplicationContext private val context: Context) {

    fun isInstalled(): Boolean = packageInfo() != null && !isOurs()

    fun state(): OdinToolsState {
        val info = packageInfo() ?: return OdinToolsState.Absent
        if (isOurs()) return OdinToolsState.Absent.copy(versionName = info.versionName, leftoverThorTools = true)

        val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return OdinToolsState(
            installed = true,
            serviceEnabled = SharedList.containsPackage(services, ':', PACKAGE),
            versionName = info.versionName,
        )
    }

    private fun packageInfo() = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.getOrNull()

    private fun isOurs() = context.packageManager.checkSignatures(context.packageName, PACKAGE) == PackageManager.SIGNATURE_MATCH

    companion object {
        const val PACKAGE = "de.langerhans.odintools"
    }
}
