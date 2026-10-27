package com.wesley.seldonfleet.ui.detail

import com.wesley.seldonfleet.domain.model.Classification
import com.wesley.seldonfleet.domain.model.DeviceStatus
import com.wesley.seldonfleet.domain.model.MissingData
import com.wesley.seldonfleet.domain.model.Signal
import com.wesley.seldonfleet.domain.model.Trigger
import java.util.Locale

/*
 * Pure text helpers for the detail screen: no Android, no Compose, so they run in JVM tests.
 * They only turn the domain verdict into words; they never decide a status.
 * Rounding happens only here, for display (protocol, section 4).
 */

/** Shown when a value does not exist. The screen never shows NaN or Infinity. */
const val NO_VALUE = "—"

/** One decimal place with a comma, e.g. 13.4 -> "13,4". Display only. */
fun formatDecimal(value: Double): String =
    String.format(Locale.ROOT, "%.1f", value).replace('.', ',')

/** Thresholds are whole numbers (12, 7, 3...), so show "12" instead of "12,0". */
private fun formatThreshold(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else formatDecimal(value)

/** Status name used at the start of each sentence ("Crítico: ..."). */
private fun DeviceStatus.sentenceName(): String = when (this) {
    DeviceStatus.OK -> "OK"
    DeviceStatus.ATENCAO -> "Atenção"
    DeviceStatus.CRITICO -> "Crítico"
    DeviceStatus.INDETERMINADO -> "Sem dados"
}

/** One fired signal in words, with the observed value and the threshold (decision D3). */
private fun Trigger.describe(): String = when (signal) {
    Signal.RELATIVE_DRAIN ->
        "dreno de ${formatDecimal(observed)} %/h (limiar: ${formatThreshold(threshold)} %/h)"
    Signal.CRASH_COUNT -> {
        val crashes = observed.toInt() // crash counts are whole numbers
        val noun = if (crashes == 1) "falha" else "falhas"
        "$crashes $noun em 7 dias (limiar: ${formatThreshold(threshold)})"
    }
    Signal.JANK_FRAMES ->
        "${formatDecimal(observed)} % de quadros perdidos (limiar: ${formatThreshold(threshold)} %)"
}

/** Which input was missing or invalid, in words. */
private fun MissingData.describe(): String = when (this) {
    MissingData.BATTERY_CAPACITY -> "capacidade da bateria ausente ou menor ou igual a zero"
    MissingData.SCREEN_TIME -> "tempo de tela menor ou igual a zero"
    MissingData.ENERGY ->
        "consumo não pôde ser convertido para mAh (mWh sem tensão válida ou unidade desconhecida)"
}

/**
 * The justification shown on the detail screen, one line per reason (REQ-06).
 * Every signal of the deciding level is listed (protocol, section 5).
 */
fun justificationLines(classification: Classification): List<String> {
    val prefix = classification.status.sentenceName()
    return when (classification.status) {
        DeviceStatus.INDETERMINADO ->
            classification.missingData.map { "$prefix: ${it.describe()}." }
        DeviceStatus.OK ->
            listOf("OK: nenhum sinal atingiu os limiares de atenção.")
        DeviceStatus.ATENCAO, DeviceStatus.CRITICO ->
            classification.triggers.map { "$prefix: ${it.describe()}." }
    }
}

// ISO-8601 UTC timestamp, e.g. 2026-10-20T02:58:10Z (seconds and fraction optional).
private val ISO_UTC = Regex("""^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})(:\d{2}(\.\d+)?)?Z$""")

/**
 * "2026-10-20T02:58:10Z" -> "2026-10-20 02:58 UTC" (decision D4).
 * Plain text work: java.time would need extra setup on minSdk 24.
 * Anything unexpected is shown as it came, never hidden.
 */
fun formatLastSeen(raw: String): String {
    val match = ISO_UTC.matchEntire(raw) ?: return raw
    val (date, time) = match.destructured
    return "$date $time UTC"
}