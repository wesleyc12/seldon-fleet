package com.wesley.seldonfleet

import android.content.Context
import com.wesley.seldonfleet.data.mapper.DeviceMapper
import com.wesley.seldonfleet.data.repository.FavoritesRepositoryImpl
import com.wesley.seldonfleet.data.repository.FleetRepositoryImpl
import com.wesley.seldonfleet.data.source.AssetFleetSource
import com.wesley.seldonfleet.data.source.FleetJsonParser
import com.wesley.seldonfleet.domain.repository.DeviceRepository
import com.wesley.seldonfleet.domain.repository.FavoritesRepository
import com.wesley.seldonfleet.domain.usecase.ClassifyDevice

/**
 * Composition root: the only place that knows the concrete classes.
 * Builds the objects by hand (manual dependency injection, spec section 6.3).
 * It is a plain class created by MainActivity, never a global singleton.
 * The UI gets these objects through the ViewModel constructor, so ui/ never imports data/.
 */
class AppContainer(context: Context) {

    // Typed as the domain interface: the rest of the app only sees the contract.
    val deviceRepository: DeviceRepository = FleetRepositoryImpl(
        source = AssetFleetSource(context.applicationContext),
        parser = FleetJsonParser(),
        mapper = DeviceMapper(),
    )

    // The classification rule, received by whoever needs it (never created inside a screen).
    val classifyDevice: ClassifyDevice = ClassifyDevice()

    // Favorites saved with DataStore (REQ-07), also typed as the domain interface.
    // Safe to build again when MainActivity is recreated: the DataStore behind it
    // is a single app-wide instance kept inside the data layer.
    val favoritesRepository: FavoritesRepository =
        FavoritesRepositoryImpl(context.applicationContext)
}