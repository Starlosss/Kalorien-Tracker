package com.kalorientracker.app.data.db

import android.content.Context
import android.util.Log
import com.kalorientracker.app.data.repository.Gtin
import com.kalorientracker.app.domain.model.Food
import com.kalorientracker.app.domain.model.FoodSource
import com.kalorientracker.app.domain.model.Nutrients
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports the bundled German barcode table (`products.csv`, ~40.000 rows) into Room once, in the
 * background, so a barcode scan finds a match fully offline. Follows the same "seed once, flagged
 * in SharedPreferences" pattern as [FoodSeeder]; a chunked insert keeps memory bounded. Because the
 * flag is only written after the last chunk, an import interrupted midway is not resumed from a
 * byte offset: the next launch restarts and re-parses the whole file from row one. That is a full
 * restart, not a partial skip, and it stays cheap here (about 2 seconds for ~40.000 rows).
 */
@Singleton
class ProductCatalogImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foodDao: FoodDao,
) {
    private val mutex = Mutex()

    /**
     * Whether the catalogue is available locally (imported this run or in a previous one). A
     * barcode scan that arrives while this is still false must not be treated specially: the
     * normal online fallback in `FoodRepository.lookupBarcode` already covers that case.
     */
    @Volatile
    var isImported: Boolean = false
        private set

    suspend fun importIfNeeded() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getInt(KEY_VERSION, 0) >= CATALOG_VERSION) {
                isImported = true
                return@withLock
            }

            val startedAt = System.currentTimeMillis()
            var accepted = 0
            var rejected = 0
            val chunk = ArrayList<FoodEntity>(CHUNK_SIZE)

            context.assets.open(ASSET).bufferedReader().useLines { lines ->
                for (line in lines) {
                    val food = parseLine(line)
                    if (food == null) {
                        if (line.isNotBlank()) rejected++
                        continue
                    }
                    accepted++
                    chunk.add(food.toEntity(startedAt))
                    if (chunk.size >= CHUNK_SIZE) {
                        foodDao.insertAllIgnoring(chunk)
                        chunk.clear()
                    }
                }
            }
            if (chunk.isNotEmpty()) {
                foodDao.insertAllIgnoring(chunk)
            }

            prefs.edit().putInt(KEY_VERSION, CATALOG_VERSION).apply()
            isImported = true

            val durationMs = System.currentTimeMillis() - startedAt
            Log.i(TAG, "Katalogimport abgeschlossen: $accepted übernommen, $rejected verworfen, ${durationMs} ms")
        }
    }

    companion object {
        private const val TAG = "ProductCatalogImporter"
        private const val ASSET = "products.csv"
        private const val PREFS_NAME = "product_catalog"
        private const val KEY_VERSION = "imported_version"
        private const val CATALOG_VERSION = 1
        private const val CHUNK_SIZE = 2000
        private const val MIN_KCAL = 1.0
        private const val MAX_KCAL = 900.0
        private val BARCODE_LENGTHS = 8..14
        private const val FIELD_COUNT = 12

        /**
         * Parses one `products.csv` line. Pure and Android-free so it is unit-testable on its
         * own. Returns null for anything broken or implausible: wrong field count, a barcode that
         * is not 8 to 14 digits or fails the GTIN check digit, an empty name, kcal outside a
         * plausible 1..900 per-100g range, or an odd number of quote characters (an unterminated
         * quoted field, e.g. one whose embedded newline split it across two lines when the asset
         * is read line by line; letting that through could silently truncate a later field instead
         * of failing the row). Missing *secondary* nutrient fields (fibre, sugar, saturated fat,
         * salt) are treated as 0, not rejected; see the comment at their use below.
         */
        fun parseLine(line: String): Food? {
            if (line.isBlank()) return null
            if (line.count { it == '"' } % 2 != 0) return null
            val fields = splitCsvLine(line)
            if (fields.size != FIELD_COUNT) return null

            val barcode = fields[0].trim()
            if (barcode.length !in BARCODE_LENGTHS || !barcode.all(Char::isDigit)) return null
            if (!Gtin.isValid(barcode)) return null

            val name = fields[1].trim()
            if (name.isEmpty()) return null

            val kcal = fields[3].toDoubleOrNull() ?: return null
            if (kcal < MIN_KCAL || kcal > MAX_KCAL) return null

            return Food(
                name = name,
                brand = fields[2].trim().takeIf { it.isNotEmpty() },
                barcode = barcode,
                // kcal/protein/carbs/fat are guaranteed present by Task 1's data generation, so a
                // missing value there fails the row above. Fibre, sugar, saturated fat and salt are
                // not guaranteed: for many packaged products only the headline nutrients are known,
                // and an empty field here is coerced to 0.0, indistinguishable from a genuinely
                // measured zero (about 32% of rows have no fibre value, for example). We accept
                // that known simplification instead of making Nutrients' fields nullable, because
                // that type is shared by meal totals, the daily view, statistics, export and
                // backup; changing it here would touch all of those and need a Room migration for
                // one import source. The gap is disclosed to the user in SourceNote instead.
                per100g = Nutrients(
                    kcal = kcal,
                    protein = fields[4].toDoubleOrNull() ?: 0.0,
                    carbs = fields[5].toDoubleOrNull() ?: 0.0,
                    fat = fields[6].toDoubleOrNull() ?: 0.0,
                    fiber = fields[7].toDoubleOrNull() ?: 0.0,
                    sugar = fields[8].toDoubleOrNull() ?: 0.0,
                    saturatedFat = fields[9].toDoubleOrNull() ?: 0.0,
                    salt = fields[10].toDoubleOrNull() ?: 0.0,
                ),
                servingGrams = fields[11].toDoubleOrNull(),
                source = FoodSource.ONLINE_CACHED,
            )
        }

        /** Splits on commas, honouring double quotes around a field that itself contains one. */
        private fun splitCsvLine(line: String): List<String> {
            val fields = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                        current.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = !inQuotes
                    c == ',' && !inQuotes -> {
                        fields.add(current.toString())
                        current.setLength(0)
                    }
                    else -> current.append(c)
                }
                i++
            }
            fields.add(current.toString())
            return fields
        }
    }
}
