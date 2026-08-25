// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.web

import com.fencing.spacedrepetition.ui.image.ImagePicker
import com.fencing.spacedrepetition.ui.image.PickedImage
import com.fencing.spacedrepetition.util.ImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlin.js.Promise

/**
 * The browser half of [ImagePicker]: a hidden file input.
 *
 * Compose draws into a canvas and has no file dialog of its own, so this is
 * one of the few places the page reaches for a DOM element -- and the only
 * way to open a file chooser without one is not to open it at all. The input
 * is created per pick and discarded: a reused one keeps its previous value and
 * would not fire a change event when the same file is chosen twice in a row.
 *
 * A cancelled dialog never resolves anything, matching Android, where the
 * result callback simply carries no URI.
 */
fun browserImagePicker(scope: CoroutineScope, store: ImageStore): ImagePicker =
    ImagePicker { onPicked ->
        scope.launch {
            val picked = pickImageFile().await<JsAny?>() ?: return@launch
            val bytes = picked.bytes()
            val key = store.write(bytes, picked.extension())
            onPicked(PickedImage(key, bytes.size))
        }
    }

private fun JsAny.bytes(): ByteArray {
    val length = dataLength(this)
    val out = ByteArray(length)
    for (i in 0 until length) out[i] = dataByte(this, i).toByte()
    return out
}

private fun JsAny.extension(): String {
    val type = mimeType(this)
    val extension = type.substringAfterLast('/', "")
    return if (extension.isBlank()) "jpg" else extension
}

private fun dataLength(picked: JsAny): Int = js("picked.data.length")
private fun dataByte(picked: JsAny, index: Int): Int = js("picked.data[index]")
private fun mimeType(picked: JsAny): String = js("picked.type || ''")

/**
 * Resolves with { data, type } once a file is chosen, or null if the dialog
 * is dismissed.
 *
 * Cancellation has no event of its own in any browser this targets, so the
 * promise that resolves null is settled by the window regaining focus with no
 * file selected. Without that the coroutine would wait forever on a dialog the
 * user closed, and the caller would never learn to stop showing a spinner.
 */
private fun pickImageFile(): Promise<JsAny?> = js(
    """
    new Promise(function (resolve) {
        var input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.style.display = 'none';
        document.body.appendChild(input);

        var settled = false;
        function finish(value) {
            if (settled) return;
            settled = true;
            window.removeEventListener('focus', onFocus);
            input.remove();
            resolve(value);
        }

        input.addEventListener('change', function () {
            var file = input.files && input.files[0];
            if (!file) { finish(null); return; }
            file.arrayBuffer().then(function (buffer) {
                finish({ data: new Uint8Array(buffer), type: file.type });
            }).catch(function () { finish(null); });
        });

        function onFocus() {
            // The change event, when there is one, arrives after focus
            // returns. This gives it that chance before calling it a cancel.
            setTimeout(function () {
                if (!input.files || input.files.length === 0) finish(null);
            }, 500);
        }
        window.addEventListener('focus', onFocus);

        input.click();
    })
    """
)
