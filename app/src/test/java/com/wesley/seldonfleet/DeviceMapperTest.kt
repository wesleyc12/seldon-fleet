package com.wesley.seldonfleet

import com.wesley.seldonfleet.data.dto.DeviceDto
import com.wesley.seldonfleet.data.mapper.DeviceMapper
import com.wesley.seldonfleet.domain.model.DeviceStatus
import com.wesley.seldonfleet.domain.usecase.ClassifyDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceMapperTest {

    private val mapper = DeviceMapper()
    private val classify = ClassifyDevice()

    // Healthy record based on DEV-0204 (Annex A). Each test changes only what it needs.
    private fun dto(
        energyConsumed: Double,
        energyUnit: String,
        nominalVoltage: Double? = 3.85,
        batteryCapacityMah: Int? = 5000,
    ) = DeviceDto(
        id = "DEV-TEST",
        model = "Test Phone",
        soc = "Test SoC",
        tier = "mid",
        ramMb = 6144,
        androidApi = 34,
        batteryCapacityMah = batteryCapacityMah,
        batteryLevelPct = 50,
        screenOnMinutes = 186,
        energyConsumed = energyConsumed,
        energyUnit = energyUnit,
        nominalVoltage = nominalVoltage,
        jankFramesPct = 4.1,
        crashCount7d = 0,
        lastSeen = "2026-10-20T02:58:10Z",
    )

    // --- Unit conversion (REQ-02) ---

    @Test
    fun `mWh is converted to mAh using nominal voltage (spec example)`() {
        val device = mapper.toDomain(dto(energyConsumed = 3773.0, energyUnit = "mWh", nominalVoltage = 3.85))
        assertEquals(980.0, device.energyMah!!, 1e-9)
    }

    @Test
    fun `mAh is passed through unchanged`() {
        val device = mapper.toDomain(dto(energyConsumed = 980.0, energyUnit = "mAh"))
        assertEquals(980.0, device.energyMah!!, 0.0)
    }

    @Test
    fun `same physical consumption in mWh and mAh gives the same drain and status`() {
        // 3750 mWh / 3.75 V = 1000 mAh exactly, so both records carry the same charge.
        val fromMwh = classify(mapper.toDomain(dto(energyConsumed = 3750.0, energyUnit = "mWh", nominalVoltage = 3.75)))
        val fromMah = classify(mapper.toDomain(dto(energyConsumed = 1000.0, energyUnit = "mAh")))

        // Whole result is equal: same drains, same status, same reasons.
        assertEquals(fromMah, fromMwh)
        // 1000 / 3.1 h / 5000 * 100 = 6.45 %/h -> OK. Unconverted it would be 24.2 %/h -> CRITICO.
        assertEquals(DeviceStatus.OK, fromMwh.status)
    }

    // --- Energy that cannot be converted safely -> null -> INDETERMINADO ---

    @Test
    fun `mWh without voltage gives null energy and INDETERMINADO`() {
        val device = mapper.toDomain(dto(energyConsumed = 3773.0, energyUnit = "mWh", nominalVoltage = null))
        assertNull(device.energyMah)
        assertEquals(DeviceStatus.INDETERMINADO, classify(device).status)
    }

    @Test
    fun `mWh with zero voltage gives null energy (protocol decision 6)`() {
        val device = mapper.toDomain(dto(energyConsumed = 3773.0, energyUnit = "mWh", nominalVoltage = 0.0))
        assertNull(device.energyMah)
    }

    @Test
    fun `unknown unit spelling gives null energy (decision 2)`() {
        // "MWH" would be megawatt-hour in SI: no case-insensitive guessing.
        val device = mapper.toDomain(dto(energyConsumed = 3773.0, energyUnit = "MWH"))
        assertNull(device.energyMah)
    }

    // --- Capacity is kept as it arrives (decision 1) ---

    @Test
    fun `capacity zero stays zero and missing capacity stays null`() {
        val zero = mapper.toDomain(dto(energyConsumed = 980.0, energyUnit = "mAh", batteryCapacityMah = 0))
        val missing = mapper.toDomain(dto(energyConsumed = 980.0, energyUnit = "mAh", batteryCapacityMah = null))
        assertEquals(0, zero.batteryCapacityMah)
        assertNull(missing.batteryCapacityMah)
    }
}