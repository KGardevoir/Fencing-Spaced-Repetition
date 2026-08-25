// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Says an attached image was large, once, without getting in the way.
 *
 * Not a dialog and not a refusal. Images live on the user's own device, so an
 * oversized one costs them their own storage and nothing else; the honest
 * response is to mention it and carry on. It disappears as soon as another
 * image is attached or the screen is left.
 */
@Composable
fun LargeImageNotice(byteCount: Int?, modifier: Modifier = Modifier) {
    if (byteCount == null) return

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "That image is ${byteCount.asMegabytes()} MB. It is kept as-is, " +
                "which uses that much space on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun Int.asMegabytes(): String {
    val tenths = (this * 10L) / (1024 * 1024)
    return "${tenths / 10}.${tenths % 10}"
}
