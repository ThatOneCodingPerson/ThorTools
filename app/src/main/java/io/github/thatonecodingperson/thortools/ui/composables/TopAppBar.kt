package io.github.thatonecodingperson.thortools.ui.composables

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.thatonecodingperson.thortools.R

/** A screen's top bar with a back arrow; [actions] go at its end (a form's Save, say). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubTopAppBar(@StringRes title: Int, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) = TopAppBar(
    title = { Text(text = stringResource(title)) },
    navigationIcon = {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
    },
    actions = actions,
)
