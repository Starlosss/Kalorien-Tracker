package com.kalorientracker.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.kalorientracker.app.data.db.ActivityEntity
import com.kalorientracker.app.data.db.AppDatabase
import com.kalorientracker.app.data.db.FoodEntity
import com.kalorientracker.app.data.db.FoodSeeder
import com.kalorientracker.app.data.db.GoalTargetsEntity
import com.kalorientracker.app.data.db.LearningCorrectionEntity
import com.kalorientracker.app.data.db.MealEntity
import com.kalorientracker.app.data.db.MealIngredientEntity
import com.kalorientracker.app.data.db.UserProfileEntity
import com.kalorientracker.app.data.db.WeightEntryEntity
import com.kalorientracker.app.data.db.WorkoutExerciseEntity
import com.kalorientracker.app.data.db.WorkoutLogEntity
import com.kalorientracker.app.data.db.WorkoutPlanEntity
import com.kalorientracker.app.data.db.joinPaths
import com.kalorientracker.app.data.db.splitPaths
import com.kalorientracker.app.data.image.PhotoStorage
import com.kalorientracker.app.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupSettings(
    val animationsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val onlineLookupEnabled: Boolean = true,
    val smoothCharts: Boolean = true,
    val showTargetWeightLine: Boolean = true,
)

