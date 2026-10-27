package com.wesley.seldonfleet.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wesley.seldonfleet.domain.model.DeviceStatus
import com.wesley.seldonfleet.domain.repository.DeviceRepository
import com.wesley.seldonfleet.domain.repository.FavoritesRepository
import com.wesley.seldonfleet.domain.usecase.ClassifyDevice
import com.wesley.seldonfleet.ui.ClassifiedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * List screen ViewModel (MVVM, spec section 6.4).
 * Receives the repository contracts and the rule in the constructor (spec 6.3):
 * fakes can replace the real ones with zero changes here or in domain/.
 * It classifies each device once, through the domain rule, and keeps the filter.
 * Because the ViewModel outlives the screen, the filter survives the trip to the detail (REQ-06).
 * Favorites come from a stream: the saved set is the single source of truth (REQ-07).
 */
class FleetListViewModel(
    private val repository: DeviceRepository,
    private val classify: ClassifyDevice,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    // Private writable state; the screen only gets the read-only version.
    private val _uiState = MutableStateFlow<FleetListUiState>(FleetListUiState.Loading)
    val uiState: StateFlow<FleetListUiState> = _uiState.asStateFlow()

    // Whole fleet, classified once. Changing the filter never re-classifies.
    private var allDevices: List<ClassifiedDevice> = emptyList()
    private var selectedFilter: DeviceStatus? = null

    // Last set of favorite IDs received from the store (empty until the first value arrives).
    private var favoriteIds: Set<String> = emptySet()

    init {
        observeFavorites()
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = try {
                allDevices = repository.getDevices().map { device ->
                    ClassifiedDevice(device, classify(device)) // the domain decides
                }
                buildLoaded()
            } catch (e: IOException) {
                // File could not be opened or read.
                FleetListUiState.Error
            } catch (e: IllegalArgumentException) {
                // Malformed JSON or a guaranteed field missing (parser errors extend this type).
                FleetListUiState.Error
            }
        }
    }

    /**
     * Keeps listening to the saved favorites for as long as the ViewModel lives.
     * Every time the set changes (a tap, or the first read after the app opens),
     * the list is rebuilt with the new stars. Nothing is updated "by hand".
     */
    private fun observeFavorites() {
        viewModelScope.launch {
            favorites.favoriteIds.collect { ids ->
                favoriteIds = ids
                if (_uiState.value is FleetListUiState.Loaded) {
                    _uiState.value = buildLoaded()
                }
            }
        }
    }

    /** Called when the user taps a filter chip. null = "Todos". */
    fun onFilterSelected(filter: DeviceStatus?) {
        selectedFilter = filter
        if (_uiState.value is FleetListUiState.Loaded) {
            _uiState.value = buildLoaded()
        }
    }

    /**
     * Called when the user taps the star of a row.
     * It only asks the store to change; the new set comes back through
     * observeFavorites(), so the screen shows exactly what was saved.
     */
    fun onFavoriteToggled(deviceId: String) {
        viewModelScope.launch {
            try {
                favorites.toggle(deviceId)
            } catch (e: IOException) {
                // Write failed: no crash; the star keeps showing what is really saved.
            }
        }
    }

    private fun buildLoaded(): FleetListUiState.Loaded {
        val filter = selectedFilter // local copy so the compiler can smart cast it
        return FleetListUiState.Loaded(
            devices = if (filter == null) {
                allDevices
            } else {
                allDevices.filter { it.classification.status == filter }
            },
            // One entry per status, zero included, always over the whole fleet (REQ-05).
            counts = DeviceStatus.entries.associateWith { status ->
                allDevices.count { it.classification.status == status }
            },
            total = allDevices.size,
            selectedFilter = filter,
            favoriteIds = favoriteIds,
        )
    }

    companion object {
        /** Builds the ViewModel with its dependencies (manual injection, no DI library). */
        fun factory(
            repository: DeviceRepository,
            classify: ClassifyDevice,
            favorites: FavoritesRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { FleetListViewModel(repository, classify, favorites) }
        }
    }
}