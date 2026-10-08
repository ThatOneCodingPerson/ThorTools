package io.github.thatonecodingperson.thortools.extras

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material.icons.rounded.WbIridescent
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.leds.modeName
import io.github.thatonecodingperson.thortools.navigation.GestureWish
import io.github.thatonecodingperson.thortools.oled.OledPart
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.ChoiceCard
import io.github.thatonecodingperson.thortools.ui.composables.LinkCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar

/**
 * The extras that don't belong to another section: the stick lights, Wii profiles for Dolphin, the RetroArch assistant,
 * OLED Safety, gesture navigation, where the keyboard appears.
 */
@Composable
fun ExtraToolsScreen(
    viewModel: ExtraToolsViewModel = hiltViewModel(),
    onLeds: () -> Unit,
    onWii: () -> Unit,
    onRetroArch: () -> Unit,
    onOled: () -> Unit,
    onGestures: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    Scaffold(topBar = { SubTopAppBar(title = R.string.extraTools, onBack = onBack) }) { padding ->
        CardColumns(
            padding = padding,
            left = {
                LinkCard(
                    icon = Icons.Rounded.WbIridescent,
                    title = stringResource(R.string.ledTitle),
                    info = stringResource(R.string.ledTitleInfo),
                    value = stringResource(modeName(state.ledMode)),
                    onClick = onLeds,
                )
                LinkCard(
                    icon = Icons.Rounded.SportsEsports,
                    title = stringResource(R.string.wiiTitle),
                    info = stringResource(R.string.wiiTitleInfo),
                    onClick = onWii,
                )
                LinkCard(
                    icon = Icons.Rounded.VideogameAsset,
                    title = stringResource(R.string.raTitle),
                    info = stringResource(R.string.raTitleInfo),
                    onClick = onRetroArch,
                )
                val oledParts = state.oled.activeParts
                LinkCard(
                    icon = Icons.Rounded.Brightness4,
                    title = stringResource(R.string.oledTitle),
                    info = if (oledParts.isEmpty()) {
                        stringResource(R.string.oledTitleInfo)
                    } else {
                        stringResource(R.string.oledActive, oledParts.map { stringResource(oledPartLabel(it)) }.joinToString(", "))
                    },
                    value = stringResource(if (oledParts.isEmpty()) R.string.oledOff else R.string.oledOn),
                    onClick = onOled,
                )
                LinkCard(
                    icon = Icons.Rounded.Swipe,
                    title = stringResource(R.string.gestureNavTitle),
                    info = gesturesInfo(state.gestures),
                    value = stringResource(if (state.gestures.on) R.string.gestureNavOn else R.string.gestureNavOff),
                    onClick = onGestures,
                )
            },
            right = {
                ChoiceCard(
                    icon = Icons.Rounded.Keyboard,
                    title = stringResource(R.string.keyboardPlace),
                    info = state.keyboard?.let { stringResource(it.infoRes) } ?: stringResource(R.string.keyboardPlaceInfo),
                    options = KeyboardPlace.entries.map { it to stringResource(it.textRes) },
                    selected = state.keyboard,
                    onSelect = viewModel::setKeyboard,
                )
                if (state.keyboardFailed) NoteCard(stringResource(R.string.keyboardFailed), warning = true)
            },
        )
    }
}

@Composable
private fun gesturesInfo(wish: GestureWish): String {
    val parts = listOfNotNull(
        R.string.gestureNavPartHome.takeIf { !wish.parts.homeSwipe },
        R.string.gestureNavPartBack.takeIf { !wish.parts.backSwipe },
    )
    if (parts.isEmpty()) return stringResource(R.string.gestureNavTitleInfo)
    return stringResource(R.string.gestureNavOffInfo, parts.map { stringResource(it) }.joinToString(", "))
}

@StringRes
private fun oledPartLabel(part: OledPart): Int = when (part) {
    OledPart.SHIFTER -> R.string.oledPartShifter
    OledPart.STILL_AREAS -> R.string.oledPartAreas
    OledPart.REFRESHER -> R.string.oledPartRefresher
    OledPart.IDLE -> R.string.oledPartIdle
}
