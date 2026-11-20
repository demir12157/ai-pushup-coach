package com.sinavkoc.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinavkoc.app.model.FormIssue
import com.sinavkoc.app.model.PushupCatalog
import com.sinavkoc.app.model.PushupVariationId
import com.sinavkoc.app.ui.theme.*

@Composable
fun CoachGuideScreen(
    topIssues: List<Pair<FormIssue, Int>>,
    onStartVariation: (PushupVariationId) -> Unit
) {
    var expandedId by remember { mutableStateOf<PushupVariationId?>(PushupVariationId.STANDARD) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("AI Form Koçu & Rehber", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Doğru teknik ve sakatlıksız gelişim ipuçları", color = TextSecondary, fontSize = 13.sp)
        }

        // Personalized Coach Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(EmeraldDark.copy(alpha = 0.4f))
                .border(1.dp, Emerald.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("SANA ÖZEL KOÇ ÖNERİSİ", color = Emerald, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            if (topIssues.isNotEmpty()) {
                val (issue, count) = topIssues.first()
                Text(
                    "Son setlerinde en sık ${issue.label} tespit edildi ($count kez).",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                Text(issue.recommendation, color = TextSecondary, fontSize = 12.5.sp, lineHeight = 18.sp)
            } else {
                Text(
                    "İlk antrenmanını tamamladığında kamera analizine göre bel hizan, iniş derinliğin ve tempon hakkında sana özel öneriler burada görünecek.",
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // Variation Guides
        PushupCatalog.variations.forEach { v ->
            val expanded = expandedId == v.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedId = if (expanded) null else v.id }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("${v.name}  •  ${v.difficulty}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(v.muscles, color = TextSecondary, fontSize = 12.sp)
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }

                if (expanded) {
                    Column(
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(v.description, color = TextSecondary, fontSize = 12.5.sp, lineHeight = 18.sp)
                        Text("Doğru Form Kontrol Listesi", color = Emerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        v.checklist.forEach { tip ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald, modifier = Modifier.size(15.dp))
                                Text(tip, color = TextPrimary, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { onStartVariation(v.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald.copy(alpha = 0.18f), contentColor = Emerald)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${v.shortName} ile Başla", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}
