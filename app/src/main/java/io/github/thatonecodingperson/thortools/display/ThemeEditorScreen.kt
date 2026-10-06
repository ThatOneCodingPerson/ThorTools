package io.github.thatonecodingperson.thortools.display

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.panel.PanelLayout
import io.github.thatonecodingperson.thortools.panel.PanelPreview
import io.github.thatonecodingperson.thortools.panel.panelSizeDp
import io.github.thatonecodingperson.thortools.panel.samplePanelState
import io.github.thatonecodingperson.thortools.ui.composables.DeleteConfirmDialog
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.theme.HslColor
import io.github.thatonecodingperson.thortools.ui.theme.ThemeBasics
import io.github.thatonecodingperson.thortools.ui.theme.ThemeBuilder
import io.github.thatonecodingperson.thortools.ui.theme.ThemeColor
import io.github.thatonecodingperson.thortools.ui.theme.ThorPalette
import io.github.thatonecodingperson.thortools.ui.theme.ThorThemes
import io.github.thatonecodingperson.thortools.ui.theme.ThorToolsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.roundToInt

data class ThemeEditorUiModel(
    val isNew: Boolean = true,
    val name: String = "",
    val basics: ThemeBasics = ThemeBuilder.basics(ThorThemes.default),
    /** The colour the controls change, and its hue, saturation and lightness and hex text as being edited. */
    val picking: ThemeColor = ThemeColor.BACKGROUND,
    val hsl: HslColor = HslColor.of(ThemeBuilder.colorOf(basics, picking)),
    val hexText: String = ThemeBuilder.toHex(ThemeBuilder.colorOf(basics, picking)),
    val showDeleteConfirm: Boolean = false,
    val navigateBack: Boolean = false,
    val previewWidthDp: Int = 620,
    val previewHeightDp: Int = 540,
) {
    val palette get() = ThemeBuilder.build(PREVIEW_ID, name, basics)
    val canSave get() = name.isNotBlank()

    private companion object {
        const val PREVIEW_ID = "preview"
    }
}

/**
 * Creates or edits a user theme. "new" starts from the theme in use, or with `from` from that theme (a copy); saving
 * also switches to the theme. Every change shows in the preview at once.
 */
@HiltViewModel
class ThemeEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    private val prefs: SharedPrefsRepo,
) : ViewModel() {

    private val existing = savedStateHandle.get<String>("id")?.let { id -> prefs.customPalettes.find { it.id == id } }
    private val source = savedStateHandle.get<String>("from")?.let { ThorThemes.resolve(it, prefs.customPalettes) }
    private val _uiState = MutableStateFlow(start(context))
    val uiState: StateFlow<ThemeEditorUiModel> = _uiState.asStateFlow()

    init {
        val (width, height) = panelSizeDp(context)
        _uiState.update { it.copy(previewWidthDp = width, previewHeightDp = height) }
    }

    private fun start(context: Context): ThemeEditorUiModel {
        val name = when {
            existing != null -> existing.name
            source != null -> context.getString(R.string.themeCopyName, source.label?.let(context::getString) ?: source.name)
            else -> context.getString(R.string.themeNewName)
        }
        val from = existing ?: source ?: prefs.palette() ?: ThorThemes.default
        return ThemeEditorUiModel(isNew = existing == null, name = name, basics = ThemeBuilder.basics(from))
    }

    fun nameChanged(name: String) = _uiState.update { it.copy(name = name.replace('\n', ' ')) }

    /** Which of the four colours the controls change from now on. */
    fun pick(color: ThemeColor) = _uiState.update { state ->
        val value = ThemeBuilder.colorOf(state.basics, color)
        state.copy(picking = color, hsl = HslColor.of(value), hexText = ThemeBuilder.toHex(value))
    }

    fun hslChanged(hsl: HslColor) = _uiState.update { state ->
        val value = hsl.color
        state.copy(hsl = hsl, hexText = ThemeBuilder.toHex(value), basics = ThemeBuilder.withColor(state.basics, state.picking, value))
    }

    /** Typed hex: kept as typed, and used as soon as it is a whole colour. */
    fun hexChanged(text: String) = _uiState.update { state ->
        val value = ThemeBuilder.parseHex(text) ?: return@update state.copy(hexText = text)
        state.copy(hexText = text, hsl = HslColor.of(value), basics = ThemeBuilder.withColor(state.basics, state.picking, value))
    }

    fun cornerChanged(corner: Float) = _uiState.update { it.copy(basics = it.basics.copy(cornerDp = corner.roundToInt())) }

    fun saveClicked() {
        val state = _uiState.value
        if (!state.canSave) return
        val id = existing?.id ?: "custom_${System.currentTimeMillis()}"
        prefs.saveCustomPalette(ThemeBuilder.build(id, state.name.trim(), state.basics))
        prefs.themeId = id
        _uiState.update { it.copy(navigateBack = true) }
    }

    fun deleteClicked() = _uiState.update { it.copy(showDeleteConfirm = true) }

    fun deleteDismissed() = _uiState.update { it.copy(showDeleteConfirm = false) }

    fun deleteConfirmed() {
        existing?.let { prefs.deleteCustomPalette(it.id) }
        _uiState.update { it.copy(showDeleteConfirm = false, navigateBack = true) }
    }
}

