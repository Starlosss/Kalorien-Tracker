package com.kalorientracker.app.ui.add.analysis

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kalorientracker.app.data.ai.ModelState
import com.kalorientracker.app.ui.add.AddFlowViewModel
import com.kalorientracker.app.ui.add.AnalysisPhase
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.common.ScreenHeader
import com.kalorientracker.app.ui.common.SecondaryButton
import com.kalorientracker.app.ui.common.SelectChip
import com.kalorientracker.app.ui.theme.GlassCard
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Palette
import com.kalorientracker.app.ui.theme.PlainCard
import java.io.File

/** A calm, stepwise progress view instead of a spinner. The analysis itself runs off the main thread. */
@Composable
fun AnalyzingScreen(
    viewModel: AddFlowViewModel,
    onBack: () -> Unit,
    onQuestions: () -> Unit,
    onResult: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current

    LaunchedEffect(state.phase) {
        when (state.phase) {
            AnalysisPhase.QUESTIONS -> { haptics.perform(HapticEvent.Tick); onQuestions() }
            AnalysisPhase.DONE -> { haptics.perform(HapticEvent.Tick); onResult() }
            else -> Unit
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        ScreenHeader(title = "Analyse", onBack = onBack)
        Spacer(Modifier.height(12.dp))
        state.photos.firstOrNull()?.let { path ->
            GlassCard(Modifier.fillMaxWidth().height(200.dp), contentPadding = 0.dp) {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.55f),
                )
                ScanLine()
            }
            Spacer(Modifier.height(28.dp))
        }
        if (state.phase == AnalysisPhase.FAILED) {
            Text("Die Analyse hat nicht funktioniert.", style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Du kannst es erneut versuchen oder die Zutaten selbst eintragen.", color = Palette.TextSecondary)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Erneut versuchen", viewModel::analyze, Modifier.weight(1f))
                SecondaryButton("Manuell", onResult, Modifier.weight(1f))
            }
            return@Column
        }
        AddFlowViewModel.analysisStepLabels.forEachIndexed { index, label ->
            StepRow(label, done = index < state.analysisStep, active = index == state.analysisStep)
        }
        val aiState by viewModel.aiState.collectAsStateWithLifecycle()
        if (aiState is ModelState.Ready && state.photos.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Die Erkennung läuft auf deinem Gerät und dauert bis zu einer halben Minute. Deine Fotos verlassen das Handy nicht.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
            )
        }
    }
}

@Composable
private fun ScanLine() {
    val transition = rememberInfiniteTransition(label = "scan")
    val y by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "scanY")
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .align(Alignment.TopCenter)
                .padding(top = (y * 190).dp)
                .height(1.dp)
                .background(Palette.TextPrimary.copy(alpha = 0.7f)),
        )
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, active: Boolean) {
    val color by animateColorAsState(
        when {
            done -> Palette.TextSecondary
            active -> Palette.TextPrimary
            else -> Palette.TextTertiary.copy(alpha = 0.6f)
        },
        label = "stepColor",
    )
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(0.7f, 1.15f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulseScale")
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(Icons.Outlined.Check, contentDescription = null, tint = Palette.TextSecondary, modifier = Modifier.size(18.dp))
                active -> Box(Modifier.size(8.dp).scale(scale).clip(CircleShape).background(Palette.TextPrimary))
                else -> Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.OutlineStrong))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FollowUpScreen(
    viewModel: AddFlowViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val allAnswered = state.questions.all { it.id in state.answers }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        ScreenHeader(title = "Kurze Rückfrage", onBack = onBack)
        Text(
            if (state.questions.size == 1) "Eine Angabe macht die Schätzung genauer." else "${state.questions.size} Angaben machen die Schätzung genauer.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.questions.forEach { question ->
                PlainCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(question.text, style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary)
                        Spacer(Modifier.height(14.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            question.options.forEach { option ->
                                SelectChip(option, state.answers[question.id] == option, { viewModel.answer(question.id, option) })
                            }
                        }
                    }
                }
            }
        }
        PrimaryButton(
            "Weiter",
            onClick = { viewModel.submitAnswers(onDone) },
            enabled = allAnswered,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { viewModel.submitAnswers(onDone) }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)) {
            Text("Überspringen", color = Palette.TextTertiary)
        }
    }
}
