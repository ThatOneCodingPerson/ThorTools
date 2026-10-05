package io.github.thatonecodingperson.thortools.setup

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import io.github.thatonecodingperson.thortools.tools.SettingsRepo
import io.github.thatonecodingperson.thortools.ui.composables.SubScreen
import io.github.thatonecodingperson.thortools.ui.composables.TriggerPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A system screen the user has to finish a fix in, because root couldn't do it. */
enum class SystemScreen { ACCESSIBILITY, NOTIFICATION_PERMISSION, NOTIFICATION_SETTINGS, BATTERY }

data class PermissionsUiModel(val report: AccessReport = AccessReport.of(), val open: SystemScreen? = null)

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val checker: AccessChecker,
    private val settings: SettingsRepo,
    private val status: ServiceStatus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionsUiModel(checker.quick()))
    val uiState: StateFlow<PermissionsUiModel> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) { checker.full() }
            _uiState.update { it.copy(report = report) }
        }
    }

    /** Root first; only what root can't do goes to Android's own screens. */
    fun fix(check: AccessCheck) {
        if (_uiState.value.report.state(check) != AccessState.MISSING) return
        viewModelScope.launch {
            val open = withContext(Dispatchers.IO) {
                when (check) {
                    AccessCheck.ROOT -> null
                    AccessCheck.ACCESSIBILITY -> {
                        settings.applyRequiredSettings()
                        delay(SERVICE_START_MS)
                        SystemScreen.ACCESSIBILITY.takeUnless { status.connected }
                    }
                    AccessCheck.NOTIFICATIONS -> when {
                        settings.grantNotifications() -> null
                        settings.notificationPermissionGranted() -> SystemScreen.NOTIFICATION_SETTINGS
                        else -> SystemScreen.NOTIFICATION_PERMISSION
                    }
                    AccessCheck.BATTERY -> {
                        settings.exemptFromBatteryOptimisation()
                        SystemScreen.BATTERY.takeUnless { settings.isBatteryExempt() }
                    }
                    AccessCheck.BACKGROUND_LIST -> {
                        settings.addSelfToWhitelist()
                        null
                    }
                    AccessCheck.INPUT_HELPER -> {
                        status.rawInput?.start()
                        delay(HELPER_START_MS)
                        null
                    }
                }
            }
            status.onAccessChanged?.invoke()
            _uiState.update { it.copy(open = open) }
            refresh()
        }
    }

    fun systemScreenOpened() {
        _uiState.update { it.copy(open = null) }
    }

    private companion object {
        const val SERVICE_START_MS = 1500L
        const val HELPER_START_MS = 2000L
    }
}

private data class CheckText(@StringRes val title: Int, @StringRes val ok: Int, @StringRes val missing: Int, @StringRes val notNeeded: Int)

private val texts = mapOf(
    AccessCheck.ROOT to CheckText(R.string.accessRoot, R.string.accessRootOk, R.string.accessRootMissing, R.string.accessRootOk),
    AccessCheck.ACCESSIBILITY to
        CheckText(
            R.string.accessAccessibility,
            R.string.accessAccessibilityOk,
            R.string.accessAccessibilityMissing,
            R.string.accessAccessibilityOk,
        ),
    AccessCheck.NOTIFICATIONS to
        CheckText(
            R.string.accessNotifications,
            R.string.accessNotificationsOk,
            R.string.accessNotificationsMissing,
            R.string.accessNotificationsOk,
        ),
    AccessCheck.BATTERY to
        CheckText(R.string.accessBattery, R.string.accessBatteryOk, R.string.accessBatteryMissing, R.string.accessBatteryOk),
    AccessCheck.BACKGROUND_LIST to
        CheckText(
            R.string.accessBackgroundList,
            R.string.accessBackgroundListOk,
            R.string.accessBackgroundListMissing,
            R.string.accessBackgroundListNotNeeded,
        ),
    AccessCheck.INPUT_HELPER to
        CheckText(
            R.string.accessInputHelper,
            R.string.accessInputHelperOk,
            R.string.accessInputHelperMissing,
            R.string.accessInputHelperNotNeeded,
        ),
)

@Composable
fun PermissionsScreen(viewModel: PermissionsViewModel = hiltViewModel(), onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(uiState.open) {
        val target = uiState.open ?: return@LaunchedEffect
        val app = Uri.parse("package:${context.packageName}")
        when (target) {
            SystemScreen.NOTIFICATION_PERMISSION -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            SystemScreen.NOTIFICATION_SETTINGS ->
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            SystemScreen.ACCESSIBILITY -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            SystemScreen.BATTERY -> context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, app))
        }
        viewModel.systemScreenOpened()
    }

    SubScreen(title = R.string.permissionsTitle, onBack = onBack) {
        AccessCheck.entries.forEach { check ->
            val text = texts.getValue(check)
            val state = uiState.report.state(check)
            TriggerPreference(
                icon = R.drawable.ic_info,
                title = stringResource(text.title),
                description = stringResource(
                    when (state) {
                        AccessState.OK -> text.ok
                        AccessState.MISSING -> text.missing
                        AccessState.NOT_NEEDED -> text.notNeeded
                        AccessState.UNKNOWN -> R.string.accessChecking
                    },
                ),
                tag = stringResource(
                    when (state) {
                        AccessState.OK -> R.string.tagOk
                        AccessState.MISSING -> R.string.tagFix
                        AccessState.NOT_NEEDED -> R.string.tagNotNeeded
                        AccessState.UNKNOWN -> R.string.tagChecking
                    },
                ),
            ) { viewModel.fix(check) }
        }
    }
}
