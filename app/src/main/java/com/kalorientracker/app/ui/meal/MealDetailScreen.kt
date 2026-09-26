package com.kalorientracker.app.ui.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kalorientracker.app.domain.model.Ingredient
import com.kalorientracker.app.ui.add.manual.FoodPicker
import com.kalorientracker.app.ui.add.manual.FoodSearchViewModel
import com.kalorientracker.app.ui.add.review.CategoryChips
import com.kalorientracker.app.ui.add.review.NutritionSummary
import com.kalorientracker.app.ui.add.review.TimeDialog
import com.kalorientracker.app.ui.common.ConfidenceBadge
import com.kalorientracker.app.ui.common.EmptyHint
import com.kalorientracker.app.ui.common.Fmt
import com.kalorientracker.app.ui.common.NumberField
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.common.ScreenHeader
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.common.TextInput
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Palette
import java.io.File
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(
    onBack: () -> Unit,
    viewModel: MealDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    var confirmDelete by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    val meal = state.draft
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        ScreenHeader(
            title = "Mahlzeit",
            subtitle = meal?.let { Fmt.relativeDay(java.time.LocalDate.ofEpochDay(it.epochDay)) },
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 20.dp),
            actions = {
                if (meal != null) {
                    IconButton(onClick = { haptics.perform(HapticEvent.Tap); confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Löschen", tint = Palette.TextSecondary)
                    }
                }
            },
        )
        if (state.notFound) {
            EmptyHint("Diese Mahlzeit gibt es nicht mehr. Geh mit dem Pfeil oben zurück.")
            return@Column
        }
        if (meal == null) return@Column

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (meal.photoPaths.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(meal.photoPaths, key = { it }) { path ->
                        Box(Modifier.size(110.dp)) {
                            AsyncImage(
                                model = File(path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Palette.Background.copy(alpha = 0.75f))
                                    .clickable { haptics.perform(HapticEvent.Tap); viewModel.removePhoto(path) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Foto löschen", tint = Palette.TextPrimary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            TextInput(meal.name, viewModel::setName, "Name", Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            NutritionSummary(meal.totals)
            Spacer(Modifier.height(20.dp))
            SectionLabel("Kategorie")
            Spacer(Modifier.height(8.dp))
            CategoryChips(meal.category, viewModel::setCategory)
            Spacer(Modifier.height(12.dp))
            val time = Instant.ofEpochMilli(meal.loggedAtMillis).atZone(ZoneId.systemDefault()).toLocalTime()
            Row(
                Modifier.fillMaxWidth().clickable { haptics.perform(HapticEvent.Tap); showTime = true }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Uhrzeit", style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
                Text(Fmt.time(time), style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("Zutaten")
            meal.ingredients.forEachIndexed { index, ingredient ->
                IngredientEditRow(
                    ingredient = ingredient,
                    onGrams = { viewModel.setGrams(index, it) },
                    onRemove = { viewModel.removeIngredient(index) },
                )
            }
            TextButton(onClick = { showPicker = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Palette.TextPrimary)
                Spacer(Modifier.width(6.dp))
                Text("Zutat hinzufügen", color = Palette.TextPrimary)
            }
            meal.description?.let {
                Spacer(Modifier.height(8.dp))
                Text("Beschreibung: $it", style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary)
            }
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton(
            text = if (state.dirty) "Änderungen speichern" else "Gespeichert",
            enabled = state.dirty && meal.ingredients.isNotEmpty(),
            haptic = HapticEvent.Save,
            onClick = { viewModel.save(onBack) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }

    val current = state.draft
    if (showTime && current != null) {
        TimeDialog(
            initial = Instant.ofEpochMilli(current.loggedAtMillis).atZone(ZoneId.systemDefault()).toLocalTime(),
            onDismiss = { showTime = false },
            onConfirm = { viewModel.setTime(it); showTime = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Palette.SurfaceRaised,
            title = { Text("Mahlzeit löschen?", color = Palette.TextPrimary) },
            text = { Text("Die Mahlzeit und ihre Fotos werden von diesem Gerät entfernt.", color = Palette.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    haptics.perform(HapticEvent.Confirm)
                    viewModel.delete(onBack)
                }) { Text("Löschen", color = Palette.Signal) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen", color = Palette.TextSecondary) } },
        )
    }

    if (showPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val searchViewModel: FoodSearchViewModel = hiltViewModel(key = "meal-picker")
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = sheetState,
            containerColor = Palette.SurfaceRaised,
        ) {
            FoodPicker(
                viewModel = searchViewModel,
                confirmLabel = "Hinzufügen",
                onPicked = { food, grams ->
                    viewModel.addFood(food, grams)
                    showPicker = false
                },
                onCreateCustom = null,
                modifier = Modifier.fillMaxHeight(0.85f).padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun IngredientEditRow(ingredient: Ingredient, onGrams: (Double) -> Unit, onRemove: () -> Unit) {
    var text by remember(ingredient.id, ingredient.name) { mutableStateOf(Fmt.int(ingredient.grams)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(ingredient.name, style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
            Text(
                "${Fmt.int(ingredient.nutrients.kcal)} kcal · P ${Fmt.int(ingredient.nutrients.protein)} g",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
            )
            ingredient.confidence?.let { ConfidenceBadge(it, Modifier.padding(top = 4.dp)) }
        }
        NumberField(
            value = text,
            onValueChange = { t -> text = t; Fmt.parse(t)?.let(onGrams) },
            label = "g",
            modifier = Modifier.width(96.dp),
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Close, contentDescription = "Entfernen", tint = Palette.TextTertiary)
        }
    }
}
