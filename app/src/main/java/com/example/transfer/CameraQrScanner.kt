package com.example.transfer

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@Composable
fun CameraQrScanner(
    modifier: Modifier = Modifier,
    onQrCodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasDetected by remember { mutableStateOf(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val reader = MultiFormatReader().apply {
                            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (!hasDetected) {
                                val yBuffer = imageProxy.planes[0].buffer
                                val yBytes = ByteArray(yBuffer.remaining())
                                yBuffer.get(yBytes)
                                val width = imageProxy.width
                                val height = imageProxy.height
                                val source = PlanarYUVLuminanceSource(
                                    yBytes, width, height, 0, 0, width, height, false
                                )
                                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                                try {
                                    val result = reader.decodeWithState(binaryBitmap)
                                    reader.reset()
                                    if (result != null && result.text.isNotEmpty()) {
                                        hasDetected = true
                                        onQrCodeDetected(result.text)
                                    }
                                } catch (e: Exception) {
                                    reader.reset()
                                }
                            }
                            imageProxy.close()
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Overlay with scanning frame and animated laser line
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameSize = size.minDimension * 0.7f
            val left = (size.width - frameSize) / 2f
            val top = (size.height - frameSize) / 2f

            // Dim outer area
            drawRect(
                color = Color.Black.copy(alpha = 0.5f)
            )

            // Clear cutout
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(frameSize, frameSize),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            // Corner accent lines
            val cornerLength = 36.dp.toPx()
            val strokeWidth = 4.dp.toPx()
            val accentColor = Color(0xFF0066FF)

            // Top-Left
            drawLine(accentColor, Offset(left, top + cornerLength), Offset(left, top), strokeWidth)
            drawLine(accentColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)

            // Top-Right
            drawLine(accentColor, Offset(left + frameSize - cornerLength, top), Offset(left + frameSize, top), strokeWidth)
            drawLine(accentColor, Offset(left + frameSize, top), Offset(left + frameSize, top + cornerLength), strokeWidth)

            // Bottom-Left
            drawLine(accentColor, Offset(left, top + frameSize - cornerLength), Offset(left, top + frameSize), strokeWidth)
            drawLine(accentColor, Offset(left, top + frameSize), Offset(left + cornerLength, top + frameSize), strokeWidth)

            // Bottom-Right
            drawLine(accentColor, Offset(left + frameSize - cornerLength, top + frameSize), Offset(left + frameSize, top + frameSize), strokeWidth)
            drawLine(accentColor, Offset(left + frameSize, top + frameSize), Offset(left + frameSize, top + frameSize - cornerLength), strokeWidth)

            // Animated laser line
            val currentLaserY = top + (frameSize * laserY)
            drawLine(
                color = Color(0xFF00E5FF),
                start = Offset(left + 12.dp.toPx(), currentLaserY),
                end = Offset(left + frameSize - 12.dp.toPx(), currentLaserY),
                strokeWidth = 2.5.dp.toPx()
            )
        }
    }
}
