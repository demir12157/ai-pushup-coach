package com.sinavkoc.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sinavkoc.app.audio.VoiceCoach
import com.sinavkoc.app.data.WorkoutRepository
import com.sinavkoc.app.model.PushupCatalog
import com.sinavkoc.app.model.PushupVariationId
import com.sinavkoc.app.model.WorkoutSession
import com.sinavkoc.app.ui.components.WorkoutSummaryDialog
import com.sinavkoc.app.ui.screens.CameraWorkoutScreen
import com.sinavkoc.app.ui.screens.CoachGuideScreen
import com.sinavkoc.app.ui.screens.ProgressScreen
import com.sinavkoc.app.ui.screens.WorkoutSetupScreen
import com.sinavkoc.app.ui.theme.*

class MainActivity : ComponentActivity() {
    private lateinit var voiceCoach: VoiceCoach
    private lateinit var repository: WorkoutRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        voiceCoach = VoiceCoach(this)
        repository = WorkoutRepository(this)

        setContent {
            SinavKocTheme {
                SinavKocRoot(repository = repository, voiceCoach = voiceCoach)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceCoach.shutdown()
    }
}

@Composable
private fun SinavKocRoot(
    repository: WorkoutRepository,
    voiceCoach: VoiceCoach
) {
    val context = LocalContext.current

    var activeTab by remember { mutableIntStateOf(0) }
    var selectedVariationId by remember { mutableStateOf(PushupVariationId.STANDARD) }
    var targetReps by remember { mutableStateOf<Int?>(20) }
    var isWorkoutActive by remember { mutableStateOf(false) }

    var sessions by remember { mutableStateOf(repository.loadSessions()) }
    var dailyGoal by remember { mutableIntStateOf(repository.loadDailyGoal()) }
    var voiceEnabled by remember { mutableStateOf(repository.loadVoiceEnabled()) }
    var showSkeleton by remember { mutableStateOf(repository.loadShowSkeleton()) }
    var summarySession by remember { mutableStateOf<WorkoutSession?>(null) }

    LaunchedEffect(voiceEnabled) {
        voiceCoach.setEnabled(voiceEnabled)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isWorkoutActive = true
        } else {
            Toast.makeText(context, "Şınav sayacı için kamera izni gerekli", Toast.LENGTH_LONG).show()
        }
    }

    fun requestCameraAndStart() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            isWorkoutActive = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (isWorkoutActive) {
        CameraWorkoutScreen(
            variation = PushupCatalog.get(selectedVariationId),
            targetReps = targetReps,
            voiceEnabled = voiceEnabled,
            showSkeleton = showSkeleton,
            voiceCoach = voiceCoach,
            onToggleVoice = {
                voiceEnabled = !voiceEnabled
                repository.saveVoiceEnabled(voiceEnabled)
            },
            onToggleSkeleton = {
                showSkeleton = !showSkeleton
                repository.saveShowSkeleton(showSkeleton)
            },
            onFinish = { session ->
                isWorkoutActive = false
                if (session != null) {
                    sessions = repository.saveSession(session)
                    summarySession = session
                }
            }
        )
    } else {
        val streak = remember(sessions) { repository.calculateStreak(sessions) }
        val todayReps = remember(sessions) { repository.todayReps(sessions) }
        val weekStats = remember(sessions) { repository.last7Days(sessions) }
        val topIssues = remember(sessions) { repository.topFormIssues(sessions) }

        Scaffold(
            containerColor = BgDark,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgDark)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Emerald),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = BgDark, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text("ŞınavKoç", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text("AI Kamera & Form Koçu", color = Emerald, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Amber.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp))
                        Text("$streak gün", color = Amber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = SurfaceDark, contentColor = TextPrimary) {
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        icon = { Icon(Icons.Default.Videocam, contentDescription = null) },
                        label = { Text("Antrenman") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Emerald,
                            selectedTextColor = Emerald,
                            indicatorColor = Emerald.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        icon = { Icon(Icons.Default.TrendingUp, contentDescription = null) },
                        label = { Text("Gelişim") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Emerald,
                            selectedTextColor = Emerald,
                            indicatorColor = Emerald.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                    NavigationBarItem(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        label = { Text("Koç") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Emerald,
                            selectedTextColor = Emerald,
                            indicatorColor = Emerald.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (activeTab) {
                    0 -> WorkoutSetupScreen(
                        selectedId = selectedVariationId,
                        targetReps = targetReps,
                        todayReps = todayReps,
                        dailyGoal = dailyGoal,
                        voiceEnabled = voiceEnabled,
                        showSkeleton = showSkeleton,
                        onSelectVariation = { selectedVariationId = it },
                        onSelectTarget = { targetReps = it },
                        onToggleVoice = {
                            voiceEnabled = !voiceEnabled
                            repository.saveVoiceEnabled(voiceEnabled)
                        },
                        onToggleSkeleton = {
                            showSkeleton = !showSkeleton
                            repository.saveShowSkeleton(showSkeleton)
                        },
                        onOpenProgress = { activeTab = 1 },
                        onStartWorkout = { requestCameraAndStart() }
                    )
                    1 -> ProgressScreen(
                        sessions = sessions,
                        dailyGoal = dailyGoal,
                        todayReps = todayReps,
                        streak = streak,
                        weekStats = weekStats,
                        topIssues = topIssues,
                        onChangeDailyGoal = {
                            dailyGoal = it
                            repository.saveDailyGoal(it)
                        },
                        onSelectSession = { summarySession = it },
                        onDeleteSession = { id -> sessions = repository.deleteSession(id) }
                    )
                    else -> CoachGuideScreen(
                        topIssues = topIssues,
                        onStartVariation = { id ->
                            selectedVariationId = id
                            activeTab = 0
                            requestCameraAndStart()
                        }
                    )
                }
            }
        }
    }

    summarySession?.let { session ->
        WorkoutSummaryDialog(
            session = session,
            onDismiss = { summarySession = null }
        )
    }
}
