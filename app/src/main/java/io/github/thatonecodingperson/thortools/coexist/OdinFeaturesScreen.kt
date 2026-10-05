package io.github.thatonecodingperson.thortools.coexist

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppSettingsAlt
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Vibration
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
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.CardSection
import io.github.thatonecodingperson.thortools.ui.composables.CardSwitchRow
import io.github.thatonecodingperson.thortools.ui.composables.ChargeLimitPreferenceDialog
import io.github.thatonecodingperson.thortools.ui.composables.LinkCard
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
                        rows = { CardRow(title = stringResource(R.string.odinFeaturesEditApps), onClick = onAppOverrides) },
                        onChange = profiles::appOverridesEnabled,
                    )
                    SwitchCard(
                        icon = Icons.Rounded.Cast,
                        title = R.string.overlapExternalDisplay,
                        info = R.string.videoOutputOverrideDescription,
                        checked = profilesState.videoOutputOverrideEnabled,
                        rows = {
                            CardRow(
                                title = stringResource(R.string.odinFeaturesChooseStyle),
                                value = listOf(
                                    stringResource(profilesState.videoOutputControllerStyle.textRes),
                                    stringResource(profilesState.videoOutputL2R2Style.textRes),
                                ).joinToString(" · "),
                                onClick = profiles::videoOutputOverrideClicked,
                            )
                        },
                        onChange = profiles::updateVideoOutputOverridePreference,
                    )
                }
                CardSection(R.string.charging) {
                    SwitchCard(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = R.string.overlapChargeAutomation,
                        info = R.string.chargeLimitDescription,
                        checked = chargingState.chargeLimitEnabled,
                        rows = {
                            CardRow(
                                title = stringResource(R.string.chargeLimitLevels),
                                value = stringResource(
                                    R.string.chargeLimitLevelsValue,
                                    chargingState.currentChargeLimit.start,
                                    chargingState.currentChargeLimit.endInclusive,
                                ),
                                onClick = charging::chargeLimitClicked,
                            )
                        },
                        onChange = charging::updateChargeLimitPreference,
                    )
                    NoteCard(stringResource(R.string.chargeLimitNote))
                }
            },
            right = {
                CardSection(R.string.display) {
                    LinkCard(
                        icon = Icons.Rounded.Contrast,
                        title = stringResource(R.string.saturation),
                        info = stringResource(R.string.saturationDescription),
                        value = stringResource(R.string.saturationValue, displayState.currentSaturation),
                        rows = {
                            CardSwitchRow(
                                title = stringResource(R.string.keepAfterRestart),
                                info = stringResource(R.string.saturationAtBootInfo),
                                icon = Icons.Rounded.History,
                                checked = displayState.saturationAtBoot,
                                onChange = display::setSaturationAtBoot,
                            )
                        },
                        onClick = display::saturationClicked,
                    )
                }
                CardSection(R.string.haptics) {
                    SwitchCard(
                        icon = Icons.Rounded.Vibration,
                        title = R.string.vibrationStrength,
                        info = R.string.vibrationStrengthDescription,
                        checked = displayState.vibrationEnabled,
                        rows = {
                            CardRow(
                                title = stringResource(R.string.vibrationStrengthRow),
                                value = displayState.currentVibration.takeIf { it > 0 }?.let { stringResource(R.string.vibrationMv, it) },
                                enabled = displayState.vibrationEnabled,
                                onClick = display::vibrationClicked,
                            )
                            CardSwitchRow(
                                title = stringResource(R.string.keepAfterRestart),
                                info = stringResource(R.string.vibrationAtBootInfo),
                                icon = Icons.Rounded.History,
                                checked = displayState.vibrationAtBoot,
                                onChange = display::setVibrationAtBoot,
                            )
                        },
                        onChange = display::updateVibrationPreference,
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
