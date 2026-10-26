package com.wesley.seldonfleet.data.dto

import kotlinx.serialization.Serializable

/**
 * Root object of assets/frota.json.
 * Only "devices" is needed. The other root keys (schemaVersion, exportedAt,
 * fleetId, deviceCount) are ignored by the Json configuration in the data layer.
 */
@Serializable
data class FleetDto(
    val devices: List<DeviceDto>,
)

/**
 * Mirrors one record of frota.json exactly as it arrives, defects included
 * (spec section 3.2 and Annex B).
 *
 * Nullability follows the file contract (decision 3):
 * - only batteryCapacityMah and nominalVoltage may be missing, so only they are nullable;
 * - every other field is guaranteed by the spec. If one is missing, parsing the whole
 *   file fails and the UI shows an explicit error message.
 *
 * No unit conversion and no validation here: this class only describes the format.
 */
@Serializable
data class DeviceDto(
    val id: String,
    val model: String,
    val soc: String,
    val tier: String,
    val ramMb: Int,
    val androidApi: Int,
    // May be 0 or missing (spec 3.2). "= null" makes the key optional in the JSON.
    val batteryCapacityMah: Int? = null,
    val batteryLevelPct: Int,
    val screenOnMinutes: Int,
    // Unit is given by energyUnit ("mAh" or "mWh"). Converted only in DeviceMapper.
    val energyConsumed: Double,
    val energyUnit: String,
    // Present when energyUnit = "mWh" (spec 3.2); may be missing otherwise.
    val nominalVoltage: Double? = null,
    val jankFramesPct: Double,
    val crashCount7d: Int,
    val lastSeen: String,
)