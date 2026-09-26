package com.kalorientracker.app.ui.add.camera

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kalorientracker.app.ui.add.AddFlowViewModel
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Motion
import com.kalorientracker.app.ui.theme.motionSpec
import com.kalorientracker.app.ui.theme.Palette
import java.io.File

@Composable
fun CameraScreen(
    viewModel: AddFlowViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberCameraPermission()
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val useCases = remember { listOf(imageCapture) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun capture() {
        if (capturing || state.photos.size >= AddFlowViewModel.MAX_PHOTOS) return
        haptics.perform(HapticEvent.Capture)
        capturing = true
        val file: File = viewModel.newPhotoFile()
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    error = null
                    viewModel.onPhotoCaptured(file.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    error = "Aufnahme fehlgeschlagen. Bitte erneut versuchen."
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        if (permission.granted) {
            CameraPreview(useCases, Modifier.fillMaxSize())
            CaptureFrame()
        } else {
            PermissionMissing("Für Fotos deiner Mahlzeit braucht die App die Kamera. Die Bilder bleiben auf deinem Gerät.", permission.request)
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { haptics.perform(HapticEvent.Tap); onBack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = Palette.TextPrimary)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.photos.size} / ${AddFlowViewModel.MAX_PHOTOS}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
            val latestIssue = state.photos.lastOrNull()?.let { state.photoIssues[it] }
            val hint = when {
                error != null -> error!!
                latestIssue != null -> latestIssue.message
                state.photos.isEmpty() -> "Essen innerhalb des Rahmens platzieren"
                state.photos.size == 1 -> "Ein zweites Foto von oben macht die Schätzung genauer."
                else -> "Weitere Fotos aus anderen Winkeln oder von der Verpackung helfen bei der Erkennung."
            }
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextPrimary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.Background.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            if (latestIssue != null) {
                Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallPill("Verwerfen") { viewModel.removePhoto(state.photos.last()) }
                }
            }
            Spacer(Modifier.weight(1f))

            LazyRow(
                Modifier.fillMaxWidth().height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            ) {
                items(state.photos, key = { it }) { path ->
                    Box(Modifier.size(64.dp)) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, if (state.photoIssues[path] != null) Palette.Signal else Palette.OutlineStrong, RoundedCornerShape(14.dp)),
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Palette.Background.copy(alpha = 0.75f))
                                .clickable { haptics.perform(HapticEvent.Tap); viewModel.removePhoto(path) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Foto entfernen", tint = Palette.TextPrimary, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f))
                ShutterButton(enabled = permission.granted && !capturing, onClick = ::capture)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    val hasPhotos = state.photos.isNotEmpty()
                    val alpha by animateFloatAsState(if (hasPhotos) 1f else 0f, motionSpec(Motion.enter()), label = "continue")
                    if (hasPhotos || alpha > 0f) {
                        PrimaryButton("Weiter", onContinue, Modifier.graphicsLayer { this.alpha = alpha }, enabled = hasPhotos)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .size(78.dp)
            .scale(if (pressed) 0.92f else 1f)
            .border(3.dp, Palette.TextPrimary, CircleShape)
            .padding(7.dp)
            .clip(CircleShape)
            .background(if (enabled) Palette.TextPrimary else Palette.OutlineStrong)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun SmallPill(text: String, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Palette.Background,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.TextPrimary)
            .clickable { haptics.perform(HapticEvent.Tap); onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
