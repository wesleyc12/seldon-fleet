package com.wesley.seldonfleet.ui.list

import com.wesley.seldonfleet.domain.model.DeviceStatus
import com.wesley.seldonfleet.ui.ClassifiedDevice

/** Everything the list screen can show. The screen only renders this state. */
sealed interface FleetListUiState {

    /** The file is still being read. */
    data object Loading : FleetListUiState

    /** The file could not be read or parsed (stage 4, decision 3): no crash, no empty list. */
    data object Error : FleetListUiState

    /**
     * Fleet loaded and classified.
     * [devices] is already filtered. [counts] always covers the whole fleet,
     * so the counts add up to [total] whatever the filter (REQ-05).
     * [selectedFilter] null means "all devices".
     */
    data class Loaded(
        val devices: List<ClassifiedDevice>,
        val counts: Map<DeviceStatus, Int>,
        val total: Int,
        val selectedFilter: DeviceStatus?,
    ) : FleetListUiState
}