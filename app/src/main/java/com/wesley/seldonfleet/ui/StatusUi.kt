package com.wesley.seldonfleet.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wesley.seldonfleet.domain.model.DeviceStatus

/*
 * How each domain status looks on screen.
 * Display text and colors live here, in the UI layer, never inside the
 * domain enum (spec section 4.3). The `when` has no `else`: if a new status
 * is ever added to the domain, this file stops compiling until it is handled.
 */

/** Text shown on the chip and on the filter. */
fun DeviceStatus.label(): String = when (this) {
    DeviceStatus.OK -> "OK"
    DeviceStatus.ATENCAO -> "ATENÇÃO"
    DeviceStatus.CRITICO -> "CRÍTICO"
    DeviceStatus.INDETERMINADO -> "SEM DADOS"
}

/** Chip background color. Text is always shown too, so color is never the only cue. */
private fun DeviceStatus.chipColor(): Color = when (this) {
    DeviceStatus.OK -> Color(0xFF2E7D32)            // green
    DeviceStatus.ATENCAO -> Color(0xFFF9A825)       // amber
    DeviceStatus.CRITICO -> Color(0xFFC62828)       // red
    DeviceStatus.INDETERMINADO -> Color(0xFF757575) // gray
}

/** Dark text on amber, white text on the darker colors, for readable contrast. */
private fun DeviceStatus.chipTextColor(): Color =
    if (this == DeviceStatus.ATENCAO) Color.Black else Color.White

/** Visually distinct status chip (REQ-05). Only displays: it never decides the status. */
@Composable
fun StatusChip(status: DeviceStatus, modifier: Modifier = Modifier) {
    Surface(
        color = status.chipColor(),
        contentColor = status.chipTextColor(),
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}