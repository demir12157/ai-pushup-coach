package com.sinavkoc.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sinavkoc.app.model.PushupCatalog
import com.sinavkoc.app.model.WorkoutSession
import com.sinavkoc.app.ui.theme.*

@Composable
fun WorkoutSummaryDialog(
    session: WorkoutSession,
    onDismiss: () -> Unit
) {
    val variation = PushupCatalog.get(session.variationId)
    val issues = session.issueCounts.entries.filter { it.value > 0 }.sortedByDescending { it.value }
    val scoreColor = when {
        session.avgFormScore >= 85 -> Emerald
        session.avgFormScore >= 70 -> Amber
        else -> CoralRed
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceElevated)
                .border(1.dp, BorderSlate, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Antrenman Tamamlandı", color = Emerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(variation.name, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("Tekrar", "${session.reps}", TextPrimary, Modifier.weight(1f))
                StatPill("Form Puanı", "%${session.avgFormScore}", scoreColor, Modifier.weight(1f))
                StatPill("Süre", "${session.durationSec}sn", Sky, Modifier.weight(1f))
            }

            if (issues.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Emerald.copy(alpha = 0.14f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald, modifier = Modifier.size(18.dp))
                    Text(
                        "Harika iş! Bu sette belirgin bir form bozukluğu tespit edilmedi.",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            } else {
                Text("Koç Önerileri", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                issues.forEach { (issue, count) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Amber.copy(alpha = 0.12f))
                            .border(1.dp, Amber.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp))
                                Text(issue.label, color = Amber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text("$count kez", color = Amber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text(issue.recommendation, color = TextPrimary, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = BgDark)
            ) {
                Text("Kaydet & Kapat", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun StatPill(title: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(title, color = TextSecondary, fontSize = 11.sp)
    }
}
