package it.saranave.ciclo.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.saranave.ciclo.data.Backup
import it.saranave.ciclo.data.Importer
import it.saranave.ciclo.data.Store
import it.saranave.ciclo.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)
private fun nice(d: String?) = d?.let { runCatching { LocalDate.parse(it).format(dFmt) }.getOrNull() } ?: "–"

@Composable
private fun Step(n: Int, title: String, done: Boolean = false, content: @Composable () -> Unit) {
    SectionCard("${if (done) "✅" else "$n."}  $title") { content() }
}

@Composable
private fun SummaryText(s: Backup.Summary) {
    Text("• ${s.days} giorni (dal ${nice(s.first)} al ${nice(s.last)})")
    Text("• ${s.photos} foto della pelle")
    Text(if (s.settings) "• impostazioni (voci visibili, promemoria)" else "• impostazioni: non presenti")
}

/** Telefono vecchio: crea il file e mettilo al sicuro. */
@Composable
fun SendWizard(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var made by remember { mutableStateOf<Pair<File, Backup.Summary>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var savedMsg by remember { mutableStateOf<String?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        val f = made?.first
        if (uri != null && f != null) savedMsg = runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }; "Salvato ✓"
        }.getOrElse { "Non è stato possibile salvarlo lì" }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row { TextButton(onClick = onBack) { Text("◀ Indietro") } }
        Text("📦 Trasferisci su un altro telefono", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Tre passi. Il file contiene tutto e resta tuo: non passa da nessun server.", style = MaterialTheme.typography.bodyMedium)

        Step(1, "Crea il file di trasferimento", done = made != null) {
            if (made == null) {
                Button(enabled = !busy, onClick = {
                    busy = true; err = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { Backup.writeToCache(ctx) } }
                        busy = false
                        r.onSuccess { made = it }.onFailure { err = "Qualcosa è andato storto, riprova." }
                    }
                }) { Text(if (busy) "Preparo…" else "Crea il file") }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } else {
                Text("Ho ricontrollato il file, dentro c'è:", fontWeight = FontWeight.Medium)
                SummaryText(made!!.second)
            }
        }

        if (made != null) Step(2, "Mettilo al sicuro", done = savedMsg == "Salvato ✓") {
            Text("Scegli dove: Google Drive, mandalo a te stessa su WhatsApp/Telegram, per email o su una chiavetta. L'importante è poterlo aprire dal telefono nuovo.",
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {
                val uri = PhotoFiles.uri(ctx, made!!.first)
                val send = Intent(Intent.ACTION_SEND).setType("application/zip")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(Intent.createChooser(send, "Dove lo mando?"))
            }, modifier = Modifier.fillMaxWidth()) { Text("Condividi (Drive, WhatsApp, email…)") }
            OutlinedButton(onClick = { saver.launch(made!!.first.name) }, modifier = Modifier.fillMaxWidth()) { Text("Oppure salvalo in una cartella") }
            savedMsg?.let { Text(it) }
        }

        if (made != null) Step(3, "Sul telefono nuovo") {
            Text("1. Scarica e installa l'ultima versione di Ciclo:")
            TextButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/saranave000-design/ciclo/releases/latest")))
            }) { Text("github.com/saranave000-design/ciclo/releases") }
            Text("2. Apri Ciclo → Altro → Ricevi da un altro telefono")
            Text("3. Scegli il file che hai salvato al passo 2 e conferma")
            Text("4. Rimetti il widget sulla home e, se lo usi, ricollega Claude (la chiave non viene copiata, per sicurezza)")
            Text("Tieni il vecchio telefono finché non hai controllato che sul nuovo ci sia tutto.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Telefono nuovo: scegli il file, controlla, importa. Accetta anche backup vecchi ed export di Clue. */
@Composable
fun ReceiveWizard(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<File?>(null) }
    var kind by remember { mutableStateOf("") } // zip, backup, clue
    var summary by remember { mutableStateOf<Backup.Summary?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val currentDays = remember { Store.snapshot(ctx).size }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            err = null; result = null
            withContext(Dispatchers.IO) {
                runCatching {
                    val f = Backup.copyToCache(ctx, uri)
                    if (Backup.isZip(f)) {
                        val s = Backup.summary(f) ?: error("zip")
                        file = f; kind = "zip"; summary = s
                    } else {
                        val text = f.readText()
                        if (Store.isBackup(text)) {
                            val n = org.json.JSONObject(text).getJSONObject("days").length()
                            file = f; kind = "backup"; summary = Backup.Summary(n, 0, false, null, null)
                        } else {
                            val n = Importer.fromClue(text).size
                            if (n == 0) error("vuoto")
                            file = f; kind = "clue"; summary = Backup.Summary(n, 0, false, null, null)
                        }
                    }
                }.onFailure { err = "Questo file non sembra un backup di Ciclo né un export di Clue 😕" }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row { TextButton(onClick = onBack) { Text("◀ Indietro") } }
        Text("📥 Ricevi da un altro telefono", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        Step(1, "Scegli il file", done = summary != null) {
            Text("Il file \"ciclo-trasferimento-….zip\" creato sul vecchio telefono (va bene anche un backup o l'export di Clue).",
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text(if (summary == null) "Scegli il file" else "Scegli un altro file") }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        val s = summary
        if (s != null && result == null) Step(2, "Controlla e importa") {
            Text(when (kind) { "zip" -> "Trasferimento completo:"; "backup" -> "Backup (solo dati, senza foto):"; else -> "Export di Clue:" }, fontWeight = FontWeight.Medium)
            if (kind == "zip") SummaryText(s) else Text("• ${s.days} giorni")
            if (currentDays > 0 && kind != "clue") Text(
                "Su questo telefono ci sono già $currentDays giorni: verranno sostituiti (ne tengo una copia di sicurezza).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            )
            if (kind == "clue") Text("I dati di Clue si aggiungono a quelli che hai già.", style = MaterialTheme.typography.bodySmall)
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    result = withContext(Dispatchers.IO) {
                        runCatching {
                            val f = file!!
                            when (kind) {
                                "zip" -> { Backup.restore(ctx, f); "Tutto importato ✓" }
                                "backup" -> { Store.restore(ctx, f.readText()); "Backup importato ✓" }
                                else -> { Store.merge(ctx, Importer.fromClue(f.readText()), replace = false); "Dati di Clue importati ✓" }
                            }
                        }.getOrElse { "Importazione non riuscita: i tuoi dati non sono stati toccati." }
                    }
                    busy = false
                    Widgets.refreshAsync(ctx)
                }
            }) { Text(if (busy) "Importo…" else "Importa") }
        }

        result?.let { r ->
            Step(3, r, done = r.endsWith("✓")) {
                val now = Store.snapshot(ctx).size
                Text("Ora nell'app ci sono $now giorni.")
                Text("Ultimi passi: rimetti il widget sulla home (tieni premuto → Widget → Ciclo) e, se lo usi, ricollega Claude da Altro.")
                Button(onClick = onBack) { Text("Fatto") }
            }
        }
    }
}
