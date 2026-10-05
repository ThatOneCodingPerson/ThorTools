package io.github.thatonecodingperson.thortools.main

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppSettingsAlt
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thatonecodingperson.thortools.BuildConfig
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.coexist.LeftoverBuildBanner
import io.github.thatonecodingperson.thortools.coexist.OdinToolsDetector
import io.github.thatonecodingperson.thortools.ui.composables.PServerNotAvailableDialog
import io.github.thatonecodingperson.thortools.ui.composables.UnsupportedDeviceDialog

/** One card of the main menu: a section, and a line or two about how it stands now. */
private class Section(val icon: ImageVector, val title: String, val lines: List<String>, val route: String, val attention: String? = null)

/**
 * The main menu: the app's state at the top, shortcuts to the pages people open most, then every section as a card
 * that says how it stands. Two or three columns on the Thor's top screen.
 */
@Composable
fun HomeScreen(viewModel: MainViewModel = hiltViewModel(), navigate: (route: String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val context = LocalContext.current

    if (uiState.showPServerNotAvailableDialog) {
        PServerNotAvailableDialog {
            viewModel.pServerDialogDismissed()
            navigate(Routes.DIAGNOSTICS)
        }
    } else if (uiState.showIncompatibleDeviceDialog) {
        UnsupportedDeviceDialog { viewModel.incompatibleDeviceDialogDismissed() }
    }

    val sections = sections(uiState)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Header(uiState, onFix = { navigate(Routes.PERMISSIONS) })
        if (uiState.odinTools.leftoverThorTools) {
            LeftoverBuildBanner {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", OdinToolsDetector.PACKAGE, null)),
                )
            }
        }
        Shortcuts(serviceRunning = uiState.serviceRunning, onPanel = viewModel::openPanel, navigate = navigate)
        BoxWithConstraints {
            val columns = when {
                maxWidth >= THREE_COLUMNS -> 3
                maxWidth >= TWO_COLUMNS -> 2
                else -> 1
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sections.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        row.forEach { section ->
                            SectionCard(section, Modifier.weight(1f).fillMaxHeight()) { navigate(section.route) }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        OdinToolsCard(installed = uiState.odinTools.installed) {
            navigate(if (uiState.odinTools.installed) Routes.COEXISTENCE else Routes.ODIN_FEATURES)
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
    }
}

@Composable
private fun sections(state: MainUiModel): List<Section> {
    val summary = state.summary
    val controller = listOfNotNull(
        summary.controllerStyle?.let { style ->
            listOfNotNull(stringResource(style), summary.l2r2?.let { stringResource(it) }).joinToString(" · ")
        },
        pluralStringResource(R.plurals.homeHotkeys, summary.hotkeys, summary.hotkeys),
    )
    val panel = listOf(
        pluralStringResource(R.plurals.homePages, summary.panelPages, summary.panelPages) + " · " +
            pluralStringResource(R.plurals.homeWidgets, summary.panelWidgets, summary.panelWidgets),
    )
    val profiles = listOf(
        when (val count = summary.profiles) {
            null -> ""
            0 -> stringResource(R.string.homeNoProfiles)
            else -> pluralStringResource(R.plurals.homeProfiles, count, count)
        },
    )
    val power = listOf(
        if (summary.lidActions == 0) {
            stringResource(R.string.homeLidOff)
        } else {
            pluralStringResource(R.plurals.homeLidActions, summary.lidActions, summary.lidActions)
        },
        stringResource(if (summary.chargeAlert) R.string.homeChargeAlertOn else R.string.homeChargeAlertOff),
    )
    val theme = summary.theme
    val display = listOf(
        stringResource(
            R.string.homeTheme,
            when {
                theme == null -> stringResource(R.string.themeSystem)
                theme.label != null -> stringResource(theme.label)
                else -> theme.name
            },
        ),
    )
    val setup = listOf(stringResource(if (state.accessNeedsAttention) R.string.homeSetupCheck else R.string.homeSetupOk))
    return listOf(
        Section(Icons.Rounded.SportsEsports, stringResource(R.string.controllerAndButtons), controller, Routes.CONTROLLER),
        Section(Icons.Rounded.Dashboard, stringResource(R.string.quickPanel), panel, Routes.QUICK_PANEL),
        Section(Icons.Rounded.AppSettingsAlt, stringResource(R.string.appProfiles), profiles, Routes.PROFILES),
        Section(Icons.Rounded.BatteryChargingFull, stringResource(R.string.powerManagement), power, Routes.CHARGING),
        Section(Icons.Rounded.Palette, stringResource(R.string.display), display, Routes.DISPLAY),
        Section(
            Icons.Rounded.HealthAndSafety,
            stringResource(R.string.setupAndDiagnostics),
            setup,
            Routes.SETUP,
            attention = stringResource(R.string.tagCheck).takeIf { state.accessNeedsAttention },
        ),
    )
}

/** The name, the versions, and whether the parts that make it work are running (tap one that isn't to fix it). */
@Composable
private fun Header(state: MainUiModel, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.appName), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = listOf(BuildConfig.VERSION_NAME, state.deviceVersion).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(stringResource(R.string.homeService), state.serviceRunning, onFix)
            StatusPill(stringResource(R.string.homeHelper), state.helperRunning, onFix)
        }
    }
}

