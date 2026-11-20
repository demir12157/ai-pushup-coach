package com.sinavkoc.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinavkoc.app.data.DayStat
import com.sinavkoc.app.model.FormIssue
import com.sinavkoc.app.model.PushupCatalog
import com.sinavkoc.app.model.WorkoutSession
import com.sinavkoc.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProgressScreen(
    sessions: List<WorkoutSession>,
    dailyGoal: Int,
    todayReps: Int,
    streak: Int,
    weekStats: List<DayStat>,
    topIssues: List<Pair<FormIssue, Int>>,
    onChangeDailyGoal: (Int) -> Unit,
    onSelectSession: (WorkoutSession) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    val totalReps = sessions.sumOf { it.reps }
    val bestSet = sessions.maxOfOrNull { it.reps } ?: 0
    val avgScore = if (sessions.isEmpty()) 0 else (sessions.sumOf { it.avgFormScore } / sessions.size)
    val dailyPct = ((todayReps.toFloat() / dailyGoal.coerceAtLeast(1)) * 100f).coerceIn(0f, 100f)
    val maxDay = (weekStats.maxOfOrNull { it.reps } ?: 10).coerceAtLeast(10)
    val dateFmt = androidx.compose.runtime.remember { SimpleDateFormat("dd MMM HH:mm", Locale("tr", "TR")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text("Gelişimin", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Antrenman istatistiklerin ve form trendin", color = TextSecondary, fontSize = 13.sp)
        }

        // Daily Goal Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Günlük Hedef", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(20, 30, 50, 100).forEach { goal ->
                        val active = dailyGoal == goal
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) Emerald else SurfaceElevated)
                                .clickable { onChangeDailyGoal(goal) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "$goal",
                                color = if (active) BgDark else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text("$todayReps / $dailyGoal şınav", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("%${dailyPct.toInt()}", color = Emerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(BorderSlate)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(dailyPct / 100f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Emerald)
                )
            }
        }

        // 4 Stat Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricBox("Toplam Şınav", "$totalReps", Emerald, Modifier.weight(1f))
            MetricBox("Günlük Seri", "$streak gün", Amber, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricBox("Ort. Form", if (sessions.isEmpty()) "--" else "%$avgScore", Sky, Modifier.weight(1f))
            MetricBox("En İyi Set", if (bestSet == 0) "--" else "$bestSet", Purple, Modifier.weight(1f))
        }

        // 7-day Bar Chart
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Son 7 Gün", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Günlük şınav", color = TextSecondary, fontSize = 12.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(130.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                weekStats.forEach { day ->
                    val frac = if (day.reps > 0) (day.reps.toFloat() / maxDay).coerceIn(0.12f, 1f) else 0.05f
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            if (day.reps > 0) "${day.reps}" else "",
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height((84 * frac).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (day.reps > 0) Emerald else BorderSlate)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(day.label, color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        // Recurring Form Mistakes
        if (topIssues.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .border(1.dp, Amber.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
                    Text("En Sık Form Hataların", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                topIssues.take(3).forEach { (issue, count) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgDark)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(issue.label, color = Amber, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Text("$count kez", color = TextSecondary, fontSize = 11.sp)
                        }
                        Text(issue.recommendation, color = TextSecondary, fontSize = 11.5.sp, lineHeight = 16.sp)
                    }
                }
            }
        }

        // History
        Text("Antrenman Geçmişi", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Henüz kayıtlı antrenman yok.", color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            sessions.forEach { s ->
                val v = PushupCatalog.get(s.variationId)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
                        .clickable { onSelectSession(s) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(v.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "${dateFmt.format(Date(s.timestampMs))} • ${s.durationSec}sn • Form %${s.avgFormScore}",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${s.reps}", color = Emerald, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        IconButton(onClick = { onDeleteSession(s.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Sil", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MetricBox(title: String, value: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = accent, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}
