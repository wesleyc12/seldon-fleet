package com.wesley.seldonfleet.domain.model

/** Measured signal that can fire a status. */
enum class Signal {
    RELATIVE_DRAIN,
    CRASH_COUNT,
    JANK_FRAMES
}

/**
 * Input that is missing or invalid and makes the record impossible to classify.
 * ENERGY means the data layer could not convert it to mAh
 * (mWh without a valid nominal voltage, protocol section 6).
 */
enum class MissingData {
    BATTERY_CAPACITY,
    SCREEN_TIME,
    ENERGY
}

/** One signal that fired, with the observed value and the threshold it reached. */
data class Trigger(
    val signal: Signal,
    val observed: Double,
    val threshold: Double
)

/**
 * Result of classifying one device.
 *
 * [triggers] lists every signal of the deciding level (protocol, section 5).
 * [missingData] is filled only when the status is INDETERMINADO.
 * Drains are null when they cannot be computed: never NaN or Infinity.
 */
data class Classification(
    val status: DeviceStatus,
    val absoluteDrainMahPerHour: Double?,
    val relativeDrainPctPerHour: Double?,
    val triggers: List<Trigger>,
    val missingData: List<MissingData>
)