@Composable
private fun StatusPill(name: String, ok: Boolean, onFix: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onFix,
        enabled = !ok,
        shape = RoundedCornerShape(50),
        color = if (ok) colors.secondaryContainer else colors.errorContainer,
        contentColor = if (ok) colors.onSecondaryContainer else colors.onErrorContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Box(Modifier.size(8.dp).background(if (ok) colors.primary else colors.error, CircleShape))
            Text(
                text = stringResource(if (ok) R.string.homeRunning else R.string.homeOff, name),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** The pages people open most, one tap away; Back comes back here. */
@Composable
private fun Shortcuts(serviceRunning: Boolean, onPanel: () -> Unit, navigate: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Shortcut(Icons.Rounded.Dashboard, stringResource(R.string.homeOpenPanel), enabled = serviceRunning, onClick = onPanel)
        Shortcut(Icons.Rounded.Edit, stringResource(R.string.editPanel)) { navigate(Routes.QUICK_PANEL_EDIT) }
        Shortcut(Icons.Rounded.Keyboard, stringResource(R.string.hotkeys)) { navigate(Routes.HOTKEYS) }
        Shortcut(Icons.Rounded.Laptop, stringResource(R.string.lidTitle)) { navigate(Routes.LID) }
        Shortcut(Icons.Rounded.Palette, stringResource(R.string.homeThemes)) { navigate(Routes.THEMES) }
    }
}

@Composable
private fun Shortcut(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

/** A section as a card; the controller's focus draws a frame in the theme's main colour. */
@Composable
private fun SectionCard(section: Section, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) colors.primary else colors.outlineVariant),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.primaryContainer, CircleShape),
            ) {
                Icon(section.icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(24.dp))
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
                section.lines.filter { it.isNotEmpty() }.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            section.attention?.let { Tag(it) }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .padding(end = 4.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/**
 * The settings Thor Tools shares with OdinTools: without OdinTools, all of them in one menu; with it, the page that
 * decides which app handles each one (Thor Tools has every OdinTools setting and more, and both can stay).
 */
@Composable
private fun OdinToolsCard(installed: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(20.dp),
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        border = BorderStroke(2.dp, if (focused) colors.primary else Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Icon(Icons.Rounded.Handshake, contentDescription = null, modifier = Modifier.size(26.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(if (installed) R.string.homeOdinTools else R.string.odinFeatures),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(if (installed) R.string.homeOdinToolsLine else R.string.homeOdinFeaturesLine),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}

private val THREE_COLUMNS = 720.dp
private val TWO_COLUMNS = 440.dp
