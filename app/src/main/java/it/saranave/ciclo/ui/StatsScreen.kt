package it.saranave.ciclo.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.saranave.ciclo.data.Catalog
import it.saranave.ciclo.data.Cycle
import it.saranave.ciclo.data.Day
import it.saranave.ciclo.data.Report
import it.saranave.ciclo.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val fmt = DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN)

private class Indicator(val name: String, val denom: (Day) -> Boolean, val num: (Day) -> Boolean)

private fun has(d: Day, cat: String, vararg keys: String) =
    d[cat]?.any { Catalog.splitLevel(it).first in keys } == true

private val indicators = listOf(
    Indicator("Rosacea attiva", { Cycle.skinLogged(it) }, { Cycle.skinScores(it).first > 0 }),
    Indicator("Dermatite attiva", { Cycle.skinLogged(it) }, { Cycle.skinScores(it).second > 0 }),
    Indicator("Brufoletti / pustole", { Cycle.skinLogged(it) }, { has(it, Catalog.SKIN, "papules", "pustules") }),
    Indicator("Crampi", { it.isNotEmpty() }, { has(it, "pain", "period_cramps") }),
    Indicator("Mal di testa", { it.isNotEmpty() }, { has(it, "pain", "headache", "migraine") }),
    Indicator("Umore giù", { it["feelings"]?.isNotEmpty() == true }, { has(it, "feelings", "sad", "sensitive", "anxious", "irritable") }),
    Indicator("Stanchezza", { it["energy"]?.isNotEmpty() == true }, { has(it, "energy", "tired", "exhausted") }),
    Indicator("Chiusa socialmente", { it["social_life"]?.isNotEmpty() == true }, { has(it, "social_life", "withdrawn") }),
)

