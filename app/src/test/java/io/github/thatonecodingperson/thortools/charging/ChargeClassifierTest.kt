package io.github.thatonecodingperson.thortools.charging

import android.os.BatteryManager.BATTERY_STATUS_CHARGING
import android.os.BatteryManager.BATTERY_STATUS_DISCHARGING
import android.os.BatteryManager.BATTERY_STATUS_FULL
import android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING
import org.junit.Assert.assertEquals
import org.junit.Test

class ChargeClassifierTest {

    private fun sample(
        plugged: Boolean = true,
        status: Int = BATTERY_STATUS_CHARGING,
        level: Int = 50,
        microAmp: Int = 0,
        microVolt: Int = 0,
        limit: Boolean = false,
        direct: Boolean = false,
    ) = ChargeSample(0, plugged, status, level, microAmp, microVolt, chargeLimitOn = limit, directPowerOn = direct)

    @Test
    fun `speed buckets follow SystemUI thresholds`() {
        assertEquals(ChargeClass.FAST, ChargeClassifier.classify(sample(microAmp = 3_000_000, microVolt = 9_000_000)))
        assertEquals(ChargeClass.NORMAL, ChargeClassifier.classify(sample(microAmp = 1_200_000, microVolt = 5_000_000)))
        assertEquals(ChargeClass.SLOW, ChargeClassifier.classify(sample(microAmp = 500_000, microVolt = 5_000_000)))
    }

    @Test
    fun `missing voltage falls back to 5 V and missing current to normal`() {
        assertEquals(ChargeClass.SLOW, ChargeClassifier.classify(sample(microAmp = 900_000, microVolt = 0)))
        assertEquals(ChargeClass.NORMAL, ChargeClassifier.classify(sample(microAmp = 0)))
    }

    @Test
    fun `unexplained stops are not charging`() {
        assertEquals(ChargeClass.NOT_CHARGING, ChargeClassifier.classify(sample(status = BATTERY_STATUS_NOT_CHARGING)))
        assertEquals(ChargeClass.NOT_CHARGING, ChargeClassifier.classify(sample(status = BATTERY_STATUS_DISCHARGING)))
    }

    @Test
    fun `intentional stops are held`() {
        assertEquals(ChargeClass.HELD, ChargeClassifier.classify(sample(status = BATTERY_STATUS_FULL)))
        assertEquals(ChargeClass.HELD, ChargeClassifier.classify(sample(status = BATTERY_STATUS_NOT_CHARGING, limit = true, level = 80)))
        assertEquals(ChargeClass.HELD, ChargeClassifier.classify(sample(status = BATTERY_STATUS_NOT_CHARGING, direct = true)))
        assertEquals(ChargeClass.HELD, ChargeClassifier.classify(sample(status = BATTERY_STATUS_NOT_CHARGING, level = 100)))
    }

    @Test
    fun `unplugged wins over everything`() {
        assertEquals(ChargeClass.UNPLUGGED, ChargeClassifier.classify(sample(plugged = false, status = BATTERY_STATUS_FULL)))
    }

    @Test
    fun `input voltage bands`() {
        assertEquals(InputBand.UNKNOWN, ChargeClassifier.inputBand(null))
        assertEquals(InputBand.V5, ChargeClassifier.inputBand(5_100_000))
        assertEquals(InputBand.V9, ChargeClassifier.inputBand(8_900_000))
        assertEquals(InputBand.V20, ChargeClassifier.inputBand(20_000_000))
    }
}
