package com.kalorientracker.app.data.db

/**
 * SQLite before 3.32 caps a single statement at 999 bound variables (`SQLITE_MAX_VARIABLE_NUMBER`).
 * Android ships the system SQLite and this app binds against it, so that cap is live on API 26 to
 * 30, which is Android 8 to 11 and inside our `minSdk = 26`. Room expands a `WHERE x IN (:list)`
 * to one placeholder per element, so a query handed more than 999 values throws
 * "too many SQL variables" there while working fine on a current device.
 */
const val SQLITE_MAX_BIND_ARGS = 999

/** Comfortably under [SQLITE_MAX_BIND_ARGS], leaving room for a statement's other parameters. */
const val SQLITE_BIND_BATCH = 900

/**
 * Runs [action] over [values] in slices small enough to bind in one statement everywhere we
 * support. Calls [action] once per slice, in order, and not at all for an empty list.
 */
suspend fun <T> forEachBindBatch(
    values: List<T>,
    batchSize: Int = SQLITE_BIND_BATCH,
    action: suspend (List<T>) -> Unit,
) {
    require(batchSize in 1..SQLITE_MAX_BIND_ARGS) {
        "batchSize must be between 1 and $SQLITE_MAX_BIND_ARGS, was $batchSize"
    }
    var start = 0
    while (start < values.size) {
        action(values.subList(start, minOf(start + batchSize, values.size)))
        start += batchSize
    }
}
