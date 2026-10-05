package io.github.thatonecodingperson.thortools.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * A quick settings tile that shows a mode and switches to the next one on a tap. Reading and writing the mode goes
 * through PServer, which can be slow and takes turns with every other call, so it never runs on the main thread: the
 * accessibility service answers key events there. One call at a time, so quick taps step through the modes in order.
 */
abstract class ModeTileService : TileService() {

    @Inject
    lateinit var executor: ShellExecutor

    @Inject
    lateinit var prefs: SharedPrefsRepo

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** The label of the mode in use. Runs off the main thread. */
    @StringRes
    protected abstract fun currentMode(): Int

    /** Switches to the next mode and returns its label. Runs off the main thread. */
    @StringRes
    protected abstract fun switchToNextMode(): Int

    override fun onStartListening() {
        super.onStartListening()
        update { currentMode() }
    }

    override fun onClick() {
        super.onClick()
        update { switchToNextMode() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun update(work: () -> Int) {
        scope.launch {
            val label = if (executor.pServerAvailable) work() else null
            withContext(Dispatchers.Main) { show(label) }
        }
    }

    private fun show(@StringRes label: Int?) {
        val tile = qsTile ?: return
        tile.state = if (label == null) Tile.STATE_UNAVAILABLE else Tile.STATE_ACTIVE
        tile.subtitle = getString(label ?: R.string.unknown)
        tile.updateTile()
    }
}
