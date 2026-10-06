package io.github.thatonecodingperson.thortools.oled

import io.github.thatonecodingperson.thortools.input.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OledSafetyTest {
    private val minute = IdleWatch.MINUTE_MS
    private val dimBoth = OledChoices(idle = true, idleMinutes = 5)

    @Test
    fun `nothing is protected while idle protection is off`() {
        val watch = IdleWatch(start = 0)
        assertEquals(emptySet<Screen>(), watch.idle(OledChoices(), now = 60 * minute, controllerOn = null, mediaPlaying = false))
    }

    @Test
    fun `a screen without input for the chosen minutes is idle, the touched one isn't`() {
        val watch = IdleWatch(start = 0)
        watch.touched(Screen.TOP, 4 * minute)
        assertEquals(setOf(Screen.BOTTOM), watch.idle(dimBoth, now = 5 * minute, controllerOn = Screen.TOP, mediaPlaying = false))
        val both = setOf(Screen.TOP, Screen.BOTTOM)
        assertEquals(both, watch.idle(dimBoth, now = 9 * minute, controllerOn = Screen.TOP, mediaPlaying = false))
    }

    @Test
    fun `a button counts for the screen with the controller only, or both while that isn't known`() {
        val watch = IdleWatch(start = 0)
        watch.pressed(Screen.TOP, 5 * minute)
        assertEquals(setOf(Screen.BOTTOM), watch.idle(dimBoth, now = 6 * minute, controllerOn = Screen.TOP, mediaPlaying = false))
        watch.pressed(null, 7 * minute)
        assertEquals(emptySet<Screen>(), watch.idle(dimBoth, now = 8 * minute, controllerOn = Screen.TOP, mediaPlaying = false))
    }

    @Test
    fun `only the chosen screens are protected`() {
        val watch = IdleWatch(start = 0)
        val bottomOnly = dimBoth.copy(idleScreens = ScreenChoice.BOTTOM)
        assertEquals(setOf(Screen.BOTTOM), watch.idle(bottomOnly, now = 10 * minute, controllerOn = Screen.TOP, mediaPlaying = false))
    }

    @Test
    fun `media keeps the screen with the controller unprotected, the other one still is`() {
        val watch = IdleWatch(start = 0)
        assertEquals(setOf(Screen.BOTTOM), watch.idle(dimBoth, now = 10 * minute, controllerOn = Screen.TOP, mediaPlaying = true))
        assertEquals(emptySet<Screen>(), watch.idle(dimBoth, now = 10 * minute, controllerOn = null, mediaPlaying = true))
        val evenWithMedia = dimBoth.copy(notWhileMedia = false)
        val both = setOf(Screen.TOP, Screen.BOTTOM)
        assertEquals(both, watch.idle(evenWithMedia, now = 10 * minute, controllerOn = Screen.TOP, mediaPlaying = true))
    }

    @Test
    fun `the screens coming on start both over`() {
        val watch = IdleWatch(start = 0)
        watch.restart(20 * minute)
        assertEquals(emptySet<Screen>(), watch.idle(dimBoth, now = 22 * minute, controllerOn = null, mediaPlaying = false))
    }

    @Test
    fun `the choices survive being stored, and broken text gives the defaults`() {
        val choices = OledChoices(
            idle = true,
            look = IdleLook.BLACK,
            idleScreens = ScreenChoice.BOTTOM,
            idleMinutes = 12,
            dimPercent = 50,
            notWhileMedia = false,
            refreshScreens = ScreenChoice.TOP,
            refreshSeconds = 20,
            shift = true,
            shiftScreens = ScreenChoice.BOTTOM,
            shiftRadius = 6,
            shiftEverySeconds = 300,
            shiftMode = ShiftMode.STILL,
            shiftStillSeconds = 45,
            shiftCenter = true,
            refreshPattern = RefreshPattern.INVERSE,
            refreshAuto = true,
            refreshAfterMinutes = 3,
            areas = true,
            areaScreens = ScreenChoice.TOP,
            areaAction = AreaAction.BOTH,
            areaStillSeconds = 600,
            areaDimPercent = 40,
            areaShiftPixels = 4,
            areaEverySeconds = 20,
        )
        assertEquals(choices, OledChoices.decode(choices.encode()))
        assertEquals(OledChoices(), OledChoices.decode(null))
        assertEquals(OledChoices(), OledChoices.decode("look=zz;minutes=x"))
        assertEquals(30, OledChoices.decode("minutes=99").idleMinutes)
        assertEquals(30, OledChoices.decode("dim=1").dimPercent)
        assertEquals(60, OledChoices.decode("every=7").shiftEverySeconds)
        assertEquals(10, OledChoices.decode("radius=40").shiftRadius)
    }

    @Test
    fun `every protection that is switched on counts as on, the refresher only when it runs by itself`() {
        assertEquals(emptyList<OledPart>(), OledChoices().activeParts)
        assertEquals(emptyList<OledPart>(), OledChoices(refreshPattern = RefreshPattern.NOISE).activeParts)
        assertEquals(listOf(OledPart.SHIFTER), OledChoices(shift = true).activeParts)
        assertEquals(
            listOf(OledPart.SHIFTER, OledPart.STILL_AREAS, OledPart.REFRESHER, OledPart.IDLE),
            OledChoices(shift = true, areas = true, refreshAuto = true, idle = true).activeParts,
        )
    }

    @Test
    fun `AYN's values read and written in AYN's order`() {
        val owner = AynProtection.decode("1,1,10000,1,20000")
        assertEquals(AynProtection(shifter = true, radius = 1, shiftAfterMs = 10_000, refresher = true, refreshAfterMs = 20_000), owner)
        assertEquals("1,1,10000,0,20000", owner!!.copy(refresher = false).encode())
        assertEquals(listOf(1, 1, 10_000, 0, 20_000), owner.copy(refresher = false).values())
        assertNull(AynProtection.decode("1,2,3"))
        assertNull(AynProtection.decode("a,b,c,d,e"))
        assertNull(AynProtection.decode(null))
    }

    @Test
    fun `an empty setting means AYN's defaults`() {
        assertEquals(AynProtection.DEFAULT, AynProtection.fromSetting(null))
        assertEquals(AynProtection.DEFAULT, AynProtection.fromSetting("null"))
        assertEquals(AynProtection.DEFAULT, AynProtection.fromSetting(""))
        assertEquals("1,1,3000,1,30000", AynProtection.DEFAULT.encode())
    }

    @Test
    fun `AYN's values outside the controls' ranges read as they are`() {
        val owner = AynProtection.decode("1,10,2000,1,20000")
        assertEquals(AynProtection(shifter = true, radius = 10, shiftAfterMs = 2_000, refresher = true, refreshAfterMs = 20_000), owner)
        assertEquals("1,10,2000,0,20000", owner!!.copy(refresher = false).encode())
    }
}
