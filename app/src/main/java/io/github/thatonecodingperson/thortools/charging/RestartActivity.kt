package io.github.thatonecodingperson.thortools.charging

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.AndroidEntryPoint
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import io.github.thatonecodingperson.thortools.ui.composables.DialogButton
import io.github.thatonecodingperson.thortools.ui.theme.ThorToolsTheme
import javax.inject.Inject

/** Confirms a restart from the charging alert. The reboot only happens after the user agrees here. */
@AndroidEntryPoint
class RestartActivity : ComponentActivity() {

    @Inject
    lateinit var executor: ShellExecutor

    @Inject
    lateinit var prefs: SharedPrefsRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThorToolsTheme(prefs.palette()) {
                AlertDialog(
                    onDismissRequest = ::finish,
                    title = { Text(stringResource(R.string.restartTitle)) },
                    text = { Text(stringResource(R.string.restartText)) },
                    confirmButton = {
                        DialogButton(text = stringResource(R.string.chargeAlertRestart)) {
                            Thread { executor.executeAsRoot("svc power reboot") }.start()
                            finish()
                        }
                    },
                    dismissButton = {
                        DialogButton(text = stringResource(R.string.cancel), onClick = ::finish)
                    },
                )
            }
        }
    }
}
