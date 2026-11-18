package com.seagori.hgr.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.seagori.hgr.i18n.LocalStrings
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 데스크탑이 표시한 페어링 QR을 카메라로 읽는다.
 * CAMERA 권한은 이 화면을 열 때만 요청한다.
 * QR 디코딩은 Apache-2.0 라이선스의 ZXing Core를 사용한다.
 */
@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onScanned: (String) -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    val seen = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val frameBuffers = remember { QrFrameBuffers() }
    val reader = remember {
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )
            )
        }
    }
    val providerHolder = remember { CameraHolder() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result -> granted = result }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(Unit) {
        onDispose {
            providerHolder.provider?.unbindAll()
            providerHolder.provider = null
            executor.shutdown()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.syncQrTitle) },
        text = {
            Column {
                if (!granted) {
                    Text(strings.syncQrCameraDenied, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(strings.syncQrGrantCamera)
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                runCatching {
                                    val provider = future.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val analysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                        .build()
                                    analysis.setAnalyzer(executor) { proxy ->
                                        try {
                                            val value = decodeQr(proxy, reader, frameBuffers)
                                            if (value != null && seen.compareAndSet(false, true)) {
                                                mainExecutor.execute { onScanned(value) }
                                            }
                                        } finally {
                                            proxy.close()
                                        }
                                    }
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis
                                    )
                                    providerHolder.provider = provider
                                }.onFailure {
                                    error = it.message
                                }
                            }, mainExecutor)
                            previewView
                        },
                        onRelease = {
                            providerHolder.provider?.unbindAll()
                            providerHolder.provider = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(strings.syncQrHint, fontSize = 12.sp)
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    )
}

private class QrFrameBuffers {
    var data = ByteArray(0)
    var row = ByteArray(0)

    fun luminance(size: Int): ByteArray {
        if (data.size < size) data = ByteArray(size)
        return data
    }

    fun rowBuffer(stride: Int): ByteArray {
        if (row.size < stride) row = ByteArray(stride)
        return row
    }
}

/** YUV_420_888 프레임의 Y 평면을 ZXing 휘도 소스로 변환해 QR을 디코딩한다. */
private fun decodeQr(
    proxy: ImageProxy,
    reader: MultiFormatReader,
    buffers: QrFrameBuffers
): String? {
    val media = proxy.image ?: return null
    if (media.planes.isEmpty()) return null
    val plane = media.planes[0]
    val width = media.width
    val height = media.height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    if (width <= 0 || height <= 0 || rowStride < width) return null

    val data = buffers.luminance(width * height)
    val row = buffers.rowBuffer(rowStride)
    val buffer = plane.buffer
    buffer.rewind()
    var offset = 0
    for (y in 0 until height) {
        val length = minOf(rowStride, buffer.remaining())
        if (length <= 0) break
        buffer.get(row, 0, length)
        if (pixelStride == 1) {
            System.arraycopy(row, 0, data, offset, width)
        } else {
            var src = 0
            for (x in 0 until width) {
                data[offset + x] = row[src]
                src += pixelStride
            }
        }
        offset += width
    }

    val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
    val bitmap = BinaryBitmap(HybridBinarizer(source))
    return try {
        reader.decodeWithState(bitmap).text?.takeIf { it.isNotBlank() }
    } catch (e: ReaderException) {
        null
    } finally {
        reader.reset()
    }
}

private class CameraHolder {
    var provider: ProcessCameraProvider? = null
}
