// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Save image from URI to app's internal storage under the given subdirectory.
 * Returns the saved file path or null if failed.
 */
fun saveImageToInternalStorage(context: Context, uri: Uri, subDir: String = "card_images"): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null

        val imagesDir = File(context.filesDir, subDir)
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        val timestamp = Time.now()
        val extension = context.contentResolver.getType(uri)?.split("/")?.lastOrNull() ?: "jpg"
        val fileName = "${subDir}_${timestamp}.${extension}"
        val outputFile = File(imagesDir, fileName)

        FileOutputStream(outputFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        inputStream.close()

        outputFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
