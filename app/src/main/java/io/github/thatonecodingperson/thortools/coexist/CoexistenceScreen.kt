package io.github.thatonecodingperson.thortools.coexist

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.ui.composables.OverlapConfirmDialog
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchPreference

@Composable
fun CoexistenceScreen(viewModel: CoexistenceViewModel = hiltViewModel(), onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    uiState.pendingConfirmation?.let { overlap ->
        OverlapConfirmDialog(
            overlap = overlap,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissConfirmation,
        )
    }

    Scaffold(topBar = { SubTopAppBar(title = R.string.coexistence, onBack = onBack) }) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(top = contentPadding.calculateTopPadding())
                .padding(end = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val odinTools = uiState.odinTools
            val status = when {
                !odinTools.installed -> stringResource(R.string.coexistenceAbsent)
                odinTools.serviceEnabled -> stringResource(R.string.coexistenceServiceOn, odinTools.versionName.orEmpty())
                else -> stringResource(R.string.coexistenceServiceOff, odinTools.versionName.orEmpty())
            }
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (odinTools.installed) {
                NoteCard(
                    text = stringResource(R.string.coexistenceSuperset),
                    actionLabel = stringResource(R.string.coexistenceAppInfo),
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", OdinToolsDetector.PACKAGE, null)),
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Text(
                text = stringResource(R.string.coexistenceIntro),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Overlap.entries.forEach { overlap ->
                SwitchPreference(
                    icon = R.drawable.ic_info,
                    title = overlap.title,
                    description = overlap.detail,
                    state = uiState.enabled[overlap] == true,
                ) { viewModel.toggle(overlap, it) }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
        }
    }
}
