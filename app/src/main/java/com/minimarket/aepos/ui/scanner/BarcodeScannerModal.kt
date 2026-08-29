package com.minimarket.aepos.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.minimarket.aepos.ui.theme.*
import java.util.concurrent.Executors

@Composable
fun BarcodeScannerModal(
    onBarcodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                CameraPreviewWithAnalyzer(
                    onBarcodeScanned = { code ->
                        // Emisión de Beep de escáner POS
                        try {
                            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                        } catch (_: Exception) {}

                        onBarcodeScanned(code)
                    }
                )

                // Overlay y marco de escaneo
                ScannerOverlay(
                    onClose = onDismiss,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Estado: Solicitud de Permiso
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Slate950)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Permiso de Cámara Requerido",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Necesitamos acceso a tu cámara para leer códigos de barras de productos instantáneamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { launcher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        Text("Conceder Permiso", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Slate400)
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreviewWithAnalyzer(
    onBarcodeScanned: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(
                                    cameraExecutor,
                                    BarcodeAnalyzer { barcode ->
                                        onBarcodeScanned(barcode)
                                    }
                                )
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Botón de Linterna (Torch)
        IconButton(
            onClick = {
                isTorchOn = !isTorchOn
                cameraControl?.enableTorch(isTorchOn)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 54.dp)
                .size(52.dp)
                .background(Slate900.copy(alpha = 0.85f), CircleShape)
                .border(1.dp, if (isTorchOn) Emerald500 else Slate700, CircleShape)
        ) {
            Icon(
                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Linterna",
                tint = if (isTorchOn) Emerald400 else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ScannerOverlay(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Laser")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserPosition"
    )

    Box(modifier = modifier) {
        // Botón Cerrar
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 42.dp, end = 18.dp)
                .size(40.dp)
                .background(Slate900.copy(alpha = 0.8f), CircleShape)
                .border(1.dp, Slate700, CircleShape)
        ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(20.dp))
        }

        // Título e instrucciones
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 46.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Slate900.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Slate700)
            ) {
                Text(
                    text = "Apunta al código de barras del producto",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // Marco de escaneo central (Target Box)
        Box(
            modifier = Modifier
                .size(width = 280.dp, height = 190.dp)
                .align(Alignment.Center)
                .border(width = 2.dp, color = Emerald500, shape = RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height * laserPosition
                drawLine(
                    color = Color(0xFF10B981),
                    start = Offset(x = 0f, y = y),
                    end = Offset(x = size.width, y = y),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
    }
}

