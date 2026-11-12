package it.saranave.ciclo.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.saranave.ciclo.Reminder
import it.saranave.ciclo.data.Catalog
import it.saranave.ciclo.data.Importer
import it.saranave.ciclo.data.Prefs
import it.saranave.ciclo.data.Store
import it.saranave.ciclo.data.Sync
import it.saranave.ciclo.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsRev by Prefs.revision.collectAsState()
    val rev by Store.revision.collectAsState()
    var msg by remember { mutableStateOf<String?>(null) }
    var linkDialog by remember { mutableStateOf(false) }
    var restoreText by remember { mutableStateOf<String?>(null) }
    var openCat by remember { mutableStateOf<String?>(null) }
    val linked = remember(prefsRev) { Prefs.token(ctx) != null }
    val dayCount = remember(rev) { Store.snapshot(ctx).size }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            msg = withContext(Dispatchers.IO) {
                runCatching {
                    val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
                    if (Store.isBackup(text)) { restoreText = text; null }
                    else {
                        val data = Importer.fromClue(text)
                        val n = Store.merge(ctx, data, replace = false)
                        "Importati ${data.size} giorni da Clue ($n voci nuove) ✓"
                    }
                }.getOrElse { "Non riesco a leggere questo file 😕" }
            }
            Widgets.refreshAsync(ctx)
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        msg = runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Store.exportJson(ctx).toByteArray()) }
            "Backup salvato ✓"
        }.getOrElse { "Backup non riuscito" }
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        msg?.let { m -> item { SectionCard("ℹ️") { Text(m); TextButton(onClick = { msg = null }) { Text("Ok") } } } }

        item {
            SectionCard("🤝 Collegamento con Claude") {
                Text(
                    if (linked) "Collegata ✓ — quando Claude ti carica dei dati, l'app li prende da sola all'apertura." +
                        (Prefs.lastSync(ctx).takeIf { it.isNotEmpty() }?.let { "\nUltimo controllo: $it" } ?: "")
                    else "Collega l'app così Claude può caricarti i dati (anche lo storico di Clue) senza file né password.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (linked) Button(onClick = {
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Sync.pull(ctx) }
                            msg = when (r) {
                                is Sync.Result.Ok -> if (r.applied == 0) "Niente di nuovo da Claude." else "Arrivati ${r.applied} pacchetti da Claude (${r.changes} voci) ✓"
                                is Sync.Result.Error -> r.msg
                                Sync.Result.NotLinked -> "App non collegata"
                            }
                            Widgets.refreshAsync(ctx)
                        }
                    }) { Text("Controlla adesso") }
                    OutlinedButton(onClick = { linkDialog = true }) { Text(if (linked) "Cambia chiave" else "Collega") }
                }
            }
        }

        item {
            SectionCard("🔔 Promemoria") {
                val on = Prefs.reminderOn(ctx); val hour = Prefs.reminderHour(ctx)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Promemoria serale")
                        Text("Con i pulsanti Pelle e Umore dentro la notifica", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = on, onCheckedChange = { v ->
                        if (v && Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        Prefs.setReminder(ctx, v, hour); Reminder.schedule(ctx)
                    })
                }
                if (on) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ora", Modifier.weight(1f))
                    TextButton(onClick = { Prefs.setReminder(ctx, true, (hour + 23) % 24); Reminder.schedule(ctx) }) { Text("−") }
                    Text("%02d:00".format(hour), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { Prefs.setReminder(ctx, true, (hour + 1) % 24); Reminder.schedule(ctx) }) { Text("+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Avviso prima del ciclo")
                        Text("Due giorni prima della data prevista", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = Prefs.periodAlertOn(ctx), onCheckedChange = { v ->
                        if (v && Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        Prefs.setPeriodAlert(ctx, v); Reminder.schedule(ctx)
                    })
                }
                Text("Su Xiaomi: se le notifiche non arrivano, attiva \"Avvio automatico\" per Ciclo e togli le restrizioni batteria.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SectionCard("👁️ Cosa vedere nell'app") {
                Text("Scegli le voci e, toccando il nome, le singole opzioni.", style = MaterialTheme.typography.bodySmall)
                for ((group, cats) in Catalog.groups) {
                    Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                    for (c in cats) {
                        val vis = remember(prefsRev) { Prefs.catVisible(ctx, c) }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                            .clickable { openCat = if (openCat == c.key) null else c.key }) {
                            Text("${c.emoji}  ${c.title}", Modifier.weight(1f))
                            Text(if (openCat == c.key) "▲" else "▼", fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = vis, onCheckedChange = { Prefs.setCatVisible(ctx, c, it) })
                        }
                        if (openCat == c.key) Column(Modifier.padding(start = 16.dp)) {
                            for (o in c.options) Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = remember(prefsRev) { Prefs.optVisible(ctx, c, o) },
                                    onCheckedChange = { Prefs.setOptVisible(ctx, c, o, it) })
                                Text(o.label)
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard("💾 Dati") {
                Text("Giorni registrati: $dayCount · tutto resta solo sul telefono.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth()) { Text("Importa file (Clue o backup)") }
                OutlinedButton(onClick = { exporter.launch("ciclo-backup-${LocalDate.now()}.json") },
                    modifier = Modifier.fillMaxWidth()) { Text("Salva un backup") }
                Text("Per ripristinare un backup, importalo qui sopra.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard("📱 Widget") {
                Text("Tieni premuto su uno spazio vuoto della home → Widget → Ciclo. È pensato 4×2, ma puoi ridimensionarlo.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (linkDialog) LinkDialog { linkDialog = false }

    restoreText?.let { text ->
        AlertDialog(
            onDismissRequest = { restoreText = null },
            title = { Text("Ripristinare il backup?") },
            text = { Text("I dati attuali verranno sostituiti da quelli del backup (una copia di sicurezza resta salvata).") },
            confirmButton = {
                Button(onClick = {
                    msg = runCatching { Store.restore(ctx, text); "Backup ripristinato ✓" }.getOrElse { "Backup non valido" }
                    restoreText = null; Widgets.refreshAsync(ctx)
                }) { Text("Ripristina") }
            },
            dismissButton = { TextButton(onClick = { restoreText = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun LinkDialog(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Collega con Claude") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Come per Foto Assistant, serve una chiave GitHub:", fontSize = 13.sp)
                Text("1. Apri la pagina qui sotto (accedi a GitHub)", fontSize = 13.sp)
                Text("2. Nome: Ciclo · Scadenza: la più lunga", fontSize = 13.sp)
                Text("3. Repository access → Only select → ciclo", fontSize = 13.sp)
                Text("4. Permissions → Contents → Read-only", fontSize = 13.sp)
                Text("5. Generate token → copia e incolla qui", fontSize = 13.sp)
                TextButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new")))
                }) { Text("Apri GitHub") }
                OutlinedTextField(value = token, onValueChange = { token = it }, placeholder = { Text("github_pat_…") }, singleLine = true)
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(enabled = token.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Sync.test(token.trim()) }
                    if (ok) {
                        Prefs.setToken(ctx, token)
                        withContext(Dispatchers.IO) { Sync.pull(ctx) }
                        Widgets.refreshAsync(ctx)
                        onDone()
                    } else { err = "La chiave non funziona: controlla di aver scelto la repo ciclo e Contents → Read-only."; busy = false }
                }
            }) { Text(if (busy) "Controllo…" else "Collega") }
        },
        dismissButton = {
            Row {
                if (Prefs.token(ctx) != null) TextButton(onClick = { Prefs.setToken(ctx, null); onDone() }) { Text("Scollega") }
                TextButton(onClick = onDone) { Text("Annulla") }
            }
        },
    )
}
