package com.sinavkoc.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinavkoc.app.model.PushupCatalog
import com.sinavkoc.app.model.PushupVariation
import com.sinavkoc.app.model.PushupVariationId
import com.sinavkoc.app.ui.theme.*

@Composable
fun WorkoutSetupScreen(
    selectedId: PushupVariationId,
    targetReps: Int?,
    todayReps: Int,
    dailyGoal: Int,
    voiceEnabled: Boolean,
    showSkeleton: Boolean,
    onSelectVariation: (PushupVariationId) -> Unit,
    onSelectTarget: (Int?) -> Unit,
    onToggleVoice: () -> Unit,
    onToggleSkeleton: () -> Unit,
    onOpenProgress: () -> Unit,
    onStartWorkout: () -> Unit
) {
    val selected = PushupCatalog.get(selectedId)
    val targets = listOf<Pair<String, Int?>>(
        "Serbest" to null, "10" to 10, "20" to 20, "30" to 30, "50" to 50
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Today Summary Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(SurfaceDark, EmeraldDark.copy(alpha = 0.4f))))
                .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Bugünkü Toplam", color = TextSecondary, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$todayReps", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("/ $dailyGoal şınav", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            TextButton(onClick = onOpenProgress) {
                Text("Gelişimi Gör →", color = Emerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Variation Grid (2 columns)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1. Şınav Varyasyonu Seç", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("6 varyasyon", color = TextSecondary, fontSize = 12.sp)
            }

            PushupCatalog.variations.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { v ->
                        VariationCard(
                            variation = v,
                            selected = v.id == selectedId,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectVariation(v.id) }
                        )
                    }
                }
            }
        }

        // Selected Variation Details + Camera Tip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(selected.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("~${selected.caloriesPerRep} kcal/tekrar", color = Emerald, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(selected.description, color = TextSecondary, fontSize = 12.5.sp, lineHeight = 18.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Sky.copy(alpha = 0.12f))
                    .border(1.dp, Sky.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Sky, modifier = Modifier.size(16.dp))
                Text(selected.cameraTip, color = Color(0xFFBAE6FD), fontSize = 12.sp, lineHeight = 17.sp)
            }
        }

        // Target Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("2. Set Hedefi Belirle", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.forEach { (label, value) ->
                    val active = targetReps == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) Emerald else SurfaceDark)
                            .border(1.dp, if (active) Emerald else BorderSlate, RoundedCornerShape(12.dp))
                            .clickable { onSelectTarget(value) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (active) BgDark else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Quick Toggles
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (voiceEnabled) Emerald.copy(alpha = 0.12f) else SurfaceDark)
                    .border(1.dp, if (voiceEnabled) Emerald.copy(alpha = 0.4f) else BorderSlate, RoundedCornerShape(12.dp))
                    .clickable { onToggleVoice() }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = null,
                    tint = if (voiceEnabled) Emerald else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (voiceEnabled) "Sesli Koç: Açık" else "Sesli Koç: Kapalı",
                    color = if (voiceEnabled) Emerald else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (showSkeleton) Sky.copy(alpha = 0.12f) else SurfaceDark)
                    .border(1.dp, if (showSkeleton) Sky.copy(alpha = 0.4f) else BorderSlate, RoundedCornerShape(12.dp))
                    .clickable { onToggleSkeleton() }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = null,
                    tint = if (showSkeleton) Sky else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (showSkeleton) "İskelet: Açık" else "İskelet: Kapalı",
                    color = if (showSkeleton) Sky else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Start CTA
        Button(
            onClick = onStartWorkout,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = BgDark)
        ) {
            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Kamerayı Aç & Şınava Başla", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun VariationCard(
    variation: PushupVariation,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val badgeColor = when (variation.difficulty) {
        "Başlangıç" -> Emerald
        "Orta" -> Sky
        else -> Purple
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) EmeraldDark.copy(alpha = 0.45f) else SurfaceDark)
            .border(1.5.dp, if (selected) Emerald else BorderSlate, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(13.dp)
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Emerald),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = BgDark, modifier = Modifier.size(14.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(variation.difficulty, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(variation.shortName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(variation.muscles, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
        }
    }
}
