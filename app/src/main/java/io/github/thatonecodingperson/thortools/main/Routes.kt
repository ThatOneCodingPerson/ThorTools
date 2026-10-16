package io.github.thatonecodingperson.thortools.main

import android.net.Uri

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
    const val ROMS = "roms"
    const val ROM_CONSOLE = "roms/console/{console}"
    const val ROM_GAME = "roms/game?game={game}"

    fun override(packageName: String) = "override/$packageName"

    fun themeEdit(id: String) = "display/theme/$id"

    fun romConsole(id: String) = "roms/console/$id"

    /** A game's page; [id] is its path, encoded for the route. */
    fun romGame(id: String) = "roms/game?game=${Uri.encode(id)}"

    /** A new theme that starts as a copy of the theme [from]. */
    fun themeCopy(from: String) = "display/theme/$NEW_THEME?from=$from"
}
