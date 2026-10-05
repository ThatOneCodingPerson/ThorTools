package io.github.thatonecodingperson.thortools.diagnostics

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import io.github.thatonecodingperson.thortools.ui.composables.SubScreen
import io.github.thatonecodingperson.thortools.ui.composables.TriggerPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(@ApplicationContext private val context: Context, private val shell: ShellExecutor) : ViewModel() {

    /** A root logcat dump takes a while, so it runs off the main thread and says where the file went. */
    fun dumpLogToFile() {
        val timeStamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val file = "ThorTools_$timeStamp.log"
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                shell.executeAsRoot("logcat -d -v threadtime > /storage/emulated/0/$file").isSuccess
            }
            val text = if (saved) context.getString(R.string.dumpLogSaved, file) else context.getString(R.string.dumpLogFailed)
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun SetupScreen(
    viewModel: SetupViewModel = hiltViewModel(),
    onPermissions: () -> Unit,
    onCoexistence: () -> Unit,
    onDiagnostics: () -> Unit,
    onBack: () -> Unit,
) {
    SubScreen(title = R.string.setupAndDiagnostics, onBack = onBack) {
        TriggerPreference(
            icon = R.drawable.ic_info,
            title = R.string.permissionsTitle,
            description = R.string.permissionsDescription,
            onClick = onPermissions,
        )
        TriggerPreference(
            icon = R.drawable.ic_info,
            title = R.string.coexistence,
            description = R.string.coexistenceDescription,
            onClick = onCoexistence,
        )
        TriggerPreference(
            icon = R.drawable.ic_sliders,
            title = R.string.diagnostics,
            description = R.string.diagnosticsDescription,
            onClick = onDiagnostics,
        )
        TriggerPreference(
            icon = R.drawable.ic_file_save,
            title = R.string.dumpLogToFile,
            description = R.string.dumpLogToFileDescription,
            onClick = viewModel::dumpLogToFile,
        )
    }
}
