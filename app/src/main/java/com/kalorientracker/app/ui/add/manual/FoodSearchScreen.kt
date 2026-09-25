package com.kalorientracker.app.ui.add.manual

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kalorientracker.app.ui.add.AddFlowViewModel
import com.kalorientracker.app.ui.common.ScreenHeader

@Composable
fun FoodSearchScreen(
    addFlow: AddFlowViewModel,
    swapKey: String?,
    onBack: () -> Unit,
    onCreateCustom: () -> Unit,
    onDone: () -> Unit,
    viewModel: FoodSearchViewModel = hiltViewModel(),
) {
    val swapping = swapKey?.let { addFlow.ingredient(it) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp)) {
        ScreenHeader(
            title = if (swapping != null) "Austauschen" else "Lebensmittel",
            subtitle = swapping?.name,
            onBack = onBack,
        )
        FoodPicker(
            viewModel = viewModel,
            confirmLabel = if (swapping != null) "Austauschen" else "Hinzufügen",
            askAmount = swapping == null,
            onPicked = { food, grams ->
                if (swapping != null) addFlow.swapFood(swapping.key, food) else addFlow.addFood(food, grams)
                onDone()
            },
            onCreateCustom = if (swapping == null) onCreateCustom else null,
            modifier = Modifier.weight(1f),
        )
    }
}
