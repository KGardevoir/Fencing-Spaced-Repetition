// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui

// The four things a browser has to be asked in JavaScript before a deck can
// be moved in or out of it: open a chooser, learn a file's name, read it as
// text, and hand one back to the user.
//
// Compose draws into a canvas and has no file dialog of its own, so this is
// the same corner BrowserImagePicker lives in -- the page reaching for a DOM
// element because there is no other way to open a file chooser at all.
//
// Compression is here rather than in shared code because it is the browser's
// own: CompressionStream and DecompressionStream are built into the platform,
// the way java.util.zip is on Android, so neither side carries a deflate
// implementation and the two agree by both using gzip.

import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * Opens a file chooser, returning a promise of the chosen file -- or of null,
 * if the dialog was dismissed.
 *
 * A promise rather than a suspending call, because the dialog has to open in
 * the click that asked for it: a browser withdraws that permission from code
 * resumed on a later turn of the event loop, which is where a coroutine would
 * be. Callers open it now and wait for it in a coroutine.
 *
 * [accept] is a hint, not a guard -- pass an empty string for no hint at all,
 * which is what an archive import does. An export of this app's is a
 * `.tsv.gz`, a type no two systems name the same way, and the Android picker
 * accepts every type for the same reason.
 *
 * A fresh input per pick, and a cancel detected by the window regaining focus
 * with nothing selected -- both for the reasons BrowserImagePicker gives: a
 * reused input does not fire a change event for the same file twice, and no
 * browser this targets has a cancel event at all.
 */
internal fun openFileDialog(accept: String): Promise<JsAny?> = js(
    """
    new Promise(function (resolve) {
        var input = document.createElement('input');
        input.type = 'file';
        if (accept) input.accept = accept;
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
            finish((input.files && input.files[0]) || null);
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

/** The chosen file's name, which a CSV import derives a group name from. */
internal fun fileName(file: JsAny): String = js("file.name || ''")

/**
 * A file's text, decompressed if it is gzipped, or null if it could not be
 * read.
 *
 * Which it is comes from the first two bytes rather than from the name or the
 * type the chooser reported: an export written by this app is gzipped, one
 * edited by hand or produced by another tool usually is not, and both are
 * offered as the same kind of file.
 */
internal suspend fun blobText(blob: JsAny): String? =
    readBlobText(blob).await<JsString?>()?.toString()

/**
 * Hands [text] to the user as a download named [name], gzipped when
 * [compress] is set.
 *
 * Returns null when the download started, or the failure's message.
 *
 * "Started" is as much as a page can know: what happens after the click --
 * where it is saved, whether it is saved at all -- belongs to the browser,
 * and there is no event back. So an export reports what it wrote, which is
 * the part this side can actually vouch for.
 */
internal suspend fun downloadText(
    name: String,
    text: String,
    mime: String,
    compress: Boolean
): String? = saveAsDownload(name, text, mime, compress).await<JsString?>()?.toString()

private fun readBlobText(blob: JsAny): Promise<JsString?> = js(
    """
    blob.arrayBuffer().then(function (buffer) {
        var bytes = new Uint8Array(buffer);
        if (bytes.length > 1 && bytes[0] === 0x1f && bytes[1] === 0x8b) {
            var stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip'));
            return new Response(stream).text();
        }
        return new Blob([bytes]).text();
    }).catch(function () { return null; })
    """
)

private fun saveAsDownload(
    name: String,
    text: String,
    mime: String,
    compress: Boolean
): Promise<JsString?> = js(
    """
    (function () {
        var blob = new Blob([text], { type: mime });
        var ready = compress
            ? new Response(blob.stream().pipeThrough(new CompressionStream('gzip'))).blob()
            : Promise.resolve(blob);
        return ready.then(function (saved) {
            var url = URL.createObjectURL(saved);
            var link = document.createElement('a');
            link.href = url;
            link.download = name;
            link.style.display = 'none';
            document.body.appendChild(link);
            link.click();
            link.remove();
            // Long after the browser has taken the blob: revoking it while
            // the download is still starting cancels the download.
            setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
            return null;
        }).catch(function (error) {
            return String((error && error.message) || error);
        });
    })()
    """
)
