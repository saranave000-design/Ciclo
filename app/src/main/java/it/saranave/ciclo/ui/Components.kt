package it.saranave.ciclo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.saranave.ciclo.data.Cat
import it.saranave.ciclo.data.Catalog
import it.saranave.ciclo.data.Day
import it.saranave.ciclo.data.Prefs
import it.saranave.ciclo.data.Store
import it.saranave.ciclo.widget.Widgets

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/** Editor di una voce: chip normali, a scelta singola o con livello. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatEditor(
    cat: Cat, date: String, day: Day, showTitle: Boolean = true,
    onEdit: (() -> Unit)? = null, handle: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val sel = day[cat.key] ?: emptySet()
    val changed = { Widgets.refreshAsync(ctx) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showTitle) Row(verticalAlignment = Alignment.CenterVertically) {
            Row(handle.weight(1f).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onEdit != null) { Text("☰", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(10.dp)) }
                Text("${cat.emoji}  ${cat.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            if (onEdit != null) TextButton(onClick = onEdit) { Text("✏️ Modifica", style = MaterialTheme.typography.labelMedium) }
        }
        if (cat.leveled) Text(
            "Tocca più volte: lieve → medio → forte → togli",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val opts = Prefs.visibleOptions(ctx, cat, sel)
        val groups = opts.groupBy { it.group }
        for ((g, list) in groups) {
            if (g != null) Text(g, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (opt in list) {
                    if (cat.leveled) {
                        val lvl = sel.firstOrNull { Catalog.splitLevel(it).first == opt.key }?.let { Catalog.splitLevel(it).second } ?: 0
                        FilterChip(
                            selected = lvl > 0,
                            onClick = { Store.cycleLevel(ctx, date, cat.key, opt.key); changed() },
                            label = { Text(if (lvl > 0) "${opt.label} · ${Catalog.levelNames[lvl.coerceIn(1, 3)]}" else opt.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LevelColors[lvl.coerceIn(1, 3)].copy(alpha = 0.40f),
                            ),
                        )
                    } else {
                        FilterChip(
                            selected = opt.key in sel,
                            onClick = { Store.toggle(ctx, date, cat, opt.key); changed() },
                            label = { Text(opt.label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SkinOkChip(date: String, day: Day) {
    val ctx = LocalContext.current
    val ok = day[Catalog.SKIN_OK]?.isNotEmpty() == true
    FilterChip(
        selected = ok,
        onClick = { Store.setSkinOk(ctx, date, !ok); Widgets.refreshAsync(ctx) },
        label = { Text("✨ Pelle ok, nessun sintomo") },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LevelColors[0].copy(alpha = 0.35f)),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsEditor(date: String, day: Day) {
    val ctx = LocalContext.current
    val sel = day[Catalog.TAGS] ?: emptySet()
    val known = remember(sel) { (Store.allValues(ctx, Catalog.TAGS) + sel).sorted() }
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in known) FilterChip(
                selected = t in sel,
                onClick = { Store.set(ctx, date, Catalog.TAGS, if (t in sel) sel - t else sel + t) },
                label = { Text(t) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Nuovo tag…") }, singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(enabled = text.isNotBlank(), onClick = {
                Store.set(ctx, date, Catalog.TAGS, sel + text.trim()); text = ""
            }) { Text("Aggiungi") }
        }
    }
}

/** Testo libero salvato in una voce (appunti, visite). multi = si possono aggiungere più righe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextListEditor(date: String, day: Day, catKey: String, placeholder: String) {
    val ctx = LocalContext.current
    val items = day[catKey] ?: emptySet()
    var text by remember(date) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (item in items.sorted()) {
            InputChip(selected = false, onClick = {}, label = { Text(item) }, trailingIcon = {
                TextButton(onClick = { Store.set(ctx, date, catKey, items - item) }) { Text("✕") }
            })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text(placeholder) })
            Spacer(Modifier.width(8.dp))
            Button(enabled = text.isNotBlank(), onClick = { Store.set(ctx, date, catKey, items + text.trim()); text = "" }) { Text("Salva") }
        }
    }
}

@Composable
fun WeightEditor(date: String, day: Day) {
    val ctx = LocalContext.current
    val cur = day[Catalog.WEIGHT]?.firstOrNull() ?: ""
    var text by remember(date, cur) { mutableStateOf(cur) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            text, { text = it.replace(',', '.') }, Modifier.weight(1f),
            label = { Text("Peso (kg)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            val v = text.trim().toDoubleOrNull()
            Store.set(ctx, date, Catalog.WEIGHT, if (v != null) setOf(v.toString()) else emptySet())
        }) { Text("Salva") }
    }
}

@Composable
fun SmallGap() = Spacer(Modifier.height(4.dp))
