package com.wesley.seldonfleet.domain.usecase

import com.wesley.seldonfleet.domain.model.Classification
import com.wesley.seldonfleet.domain.model.Device
import com.wesley.seldonfleet.domain.model.DeviceStatus
import com.wesley.seldonfleet.domain.model.MissingData
import com.wesley.seldonfleet.domain.model.Signal
import com.wesley.seldonfleet.domain.model.Trigger

// Reference thresholds (spec 4.2 / PROTOCOLO.md section 1). Every comparison uses >=.
private const val CRITICAL_DRAIN_PCT_PER_HOUR = 12.0
private const val CRITICAL_CRASHES_7D = 3
private const val CRITICAL_JANK_PCT = 15.0

private const val ATTENTION_DRAIN_PCT_PER_HOUR = 7.0
private const val ATTENTION_CRASHES_7D = 1
private const val ATTENTION_JANK_PCT = 8.0

private const val MINUTES_PER_HOUR = 60.0

/**
 * The classification rule.
 *
 * Order: INDETERMINADO -> CRITICO -> ATENCAO -> OK. The first match wins.
 * Works with exact values: rounding is a UI concern (protocol, section 4).
 * It is a plain class, received by whoever uses it (spec 6.3), never a global object.
 */
class ClassifyDevice {

    operator fun invoke(device: Device): Classification {
        val capacity = device.batteryCapacityMah
        val screenMinutes = device.screenOnMinutes
        val energy = device.energyMah

        // 1. INDETERMINADO: reject invalid inputs BEFORE any division,
        //    so no path can produce NaN or Infinity (REQ-03).
        if (capacity == null || capacity <= 0 || screenMinutes <= 0 || energy == null) {
            return indeterminate(capacity, screenMinutes, energy)
        }

        // From here on the compiler knows capacity and energy are not null (smart cast),
        // and we know both divisors are greater than zero.
        val screenHours = screenMinutes / MINUTES_PER_HOUR
        val absoluteDrain = energy / screenHours
        // Same as (absoluteDrain / capacity) * 100. Multiplying first keeps
        // threshold values like 7.0 and 12.0 exact in floating point.
        val relativeDrain = absoluteDrain * 100.0 / capacity

        val criticalSignals = firedSignals(
            relativeDrain, device.crashCount7d, device.jankFramesPct,
            CRITICAL_DRAIN_PCT_PER_HOUR, CRITICAL_CRASHES_7D, CRITICAL_JANK_PCT
        )
        val attentionSignals = firedSignals(
            relativeDrain, device.crashCount7d, device.jankFramesPct,
            ATTENTION_DRAIN_PCT_PER_HOUR, ATTENTION_CRASHES_7D, ATTENTION_JANK_PCT
        )

        // 2 to 4. CRITICO is checked before ATENCAO, so it wins (precedence).
        val status = when {
            criticalSignals.isNotEmpty() -> DeviceStatus.CRITICO
            attentionSignals.isNotEmpty() -> DeviceStatus.ATENCAO
            else -> DeviceStatus.OK
        }

        // Only signals of the deciding level are reported (protocol, section 5).
        val triggers = when (status) {
            DeviceStatus.CRITICO -> criticalSignals
            DeviceStatus.ATENCAO -> attentionSignals
            else -> emptyList()
        }

        return Classification(
            status = status,
            absoluteDrainMahPerHour = absoluteDrain,
            relativeDrainPctPerHour = relativeDrain,
            triggers = triggers,
            missingData = emptyList()
        )
    }

    /** Lists every invalid input, so the UI can say exactly what is missing. */
    private fun indeterminate(capacity: Int?, screenMinutes: Int, energy: Double?): Classification {
        val missing = mutableListOf<MissingData>()
        if (capacity == null || capacity <= 0) missing.add(MissingData.BATTERY_CAPACITY)
        if (screenMinutes <= 0) missing.add(MissingData.SCREEN_TIME)
        if (energy == null) missing.add(MissingData.ENERGY)

        return Classification(
            status = DeviceStatus.INDETERMINADO,
            absoluteDrainMahPerHour = null,
            relativeDrainPctPerHour = null,
            triggers = emptyList(),
            missingData = missing
        )
    }

    /** Returns every signal that reached its threshold for one level. */
    private fun firedSignals(
        relativeDrain: Double,
        crashes: Int,
        jank: Double,
        drainLimit: Double,
        crashLimit: Int,
        jankLimit: Double
    ): List<Trigger> {
        val fired = mutableListOf<Trigger>()
        if (relativeDrain >= drainLimit) {
            fired.add(Trigger(Signal.RELATIVE_DRAIN, relativeDrain, drainLimit))
        }
        if (crashes >= crashLimit) {
            fired.add(Trigger(Signal.CRASH_COUNT, crashes.toDouble(), crashLimit.toDouble()))
        }
        if (jank >= jankLimit) {
            fired.add(Trigger(Signal.JANK_FRAMES, jank, jankLimit))
        }
        return fired
    }
}