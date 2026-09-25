package com.kalorientracker.app.ui.add.barcode

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.kalorientracker.app.ui.add.AddFlowViewModel
import com.kalorientracker.app.ui.add.BarcodeState
import com.kalorientracker.app.ui.add.camera.CameraPreview
import com.kalorientracker.app.ui.add.camera.CaptureFrame
import com.kalorientracker.app.ui.add.camera.PermissionMissing
import com.kalorientracker.app.ui.add.camera.rememberCameraPermission
import com.kalorientracker.app.ui.add.manual.QuantityStep
import com.kalorientracker.app.ui.common.NumberField
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.common.ScreenHeader
import com.kalorientracker.app.ui.common.SecondaryButton
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Palette
import java.util.concurrent.Executors

@Composable
fun BarcodeScreen(
    viewModel: AddFlowViewModel,
    onBack: () -> Unit,
    onCreateCustom: (String) -> Unit,
    onSearch: () -> Unit,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    LaunchedEffect(Unit) { viewModel.resetBarcode() }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        ScreenHeader(title = "Barcode", onBack = onBack, modifier = Modifier.padding(horizontal = 20.dp))
        when (val barcode = state.barcode) {
            BarcodeState.Scanning, is BarcodeState.Loading -> ScannerView(
                loading = barcode is BarcodeState.Loading,
                onCode = { code ->
                    haptics.perform(HapticEvent.Tick)
                    viewModel.onBarcode(code)
                },
            )
            is BarcodeState.Found -> Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (barcode.fromOnline) "Produkt online gefunden und lokal gespeichert" else "Produkt gefunden",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                QuantityStep(
                    food = barcode.food,
                    defaultGrams = barcode.defaultGrams,
                    confirmLabel = "Hinzufügen",
                    onBack = viewModel::resetBarcode,
                    onConfirm = { grams ->
                        viewModel.addFood(barcode.food, grams)
                        viewModel.resetBarcode()
                        onDone()
                    },
                )
            }
            is BarcodeState.NotFound -> Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Unbekanntes Produkt", style = MaterialTheme.typography.headlineSmall, color = Palette.TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(barcode.reason, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                Text("Barcode ${barcode.code}", style = MaterialTheme.typography.labelMedium, color = Palette.TextTertiary, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Selbst anlegen", { onCreateCustom(barcode.code) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Lebensmittel suchen", onSearch, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = viewModel::resetBarcode, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Erneut scannen", color = Palette.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ScannerView(loading: Boolean, onCode: (String) -> Unit) {
    val permission = rememberCameraPermission()
    val currentOnCode by rememberUpdatedState(onCode)
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build(),
        )
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor) { proxy -> analyze(scanner, proxy) { code -> currentOnCode(code) } } }
    }
    val useCases = remember { listOf(analysis) }
    DisposableEffect(Unit) {
        onDispose {
            analysis.clearAnalyzer()
            scanner.close()
            executor.shutdown()
        }
    }
    var manual by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Palette.Surface),
        ) {
            if (permission.granted) {
                CameraPreview(useCases, Modifier.fillMaxSize())
                CaptureFrame(aspect = 1.6f)
            } else {
                PermissionMissing("Zum Scannen wird die Kamera benötigt.", permission.request)
            }
            Text(
                if (loading) "Suche Produkt …" else "Barcode in den Rahmen halten",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextPrimary,
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
            )
        }
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(manual, { manual = it }, "Nummer eingeben", Modifier.weight(1f), decimal = false)
            PrimaryButton("Suchen", { if (manual.length >= 6) onCode(manual) }, enabled = manual.length >= 6 && !loading)
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyze(scanner: BarcodeScanner, proxy: ImageProxy, onCode: (String) -> Unit) {
    val media = proxy.image
    if (media == null) {
        proxy.close()
        return
    }
    val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onCode) }
        .addOnCompleteListener { proxy.close() }
}
