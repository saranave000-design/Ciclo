package it.saranave.ciclo.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Backup completo in un unico .zip:
 *   ciclo.json        tutti i giorni
 *   impostazioni.json voci visibili, promemoria (non la chiave GitHub)
 *   foto/…            foto della pelle
 *   info.json         riepilogo, per controllare che ci sia tutto
 */
object Backup {
    data class Summary(val days: Int, val photos: Int, val settings: Boolean, val first: String?, val last: String?)

    private fun photoDir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }

    fun write(ctx: Context, out: OutputStream): Summary {
        val days = Store.snapshot(ctx)
        val photos = photoDir(ctx).listFiles()?.filter { it.isFile } ?: emptyList()
        val s = Summary(days.size, photos.size, true, days.keys.minOrNull(), days.keys.maxOrNull())
        ZipOutputStream(out.buffered()).use { z ->
            fun text(name: String, t: String) { z.putNextEntry(ZipEntry(name)); z.write(t.toByteArray()); z.closeEntry() }
            text("info.json", JSONObject().put("app", "ciclo").put("tipo", "backup-completo").put("creato", LocalDate.now().toString())
                .put("giorni", s.days).put("foto", s.photos).put("dal", s.first ?: "").put("al", s.last ?: "").toString(1))
            text("ciclo.json", Store.exportJson(ctx))
            text("impostazioni.json", Prefs.dumpSettings(ctx).toString(1))
            for (f in photos) {
                z.putNextEntry(ZipEntry("foto/${f.name}")); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
        return s
    }

    /** Crea il file nella cache (per condividerlo) e lo ricontrolla. */
    fun writeToCache(ctx: Context): Pair<File, Summary> {
        val dir = File(ctx.cacheDir, "report").apply { mkdirs() }
        dir.listFiles()?.filter { it.name.startsWith("ciclo-trasferimento") }?.forEach { it.delete() }
        val f = File(dir, "ciclo-trasferimento-${LocalDate.now()}.zip")
        f.outputStream().use { write(ctx, it) }
        val check = summary(f) ?: error("controllo fallito")
        return f to check
    }

    /** Copia un file scelto dall'utente nella cache, per poterlo leggere con calma. */
    fun copyToCache(ctx: Context, uri: Uri): File {
        val f = File(ctx.cacheDir, "in-arrivo.bin")
        ctx.contentResolver.openInputStream(uri)!!.use { inp -> f.outputStream().use { inp.copyTo(it) } }
        return f
    }

    fun isZip(f: File): Boolean = f.inputStream().use { val b = ByteArray(2); it.read(b) == 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() }

    fun summary(f: File): Summary? = runCatching {
        ZipFile(f).use { z ->
            val data = z.getEntry("ciclo.json") ?: return null
            val text = z.getInputStream(data).bufferedReader().readText()
            val days = JSONObject(text).getJSONObject("days")
            val keys = days.keys().asSequence().toList()
            val photos = z.entries().asSequence().count { it.name.startsWith("foto/") && !it.isDirectory }
            Summary(keys.size, photos, z.getEntry("impostazioni.json") != null, keys.minOrNull(), keys.maxOrNull())
        }
    }.getOrNull()

    /** Ripristina tutto dal .zip (i dati attuali vengono salvati prima in una copia di sicurezza). */
    fun restore(ctx: Context, f: File): Summary {
        val s = summary(f) ?: error("file non valido")
        ZipFile(f).use { z ->
            Store.restore(ctx, z.getInputStream(z.getEntry("ciclo.json")).bufferedReader().readText())
            z.getEntry("impostazioni.json")?.let { Prefs.loadSettings(ctx, JSONObject(z.getInputStream(it).bufferedReader().readText())) }
            val dir = photoDir(ctx)
            for (e in z.entries()) {
                if (e.isDirectory || !e.name.startsWith("foto/")) continue
                val name = File(e.name).name
                if (name.isEmpty() || name.contains("..")) continue
                z.getInputStream(e).use { inp -> File(dir, name).outputStream().use { inp.copyTo(it) } }
            }
        }
        return s
    }

    /**
     * Tabella leggibile da Excel e da altre app: una riga per ogni cosa segnata.
     * Separatore ";" (Excel italiano), UTF-8 con BOM.
     */
    fun writeCsv(ctx: Context, out: OutputStream): Int {
        val days = Store.snapshot(ctx)
        val info = Cycle.compute(days)
        var rows = 0
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿")
            w.write("data;giorno_ciclo;categoria;voce;livello;chiave_categoria;chiave_voce\n")
            fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
            for (date in days.keys.sorted()) {
                val cd = runCatching { Cycle.cycleDayOf(LocalDate.parse(date), info.starts) }.getOrNull()?.toString() ?: ""
                val d = days.getValue(date)
                for ((cat, vals) in d.toSortedMap()) for (v in vals.sorted()) {
                    val c = Catalog.cat(cat)
                    val catName = c?.title ?: when (cat) {
                        Catalog.TAGS -> "Tag"; Catalog.NOTE -> "Appunti"; Catalog.EVENT -> "Visite e terapie"
                        Catalog.WEIGHT -> "Peso (kg)"; Catalog.PHOTO -> "Foto"; Catalog.SKIN_OK -> "Pelle ok"; else -> cat
                    }
                    val (k, l) = if (c?.leveled == true) Catalog.splitLevel(v) else v to 0
                    val label = if (c != null) Catalog.label(cat, k) else k
                    val lvl = if (c?.leveled == true) Catalog.levelNames[l.coerceIn(1, 3)] else ""
                    w.write(listOf(date, cd, q(catName), q(label), lvl, cat, q(k)).joinToString(";") + "\n")
                    rows++
                }
            }
        }
        return rows
    }
}
