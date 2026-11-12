package it.saranave.ciclo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val ds = date.toString()
    val day = remember(rev, ds) { Store.day(ctx, ds) }
    val info = remember(rev) { Cycle.compute(Store.snapshot(ctx)) }
    val today = LocalDate.now()
    val cycleDay = Cycle.cycleDayOf(date, info.starts)

    editing?.let { k -> Catalog.cat(k)?.let { OptionsEditor(it) { editing = null }; return } }

    val cats = remember(prefsRev, day) {
        Prefs.orderedCats(ctx).filter { Prefs.catVisible(ctx, it) || day[it.key]?.isNotEmpty() == true }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        if (date != today) TextButton(onClick = { onDate(today) }) { Text("Torna a oggi") }
        Text("Tieni premuto sul nome di una voce e trascinala per cambiare l'ordine.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        ReorderColumn(
            items = cats, keyOf = { it.key },
            onReordered = { keys ->
                val full = Prefs.orderedCats(ctx).map { it.key }.toMutableList()
                val set = keys.toSet()
                val pos = full.indices.filter { full[it] in set }
                pos.forEachIndexed { i, p -> full[p] = keys[i] }
                Prefs.setCatOrder(ctx, full)
            },
        ) { c, dragging, handle ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (dragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CatEditor(c, ds, day, onEdit = { editing = c.key }, handle = handle)
                    if (c.key == Catalog.SKIN) SkinOkChip(ds, day)
                }
            }
        }

        SectionCard("🏷️ Tag") { TagsEditor(ds, day) }
        SectionCard("📝 Appunti") { TextListEditor(ds, day, Catalog.NOTE, "Scrivi un appunto…") }
        SectionCard("🩺 Visite e cambi di terapia") { TextListEditor(ds, day, Catalog.EVENT, "Es. iniziato Efracea, visita dermatologa…") }
        SectionCard("📸 Foto della pelle") { PhotoStrip(ds, day) }
        SectionCard("⚖️ Peso") { WeightEditor(ds, day) }
    }
}
