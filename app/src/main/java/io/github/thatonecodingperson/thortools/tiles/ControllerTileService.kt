package io.github.thatonecodingperson.thortools.tiles

import dagger.hilt.android.AndroidEntryPoint
import io.github.thatonecodingperson.thortools.models.ControllerStyle

@AndroidEntryPoint
class ControllerTileService : ModeTileService() {

    override fun currentMode(): Int = ControllerStyle.getStyle(executor).textRes

    override fun switchToNextMode(): Int {
        val skipped = prefs.disabledControllerStyle?.let(ControllerStyle::getById)
        return ControllerStyle.next(ControllerStyle.getStyle(executor), skipped).also { it.enable(executor) }.textRes
    }
}
