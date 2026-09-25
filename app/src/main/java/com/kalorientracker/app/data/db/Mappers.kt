package com.kalorientracker.app.data.db

import com.kalorientracker.app.domain.model.Confidence
import com.kalorientracker.app.domain.model.DailyTotals
import com.kalorientracker.app.domain.model.Difficulty
import com.kalorientracker.app.domain.model.Food
import com.kalorientracker.app.domain.model.FoodSource
import com.kalorientracker.app.domain.model.Goal
import com.kalorientracker.app.domain.model.GoalTargets
import com.kalorientracker.app.domain.model.Ingredient
import com.kalorientracker.app.domain.model.LearningCorrection
import com.kalorientracker.app.domain.model.Meal
import com.kalorientracker.app.domain.model.MealCategory
import com.kalorientracker.app.domain.model.Nutrients
import com.kalorientracker.app.domain.model.Sex
import com.kalorientracker.app.domain.model.SportActivity
import com.kalorientracker.app.domain.model.UserProfile
import com.kalorientracker.app.domain.model.WeightEntry
import com.kalorientracker.app.domain.model.WorkoutExercise
import com.kalorientracker.app.domain.model.WorkoutPlan

private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
    value?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: fallback

fun FoodEntity.toDomain() = Food(
    id = id,
    name = name,
    brand = brand,
    barcode = barcode,
    per100g = Nutrients(kcal, protein, carbs, fat, fiber, sugar, saturatedFat, salt),
    servingGrams = servingGrams,
    source = enumOr(source, FoodSource.USER_ADDED),
)

fun Food.toEntity(createdAt: Long) = FoodEntity(
    id = id,
    name = name,
    brand = brand,
    barcode = barcode?.takeIf { it.isNotBlank() },
    kcal = per100g.kcal,
    protein = per100g.protein,
    carbs = per100g.carbs,
    fat = per100g.fat,
    fiber = per100g.fiber,
    sugar = per100g.sugar,
    saturatedFat = per100g.saturatedFat,
    salt = per100g.salt,
    servingGrams = servingGrams,
    source = source.name,
    createdAt = createdAt,
)

fun MealIngredientEntity.toDomain() = Ingredient(
    id = id,
    foodId = foodId,
    name = name,
    grams = grams,
    per100g = Nutrients(kcal100, protein100, carbs100, fat100, fiber100, sugar100, saturatedFat100, salt100),
    estimatedGrams = estimatedGrams,
    confidence = confidence?.let { enumOr(it, Confidence.MEDIUM) },
)

fun Ingredient.toEntity(mealId: Long, position: Int) = MealIngredientEntity(
    mealId = mealId,
    foodId = foodId,
    position = position,
    name = name,
    grams = grams,
    estimatedGrams = estimatedGrams,
    confidence = confidence?.name,
    kcal100 = per100g.kcal,
    protein100 = per100g.protein,
    carbs100 = per100g.carbs,
    fat100 = per100g.fat,
    fiber100 = per100g.fiber,
    sugar100 = per100g.sugar,
    saturatedFat100 = per100g.saturatedFat,
    salt100 = per100g.salt,
)

fun joinPaths(paths: List<String>): String = paths.joinToString("\n")

fun splitPaths(paths: String): List<String> = paths.split('\n').filter { it.isNotBlank() }

fun MealWithIngredients.toDomain() = Meal(
    id = meal.id,
    name = meal.name,
    loggedAtMillis = meal.loggedAt,
    epochDay = meal.epochDay,
    category = enumOr(meal.category, MealCategory.SNACK),
    photoPaths = splitPaths(meal.photoPaths),
    description = meal.description,
    ingredients = ingredients.sortedBy { it.position }.map { it.toDomain() },
)

fun Meal.toEntity() = MealEntity(
    id = id,
    name = name,
    loggedAt = loggedAtMillis,
    epochDay = epochDay,
    category = category.name,
    photoPaths = joinPaths(photoPaths),
    description = description,
)

fun DailyTotalsRow.toDomain() = DailyTotals(
    epochDay = epochDay,
    nutrients = Nutrients(kcal, protein, carbs, fat, fiber, sugar, saturatedFat, salt),
    mealCount = mealCount,
)

fun WeightEntryEntity.toDomain() = WeightEntry(epochDay, weightKg)

fun UserProfileEntity.toDomain(activities: List<ActivityEntity>) = UserProfile(
    age = age,
    heightCm = heightCm,
    weightKg = weightKg,
    targetWeightKg = targetWeightKg,
    sex = enumOr(sex, Sex.MALE),
    goal = enumOr(goal, Goal.MAINTAIN),
    dailySteps = dailySteps,
    activities = activities.map { SportActivity(it.name, it.sessionsPerWeek, it.minutesPerSession) },
)

fun UserProfile.toEntity() = UserProfileEntity(
    age = age,
    heightCm = heightCm,
    weightKg = weightKg,
    targetWeightKg = targetWeightKg,
    sex = sex.name,
    goal = goal.name,
    dailySteps = dailySteps,
)

fun UserProfile.activityEntities() = activities.map {
    ActivityEntity(name = it.name, sessionsPerWeek = it.sessionsPerWeek, minutesPerSession = it.minutesPerSession)
}

fun GoalTargetsEntity.toDomain() = GoalTargets(
    effectiveFromEpochDay = effectiveFrom,
    maintenanceKcal = maintenanceKcal,
    targetKcal = targetKcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    fiberG = fiberG,
    sugarMaxG = sugarMaxG,
    saturatedFatMaxG = saturatedFatMaxG,
    saltMaxG = saltMaxG,
    isUserAdjusted = isUserAdjusted,
)

fun GoalTargets.toEntity(createdAt: Long) = GoalTargetsEntity(
    effectiveFrom = effectiveFromEpochDay,
    maintenanceKcal = maintenanceKcal,
    targetKcal = targetKcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    fiberG = fiberG,
    sugarMaxG = sugarMaxG,
    saturatedFatMaxG = saturatedFatMaxG,
    saltMaxG = saltMaxG,
    isUserAdjusted = isUserAdjusted,
    createdAt = createdAt,
)

fun toWorkoutPlan(plan: WorkoutPlanEntity, exercises: List<WorkoutExerciseEntity>) = WorkoutPlan(
    frequencyPerWeek = plan.frequencyPerWeek,
    minutesPerSession = plan.minutesPerSession,
    difficulty = enumOr(plan.difficulty, Difficulty.EASY),
    exercises = exercises.sortedBy { it.position }.map { WorkoutExercise(it.name, it.sets, it.reps, it.durationSeconds) },
)

fun LearningCorrectionEntity.toDomain() = LearningCorrection(foodKey, originalGrams, correctedGrams, createdAt)
