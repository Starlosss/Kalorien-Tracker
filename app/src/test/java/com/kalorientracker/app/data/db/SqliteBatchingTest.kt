package com.kalorientracker.app.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The regression this guards: `relabelAsCatalog` was called once per import chunk with 2.000
 * barcodes. Room expands `IN (:barcodes)` to one bound variable each, and SQLite below 3.32 caps
 * a statement at 999 of them. That is the system SQLite on API 26 to 30, so the catalogue import
 * threw "too many SQL variables" on Android 8 to 11 while working on a current phone and in the
 * emulator, which is why no walk-through found it.
 *
 * The tests below drive the real production code: the batching lives in the DAO itself, and the
 * fake overrides only the statement it splits into.
 */
class SqliteBatchingTest {

    @Test
    fun `a chunk never binds more variables than the oldest supported SQLite allows`() = runTest {
        val dao = RecordingFoodDao()

        dao.relabelAsCatalog((1..2_000).map { "code$it" })

        assertTrue("nothing was relabelled", dao.chunks.isNotEmpty())
        val biggest = dao.chunks.maxOf { it.size }
        assertTrue(
            "one statement bound $biggest variables, the cap on API 26 to 30 is $SQLITE_MAX_BIND_ARGS",
            biggest <= SQLITE_MAX_BIND_ARGS,
        )
    }

    @Test
    fun `every barcode is relabelled exactly once and in order`() = runTest {
        val dao = RecordingFoodDao()
        val barcodes = (1..2_000).map { "code$it" }

        dao.relabelAsCatalog(barcodes)

        assertEquals(barcodes, dao.chunks.flatten())
    }

    @Test
    fun `an empty list runs no statement at all`() = runTest {
        val dao = RecordingFoodDao()

        dao.relabelAsCatalog(emptyList())

        assertEquals(emptyList<List<String>>(), dao.chunks)
    }

    @Test
    fun `a list that fits stays a single statement`() = runTest {
        val dao = RecordingFoodDao()
        val barcodes = (1..SQLITE_BIND_BATCH).map { "code$it" }

        dao.relabelAsCatalog(barcodes)

        assertEquals(1, dao.chunks.size)
    }

    @Test
    fun `the helper passes each slice on without copying the list`() = runTest {
        val seen = mutableListOf<List<Int>>()

        forEachBindBatch((1..10).toList(), batchSize = 4) { seen.add(it.toList()) }

        assertEquals(listOf(listOf(1, 2, 3, 4), listOf(5, 6, 7, 8), listOf(9, 10)), seen)
    }

    /** A batch size at or over the cap is a programming error, not something to paper over. */
    @Test(expected = IllegalArgumentException::class)
    fun `a batch size above the cap is refused`() = runTest {
        forEachBindBatch(listOf(1), batchSize = SQLITE_MAX_BIND_ARGS + 1) { }
    }
}

/** Records what each statement would have bound; everything else is unused here. */
private class RecordingFoodDao : FoodDao {
    val chunks = mutableListOf<List<String>>()

    override suspend fun relabelChunk(barcodes: List<String>) {
        chunks.add(barcodes.toList())
    }

    override suspend fun search(query: String, limit: Int): List<FoodEntity> = emptyList()
    override suspend fun byBarcode(barcode: String): FoodEntity? = null
    override suspend fun byId(id: Long): FoodEntity? = null
    override suspend fun byName(name: String): FoodEntity? = null
    override suspend fun insert(food: FoodEntity): Long = 0
    override suspend fun insertAll(foods: List<FoodEntity>) = Unit
    override suspend fun insertAllIgnoring(foods: List<FoodEntity>) = Unit
    override suspend fun countBySource(source: String): Int = 0
    override fun observeFrequent(limit: Int): Flow<List<FoodEntity>> = flowOf(emptyList())
    override suspend fun all(): List<FoodEntity> = emptyList()
    override suspend fun allUserFoods(): List<FoodEntity> = emptyList()
}
