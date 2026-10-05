package io.github.thatonecodingperson.thortools.wii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The suggested layouts, checked binding by binding against RetroPup's Dolphin setup for the Thor, as Dolphin shows it
 * for the pad in its Xbox style (the printed A arrives as Button B there).
 */
class SuggestedLayoutTest {
    private val device = DolphinProfile.device(xboxStyle = true)
    private val pad = "Android/1/Xbox Wireless Controller"

    private fun profile(setup: WiiSetup): Map<String, String> = DolphinProfile.ini(WiiMapping(setup), device).lines()
        .filter { " = " in it }
        .associate { it.substringBefore(" = ") to it.substringAfter(" = ") }

    private fun assertBinds(profile: Map<String, String>, key: String, input: String) {
        val value = profile[key]
        assertTrue("$key is missing", value != null)
        assertTrue("$key = $value has no $input", "`$pad:$input`" in value!!)
    }

    private val remote = mapOf(
        "Buttons/A" to "Button B",
        "Buttons/B" to "Button A",
        "Buttons/1" to "Button Y",
        "Buttons/2" to "Button X",
        "Buttons/-" to "Select",
        "Buttons/+" to "Start",
        "D-Pad/Up" to "Axis 16-",
        "D-Pad/Down" to "Axis 16+",
        "D-Pad/Left" to "Axis 15-",
        "D-Pad/Right" to "Axis 15+",
    )

    private val pointer = mapOf(
        "IR/Up" to "Axis 14-",
        "IR/Down" to "Axis 14+",
        "IR/Left" to "Axis 11-",
        "IR/Right" to "Axis 11+",
        "IR/Recenter" to "Button R3",
        "Shake/X" to "Axis 22+",
        "Shake/Y" to "Axis 22+",
        "Shake/Z" to "Axis 22+",
    )

    @Test
    fun `the Wii Remote and Nunchuk tab has every binding of the layout`() {
        val profile = profile(WiiSetup.NUNCHUK)
        val nunchuk = mapOf(
            "Nunchuk/Buttons/C" to "Button L1",
            "Nunchuk/Buttons/Z" to "Axis 23+",
            "Nunchuk/Stick/Up" to "Axis 1-",
            "Nunchuk/Stick/Down" to "Axis 1+",
            "Nunchuk/Stick/Left" to "Axis 0-",
            "Nunchuk/Stick/Right" to "Axis 0+",
            "Nunchuk/Shake/X" to "Button L3",
            "Nunchuk/Shake/Y" to "Button L3",
            "Nunchuk/Shake/Z" to "Button L3",
        )
        (remote + pointer + nunchuk).forEach { (key, input) -> assertBinds(profile, key, input) }
        assertTrue("@(`$pad:Select`+`$pad:Axis 23+`)" in profile.getValue("Hotkeys/Sideways Toggle"))
        assertTrue("@(`$pad:Select`+`$pad:Axis 22+`)" in profile.getValue("Hotkeys/Upright Toggle"))
        assertEquals("True", profile["IR/Relative Input"])
        assertEquals("`Android/0/Device Sensors:Motor 0`", profile["Rumble/Motor"])
        assertEquals("Nunchuk", profile["Extension"])
    }

    @Test
    fun `the Pointing tab has the Wii Remote's part of the layout`() {
        val profile = profile(WiiSetup.POINTING)
        (remote + pointer).forEach { (key, input) -> assertBinds(profile, key, input) }
        assertTrue("Hotkeys/Sideways Toggle" in profile)
        assertTrue("Hotkeys/Upright Toggle" in profile)
    }

    @Test
    fun `the Classic Controller tab has every binding of the layout`() {
        val profile = profile(WiiSetup.CLASSIC)
        mapOf(
            "Classic/Buttons/A" to "Button B",
            "Classic/Buttons/B" to "Button A",
            "Classic/Buttons/X" to "Button Y",
            "Classic/Buttons/Y" to "Button X",
            "Classic/Buttons/ZL" to "Button L1",
            "Classic/Buttons/ZR" to "Button R1",
            "Classic/Buttons/-" to "Select",
            "Classic/Buttons/+" to "Start",
            "Classic/Left Stick/Up" to "Axis 1-",
            "Classic/Left Stick/Down" to "Axis 1+",
            "Classic/Left Stick/Left" to "Axis 0-",
            "Classic/Left Stick/Right" to "Axis 0+",
            "Classic/Right Stick/Up" to "Axis 14-",
            "Classic/Right Stick/Down" to "Axis 14+",
            "Classic/Right Stick/Left" to "Axis 11-",
            "Classic/Right Stick/Right" to "Axis 11+",
            "Classic/Triggers/L" to "Axis 23+",
            "Classic/Triggers/R" to "Axis 22+",
            "Classic/Triggers/L-Analog" to "Axis 23+",
            "Classic/Triggers/R-Analog" to "Axis 22+",
            "Classic/D-Pad/Up" to "Axis 16-",
            "Classic/D-Pad/Down" to "Axis 16+",
            "Classic/D-Pad/Left" to "Axis 15-",
            "Classic/D-Pad/Right" to "Axis 15+",
        ).forEach { (key, input) -> assertBinds(profile, key, input) }
        assertEquals("Classic", profile["Extension"])
    }
}
