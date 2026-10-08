package io.github.thatonecodingperson.thortools.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.ui.composables.CardCheckRow
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardHeader
import io.github.thatonecodingperson.thortools.ui.composables.CardRowBox
import io.github.thatonecodingperson.thortools.ui.composables.CardSwitchRow
import io.github.thatonecodingperson.thortools.ui.composables.LinkCard
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchCard

/** Android's navigation swipes off and on, and the home bar hidden, with Thor Tools' root access. */
@Composable
fun GestureNavScreen(viewModel: GestureNavViewModel = hiltViewModel(), onProfiles: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    Scaffold(topBar = { SubTopAppBar(title = R.string.gestureNavTitle, onBack = onBack) }) { padding ->
        CardColumns(
            padding = padding,
            left = {
                SwitchCard(
                    icon = Icons.Rounded.Swipe,
                    title = R.string.gestureNavSwitch,
                    info = R.string.gestureNavSwitchInfo,
                    checked = state.choice.on,
                    rows = nowText(state.nav)?.let { now ->
                        {
                            CardRowBox {
                                Text(stringResource(R.string.gestureNavNow), style = MaterialTheme.typography.labelLarge)
                                Text(now, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    },
                    onChange = viewModel::setOn,
                )
                SettingsCard(contentPadding = PaddingValues()) {
                    CardHeader(
                        Icons.Rounded.Checklist,
                        stringResource(R.string.gestureNavStops),
                        stringResource(R.string.gestureNavStopsInfo),
                    )
                    CardCheckRow(
                        title = stringResource(R.string.gestureNavStopHome),
                        checked = state.choice.stopHome,
                        info = stringResource(R.string.gestureNavStopHomeInfo),
                        enabled = !state.choice.stopHome || state.choice.stopBack,
                        onChange = viewModel::setStopHome,
                    )
                    CardCheckRow(
                        title = stringResource(R.string.gestureNavStopBack),
                        checked = state.choice.stopBack,
                        info = stringResource(R.string.gestureNavStopBackInfo),
                        enabled = !state.choice.stopBack || state.choice.stopHome,
                        onChange = viewModel::setStopBack,
                    )
                }
                SwitchCard(
                    icon = Icons.Rounded.Mouse,
                    title = R.string.gestureNavDesktop,
                    info = R.string.gestureNavDesktopInfo,
                    checked = state.choice.offWithDesktop,
                    onChange = viewModel::setOffWithDesktop,
                )
                if (state.nav.failed) {
                    NoteCard(stringResource(R.string.gestureNavFailed), warning = true)
                } else if (!state.choice.on && state.choice.stopHome) {
                    NoteCard(stringResource(R.string.gestureNavRestartNote))
                }
            },
            right = {
                SettingsCard(contentPadding = PaddingValues()) {
                    CardHeader(
                        Icons.Rounded.HorizontalRule,
                        stringResource(R.string.gestureNavBar),
                        stringResource(R.string.gestureNavBarInfo),
                    )
                    CardSwitchRow(
                        title = stringResource(R.string.gestureNavBarTop),
                        checked = state.topBarHidden,
                        info = stringResource(R.string.gestureNavBarTopInfo),
                        onChange = viewModel::setTopBarHidden,
                    )
                }
                LinkCard(
                    icon = Icons.Rounded.Apps,
                    title = stringResource(R.string.gestureNavPerApp),
                    info = stringResource(R.string.gestureNavPerAppInfo),
                    onClick = onProfiles,
                )
            },
        )
    }
}

/** What is stopped right now and why, as read back; null until read. */
@Composable
private fun nowText(nav: GestureNavState): String? {
    val parts = nav.reading?.parts ?: return null
    if (parts.allWork) return stringResource(R.string.gestureNavNowOn)
    val stopped = listOfNotNull(
        R.string.gestureNavPartHome.takeIf { !parts.homeSwipe },
        R.string.gestureNavPartBack.takeIf { !parts.backSwipe },
    ).map { stringResource(it) }.joinToString(", ")
    val why = when (nav.wish.source) {
        GestureSource.PAGE -> R.string.gestureNavWhyPage
        GestureSource.APP -> R.string.gestureNavWhyApp
        GestureSource.DESKTOP -> R.string.gestureNavWhyDesktop
        GestureSource.ACTION -> R.string.gestureNavWhyAction
    }
    return stringResource(R.string.gestureNavNowOff, stopped, stringResource(why))
}
