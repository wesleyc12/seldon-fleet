package com.wesley.seldonfleet.ui.detail

import com.wesley.seldonfleet.ui.ClassifiedDevice

/** Everything the detail screen can show. The screen only renders this state. */
sealed interface DeviceDetailUiState {

    /** The file is still being read. */
    data object Loading : DeviceDetailUiState

    /** The file could not be read or parsed (stage 4, decision 3): no crash. */
    data object Error : DeviceDetailUiState

    /** The file was read, but no device has the requested id. */
    data object NotFound : DeviceDetailUiState

    /** One device and the verdict the domain rule gave it. */
    data class Loaded(val item: ClassifiedDevice) : DeviceDetailUiState
}