/**
 * The theme editor: the live preview on one side and the colours on the other, all on screen at once. On a narrow
 * screen the preview stays at the top while the rest scrolls under it.
 */
@Composable
fun ThemeEditorScreen(viewModel: ThemeEditorViewModel = hiltViewModel(), navigateBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.navigateBack) {
        if (uiState.navigateBack) navigateBack()
    }

    if (uiState.showDeleteConfirm) {
        DeleteConfirmDialog(onDelete = viewModel::deleteConfirmed, onDismiss = viewModel::deleteDismissed)
    }

    val title = if (uiState.isNew) R.string.themeCreate else R.string.themeEdit
    Scaffold(
        topBar = {
            SubTopAppBar(title = title, onBack = navigateBack) {
                if (!uiState.isNew) {
                    IconButton(onClick = viewModel::deleteClicked) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.themeDelete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Button(onClick = viewModel::saveClicked, enabled = uiState.canSave, modifier = Modifier.padding(end = 8.dp)) {
                    Text(stringResource(R.string.themeSaveAndUse))
                }
            }
        },
    ) { contentPadding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (maxWidth >= TWO_PANES_WIDTH) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxSize()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(PREVIEW_WEIGHT)
                            .fillMaxHeight(),
                    ) {
                        NameField(uiState.name, viewModel::nameChanged)
                        ThemePreview(uiState, withAppRow = true, modifier = Modifier.weight(1f).fillMaxWidth())
                        CornerRow(uiState.basics.cornerDp, viewModel::cornerChanged)
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f - PREVIEW_WEIGHT)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) { ColorPicking(uiState, viewModel) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    ThemePreview(uiState, withAppRow = false, modifier = Modifier.fillMaxWidth().height(NARROW_PREVIEW_HEIGHT))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        NameField(uiState.name, viewModel::nameChanged)
                        ColorPicking(uiState, viewModel)
                        CornerRow(uiState.basics.cornerDp, viewModel::cornerChanged)
                    }
                }
            }
        }
    }
}

@Composable
private fun NameField(name: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = name,
    onValueChange = onChange,
    label = { Text(stringResource(R.string.themeName)) },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
)

@Composable
private fun CornerRow(cornerDp: Int, onChange: (Float) -> Unit) = SliderRow(
    label = R.string.themeCornersLabel,
    value = cornerDp.toFloat(),
    range = 0f..ThemeBuilder.MAX_CORNER_DP.toFloat(),
    valueText = stringResource(R.string.themeCornerValue, cornerDp),
    steps = ThemeBuilder.MAX_CORNER_DP / CORNER_STEP - 1,
    onChange = onChange,
)

