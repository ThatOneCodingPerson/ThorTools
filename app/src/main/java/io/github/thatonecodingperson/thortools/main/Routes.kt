package io.github.thatonecodingperson.thortools.main

object Routes {
    const val SETTINGS = "settings"
    const val CONTROLLER = "controller"
    const val HOTKEYS = "controller/hotkeys"
    const val CONTROLLER_MODES = "controller/modes"
    const val DESKTOP = "controller/desktop"
    const val DESKTOP_HOTKEY = "controller/desktop/hotkey"
    const val PROFILES = "profiles"
    const val CHARGING = "charging"
    const val LID = "charging/lid"
    const val DISPLAY = "display"
    const val THEMES = "display/themes"
    const val THEME_EDIT = "display/theme/{id}?from={from}"
    const val NEW_THEME = "new"
    const val SETUP = "setup"
    const val SETUP_WIZARD = "setup/wizard"
    const val QUICK_PANEL = "quick_panel"
    const val QUICK_PANEL_EDIT = "quick_panel/edit"
    const val PERMISSIONS = "permissions"
    const val OVERRIDE_LIST = "override/list"
    const val OVERRIDE = "override/{packageName}"
    const val COEXISTENCE = "coexistence"
    const val ODIN_FEATURES = "odintools_features"
    const val DIAGNOSTICS = "diagnostics"
    const val DEBUG = "diagnostics/debug"
    const val EXTRAS = "extras"
    const val LEDS = "extras/leds"
    const val WII = "extras/wii"
    const val RETROARCH = "extras/retroarch"
    const val OLED = "extras/oled"
    const val OLED_AYN = "extras/oled/ayn"
    const val GESTURES = "extras/gestures"

    fun override(packageName: String) = "override/$packageName"

    fun themeEdit(id: String) = "display/theme/$id"

    /** A new theme that starts as a copy of the theme [from]. */
    fun themeCopy(from: String) = "display/theme/$NEW_THEME?from=$from"
}
