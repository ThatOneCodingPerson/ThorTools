package io.github.thatonecodingperson.thortools.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.main.Routes
import io.github.thatonecodingperson.thortools.ui.composables.SettingsHeader
import io.github.thatonecodingperson.thortools.ui.composables.SubScreen
import io.github.thatonecodingperson.thortools.ui.theme.ThorPalette
import io.github.thatonecodingperson.thortools.ui.theme.ThorThemes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ThemesUiModel(val themeId: String = ThorThemes.default.id, val custom: List<ThorPalette> = emptyList())

@HiltViewModel
class ThemesViewModel @Inject constructor(private val prefs: SharedPrefsRepo) : ViewModel() {

    private val _uiState = MutableStateFlow(ThemesUiModel())
    val uiState: StateFlow<ThemesUiModel> = _uiState.asStateFlow()

    /** Again on every visit: the theme editor may have added, changed or deleted a theme. */
    fun refresh() = _uiState.update { it.copy(themeId = prefs.themeId, custom = prefs.customPalettes) }

    fun select(id: String) {
        prefs.themeId = id
        _uiState.update { it.copy(themeId = id) }
    }
}

/**
 * Every theme as a small preview card: your own first (with "Create a theme"), then the built-in ones. Each but System
 * colours can be copied into a new theme of your own ([onCopy]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemesScreen(
    viewModel: ThemesViewModel = hiltViewModel(),
    onEdit: (id: String) -> Unit,
    onCopy: (id: String) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    SubScreen(title = R.string.theme, onBack = onBack) {
        Text(
            text = stringResource(R.string.themesIntro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        SettingsHeader(R.string.themesYours)
        CardFlow {
            CreateCard { onEdit(Routes.NEW_THEME) }
            uiState.custom.forEach { palette ->
                ThemeCard(
                    palette = palette,
                    name = palette.name,
                    selected = palette.id == uiState.themeId,
                    onEdit = { onEdit(palette.id) },
                    onCopy = { onCopy(palette.id) },
                ) { viewModel.select(palette.id) }
            }
        }
        SettingsHeader(R.string.themesBuiltIn)
        CardFlow {
            ThorThemes.builtIn.forEach { palette ->
                ThemeCard(
                    palette = palette,
                    name = palette.label?.let { stringResource(it) } ?: palette.name,
                    selected = palette.id == uiState.themeId,
                    onCopy = { onCopy(palette.id) },
                ) { viewModel.select(palette.id) }
            }
            SystemCard(selected = uiState.themeId == ThorThemes.SYSTEM) { viewModel.select(ThorThemes.SYSTEM) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) { content() }
}

/** A miniature of the theme: background, a card with text, a switched-on tile, an accent button. */
@Composable
private fun ThemeCard(
    palette: ThorPalette,
    name: String,
    selected: Boolean,
    onEdit: (() -> Unit)? = null,
    onCopy: () -> Unit,
    onClick: () -> Unit,
) {
    val corner = RoundedCornerShape((palette.cornerDp / 2).dp + 4.dp)
    CardFrame(selected = selected, name = name, onEdit = onEdit, onCopy = onCopy, onClick = onClick) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(palette.background))
                .padding(10.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(corner)
                    .background(Color(palette.surface))
                    .padding(8.dp),
            ) {
                Text(text = stringResource(R.string.themesSampleText), color = Color(palette.onSurface), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(Color(palette.tileOn)))
                    Box(Modifier.size(18.dp).clip(CircleShape).background(Color(palette.tileOff)))
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .width(34.dp)
                            .height(16.dp)
                            .clip(CircleShape)
                            .background(Color(palette.primary)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemCard(selected: Boolean, onClick: () -> Unit) {
    CardFrame(selected = selected, name = stringResource(R.string.themeSystem), onClick = onClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
        ) {
            Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.themesSystemInfo),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreateCard(onClick: () -> Unit) {
    CardFrame(selected = false, name = stringResource(R.string.themeCreateOwn), onClick = onClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(R.string.themesCreateInfo),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun CardFrame(
    selected: Boolean,
    name: String,
    onEdit: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .clip(shape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(PREVIEW_HEIGHT),
        ) { preview() }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            )
            if (onCopy != null) CardAction(Icons.Rounded.ContentCopy, stringResource(R.string.themeCopy), onCopy)
            if (onEdit != null) CardAction(Icons.Rounded.Edit, stringResource(R.string.themeEdit), onEdit)
        }
    }
}

@Composable
private fun CardAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(ACTION_SIZE)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

private val CARD_WIDTH = 192.dp
private val ACTION_SIZE = 36.dp
private val PREVIEW_HEIGHT = 96.dp
