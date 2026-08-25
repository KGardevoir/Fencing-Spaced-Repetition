# sqlite-web-worker

The Web Worker that gives the browser build a real database.

`androidx.sqlite:sqlite-web` ships `WebWorkerSQLiteDriver`, a `SQLiteDriver`
that does no SQLite work itself: it posts messages to a Web Worker and waits
for replies. The artifact deliberately ships no worker — androidx's own
release notes say "there is no default worker implementation with the
artifact, one can be provided via the constructor". This directory is that
worker.

## Where it came from

| | |
|---|---|
| Upstream | The Android Open Source Project (androidx) |
| Path | `sqlite/sqlite-web-worker-test/web-worker/worker.js` |
| Mirror fetched from | `https://raw.githubusercontent.com/androidx/androidx/androidx-main/sqlite/sqlite-web-worker-test/web-worker/worker.js` |
| Fetched | 2026-08-25 |
| Licence | Apache-2.0 (`LICENSE` in this directory) |

Upstream uses this worker to run the driver's own conformance tests, which is
the reason to prefer it over a reimplementation: it is the implementation the
protocol is tested against, by the people who define the protocol.

The protocol itself *is* documented, in the KDoc on `WebWorkerSQLiteDriver` —
four commands (`open`, `prepare`, `step`, `close`) inside an envelope carrying
a correlation `id` and either `data` or `error`. Vendoring is not a hedge
against secret behaviour; it is so that the two halves of a versioned protocol
come from one revision instead of two, and so that a driver upgrade cannot
silently outrun the worker.

## What was changed

One thing: `openDatabase()` picks a VFS at runtime rather than always using
`sqlite3.oo1.OpfsDb`. The message protocol is untouched.

Upstream's choice needs `SharedArrayBuffer`, which needs the page to be
cross-origin isolated, which needs `Cross-Origin-Opener-Policy` and
`Cross-Origin-Embedder-Policy` response headers. A static host — GitHub Pages,
Codeberg Pages — cannot set those, and static hosting is how a FOSS web app
usually reaches people. The SAH-pool VFS is equally persistent and needs no
headers, so it is tried first, with upstream's VFS next and a non-persistent
in-memory database as the last resort.

## SQLite itself

`@sqlite.org/sqlite-wasm`, pinned in `package.json`. SQLite is in the public
domain; the npm packaging around it is Apache-2.0. Neither constrains this
repository's GPL-3.0-or-later licence.

## Storage caveats worth knowing

OPFS is per-origin and browsers evict it under storage pressure.
`navigator.storage.persist()` has to be requested, and iOS only honours it for
web apps added to the home screen. That is the difference between a review
history that survives and one that quietly disappears.
