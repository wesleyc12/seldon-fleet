package com.wesley.seldonfleet.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wesley.seldonfleet.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The one DataStore for the favorites file (official pattern from the DataStore guide).
 *
 * DataStore allows only ONE active instance per file in the whole app; two instances
 * on the same file make the app crash. This delegate guarantees a single instance
 * per process, even when MainActivity (and AppContainer) are recreated on rotation.
 *
 * It is PRIVATE: no other file can reach it. Consumers never access it globally;
 * the ViewModel receives a FavoritesRepository through its constructor (spec 6.3).
 */
private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "favorites",
)

/**
 * Real implementation of the domain contract, backed by Preferences DataStore.
 * Stores a single value: the set of favorite device IDs (spec 2.3, REQ-07).
 *
 * The DataStore is received in the primary constructor, so this class does not
 * create its own dependency. The secondary constructor is the convenience used
 * by AppContainer: it plugs in the single app-wide instance declared above.
 */
class FavoritesRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : FavoritesRepository {

    /** Used by AppContainer: wires the single DataStore instance of the app. */
    constructor(context: Context) : this(context.favoritesDataStore)

    override val favoriteIds: Flow<Set<String>> = dataStore.data
        // If the file cannot be read, start with no favorites instead of crashing
        // (pattern recommended by the official DataStore guide).
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        // Missing key (first run, nothing saved yet) means an empty set.
        .map { preferences -> preferences[FAVORITE_IDS] ?: emptySet() }

    override suspend fun toggle(deviceId: String) {
        // edit {} reads, changes and writes the file as one safe transaction.
        dataStore.edit { preferences ->
            val current = preferences[FAVORITE_IDS] ?: emptySet()
            preferences[FAVORITE_IDS] =
                if (deviceId in current) current - deviceId else current + deviceId
        }
    }

    private companion object {
        /** The only key in the file: a set of strings (the device IDs). */
        val FAVORITE_IDS = stringSetPreferencesKey("favorite_ids")
    }
}