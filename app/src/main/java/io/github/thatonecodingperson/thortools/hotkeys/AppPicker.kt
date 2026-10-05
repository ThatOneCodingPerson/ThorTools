package io.github.thatonecodingperson.thortools.hotkeys

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import io.github.thatonecodingperson.thortools.R

/** Every app with a launcher icon: pick one to open with a hotkey, or tick the apps where hotkeys are off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPicker(
    mode: AppPickerMode,
    apps: List<LaunchableApp>,
    loaded: Boolean,
    checked: Set<String>,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = apps.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (mode) {
                                AppPickerMode.OPEN_APP -> R.string.hotkeyPickApp
                                AppPickerMode.HOTKEYS_OFF -> R.string.hotkeysOffApps
                                AppPickerMode.HOTKEY_APPS -> R.string.hotkeyAppsTitle
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            if (mode != AppPickerMode.OPEN_APP) {
                Text(
                    text = stringResource(
                        when (mode) {
                            AppPickerMode.HOTKEYS_OFF -> R.string.hotkeysOffAppsInfo
                            else -> R.string.hotkeyAppsInfo
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.hotkeyPickAppSearch)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (!loaded) {
                Text(stringResource(R.string.hotkeyPickAppLoading), modifier = Modifier.padding(16.dp))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.weight(1f)) {
                items(shown, key = { it.packageName }) { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app.packageName) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Image(painter = rememberDrawablePainter(app.icon), contentDescription = null, modifier = Modifier.size(36.dp))
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                        )
                        if (mode != AppPickerMode.OPEN_APP) {
                            Checkbox(checked = app.packageName in checked, onCheckedChange = { onPick(app.packageName) })
                        }
                    }
                }
            }
        }
    }
}
