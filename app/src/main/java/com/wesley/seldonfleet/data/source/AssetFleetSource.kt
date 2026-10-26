package com.wesley.seldonfleet.data.source

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FLEET_FILE = "frota.json"

/**
 * Reads assets/frota.json packaged inside the app (REQ-01: local file, no network).
 * The only data-layer class that needs Android (Context).
 */
class AssetFleetSource(private val context: Context) {

    // File reading runs on the IO thread pool, so the screen never freezes.
    suspend fun readText(): String = withContext(Dispatchers.IO) {
        context.assets.open(FLEET_FILE).bufferedReader().use { it.readText() }
    }
}