package com.kalorientracker.app.ui.add.manual

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalorientracker.app.domain.model.Food
import com.kalorientracker.app.domain.model.FoodSource
import com.kalorientracker.app.domain.usecase.PortionScale
import com.kalorientracker.app.ui.common.AnimatedNumber
import com.kalorientracker.app.ui.common.EmptyHint
import com.kalorientracker.app.ui.common.Fmt
import com.kalorientracker.app.ui.common.NumberField
import com.kalorientracker.app.ui.common.PortionSlider
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.common.SecondaryButton
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.common.fieldColors
import com.kalorientracker.app.ui.theme.GlassCard
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Palette

/** Search → pick → amount. Used by the add flow and when editing saved meals. */
@Composable
fun FoodPicker(
    viewModel: FoodSearchViewModel,
    confirmLabel: String,
    onPicked: (Food, Double) -> Unit,
    onCreateCustom: (() -> Unit)?,
    modifier: Modifier = Modifier,
    askAmount: Boolean = true,
) {
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val current = selection
    if (current != null && askAmount) {
        QuantityStep(
            food = current.food,
            defaultGrams = current.defaultGrams,
            confirmLabel = confirmLabel,
            onBack = viewModel::clearSelection,
            onConfirm = { grams ->
                viewModel.clearSelection()
                onPicked(current.food, grams)
            },
            modifier = modifier,
        )
    } else {
        if (current != null) {
            LaunchedEffect(current) {
                viewModel.clearSelection()
                onPicked(current.food, current.defaultGrams)
            }
        }
        SearchStep(viewModel, onCreateCustom, modifier)
    }
}

@Composable
private fun SearchStep(viewModel: FoodSearchViewModel, onCreateCustom: (() -> Unit)?, modifier: Modifier) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val frequent by viewModel.frequent.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text("Lebensmittel suchen", color = Palette.TextTertiary) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Palette.TextTertiary) },
            singleLine = true,
            colors = fieldColors(),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (query.isBlank()) {
                if (frequent.isNotEmpty()) {
                    item { SectionLabel("Häufig verwendet", Modifier.padding(vertical = 10.dp)) }
                    items(frequent, key = { "f-${it.id}" }) { FoodRow(it) { viewModel.select(it) } }
                } else {
                    item { EmptyHint("Tippe den Namen eines Lebensmittels ein.") }
                }
            } else {
                items(results, key = { "r-${it.id}" }) { FoodRow(it) { viewModel.select(it) } }
                if (results.isEmpty()) {
                    item { EmptyHint("Auf deinem Gerät nicht gefunden.") }
                }
                item {
                    Column(Modifier.padding(top = 8.dp)) {
                        when (val o = online) {
                            OnlineResults.Idle -> TextButton(onClick = viewModel::searchOnline) {
                                Icon(Icons.Outlined.Language, contentDescription = null, tint = Palette.TextSecondary)
                                Spacer(Modifier.width(8.dp))
                                Text("Online nach „$query“ suchen", color = Palette.TextSecondary)
                            }
                            OnlineResults.Loading -> Text("Suche online …", style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary, modifier = Modifier.padding(12.dp))
                            is OnlineResults.Unavailable -> Text(o.reason, style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary, modifier = Modifier.padding(12.dp))
                            is OnlineResults.Loaded -> {
                                SectionLabel("Online gefunden, wird auf deinem Gerät gespeichert", Modifier.padding(vertical = 10.dp))
                                o.foods.forEach { food -> FoodRow(food) { viewModel.select(food) } }
                            }
                        }
                    }
                }
            }
            if (onCreateCustom != null) {
                item {
                    SecondaryButton("Eigenes Lebensmittel anlegen", onCreateCustom, Modifier.fillMaxWidth().padding(top = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun FoodRow(food: Food, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { haptics.perform(HapticEvent.Tap); onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(food.displayName, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary, maxLines = 2)
            val source = when (food.source) {
                FoodSource.BASE_DB -> null
                FoodSource.USER_ADDED -> "Eigenes"
                FoodSource.ONLINE_CACHED -> "Produkt"
            }
            Text(
                listOfNotNull(source, "P ${Fmt.one(food.per100g.protein)} · K ${Fmt.one(food.per100g.carbs)} · F ${Fmt.one(food.per100g.fat)}").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text("${Fmt.int(food.per100g.kcal)} kcal", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
    }
}

/** Amount entry via slider (relative to the usual portion) or exact grams; values update live. */
@Composable
fun QuantityStep(
    food: Food,
    defaultGrams: Double,
    confirmLabel: String,
    onBack: () -> Unit,
    onConfirm: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var portionIndex by remember(food.id) { mutableIntStateOf(PortionScale.NORMAL_INDEX) }
    var gramsText by remember(food.id) { mutableStateOf(Fmt.int(defaultGrams)) }
    val grams = Fmt.parse(gramsText) ?: 0.0
    val nutrients = food.per100g.forGrams(grams)
    Column(modifier) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column {
                Text(food.name, style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary)
                food.brand?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary) }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedNumber(nutrients.kcal, MaterialTheme.typography.displaySmall)
                    Text(" kcal", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    "Protein ${Fmt.int(nutrients.protein)} g · Kohlenhydrate ${Fmt.int(nutrients.carbs)} g · Fett ${Fmt.int(nutrients.fat)} g",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        NumberField(
            value = gramsText,
            onValueChange = { gramsText = it },
            label = "Menge",
            suffix = "g",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        PortionSlider(
            index = portionIndex,
            onIndexChange = {
                portionIndex = it
                gramsText = Fmt.int(defaultGrams * PortionScale.factor(it))
            },
        )
        Text(
            "Normal = ${Fmt.int(defaultGrams)} g" + if (food.servingGrams != null) " (übliche Portion)" else "",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextTertiary,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Zurück", onBack, Modifier.weight(1f))
            PrimaryButton(confirmLabel, { onConfirm(grams) }, Modifier.weight(2f), enabled = grams > 0)
        }
    }
}
