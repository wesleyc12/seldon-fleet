package com.wesley.seldonfleet

import com.wesley.seldonfleet.domain.model.Device
import com.wesley.seldonfleet.domain.model.DeviceStatus
import com.wesley.seldonfleet.domain.repository.DeviceRepository
import com.wesley.seldonfleet.domain.repository.FavoritesRepository
import com.wesley.seldonfleet.domain.usecase.ClassifyDevice
import com.wesley.seldonfleet.ui.list.FleetListUiState
import com.wesley.seldonfleet.ui.list.FleetListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * List ViewModel tests on the JVM, with no emulator, no frota.json and no DataStore file.
 * The real repositories are swapped for fakes with zero changes in the ViewModel
 * or in domain/ (spec section 6.3). The real domain rule is used on purpose,
 * so these tests also break if the rule changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FleetListViewModelTest {

    // viewModelScope runs on the Android main thread, which does not exist on the JVM.
    // A test dispatcher takes its place and runs the coroutines immediately.
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Fake repository: returns a hand-written list instead of reading a file. */
    private class FakeDeviceRepository(private val devices: List<Device>) : DeviceRepository {
        override suspend fun getDevices(): List<Device> = devices
    }

    /** Fake repository that simulates an unreadable file. */
    private class FailingDeviceRepository : DeviceRepository {
        override suspend fun getDevices(): List<Device> = throw IOException("file not readable")
    }

    /**
     * Fake favorites store: keeps the set in memory instead of a DataStore file.
     * Like the real one, it emits the new set every time it changes.
     */
    private class FakeFavoritesRepository(initial: Set<String> = emptySet()) : FavoritesRepository {
        private val state = MutableStateFlow(initial)

        /** What is "saved" right now, so the test can check the store itself. */
        val saved: Set<String> get() = state.value

        override val favoriteIds: Flow<Set<String>> = state

        override suspend fun toggle(deviceId: String) {
            state.value = if (deviceId in state.value) state.value - deviceId else state.value + deviceId
        }
    }

    // Healthy device: 10 mAh in 60 min on a 1000 mAh battery = 1 %/h, no crashes, no jank -> OK.
    private fun device(
        id: String,
        capacity: Int? = 1000,
        crashes: Int = 0,
    ) = Device(
        id = id,
        model = "Test model",
        soc = "Test SoC",
        tier = "mid",
        ramMb = 4096,
        androidApi = 34,
        batteryCapacityMah = capacity,
        batteryLevelPct = 50,
        screenOnMinutes = 60,
        energyMah = 10.0,
        jankFramesPct = 0.0,
        crashCount7d = crashes,
        lastSeen = "2026-10-20T00:00:00Z",
    )

    // One small fleet with every status: 2 OK, 1 ATENCAO, 1 CRITICO, 1 INDETERMINADO.
    private val fleet = listOf(
        device("OK-1"),
        device("OK-2"),
        device("ATT-1", crashes = 1),  // 1 crash -> ATENCAO
        device("CRIT-1", crashes = 3), // 3 crashes -> CRITICO
        device("IND-1", capacity = 0), // capacity 0 -> INDETERMINADO
    )

    private fun newViewModel(
        repository: DeviceRepository,
        favorites: FavoritesRepository = FakeFavoritesRepository(),
    ) = FleetListViewModel(repository, ClassifyDevice(), favorites)

    private fun loadedState(viewModel: FleetListViewModel): FleetListUiState.Loaded {
        val state = viewModel.uiState.value
        assertTrue("expected Loaded but was $state", state is FleetListUiState.Loaded)
        return state as FleetListUiState.Loaded
    }

    @Test
    fun `counts cover every status and add up to the total`() {
        val state = loadedState(newViewModel(FakeDeviceRepository(fleet)))

        assertEquals(
            mapOf(
                DeviceStatus.OK to 2,
                DeviceStatus.ATENCAO to 1,
                DeviceStatus.CRITICO to 1,
                DeviceStatus.INDETERMINADO to 1,
            ),
            state.counts,
        )
        assertEquals(5, state.total)
        assertEquals(state.total, state.counts.values.sum()) // REQ-05
        assertEquals(5, state.devices.size)                  // no filter = whole fleet
        assertNull(state.selectedFilter)
    }

    @Test
    fun `filter shows only that status and keeps the whole-fleet counts`() {
        val viewModel = newViewModel(FakeDeviceRepository(fleet))

        viewModel.onFilterSelected(DeviceStatus.CRITICO)
        val state = loadedState(viewModel)

        assertEquals(listOf("CRIT-1"), state.devices.map { it.device.id })
        assertEquals(DeviceStatus.CRITICO, state.selectedFilter)
        assertEquals(5, state.counts.values.sum()) // counts do not shrink with the filter
    }

    @Test
    fun `clearing the filter shows the whole fleet again`() {
        val viewModel = newViewModel(FakeDeviceRepository(fleet))

        viewModel.onFilterSelected(DeviceStatus.CRITICO)
        viewModel.onFilterSelected(null)
        val state = loadedState(viewModel)

        assertEquals(5, state.devices.size)
        assertNull(state.selectedFilter)
    }

    @Test
    fun `unreadable file becomes an error state instead of a crash`() {
        val viewModel = newViewModel(FailingDeviceRepository())

        assertEquals(FleetListUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun `favorites already saved show as marked when the list opens`() {
        // Simulates reopening the app: the store already has CRIT-1 saved (REQ-07).
        val favorites = FakeFavoritesRepository(initial = setOf("CRIT-1"))

        val state = loadedState(newViewModel(FakeDeviceRepository(fleet), favorites))

        assertEquals(setOf("CRIT-1"), state.favoriteIds)
    }

    @Test
    fun `tapping the star marks the device and tapping again unmarks it`() {
        val favorites = FakeFavoritesRepository()
        val viewModel = newViewModel(FakeDeviceRepository(fleet), favorites)

        viewModel.onFavoriteToggled("OK-1")
        assertEquals(setOf("OK-1"), favorites.saved)                  // the store changed
        assertEquals(setOf("OK-1"), loadedState(viewModel).favoriteIds) // the screen followed

        viewModel.onFavoriteToggled("OK-1")
        assertEquals(emptySet<String>(), favorites.saved)
        assertEquals(emptySet<String>(), loadedState(viewModel).favoriteIds)

        // Favorites never change the status counts (REQ-05).
        assertEquals(5, loadedState(viewModel).counts.values.sum())
    }
}