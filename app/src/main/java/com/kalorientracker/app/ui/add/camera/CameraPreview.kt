package com.kalorientracker.app.ui.add.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.theme.Palette

class CameraPermission(val granted: Boolean, val request: () -> Unit)

@Composable
fun rememberCameraPermission(): CameraPermission {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    return CameraPermission(granted) { launcher.launch(Manifest.permission.CAMERA) }
}

/** Binds a back-camera preview plus the given use cases to the current lifecycle. */
@Composable
fun CameraPreview(useCases: List<UseCase>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(owner, useCases) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener(
            {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                p.unbindAll()
                runCatching {
                    p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, *useCases.toTypedArray())
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose { provider?.unbindAll() }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Dims everything outside the central capture frame and draws corner marks. */
@Composable
fun CaptureFrame(modifier: Modifier = Modifier, aspect: Float = 1f) {
    Canvas(modifier.fillMaxSize()) {
        val frameWidth = size.width * 0.82f
        val frameHeight = (frameWidth / aspect).coerceAtMost(size.height * 0.7f)
        val left = (size.width - frameWidth) / 2
        val top = (size.height - frameHeight) / 2.3f
        val radius = 28.dp.toPx()
        val frame = RoundRect(Rect(Offset(left, top), Size(frameWidth, frameHeight)), CornerRadius(radius, radius))
        val hole = Path().apply { addRoundRect(frame) }
        clipPath(hole, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.55f))
        }
        val stroke = 3.dp.toPx()
        val len = 34.dp.toPx()
        val right = left + frameWidth
        val bottom = top + frameHeight
        val c = Color.White
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(x, y + dy * radius), Offset(x, y + dy * (radius + len)), stroke, StrokeCap.Round)
            drawLine(c, Offset(x + dx * radius, y), Offset(x + dx * (radius + len), y), stroke, StrokeCap.Round)
            drawArc(
                c,
                startAngle = when {
                    dx > 0 && dy > 0 -> 180f
                    dx < 0 && dy > 0 -> 270f
                    dx < 0 && dy < 0 -> 0f
                    else -> 90f
                },
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(if (dx > 0) x else x - 2 * radius, if (dy > 0) y else y - 2 * radius),
                size = Size(2 * radius, 2 * radius),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        corner(left, top, 1f, 1f)
        corner(right, top, -1f, 1f)
        corner(right, bottom, -1f, -1f)
        corner(left, bottom, 1f, -1f)
    }
}

@Composable
fun PermissionMissing(text: String, onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Kamera erlauben", onRequest)
    }
}
