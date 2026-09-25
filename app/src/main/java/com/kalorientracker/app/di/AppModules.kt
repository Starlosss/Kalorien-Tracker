package com.kalorientracker.app.di

import android.content.Context
import androidx.room.Room
import com.kalorientracker.app.data.analyzer.FoodAnalyzer
import com.kalorientracker.app.data.analyzer.StubFoodAnalyzer
import com.kalorientracker.app.data.db.AppDatabase
import com.kalorientracker.app.data.db.FoodDao
import com.kalorientracker.app.data.db.GoalTargetsDao
import com.kalorientracker.app.data.db.LearningDao
import com.kalorientracker.app.data.db.MealDao
import com.kalorientracker.app.data.db.ProfileDao
import com.kalorientracker.app.data.db.WeightDao
import com.kalorientracker.app.data.db.WorkoutDao
import com.kalorientracker.app.data.remote.OnlineProductSource
import com.kalorientracker.app.data.remote.OpenFoodFactsSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides fun foodDao(db: AppDatabase): FoodDao = db.foodDao()
    @Provides fun mealDao(db: AppDatabase): MealDao = db.mealDao()
    @Provides fun weightDao(db: AppDatabase): WeightDao = db.weightDao()
    @Provides fun profileDao(db: AppDatabase): ProfileDao = db.profileDao()
    @Provides fun goalTargetsDao(db: AppDatabase): GoalTargetsDao = db.goalTargetsDao()
    @Provides fun workoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
    @Provides fun learningDao(db: AppDatabase): LearningDao = db.learningDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun onlineProductSource(impl: OpenFoodFactsSource): OnlineProductSource
}

/** Swap the vision model here. Everything else only depends on [FoodAnalyzer]. */
@Module
@InstallIn(SingletonComponent::class)
object AnalyzerModule {
    @Provides
    @Singleton
    fun foodAnalyzer(): FoodAnalyzer = StubFoodAnalyzer()
}
