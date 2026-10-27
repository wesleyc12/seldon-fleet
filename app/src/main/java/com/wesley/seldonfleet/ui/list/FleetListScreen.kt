package com.wesley.seldonfleet.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wesley.seldonfleet.domain.model.DeviceStatus
import com.wesley.seldonfleet.ui.ClassifiedDevice
import com.wesley.seldonfleet.ui.StatusChip
import com.wesley.seldonfleet.ui.label

/**
 * Entry point: reads the ViewModel state and forwards user actions.
 * [onDeviceClick] only reports which device was tapped; navigation is decided outside this screen.
 */
@Composable
fun FleetListScreen(
    viewModel: FleetListViewModel,
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FleetListContent(
        state = state,
        onFilterSelected = viewModel::onFilterSelected,
        onDeviceClick = onDeviceClick,
        onToggleFavorite = viewModel::onFavoriteToggled,
        modifier = modifier,
    )
}

/** Only draws the state it receives. No classification happens in any Composable. */
@Composable
fun FleetListContent(
    state: FleetListUiState,
    onFilterSelected: (DeviceStatus?) -> Unit,
    onDeviceClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        FleetListUiState.Loading -> CenteredMessage("Carregando a frota…", modifier)
        FleetListUiState.Error -> CenteredMessage("Não foi possível ler os dados da frota.", modifier)
        is FleetListUiState.Loaded ->
            LoadedList(state, onFilterSelected, onDeviceClick, onToggleFavorite, modifier)
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

@Composable
private fun LoadedList(
    state: FleetListUiState.Loaded,
    onFilterSelected: (DeviceStatus?) -> Unit,
    onDeviceClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Frota",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        FilterRow(state = state, onFilterSelected = onFilterSelected)
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // key = device id: lets Compose track each row correctly when the filter changes.
            items(items = state.devices, key = { it.device.id }) { item ->
                DeviceRow(
                    item = item,
                    isFavorite = item.device.id in state.favoriteIds,
                    onClick = { onDeviceClick(item.device.id) },
                    onToggleFavorite = { onToggleFavorite(item.device.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

/** One chip per status with its count, plus "Todos". Single selection (decision D1). */
@Composable
private fun FilterRow(
    state: FleetListUiState.Loaded,
    onFilterSelected: (DeviceStatus?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.selectedFilter == null,
            onClick = { onFilterSelected(null) },
            label = { Text("Todos (${state.total})") },
        )
        DeviceStatus.entries.forEach { status ->
            FilterChip(
                selected = state.selectedFilter == status,
                onClick = { onFilterSelected(status) },
                label = { Text("${status.label()} (${state.counts[status] ?: 0})") },
            )
        }
    }
}

/**
 * One tappable row: favorite star, id, model, battery level and the status chip (spec 2.1).
 * The star is its own button: tapping it toggles the favorite and does NOT open the detail,
 * because the inner button consumes the tap before the row's clickable sees it.
 */
@Composable
private fun DeviceRow(
    item: ClassifiedDevice,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    ListItem(
        leadingContent = { FavoriteStar(isFavorite = isFavorite, onToggle = onToggleFavorite) },
        headlineContent = { Text(item.device.id) },
        supportingContent = {
            Text("${item.device.model} · Bateria ${item.device.batteryLevelPct}%")
        },
        trailingContent = { StatusChip(item.classification.status) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Full star = favorite, empty star = not favorite (shape changes, not only the color).
 * Plain text characters: no extra icon library needed.
 * The content description lets screen readers announce what the button does.
 */
@Composable
private fun FavoriteStar(isFavorite: Boolean, onToggle: () -> Unit) {
    IconToggleButton(
        checked = isFavorite,
        onCheckedChange = { onToggle() },
        modifier = Modifier.semantics {
            contentDescription = if (isFavorite) "Remover dos favoritos" else "Marcar como favorito"
        },
    ) {
        Text(
            text = if (isFavorite) "★" else "☆",
            style = MaterialTheme.typography.titleLarge,
            color = if (isFavorite) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}