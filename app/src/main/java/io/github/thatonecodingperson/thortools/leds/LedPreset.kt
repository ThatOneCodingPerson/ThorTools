package io.github.thatonecodingperson.thortools.leds

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R

/** The looks a profile can give the stick lights; stored as the look's text, so the lights screen and profiles share one form. */
enum class LedPreset(val id: String, val look: LedLook, @StringRes val textRes: Int) {
    OFF("off", LedLook(LedMode.OFF), R.string.ledPresetOff),
    RAINBOW("rainbow", LedLook(LedMode.RAINBOW), R.string.ledPresetRainbow),
    BATTERY("battery", LedLook(LedMode.BATTERY), R.string.ledPresetBattery),
    RED("red", colour(0xFF0000), R.string.ledColourRed),
    ORANGE("orange", colour(0xFF6A00), R.string.ledColourOrange),
    YELLOW("yellow", colour(0xFFD000), R.string.ledColourYellow),
    GREEN("green", colour(0x00FF40), R.string.ledColourGreen),
    CYAN("cyan", colour(0x00E5FF), R.string.ledColourCyan),
    BLUE("blue", colour(0x0050FF), R.string.ledColourBlue),
    PURPLE("purple", colour(0x8A2BE2), R.string.ledColourPurple),
    PINK("pink", colour(0xFF00C8), R.string.ledColourPink),
    WHITE("white", colour(0xFFFFFF), R.string.ledColourWhite),
    ;

    companion object {
        fun byId(id: String?): LedPreset? = entries.find { it.id == id }

        /** The preset a stored look came from, or null for a look made elsewhere. */
        fun of(stored: String?): LedPreset? = stored?.let { text -> entries.find { it.look == LedLook.decode(text) } }

        /** Plain colours for the lights screen's swatches. */
        val colours: List<LedPreset> get() = entries.filter { it.look.mode == LedMode.COLOUR }
    }
}

private fun colour(rgb: Int) = LedLook(LedMode.COLOUR, left = rgb, right = rgb)
