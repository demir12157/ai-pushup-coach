package com.sinavkoc.app.ui.screens

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sinavkoc.app.audio.VoiceCoach
import com.sinavkoc.app.model.*
import com.sinavkoc.app.pose.PoseLandmarkerHelper
import com.sinavkoc.app.pose.PushupAnalyzer
import com.sinavkoc.app.ui.theme.*
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private val SKELETON_EDGES = listOf(
    11 to 13, 13 to 15,
    12 to 14, 14 to 16,
    11 to 12,
    11 to 23, 12 to 24,
    23 to 24,
    23 to 25, 25 to 27,
    24 to 26, 26 to 28
)

@Composable
fun CameraWorkoutScreen(
    variation: PushupVariation,
    targetReps: Int?,
    voiceEnabled: Boolean,
    showSkeleton: Boolean,
    voiceCoach: VoiceCoach,
    onToggleVoice: () -> Unit,
    onToggleSkeleton: () -> Unit,
    onFinish: (WorkoutSession?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isFrontCamera by remember { mutableStateOf(true) }
    var liveState by remember { mutableStateOf(LivePoseState()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var elapsedSec by remember { mutableIntStateOf(0) }

    val analyzer = remember(variation.id) { PushupAnalyzer(variation) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(Unit) {
        voiceCoach.speak("${variation.name} başladı. Yan profilden pozisyon al.", priority = true)
        while (true) {
            delay(1000)
            elapsedSec += 1
        }
    }

    val landmarkerHelper = remember(variation.id) {
        PoseLandmarkerHelper(
            context = context,
            onResult = { points ->
                val out = analyzer.analyze(points)
                liveState = out.state
                if (out.repCompleted != null) {
                    voiceCoach.announceRep(
                        out.state.reps,
                        targetReps,
                        out.repCompleted.issues.isEmpty()
                    )
                } else if (out.voiceCue != null) {
                    voiceCoach.speak(out.voiceCue)
                }
            },
            onError = { msg -> errorMsg = msg }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            landmarkerHelper.close()
            cameraExecutor.shutdown()
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(isFrontCamera) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(480, 640))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { imgAnalysis ->
                        imgAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            landmarkerHelper.detectLiveStream(imageProxy, isFrontCamera)
                        }
                    }

                val selector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (e: Exception) {
                errorMsg = "Kamera başlatılamadı: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    val formColor = when (liveState.severity) {
        FormSeverity.GOOD -> Emerald
        FormSeverity.WARNING -> Amber
        FormSeverity.ERROR -> CoralRed
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Real Hardware Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Live Pose Skeleton Overlay
        if (showSkeleton && liveState.landmarks.size >= 29) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lms = liveState.landmarks
                for ((a, b) in SKELETON_EDGES) {
                    val pA = lms[a]
                    val pB = lms[b]
                    if (pA.visibility < 0.3f || pB.visibility < 0.3f) continue
                    drawLine(
                        color = formColor,
                        start = Offset(pA.x * size.width, pA.y * size.height),
                        end = Offset(pB.x * size.width, pB.y * size.height),
                        strokeWidth = 10f,
                        cap = StrokeCap.Round
                    )
                }
                val keyJoints = if (liveState.activeSideRight) {
                    listOf(12, 14, 16, 24, 26, 28)
                } else {
                    listOf(11, 13, 15, 23, 25, 27)
                }
                for (idx in keyJoints) {
                    val p = lms[idx]
                    if (p.visibility < 0.3f) continue
                    val center = Offset(p.x * size.width, p.y * size.height)
                    drawCircle(color = formColor, radius = 16f, center = center)
                    drawCircle(color = Color.White, radius = 10f, center = center)
                }
            }
        }

        // Top Bar HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent))
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(
                    onClick = { onFinish(null) },
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                }
                Column {
                    Text(variation.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val mins = elapsedSec / 60
                    val secs = elapsedSec % 60
                    Text(
                        text = "%02d:%02d  •  Form %%%d".format(mins, secs, liveState.currentScore),
                        color = Emerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onToggleVoice,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(if (voiceEnabled) Emerald.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(
                        if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Sesli Koç",
                        tint = if (voiceEnabled) Emerald else Color.White
                    )
                }
                IconButton(
                    onClick = onToggleSkeleton,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(if (showSkeleton) Sky.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(
                        if (showSkeleton) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "İskelet",
                        tint = if (showSkeleton) Sky else Color.White
                    )
                }
                IconButton(
                    onClick = { isFrontCamera = !isFrontCamera },
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Kamera Çevir", tint = Color.White)
                }
            }
        }

        // Right-side Depth Gauge + Angle Readout
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("DERİNLİK", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(140.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight((liveState.depthPercent / 100f).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.verticalGradient(listOf(Sky, Emerald)))
                )
            }
            Text(
                text = liveState.elbowAngle?.let { "${it}°" } ?: "--",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            liveState.bodyAngle?.let { body ->
                Text(
                    text = "Bel ${body}°",
                    color = if (body < variation.thresholds.minBodyAngle) CoralRed else Emerald,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Error overlay if any
        if (errorMsg != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(16.dp)
            ) {
                Text(errorMsg ?: "", color = CoralRed, fontSize = 14.sp)
            }
        }

        // Bottom HUD: Feedback Banner + Counter + Finish
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)))
                )
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Coaching Feedback Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(formColor.copy(alpha = 0.22f))
                    .border(1.dp, formColor.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    if (liveState.severity == FormSeverity.GOOD) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = formColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = liveState.feedbackText,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Counter + Finish Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${liveState.reps}",
                        color = Color.White,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 58.sp
                    )
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            text = if (targetReps != null) "/ $targetReps HEDEF" else "TEKRAR",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val kcal = ((liveState.reps * variation.caloriesPerRep) * 10f).roundToInt() / 10f
                        Text("$kcal kcal", color = Emerald, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        val reps = analyzer.getReps()
                        if (reps == 0) {
                            onFinish(null)
                        } else {
                            val session = WorkoutSession(
                                id = "${System.currentTimeMillis()}",
                                timestampMs = System.currentTimeMillis(),
                                variationId = variation.id,
                                reps = reps,
                                targetReps = targetReps,
                                durationSec = elapsedSec.coerceAtLeast(1),
                                avgFormScore = analyzer.getAverageScore(),
                                calories = ((reps * variation.caloriesPerRep) * 10f).roundToInt() / 10f,
                                issueCounts = analyzer.getIssueCounts()
                            )
                            onFinish(session)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = BgDark),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bitir & Kaydet", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
        }
    }
}
