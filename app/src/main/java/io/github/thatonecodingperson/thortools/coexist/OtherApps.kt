package io.github.thatonecodingperson.thortools.coexist

import android.content.Context
import android.provider.Settings
import io.github.thatonecodingperson.thortools.tools.SharedList

/** Other apps that act on the same buttons. OdinTools is not one: it doesn't filter keys. */
object OtherApps {
    private const val MJOLNIR = "xyz.blacksheep.mjolnir"

    /** Mjolnir's accessibility service is on, so one Home press can be handled twice. */
    fun mjolnirActive(context: Context): Boolean {
        val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return SharedList.containsPackage(services, ':', MJOLNIR)
    }
}
