package com.wesley.seldonfleet

import com.wesley.seldonfleet.domain.model.Device
import com.wesley.seldonfleet.domain.usecase.ClassifyDevice
import com.wesley.seldonfleet.ui.detail.formatDecimal
import com.wesley.seldonfleet.ui.detail.formatLastSeen
import com.wesley.seldonfleet.ui.detail.justificationLines
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Detail screen text (REQ-06): the justification names the signal, the observed value
 * and the threshold. Runs on the JVM, no emulator.
 * Devices go through the REAL domain rule first, so these tests also break if a threshold changes.
 */
class DetailTextTest {

    private val classify = ClassifyDevice()

    // Same trick as ClassifyDeviceTest: 1000 mAh battery and 60 min of screen,
    // so relative drain (%/h) = energy / 10. Default device is healthy (OK).
    private fun device(
        energy: Double = 10.0,
        capacity: Int? = 1000,
        screenMinutes: Int = 60,
        crashes: Int = 0,
        jank: Double = 0.0,
    ) = Device(
        id = "DEV-TEST",
        model = "Test model",
        soc = "Test SoC",
        tier = "mid",
        ramMb = 4096,
        androidApi = 34,
        batteryCapacityMah = capacity,
        batteryLevelPct = 50,
        screenOnMinutes = screenMinutes,
        energyMah = energy,
        jankFramesPct = jank,
        crashCount7d = crashes,
        lastSeen = "2026-10-20T02:58:10Z",
    )

    private fun linesFor(device: Device) = justificationLines(classify(device))

    @Test
    fun `critical drain names the signal, the value and the threshold`() {
        // energy 134 -> 13.4 %/h, the example from spec section 2.2
        assertEquals(
            listOf("Crítico: dreno de 13,4 %/h (limiar: 12 %/h)."),
            linesFor(device(energy = 134.0)),
        )
    }

    @Test
    fun `drain exactly at the threshold is reported with the threshold`() {
        assertEquals(
            listOf("Crítico: dreno de 12,0 %/h (limiar: 12 %/h)."),
            linesFor(device(energy = 120.0)),
        )
    }

    @Test
    fun `attention by crashes uses singular and plural`() {
        assertEquals(
            listOf("Atenção: 1 falha em 7 dias (limiar: 1)."),
            linesFor(device(crashes = 1)),
        )
        assertEquals(
            listOf("Atenção: 2 falhas em 7 dias (limiar: 1)."),
            linesFor(device(crashes = 2)),
        )
    }

    @Test
    fun `every signal of the deciding level gets its own line`() {
        assertEquals(
            listOf(
                "Crítico: dreno de 13,4 %/h (limiar: 12 %/h).",
                "Crítico: 15,0 % de quadros perdidos (limiar: 15 %).",
            ),
            linesFor(device(energy = 134.0, jank = 15.0)),
        )
    }

    @Test
    fun `lower level signals are not cited`() {
        // 1 crash is an ATENCAO signal; the status is CRITICO by drain, so only the drain is cited.
        assertEquals(
            listOf("Crítico: dreno de 13,4 %/h (limiar: 12 %/h)."),
            linesFor(device(energy = 134.0, crashes = 1)),
        )
    }

    @Test
    fun `incomplete record says which data is missing`() {
        assertEquals(
            listOf("Sem dados: capacidade da bateria ausente ou menor ou igual a zero."),
            linesFor(device(capacity = 0)),
        )
        assertEquals(
            listOf("Sem dados: tempo de tela menor ou igual a zero."),
            linesFor(device(screenMinutes = 0)),
        )
    }

    @Test
    fun `healthy device gets the OK sentence`() {
        assertEquals(
            listOf("OK: nenhum sinal atingiu os limiares de atenção."),
            linesFor(device()),
        )
    }

    @Test
    fun `numbers use one decimal place and a comma`() {
        assertEquals("6,2", formatDecimal(6.2305))
        assertEquals("980,0", formatDecimal(980.0))
    }

    @Test
    fun `last seen is shortened and unexpected text is kept as it came`() {
        assertEquals("2026-10-20 02:58 UTC", formatLastSeen("2026-10-20T02:58:10Z"))
        assertEquals("ontem", formatLastSeen("ontem"))
    }
}