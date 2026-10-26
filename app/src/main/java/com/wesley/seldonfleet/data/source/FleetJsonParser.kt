package com.wesley.seldonfleet.data.source

import com.wesley.seldonfleet.data.dto.DeviceDto
import com.wesley.seldonfleet.data.dto.FleetDto
import kotlinx.serialization.json.Json

/**
 * Turns the text of frota.json into DTOs. Pure Kotlin (no Context), so it runs on the JVM.
 *
 * If the file breaks the contract (malformed JSON or a guaranteed field missing),
 * decoding throws an exception. It is not swallowed here: it goes up to the UI,
 * which shows an explicit error message (decision 3).
 */
class FleetJsonParser {

    // Root keys not declared in FleetDto (schemaVersion, exportedAt, fleetId,
    // deviceCount) are ignored instead of failing the whole file.
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<DeviceDto> =
        json.decodeFromString(FleetDto.serializer(), text).devices
}