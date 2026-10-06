package io.github.thatonecodingperson.thortools.oled

/** A rectangle as `Rect(left, top - right, bottom)`. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun moved(offset: Offset): Box = Box(left + offset.x, top + offset.y, right + offset.x, bottom + offset.y)
}

/**
 * Where the display manager last placed one screen's picture on its panel: the panel's physical id, the orientation,
 * the part of the screen's content shown, and where it lands on the panel. Moving [display] moves the whole picture.
 */
data class DisplayProjection(val physicalId: Long, val layerStackId: Int, val orientation: Int, val layerStack: Box, val display: Box) {
    companion object {
        private val rect = Regex("""Rect\((-?\d+), (-?\d+) - (-?\d+), (-?\d+)\)""")
        private val deviceStart = Regex("""^\s+DisplayDeviceInfo\{"([^"]*)"""")
        private val logicalStart = Regex("""^\s+Display (\d+):$""")

        /** Each logical display's projection, by display id, from `dumpsys display`. Pure. */
        fun parse(dump: String): Map<Int, DisplayProjection> {
            val devices = mutableMapOf<String, MutableMap<String, String>>()
            val logical = mutableMapOf<Int, String>()
            var device: MutableMap<String, String>? = null
            var display: Int? = null
            for (line in dump.lineSequence()) {
                val deviceMatch = deviceStart.find(line)
                val logicalMatch = logicalStart.find(line)
                when {
                    deviceMatch != null -> {
                        device = mutableMapOf<String, String>().also { devices[deviceMatch.groupValues[1]] = it }
                        display = null
                    }
                    logicalMatch != null -> {
                        display = logicalMatch.groupValues[1].toInt()
                        device = null
                    }
                    // A new section of the dump ends the block.
                    line.isNotEmpty() && !line.first().isWhitespace() -> {
                        device = null
                        display = null
                    }
                    else -> {
                        val field = line.trim()
                        val key = field.substringBefore('=', "")
                        val value = field.substringAfter('=')
                        val fields = device
                        val id = display
                        if (fields != null && (key.startsWith("mCurrent") || key == "mPhysicalDisplayId")) fields.putIfAbsent(key, value)
                        if (id != null && key == "mPrimaryDisplayDevice") logical.putIfAbsent(id, value)
                    }
                }
            }
            return logical.mapNotNull { (id, name) ->
                val fields = devices[name] ?: return@mapNotNull null
                val projection = DisplayProjection(
                    physicalId = fields["mPhysicalDisplayId"]?.toLongOrNull() ?: return@mapNotNull null,
                    layerStackId = fields["mCurrentLayerStack"]?.toIntOrNull() ?: return@mapNotNull null,
                    orientation = fields["mCurrentOrientation"]?.toIntOrNull() ?: return@mapNotNull null,
                    layerStack = box(fields["mCurrentLayerStackRect"]) ?: return@mapNotNull null,
                    display = box(fields["mCurrentDisplayRect"]) ?: return@mapNotNull null,
                )
                id to projection
            }.toMap()
        }

        private fun box(text: String?): Box? {
            val values = rect.find(text ?: return null)?.groupValues?.drop(1)?.map { it.toInt() } ?: return null
            return Box(values[0], values[1], values[2], values[3])
        }
    }
}
