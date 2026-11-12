package it.saranave.ciclo.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import it.saranave.ciclo.CameraActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import it.saranave.ciclo.data.Catalog
import it.saranave.ciclo.data.Day
import it.saranave.ciclo.data.Store
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object PhotoFiles {
    fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }
    fun file(ctx: Context, name: String) = File(dir(ctx), name)
    fun newName(date: String) = "${date}_${System.currentTimeMillis()}.jpg"
    fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
}

@Composable
fun PhotoStrip(date: String, day: Day) {
    val ctx = LocalContext.current
    val photos = (day[Catalog.PHOTO] ?: emptySet()).sorted()
    var pendingName by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val n = pendingName
        val f = n?.let { PhotoFiles.file(ctx, it) }
        if (res.resultCode == Activity.RESULT_OK && n != null && f != null && f.length() > 0)
            Store.set(ctx, date, Catalog.PHOTO, Store.get(ctx, date, Catalog.PHOTO) + n)
        else f?.delete()
        pendingName = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val n = PhotoFiles.newName(date)
            runCatching {
                ctx.contentResolver.openInputStream(uri)!!.use { inp -> PhotoFiles.file(ctx, n).outputStream().use { inp.copyTo(it) } }
                Store.set(ctx, date, Catalog.PHOTO, Store.get(ctx, date, Catalog.PHOTO) + n)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (photos.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (p in photos) AsyncImage(
                model = PhotoFiles.file(ctx, p), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(92.dp).clip(RoundedCornerShape(14.dp)).clickable { open = p },
            )
        }
        Text(
            "Con \"Scatta con guida\" hai l'ovale per il viso e l'ultima foto in trasparenza, così gli scatti sono confrontabili.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val n = PhotoFiles.newName(date); pendingName = n
                val last = Store.allValues(ctx, Catalog.PHOTO).maxOrNull()?.let { PhotoFiles.file(ctx, it) }?.takeIf { it.exists() }
                camera.launch(
                    Intent(ctx, CameraActivity::class.java)
                        .putExtra("out", PhotoFiles.file(ctx, n).absolutePath)
                        .putExtra("ghost", last?.absolutePath),
                )
            }) { Text("📷 Scatta con guida") }
            OutlinedButton(onClick = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("🖼️ Dalla galleria") }
        }
    }

    open?.let { name ->
        AlertDialog(
            onDismissRequest = { open = null },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Chiudi") } },
            dismissButton = {
                TextButton(onClick = {
                    Store.set(ctx, date, Catalog.PHOTO, photos.toSet() - name)
                    PhotoFiles.file(ctx, name).delete(); open = null
                }) { Text("Elimina") }
            },
            text = {
                AsyncImage(model = PhotoFiles.file(ctx, name), contentDescription = null,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
            },
        )
    }
}

private val shortFmt = DateTimeFormatter.ofPattern("d MMM yy", Locale.ITALIAN)

/** Tutte le foto, dalla più recente: tocca due foto per confrontarle affiancate. */
@Composable
fun PhotoGallery(days: Map<String, Day>) {
    val ctx = LocalContext.current
    val all = remember(days) {
        days.flatMap { (d, v) -> (v[Catalog.PHOTO] ?: emptySet()).map { d to it } }.sortedByDescending { it.second }
    }
    val chosen = remember { mutableStateListOf<Pair<String, String>>() }
    var compare by remember { mutableStateOf(false) }
    if (all.isEmpty()) {
        Text("Ancora nessuna foto. Le aggiungi dalla pagina del giorno.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Text("Tocca due foto per confrontarle.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in all) Column {
            val sel = p in chosen
            AsyncImage(
                model = PhotoFiles.file(ctx, p.second), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(14.dp))
                    .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier)
                    .clickable {
                        if (sel) chosen.remove(p) else { if (chosen.size == 2) chosen.removeAt(0); chosen.add(p) }
                        if (chosen.size == 2) compare = true
                    },
            )
            Text(LocalDate.parse(p.first).format(shortFmt), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
        }
    }
    if (compare && chosen.size == 2) {
        AlertDialog(
            onDismissRequest = { compare = false; chosen.clear() },
            confirmButton = { TextButton(onClick = { compare = false; chosen.clear() }) { Text("Chiudi") } },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in chosen.sortedBy { it.second }) Column(Modifier.weight(1f)) {
                        Box {
                            AsyncImage(model = PhotoFiles.file(ctx, p.second), contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(10.dp)))
                        }
                        Text(LocalDate.parse(p.first).format(shortFmt), style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
        )
    }
}
