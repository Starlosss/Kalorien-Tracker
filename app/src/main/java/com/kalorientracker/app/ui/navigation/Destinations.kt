package com.kalorientracker.app.ui.navigation

object Routes {
    const val ONBOARDING = "onboarding"

    const val TODAY = "today"
    const val STATISTICS = "statistics"

    const val ADD_GRAPH = "add"
    const val ADD_HOME = "add/home"
    const val ADD_CAMERA = "add/camera"
    const val ADD_DESCRIBE = "add/describe"
    const val ADD_ANALYZE = "add/analyze"
    const val ADD_FOLLOW_UP = "add/follow-up"
    const val ADD_REVIEW = "add/review"
    const val ADD_SUMMARY = "add/summary"
    const val ADD_BARCODE = "add/barcode"
    const val ADD_SEARCH = "add/search?swap={swap}"
    const val ADD_CUSTOM_FOOD = "add/custom-food?barcode={barcode}"

    fun addSearch(swapKey: String? = null) = if (swapKey == null) "add/search" else "add/search?swap=$swapKey"
    fun addCustomFood(barcode: String? = null) = if (barcode == null) "add/custom-food" else "add/custom-food?barcode=$barcode"

    const val MEAL = "meal/{id}"
    fun meal(id: Long) = "meal/$id"

    const val WEIGHT = "weight"
    const val WORKOUT = "workout"

    const val SETTINGS = "settings"
    const val SETTINGS_SECTION = "settings/{section}"
    fun settingsSection(section: SettingsSection) = "settings/${section.name}"

    val bottomBarRoutes = setOf(TODAY, ADD_HOME, STATISTICS)
}

enum class SettingsSection(val title: String, val subtitle: String) {
    PROFILE("Profil", "Alter, Größe, Gewicht, Aktivität"),
    PLAN("Ernährungsplan", "Ziel, Kalorien und Makronährstoffe"),
    AI("KI-Erkennung", "Foto-Erkennung mit Gemma auf dem Gerät"),
    TRAINING("Training", "Home-Workout-Plan"),
    DATA("Daten", "Export, Import, Backup, Löschen"),
    PRIVACY("Datenschutz", "Daten auf dem Gerät, Fotos, Online-Suche"),
    APPEARANCE("Darstellung", "Animationen, Haptik, Diagramme"),
}
