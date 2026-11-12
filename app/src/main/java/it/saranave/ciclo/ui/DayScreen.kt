package it.saranave.ciclo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.saranave.ciclo.data.Catalog
import it.saranave.ciclo.data.Cycle
import it.saranave.ciclo.data.Prefs
import it.saranave.ciclo.data.Store
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

@Composable
fun DayScreen(date: LocalDate, onDate: (LocalDate) -> Unit) {
    val ctx = LocalContext.current
    val rev by Store.revision.collectAsState()
    val prefsRev by Prefs.revision.collectAsState()
    val ds = date.toString()
    val day = remember(rev, ds) { Store.day(ctx, ds) }
    val info = remember(rev) { Cycle.compute(Store.snapshot(ctx)) }
    val today = LocalDate.now()
    val cycleDay = Cycle.cycleDayOf(date, info.starts)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onDate(date.minusDays(1)) }) { Text("◀", style = MaterialTheme.typography.titleLarge) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (date == today) "Oggi" else date.format(dayFmt).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    )
                    val sub = buildString {
                        if (date == today) append(date.format(dayFmt))
                        if (cycleDay != null) {
                            if (isNotEmpty()) append(" · ")
                            append("giorno $cycleDay del ciclo · fase ${Cycle.phaseNames[Cycle.phase(cycleDay, info.avg)].lowercase()}")
                        }
                    }
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onDate(date.plusDays(1)) }, enabled = date < today) {
                    Text("▶", style = MaterialTheme.typography.titleLarge)
                }
            }
            if (date != today) TextButton(onClick = { onDate(today) }, modifier = Modifier) { Text("Torna a oggi") }
        }
        for ((title, cats) in Catalog.groups) {
            val visible = cats.filter { Prefs.catVisible(ctx, it) || day[it.key]?.isNotEmpty() == true }
            if (visible.isEmpty()) continue
            item(key = "$title-$prefsRev") {
                SectionCard(title) {
                    if (title == "Pelle") SkinOkChip(ds, day)
                    visible.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        CatEditor(c, ds, day)
                    }
                }
            }
        }
        item { SectionCard("🏷️ Tag") { TagsEditor(ds, day) } }
        item { SectionCard("📝 Appunti") { TextListEditor(ds, day, Catalog.NOTE, "Scrivi un appunto…") } }
        item { SectionCard("🩺 Visite e cambi di terapia") { TextListEditor(ds, day, Catalog.EVENT, "Es. iniziato Efracea, visita dermatologa…") } }
        item { SectionCard("📸 Foto della pelle") { PhotoStrip(ds, day) } }
        item { SectionCard("⚖️ Peso") { WeightEditor(ds, day) } }
    }
}
