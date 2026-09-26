package com.kalorientracker.app.domain.model

enum class Goal(val label: String, val description: String) {
    LOSE("Abnehmen", "Körperfett reduzieren mit moderatem Defizit"),
    MAINTAIN("Gewicht halten", "Aktuelles Gewicht stabil halten"),
    GAIN("Zunehmen", "Gewicht mit leichtem Überschuss aufbauen"),
    MUSCLE_BUILD("Muskelaufbau", "Ohne Fitnessstudio, mit Alltag, Sport und auf Wunsch einem Home-Workout"),
}

enum class Sex(val label: String) {
    MALE("Männlich"),
    FEMALE("Weiblich"),
}

enum class MealCategory(val label: String) {
    BREAKFAST("Frühstück"),
    LUNCH("Mittagessen"),
    SNACK("Snack"),
    DINNER("Abendessen"),
}

/** Qualitative certainty of an estimate. Intentionally not a percentage. */
enum class Confidence(val label: String) {
    HIGH("Sehr sicher"),
    MEDIUM("Mittel"),
    LOW("Unsicher"),
}

enum class FoodSource { BASE_DB, USER_ADDED, ONLINE_CACHED }

enum class Difficulty(val label: String) {
    EASY("Einsteiger"),
    MEDIUM("Fortgeschritten"),
    HARD("Anspruchsvoll"),
}

enum class Metric(val label: String, val unit: String) {
    CALORIES("Kalorien", "kcal"),
    PROTEIN("Protein", "g"),
    CARBS("Kohlenhydrate", "g"),
    FAT("Fett", "g"),
    WEIGHT("Gewicht", "kg"),
}
