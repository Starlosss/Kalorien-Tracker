package com.kalorientracker.app.data.db

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sequences catalogue imports against the wipes that invalidate them. Holds no Android types, so
 * the ordering it guarantees can be driven deterministically in a unit test instead of by trying
 * to hit a one-and-a-half second window in the UI.
 *
 * The problem it solves: the importer inserts about 39.960 rows in chunks and only afterwards
 * writes an "already imported" mark that lives outside the database. A wipe that lands in the
 * middle deletes the rows already inserted. Without coordination the interrupted import would
 * carry on, finish its remaining chunks and write the mark, and the fresh import that the wipe
 * started would find the mark set and return at once, leaving a permanently half-filled catalogue
 * that claims to be complete. That is worse than no catalogue, because nothing ever retries it.
 *
 * Two rules close it:
 *
 * 1. [invalidate] raises a generation counter before it does anything else. An import checks the
 *    generation between chunks through the `stillWanted` probe it is handed, and abandons as soon
 *    as it is stale. It then returns [Outcome.ABANDONED] without writing the mark.
 * 2. [invalidate] clears the mark while holding the same lock the import runs under. That makes
 *    "check the generation, then write the mark" atomic with respect to the wipe: an import that
 *    has already passed its last check cannot write the mark after the wipe cleared it, because
 *    the wipe cannot get the lock until that import has released it.
 */
class CatalogImportCoordinator {

    private val mutex = Mutex()
    private val generation = AtomicInteger(0)

    enum class Outcome {
        /** The mark was already set, so nothing was read or inserted. */
        ALREADY_DONE,

        /** The import ran to the end and [markDone] was called. */
        IMPORTED,

        /** A wipe overtook the import; nothing was marked and a fresh import is already queued. */
        ABANDONED,
    }

    /**
     * Runs [import] once, under the lock, unless [alreadyDone] reports the mark is set.
     *
     * [import] is handed a `stillWanted` probe. It must call it between chunks and return early
     * when it reports false; the coordinator cannot interrupt it on its own. [markDone] runs only
     * when the import reached the end without a wipe overtaking it.
     */
    suspend fun importOnce(
        alreadyDone: () -> Boolean,
        markDone: () -> Unit,
        import: suspend (stillWanted: () -> Boolean) -> Unit,
    ): Outcome = mutex.withLock {
        val mine = generation.get()
        if (alreadyDone()) return@withLock Outcome.ALREADY_DONE

        import { generation.get() == mine }

        // Checked inside the lock, so a wipe waiting on the lock cannot slip between this test
        // and the write below.
        if (generation.get() != mine) return@withLock Outcome.ABANDONED
        markDone()
        Outcome.IMPORTED
    }

    /**
     * Tells a running import to stop, waits until it actually has, and then runs [clearMark].
     * Once this returns, no import that was already running can set the mark any more.
     *
     * [clearMark] is called twice on purpose. The wait for the lock can last as long as a chunk
     * takes, about one and a half seconds, and the caller has just emptied the table. If the
     * process dies inside that wait, a mark cleared only afterwards would still be set over an
     * empty catalogue and no later launch would ever refill it. Clearing once up front means the
     * worst a death in the window can leave behind is a cleared mark, which costs one needless
     * re-import. The second call is still the authoritative one: it is the one an import racing
     * to write the mark cannot get in front of.
     */
    suspend fun invalidate(clearMark: () -> Unit) {
        clearMark()
        generation.incrementAndGet()
        mutex.withLock { clearMark() }
    }
}