@Composable
fun StatsScreen() {
    val ctx = LocalContext.current
    val rev by Store.revision.collectAsState()
    val days = remember(rev) { Store.snapshot(ctx) }
    val info = remember(days) { Cycle.compute(days) }
    val alerts = remember(days) { Cycle.alerts(days, info) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("🩸 Il tuo ciclo") {
                if (info.starts.isEmpty()) Text("Ancora nessun dato: importa da Clue o segna le mestruazioni.")
                else {
                    info.dayOfCycle?.let { Line("Oggi", "giorno $it") }
                    info.nextStart?.let {
                        val diff = ChronoUnit.DAYS.between(LocalDate.now(), it)
                        Line("Prossimo previsto", "${it.format(fmt)} (${if (diff >= 0) "tra $diff gg" else "ritardo ${-diff} gg"})")
                    }
                    if (info.nextEarliest != null && info.nextLatest != null)
                        Line("Finestra possibile", "${info.nextEarliest.format(fmt)} – ${info.nextLatest.format(fmt)}")
                    info.avg?.let { Line("Durata media", "$it gg (da ${info.minL} a ${info.maxL})") }
                    val bl = info.bleedDays.takeLast(6)
                    if (bl.isNotEmpty()) Line("Mestruazioni", "in media ${Math.round(bl.average())} gg")
                    Line("Cicli registrati", "${info.starts.size}")
                }
            }
        }
        if (alerts.isNotEmpty()) item {
            SectionCard("⚠️ Da segnalare al medico se si ripete") {
                alerts.forEach {
                    Column {
                        Text(it.title, fontWeight = FontWeight.SemiBold)
                        Text(it.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (info.lengths.isNotEmpty()) item { SectionCard("📏 Ultimi cicli (giorni)") { CycleBars(info.lengths.takeLast(12)) } }
        item { SectionCard("🌡️ Pelle negli ultimi 90 giorni") { SkinChart(days, info.starts) } }
        item { SectionCard("🔄 Per fase del ciclo") { PhaseTable(days, info) } }
        item { SectionCard("🔥 Trigger nei giorni di sfogo") { TriggerTable(days) } }
        item { SectionCard("📸 Foto della pelle") { PhotoGallery(days) } }
        item {
            SectionCard("🩺 Riepilogo per il medico") {
                Text("Un PDF con cicli, avvisi, sintomi della pelle, trigger, terapie e visite degli ultimi 3 mesi.",
                    style = MaterialTheme.typography.bodySmall)
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val f = withContext(Dispatchers.IO) { runCatching { Report.build(ctx) }.getOrNull() }
                        busy = false
                        if (f != null) {
                            val uri = PhotoFiles.uri(ctx, f)
                            val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
                                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            ctx.startActivity(Intent.createChooser(send, "Condividi il riepilogo"))
                        }
                    }
                }) { Text(if (busy) "Preparo…" else "Crea PDF") }
            }
        }
    }
}

@Composable
private fun Line(k: String, v: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

@Composable
private fun CycleBars(lengths: List<Int>) {
    val max = (lengths.maxOrNull() ?: 35).coerceAtLeast(35)
    Row(Modifier.fillMaxWidth().height(150.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (l in lengths) Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$l", fontSize = 11.sp)
            val ok = l in 21..35
            Box(Modifier.fillMaxWidth().fillMaxHeight(l.toFloat() / max).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(if (ok) PeriodColors[1] else PeriodColors[3].copy(alpha = 0.6f)))
        }
    }
    Text("Più chiari = nella norma (21–35 giorni).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SkinChart(days: Map<String, Day>, starts: List<LocalDate>) {
    val today = LocalDate.now()
    val n = 90
    val dates = (0 until n).map { today.minusDays((n - 1 - it).toLong()) }
    val scores = dates.map { Cycle.skinScores(days[it.toString()]) }
    val period = dates.map { days[it.toString()]?.get(Catalog.PERIOD)?.isNotEmpty() == true }
    val events = dates.map { days[it.toString()]?.get(Catalog.EVENT)?.isNotEmpty() == true }
    val logged = dates.map { Cycle.skinLogged(days[it.toString()]) }
    if (logged.none { it }) {
        Text("Segna la pelle per qualche giorno e qui vedrai rosacea e dermatite separate, con le mestruazioni sullo sfondo.",
            style = MaterialTheme.typography.bodyMedium)
        return
    }
    val maxV = (scores.maxOf { maxOf(it.first, it.second) }).coerceAtLeast(3).toFloat()
    val periodBg = PeriodColors[0].copy(alpha = 0.25f)
    val eventC = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val step = size.width / (n - 1)
        for (i in 0 until n) if (period[i]) drawRect(periodBg, Offset(i * step - step / 2, 0f), Size(step, size.height))
        for (i in 0 until n) if (events[i]) drawLine(eventC, Offset(i * step, 0f), Offset(i * step, size.height), strokeWidth = 3f)
        fun series(sel: (Pair<Int, Int>) -> Int, c: Color) {
            val p = Path(); var started = false
            for (i in 0 until n) {
                if (!logged[i]) continue
                val x = i * step; val y = size.height - sel(scores[i]) / maxV * size.height * 0.92f - 4f
                if (!started) { p.moveTo(x, y); started = true } else p.lineTo(x, y)
                drawCircle(c, 4f, Offset(x, y))
            }
            drawPath(p, c, style = Stroke(width = 4f))
        }
        series({ it.first }, RosaceaColor)
        series({ it.second }, DsColor)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Key(RosaceaColor, "Rosacea"); Key(DsColor, "Dermatite"); Key(periodBg, "Mestruazioni"); Key(eventC, "Visite/terapie")
    }
    Text("Il punteggio è la somma dei livelli dei sintomi di quel giorno.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Key(c: Color, t: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c)); Spacer(Modifier.width(4.dp))
    Text(t, fontSize = 11.sp)
}

@Composable
private fun PhaseTable(days: Map<String, Day>, info: it.saranave.ciclo.data.CycleInfo) {
    val rows = remember(days) {
        indicators.map { ind ->
            val num = IntArray(4); val den = IntArray(4)
            for ((k, d) in days) {
                val date = runCatching { LocalDate.parse(k) }.getOrNull() ?: continue
                val cd = Cycle.cycleDayOf(date, info.starts) ?: continue
                if (!ind.denom(d)) continue
                val ph = Cycle.phase(cd, info.avg)
                den[ph]++; if (ind.num(d)) num[ph]++
            }
            Triple(ind.name, num, den)
        }.filter { it.third.sum() >= 5 }
    }
    if (rows.isEmpty()) { Text("Servono un po' più di dati per vedere le differenze tra le fasi."); return }
    Row {
        Spacer(Modifier.weight(1.6f))
        Cycle.phaseShort.forEach { Text(it, Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold) }
    }
    val base = MaterialTheme.colorScheme.primary
    for ((name, num, den) in rows) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(name, Modifier.weight(1.6f), fontSize = 13.sp)
        for (p in 0 until 4) {
            val pct = if (den[p] > 0) num[p] * 100 / den[p] else -1
            Box(Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(8.dp))
                .background(if (pct >= 0) base.copy(alpha = 0.08f + 0.6f * pct / 100f) else Color.Transparent)
                .padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(if (pct >= 0) "$pct%" else "–", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    Text("Percentuale dei giorni registrati in cui c'era quel sintomo, per fase del ciclo (stimata dalla tua durata media).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TriggerTable(days: Map<String, Day>) {
    val res = remember(days) {
        var flare = 0; var calm = 0
        val inFlare = mutableMapOf<String, Int>(); val inCalm = mutableMapOf<String, Int>()
        for ((k, d) in days) {
            if (!Cycle.skinLogged(d)) continue
            val date = runCatching { LocalDate.parse(k) }.getOrNull() ?: continue
            val (r, s) = Cycle.skinScores(d)
            val trig = (d["triggers"] ?: emptySet()) + (days[date.minusDays(1).toString()]?.get("triggers") ?: emptySet())
            if (r + s >= 3) { flare++; trig.forEach { inFlare[it] = (inFlare[it] ?: 0) + 1 } }
            else { calm++; trig.forEach { inCalm[it] = (inCalm[it] ?: 0) + 1 } }
        }
        Triple(flare, calm, inFlare.entries.sortedByDescending { it.value }.take(8).map { e ->
            Triple(Catalog.label("triggers", e.key), e.value * 100 / flare.coerceAtLeast(1), (inCalm[e.key] ?: 0) * 100 / calm.coerceAtLeast(1))
        })
    }
    val (flare, _, list) = res
    if (flare < 3 || list.isEmpty()) {
        Text("Quando avrai qualche giorno di sfogo con i trigger segnati, qui vedrai quali compaiono più spesso (lo stesso giorno o il giorno prima).",
            style = MaterialTheme.typography.bodyMedium); return
    }
    Row { Spacer(Modifier.weight(2f)); Text("sfogo", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center); Text("giorni calmi", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center) }
    for ((name, a, b) in list) Row(Modifier.padding(vertical = 2.dp)) {
        Text(name, Modifier.weight(2f), fontSize = 13.sp)
        Text("$a%", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = RosaceaColor)
        Text("$b%", Modifier.weight(1f), textAlign = TextAlign.Center)
    }
    Text("Sfogo = somma dei livelli dei sintomi almeno 3. È un'associazione, non una prova che sia la causa.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
