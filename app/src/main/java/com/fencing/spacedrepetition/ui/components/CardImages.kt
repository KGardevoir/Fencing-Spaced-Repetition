// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

/**
 * Display multiple card images in a horizontal scrollable row
 */
@Composable
fun CardImagesDisplay(
    imagePaths: List<String>,
    modifier: Modifier = Modifier,
    maxHeight: Int = 200,
    onImageClick: ((String) -> Unit)? = null
) {
    if (imagePaths.isEmpty()) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(imagePaths) { imagePath ->
            CardImage(
                imagePath = imagePath,
                maxHeight = maxHeight,
                onClick = { onImageClick?.invoke(imagePath) }
            )
        }
    }
}

/**
 * Display a single card image
 */
@Composable
fun CardImage(
    imagePath: String,
    modifier: Modifier = Modifier,
    maxHeight: Int = 200,
    onClick: (() -> Unit)? = null
) {
    var showFullScreen by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .height(maxHeight.dp)
            .width((maxHeight * 1.33f).dp)
            .clickable {
                if (onClick != null) {
                    onClick()
                } else {
                    showFullScreen = true
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        AsyncImage(
            model = Uri.parse(imagePath),
            contentDescription = "Card image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }

    // Full screen dialog
    if (showFullScreen) {
        ImageFullScreenDialog(
            imagePath = imagePath,
            onDismiss = { showFullScreen = false }
        )
    }
}

/**
 * Display image in full screen dialog
 */
@Composable
fun ImageFullScreenDialog(
    imagePath: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                AsyncImage(
                    model = Uri.parse(imagePath),
                    contentDescription = "Card image full screen",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )

                // Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Display editable card images with remove functionality
 */
@Composable
fun CardImagesEdit(
    imagePaths: List<String>,
    onRemoveImage: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Int = 150
) {
    if (imagePaths.isEmpty()) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(imagePaths) { imagePath ->
            Box {
                CardImage(
                    imagePath = imagePath,
                    maxHeight = maxHeight,
                    onClick = null
                )

                // Remove button
                IconButton(
                    onClick = { onRemoveImage(imagePath) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove image",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
