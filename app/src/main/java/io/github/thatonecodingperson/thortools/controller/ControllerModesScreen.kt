package io.github.thatonecodingperson.thortools.controller

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.CycleChoice
import io.github.thatonecodingperson.thortools.models.L2R2Style
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar

/** One mode as a card: what it is, an example, whether it is in use, and whether the switching goes through it. */
private data class ModeOption(val id: String, @StringRes val title: Int, @StringRes val info: Int, val icon: ImageVector)

private val ControllerStyle.icon: ImageVector
    get() = when (this) {
        ControllerStyle.Xbox -> Icons.Rounded.SportsEsports
        ControllerStyle.Odin -> Icons.Rounded.Gamepad
        else -> Icons.Rounded.LinkOff
    }

private val L2R2Style.icon: ImageVector
    get() = when (this) {
        L2R2Style.Analog -> Icons.Rounded.Speed
        L2R2Style.Digital -> Icons.Rounded.RadioButtonChecked
        else -> Icons.Rounded.Tune
    }

/**
 * Controller style and L2/R2 mode on one screen, laid out like the Hotkeys screens: every mode is a card; a tap uses
 * it right away, and its switch says whether the quick settings tile, the quick panel and the "Next ..." hotkeys go
 * through it. Two columns when the screen is wide enough.
 */
@Composable
fun ControllerModesScreen(viewModel: ControllerModesViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    val styles: @Composable () -> Unit = {
        ModeSection(
            title = R.string.controllerStyle,
            intro = R.string.controllerModesStyleIntro,
            options = ControllerStyle.choices.map { ModeOption(it.id, it.textRes, it.infoRes, it.icon) },
            inUse = state.style.id,
            unreadable = state.loaded && state.style == ControllerStyle.Unknown,
            skipped = state.skippedStyle,
            onUse = { viewModel.useStyle(ControllerStyle.getById(it)) },
            onCycle = viewModel::setStyleInCycle,
        )
    }
    val triggers: @Composable () -> Unit = {
        ModeSection(
            title = R.string.l2r2mode,
            intro = R.string.controllerModesTriggerIntro,
            options = L2R2Style.choices.map { ModeOption(it.id, it.textRes, it.infoRes, it.icon) },
            inUse = state.l2r2.id,
            unreadable = state.loaded && state.l2r2 == L2R2Style.Unknown,
            skipped = state.skippedL2r2,
            onUse = { viewModel.useL2r2(L2R2Style.getById(it)) },
            onCycle = viewModel::setL2r2InCycle,
        )
    }

    Scaffold(topBar = { SubTopAppBar(title = R.string.controllerModes, onBack = onBack) }) { padding ->
        BoxWithConstraints(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            if (maxWidth >= TWO_COLUMNS_WIDTH) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    ScrollingColumn(Modifier.weight(1f)) { styles() }
                    ScrollingColumn(Modifier.weight(1f)) { triggers() }
                }
            } else {
                ScrollingColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    styles()
                    triggers()
                }
            }
        }
    }
}

@Composable
private fun ScrollingColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
    ) { content() }
}

@Composable
private fun ModeSection(
    @StringRes title: Int,
    @StringRes intro: Int,
    options: List<ModeOption>,
    inUse: String,
    unreadable: Boolean,
    skipped: String?,
    onUse: (String) -> Unit,
    onCycle: (String, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (unreadable) NoteCard(stringResource(R.string.modeUnknownInfo))
        options.forEach { option ->
            ModeCard(
                option = option,
                inUse = option.id == inUse,
                inCycle = skipped != option.id,
                canLeaveCycle = CycleChoice.canSkip(skipped, option.id),
                onUse = { onUse(option.id) },
                onCycle = { include -> onCycle(option.id, include) },
            )
        }
        Text(
            text = stringResource(R.string.controllerModesCycleInfo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeCard(
    option: ModeOption,
    inUse: Boolean,
    inCycle: Boolean,
    canLeaveCycle: Boolean,
    onUse: () -> Unit,
    onCycle: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onUse,
        shape = RoundedCornerShape(16.dp),
        color = if (inUse) colors.secondaryContainer else colors.surfaceContainer,
        border = BorderStroke(if (inUse) 2.dp else 1.dp, if (inUse) colors.primary else colors.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(option.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.padding(top = 2.dp).size(28.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(option.title), style = MaterialTheme.typography.titleMedium)
                        if (inUse) InUseTag()
                    }
                    Text(stringResource(option.info), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.controllerModesInCycle),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                // At least two modes stay in the switching, so the last two can't be switched off.
                Switch(checked = inCycle, enabled = !inCycle || canLeaveCycle, onCheckedChange = onCycle)
            }
        }
    }
}

@Composable
private fun InUseTag() {
    Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
        Text(
            text = stringResource(R.string.controllerModesInUse),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private val TWO_COLUMNS_WIDTH = 640.dp
