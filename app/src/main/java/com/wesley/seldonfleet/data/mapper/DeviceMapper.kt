package com.wesley.seldonfleet.data.mapper

import com.wesley.seldonfleet.data.dto.DeviceDto
import com.wesley.seldonfleet.domain.model.Device

// Exact spellings accepted for energyUnit (decision 2: no case-insensitive guessing).
private const val UNIT_MAH = "mAh"
private const val UNIT_MWH = "mWh"

/**
 * Converts DeviceDto (file format) into Device (domain format).
 * It only translates: it never decides the status (spec section 9.1).
 */
class DeviceMapper {

    fun toDomain(dto: DeviceDto): Device = Device(
        id = dto.id,
        model = dto.model,
        soc = dto.soc,
        tier = dto.tier,
        ramMb = dto.ramMb,
        androidApi = dto.androidApi,
        // Kept as it arrives: 0 stays 0, missing stays null (decision 1).
        batteryCapacityMah = dto.batteryCapacityMah,
        batteryLevelPct = dto.batteryLevelPct,
        screenOnMinutes = dto.screenOnMinutes,
        energyMah = energyInMah(dto.energyConsumed, dto.energyUnit, dto.nominalVoltage),
        jankFramesPct = dto.jankFramesPct,
        crashCount7d = dto.crashCount7d,
        lastSeen = dto.lastSeen,
    )

    /**
     * Normalizes energy to mAh (REQ-02): mAh = mWh / nominalVoltage.
     * Returns null when it cannot be converted safely, so the domain
     * classifies the device as INDETERMINADO instead of guessing:
     * - mWh with missing or non-positive voltage (protocol, decision 6);
     * - unknown unit, compared with exact spelling (decision 2).
     */
    private fun energyInMah(value: Double, unit: String, voltage: Double?): Double? =
        when (unit) {
            UNIT_MAH -> value
            UNIT_MWH -> if (voltage != null && voltage > 0.0) value / voltage else null
            else -> null
        }
}