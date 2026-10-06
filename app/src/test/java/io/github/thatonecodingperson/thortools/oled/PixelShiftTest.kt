package io.github.thatonecodingperson.thortools.oled

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PixelShiftTest {

    @Test
    fun `the path stays within the radius, visits every point and never jumps across the circle`() {
        for (radius in 1..10) {
            val path = ShiftPath(radius)
            val inside = (-radius..radius).flatMap { y -> (-radius..radius).map { x -> Offset(x, y) } }
                .filter { it.x * it.x + it.y * it.y <= radius * radius }
            assertTrue(path.points.all { it.x * it.x + it.y * it.y <= radius * radius })
            assertEquals(inside.toSet(), path.points.toSet())
            // Around the loop, back to the start too: never more than a row's change in width at once.
            for (i in 0 until path.size) {
                val a = path.at(i)
                val b = path.at(i + 1)
                assertTrue("radius $radius step $i: $a -> $b", abs(a.y - b.y) <= 1 && abs(a.x - b.x) <= radius)
            }
        }
    }

    @Test
    fun `both screens start in the middle and then move apart`() {
        val path = ShiftPath(3)
        assertEquals(Offset(0, 0), path.at(path.topStart))
        assertEquals(Offset(0, 0), path.at(path.bottomStart))
        assertNotEquals(path.at(path.topStart + 1), path.at(path.bottomStart + 1))
        assertEquals(path.at(0), path.at(path.size))
    }

    @Test
    fun `each screen's placement is read from the display manager's dump`() {
        val projections = DisplayProjection.parse(DUMP)
        assertEquals(
            DisplayProjection(4630946441858561667, 0, 1, Box(0, 0, 1920, 1080), Box(1, 0, 1921, 1080)),
            projections[0],
        )
        assertEquals(
            DisplayProjection(4630946482288158084, 4, 1, Box(0, 0, 1240, 1080), Box(1, 0, 1241, 1080)),
            projections[4],
        )
        assertEquals(setOf(0, 4), projections.keys)
        assertEquals(Box(3, -2, 1243, 1078), projections.getValue(4).display.moved(Offset(2, -2)))
        assertEquals(emptyMap<Int, DisplayProjection>(), DisplayProjection.parse("nothing here"))
    }

    @Test
    fun `the engine's settings travel as one word`() {
        val config = EngineConfig(shiftDisplays = setOf(4, 0), radius = 5, everyMs = 30_000)
        assertEquals(
            "shift=0+4;r=5;every=30000;still=0;center=0;watch=-;areas=-;astill=180000;adim=0;ashift=0;aevery=60000;aexp=0",
            config.encode(),
        )
        assertEquals(config, EngineConfig.decode(config.encode()))
        assertTrue(EngineConfig.decode(EngineConfig().encode()).idle)
        val still = EngineConfig(shiftDisplays = setOf(0), whenStill = true, stillMs = 5_000, center = true, watchDisplays = setOf(4))
        assertEquals(still, EngineConfig.decode(still.encode()))
        assertEquals(setOf(0, 4), still.watching)
        assertEquals(setOf(4), still.copy(whenStill = false).watching)
        assertTrue(!EngineConfig(watchDisplays = setOf(4)).idle)
        assertEquals(EngineConfig.MAX_RADIUS, EngineConfig.decode("shift=0;r=99").radius)
        assertEquals(EngineConfig.MIN_EVERY_MS, EngineConfig.decode("shift=0;every=1").everyMs)
    }

    @Test
    fun `AYN's shifter and refresher are off while Thor Tools' own run, everything else as chosen`() {
        val chosen = AynProtection(shifter = true, radius = 1, shiftAfterMs = 10_000, refresher = true, refreshAfterMs = 20_000)
        assertEquals(chosen.copy(shifter = false), chosen.applied(ownShifter = true, ownRefresher = false))
        assertEquals(chosen.copy(refresher = false), chosen.applied(ownShifter = false, ownRefresher = true))
        assertEquals(chosen, chosen.applied(ownShifter = false, ownRefresher = false))
        val off = chosen.copy(shifter = false)
        assertEquals(off, off.applied(ownShifter = false, ownRefresher = false))
    }

    private companion object {
        val DUMP = """
            Display Devices: size=2
              DisplayDeviceInfo{"Built-in Screen": uniqueId="local:4630946441858561667", 1080 x 1920, modeId 2}
                mAdapter=LocalDisplayAdapter
                mUniqueId=local:4630946441858561667
                mCurrentLayerStack=0
                mCurrentOrientation=1
                mCurrentLayerStackRect=Rect(0, 0 - 1920, 1080)
                mCurrentDisplayRect=Rect(1, 0 - 1921, 1080)
                mPhysicalDisplayId=4630946441858561667
                , mScreenOffBrightnessSensorValueToLux=null}
              DisplayDeviceInfo{"Screen-2": uniqueId="local:4630946482288158084", 1080 x 1240, modeId 3}
                mUniqueId=local:4630946482288158084
                mCurrentLayerStack=4
                mCurrentOrientation=1
                mCurrentLayerStackRect=Rect(0, 0 - 1240, 1080)
                mCurrentDisplayRect=Rect(1, 0 - 1241, 1080)
                mPhysicalDisplayId=4630946482288158084

            LogicalDisplayMapper:
              mCurrentLayout=[{addr: {port=131}, dispId: 0(ON)}]
              Logical Displays: size=2
              Display 0:
                mDisplayId=0
                mDisplayOffset=(0, 1)
                mPrimaryDisplayDevice=Built-in Screen
              Display 4:
                mDisplayId=4
                mPrimaryDisplayDevice=Screen-2
            Display Power Controller:
                mCurrentOrientation=9
        """.trimIndent()
    }
}
