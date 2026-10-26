package com.wesley.seldonfleet.domain.model

/**
 * Health state of a device, decided by the domain rule.
 *
 * These are domain names only. The text shown on screen ("SEM DADOS",
 * "ATENÇÃO", etc.) belongs to the UI layer (spec section 4.3), so no
 * display strings live in this enum.
 */
enum class DeviceStatus {
    OK,
    ATENCAO,
    CRITICO,
    INDETERMINADO
}