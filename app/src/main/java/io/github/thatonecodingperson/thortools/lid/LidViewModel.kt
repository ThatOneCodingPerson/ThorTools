package io.github.thatonecodingperson.thortools.lid

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** SleepManager, a separate app with much more for sleep and the lid, and how to get it. */
object SleepManagerApp {
    const val PACKAGE = "com.med.sleepmanager"
    const val PAGE = "https://github.com/Baggio94/SleepManager"

    /** Obtainium's link that opens its "add app" page with this one filled in (the address goes in unencoded). */
    const val OBTAINIUM_ADD = "obtainium://add/$PAGE"
    val OBTAINIUM_PACKAGES = listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")

    /** Its launcher activity; resolved when started, so no package lookup is needed for it. */
    fun launchIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(PACKAGE)
}

data class LidUiModel(
    val choices: LidChoices = LidChoices(),
    val last: LidResult? = null,
    val sleepManagerInstalled: Boolean = false,
    val obtainiumInstalled: Boolean = false,
)

@HiltViewModel
class LidViewModel @Inject constructor(@ApplicationContext private val context: Context, private val prefs: SharedPrefsRepo) : ViewModel() {

    private val _uiState = MutableStateFlow(LidUiModel(choices = prefs.lidChoices))
    val uiState: StateFlow<LidUiModel> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { prefs.lidResultChanges().collect { last -> _uiState.update { it.copy(last = last) } } }
    }

    /** Whether SleepManager and Obtainium are installed; again each time the screen shows, as either may have come since. */
    fun refresh() {
        viewModelScope.launch {
            val (sleepManager, obtainium) = withContext(Dispatchers.IO) {
                installed(SleepManagerApp.PACKAGE) to SleepManagerApp.OBTAINIUM_PACKAGES.any(::installed)
            }
            _uiState.update { it.copy(sleepManagerInstalled = sleepManager, obtainiumInstalled = obtainium) }
        }
    }

    fun change(update: (LidChoices) -> LidChoices) {
        val choices = update(_uiState.value.choices)
        prefs.lidChoices = choices
        _uiState.update { it.copy(choices = choices) }
    }

    private fun installed(packageName: String): Boolean = runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
}
