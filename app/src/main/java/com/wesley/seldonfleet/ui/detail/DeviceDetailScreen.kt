package com.wesley.seldonfleet.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wesley.seldonfleet.ui.ClassifiedDevice
import com.wesley.seldonfleet.ui.StatusChip

/** Entry point: reads the ViewModel state. The system back button returns to the list (D6). */
@Composable
fun DeviceDetailScreen(viewModel: DeviceDetailViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DeviceDetailContent(state = state, modifier = modifier)
}

/** Only draws the state it receives. No classification happens in any Composable. */
@Composable
fun DeviceDetailContent(state: DeviceDetailUiState, modifier: Modifier = Modifier) {
    when (state) {
        DeviceDetailUiState.Loading -> CenteredMessage("Carregando o aparelho…", modifier)
        DeviceDetailUiState.Error -> CenteredMessage("Não foi possível ler os dados da frota.", modifier)
        DeviceDetailUiState.NotFound -> CenteredMessage("Aparelho não encontrado.", modifier)
        is DeviceDetailUiState.Loaded -> DeviceDetail(state.item, modifier)
    }
}

@Composable
private fun CenteredMessage(text: String, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Header, justification and the attributes listed in spec section 2.2. */
@Composable
private fun DeviceDetail(item: ClassifiedDevice, modifier: Modifier) {
    val device = item.device
    val classification = item.classification

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header: id, model and the same status chip used in the list.
        Text(text = device.id, style = MaterialTheme.typography.headlineSmall)
        Text(text = device.model, style = MaterialTheme.typography.titleMedium)
        StatusChip(classification.status)

        // Justification: words built from the domain verdict (REQ-06).
        Text(
            text = "Justificativa",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        justificationLines(classification).forEach { line ->
            Text(text = line, style = MaterialTheme.typography.bodyLarge)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Attributes from spec section 2.2. Missing values show NO_VALUE, never NaN.
        AttributeRow("SoC", device.soc)
        AttributeRow("Memória", "${device.ramMb} MB")
        AttributeRow("Nível de API", device.androidApi.toString())
        AttributeRow(
            "Capacidade da bateria",
            device.batteryCapacityMah?.let { "$it mAh" } ?: NO_VALUE,
        )
        AttributeRow(
            "Consumo (normalizado)",
            device.energyMah?.let { "${formatDecimal(it)} mAh" } ?: NO_VALUE,
        )
        AttributeRow("Tempo de tela", "${device.screenOnMinutes} min")
        AttributeRow(
            "Dreno relativo",
            classification.relativeDrainPctPerHour?.let { "${formatDecimal(it)} %/h" } ?: NO_VALUE,
        )
        AttributeRow("Quadros perdidos (jank)", "${formatDecimal(device.jankFramesPct)} %")
        AttributeRow("Falhas (7 dias)", device.crashCount7d.toString())
        AttributeRow("Último contato", formatLastSeen(device.lastSeen))
    }
}

/** One "label ..... value" line. */
@Composable
private fun AttributeRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}