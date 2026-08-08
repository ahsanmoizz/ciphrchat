package org.ciphrchat.app.transport.infrared

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InfraredCameraReceiver @Inject constructor(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val decoder = InfraredOpticalDecoder()
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var attached = false
    private val _frames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
    val frames: SharedFlow<ByteArray> = _frames.asSharedFlow()

    fun attach(owner: LifecycleOwner): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return false
        attached = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val cameraProvider = future.get()
                provider = cameraProvider
                val next = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                next.setAnalyzer(executor) { image -> analyze(image) }
                analysis?.let { cameraProvider.unbind(it) }
                cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, next)
                analysis = next
            }
        }, ContextCompat.getMainExecutor(context))
        return true
    }

    fun detach() {
        attached = false
        analysis?.let { provider?.unbind(it) }
        analysis = null
        decoder.reset()
    }

    fun isAttached(): Boolean = attached && analysis != null

    private fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val width = image.width
            val height = image.height
            val left = width / 4
            val right = width * 3 / 4
            val top = height / 4
            val bottom = height * 3 / 4
            var total = 0L
            var count = 0
            for (y in top until bottom step 8) {
                for (x in left until right step 8) {
                    val index = y * plane.rowStride + x * plane.pixelStride
                    if (index < buffer.limit()) {
                        total += buffer.get(index).toInt() and 0xFF
                        count++
                    }
                }
            }
            if (count > 0) decoder.offer((total / count).toInt(), image.imageInfo.timestamp / 1_000_000L)?.let(_frames::tryEmit)
        } finally {
            image.close()
        }
    }
}
