package com.bokor.fuelapp

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun OdometerScanner(
    onResult: (String) -> Unit,
    onError: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(cameraExecutor) { onDispose { cameraExecutor.shutdown() } }
    val imageCapture = remember { ImageCapture.Builder().build() }
    
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(cameraProviderFuture) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Scanner Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val scanWidth = width * 0.8f
            val scanHeight = 100.dp.toPx()
            val left = (width - scanWidth) / 2
            val top = (height - scanHeight) / 2

            // Darken the background around the focus area
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(Offset(left, top), Size(scanWidth, scanHeight)),
                        cornerRadius = CornerRadius(12.dp.toPx())
                    )
                )
            }
            
            // Draw overlay with cutout
            drawRect(color = Color.Black.copy(alpha = 0.6f))
            drawPath(
                path = path,
                color = Color.Transparent,
                blendMode = BlendMode.Clear
            )
            
            // Draw border for the focus area
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(scanWidth, scanHeight),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }
        
        Text(
            text = "Center the Odometer digits in the box",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 140.dp)
        )
        
        Button(
            onClick = {
                takePhoto(
                    context = context,
                    imageCapture = imageCapture,
                    executor = cameraExecutor,
                    onImageCaptured = { uri ->
                        processImage(
                            context = context,
                            uri = uri,
                            onTextFound = { text ->
                                val reading = longestDigitRun(text)
                                if (reading == null) onError(R.string.scan_no_digits) else onResult(reading)
                            },
                            onFailure = { onError(R.string.scan_failed) }
                        )
                    },
                    onFailure = { onError(R.string.scan_failed) }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        ) {
            Text("Capture")
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: ExecutorService,
    onImageCaptured: (Uri) -> Unit,
    onFailure: () -> Unit
) {
    val photoFile = File(context.cacheDir, "odometer_scan.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // The whole frame is recognised rather than a hand-cropped band: the previous
                // crop assumed the preview and the capture shared an aspect ratio and ignored
                // EXIF rotation, so it read the wrong strip on most devices. InputImage applies
                // the rotation itself, and longestDigitRun picks the odometer out of the text.
                onImageCaptured(Uri.fromFile(photoFile))
            }

            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
                onFailure()
            }
        }
    )
}

/**
 * Picks the most plausible odometer reading out of recognised text: the longest run of
 * digits, which beats concatenating every digit on the dashboard into one number.
 */
fun longestDigitRun(text: String): String? =
    Regex("""\d+""").findAll(text)
        .map { it.value }
        .maxByOrNull { it.length }
        ?.takeIf { it.isNotEmpty() }

private fun processImage(
    context: Context,
    uri: Uri,
    onTextFound: (String) -> Unit,
    onFailure: () -> Unit
) {
    try {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromFilePath(context, uri)

        recognizer.process(image)
            .addOnSuccessListener { visionText -> onTextFound(visionText.text) }
            .addOnFailureListener { e ->
                e.printStackTrace()
                onFailure()
            }
    } catch (e: Exception) {
        e.printStackTrace()
        onFailure()
    }
}
