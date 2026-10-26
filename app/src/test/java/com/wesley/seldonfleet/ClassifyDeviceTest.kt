package com.wesley.seldonfleet

import com.wesley.seldonfleet.domain.model.Device
import com.wesley.seldonfleet.domain.model.DeviceStatus
import com.wesley.seldonfleet.domain.model.MissingData
import com.wesley.seldonfleet.domain.model.Signal
import com.wesley.seldonfleet.domain.model.Trigger
import com.wesley.seldonfleet.domain.usecase.ClassifyDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the classification rule (spec 7.1, REQ-03, REQ-08).
 *
 * Thresholds are written as literal numbers on purpose: if someone changes
 * a threshold inside ClassifyDevice, these tests must fail.
 *
 * Helper device: capacity 1000 mAh and 60 min of screen, so
 * relative drain (%/h) = energyMah / 10. Example: 70.0 mAh -> 7.0 %/h.
 */
class ClassifyDeviceTest {

    private val classify = ClassifyDevice()

    /** Healthy device by default; each test changes only what it needs. */
    private fun device(
        capacity: Int? = 1000,
        screenMinutes: Int = 60,
        energyMah: Double? = 10.0, // 1 %/h: below every threshold
        jank: Double = 0.0,
        crashes: Int = 0
    ) = Device(
        id = "DEV-TEST",
        model = "Test Phone",
        soc = "Test SoC",
        tier = "mid",
        ramMb = 4096,
        androidApi = 34,
        batteryCapacityMah = capacity,
        batteryLevelPct = 50,
        screenOnMinutes = screenMinutes,
        energyMah = energyMah,
        jankFramesPct = jank,
        crashCount7d = crashes,
        lastSeen = "2026-10-20T00:00:00Z"
    )

    private fun statusOf(device: Device): DeviceStatus = classify(device).status

    // ---------- Spec Annex A examples ----------

    @Test
    fun `DEV-0137 is ATENCAO by crashes even with drain below 7`() {
        val result = classify(
            device(capacity = 4410, screenMinutes = 214, energyMah = 980.0, jank = 7.4, crashes = 2)
        )

        assertEquals(DeviceStatus.ATENCAO, result.status)
        assertEquals(6.2, result.relativeDrainPctPerHour!!, 0.05)
        assertEquals(listOf(Trigger(Signal.CRASH_COUNT, 2.0, 1.0)), result.triggers)
    }

    @Test
    fun `DEV-0204 already converted to 980 mAh is OK`() {
        val result = classify(
            device(capacity = 5000, screenMinutes = 186, energyMah = 980.0, jank = 4.1, crashes = 0)
        )

        assertEquals(DeviceStatus.OK, result.status)
        assertEquals(6.3, result.relativeDrainPctPerHour!!, 0.05)
        assertTrue(result.triggers.isEmpty())
    }

    @Test
    fun `DEV-0204 read as mAh without conversion would wrongly be CRITICO`() {
        // Documents defect 1: 3773 is mWh. Skipping the conversion flips the verdict.
        val result = classify(
            device(capacity = 5000, screenMinutes = 186, energyMah = 3773.0, jank = 4.1, crashes = 0)
        )

        assertEquals(DeviceStatus.CRITICO, result.status)
        assertEquals(24.3, result.relativeDrainPctPerHour!!, 0.05)
    }

    // ---------- Incomplete records (defect 2): INDETERMINADO, no NaN, no exception ----------

    @Test
    fun `capacity zero is INDETERMINADO without computing drain`() {
        val result = classify(device(capacity = 0))

        assertEquals(DeviceStatus.INDETERMINADO, result.status)
        assertNull(result.absoluteDrainMahPerHour)
        assertNull(result.relativeDrainPctPerHour)
        assertEquals(listOf(MissingData.BATTERY_CAPACITY), result.missingData)
    }

    @Test
    fun `missing capacity is INDETERMINADO`() {
        val result = classify(device(capacity = null))

        assertEquals(DeviceStatus.INDETERMINADO, result.status)
        assertNull(result.relativeDrainPctPerHour)
        assertEquals(listOf(MissingData.BATTERY_CAPACITY), result.missingData)
    }

    @Test
    fun `screen time zero is INDETERMINADO without computing drain`() {
        val result = classify(device(screenMinutes = 0))

        assertEquals(DeviceStatus.INDETERMINADO, result.status)
        assertNull(result.absoluteDrainMahPerHour)
        assertNull(result.relativeDrainPctPerHour)
        assertEquals(listOf(MissingData.SCREEN_TIME), result.missingData)
    }

