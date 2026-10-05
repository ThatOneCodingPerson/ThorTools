package io.github.thatonecodingperson.thortools.display

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import io.github.thatonecodingperson.thortools.ui.theme.ThemeBasics
import io.github.thatonecodingperson.thortools.ui.theme.ThemeBuilder
import io.github.thatonecodingperson.thortools.ui.theme.ThorThemes
import io.github.thatonecodingperson.thortools.ui.theme.ThorToolsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** The four colours a user theme is made of. */
enum class ColorSlot(@StringRes val label: Int, @StringRes val description: Int) {
    BACKGROUND(R.string.colorBackground, R.string.colorBackgroundInfo),
    CARDS(R.string.colorCards, R.string.colorCardsInfo),
    TEXT(R.string.colorText, R.string.colorTextInfo),
    ACCENT(R.string.colorAccent, R.string.colorAccentInfo),
}

data class ThemeEditorUiModel(
    val isNew: Boolean = true,
    val name: String = "",
    val basics: ThemeBasics = ThemeBuilder.basics(ThorThemes.default),
    val editing: ColorSlot? = null,
    val showDeleteConfirm: Boolean = false,
    val navigateBack: Boolean = false,
    val previewWidthDp: Int = 620,
    val previewHeightDp: Int = 540,
) {
    val palette get() = ThemeBuilder.build(PREVIEW_ID, name, basics)
    val issues get() = ThemeBuilder.issues(palette)
    val canSave get() = name.isNotBlank()

    fun colorOf(slot: ColorSlot): Long = when (slot) {
        ColorSlot.BACKGROUND -> basics.background
        ColorSlot.CARDS -> basics.cards
        ColorSlot.TEXT -> basics.text
        ColorSlot.ACCENT -> basics.accent
    }

    private companion object {
        const val PREVIEW_ID = "preview"
    }
}

/** Creates or edits a user theme. "new" starts from the theme in use; saving also switches to the theme. */
@HiltViewModel
class ThemeEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    private val prefs: SharedPrefsRepo,
) : ViewModel() {

    private val existing = savedStateHandle.get<String>("id")?.let { id -> prefs.customPalettes.find { it.id == id } }
    private val _uiState = MutableStateFlow(
        ThemeEditorUiModel(
            isNew = existing == null,
            name = existing?.name ?: context.getString(R.string.themeNewName),
            basics = ThemeBuilder.basics(existing ?: prefs.palette() ?: ThorThemes.default),
        ),
    )
    val uiState: StateFlow<ThemeEditorUiModel> = _uiState.asStateFlow()

    init {
        val (width, height) = panelSizeDp(context)
        _uiState.update { it.copy(previewWidthDp = width, previewHeightDp = height) }
    }

    fun nameChanged(name: String) = _uiState.update { it.copy(name = name.replace('\n', ' ')) }

    fun colorClicked(slot: ColorSlot) = _uiState.update { it.copy(editing = slot) }

    fun colorDismissed() = _uiState.update { it.copy(editing = null) }

    fun colorPicked(color: Long) = _uiState.update { state ->
        val basics = when (state.editing) {
            ColorSlot.BACKGROUND -> state.basics.copy(background = color)
            ColorSlot.CARDS -> state.basics.copy(cards = color)
            ColorSlot.TEXT -> state.basics.copy(text = color)
            ColorSlot.ACCENT -> state.basics.copy(accent = color)
            null -> state.basics
        }
        state.copy(basics = basics, editing = null)
    }

    fun cornerChanged(corner: Float) = _uiState.update { it.copy(basics = it.basics.copy(cornerDp = corner.toInt())) }

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

@Composable
fun ThemeEditorScreen(viewModel: ThemeEditorViewModel = hiltViewModel(), navigateBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.navigateBack) {
        if (uiState.navigateBack) navigateBack()
    }

    uiState.editing?.let { slot ->
        ColorPickerDialog(
            title = stringResource(slot.label),
            initial = uiState.colorOf(slot),
            onPick = viewModel::colorPicked,
            onDismiss = viewModel::colorDismissed,
        )
    }

    if (uiState.showDeleteConfirm) {
        DeleteConfirmDialog(onDelete = viewModel::deleteConfirmed, onDismiss = viewModel::deleteDismissed)
    }

    val title = if (uiState.isNew) R.string.themeCreate else R.string.themeEdit
    Scaffold(topBar = { SubTopAppBar(title = title, onBack = navigateBack) }) { contentPadding ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            ThemePreview(
                uiState = uiState,
                modifier = Modifier
                    .weight(PREVIEW_WEIGHT)
                    .fillMaxHeight(),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f - PREVIEW_WEIGHT)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::nameChanged,
                    label = { Text(stringResource(R.string.themeName)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ColorSlot.entries.forEach { slot ->
                    ColorRow(slot, uiState.colorOf(slot)) { viewModel.colorClicked(slot) }
                }
                Text(text = stringResource(R.string.themeCorners, uiState.basics.cornerDp), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = uiState.basics.cornerDp.toFloat(),
                    onValueChange = viewModel::cornerChanged,
                    valueRange = 0f..ThemeBuilder.MAX_CORNER_DP.toFloat(),
                    steps = ThemeBuilder.MAX_CORNER_DP / CORNER_STEP - 1,
                )
                uiState.issues.forEach { issue ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(issueText(issue)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Button(onClick = viewModel::saveClicked, enabled = uiState.canSave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.themeSaveAndUse))
                }
                FilledTonalButton(onClick = navigateBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel))
                }
                if (!uiState.isNew) {
                    OutlinedButton(onClick = viewModel::deleteClicked, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.themeDelete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@StringRes
private fun issueText(issue: ThemeBuilder.Issue): Int = when (issue) {
    ThemeBuilder.Issue.TEXT_ON_BACKGROUND -> R.string.themeIssueTextOnBackground
    ThemeBuilder.Issue.TEXT_ON_CARDS -> R.string.themeIssueTextOnCards
    ThemeBuilder.Issue.ACCENT_ON_CARDS -> R.string.themeIssueAccentOnCards
}

@Composable
private fun ColorRow(slot: ColorSlot, color: Long, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(slot.label))
            Text(
                text = stringResource(slot.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = ThemeBuilder.toHex(color),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The panel's first page and a few app rows, drawn in the theme being edited. */
@Composable
private fun ThemePreview(uiState: ThemeEditorUiModel, modifier: Modifier) {
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
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.themePreviewHeader),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.themePreviewRow), color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            text = stringResource(R.string.themePreviewRowInfo),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = true, onCheckedChange = null)
                }
                Button(onClick = {}) { Text(stringResource(R.string.themePreviewButton)) }
            }
        }
    }
}

private const val PREVIEW_WEIGHT = 0.5f
private const val CORNER_STEP = 2
