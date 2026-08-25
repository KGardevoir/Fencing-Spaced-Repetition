// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fencing.spacedrepetition.ui.image.ImageLoad
import com.fencing.spacedrepetition.ui.image.rememberImageBitmap

/**
 * Card images, drawn from the shared image store.
 *
 * These used to be Coil's AsyncImage over an Android Uri. Coil 2 is
 * Android-only, and what it was doing here -- read a local file, decode it,
 * cache the result -- is now [rememberImageBitmap], so the screens that show
 * images can be shared instead of the library being replaced with a
 * multiplatform one.
 *
 * The paths are keys into the store now, but the parameter names stay
 * imagePaths: the database column is still image_paths, and renaming the
 * parameter without renaming the column would only move the mismatch.
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
                if (onClick != null) onClick() else showFullScreen = true
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        StoredImage(
            imagePath = imagePath,
            // The thumbnail is 4:3, so the width is the longer edge. Doubled
            // because the value is in dp and the decode wants pixels, and a
            // 2x screen is the common case -- guessing low here is the one
            // mistake that is visible rather than merely wasteful.
            maxDimension = (maxHeight * 1.33f * 2).toInt(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showFullScreen) {
        ImageFullScreenDialog(imagePath = imagePath, onDismiss = { showFullScreen = false })
    }
}

@Composable
fun ImageFullScreenDialog(imagePath: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                StoredImage(
                    imagePath = imagePath,
                    // Full screen: no cap worth applying, so ask for the
                    // image as stored and let it draw scaled to fit.
                    maxDimension = Int.MAX_VALUE,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
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
                CardImage(imagePath = imagePath, maxHeight = maxHeight, onClick = null)

                IconButton(
                    onClick = { onRemoveImage(imagePath) },
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove image",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(4.dp).size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * One image from the store, with something in its place until it arrives.
 *
 * Loading is asynchronous on both platforms, so there is always a frame with
 * no pixels. A blank box of the right size is drawn then, rather than nothing:
 * collapsing to zero height and expanding a moment later makes a list jump
 * under the reader's finger.
 *
 * A missing image is not blank but a broken-image icon. Reaching this means a
 * card references something the store does not have -- an import that dropped
 * an image, or storage evicted by the browser -- and silently showing an empty
 * frame would leave that looking like a slow load forever.
 */
@Composable
private fun StoredImage(
    imagePath: String,
    maxDimension: Int,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    when (val load = rememberImageBitmap(imagePath, maxDimension).value) {
        is ImageLoad.Loaded -> Image(
            bitmap = load.bitmap,
            contentDescription = "Card image",
            modifier = modifier,
            contentScale = contentScale
        )

        ImageLoad.Loading -> Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        )

        ImageLoad.Missing -> Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = "Image unavailable",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