    @Test
    fun `null energy (mWh without valid voltage) is INDETERMINADO`() {
        val result = classify(device(energyMah = null))

        assertEquals(DeviceStatus.INDETERMINADO, result.status)
        assertNull(result.relativeDrainPctPerHour)
        assertEquals(listOf(MissingData.ENERGY), result.missingData)
    }

    @Test
    fun `DEV-0311 stays INDETERMINADO even with bad jank and crashes`() {
        // Known limitation, protocol section 3: invalid input wins over other signals.
        val result = classify(
            device(capacity = 0, screenMinutes = 97, energyMah = 612.0, jank = 11.2, crashes = 1)
        )

        assertEquals(DeviceStatus.INDETERMINADO, result.status)
        assertTrue(result.triggers.isEmpty())
    }

    @Test
    fun `every invalid input is reported`() {
        val result = classify(device(capacity = 0, screenMinutes = 0))

        assertEquals(
            listOf(MissingData.BATTERY_CAPACITY, MissingData.SCREEN_TIME),
            result.missingData
        )
    }

    // ---------- Relative drain thresholds: 7 and 12 %/h ----------

    @Test
    fun `drain just below 7 stays OK`() {
        assertEquals(DeviceStatus.OK, statusOf(device(energyMah = 69.9))) // 6.99 %/h
    }

    @Test
    fun `drain exactly 7 is ATENCAO`() {
        assertEquals(DeviceStatus.ATENCAO, statusOf(device(energyMah = 70.0))) // 7.0 %/h
    }

    @Test
    fun `drain just below 12 stays ATENCAO`() {
        assertEquals(DeviceStatus.ATENCAO, statusOf(device(energyMah = 119.9))) // 11.99 %/h
    }

    @Test
    fun `drain exactly 12 is CRITICO`() {
        val result = classify(device(energyMah = 120.0)) // 12.0 %/h

        assertEquals(DeviceStatus.CRITICO, result.status)
        assertEquals(listOf(Trigger(Signal.RELATIVE_DRAIN, 12.0, 12.0)), result.triggers)
    }

    // ---------- Crash thresholds: 1 and 3 ----------

    @Test
    fun `zero crashes stays OK`() {
        assertEquals(DeviceStatus.OK, statusOf(device(crashes = 0)))
    }

    @Test
    fun `one crash is ATENCAO`() {
        assertEquals(DeviceStatus.ATENCAO, statusOf(device(crashes = 1)))
    }

    @Test
    fun `two crashes stay ATENCAO`() {
        assertEquals(DeviceStatus.ATENCAO, statusOf(device(crashes = 2)))
    }

    @Test
    fun `three crashes is CRITICO`() {
        assertEquals(DeviceStatus.CRITICO, statusOf(device(crashes = 3)))
    }

    // ---------- Jank thresholds: 8 and 15 % ----------

    @Test
    fun `jank just below 8 stays OK`() {
        assertEquals(DeviceStatus.OK, statusOf(device(jank = 7.99)))
    }

    @Test
    fun `jank exactly 8 is ATENCAO`() {
        assertEquals(DeviceStatus.ATENCAO, statusOf(device(jank = 8.0)))
    }

    @Test
    fun `jank just below 15 stays ATENCAO`() {
        assertEquals(DeviceStatus.ATENCAO, statusOf(device(jank = 14.99)))
    }

    @Test
    fun `jank exactly 15 is CRITICO`() {
        assertEquals(DeviceStatus.CRITICO, statusOf(device(jank = 15.0)))
    }

    // ---------- Precedence and justification (protocol section 5) ----------

    @Test
    fun `critical signal wins over a simultaneous attention signal`() {
        // Drain 13 %/h (CRITICO) + 1 crash (ATENCAO).
        val result = classify(device(energyMah = 130.0, crashes = 1))

        assertEquals(DeviceStatus.CRITICO, result.status)
        // The lower-level signal (1 crash) is not reported as a reason.
        assertEquals(listOf(Trigger(Signal.RELATIVE_DRAIN, 13.0, 12.0)), result.triggers)
    }

    @Test
    fun `all signals of the deciding level are reported`() {
        // Drain 13 %/h and jank 16 % are both CRITICO; 1 crash is only ATENCAO.
        val result = classify(device(energyMah = 130.0, jank = 16.0, crashes = 1))

        assertEquals(DeviceStatus.CRITICO, result.status)
        assertEquals(
            listOf(
                Trigger(Signal.RELATIVE_DRAIN, 13.0, 12.0),
                Trigger(Signal.JANK_FRAMES, 16.0, 15.0)
            ),
            result.triggers
        )
    }
}