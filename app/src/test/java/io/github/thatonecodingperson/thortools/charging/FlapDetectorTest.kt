package io.github.thatonecodingperson.thortools.charging

import io.github.thatonecodingperson.thortools.charging.ChargeClass.FAST
import io.github.thatonecodingperson.thortools.charging.ChargeClass.HELD
import io.github.thatonecodingperson.thortools.charging.ChargeClass.NOT_CHARGING
import io.github.thatonecodingperson.thortools.charging.ChargeClass.SLOW
import io.github.thatonecodingperson.thortools.charging.ChargeClass.UNPLUGGED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlapDetectorTest {

    private val second = 1_000L
    private val minute = 60 * second

    /** Feeds the classes one [step] apart starting at [start] and returns every verdict. */
    private fun FlapDetector.feed(vararg classes: ChargeClass, start: Long = 0, step: Long = 20 * second) =
        classes.mapIndexed { index, chargeClass -> onSample(start + index * step, chargeClass) }

    @Test
    fun `steady charging never alerts`() {
        val detector = FlapDetector()
        val verdicts = detector.feed(*Array(60) { FAST })
        assertTrue(verdicts.none { it.alerting })
    }

    @Test
    fun `the reported Thor cycle alerts once`() {
        val detector = FlapDetector()
        val verdicts = detector.feed(FAST, NOT_CHARGING, SLOW, FAST, NOT_CHARGING, SLOW, FAST, NOT_CHARGING)

        val firstAlert = verdicts.indexOfFirst { it.alerting }
        assertEquals(6, firstAlert)
        assertEquals(1, verdicts.count { it.alertChanged })
    }

    @Test
    fun `changes spread beyond the window do not add up`() {
        val detector = FlapDetector()
        val verdicts = detector.feed(FAST, SLOW, FAST, SLOW, FAST, SLOW, FAST, SLOW, step = 3 * minute)
        assertTrue(verdicts.none { it.alerting })
    }

    @Test
    fun `holding at the limit is not a flip`() {
        val detector = FlapDetector()
        val verdicts = detector.feed(FAST, HELD, HELD, HELD, HELD)
        assertEquals(1, verdicts.last().changesInWindow)
    }

    @Test
    fun `short disconnects count as flips`() {
        val detector = FlapDetector(sensitivity = Sensitivity.HIGH)
        var time = 0L
        detector.onSample(time, FAST)
        repeat(4) {
            detector.onSample(time + 1 * second, UNPLUGGED)
            detector.onSample(time + 3 * second, FAST)
            time += 30 * second
        }
        assertTrue(detector.tick(time).alerting)
    }

    @Test
    fun `a real unplug clears the alert`() {
        val detector = FlapDetector()
        val verdicts = detector.feed(FAST, NOT_CHARGING, SLOW, FAST, NOT_CHARGING, SLOW, FAST)
        val alertAt = 6 * 20 * second
        assertTrue(verdicts.last().alerting)

        detector.onSample(alertAt + second, UNPLUGGED)
        val cleared = detector.tick(alertAt + 30 * second)
        assertFalse(cleared.alerting)
        assertTrue(cleared.alertChanged)
        assertEquals(0, cleared.changesInWindow)
    }

    @Test
    fun `the alert clears after a calm period`() {
        val detector = FlapDetector()
        detector.feed(FAST, NOT_CHARGING, SLOW, FAST, NOT_CHARGING, SLOW, FAST)
        val lastChange = 6 * 20 * second

        assertTrue(detector.tick(lastChange + 4 * minute).alerting)
        val later = detector.tick(lastChange + 11 * minute)
        assertFalse(later.alerting)
        assertTrue(later.alertChanged)
    }

    @Test
    fun `voltage renegotiation counts even when the label stays the same`() {
        val detector = FlapDetector(sensitivity = Sensitivity.HIGH)
        val bands = listOf(InputBand.V9, InputBand.V5, InputBand.V9, InputBand.V5, InputBand.V9)
        val verdicts = bands.mapIndexed { index, band -> detector.onSample(index * 10 * second, FAST, band) }
        assertTrue(verdicts.last().alerting)
    }

    @Test
    fun `the voltage reading appearing late is not a flip`() {
        val detector = FlapDetector()
        detector.onSample(0, FAST, InputBand.UNKNOWN)
        val verdict = detector.onSample(5 * second, FAST, InputBand.V9)
        assertEquals(0, verdict.changesInWindow)
    }
}
