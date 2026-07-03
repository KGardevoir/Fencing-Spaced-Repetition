// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.web

import com.fencing.spacedrepetition.ui.downloadImage
import com.fencing.spacedrepetition.ui.image.ImageExporter
import com.fencing.spacedrepetition.util.ImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The browser half of [ImageExporter]: a download.
 *
 * There is no chooser, unlike Android's, because a browser does not offer one
 * -- a download goes where downloads go, under the name the page asks for.
 * That also means there is nothing to report: the browser shows the download
 * itself, so a failure here is silent by design rather than by omission, and
 * a missing image is one that was already drawn as a broken-image icon.
 *
 * The name is the word photo and the store's extension, for the reason the
 * Android one gives: the key is a content hash and no name for a file anyone
 * has to find again.
 */
fun browserImageExporter(scope: CoroutineScope, store: ImageStore): ImageExporter =
    ImageExporter { key ->
        scope.launch {
            val bytes = store.read(key) ?: return@launch
            downloadImage("photo.${key.substringAfterLast('.', "jpg")}", bytes)
        }
    }
