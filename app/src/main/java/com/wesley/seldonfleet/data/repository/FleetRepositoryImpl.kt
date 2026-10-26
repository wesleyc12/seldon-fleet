package com.wesley.seldonfleet.data.repository

import com.wesley.seldonfleet.data.mapper.DeviceMapper
import com.wesley.seldonfleet.data.source.AssetFleetSource
import com.wesley.seldonfleet.data.source.FleetJsonParser
import com.wesley.seldonfleet.domain.model.Device
import com.wesley.seldonfleet.domain.repository.DeviceRepository

/**
 * Real implementation of the domain contract (spec section 6.1: data depends on domain).
 * Path of the data: read text -> parse DTOs -> map to Device (energy already in mAh).
 * Its parts are received in the constructor, never created inside (spec section 6.3).
 */
class FleetRepositoryImpl(
    private val source: AssetFleetSource,
    private val parser: FleetJsonParser,
    private val mapper: DeviceMapper,
) : DeviceRepository {

    override suspend fun getDevices(): List<Device> =
        parser.parse(source.readText()).map(mapper::toDomain)
}