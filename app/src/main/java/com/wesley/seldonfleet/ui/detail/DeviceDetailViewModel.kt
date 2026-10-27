package com.wesley.seldonfleet.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wesley.seldonfleet.domain.repository.DeviceRepository
import com.wesley.seldonfleet.domain.usecase.ClassifyDevice
import com.wesley.seldonfleet.ui.ClassifiedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Detail screen ViewModel (MVVM, spec section 6.4).
 * Receives the device id, the repository contract and the rule in the constructor (spec 6.3).
 * It finds one device and classifies it through the domain rule: the screen never classifies.
 * It reads the repository again instead of sharing the list ViewModel (decision D2),
 * so each screen stays independent and testable.
 */
class DeviceDetailViewModel(
    private val deviceId: String,
    private val repository: DeviceRepository,
    private val classify: ClassifyDevice,
) : ViewModel() {

    // Private writable state; the screen only gets the read-only version.
    private val _uiState = MutableStateFlow<DeviceDetailUiState>(DeviceDetailUiState.Loading)
    val uiState: StateFlow<DeviceDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = try {
                val device = repository.getDevices().firstOrNull { it.id == deviceId }
                if (device == null) {
                    DeviceDetailUiState.NotFound
                } else {
                    // The domain decides; the ViewModel only pairs device and verdict.
                    DeviceDetailUiState.Loaded(ClassifiedDevice(device, classify(device)))
                }
            } catch (e: IOException) {
                // File could not be opened or read.
                DeviceDetailUiState.Error
            } catch (e: IllegalArgumentException) {
                // Malformed JSON or a guaranteed field missing (parser errors extend this type).
                DeviceDetailUiState.Error
            }
        }
    }

    companion object {
        /** Builds the ViewModel with its dependencies (manual injection, no DI library). */
        fun factory(
            deviceId: String,
            repository: DeviceRepository,
            classify: ClassifyDevice,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { DeviceDetailViewModel(deviceId, repository, classify) }
        }
    }
}