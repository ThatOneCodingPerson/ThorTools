package io.github.thatonecodingperson.thortools.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.appsettings.AppOverrideListScreen
import io.github.thatonecodingperson.thortools.appsettings.AppOverridesScreen
import io.github.thatonecodingperson.thortools.appsettings.ProfilesScreen
import io.github.thatonecodingperson.thortools.charging.ChargingScreen
import io.github.thatonecodingperson.thortools.coexist.CoexistenceScreen
import io.github.thatonecodingperson.thortools.coexist.LeftoverBuildBanner
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.controller.ControllerModesScreen
import io.github.thatonecodingperson.thortools.controller.ControllerScreen
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.diagnostics.DiagnosticsScreen
import io.github.thatonecodingperson.thortools.diagnostics.SetupScreen
import io.github.thatonecodingperson.thortools.display.DisplayScreen
import io.github.thatonecodingperson.thortools.display.ThemeEditorScreen
import io.github.thatonecodingperson.thortools.display.ThemesScreen
import io.github.thatonecodingperson.thortools.hotkeys.HotkeysScreen
import io.github.thatonecodingperson.thortools.hotkeys.toPadSample
import io.github.thatonecodingperson.thortools.input.PadDirections
import io.github.thatonecodingperson.thortools.lid.LidScreen
import io.github.thatonecodingperson.thortools.panel.PanelEditorScreen
import io.github.thatonecodingperson.thortools.panel.PanelSettingsScreen
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import io.github.thatonecodingperson.thortools.setup.PermissionsScreen
import io.github.thatonecodingperson.thortools.ui.composables.PServerNotAvailableDialog
import io.github.thatonecodingperson.thortools.ui.composables.ThorTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.TriggerPreference
import io.github.thatonecodingperson.thortools.ui.composables.UnsupportedDeviceDialog
import io.github.thatonecodingperson.thortools.ui.theme.ThorToolsTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var prefs: SharedPrefsRepo

    @Inject
    lateinit var status: ServiceStatus

    /** A screen to open, asked for by the quick panel's edit button ([EXTRA_OPEN]); only known values are followed. */
    private val openRequest = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest.value = intent.getStringExtra(EXTRA_OPEN)
    }

    // The quick panel's Thor Tools tile checks where this came up.
    override fun onResume() {
        super.onResume()
        status.appShownOn = display?.displayId ?: Display.DEFAULT_DISPLAY
    }

    override fun onPause() {
        super.onPause()
        status.appShownOn = null
    }

    // While the hotkey editor records, the D-pad and sticks reach this window (it has the controller): they become
    // directions for the recorder, like the keys the service hands it.
    private val recordedDirections = PadDirections()
    private var recordingFor: Any? = null

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val record = status.buttonRecorder
        if (record !== recordingFor) {
            recordingFor = record
            recordedDirections.reset()
        }
        if (record == null || !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return super.dispatchGenericMotionEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            recordedDirections.update(event.toPadSample()).forEach { record(it.button, it.down) }
        }
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openRequest.value = intent.getStringExtra(EXTRA_OPEN)
        setContent {
            val paletteChanges = remember { prefs.paletteChanges() }
            val palette by paletteChanges.collectAsState(initial = prefs.palette())
            ThorToolsTheme(palette) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val back: () -> Unit = { navController.popBackStack() }
                    val request by openRequest
                    LaunchedEffect(request) {
                        if (request == OPEN_PANEL_EDITOR) {
                            navController.navigate(Routes.QUICK_PANEL)
                            navController.navigate(Routes.QUICK_PANEL_EDIT)
                        }
                        openRequest.value = null
                    }
                    NavHost(navController = navController, startDestination = Routes.SETTINGS) {
                        composable(Routes.SETTINGS) {
                            SettingsScreen { navController.navigate(it) }
                        }
                        composable(Routes.CONTROLLER) {
                            ControllerScreen(
                                onModes = { navController.navigate(Routes.CONTROLLER_MODES) },
                                onHotkeys = { navController.navigate(Routes.HOTKEYS) },
                                onBack = back,
                            )
                        }
                        composable(Routes.HOTKEYS) {
                            HotkeysScreen(onBack = back)
                        }
                        composable(Routes.CONTROLLER_MODES) {
                            ControllerModesScreen(onBack = back)
                        }
                        composable(Routes.QUICK_PANEL) {
                            PanelSettingsScreen(onEditPanel = { navController.navigate(Routes.QUICK_PANEL_EDIT) }, onBack = back)
                        }
                        composable(Routes.QUICK_PANEL_EDIT) {
                            PanelEditorScreen(onBack = back)
                        }
                        composable(Routes.PROFILES) {
                            ProfilesScreen(onAppOverrides = { navController.navigate(Routes.OVERRIDE_LIST) }, onBack = back)
                        }
                        composable(Routes.CHARGING) {
                            ChargingScreen(onLid = { navController.navigate(Routes.LID) }, onBack = back)
                        }
                        composable(Routes.LID) {
                            LidScreen(onBack = back)
                        }
                        composable(Routes.DISPLAY) {
                            DisplayScreen(onThemes = { navController.navigate(Routes.THEMES) }, onBack = back)
                        }
                        composable(Routes.THEMES) {
                            ThemesScreen(onEdit = { navController.navigate(Routes.themeEdit(it)) }, onBack = back)
                        }
                        composable(Routes.THEME_EDIT) {
                            ThemeEditorScreen(navigateBack = back)
                        }
                        composable(Routes.PERMISSIONS) {
                            PermissionsScreen(onBack = back)
                        }
                        composable(Routes.SETUP) {
                            SetupScreen(
                                onPermissions = { navController.navigate(Routes.PERMISSIONS) },
                                onCoexistence = { navController.navigate(Routes.COEXISTENCE) },
                                onDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                                onBack = back,
                            )
                        }
                        composable(Routes.OVERRIDE_LIST) {
                            AppOverrideListScreen(onBack = back) { navController.navigate(Routes.override(it)) }
                        }
                        composable(Routes.OVERRIDE) {
                            AppOverridesScreen(navigateBack = back)
                        }
                        composable(Routes.COEXISTENCE) {
                            CoexistenceScreen(onBack = back)
                        }
                        composable(Routes.DIAGNOSTICS) {
                            DiagnosticsScreen(onBack = back)
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Intent extra: a screen to open on start. */
        const val EXTRA_OPEN = "thortools.open"

        /** [EXTRA_OPEN] value: the quick panel editor. */
        const val OPEN_PANEL_EDITOR = "panel_editor"
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel = hiltViewModel(), navigate: (route: String) -> Unit) {
    val uiState: MainUiModel by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshCoexistence() }

    val context = LocalContext.current

    if (uiState.showPServerNotAvailableDialog) {
        PServerNotAvailableDialog {
            viewModel.pServerDialogDismissed()
            navigate(Routes.DIAGNOSTICS)
        }
    } else if (uiState.showIncompatibleDeviceDialog) {
        UnsupportedDeviceDialog { viewModel.incompatibleDeviceDialogDismissed() }
    }

    Scaffold(topBar = { ThorTopAppBar(deviceVersion = uiState.deviceVersion) }) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(top = contentPadding.calculateTopPadding()) // Top app bar padding
                .padding(end = 8.dp) // Extra padding cause of GameAssist bar overlay
                .verticalScroll(rememberScrollState()),
        ) {
            if (uiState.odinTools.leftoverThorTools) {
                LeftoverBuildBanner {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", OdinToolsDetector.PACKAGE, null)),
                    )
                }
            }

            TriggerPreference(
                icon = R.drawable.ic_gamepad,
                title = R.string.controllerAndButtons,
                description = R.string.controllerAndButtonsSummary,
            ) { navigate(Routes.CONTROLLER) }
            TriggerPreference(
                icon = R.drawable.ic_sliders,
                title = R.string.quickPanel,
                description = R.string.quickPanelDescription,
            ) { navigate(Routes.QUICK_PANEL) }
            TriggerPreference(
                icon = R.drawable.ic_app_settings,
                title = R.string.appProfiles,
                description = R.string.appProfilesSummary,
            ) { navigate(Routes.PROFILES) }
            TriggerPreference(
                icon = R.drawable.ic_battery,
                title = R.string.powerManagement,
                description = R.string.powerManagementSummary,
            ) { navigate(Routes.CHARGING) }
            TriggerPreference(
                icon = R.drawable.ic_palette,
                title = R.string.display,
                description = R.string.displaySummary,
            ) { navigate(Routes.DISPLAY) }
            TriggerPreference(
                icon = R.drawable.ic_info,
                title = R.string.setupAndDiagnostics,
                description = R.string.setupAndDiagnosticsSummary,
                tag = R.string.tagCheck.takeIf { uiState.accessNeedsAttention },
            ) { navigate(Routes.SETUP) }

            // Navigation bar padding
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
        }
    }
}
