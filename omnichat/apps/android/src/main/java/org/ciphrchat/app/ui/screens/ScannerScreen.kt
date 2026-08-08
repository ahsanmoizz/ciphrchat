package org.ciphrchat.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.ciphrchat.app.ui.components.CiphrPrimaryButton
import org.ciphrchat.app.ui.components.CiphrSecondaryButton
import org.ciphrchat.app.ui.theme.CiphrBackground
import org.ciphrchat.app.ui.theme.CiphrText
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScannerScreen(
    onScanResult: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanCompleted = remember { AtomicBoolean(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val executor = Executors.newSingleThreadExecutor()
                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            try {
                                if (!scanCompleted.get()) {
                                    val text = decodeQr(imageProxy)
                                    if (text?.trimStart()?.startsWith("{") == true &&
                                        scanCompleted.compareAndSet(false, true)
                                    ) {
                                        ContextCompat.getMainExecutor(ctx).execute {
                                            onScanResult(text)
                                        }
                                    }
                                }
                            } finally {
                                imageProxy.close()
                            }
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
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
                },
                modifier = Modifier.fillMaxSize()
            )

            CiphrSecondaryButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CiphrBackground)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Camera permission is required to scan QR codes.",
                color = CiphrText,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            CiphrPrimaryButton(
                text = "Grant Permission",
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            CiphrSecondaryButton(
                text = "Cancel",
                onClick = onCancel
            )
        }
    }
}

private fun decodeQr(image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val width = image.width
    val height = image.height
    val buffer = plane.buffer.duplicate()
    val data = ByteArray(width * height)
    val base = buffer.position()

    // YUV_420_888 commonly has row padding. Copy only luminance pixels so
    // ZXing receives a tightly packed image instead of a padded camera row.
    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = base + y * plane.rowStride + x * plane.pixelStride
            if (index >= buffer.limit()) return null
            data[y * width + x] = buffer.get(index)
        }
    }

    val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
    val candidates = listOf(source, source.invert())
    for (candidate in candidates) {
        runCatching {
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(candidate))).text
        }.onSuccess { text ->
            if (text.trimStart().startsWith("{")) return text
        }
    }
    return null
}
