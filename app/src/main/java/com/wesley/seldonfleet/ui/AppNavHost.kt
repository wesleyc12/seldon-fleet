package com.wesley.seldonfleet.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.wesley.seldonfleet.domain.repository.DeviceRepository
import com.wesley.seldonfleet.domain.usecase.ClassifyDevice
import com.wesley.seldonfleet.ui.detail.DeviceDetailScreen
import com.wesley.seldonfleet.ui.detail.DeviceDetailViewModel
import com.wesley.seldonfleet.ui.list.FleetListScreen
import com.wesley.seldonfleet.ui.list.FleetListViewModel
import kotlinx.serialization.Serializable

/** Route of the list screen (type-safe navigation: the route is a Kotlin type). */
@Serializable
object FleetListRoute

/** Route of the detail screen. The device id travels inside the route. */
@Serializable
data class DeviceDetailRoute(val deviceId: String)

/**
 * The two screens and how to move between them.
 * Receives the domain contract and the rule (never data/ classes) and hands them
 * to each ViewModel factory (manual dependency injection, spec 6.3).
 *
 * Each back stack entry owns its ViewModels. While the detail is open, the list entry
 * stays on the stack, so FleetListViewModel and its filter stay alive (REQ-06).
 * The system back button pops the detail and the list shows the same filter.
 */
@Composable
fun AppNavHost(
    repository: DeviceRepository,
    classify: ClassifyDevice,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = FleetListRoute,
        modifier = modifier,
    ) {
        composable<FleetListRoute> {
            // Scoped to the list entry: created once, reused when coming back from the detail.
            val listViewModel: FleetListViewModel = viewModel(
                factory = FleetListViewModel.factory(repository, classify),
            )
            FleetListScreen(
                viewModel = listViewModel,
                onDeviceClick = { deviceId -> navController.navigate(DeviceDetailRoute(deviceId)) },
            )
        }

        composable<DeviceDetailRoute> { backStackEntry ->
            // Reads the id from the route, then builds the detail ViewModel with it.
            val route: DeviceDetailRoute = backStackEntry.toRoute()
            val detailViewModel: DeviceDetailViewModel = viewModel(
                factory = DeviceDetailViewModel.factory(route.deviceId, repository, classify),
            )
            DeviceDetailScreen(viewModel = detailViewModel)
        }
    }
}