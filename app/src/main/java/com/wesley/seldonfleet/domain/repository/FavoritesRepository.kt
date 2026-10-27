package com.wesley.seldonfleet.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Contract for the user's favorite devices (REQ-07).
 *
 * The domain only declares WHAT it needs; the data layer decides HOW it is stored
 * (DataStore today). The UI depends on this interface, never on the data class,
 * so tests can pass a fake implementation with zero change here.
 *
 * Only device IDs are stored: nothing else is persisted (spec section 2.3).
 */
interface FavoritesRepository {

    /**
     * The current set of favorite device IDs.
     * Emits again every time the set changes, so the screen updates by itself.
     */
    val favoriteIds: Flow<Set<String>>

    /** Marks the device as favorite if it is not, or unmarks it if it is. */
    suspend fun toggle(deviceId: String)
}