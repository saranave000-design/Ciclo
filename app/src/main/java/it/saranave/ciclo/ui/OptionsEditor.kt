package it.saranave.ciclo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.saranave.ciclo.data.Cat
import it.saranave.ciclo.data.Prefs
import it.saranave.ciclo.widget.Widgets

/** Modifica opzioni di una voce: aggiungi, elimina, sposta (tieni premuto e trascina). */
@Composable
fun OptionsEditor(cat: Cat, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val prefsRev by Prefs.revision.collectAsState()
    val opts = remember(prefsRev) { Prefs.orderedOptions(ctx, cat) }
    val hidden = remember(prefsRev) { Prefs.hiddenBuiltIns(ctx, cat) }
    val visible = remember(prefsRev) { Prefs.catVisible(ctx, cat) }
    var newText by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row { TextButton(onClick = onBack) { Text("◀ Fatto") } }
        Text("${cat.emoji}  ${cat.title}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mostra questa voce nella pagina del giorno", Modifier.weight(1f))
            Switch(checked = visible, onCheckedChange = { Prefs.setCatVisible(ctx, cat, it); Widgets.refreshAsync(ctx) })
        }
        Text("Tieni premuto su un'opzione e trascinala per spostarla. 🗑️ per toglierla dall'elenco.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)

        val groups = opts.groupBy { it.group }
        for ((g, list) in groups) {
            if (g != null) Text(g, style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.padding(top = 6.dp))
            ReorderColumn(
                items = list, keyOf = { it.key }, spacing = 6.dp,
                onReordered = { keys ->
                    val full = opts.map { it.key }.toMutableList()
                    val set = keys.toSet()
                    val pos = full.indices.filter { full[it] in set }
                    pos.forEachIndexed { i, p -> full[p] = keys[i] }
                    Prefs.setOptOrder(ctx, cat, full)
                },
            ) { o, dragging, handle ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (dragging) cs.primaryContainer else cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(handle.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("☰", color = cs.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(o.label)
                        }
                        TextButton(onClick = { Prefs.removeOption(ctx, cat, o); msg = "\"${o.label}\" tolta dall'elenco" }) { Text("🗑️") }
                    }
                }
            }
        }

        Text("➕ Aggiungi un'opzione", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newText, { newText = it }, Modifier.weight(1f), placeholder = { Text("Es. nuova crema, nuovo farmaco…") }, singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(enabled = newText.isNotBlank(), onClick = {
                val ok = Prefs.addCustom(ctx, cat, newText)
                msg = if (ok) "\"${newText.trim()}\" aggiunta ✓" else "C'è già un'opzione con questo nome"
                if (ok) newText = ""
            }) { Text("Aggiungi") }
        }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.secondary) }

        if (hidden.isNotEmpty()) {
            Text("Tolte dall'elenco", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
            for (o in hidden) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.label, Modifier.weight(1f), color = cs.onSurfaceVariant)
                TextButton(onClick = { Prefs.setOptVisible(ctx, cat, o, true) }) { Text("Rimetti") }
            }
        }
        Text("Togliere un'opzione non cancella niente: nei giorni passati in cui era segnata resta, e la ritrovi nello storico.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}
