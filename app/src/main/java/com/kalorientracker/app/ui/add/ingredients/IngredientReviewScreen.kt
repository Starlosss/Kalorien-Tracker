package com.kalorientracker.app.ui.add.ingredients

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalorientracker.app.domain.model.Nutrients
import com.kalorientracker.app.ui.add.AddFlowViewModel
import com.kalorientracker.app.ui.add.DraftIngredient
import com.kalorientracker.app.ui.common.AnimatedNumber
import com.kalorientracker.app.ui.common.ConfidenceBadge
import com.kalorientracker.app.ui.common.EmptyHint
import com.kalorientracker.app.ui.common.Fmt
import com.kalorientracker.app.ui.common.NumberField
import com.kalorientracker.app.ui.common.PortionSlider
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.common.ScreenHeader
import com.kalorientracker.app.ui.common.SecondaryButton
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.common.TextInput
import com.kalorientracker.app.ui.theme.GlassCard
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.LocalMotion
import com.kalorientracker.app.ui.theme.Palette
import kotlinx.coroutines.delay

@Composable
fun IngredientReviewScreen(
    viewModel: AddFlowViewModel,
    onBack: () -> Unit,
    onAddIngredient: () -> Unit,
    onSwapIngredient: (String) -> Unit,
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        ScreenHeader(title = "Zutaten", onBack = onBack, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { TotalsCard(state.totals) }
            state.notes.forEach { note ->
                item { Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary) }
            }
            item {
                PortionSlider(
                    index = state.mealPortionIndex,
                    onIndexChange = viewModel::setMealPortion,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            item { SectionLabel(if (state.photos.isNotEmpty()) "Erkannt" else "Zutaten", Modifier.padding(top = 4.dp)) }
            if (state.ingredients.isEmpty()) {
                item { EmptyHint("Noch keine Zutaten. Tippe unten auf „Zutat hinzufügen“.") }
            }
            itemsIndexed(state.ingredients, key = { _, it -> it.key }) { index, ingredient ->
                StaggeredAppear(index) {
                    IngredientCard(
                        ingredient = ingredient,
                        effectiveGrams = state.effectiveGrams(ingredient),
                        mealPortionIndex = state.mealPortionIndex,
                        expanded = expandedKey == ingredient.key,
                        onToggle = { expandedKey = if (expandedKey == ingredient.key) null else ingredient.key },
                        onGrams = { viewModel.setGrams(ingredient.key, it) },
                        onPortion = { viewModel.setIngredientPortion(ingredient.key, it) },
                        onRename = { viewModel.rename(ingredient.key, it) },
                        onSwap = { onSwapIngredient(ingredient.key) },
                        onRemove = { viewModel.remove(ingredient.key) },
                    )
                }
            }
            item {
                TextButton(onClick = onAddIngredient) {
                    androidx.compose.material3.Icon(Icons.Outlined.Add, contentDescription = null, tint = Palette.TextPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("Zutat hinzufügen", color = Palette.TextPrimary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Verwerfen", { viewModel.discard(); onDiscard() }, Modifier.weight(1f))
            PrimaryButton("Weiter", onContinue, Modifier.weight(2f), enabled = state.ingredients.isNotEmpty())
        }
    }
}

/** The analysis result appears item by item rather than all at once. */
@Composable
private fun StaggeredAppear(index: Int, content: @Composable () -> Unit) {
    val animate = LocalMotion.current.animationsEnabled
    var visible by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) {
        if (!visible) {
            delay(60L * index)
            visible = true
        }
    }
    AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { it / 3 }) { content() }
}

@Composable
private fun TotalsCard(totals: Nutrients) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Gesamt")
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedNumber(totals.kcal, MaterialTheme.typography.displaySmall)
                    Text(" kcal", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            MacroMini("P", totals.protein, Palette.Protein)
            Spacer(Modifier.width(12.dp))
            MacroMini("K", totals.carbs, Palette.Carbs)
            Spacer(Modifier.width(12.dp))
            MacroMini("F", totals.fat, Palette.Fat)
        }
    }
}

@Composable
private fun MacroMini(label: String, grams: Double, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
        }
        AnimatedNumber(grams, MaterialTheme.typography.titleSmall, format = { "${Fmt.int(it)} g" })
    }
}

@Composable
private fun IngredientCard(
    ingredient: DraftIngredient,
    effectiveGrams: Double,
    mealPortionIndex: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onGrams: (Double) -> Unit,
    onPortion: (Int) -> Unit,
    onRename: (String) -> Unit,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (expanded) Palette.SurfaceRaised else Palette.Surface)
            .border(1.dp, if (expanded) Palette.OutlineStrong else Palette.Outline, shape)
            .animateContentSize(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { haptics.perform(HapticEvent.Tap); onToggle() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(ingredient.name, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
                Spacer(Modifier.height(4.dp))
                ingredient.confidence?.let { ConfidenceBadge(it) }
            }
            Column(horizontalAlignment = Alignment.End) {
                AnimatedNumber(effectiveGrams, MaterialTheme.typography.titleMedium, format = { "ca. ${Fmt.int(it)} g" })
                AnimatedNumber(
                    ingredient.per100g.forGrams(effectiveGrams).kcal,
                    MaterialTheme.typography.bodySmall,
                    color = Palette.TextTertiary,
                    format = { "${Fmt.int(it)} kcal" },
                )
            }
        }
        if (expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                var name by remember(ingredient.key) { mutableStateOf(ingredient.name) }
                TextInput(name, { name = it; onRename(it) }, "Name", Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                var gramsText by remember(ingredient.key, ingredient.portionIndex, mealPortionIndex) {
                    mutableStateOf(Fmt.int(effectiveGrams))
                }
                NumberField(
                    value = gramsText,
                    onValueChange = { text ->
                        gramsText = text
                        Fmt.parse(text)?.let(onGrams)
                    },
                    label = "Genaue Menge",
                    suffix = "g",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                PortionSlider(
                    index = ingredient.portionIndex ?: com.kalorientracker.app.domain.usecase.PortionScale.nearestIndex(ingredient.grams / ingredient.baseGrams.coerceAtLeast(1.0)),
                    onIndexChange = onPortion,
                )
                Spacer(Modifier.height(6.dp))
                val per = ingredient.per100g
                Text(
                    "Pro 100 g: ${Fmt.int(per.kcal)} kcal · P ${Fmt.one(per.protein)} · K ${Fmt.one(per.carbs)} · F ${Fmt.one(per.fat)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextTertiary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Austauschen", onSwap, Modifier.weight(1f), icon = Icons.Outlined.SwapHoriz)
                    SecondaryButton("Entfernen", onRemove, Modifier.weight(1f), icon = Icons.Outlined.Delete)
                }
            }
        }
    }
}
