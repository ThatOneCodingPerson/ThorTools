package io.github.thatonecodingperson.thortools.oled

/**
 * AYN's own OLED protection on the Thor. While neither screen's picture changes (no new frame in a full-screen window,
 * under 15 fps overall), the system moves both screens by [radius] pixels after [shiftAfterMs] (the shifter) and shows
 * AYN's refresher after [refreshAfterMs]; any change or a touch starts over, so it never runs in games or videos.
 * AYN's settings app keeps the values as text in [SETTING]; the system itself only takes them from a window manager
 * call ([SET_VALUES]), which the root helper makes. Pure.
 */
data class AynProtection(val shifter: Boolean, val radius: Int, val shiftAfterMs: Int, val refresher: Boolean, val refreshAfterMs: Int) {
    /** In AYN's own order: detectSwitch, offsetRadius, detectDuration, refresherSwitch, refresherDuration. */
    fun values(): List<Int> = listOf(if (shifter) 1 else 0, radius, shiftAfterMs, if (refresher) 1 else 0, refreshAfterMs)

    fun encode(): String = values().joinToString(",")

    /**
     * What AYN runs with these values chosen: its shifter off while Thor Tools' own shifter runs, its refresher off while
     * Thor Tools' refresher runs by itself.
     */
    fun applied(ownShifter: Boolean, ownRefresher: Boolean): AynProtection =
        copy(shifter = shifter && !ownShifter, refresher = refresher && !ownRefresher)

    companion object {
        /** `Settings.System` key where AYN's settings app keeps the values. */
        const val SETTING = "bip.detect.wm.frame.stats.setting.arg"

        /** What AYN's settings app uses while the setting is empty. */
        val DEFAULT = AynProtection(shifter = true, radius = 1, shiftAfterMs = 3_000, refresher = true, refreshAfterMs = 30_000)

        /** The window manager call that hands the system the five values: binder service, interface token, code. */
        const val WINDOW_SERVICE = "window"
        const val WINDOW_TOKEN = "android.view.IWindowManager"
        const val SET_VALUES = 8192

        /** AYN's settings app's own binder service, and the call that shows its refresher on both screens now. */
        const val SETTINGS_SERVICE = "SettingsController"
        const val SETTINGS_TOKEN = "com.ro.settings.IExternalControlManager"
        const val SHOW_REFRESHER = 2048

        /** What the controls offer; AYN's own page takes any number, and a value outside is left as it is. */
        val RADIUS = 1..20
        val SHIFT_AFTER_MS = 1_000..60_000
        val REFRESH_AFTER_MS = 10_000..600_000

        /** `1,1,3000,1,30000`; null for anything else. */
        fun decode(text: String?): AynProtection? {
            val parts = text?.trim()?.split(',')?.map { it.trim().toIntOrNull() ?: return null } ?: return null
            if (parts.size != 5) return null
            return AynProtection(parts[0] == 1, parts[1], parts[2], parts[3] == 1, parts[4])
        }

        /** As AYN's settings app reads it: empty means its defaults. */
        fun fromSetting(text: String?): AynProtection = if (text.isNullOrBlank() || text == "null") DEFAULT else decode(text) ?: DEFAULT
    }
}
