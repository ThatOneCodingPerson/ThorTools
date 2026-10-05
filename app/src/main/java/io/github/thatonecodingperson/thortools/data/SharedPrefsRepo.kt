package io.github.thatonecodingperson.thortools.data

import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.actions.PanelCloseTarget
import io.github.thatonecodingperson.thortools.charging.Sensitivity
import io.github.thatonecodingperson.thortools.coexist.CoexistencePolicy
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.coexist.Overlap
import io.github.thatonecodingperson.thortools.hotkeys.Hotkey
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyList
import io.github.thatonecodingperson.thortools.leds.LedLook
import io.github.thatonecodingperson.thortools.lid.LidChoices
import io.github.thatonecodingperson.thortools.lid.LidPlan
import io.github.thatonecodingperson.thortools.lid.LidResult
import io.github.thatonecodingperson.thortools.lid.LidSession
import io.github.thatonecodingperson.thortools.lid.WaitUnit
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.L2R2Style
import io.github.thatonecodingperson.thortools.panel.PanelLayout
import io.github.thatonecodingperson.thortools.ui.theme.ThorPalette
import io.github.thatonecodingperson.thortools.ui.theme.ThorThemes
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class SharedPrefsRepo @Inject constructor(@ApplicationContext private val context: Context, private val odinTools: OdinToolsDetector) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(overlap: Overlap): Boolean = CoexistencePolicy.resolve(
        defaultEnabled = overlap.defaultEnabled,
        stored = if (prefs.contains(overlap.prefKey)) prefs.getBoolean(overlap.prefKey, false) else null,
        odinToolsInstalled = odinTools.isInstalled(),
        guardOnly = overlap.guardOnly,
    )

    fun setEnabled(overlap: Overlap, enabled: Boolean) = prefs.edit().putBoolean(overlap.prefKey, enabled).apply()

    private val overlapListeners = mutableMapOf<Any, OnSharedPreferenceChangeListener>()

    /** Calls [onChange] whenever the stored choice for an [Overlap] changes. Unregister with the same [owner]. */
    fun observeOverlaps(owner: Any, onChange: (Overlap) -> Unit) {
        val listener = OnSharedPreferenceChangeListener { _, key ->
            Overlap.entries.find { it.prefKey == key }?.let(onChange)
        }
        overlapListeners.put(owner, listener)?.let(prefs::unregisterOnSharedPreferenceChangeListener)
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun removeOverlapObserver(owner: Any) {
        overlapListeners.remove(owner)?.let(prefs::unregisterOnSharedPreferenceChangeListener)
    }

    var disabledControllerStyle
        get() = prefs.getString(KEY_DISABLED_CONTROLLER_STYLE, null)
        set(value) = prefs.edit().putString(KEY_DISABLED_CONTROLLER_STYLE, value).apply()

    var disabledL2r2Style
        get() = prefs.getString(KEY_DISABLED_L2R2_STYLE, null)
        set(value) = prefs.edit().putString(KEY_DISABLED_L2R2_STYLE, value).apply()

    var saturationOverride
        get() = prefs.getFloat(KEY_SATURATION_OVERRIDE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SATURATION_OVERRIDE, value).apply()

    var vibrationStrength
        get() = prefs.getInt(KEY_VIBRATION_STRENGTH, 0)
        set(value) = prefs.edit().putInt(KEY_VIBRATION_STRENGTH, value).apply()

    var appOverridesEnabled
        get() = prefs.getBoolean(KEY_APP_OVERRIDE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_APP_OVERRIDE_ENABLED, value).apply()

    var overrideDelay
        get() = prefs.getBoolean(KEY_OVERRIDE_DELAY, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERRIDE_DELAY, value).apply()

    var chargeLimitEnabled
        get() = isEnabled(Overlap.CHARGE_AUTOMATION)
        set(value) = setEnabled(Overlap.CHARGE_AUTOMATION, value)

    var minBatteryLevel
        get() = prefs.getInt(KEY_MIN_BATTERY_LEVEL, 20)
        set(value) = prefs.edit().putInt(KEY_MIN_BATTERY_LEVEL, value).apply()

    var maxBatteryLevel
        get() = prefs.getInt(KEY_MAX_BATTERY_LEVEL, 80)
        set(value) = prefs.edit().putInt(KEY_MAX_BATTERY_LEVEL, value).apply()

    private var chargeLimitEnabledListener: OnSharedPreferenceChangeListener? = null

    fun observeChargeLimitEnabledState(onChargeLimitEnabled: (newState: Boolean) -> Unit) {
        chargeLimitEnabledListener = OnSharedPreferenceChangeListener { _, key ->
            if (key == Overlap.CHARGE_AUTOMATION.prefKey) {
                onChargeLimitEnabled(chargeLimitEnabled)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(chargeLimitEnabledListener)
    }

    fun removeChargeLimitEnabledObserver() {
        prefs.unregisterOnSharedPreferenceChangeListener(chargeLimitEnabledListener)
        chargeLimitEnabledListener = null
    }

    private var appOverrideEnabledListener: OnSharedPreferenceChangeListener? = null

    fun observeAppOverrideEnabledState(
        onAppOverridesEnabled: (newState: Boolean) -> Unit,
        onOverrideDelayEnabled: (newState: Boolean) -> Unit,
    ) {
        appOverrideEnabledListener = OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_APP_OVERRIDE_ENABLED) {
                onAppOverridesEnabled(appOverridesEnabled)
            } else if (key == KEY_OVERRIDE_DELAY) {
                onOverrideDelayEnabled(overrideDelay)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(appOverrideEnabledListener)
    }

    fun removeAppOverrideEnabledObserver() {
        prefs.unregisterOnSharedPreferenceChangeListener(appOverrideEnabledListener)
        appOverrideEnabledListener = null
    }

    var chargeAlertEnabled
        get() = prefs.getBoolean(KEY_CHARGE_ALERT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CHARGE_ALERT_ENABLED, value).apply()

    var chargeAlertSensitivity: Sensitivity
        get() = Sensitivity.entries.find { it.name == prefs.getString(KEY_CHARGE_ALERT_SENSITIVITY, null) } ?: Sensitivity.MEDIUM
        set(value) = prefs.edit().putString(KEY_CHARGE_ALERT_SENSITIVITY, value.name).apply()

    var chargeAlertSnoozedUntil
        get() = prefs.getLong(KEY_CHARGE_ALERT_SNOOZED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_CHARGE_ALERT_SNOOZED_UNTIL, value).apply()

    private var chargeAlertListener: OnSharedPreferenceChangeListener? = null

    fun observeChargeAlert(onEnabled: (Boolean) -> Unit, onSensitivity: (Sensitivity) -> Unit) {
        chargeAlertListener = OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_CHARGE_ALERT_ENABLED -> onEnabled(chargeAlertEnabled)
                KEY_CHARGE_ALERT_SENSITIVITY -> onSensitivity(chargeAlertSensitivity)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(chargeAlertListener)
    }

    fun removeChargeAlertObserver() {
        prefs.unregisterOnSharedPreferenceChangeListener(chargeAlertListener)
        chargeAlertListener = null
    }

    var themeId: String
        get() = prefs.getString(KEY_THEME_ID, null) ?: ThorThemes.default.id
        set(value) = prefs.edit().putString(KEY_THEME_ID, value).apply()

    /** User-made themes, one encoded [ThorPalette] per line. */
    var customPalettes: List<ThorPalette>
        get() = prefs.getString(KEY_CUSTOM_PALETTES, null)?.lines()?.mapNotNull(ThorPalette::decode).orEmpty()
        set(value) = prefs.edit().putString(KEY_CUSTOM_PALETTES, value.joinToString("\n") { it.encode() }).apply()

    /** Adds a user theme, or replaces the one with the same id. */
    fun saveCustomPalette(palette: ThorPalette) {
        customPalettes = customPalettes.filter { it.id != palette.id } + palette
    }

    /** Deleting the theme in use falls back to the default theme. */
    fun deleteCustomPalette(id: String) {
        customPalettes = customPalettes.filter { it.id != id }
        if (themeId == id) themeId = ThorThemes.default.id
    }

    /** The active palette; null means Android's dynamic colours. */
    fun palette(): ThorPalette? = ThorThemes.resolve(themeId, customPalettes)

    fun paletteChanges(): Flow<ThorPalette?> = callbackFlow {
        val listener = OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_THEME_ID || key == KEY_CUSTOM_PALETTES) trySend(palette())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(palette())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    var panelTakesController
        get() = prefs.getBoolean(KEY_PANEL_TAKES_CONTROLLER, true)
        set(value) = prefs.edit().putBoolean(KEY_PANEL_TAKES_CONTROLLER, value).apply()

    /** Whether the panel's Close background apps tile also cleans memory. */
    var panelCleanMemory
        get() = prefs.getBoolean(KEY_PANEL_CLEAN_MEMORY, false)
        set(value) = prefs.edit().putBoolean(KEY_PANEL_CLEAN_MEMORY, value).apply()

    /** Only a tap of the AYN button or the panel's close button closes the quick panel (`PanelClosing`). */
    var panelOnlyAynCloses
        get() = prefs.getBoolean(KEY_PANEL_ONLY_AYN_CLOSES, true)
        set(value) = prefs.edit().putBoolean(KEY_PANEL_ONLY_AYN_CLOSES, value).apply()

    /** The apps opened last, newest first, for the Recent apps widget. */
    var recentApps: List<String>
        get() = prefs.getString(KEY_RECENT_APPS, null)?.lines()?.filter { it.isNotBlank() }.orEmpty()
        set(value) = prefs.edit().putString(KEY_RECENT_APPS, value.joinToString("\n")).apply()

    /** The stick lights as chosen on their screen. */
    var ledLook: LedLook
        get() = LedLook.decode(prefs.getString(KEY_LED_LOOK, null))
        set(value) = prefs.edit().putString(KEY_LED_LOOK, value.encode()).apply()

    fun ledLookChanges(): Flow<LedLook> = callbackFlow {
        val listener = OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LED_LOOK || key == null) trySend(ledLook)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(ledLook)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** A Wii profile builder tab's mapping, as `WiiMapping.encode` stores it; null for the suggestion. */
    fun wiiMapping(setup: String): String? = prefs.getString(KEY_WII_MAPPING + setup, null)

    fun setWiiMapping(setup: String, text: String?) = prefs.edit().putString(KEY_WII_MAPPING + setup, text).apply()

    /** Which set of suggestions the Wii profile builder's drafts were made with. */
    var wiiSuggestions: Int
        get() = prefs.getInt(KEY_WII_SUGGESTIONS, 1)
        set(value) = prefs.edit().putInt(KEY_WII_SUGGESTIONS, value).apply()

    /** The profile name typed on a Wii profile builder tab; null for the default. */
    fun wiiName(setup: String): String? = prefs.getString(KEY_WII_NAME + setup, null)

    fun setWiiName(setup: String, name: String?) = prefs.edit().putString(KEY_WII_NAME + setup, name).apply()

    /** AYN's own light settings from before Thor Tools first changed them; null while they are AYN's again. */
    var ledAynSaved: String?
        get() = prefs.getString(KEY_LED_AYN_SAVED, null)
        set(value) = prefs.edit().putString(KEY_LED_AYN_SAVED, value).apply()

    /** Diagnostics shows the debug toolkit. */
    var debugMode
        get() = prefs.getBoolean(KEY_DEBUG_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_MODE, value).apply()

    /** The debug toolkit writes its results to one file, rewritten on each use. */
    var debugReportFile
        get() = prefs.getBoolean(KEY_DEBUG_REPORT_FILE, true)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_REPORT_FILE, value).apply()

    /** The setup wizard was finished or skipped; it then only opens when asked for. */
    var setupDone
        get() = prefs.getBoolean(KEY_SETUP_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_DONE, value).apply()

    /** The lid sandbox's switches; all off by default. */
    var lidChoices: LidChoices
        get() {
            val unit = WaitUnit.byId(prefs.getString(KEY_LID_SLEEP_UNIT, null)) ?: WaitUnit.SECONDS
            return LidChoices(
                enabled = prefs.getBoolean(KEY_LID_ENABLED, true),
                powerSaving = prefs.getBoolean(KEY_LID_POWER_SAVING, false),
                closeBackground = prefs.getBoolean(KEY_LID_CLOSE_BACKGROUND, false),
                pauseMedia = prefs.getBoolean(KEY_LID_PAUSE_MEDIA, false),
                wifiOff = prefs.getBoolean(KEY_LID_WIFI_OFF, false),
                bluetoothOff = prefs.getBoolean(KEY_LID_BLUETOOTH_OFF, false),
                airplane = prefs.getBoolean(KEY_LID_AIRPLANE, false),
                backToSleep = prefs.getBoolean(KEY_LID_BACK_TO_SLEEP, false),
                sleepWait = unit.clamp(prefs.getInt(KEY_LID_SLEEP_WAIT, unit.default)),
                sleepUnit = unit,
                delayMinutes = prefs.getInt(KEY_LID_DELAY, 0).takeIf { it in LidPlan.DELAYS } ?: 0,
                notWhileMedia = prefs.getBoolean(KEY_LID_NOT_WHILE_MEDIA, false),
            )
        }
        set(value) = prefs.edit()
            .putBoolean(KEY_LID_ENABLED, value.enabled)
            .putBoolean(KEY_LID_POWER_SAVING, value.powerSaving)
            .putBoolean(KEY_LID_CLOSE_BACKGROUND, value.closeBackground)
            .putBoolean(KEY_LID_PAUSE_MEDIA, value.pauseMedia)
            .putBoolean(KEY_LID_WIFI_OFF, value.wifiOff)
            .putBoolean(KEY_LID_BLUETOOTH_OFF, value.bluetoothOff)
            .putBoolean(KEY_LID_AIRPLANE, value.airplane)
            .putBoolean(KEY_LID_BACK_TO_SLEEP, value.backToSleep)
            .putInt(KEY_LID_SLEEP_WAIT, value.sleepWait)
            .putString(KEY_LID_SLEEP_UNIT, value.sleepUnit.id)
            .putInt(KEY_LID_DELAY, value.delayMinutes)
            .putBoolean(KEY_LID_NOT_WHILE_MEDIA, value.notWhileMedia)
            .apply()

    /** Input devices left muted through the kernel were looked for and unmuted; the keys of gone switches are dropped then. */
    var lidMutesCleared: Boolean
        get() = prefs.getBoolean(KEY_LID_MUTES_CLEARED, false)
        set(value) {
            val edit = prefs.edit().putBoolean(KEY_LID_MUTES_CLEARED, value)
            OLD_LID_KEYS.forEach(edit::remove)
            edit.apply()
        }

    /**
     * What a closed lid changed and must put back. Written with `commit` before anything changes, so it survives a
     * crash or a reboot; null once the lid opened and everything was put back.
     */
    var lidSession: LidSession?
        get() = LidSession.decode(prefs.getString(KEY_LID_SESSION, null))
        set(value) {
            prefs.edit().putString(KEY_LID_SESSION, value?.encode()).commit()
        }

    /** How the last closing went, as its screen shows it. */
    var lidLastResult: LidResult?
        get() = LidResult.decode(prefs.getString(KEY_LID_LAST, null))
        set(value) = prefs.edit().putString(KEY_LID_LAST, value?.encode()).apply()

    fun lidResultChanges(): Flow<LidResult?> = callbackFlow {
        val listener = OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LID_LAST || key == null) trySend(lidLastResult)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(lidLastResult)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Which app the panel's Close the current app tile closes. */
    var panelCloseAppTarget: PanelCloseTarget
        get() = PanelCloseTarget.byId(prefs.getString(KEY_PANEL_CLOSE_APP, null)) ?: PanelCloseTarget.OPENED_FROM
        set(value) = prefs.edit().putString(KEY_PANEL_CLOSE_APP, value.id).apply()

    /** The quick panel's pages (see `PanelLayout`); edited under Quick panel -> Edit panel. */
    var panelLayout: PanelLayout
        get() = PanelLayout.decode(prefs.getString(KEY_PANEL_LAYOUT, null))
        set(value) = prefs.edit().putString(KEY_PANEL_LAYOUT, value.encode()).apply()

    /**
     * The hotkeys (see `HotkeyList`); nothing stored means the defaults. The old single Messages switch decides the text
     * switch of hotkeys saved before each hotkey had its own.
     */
    var hotkeys: List<Hotkey>
        get() = HotkeyList.decode(prefs.getString(KEY_HOTKEYS, null), textByDefault = prefs.getBoolean(KEY_HOTKEY_MESSAGES, true))
        set(value) = prefs.edit().putString(KEY_HOTKEYS, HotkeyList.encode(value)).apply()

    fun resetHotkeys() = prefs.edit().remove(KEY_HOTKEYS).apply()

    /**
     * Once: a saved hotkey list with nothing on a single tap of AYN gets the Thor Tools quick panel back, the default
     * every list should have. Removing or rebinding it afterwards sticks.
     */
    fun restoreAynPanelOnce() {
        if (prefs.getBoolean(KEY_AYN_PANEL_RESTORED, false)) return
        if (prefs.contains(KEY_HOTKEYS)) {
            val list = hotkeys
            val restored = HotkeyList.withAynPanel(list)
            if (restored != list) hotkeys = restored
        }
        prefs.edit().putBoolean(KEY_AYN_PANEL_RESTORED, true).apply()
    }

    /**
     * Once: a saved hotkey list gets Home's and Back's own tap listed ([HotkeyList.ownPresses]) where that tap
     * is free; game buttons' own presses, no longer allowed, are dropped when the list is read and so leave it here.
     */
    fun addOwnPressesOnce() {
        if (prefs.getBoolean(KEY_OWN_PRESSES_ADDED, false)) return
        if (prefs.contains(KEY_HOTKEYS)) hotkeys = HotkeyList.withOwnPresses(hotkeys)
        prefs.edit().putBoolean(KEY_OWN_PRESSES_ADDED, true).apply()
    }

    fun hotkeyChanges(): Flow<List<Hotkey>> = callbackFlow {
        val listener = OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_HOTKEYS || key == null) trySend(hotkeys)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(hotkeys)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Apps in which only the AYN button's hotkeys work; every other button goes to the app untouched. */
    var hotkeyOffApps: Set<String>
        get() = prefs.getString(KEY_HOTKEY_OFF_APPS, null)?.lines()?.filter { it.isNotBlank() }?.toSet().orEmpty()
        set(value) = prefs.edit().putString(KEY_HOTKEY_OFF_APPS, value.sorted().joinToString("\n")).apply()

    fun hotkeyOffAppsChanges(): Flow<Set<String>> = callbackFlow {
        val listener = OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_HOTKEY_OFF_APPS || key == null) trySend(hotkeyOffApps)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(hotkeyOffApps)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    var videoOutputOverrideEnabled
        get() = isEnabled(Overlap.EXTERNAL_DISPLAY_STYLE)
        set(value) = setEnabled(Overlap.EXTERNAL_DISPLAY_STYLE, value)

    var videoOutputControllerStyle
        get() = prefs.getString(KEY_VIDEO_OUTPUT_CONTROLLER_STYLE, ControllerStyle.Unknown.id)
        set(value) = prefs.edit().putString(KEY_VIDEO_OUTPUT_CONTROLLER_STYLE, value).apply()

    var videoOutputL2R2Style
        get() = prefs.getString(KEY_VIDEO_OUTPUT_L2R2_STYLE, L2R2Style.Unknown.id)
        set(value) = prefs.edit().putString(KEY_VIDEO_OUTPUT_L2R2_STYLE, value).apply()

    private var videoOutputOverrideEnabledListener: OnSharedPreferenceChangeListener? = null

    fun observeVideoOutputOverrideEnabledState(onVideoOutputOverrideEnabled: (newState: Boolean) -> Unit) {
        videoOutputOverrideEnabledListener = OnSharedPreferenceChangeListener { _, key ->
            if (key == Overlap.EXTERNAL_DISPLAY_STYLE.prefKey) {
                onVideoOutputOverrideEnabled(videoOutputOverrideEnabled)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(videoOutputOverrideEnabledListener)
    }

    fun removeVideoOutputOverrideEnabledObserver() {
        prefs.unregisterOnSharedPreferenceChangeListener(videoOutputOverrideEnabledListener)
        videoOutputOverrideEnabledListener = null
    }

    companion object {
        private const val PREFS_NAME = "thortools"

        private const val KEY_THEME_ID = "theme_id"
        private const val KEY_PANEL_TAKES_CONTROLLER = "panel_takes_controller"
        private const val KEY_PANEL_LAYOUT = "panel_layout"
        private const val KEY_PANEL_CLOSE_APP = "panel_close_app_target"
        private const val KEY_PANEL_CLEAN_MEMORY = "panel_clean_memory"
        private const val KEY_PANEL_ONLY_AYN_CLOSES = "panel_only_ayn_closes"
        private const val KEY_SETUP_DONE = "setup_done"
        private const val KEY_RECENT_APPS = "recent_apps"
        private const val KEY_DEBUG_MODE = "debug_mode"
        private const val KEY_LED_LOOK = "led_look"
        private const val KEY_WII_MAPPING = "wii_mapping_"
        private const val KEY_WII_NAME = "wii_name_"
        private const val KEY_WII_SUGGESTIONS = "wii_suggestions"
        private const val KEY_LED_AYN_SAVED = "led_ayn_saved"
        private const val KEY_DEBUG_REPORT_FILE = "debug_report_file"
        private const val KEY_LID_ENABLED = "lid_enabled"
        private const val KEY_LID_POWER_SAVING = "lid_power_saving"
        private const val KEY_LID_CLOSE_BACKGROUND = "lid_close_background"
        private const val KEY_LID_PAUSE_MEDIA = "lid_pause_media"
        private const val KEY_LID_WIFI_OFF = "lid_wifi_off"
        private const val KEY_LID_BLUETOOTH_OFF = "lid_bluetooth_off"
        private const val KEY_LID_AIRPLANE = "lid_airplane"
        private const val KEY_LID_BACK_TO_SLEEP = "lid_back_to_sleep"
        private const val KEY_LID_SLEEP_WAIT = "lid_sleep_wait"
        private const val KEY_LID_SLEEP_UNIT = "lid_sleep_unit"
        private const val KEY_LID_DELAY = "lid_delay_minutes"
        private const val KEY_LID_NOT_WHILE_MEDIA = "lid_not_while_media"
        private const val KEY_LID_MUTES_CLEARED = "lid_mutes_cleared"
        private val OLD_LID_KEYS = listOf("lid_mute_buttons", "lid_mute_controller", "lid_mute_touch", "lid_keep_buttons_for")
        private const val KEY_LID_SESSION = "lid_session"
        private const val KEY_LID_LAST = "lid_last_result"
        private const val KEY_CUSTOM_PALETTES = "custom_palettes"
        private const val KEY_HOTKEYS = "hotkey_list"
        private const val KEY_AYN_PANEL_RESTORED = "hotkey_ayn_panel_restored"
        private const val KEY_OWN_PRESSES_ADDED = "hotkey_own_presses_added"
        private const val KEY_HOTKEY_MESSAGES = "hotkey_messages"
        private const val KEY_HOTKEY_OFF_APPS = "hotkey_off_apps"

        private const val KEY_DISABLED_CONTROLLER_STYLE = "disabled_controller_style"
        private const val KEY_DISABLED_L2R2_STYLE = "disabled_l2r2_style"
        private const val KEY_SATURATION_OVERRIDE = "saturation_override"
        private const val KEY_VIBRATION_STRENGTH = "vibration_strength"
        private const val KEY_APP_OVERRIDE_ENABLED = "app_override_enabled"
        private const val KEY_OVERRIDE_DELAY = "override_delay"
        private const val KEY_MIN_BATTERY_LEVEL = "min_battery_level"
        private const val KEY_MAX_BATTERY_LEVEL = "max_battery_level"
        private const val KEY_CHARGE_ALERT_ENABLED = "charge_alert_enabled"
        private const val KEY_CHARGE_ALERT_SENSITIVITY = "charge_alert_sensitivity"
        private const val KEY_CHARGE_ALERT_SNOOZED_UNTIL = "charge_alert_snoozed_until"
        private const val KEY_VIDEO_OUTPUT_CONTROLLER_STYLE = "video_output_override_controller_style"
        private const val KEY_VIDEO_OUTPUT_L2R2_STYLE = "video_output_override_l2r2_style"
    }
}
