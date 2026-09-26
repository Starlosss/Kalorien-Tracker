package com.kalorientracker.app.ui.add.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kalorientracker.app.data.repository.FoodRepository
import com.kalorientracker.app.domain.model.Food
import com.kalorientracker.app.domain.model.FoodSource
import com.kalorientracker.app.domain.model.Nutrients
import com.kalorientracker.app.ui.add.AddFlowViewModel
import com.kalorientracker.app.ui.common.Fmt
import com.kalorientracker.app.ui.common.NumberField
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.common.ScreenHeader
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.common.TextInput
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomFoodViewModel @Inject constructor(private val foods: FoodRepository) : ViewModel() {
    fun create(food: Food, onCreated: (Food) -> Unit) {
        viewModelScope.launch { onCreated(foods.addCustom(food)) }
    }
}

/** Manual product entry, e.g. from a package label when a barcode is unknown. Saved locally for next time. */
@Composable
fun CustomFoodScreen(
    addFlow: AddFlowViewModel,
    barcode: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: CustomFoodViewModel = hiltViewModel(),
) {
    var name by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf(barcode.orEmpty()) }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var fiber by rememberSaveable { mutableStateOf("") }
    var sugar by rememberSaveable { mutableStateOf("") }
    var saturated by rememberSaveable { mutableStateOf("") }
    var salt by rememberSaveable { mutableStateOf("") }
    var serving by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("100") }

    val valid = name.isNotBlank() && Fmt.parse(kcal) != null && (Fmt.parse(amount) ?: 0.0) > 0

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp)) {
        ScreenHeader(title = "Eigenes Lebensmittel", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextInput(name, { name = it }, "Name", Modifier.fillMaxWidth())
            TextInput(brand, { brand = it }, "Marke (optional)", Modifier.fillMaxWidth())
            NumberField(code, { code = it }, "Barcode (optional)", Modifier.fillMaxWidth(), decimal = false)
            Spacer(Modifier.height(6.dp))
            SectionLabel("Nährwerte pro 100 g")
            NumberField(kcal, { kcal = it }, "Kalorien", Modifier.fillMaxWidth(), suffix = "kcal")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(protein, { protein = it }, "Protein", Modifier.weight(1f), suffix = "g")
                NumberField(carbs, { carbs = it }, "Kohlenh.", Modifier.weight(1f), suffix = "g")
                NumberField(fat, { fat = it }, "Fett", Modifier.weight(1f), suffix = "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(fiber, { fiber = it }, "Ballastst.", Modifier.weight(1f), suffix = "g")
                NumberField(sugar, { sugar = it }, "Zucker", Modifier.weight(1f), suffix = "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(saturated, { saturated = it }, "Ges. Fettsäuren", Modifier.weight(1f), suffix = "g")
                NumberField(salt, { salt = it }, "Salz", Modifier.weight(1f), suffix = "g")
            }
            NumberField(serving, { serving = it }, "Übliche Portion (optional)", Modifier.fillMaxWidth(), suffix = "g")
            Spacer(Modifier.height(6.dp))
            SectionLabel("Gegessene Menge")
            NumberField(amount, { amount = it }, "Menge", Modifier.fillMaxWidth(), suffix = "g")
            Text(
                "Das Lebensmittel wird auf deinem Gerät gespeichert. Du findest es danach auch ohne Internet in der Suche.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
            )
            Spacer(Modifier.height(12.dp))
        }
        PrimaryButton(
            text = "Speichern und hinzufügen",
            enabled = valid,
            haptic = HapticEvent.Confirm,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            onClick = {
                val food = Food(
                    name = name.trim(),
                    brand = brand.trim().takeIf { it.isNotEmpty() },
                    barcode = code.trim().takeIf { it.isNotEmpty() },
                    per100g = Nutrients(
                        kcal = Fmt.parse(kcal) ?: 0.0,
                        protein = Fmt.parse(protein) ?: 0.0,
                        carbs = Fmt.parse(carbs) ?: 0.0,
                        fat = Fmt.parse(fat) ?: 0.0,
                        fiber = Fmt.parse(fiber) ?: 0.0,
                        sugar = Fmt.parse(sugar) ?: 0.0,
                        saturatedFat = Fmt.parse(saturated) ?: 0.0,
                        salt = Fmt.parse(salt) ?: 0.0,
                    ),
                    servingGrams = Fmt.parse(serving),
                    source = FoodSource.USER_ADDED,
                )
                val grams = Fmt.parse(amount) ?: 100.0
                viewModel.create(food) { saved ->
                    addFlow.addFood(saved, grams)
                    onDone()
                }
            },
        )
    }
}
