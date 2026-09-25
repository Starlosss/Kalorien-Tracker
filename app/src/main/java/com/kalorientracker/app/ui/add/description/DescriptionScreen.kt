package com.kalorientracker.app.ui.add.description

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kalorientracker.app.ui.add.AddFlowViewModel
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.common.ScreenHeader
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.common.fieldColors
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Palette
import java.io.File

@Composable
fun DescriptionScreen(
    viewModel: AddFlowViewModel,
    onBack: () -> Unit,
    onAddPhotos: () -> Unit,
    onAnalyze: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun start() {
        viewModel.analyze()
        onAnalyze()
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(title = "Beschreibung", onBack = onBack)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(state.photos, key = { it }) { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(88.dp).clip(RoundedCornerShape(18.dp)),
                )
            }
            item {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Palette.OutlineStrong, RoundedCornerShape(18.dp))
                        .clickable { haptics.perform(HapticEvent.Tap); onAddPhotos() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Weiteres Foto", tint = Palette.TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Was hast du gegessen?", style = MaterialTheme.typography.headlineSmall, color = Palette.TextPrimary)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::setDescription,
            placeholder = { Text("z. B. Reis mit Hähnchen in Sahnesoße", color = Palette.TextTertiary) },
            colors = fieldColors(),
            shape = RoundedCornerShape(18.dp),
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { start() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Spacer(Modifier.height(10.dp))
        SectionLabel("Kurz reicht. Mengen wie „200 g Reis“ werden übernommen.")
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            text = "Analysieren",
            onClick = ::start,
            enabled = state.photos.isNotEmpty() || state.description.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
    }
}
