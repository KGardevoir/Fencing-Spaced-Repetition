/**
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Vendored into this repository from the Android Open Source Project; see
 * README.md in this directory for the exact source and revision.
 *
 * Modified from upstream: openDatabase() below chooses a VFS at runtime
 * instead of always using sqlite3.oo1.OpfsDb. Everything else -- in
 * particular the whole message protocol -- is upstream's, unchanged.
 */

import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

let sqlite3 = null;

// Maps to track active database connections and prepared statements by their unique IDs.
const databases = new Map(); // stores databaseId -> SQLiteDbObject
const statements = new Map(); // stores statementId -> SQLiteStatementObject

// Counters to generate unique IDs for new database connections and statements.
let nextDatabaseId = 0;
let nextStatementId = 0;

// The VFS the database files live in, decided once at startup by initSqlite()
// below. Null until then, and null for good if no persistent VFS installs.
let sahPoolUtil = null;

/**
 * Opens a database, on the best storage the browser actually gives us.
 *
 * Upstream uses sqlite3.oo1.OpfsDb unconditionally. That VFS needs
 * SharedArrayBuffer, which needs the page to be cross-origin isolated, which
 * needs COOP and COEP response headers -- headers a static host like GitHub
 * Pages or Codeberg Pages cannot set. Preferring the SAH-pool VFS keeps the
 * database working when this app is served as plain static files, which is
 * how a FOSS web app is usually deployed.
 *
 * The order is therefore:
 *
 *   1. OPFS via the SAH-pool VFS -- persistent, and needs no special headers.
 *   2. OPFS via the standard VFS -- persistent, needs cross-origin isolation.
 *      Only reachable when the SAH pool did not install: another tab holds
 *      the pool's exclusive lock, or the browser lacks
 *      createSyncAccessHandle.
 *   3. The in-memory default. NOT PERSISTENT: everything is lost when the tab
 *      closes. A private window with OPFS disabled lands here, and the app
 *      still runs rather than failing to start -- but the console says so.
 */
function openDatabase(fileName) {
    if (sahPoolUtil) {
        return new sahPoolUtil.OpfsSAHPoolDb(fileName);
    }
    if (sqlite3.oo1.OpfsDb) {
        return new sqlite3.oo1.OpfsDb(fileName);
    }
    console.warn(
        "sqlite-web-worker: no OPFS available, so '" + fileName + "' is " +
        "in memory only and will not survive this tab."
    );
    return new sqlite3.oo1.DB(fileName);
}

function openRequest(id, requestData) {
    try {
        const newDatabaseId = nextDatabaseId++;
        const newDatabase = openDatabase(requestData.fileName);
        databases.set(newDatabaseId, newDatabase);
        postMessage({'id': id, data: {'databaseId': newDatabaseId}});
    } catch (error) {
        postMessage({'id': id, error: error.message});
    }
}

function prepareRequest(id, requestData) {
    try {
        const newStatementId = nextStatementId++;
        const resultData = {
            'statementId': newStatementId,
            'parameterCount': 0,
            'columnNames': []
        };
        const database = databases.get(requestData.databaseId);
        if (!database) {
            postMessage({'id': id, error: "Invalid database ID: " + requestData.databaseId});
            return;
        }
        const statement = database.prepare(requestData.sql);
        statements.set(newStatementId, statement);
        resultData.parameterCount = sqlite3.capi.sqlite3_bind_parameter_count(statement);
        for (let i = 0; i < statement.columnCount; i++) {
            resultData.columnNames.push(sqlite3.capi.sqlite3_column_name(statement, i));
        }
        postMessage({'id': id, data: resultData});
    } catch (error) {
        postMessage({'id': id, error: error.message});
    }
}

function stepRequest(id, requestData) {
    const statement = statements.get(requestData.statementId);
    if (!statement) {
        postMessage({'id': id, error: "Invalid statement ID: " + requestData.statementId});
        return;
    }
    try {
        const resultData = {
            'rows': [],
            'columnTypes': []
        };
        statement.reset();
        statement.clearBindings();
        for (let i = 0; i < requestData.bindings.length; i++) {
            statement.bind(i + 1, requestData.bindings[i]);
        }
        while (statement.step()) {
            if (!resultData.columnTypes.length) {
                for (let i = 0; i < statement.columnCount; i++) {
                    resultData.columnTypes.push(sqlite3.capi.sqlite3_column_type(statement, i));
                }
            }
            resultData.rows.push(statement.get([]));
        }
        postMessage({'id': id, data: resultData});
    } catch (error) {
        postMessage({'id': id, error: error.message});
    }
}

function closeRequest(id, requestData) {
    if (requestData.statementId !== undefined && requestData.statementId != null) {
        const statement = statements.get(requestData.statementId);
        if (!statement) {
            postMessage({'id': id, error: "Invalid statement ID: " + requestData.statementId});
            return;
        }
        try {
            statement.finalize();
            statements.delete(requestData.statementId);
        } catch (error) {
            postMessage({'id': id, error: error.message});
        }
    }

    if (requestData.databaseId !== undefined && requestData.databaseId != null) {
        const database = databases.get(requestData.databaseId);
        if (!database) {
            postMessage({'id': id, error: "Invalid database ID: " + requestData.databaseId});
            return;
        }
        try {
            database.close();
            databases.delete(requestData.databaseId);
        } catch (error) {
            postMessage({'id': id, error: error.message});
        }
    }
}

// A map that links command names (strings) to their respective handler functions.
const commandMap = {
    'open': openRequest,
    'prepare': prepareRequest,
    'step': stepRequest,
    'close': closeRequest,
};

function handleMessage(e) {
    const requestMsg = e.data;
    if (!Object.hasOwn(requestMsg, 'data') && requestMsg.data == null) {
        postMessage(
            {'id': requestMsg.id, 'error': "Invalid request, missing 'data'."}
        );
        return;
    }
    if (!Object.hasOwn(requestMsg.data, 'cmd') && requestMsg.data.cmd == null) {
        postMessage(
            {'id': requestMsg.id, 'error': "Invalid request, missing 'cmd'."}
        );
        return;
    }
    const command = requestMsg.data.cmd;
    const requestHandler = commandMap[command];
    if (requestHandler) {
        requestHandler(requestMsg.id, requestMsg.data);
    } else {
        postMessage(
            {'id': requestMsg.id, 'error': "Invalid request, unknown command: '" + command + "'."}
        );
    }
}

const messageQueue = [];
onmessage = (e) => {
    if (!sqlite3) {
        messageQueue.push(e);
    } else {
        handleMessage(e);
    }
};

async function initSqlite() {
    const instance = await sqlite3InitModule();
    // Installing the SAH pool has to finish before the first open request is
    // served, which is why it happens here rather than lazily: sqlite3 is
    // only published to the rest of the worker once this has settled, and
    // until then messages queue up.
    if (instance.installOpfsSAHPoolVfs) {
        try {
            sahPoolUtil = await instance.installOpfsSAHPoolVfs({
                name: 'fencing-spaced-repetition'
            });
        } catch (error) {
            console.warn(
                'sqlite-web-worker: the OPFS SAH-pool VFS did not install, ' +
                'falling back. ' + error
            );
        }
    }
    sqlite3 = instance;
}

initSqlite().then(() => {
    while (messageQueue.length > 0) {
        handleMessage(messageQueue.shift());
    }
});
