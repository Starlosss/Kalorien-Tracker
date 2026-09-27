package com.kalorientracker.app.data.db

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the wipe-during-import interleaving deterministically. The real window in the app is
 * about 1,5 seconds wide and reached by deleting everything and then restoring a backup from the
 * onboarding screen, which is not something a UI walk can hit reliably, so the proof lives here
 * instead: the coordinator is the whole of the ordering, and it holds no Android types.
 *
 * `runTest` runs everything on one scheduler, so `runCurrent()` drains the queue and every step
 * below happens in the stated order rather than by luck.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogImportCoordinatorTest {

    private companion object {
        const val VERSION = 2
        const val TOTAL_ROWS = 39_960
        const val FIRST_CHUNK = 8_000
    }

    /** The regression: an import overtaken by a wipe must not leave a half-filled catalogue marked done. */
    @Test
    fun `a wipe during an import abandons it and the next import fills the catalogue`() = runTest {
        val coordinator = CatalogImportCoordinator()
        var mark = 0
        var rows = 0

        val firstChunkWritten = CompletableDeferred<Unit>()
        val wipeRequested = CompletableDeferred<Unit>()
        var firstOutcome: CatalogImportCoordinator.Outcome? = null

        // Import A: writes one chunk, then is held at the point where the real importer would
        // check whether it is still wanted before writing the next one.
        val a = launch {
            firstOutcome = coordinator.importOnce(
                alreadyDone = { mark >= VERSION },
                markDone = { mark = VERSION },
            ) { stillWanted ->
                rows += FIRST_CHUNK
                firstChunkWritten.complete(Unit)
                wipeRequested.await()
                if (!stillWanted()) return@importOnce
                rows += TOTAL_ROWS - FIRST_CHUNK
            }
        }
        runCurrent()
        assertTrue("import A should be running", firstChunkWritten.isCompleted)
        assertEquals(FIRST_CHUNK, rows)

        // The wipe: clearAllTables has just emptied the table under A's feet.
        rows = 0
        var secondOutcome: CatalogImportCoordinator.Outcome? = null
        val wipe = launch {
            coordinator.invalidate { mark = 0 }
            secondOutcome = coordinator.importOnce(
                alreadyDone = { mark >= VERSION },
                markDone = { mark = VERSION },
            ) { rows = TOTAL_ROWS }
        }
        runCurrent()

        // invalidate() has raised the generation and is now parked on the lock A still holds.
        assertEquals("the wipe must not overtake A's lock", 0, rows)

        wipeRequested.complete(Unit)
        runCurrent()
        a.join()
        wipe.join()

        assertEquals(CatalogImportCoordinator.Outcome.ABANDONED, firstOutcome)
        assertEquals(CatalogImportCoordinator.Outcome.IMPORTED, secondOutcome)
        assertEquals("the catalogue must be complete, not half filled", TOTAL_ROWS, rows)
        assertEquals("only the import that finished may set the mark", VERSION, mark)
    }

    /**
     * The narrower half of the same race: A passes its last `stillWanted` check and is about to
     * write the mark when the wipe lands. Clearing the mark under the lock is what stops A from
     * writing it back afterwards.
     */
    @Test
    fun `a wipe cannot be overwritten by a mark from the import it interrupted`() = runTest {
        val coordinator = CatalogImportCoordinator()
        var mark = 0
        val lastChunkWritten = CompletableDeferred<Unit>()
        val wipeRequested = CompletableDeferred<Unit>()

        val a = launch {
            coordinator.importOnce(
                alreadyDone = { mark >= VERSION },
                markDone = { mark = VERSION },
            ) {
                // Never consults stillWanted again after this point.
                lastChunkWritten.complete(Unit)
                wipeRequested.await()
            }
        }
        runCurrent()
        assertTrue(lastChunkWritten.isCompleted)

        val wipe = launch { coordinator.invalidate { mark = 0 } }
        runCurrent()

        wipeRequested.complete(Unit)
        runCurrent()
        a.join()
        wipe.join()

        assertEquals("the wipe must win, the mark stays clear", 0, mark)
    }

    /**
     * The wipe empties the table and only then waits for the lock, which a running import can
     * hold for about a chunk. If the process dies in that wait, whatever the mark says at that
     * moment is what the next launch believes. It has to already say "not imported", or the
     * emptied catalogue would be treated as complete for good.
     */
    @Test
    fun `the mark is already clear while the wipe is still waiting for the lock`() = runTest {
        val coordinator = CatalogImportCoordinator()
        var mark = VERSION
        val importRunning = CompletableDeferred<Unit>()
        val releaseImport = CompletableDeferred<Unit>()

        // An import that has the lock and will not give it up until told to.
        val a = launch {
            coordinator.importOnce(
                alreadyDone = { false },
                markDone = { mark = VERSION },
            ) {
                importRunning.complete(Unit)
                releaseImport.await()
            }
        }
        runCurrent()
        assertTrue(importRunning.isCompleted)

        val wipe = launch { coordinator.invalidate { mark = 0 } }
        runCurrent()

        // This is the crash window: the wipe is parked on the lock and has changed nothing else.
        assertEquals("a process death here must not leave a full catalogue claimed", 0, mark)

        releaseImport.complete(Unit)
        runCurrent()
        a.join()
        wipe.join()
        assertEquals("and the mark still ends up clear", 0, mark)
    }

    @Test
    fun `an import is skipped when the mark is already set`() = runTest {
        val coordinator = CatalogImportCoordinator()
        var mark = VERSION
        var ran = false

        val outcome = coordinator.importOnce(
            alreadyDone = { mark >= VERSION },
            markDone = { mark = VERSION },
        ) { ran = true }

        assertEquals(CatalogImportCoordinator.Outcome.ALREADY_DONE, outcome)
        assertTrue("the asset must not be read again", !ran)
    }

    @Test
    fun `an undisturbed import marks itself done`() = runTest {
        val coordinator = CatalogImportCoordinator()
        var mark = 0
        var rows = 0

        val outcome = coordinator.importOnce(
            alreadyDone = { mark >= VERSION },
            markDone = { mark = VERSION },
        ) { stillWanted ->
            repeat(20) {
                if (!stillWanted()) return@importOnce
                rows += CHUNK
            }
        }

        assertEquals(CatalogImportCoordinator.Outcome.IMPORTED, outcome)
        assertEquals(20 * CHUNK, rows)
        assertEquals(VERSION, mark)
    }
}

private const val CHUNK = 2_000