/** Complete, unencrypted snapshot of all local data. Photos are stored next to it in ZIP backups. */
@Serializable
data class BackupData(
    val format: Int = FORMAT_VERSION,
    val exportedAt: Long,
    val foods: List<FoodEntity>,
    val meals: List<MealEntity>,
    val ingredients: List<MealIngredientEntity>,
    val weights: List<WeightEntryEntity>,
    val profile: UserProfileEntity? = null,
    val activities: List<ActivityEntity> = emptyList(),
    val targets: List<GoalTargetsEntity> = emptyList(),
    val workoutPlan: WorkoutPlanEntity? = null,
    val workoutExercises: List<WorkoutExerciseEntity> = emptyList(),
    val workoutLog: List<WorkoutLogEntity> = emptyList(),
    val corrections: List<LearningCorrectionEntity> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

data class BackupSummary(val meals: Int, val weights: Int, val photos: Int)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val photos: PhotoStorage,
    private val settings: SettingsRepository,
    private val seeder: FoodSeeder,
    private val json: Json,
) {
    /** Writes either a plain JSON export or a ZIP backup that also contains all meal photos. */
    suspend fun export(target: Uri, includePhotos: Boolean): BackupSummary = withContext(Dispatchers.IO) {
        val data = snapshot()
        val text = json.encodeToString(BackupData.serializer(), data)
        val photoFiles = if (includePhotos) photos.directory.listFiles().orEmpty().toList() else emptyList()
        val output = context.contentResolver.openOutputStream(target) ?: error("Datei kann nicht geschrieben werden")
        output.use { stream ->
            if (includePhotos) {
                ZipOutputStream(stream).use { zip ->
                    zip.putNextEntry(ZipEntry(DATA_ENTRY))
                    zip.write(text.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    for (file in photoFiles) {
                        zip.putNextEntry(ZipEntry("$PHOTO_PREFIX${file.name}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } else {
                stream.write(text.toByteArray(Charsets.UTF_8))
            }
        }
        BackupSummary(data.meals.size, data.weights.size, photoFiles.size)
    }

    /**
     * Restores a JSON export or ZIP backup. The file is fully parsed before anything local is
     * touched, so a broken file never leaves the app half-deleted.
     */
    suspend fun import(source: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "import_photos").apply { deleteRecursively(); mkdirs() }
        var text: String? = null
        val input = context.contentResolver.openInputStream(source) ?: error("Datei kann nicht gelesen werden")
        BufferedInputStream(input).use { stream ->
            stream.mark(4)
            val isZip = stream.read() == 'P'.code && stream.read() == 'K'.code
            stream.reset()
            if (isZip) {
                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == DATA_ENTRY -> text = zip.readBytes().toString(Charsets.UTF_8)
                            entry.name.startsWith(PHOTO_PREFIX) && !entry.isDirectory -> {
                                val name = File(entry.name).name
                                File(staging, name).outputStream().use { zip.copyTo(it) }
                            }
                        }
                        entry = zip.nextEntry
                    }
                }
            } else {
                text = stream.readBytes().toString(Charsets.UTF_8)
            }
        }
        val data = json.decodeFromString(BackupData.serializer(), text ?: error("Keine Daten in der Datei gefunden"))
        require(data.format <= BackupData.FORMAT_VERSION) { "Diese Sicherung stammt aus einer neueren App-Version." }

        val stagedPhotos = staging.listFiles().orEmpty()
        if (stagedPhotos.isNotEmpty()) {
            photos.deleteAll()
            stagedPhotos.forEach { it.copyTo(photos.resolve(it.name), overwrite = true) }
        }
        staging.deleteRecursively()

        val meals = data.meals.map { meal ->
            val restored = splitPaths(meal.photoPaths)
                .map { photos.resolve(it) }
                .filter { it.exists() }
                .map { it.absolutePath }
            meal.copy(photoPaths = joinPaths(restored))
        }

        db.clearAllTables()
        db.withTransaction {
            db.foodDao().insertAll(data.foods)
            db.mealDao().insertMeals(meals)
            db.mealDao().insertIngredients(data.ingredients)
            db.weightDao().insertAll(data.weights)
            data.profile?.let { db.profileDao().upsertProfile(it) }
            db.profileDao().insertActivities(data.activities)
            db.goalTargetsDao().insertAll(data.targets)
            data.workoutPlan?.let { db.workoutDao().upsertPlan(it) }
            db.workoutDao().insertExercises(data.workoutExercises)
            db.workoutDao().insertLogs(data.workoutLog)
            db.learningDao().insertAll(data.corrections)
        }
        seeder.seedIfNeeded()
        settings.restore(
            settings.current().copy(
                onboardingCompleted = data.profile != null && data.targets.isNotEmpty(),
                animationsEnabled = data.settings.animationsEnabled,
                hapticsEnabled = data.settings.hapticsEnabled,
                onlineLookupEnabled = data.settings.onlineLookupEnabled,
                smoothCharts = data.settings.smoothCharts,
                showTargetWeightLine = data.settings.showTargetWeightLine,
            ),
        )
        BackupSummary(meals.size, data.weights.size, stagedPhotos.size)
    }

    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        photos.deleteAll()
        settings.clear()
        seeder.seedIfNeeded()
    }

    suspend fun deleteAllPhotos() = withContext(Dispatchers.IO) {
        db.mealDao().clearAllPhotoPaths()
        photos.deleteAll()
    }

    private suspend fun snapshot(): BackupData {
        val s = settings.current()
        return BackupData(
            exportedAt = System.currentTimeMillis(),
            foods = db.foodDao().all(),
            meals = db.mealDao().allMeals(),
            ingredients = db.mealDao().allIngredients(),
            weights = db.weightDao().all(),
            profile = db.profileDao().getProfile(),
            activities = db.profileDao().getActivities(),
            targets = db.goalTargetsDao().all(),
            workoutPlan = db.workoutDao().getPlan(),
            workoutExercises = db.workoutDao().allExercises(),
            workoutLog = db.workoutDao().allLogs(),
            corrections = db.learningDao().all(),
            settings = BackupSettings(
                animationsEnabled = s.animationsEnabled,
                hapticsEnabled = s.hapticsEnabled,
                onlineLookupEnabled = s.onlineLookupEnabled,
                smoothCharts = s.smoothCharts,
                showTargetWeightLine = s.showTargetWeightLine,
            ),
        )
    }

    companion object {
        private const val DATA_ENTRY = "backup.json"
        private const val PHOTO_PREFIX = "photos/"
    }
}
