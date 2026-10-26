package com.wesley.seldonfleet.domain.model

/**
 * A fleet device, already normalized by the data layer.
 *
 * The domain never sees the original energy unit: [energyMah] is always mAh.
 * A null field means "missing or invalid in the source file". The classifier
 * turns it into INDETERMINADO instead of guessing a value (protocol, section 3).
 */
data class Device(
    val id: String,
    val model: String,
    val soc: String,
    val tier: String,
    val ramMb: Int,
    val androidApi: Int,
    // null = capacity missing in the file. A value of 0 is kept as 0:
    // deciding that 0 is invalid is the domain rule's job, not the data layer's.
    val batteryCapacityMah: Int?,
    val batteryLevelPct: Int,
    val screenOnMinutes: Int,
    // Energy already converted to mAh by the data layer.
    // null = energy could not be converted to mAh: mWh without a valid nominal
    // voltage (protocol, section 6) or an unknown energy unit (stage 4, decision 2).
    val energyMah: Double?,
    val jankFramesPct: Double,
    val crashCount7d: Int,
    // ISO-8601 UTC timestamp kept as text: the rule does not use it,
    // the detail screen only displays it.
    val lastSeen: String
)