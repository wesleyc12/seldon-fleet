package com.wesley.seldonfleet

import com.wesley.seldonfleet.data.mapper.DeviceMapper
import com.wesley.seldonfleet.data.source.FleetJsonParser
import com.wesley.seldonfleet.domain.model.DeviceStatus
import com.wesley.seldonfleet.domain.usecase.ClassifyDevice
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Reads the real assets/frota.json on the JVM and runs it through
 * parser -> mapper -> rule: the same path the app uses, minus the Android file read.
 * Expected counts come from an independent script that applied the pre-registered rule.
 */
class FleetFileTest {

    // Unit tests run with the module folder (app/) as the working directory.
    private val text = File("src/main/assets/frota.json").readText()
    private val devices = FleetJsonParser().parse(text).map(DeviceMapper()::toDomain)
    private val classify = ClassifyDevice()

    private fun statusOf(id: String) = classify(devices.first { it.id == id }).status

    @Test
    fun `real file is read completely`() {
        assertEquals(30, devices.size)
    }

    @Test
    fun `status counts match the expected result and add up to the total (REQ-05)`() {
        val counts = devices.groupingBy { classify(it).status }.eachCount()
        assertEquals(16, counts[DeviceStatus.OK])
        assertEquals(8, counts[DeviceStatus.ATENCAO])
        assertEquals(4, counts[DeviceStatus.CRITICO])
        assertEquals(2, counts[DeviceStatus.INDETERMINADO])
        assertEquals(devices.size, counts.values.sum())
    }

    @Test
    fun `known devices get the expected status`() {
        assertEquals(DeviceStatus.ATENCAO, statusOf("DEV-0137"))       // 2 crashes
        assertEquals(DeviceStatus.OK, statusOf("DEV-0204"))            // mWh, converted
        assertEquals(DeviceStatus.CRITICO, statusOf("DEV-0522"))       // mWh, 13.4 %/h
        assertEquals(DeviceStatus.INDETERMINADO, statusOf("DEV-0311")) // capacity 0
        assertEquals(DeviceStatus.INDETERMINADO, statusOf("DEV-0745")) // screen time 0
    }
}