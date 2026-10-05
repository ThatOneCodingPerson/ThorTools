package io.github.thatonecodingperson.thortools.tiles

import dagger.hilt.android.AndroidEntryPoint
import io.github.thatonecodingperson.thortools.models.L2R2Style

@AndroidEntryPoint
class L2R2TileService : ModeTileService() {

    override fun currentMode(): Int = L2R2Style.getStyle(executor).textRes

    override fun switchToNextMode(): Int {
        val skipped = prefs.disabledL2r2Style?.let(L2R2Style::getById)
        return L2R2Style.next(L2R2Style.getStyle(executor), skipped).also { it.enable(executor) }.textRes
    }
}
