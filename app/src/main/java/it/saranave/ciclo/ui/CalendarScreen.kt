package it.saranave.ciclo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.saranave.ciclo.data.Catalog
import it.saranave.ciclo.data.Cycle
import it.saranave.ciclo.data.Store
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)

@Composable
fun CalendarScreen(onPick: (LocalDate) -> Unit) {
    val ctx = LocalContext.current
    val rev by Store.revision.collectAsState()
    val days = remember(rev) { Store.snapshot(ctx) }
    val info = remember(days) { Cycle.compute(days) }
    val (strong, window) = remember(info) { Cycle.predictedDays(info) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    val today = LocalDate.now()
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("◀", style = MaterialTheme.typography.titleLarge) }
            Text(month.format(monthFmt).replaceFirstChar { it.uppercase() }, Modifier.weight(1f),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("▶", style = MaterialTheme.typography.titleLarge) }
        }
        Row(Modifier.fillMaxWidth()) {
            for (w in listOf("L", "M", "M", "G", "V", "S", "D")) Text(w, Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1
        val cells = offset + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) Row(Modifier.fillMaxWidth()) {
            for (c in 0 until 7) {
                val idx = r * 7 + c - offset
                Box(Modifier.weight(1f).aspectRatio(0.85f).padding(2.dp)) {
                    if (idx in 0 until month.lengthOfMonth()) {
                        val date = month.atDay(idx + 1)
                        val d = days[date.toString()]
                        val period = d?.get(Catalog.PERIOD)?.firstOrNull()
                        val bg = when {
                            period != null -> PeriodColors[Catalog.periodLevels.indexOf(period).coerceAtLeast(0)]
                            date in strong && date >= today -> PeriodColors[0].copy(alpha = 0.35f)
                            date in window && date >= today -> PeriodColors[0].copy(alpha = 0.14f)
                            else -> Color.Transparent
                        }
                        val (ros, ds) = Cycle.skinScores(d)
                        Column(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(bg)
                                .then(if (date == today) Modifier.border(2.dp, cs.primary, RoundedCornerShape(12.dp)) else Modifier)
                                .clickable { onPick(date) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("${idx + 1}", fontSize = 15.sp,
                                color = if (period != null && Catalog.periodLevels.indexOf(period) >= 1) Color.White else cs.onSurface,
                                fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (ros > 0) Dot(RosaceaColor)
                                if (ds > 0) Dot(DsColor)
                                if (d?.get(Catalog.SKIN_OK)?.isNotEmpty() == true) Dot(LevelColors[0])
                                if (d?.get("spotting")?.isNotEmpty() == true) Dot(Color(0xFF8D6E63))
                                if (d?.get(Catalog.NOTE)?.isNotEmpty() == true || d?.get(Catalog.EVENT)?.isNotEmpty() == true) Dot(cs.secondary)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(16.dp))
        Legend(PeriodColors[2], "Mestruazioni (più scuro = più abbondanti)")
        Legend(PeriodColors[0].copy(alpha = 0.35f), "Prossimo ciclo previsto")
        Legend(PeriodColors[0].copy(alpha = 0.14f), "Finestra possibile (in base ai tuoi ultimi cicli)")
        Legend(RosaceaColor, "Sintomi da rosacea", dot = true)
        Legend(DsColor, "Sintomi da dermatite seborroica", dot = true)
        Legend(LevelColors[0], "Pelle ok", dot = true)
        Legend(Color(0xFF8D6E63), "Spotting", dot = true)
        Legend(cs.secondary, "Appunti o visite", dot = true)
    }
}

@Composable
private fun Dot(c: Color) = Box(Modifier.size(6.dp).clip(CircleShape).background(c))

@Composable
private fun Legend(c: Color, text: String, dot: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Box(Modifier.size(if (dot) 10.dp else 18.dp).clip(if (dot) CircleShape else RoundedCornerShape(5.dp)).background(c))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