/** The four colours as tiles to choose from, what the chosen one is for, and the controls for it. */
@Composable
private fun ColorPicking(uiState: ThemeEditorUiModel, viewModel: ThemeEditorViewModel) {
    val palette = uiState.palette
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeColor.entries.forEach { color ->
            ColorTile(
                color = color,
                value = ThemeBuilder.colorOf(uiState.basics, color),
                chosen = color == uiState.picking,
                warning = ThemeBuilder.issuesFor(palette, color).isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { viewModel.pick(color) }
        }
    }
    Text(
        text = stringResource(description(uiState.picking)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ColorControls(hsl = uiState.hsl, hexText = uiState.hexText, onHsl = viewModel::hslChanged, onHex = viewModel::hexChanged) {
        Readability(palette, uiState.picking, Modifier.weight(1f))
    }
}

@Composable
private fun ColorTile(color: ThemeColor, value: Long, chosen: Boolean, warning: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (chosen) colors.secondaryContainer else colors.surfaceContainer,
        border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) colors.primary else colors.outlineVariant),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)) {
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(value))
                        .border(1.dp, colors.outline, CircleShape),
                )
                if (warning) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = stringResource(R.string.themeHardToRead),
                        tint = colors.error,
                        modifier = Modifier
                            .size(14.dp)
                            .offset(x = 6.dp, y = (-4).dp),
                    )
                }
            }
            Text(
                text = stringResource(label(color)),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Whether the chosen colour keeps things readable, with the contrast that matters most for it. */
@Composable
private fun Readability(palette: ThorPalette, color: ThemeColor, modifier: Modifier) {
    val issues = ThemeBuilder.issuesFor(palette, color)
    val readable = issues.isEmpty()
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Icon(
            if (readable) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            contentDescription = null,
            tint = if (readable) colors.primary else colors.error,
            modifier = Modifier.size(18.dp),
        )
        val ratio = "%.1f".format(ThemeBuilder.keyContrast(palette, color))
        val problems = issues.map { stringResource(issueText(it)) }
        Text(
            text = if (readable) stringResource(R.string.themeEasyToRead, ratio) else problems.joinToString(" "),
            style = MaterialTheme.typography.bodySmall,
            color = if (readable) colors.onSurfaceVariant else colors.error,
        )
    }
}

@StringRes
private fun label(color: ThemeColor): Int = when (color) {
    ThemeColor.BACKGROUND -> R.string.colorBackground
    ThemeColor.CARDS -> R.string.colorCards
    ThemeColor.TEXT -> R.string.colorText
    ThemeColor.ACCENT -> R.string.colorAccent
}

@StringRes
private fun description(color: ThemeColor): Int = when (color) {
    ThemeColor.BACKGROUND -> R.string.colorBackgroundInfo
    ThemeColor.CARDS -> R.string.colorCardsInfo
    ThemeColor.TEXT -> R.string.colorTextInfo
    ThemeColor.ACCENT -> R.string.colorAccentInfo
}

@StringRes
private fun issueText(issue: ThemeBuilder.Issue): Int = when (issue) {
    ThemeBuilder.Issue.TEXT_ON_BACKGROUND -> R.string.themeIssueTextOnBackground
    ThemeBuilder.Issue.TEXT_ON_CARDS -> R.string.themeIssueTextOnCards
    ThemeBuilder.Issue.ACCENT_ON_CARDS -> R.string.themeIssueAccentOnCards
}

/** The panel's first page drawn in the theme being edited, and (with [withAppRow]) how a row of the app looks. */
@Composable
private fun ThemePreview(uiState: ThemeEditorUiModel, withAppRow: Boolean, modifier: Modifier) {
    ThorToolsTheme(uiState.palette) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
            PanelPreview(
                layout = PanelLayout.DEFAULT,
                page = 0,
                state = samplePanelState(),
                widthDp = uiState.previewWidthDp,
                heightDp = uiState.previewHeightDp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            if (withAppRow) AppRowSample()
        }
    }
}

@Composable
private fun AppRowSample() {
    val shape = RoundedCornerShape(12.dp)
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.outlineVariant, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(R.string.themePreviewHeader), style = MaterialTheme.typography.labelMedium, color = colors.primary)
            Text(
                text = stringResource(R.string.themePreviewRow),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = true, onCheckedChange = null)
        Button(onClick = {}) { Text(stringResource(R.string.themePreviewButton)) }
    }
}

private const val PREVIEW_WEIGHT = 0.45f
private const val CORNER_STEP = 2
private val TWO_PANES_WIDTH = 720.dp
private val NARROW_PREVIEW_HEIGHT = 150.dp
