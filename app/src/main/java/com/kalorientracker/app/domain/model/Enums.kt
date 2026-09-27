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

enum class FoodSource {
    /** Curated entries seeded from `base_foods.json`. */
    BASE_DB,

    /** Created by the user in "Eigenes Lebensmittel". */
    USER_ADDED,

    /**
     * A row from the bundled `products.csv`. Kept apart from [ONLINE_CACHED] so a backup can
     * leave it out: it ships inside the APK and is re-imported on every device anyway.
     */
    CATALOG,

    /** Fetched online for a barcode or search the device did not know, then kept locally. */
    ONLINE_CACHED,

    /** Remembered from a recognised meal whose food the database did not know. */
    AI_ESTIMATED,
}

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
