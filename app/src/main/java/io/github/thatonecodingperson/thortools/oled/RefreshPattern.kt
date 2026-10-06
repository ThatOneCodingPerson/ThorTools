package io.github.thatonecodingperson.thortools.oled

import java.util.Base64

/**
 * What Thor Tools' refresher shows. None changes the whole screen's brightness more often than about once a second
 * (photosensitivity): the noise keeps an even grey on average, the colours change every [COLOUR_MS]. Pure parts.
 */
enum class RefreshPattern(val id: String) {
    /** A white bar sweeping down black. */
    SWEEP("sweep"),

    /** Fine black-and-white noise, new every [NOISE_MS]. */
    NOISE("noise"),

    /** The whole screen red, green, blue, white, then black, [COLOUR_MS] each. */
    COLOURS("colours"),

    /** The screen's own picture turned negative and softened: what was bright rests, what was dark works. */
    INVERSE("inverse"),
    ;

    companion object {
        const val NOISE_MS = 100L
        const val COLOUR_MS = 1_500L

        /** RGB colours of the colour wash, in order. */
        val COLOURS_RGB = listOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF, 0x000000)

        fun byId(id: String?): RefreshPattern? = entries.find { it.id == id }

        /** The colour wash's RGB colour [elapsedMs] in. */
        fun colourAt(elapsedMs: Long): Int = COLOURS_RGB[((elapsedMs / COLOUR_MS) % COLOURS_RGB.size).toInt()]

        /** The helper's copy of a screen ("width height base64-RGB") as negative ARGB pixels; null if it doesn't read. */
        fun negative(text: String): Inverse? {
            val parts = text.trim().split(' ')
            val width = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val height = parts.getOrNull(1)?.toIntOrNull() ?: return null
            val bytes = runCatching { Base64.getDecoder().decode(parts.getOrNull(2).orEmpty()) }.getOrNull() ?: return null
            if (width <= 0 || height <= 0 || bytes.size != width * height * 3) return null
            val pixels = IntArray(width * height) { i ->
                val r = 255 - (bytes[i * 3].toInt() and 0xFF)
                val g = 255 - (bytes[i * 3 + 1].toInt() and 0xFF)
                val b = 255 - (bytes[i * 3 + 2].toInt() and 0xFF)
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            return Inverse(width, height, pixels)
        }
    }
}

/** A negative copy of a screen, [width] x [height] ARGB [pixels]. */
class Inverse(val width: Int, val height: Int, val pixels: IntArray)
