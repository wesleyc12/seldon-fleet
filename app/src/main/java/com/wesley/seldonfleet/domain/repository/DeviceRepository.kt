package com.wesley.seldonfleet.domain.repository

import com.wesley.seldonfleet.domain.model.Device

/**
 * Contract for "where the devices come from".
 *
 * Declared in the domain and implemented by the data layer (spec section 6.1):
 * the domain depends on an abstraction it owns, never on files or Android.
 * A fake implementation can replace the real one in tests with zero changes here.
 */
interface DeviceRepository {

    /** Returns every device in the fleet, already normalized (energy in mAh). */
    suspend fun getDevices(): List<Device>
}