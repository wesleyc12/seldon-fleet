package com.wesley.seldonfleet.ui

import com.wesley.seldonfleet.domain.model.Classification
import com.wesley.seldonfleet.domain.model.Device

/**
 * A device paired with the verdict the domain rule gave it.
 * Built by a ViewModel (never by a Composable); screens only display it.
 */
data class ClassifiedDevice(
    val device: Device,
    val classification: Classification,
)