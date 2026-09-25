package com.kalorientracker.app.ui.add

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalorientracker.app.domain.model.Meal
import com.kalorientracker.app.ui.common.Fmt
import com.kalorientracker.app.ui.common.ScreenHeader
import com.kalorientracker.app.ui.common.SecondaryButton
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.theme.GlassCard
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Palette

@Composable
fun AddHomeScreen(
    viewModel: AddFlowViewModel,
    onOpenSettings: () -> Unit,
    onCamera: () -> Unit,
    onGalleryImported: () -> Unit,
    onBarcode: () -> Unit,
    onManual: () -> Unit,
    onReview: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(AddFlowViewModel.MAX_PHOTOS),
    ) { uris -> if (uris.isNotEmpty()) viewModel.importGallery(uris, onGalleryImported) }

    LaunchedEffect(Unit) { viewModel.loadRecentMeals() }

    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(title = "Hinzufügen", onSettings = onOpenSettings) }
        item {
            GlassCard(
                Modifier
                    .fillMaxWidth()
                    .clickable { haptics.perform(HapticEvent.Tap); onCamera() },
            ) {
                Column {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(Palette.TextPrimary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = Palette.Background)
                    }
                    Spacer(Modifier.height(28.dp))
                    Text("Foto aufnehmen", style = MaterialTheme.typography.headlineMedium, color = Palette.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Fotografieren, kurz beschreiben – die Analyse passiert auf deinem Gerät.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OptionTile("Galerie", Icons.Outlined.PhotoLibrary, Modifier.weight(1f)) {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                OptionTile("Barcode", Icons.Outlined.QrCodeScanner, Modifier.weight(1f), onBarcode)
                OptionTile("Manuell", Icons.Outlined.EditNote, Modifier.weight(1f), onManual)
            }
        }
        val notice = if (state.importing) "Bilder werden geladen …" else state.message
        if (notice != null) {
            item {
                Text(
                    notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.clearMessage() }
                        .padding(vertical = 4.dp),
                )
            }
        }
        if (state.ingredients.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, Palette.OutlineStrong, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Entwurf")
                        Text(state.suggestedName, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary, maxLines = 1)
                        Text("${Fmt.int(state.totals.kcal)} kcal · ${state.ingredients.size} Zutaten", style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary)
                    }
                    SecondaryButton("Fortsetzen", onReview)
                }
            }
        }
        if (state.recentMeals.isNotEmpty()) {
            item {
                SectionLabel("Schnell erneut eintragen", Modifier.padding(top = 12.dp))
            }
            items(state.recentMeals, key = { "recent-${it.id}" }) { meal ->
                RecentMealRow(meal) {
                    viewModel.relog(meal)
                    onReview()
                }
            }
        }
    }
}

@Composable
private fun OptionTile(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Outline, RoundedCornerShape(22.dp))
            .clickable { haptics.perform(HapticEvent.Tap); onClick() }
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
    }
}

@Composable
private fun RecentMealRow(meal: Meal, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { haptics.perform(HapticEvent.Tap); onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(meal.name, style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary, maxLines = 1)
            Text(
                meal.ingredients.joinToString(" · ") { "${it.name.substringBefore(" (")} ${Fmt.int(it.grams)} g" },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text("${Fmt.int(meal.totals.kcal)} kcal", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
    }
}
