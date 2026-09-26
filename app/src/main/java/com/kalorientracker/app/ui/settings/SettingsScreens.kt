package com.kalorientracker.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalorientracker.app.data.ai.Formats
import com.kalorientracker.app.data.ai.ModelManager
import com.kalorientracker.app.data.ai.ModelState
import com.kalorientracker.app.domain.model.Goal
import com.kalorientracker.app.domain.model.Sex
import com.kalorientracker.app.domain.model.SportActivity
import com.kalorientracker.app.domain.model.UserProfile
import com.kalorientracker.app.domain.usecase.ActivityCatalog
import com.kalorientracker.app.ui.common.Fmt
import com.kalorientracker.app.ui.common.NumberField
import com.kalorientracker.app.ui.common.NutrientRow
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.common.ScreenHeader
import com.kalorientracker.app.ui.common.SecondaryButton
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.common.SourceNote
import com.kalorientracker.app.ui.common.SelectChip
import com.kalorientracker.app.ui.common.ToggleRow
import com.kalorientracker.app.ui.navigation.SettingsSection
import com.kalorientracker.app.ui.onboarding.Stepper
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Palette
import com.kalorientracker.app.ui.theme.PlainCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenSection: (SettingsSection) -> Unit) {
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
        ScreenHeader(title = "Einstellungen", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        SettingsSection.entries.forEach { section ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { haptics.perform(HapticEvent.Tap); onOpenSection(section) }
                    .padding(vertical = 16.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
                    Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Palette.TextTertiary)
            }
            HorizontalDivider(color = Palette.Outline.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Alle deine Daten und Fotos bleiben auf deinem Gerät.\nEs gibt keinen Account und keine Anmeldung.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextTertiary,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    onBack: () -> Unit,
    onOpenWorkout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val proposal by viewModel.proposal.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp)) {
        ScreenHeader(title = section.title, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (section) {
                SettingsSection.PROFILE -> ProfileSection(state.profile, viewModel::saveProfile)
                SettingsSection.PLAN -> PlanSection(state, viewModel)
                SettingsSection.AI -> AiSection(state.settings.aiEnabled, viewModel)
                SettingsSection.TRAINING -> TrainingSection(state, onOpenWorkout)
                SettingsSection.DATA -> DataSection(state.learningCount, viewModel)
                SettingsSection.PRIVACY -> PrivacySection(state.settings.onlineLookupEnabled, viewModel)
                SettingsSection.APPEARANCE -> AppearanceSection(state, viewModel)
            }
            Spacer(Modifier.height(24.dp))
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { viewModel.clearMessage() }
                    .padding(4.dp),
            )
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(4_000)
                viewModel.clearMessage()
            }
        }
    }

    proposal?.let { p ->
        AlertDialog(
            onDismissRequest = viewModel::dismissProposal,
            containerColor = Palette.SurfaceRaised,
            title = { Text("Plan anpassen?", color = Palette.TextPrimary) },
            text = {
                Column {
                    Text(p.reason, color = Palette.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    val current = state.targets
                    NutrientRow("Kalorien", "${Fmt.int(p.targets.targetKcal)} kcal", detail = current?.let { "(bisher ${Fmt.int(it.targetKcal)})" })
                    NutrientRow("Protein", "${p.targets.proteinG} g", detail = current?.let { "(bisher ${it.proteinG})" })
                    NutrientRow("Kohlenhydrate", "${p.targets.carbsG} g", detail = current?.let { "(bisher ${it.carbsG})" })
                    NutrientRow("Fett", "${p.targets.fatG} g", detail = current?.let { "(bisher ${it.fatG})" })
                }
            },
            confirmButton = { TextButton(onClick = viewModel::acceptProposal) { Text("Übernehmen", color = Palette.TextPrimary) } },
            dismissButton = { TextButton(onClick = viewModel::dismissProposal) { Text("Behalten", color = Palette.TextSecondary) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSection(profile: UserProfile?, onSave: (UserProfile) -> Unit) {
    if (profile == null) return
    var age by remember(profile) { mutableStateOf(profile.age.toString()) }
    var height by remember(profile) { mutableStateOf(Fmt.one(profile.heightCm).removeSuffix(",0")) }
    var weight by remember(profile) { mutableStateOf(Fmt.one(profile.weightKg)) }
    var target by remember(profile) { mutableStateOf(profile.targetWeightKg?.let { Fmt.one(it) } ?: "") }
    var sex by remember(profile) { mutableStateOf(profile.sex) }
    var steps by remember(profile) { mutableStateOf(profile.dailySteps.toString()) }
    var activities by remember(profile) { mutableStateOf(profile.activities) }

    val parsed = run {
        val a = age.toIntOrNull()?.takeIf { it in 14..100 } ?: return@run null
        val h = Fmt.parse(height)?.takeIf { it in 120.0..230.0 } ?: return@run null
        val w = Fmt.parse(weight)?.takeIf { it in 35.0..300.0 } ?: return@run null
        val t = if (target.isBlank()) null else Fmt.parse(target)?.takeIf { it in 35.0..300.0 } ?: return@run null
        val s = steps.toIntOrNull()?.takeIf { it in 0..60_000 } ?: return@run null
        profile.copy(age = a, heightCm = h, weightKg = w, targetWeightKg = t, sex = sex, dailySteps = s, activities = activities)
    }

    SectionLabel("Körper")
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sex.entries.forEach { SelectChip(it.label, sex == it, { sex = it }) }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberField(age, { age = it }, "Alter", Modifier.weight(1f), decimal = false)
        NumberField(height, { height = it }, "Größe", Modifier.weight(1f), suffix = "cm")
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberField(weight, { weight = it }, "Gewicht", Modifier.weight(1f), suffix = "kg")
        NumberField(target, { target = it }, "Zielgewicht", Modifier.weight(1f), suffix = "kg")
    }
    Spacer(Modifier.height(20.dp))
    SectionLabel("Aktivität")
    Spacer(Modifier.height(8.dp))
    NumberField(steps, { steps = it }, "Schritte pro Tag", Modifier.fillMaxWidth(), decimal = false)
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (ActivityCatalog.defaults + "Andere").forEach { name ->
            val active = activities.any { it.name == name }
            SelectChip(name, active, {
                activities = if (active) activities.filterNot { it.name == name } else activities + SportActivity(name, 2, 45)
            })
        }
    }
    activities.forEach { activity ->
        Spacer(Modifier.height(10.dp))
        PlainCard(Modifier.fillMaxWidth()) {
            Column {
                Text(activity.name, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
                Stepper("Pro Woche", "${activity.sessionsPerWeek}×", {
                    activities = activities.map { if (it.name == activity.name) it.copy(sessionsPerWeek = (it.sessionsPerWeek - 1).coerceAtLeast(1)) else it }
                }, {
                    activities = activities.map { if (it.name == activity.name) it.copy(sessionsPerWeek = (it.sessionsPerWeek + 1).coerceAtMost(14)) else it }
                })
                Stepper("Dauer", "${activity.minutesPerSession} min", {
                    activities = activities.map { if (it.name == activity.name) it.copy(minutesPerSession = (it.minutesPerSession - 5).coerceAtLeast(5)) else it }
                }, {
                    activities = activities.map { if (it.name == activity.name) it.copy(minutesPerSession = (it.minutesPerSession + 5).coerceAtMost(300)) else it }
                })
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    PrimaryButton(
        "Profil speichern",
        { parsed?.let(onSave) },
        Modifier.fillMaxWidth(),
        enabled = parsed != null && parsed != profile,
        haptic = HapticEvent.Confirm,
    )
    if (parsed == null) {
        Text("Eine Eingabe passt noch nicht. Bitte die Felder oben prüfen.", style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary, modifier = Modifier.padding(top = 8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanSection(state: SettingsUiState, vm: SettingsViewModel) {
    val profile = state.profile ?: return
    val targets = state.targets
    SectionLabel("Ziel")
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Goal.entries.forEach { goal -> SelectChip(goal.label, profile.goal == goal, { vm.proposeGoal(goal) }) }
    }
    Spacer(Modifier.height(20.dp))
    if (targets != null) {
        var kcal by remember(targets) { mutableStateOf(targets.targetKcal.toString()) }
        var protein by remember(targets) { mutableStateOf(targets.proteinG.toString()) }
        var carbs by remember(targets) { mutableStateOf(targets.carbsG.toString()) }
        var fat by remember(targets) { mutableStateOf(targets.fatG.toString()) }
        PlainCard(Modifier.fillMaxWidth()) {
            Column {
                SectionLabel("Aktueller Plan · seit ${LocalDate.ofEpochDay(targets.effectiveFromEpochDay).format(DateTimeFormatter.ofPattern("d.M.yyyy"))}")
                NutrientRow("Erhaltungsbedarf (geschätzt)", "${Fmt.int(targets.maintenanceKcal)} kcal")
                if (targets.isUserAdjusted) {
                    Text("Manuell angepasst", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        NumberField(kcal, { kcal = it }, "Kalorienziel", Modifier.fillMaxWidth(), suffix = "kcal", decimal = false)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(protein, { protein = it }, "Protein", Modifier.weight(1f), suffix = "g", decimal = false)
            NumberField(carbs, { carbs = it }, "Kohlenh.", Modifier.weight(1f), suffix = "g", decimal = false)
            NumberField(fat, { fat = it }, "Fett", Modifier.weight(1f), suffix = "g", decimal = false)
        }
        val values = listOf(kcal, protein, carbs, fat).map { it.toIntOrNull() }
        val valid = values.all { it != null } && values[0]!! in 1000..6000
        val changed = valid && (values[0] != targets.targetKcal || values[1] != targets.proteinG || values[2] != targets.carbsG || values[3] != targets.fatG)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            "Zielwerte speichern",
            { vm.saveManualTargets(values[0]!!, values[1]!!, values[2]!!, values[3]!!) },
            Modifier.fillMaxWidth(),
            enabled = changed,
            haptic = HapticEvent.Confirm,
        )
        Spacer(Modifier.height(20.dp))
        SectionLabel("Weitere Richtwerte")
        NutrientRow("Ballaststoffe", "mind. ${targets.fiberG} g")
        NutrientRow("Zucker", "max. ${targets.sugarMaxG} g")
        NutrientRow("Gesättigte Fettsäuren", "max. ${targets.saturatedFatMaxG} g")
        NutrientRow("Salz", "max. ${Fmt.small(targets.saltMaxG)} g")
    }
    Spacer(Modifier.height(20.dp))
    SecondaryButton("Aus Profil neu berechnen", vm::proposeRecalculation, Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    SecondaryButton("Automatische Anpassung prüfen", vm::checkAdaptive, Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    Text(
        "Die Anpassung vergleicht dein Gewicht mit den Kalorien, die du eingetragen hast. Geändert wird nichts ohne deine Bestätigung.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextTertiary,
    )
    Spacer(Modifier.height(24.dp))
    SourceNote()
}

@Composable
private fun TrainingSection(state: SettingsUiState, onOpenWorkout: () -> Unit) {
    val plan = state.workoutPlan
    PlainCard(Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("Home-Workout")
            Spacer(Modifier.height(6.dp))
            if (plan == null) {
                Text("Kein Plan angelegt.", style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary)
            } else {
                NutrientRow("Häufigkeit", "${plan.frequencyPerWeek}× pro Woche")
                NutrientRow("Dauer", "${plan.minutesPerSession} min")
                NutrientRow("Niveau", plan.difficulty.label)
                NutrientRow("Übungen", "${plan.exercises.size}")
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    PrimaryButton(if (plan == null) "Plan erstellen" else "Plan ansehen und anpassen", onOpenWorkout, Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    Text(
        "Dein Training zählt beim Kalorienbedarf mit, sobald du den Plan unter „Ernährungsplan“ neu berechnen lässt.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextTertiary,
    )
}

@Composable
private fun DataSection(learningCount: Int, vm: SettingsViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    val stamp = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri, includePhotos = false)
    }
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri, includePhotos = true)
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    SettingBlock("Backup", "Sichert alle Daten samt Fotos in einer ZIP-Datei. Du kannst sie danach auf den PC oder einen USB-Stick kopieren. Die Datei ist nicht verschlüsselt.") {
        PrimaryButton("Backup erstellen", { exportZip.launch("kalorien-backup-$stamp.zip") }, Modifier.fillMaxWidth())
    }
    SettingBlock("Export", "Schreibt alle Daten ohne Fotos in eine JSON-Datei: Mahlzeiten, Nährwerte, Gewicht, Ziele, Einstellungen, Produkte, Training und Gelerntes.") {
        SecondaryButton("Daten exportieren", { exportJson.launch("kalorien-export-$stamp.json") }, Modifier.fillMaxWidth())
    }
    SettingBlock("Import", "Holt einen früheren Stand aus einem Backup oder Export zurück. Deine jetzigen Daten werden dabei ersetzt.") {
        SecondaryButton("Backup oder Export importieren", { importFile.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth())
    }
    SettingBlock("Lernsystem", "Die App hat $learningCount Portionskorrekturen gespeichert und rechnet sie in neue Schätzungen ein. Die Lebensmitteldatenbank bleibt unverändert.") {
        SecondaryButton("Gelerntes zurücksetzen", vm::resetLearning, Modifier.fillMaxWidth(), enabled = learningCount > 0)
    }
    SettingBlock("Daten löschen", "Löscht alle Mahlzeiten, Fotos, Gewichtseinträge, Ziele und Einstellungen von diesem Gerät.") {
        SecondaryButton("Alle Daten löschen", { confirmDelete = true }, Modifier.fillMaxWidth())
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Palette.SurfaceRaised,
            title = { Text("Wirklich alles löschen?", color = Palette.TextPrimary) },
            text = { Text("Das lässt sich nicht rückgängig machen. Erstelle vorher ein Backup, wenn du die Daten behalten möchtest.", color = Palette.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteAll() }) { Text("Alles löschen", color = Palette.Signal) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen", color = Palette.TextSecondary) } },
        )
    }
}

@Composable
private fun PrivacySection(onlineEnabled: Boolean, vm: SettingsViewModel) {
    val stats by vm.photoStats.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshPhotoStats() }

    PlainCard(Modifier.fillMaxWidth()) {
        Column {
            Text("Alle deine Daten und Fotos bleiben auf deinem Gerät.", style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Es gibt keinen Account und keine Anmeldung. Die Foto-Erkennung mit Gemma läuft ganz auf dem Gerät. Nur das Modell wird einmal von Hugging Face geladen. Deine Fotos gehen nie an einen Server.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    ToggleRow(
        title = "Online-Suche für unbekannte Produkte",
        subtitle = "Läuft nur, wenn ein Barcode oder Suchbegriff auf deinem Gerät nicht gefunden wird. An Open Food Facts geht dann nur dieser Barcode oder Suchbegriff. Das Ergebnis wird auf deinem Gerät gespeichert.",
        checked = onlineEnabled,
        onCheckedChange = vm::setOnlineLookup,
    )
    Spacer(Modifier.height(16.dp))
    SettingBlock("Fotos", "${stats.first} Fotos · ${Fmt.one(stats.second / 1_048_576.0)} MB auf diesem Gerät. Ein einzelnes Foto löschst du in der Mahlzeit, zu der es gehört.") {
        SecondaryButton("Alle Fotos löschen", { confirm = true }, Modifier.fillMaxWidth(), enabled = stats.first > 0)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Palette.SurfaceRaised,
            title = { Text("Alle Fotos löschen?", color = Palette.TextPrimary) },
            text = { Text("Mahlzeiten und Nährwerte bleiben erhalten, nur die Bilder werden entfernt.", color = Palette.TextSecondary) },
            confirmButton = { TextButton(onClick = { confirm = false; vm.deleteAllPhotos() }) { Text("Löschen", color = Palette.Signal) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen", color = Palette.TextSecondary) } },
        )
    }
}

@Composable
private fun AiSection(aiEnabled: Boolean, vm: SettingsViewModel) {
    val state by vm.modelState.collectAsStateWithLifecycle()
    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    // Progress is shown in a notification; without the permission the download would run unseen.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.downloadModel() }
    val startDownload = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else vm.downloadModel()
    }
    LaunchedEffect(Unit) { vm.refreshModel() }
    val ram = vm.deviceRamGb

    PlainCard(Modifier.fillMaxWidth()) {
        Column {
            Text(ModelManager.MODEL_NAME, style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary)
            Text("Von Google, Lizenz Apache 2.0. Läuft komplett auf deinem Gerät.", style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary)
            Spacer(Modifier.height(10.dp))
            Text(
                "Erkennt Gerichte und Zutaten auf deinen Fotos, schätzt die Mengen und fragt nach, wenn etwas unklar ist. " +
                    "Das Modell (2,4 GB) wird einmal geladen, danach läuft alles offline. Eine Analyse dauert je nach Gerät 10–30 Sekunden.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    if (ram < ModelManager.RECOMMENDED_RAM_GB) {
        Text(
            "Dein Gerät hat ${Fmt.one(ram)} GB Arbeitsspeicher. Empfohlen sind mindestens 6 GB. Die Analyse kann langsam sein oder abbrechen.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.height(12.dp))
    }
    when (val s = state) {
        ModelState.NotDownloaded, is ModelState.Failed -> {
            if (s is ModelState.Failed) {
                Text(s.reason, style = MaterialTheme.typography.bodyMedium, color = Palette.TextPrimary)
                Spacer(Modifier.height(10.dp))
            }
            ToggleRow("Nur über WLAN laden", wifiOnly, vm::setWifiOnly, "Empfohlen, der Download ist 2,4 GB groß")
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Modell herunterladen (2,4 GB)", startDownload, Modifier.fillMaxWidth(), haptic = HapticEvent.Confirm)
            Spacer(Modifier.height(8.dp))
            Text(
                "Frei: ${Fmt.one(vm.freeStorageBytes / 1_073_741_824.0)} GB",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
            )
        }
        is ModelState.Downloading -> {
            SectionLabel(if (s.waitingForWifi) "Wartet auf WLAN" else "Wird geladen")
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { s.progress },
                modifier = Modifier.fillMaxWidth(),
                color = Palette.TextPrimary,
                trackColor = Palette.Track,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${Fmt.one(s.downloadedBytes / 1_073_741_824.0)} von ${Fmt.one(s.totalBytes / 1_073_741_824.0)} GB · ${(s.progress * 100).toInt()} %",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
            )
            val pace = listOfNotNull(
                Formats.speed(s.bytesPerSecond).takeIf { s.bytesPerSecond > 0 },
                s.remainingText,
            ).joinToString(" · ")
            if (pace.isNotEmpty()) {
                Text(pace, style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary)
            }
            Text(
                "Der Download läuft weiter, auch wenn du die App verlässt. Nach einer Unterbrechung geht es an der gleichen Stelle weiter.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
            )
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Abbrechen", vm::cancelModelDownload, Modifier.fillMaxWidth())
        }
        ModelState.Ready -> {
            Text("Bereit. Fotos werden jetzt auf dem Gerät analysiert.", style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
            Spacer(Modifier.height(8.dp))
            ToggleRow("Foto-Erkennung verwenden", aiEnabled, vm::setAiEnabled, "Aus: Die Schätzung kommt nur aus deiner Beschreibung")
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Modell löschen (2,4 GB freigeben)", { confirmDelete = true }, Modifier.fillMaxWidth())
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Palette.SurfaceRaised,
            title = { Text("Modell löschen?", color = Palette.TextPrimary) },
            text = { Text("Danach musst du das Modell erst wieder herunterladen, bevor die Foto-Erkennung läuft.", color = Palette.TextSecondary) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteModel() }) { Text("Löschen", color = Palette.Signal) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen", color = Palette.TextSecondary) } },
        )
    }
}

@Composable
private fun AppearanceSection(state: SettingsUiState, vm: SettingsViewModel) {
    val s = state.settings
    ToggleRow("Animationen", s.animationsEnabled, vm::setAnimations, "Weiche Übergänge, hochzählende Zahlen und animierte Diagramme")
    ToggleRow("Vibration", s.hapticsEnabled, vm::setHaptics, "Kurzes Vibrieren bei Reglern, Knöpfen und beim Speichern")
    ToggleRow("Linien glätten", s.smoothCharts, vm::setSmoothCharts, "Liniendiagramme als weiche Kurven")
    ToggleRow("Zielgewicht im Diagramm", s.showTargetWeightLine, vm::setTargetWeightLine, "Blendet das Zielgewicht als Linie ein")
}

@Composable
private fun SettingBlock(title: String, text: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary)
        Spacer(Modifier.height(12.dp))
        content()
    }
}
