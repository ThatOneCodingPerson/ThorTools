package io.github.thatonecodingperson.thortools.diagnostics

import android.content.ClipData
import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel = hiltViewModel(), onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val keyTesterFocus = remember { FocusRequester() }

    Scaffold(topBar = { SubTopAppBar(title = R.string.diagnostics, onBack = onBack) }) { contentPadding ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
            ) {
                FilledTonalButton(
                    onClick = {
                        val report = viewModel.fullReport()
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Thor Tools diagnostics", report))) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.copyReport))
                }
                OutlinedButton(onClick = viewModel::saveToDownloads, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.saveReport))
                }
                OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.refresh))
                }
                uiState.savedAs?.let { savedAs ->
                    Text(
                        text = if (savedAs.isEmpty()) stringResource(R.string.saveFailed) else stringResource(R.string.savedAs, savedAs),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(stringResource(R.string.keyTester), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.keyTesterHint), style = MaterialTheme.typography.bodySmall)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .focusRequester(keyTesterFocus)
                        .onPreviewKeyEvent { event ->
                            viewModel.onKey(event.nativeKeyEvent)
                            event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK
                        }
                        .focusable()
                        .padding(8.dp),
                ) {
                    OutlinedButton(onClick = { keyTesterFocus.requestFocus() }) {
                        Text(stringResource(R.string.keyTesterStart))
                    }
                    uiState.keys.forEach {
                        Text(it.toString(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            SelectionContainer(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (uiState.loading) stringResource(R.string.collecting) else uiState.report,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
