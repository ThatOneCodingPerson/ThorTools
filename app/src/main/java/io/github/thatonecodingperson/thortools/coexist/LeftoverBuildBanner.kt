package io.github.thatonecodingperson.thortools.coexist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.thatonecodingperson.thortools.R

@Composable
fun LeftoverBuildBanner(onAppInfo: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            Text(
                text = stringResource(R.string.leftoverBuildTitle),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.leftoverBuildText),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = onAppInfo, modifier = Modifier.align(Alignment.End)) {
                Text(text = stringResource(R.string.appInfo))
            }
        }
    }
}
