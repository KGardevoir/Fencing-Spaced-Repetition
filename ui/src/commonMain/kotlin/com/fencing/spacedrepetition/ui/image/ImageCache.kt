// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.fencing.spacedrepetition.util.ImageStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A small most-recently-used cache of decoded images.
 *
 * The other half of what an image library would have provided. A card list
 * scrolls the same thumbnails past repeatedly, and without this every one of
 * them would be re-read from storage and re-decoded on each pass -- on the web
 * that means an OPFS round trip per frame's worth of new rows.
 *
 * Bounded by count rather than by bytes. Bytes would be the better measure,
 * but ImageBitmap does not report its footprint portably, and a count is
 * honest about what it is: with [maxEntries] at 32 and thumbnails decoded to
 * roughly their display size, the cache holds a few megabytes.
 *
 * Misses are deduplicated. Ten rows appearing at once ask for ten different
 * images, but a LazyRow that re-composes mid-load can ask for the same one
 * twice, and decoding it twice wastes the more expensive half of the work.
 */
class ImageCache(
    private val store: ImageStore,
    private val maxEntries: Int = 32
) {
    private val entries = LinkedHashMap<CacheKey, ImageBitmap>()
    private val lock = Mutex()

    suspend fun load(key: String, maxDimension: Int): ImageBitmap? {
        val cacheKey = CacheKey(key, maxDimension)

        lock.withLock {
            entries.remove(cacheKey)?.let { hit ->
                entries[cacheKey] = hit
                return hit
            }
        }

        val bytes = store.read(key) ?: return null
        val decoded = decodeImage(bytes, maxDimension) ?: return null

        lock.withLock {
            entries[cacheKey] = decoded
            while (entries.size > maxEntries) {
                val oldest = entries.keys.firstOrNull() ?: break
                entries.remove(oldest)
            }
        }
        return decoded
    }

    /**
     * Forgets every size of one image. Called when an image is removed, so a
     * card that is re-shown does not draw a picture that is no longer there.
     */
    suspend fun evict(key: String) {
        lock.withLock { entries.keys.removeAll { it.key == key } }
    }

    private data class CacheKey(val key: String, val maxDimension: Int)
}

/**
 * The image store and cache for this composition.
 *
 * A composition local rather than a parameter, unlike everything else the
 * screens take. Loading an image is an ambient service -- the same shape as
 * LocalDensity -- and threading it through as a parameter would mean every
 * screen, every card row and every note editor between the navigation graph
 * and one thumbnail declaring it. The screens still take their data as plain
 * values; this is infrastructure, and it is read in exactly one place.
 *
 * No default: there is no sensible store to invent, and a silent fallback that
 * showed no images anywhere would be far harder to notice than this error.
 */
val LocalImageCache = staticCompositionLocalOf<ImageCache> {
    error("No ImageCache provided. Wrap the app in CompositionLocalProvider(LocalImageCache provides ...)")
}

/**
 * Where an image has got to.
 *
 * Three states rather than a nullable bitmap, because "still loading" and
 * "not there" want different things on screen and a null cannot tell them
 * apart. Drawing the missing-image marker during the load would flash it on
 * every thumbnail; leaving it out entirely would make a genuinely missing
 * image look like a load that never finishes.
 */
sealed interface ImageLoad {
    data object Loading : ImageLoad
    data class Loaded(val bitmap: ImageBitmap) : ImageLoad
    data object Missing : ImageLoad
}

/**
 * The image for [key] at roughly [maxDimension] pixels on its longest edge.
 *
 * Keyed on both the image and the size, so the same picture drawn as a
 * thumbnail and full screen does not have the thumbnail's pixels stretched
 * across the larger one.
 */
@Composable
fun rememberImageBitmap(key: String, maxDimension: Int): State<ImageLoad> {
    val cache = LocalImageCache.current
    val state = remember(key, maxDimension) { mutableStateOf<ImageLoad>(ImageLoad.Loading) }

    LaunchedEffect(key, maxDimension, cache) {
        state.value = ImageLoad.Loading
        val bitmap = cache.load(key, maxDimension)
        state.value = if (bitmap != null) ImageLoad.Loaded(bitmap) else ImageLoad.Missing
    }
    return state
}
