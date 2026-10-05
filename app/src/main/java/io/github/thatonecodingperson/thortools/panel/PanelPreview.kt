package io.github.thatonecodingperson.thortools.panel

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.thatonecodingperson.thortools.input.ScreenFocus
import io.github.thatonecodingperson.thortools.models.ControllerStyle
import io.github.thatonecodingperson.thortools.models.FanMode
import io.github.thatonecodingperson.thortools.models.L2R2Style
import io.github.thatonecodingperson.thortools.models.PerfMode
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The panel's size in dp on the bottom screen, where it normally opens, so a preview has the real proportions. */
fun panelSizeDp(context: Context): Pair<Int, Int> {
    val displays = context.getSystemService(DisplayManager::class.java)
    val bottom = ScreenFocus(displays).bottomDisplayId()?.let(displays::getDisplay) ?: return FALLBACK_SIZE_DP
    val metrics = DisplayMetrics().also {
        @Suppress("DEPRECATION")
        bottom.getRealMetrics(it)
    }
    return (metrics.widthPixels / metrics.density).roundToInt() to (metrics.heightPixels / metrics.density).roundToInt()
}

/** Believable values for previews, so every tile, slider and gauge shows something. */
fun samplePanelState() = PanelUiState(
    battery = 76,
    controllerStyle = ControllerStyle.Xbox.textRes,
    l2r2 = L2R2Style.Analog.textRes,
    performance = PerfMode.Performance.textRes,
    fan = FanMode.Smart.textRes,
    refreshHz = 120,
    volume = 0.6f,
    topBrightness = 0.7f,
    bottomBrightness = 0.5f,
    stats = StatsReading(2.4f, 0.6f, 680, 0.35f, 46f, 42f, 7.1f, 16f),
    powerW = 6.8f,
    batteryTemp = 31f,
    leftMinutes = 185,
    history = (0..60).map { second ->
        val wave = sin(second / 6f)
        GraphPoint(second * 1000L, 0.55f + 0.2f * wave, 0.35f + 0.15f * cos(second / 4f), 44f + 3f * wave)
    },
    media = NowPlaying(title = "Song title", artist = "Artist", app = "Music", playing = true),
)

/**
 * The real panel content for page [page], laid out at the panel's true size ([widthDp] x [heightDp]) and scaled down to
 * fit. Nothing in it can be clicked or focused.
 */
@Composable
fun PanelPreview(layout: PanelLayout, page: Int, state: PanelUiState, widthDp: Int, heightDp: Int, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(initialPage = page) { layout.pages.size }
    LaunchedEffect(page, layout.pages.size) { pagerState.scrollToPage(page.coerceIn(0, layout.pages.lastIndex)) }
    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = modifier) {
        val widthRoom = maxWidth.value / widthDp
        val heightRoom = if (constraints.hasBoundedHeight) maxHeight.value / heightDp else Float.MAX_VALUE
        val scale = min(min(widthRoom, heightRoom), 1f)
        val shape = RoundedCornerShape(12.dp)
        Box(
            modifier = Modifier
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(widthDp.dp.roundToPx(), heightDp.dp.roundToPx()))
                    layout((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
                        placeable.placeWithLayer(0, 0) {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                    }
                },
        ) {
            CompositionLocalProvider(LocalPanelInteractive provides false) {
                QuickPanelContent(state = state, layout = layout, pagerState = pagerState, callbacks = PanelCallbacks())
            }
        }
    }
}

private val FALLBACK_SIZE_DP = 620 to 540
