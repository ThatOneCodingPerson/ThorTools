package io.github.thatonecodingperson.thortools.main

object Routes {
    const val SETTINGS = "settings"
    const val CONTROLLER = "controller"
    const val HOTKEYS = "controller/hotkeys"
    const val CONTROLLER_MODES = "controller/modes"
    const val PROFILES = "profiles"
    const val CHARGING = "charging"
    const val DISPLAY = "display"
    const val THEMES = "display/themes"
    const val THEME_EDIT = "display/theme/{id}"
    const val NEW_THEME = "new"
    const val SETUP = "setup"
    const val QUICK_PANEL = "quick_panel"
    const val QUICK_PANEL_EDIT = "quick_panel/edit"
    const val PERMISSIONS = "permissions"
    const val OVERRIDE_LIST = "override/list"
    const val OVERRIDE = "override/{packageName}"
    const val COEXISTENCE = "coexistence"
    const val DIAGNOSTICS = "diagnostics"

    fun override(packageName: String) = "override/$packageName"

    fun themeEdit(id: String) = "display/theme/$id"
}
