package io.github.thatonecodingperson.thortools.extras

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.SportsEsports
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
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.ChoiceCard
import io.github.thatonecodingperson.thortools.ui.composables.LinkCard
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar

/** The extras that don't belong to another section: the stick lights, Wii profiles for Dolphin, where the keyboard appears. */
@Composable
fun ExtraToolsScreen(viewModel: ExtraToolsViewModel = hiltViewModel(), onLeds: () -> Unit, onWii: () -> Unit, onBack: () -> Unit) {
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
