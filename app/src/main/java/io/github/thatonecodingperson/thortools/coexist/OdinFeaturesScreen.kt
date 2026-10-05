package io.github.thatonecodingperson.thortools.coexist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppSettingsAlt
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.appsettings.ProfilesViewModel
import io.github.thatonecodingperson.thortools.charging.ChargingViewModel
import io.github.thatonecodingperson.thortools.display.DisplayViewModel
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardSection
import io.github.thatonecodingperson.thortools.ui.composables.ChargeLimitPreferenceDialog
import io.github.thatonecodingperson.thortools.ui.composables.OverlapConfirmDialog
import io.github.thatonecodingperson.thortools.ui.composables.SaturationPreferenceDialog
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchCard
import io.github.thatonecodingperson.thortools.ui.composables.VibrationPreferenceDialog
import io.github.thatonecodingperson.thortools.ui.composables.VideoOutputOverridePreferenceDialog

/**
 * Every setting Thor Tools shares with OdinTools, together and usable, for when OdinTools isn't installed (with it, the
 * coexistence page decides which app handles each one). The logic is the sections' own view models.
 */
@Composable
fun OdinFeaturesScreen(
    profiles: ProfilesViewModel = hiltViewModel(),
    display: DisplayViewModel = hiltViewModel(),
    charging: ChargingViewModel = hiltViewModel(),
    onAppOverrides: () -> Unit,
    onBack: () -> Unit,
) {
    val profilesState by profiles.uiState.collectAsState()
    val displayState by display.uiState.collectAsState()
    val chargingState by charging.uiState.collectAsState()
    LaunchedEffect(Unit) {
        profiles.refresh()
        display.refresh()
        charging.refresh()
    }

    profilesState.pendingOverlap?.let { OverlapConfirmDialog(it, profiles::confirmOverlap, profiles::dismissOverlap) }
    chargingState.pendingOverlap?.let { OverlapConfirmDialog(it, charging::confirmOverlap, charging::dismissOverlap) }
    if (profilesState.showVideoOutputOverrideDialog) {
        VideoOutputOverridePreferenceDialog(
            initialControllerStyle = profilesState.videoOutputControllerStyle,
            initialL2R2Style = profilesState.videoOutputL2R2Style,
            onCancel = profiles::videoOutputOverrideDialogDismissed,
            onSave = profiles::saveVideoOutputOverride,
        )
    }
    if (displayState.showSaturationDialog) {
        SaturationPreferenceDialog(displayState.currentSaturation, display::saturationDialogDismissed, display::saveSaturation)
    }
    if (displayState.showVibrationDialog) {
        VibrationPreferenceDialog(displayState.currentVibration, display::vibrationDialogDismissed, display::saveVibration)
    }
    if (chargingState.showChargeLimitDialog) {
        ChargeLimitPreferenceDialog(chargingState.currentChargeLimit, charging::chargeLimitDialogDismissed, charging::saveChargeLimit)
    }

    Scaffold(topBar = { SubTopAppBar(title = R.string.odinFeatures, onBack = onBack) }) { padding ->
        CardColumns(
            padding = padding,
            left = {
                NoteCard(stringResource(R.string.odinFeaturesIntro))
                CardSection(R.string.appProfiles) {
                    SwitchCard(
                        icon = Icons.Rounded.AppSettingsAlt,
                        title = R.string.overlapPerAppControls,
                        info = R.string.odinFeaturesOverridesInfo,
                        checked = profilesState.appOverridesEnabled,
                        onChange = profiles::appOverridesEnabled,
                    )
                    FilledTonalButton(onClick = onAppOverrides) { Text(stringResource(R.string.odinFeaturesEditApps)) }
                    SwitchCard(
                        icon = Icons.Rounded.Cast,
                        title = R.string.overlapExternalDisplay,
                        info = R.string.videoOutputOverrideDescription,
                        checked = profilesState.videoOutputOverrideEnabled,
                        onChange = profiles::updateVideoOutputOverridePreference,
                    )
                    FilledTonalButton(onClick = profiles::videoOutputOverrideClicked) {
                        Text(stringResource(R.string.odinFeaturesChooseStyle))
                    }
                }
                CardSection(R.string.charging) {
                    SwitchCard(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = R.string.overlapChargeAutomation,
                        info = R.string.chargeLimitDescription,
                        checked = chargingState.chargeLimitEnabled,
                        onChange = charging::updateChargeLimitPreference,
                    )
                    FilledTonalButton(onClick = charging::chargeLimitClicked) {
                        Text(
                            stringResource(
                                R.string.odinFeaturesLevels,
                                chargingState.currentChargeLimit.start,
                                chargingState.currentChargeLimit.endInclusive,
                            ),
                        )
                    }
                }
            },
            right = {
                CardSection(R.string.saturation) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = display::saturationClicked) { Text(stringResource(R.string.odinFeaturesSetSaturation)) }
                    }
                    Text(
                        stringResource(R.string.saturationDescription),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SwitchCard(
                        icon = Icons.Rounded.History,
                        title = R.string.keepAfterRestart,
                        info = R.string.saturationAtBootInfo,
                        checked = displayState.saturationAtBoot,
                        onChange = display::setSaturationAtBoot,
                    )
                }
                CardSection(R.string.haptics) {
                    SwitchCard(
                        icon = Icons.Rounded.Vibration,
                        title = R.string.vibrationStrength,
                        info = R.string.vibrationStrengthDescription,
                        checked = displayState.vibrationEnabled,
                        onChange = display::updateVibrationPreference,
                    )
                    FilledTonalButton(onClick = display::vibrationClicked) { Text(stringResource(R.string.odinFeaturesSetStrength)) }
                    SwitchCard(
                        icon = Icons.Rounded.History,
                        title = R.string.keepAfterRestart,
                        info = R.string.vibrationAtBootInfo,
                        checked = displayState.vibrationAtBoot,
                        onChange = display::setVibrationAtBoot,
                    )
                }
                Text(
                    text = stringResource(R.string.odinFeaturesFootnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            },
        )
    }
}
