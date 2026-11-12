package it.saranave.ciclo.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Riepilogo in PDF da mostrare a ginecologa o dermatologa. */
object Report {
    private val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)

    fun build(ctx: Context, months: Int = 3): File {
        val days = Store.snapshot(ctx)
        val info = Cycle.compute(days)
        val today = LocalDate.now()
        val from = today.minusMonths(months.toLong())
        val recent = days.filterKeys { k ->
            runCatching { LocalDate.parse(k) }.getOrNull()?.let { !it.isBefore(from) && !it.isAfter(today) } == true
        }
        val L = mutableListOf<Pair<Int, String>>() // 0 testo, 1 titoletto, 2 titolo

        L += 2 to "Riepilogo ciclo e pelle"
        L += 0 to "Creato il ${today.format(fmt)} · periodo analizzato: ultimi $months mesi"
        L += 0 to ""

        L += 1 to "Ciclo"
        if (info.starts.isEmpty()) L += 0 to "Nessun ciclo registrato."
        else {
            info.avg?.let { L += 0 to "Durata media (ultimi 6 cicli): $it giorni (min ${info.minL}, max ${info.maxL})" }
            info.lastStart?.let { L += 0 to "Ultimo inizio: ${it.format(fmt)} · oggi è il giorno ${info.dayOfCycle}" }
            info.nextStart?.let { L += 0 to "Prossimo previsto: ${it.format(fmt)} (finestra ${info.nextEarliest?.format(fmt)} – ${info.nextLatest?.format(fmt)})" }
            L += 0 to "Ultimi cicli:"
            val n = info.starts.size
            for (i in (n - 1) downTo maxOf(0, n - 8)) {
                val len = info.lengths.getOrNull(i)?.let { "$it gg" } ?: "in corso"
                L += 0 to "   • ${info.starts[i].format(fmt)} — durata $len, mestruazioni ${info.bleedDays[i]} gg"
            }
        }
        L += 0 to ""

        L += 1 to "Da segnalare"
        val al = Cycle.alerts(days, info, today)
        if (al.isEmpty()) L += 0 to "Nessun segnale particolare." else al.forEach { L += 0 to "   • ${it.title}: ${it.detail}" }
        L += 0 to ""

        L += 1 to "Pelle (ultimi $months mesi)"
        val skinDays = recent.values.filter { Cycle.skinLogged(it) }
        L += 0 to "Giorni registrati: ${skinDays.size} · senza sintomi: ${skinDays.count { it[Catalog.SKIN].isNullOrEmpty() }}"
        val sym = mutableMapOf<String, MutableList<Int>>()
        recent.values.forEach { d -> d[Catalog.SKIN]?.forEach { v -> val (k, l) = Catalog.splitLevel(v); sym.getOrPut(k) { mutableListOf() } += l } }
        if (sym.isNotEmpty()) {
            L += 0 to "Sintomi (giorni · livello medio 1-3):"
            sym.entries.sortedByDescending { it.value.size }.forEach { (k, ls) ->
                val tag = when (k) { in Catalog.rosaceaKeys -> " [rosacea]"; in Catalog.dsKeys -> " [dermatite seb.]"; else -> "" }
                L += 0 to "   • ${Catalog.label(Catalog.SKIN, k)}$tag: ${ls.size} gg · ${"%.1f".format(Locale.ITALIAN, ls.average())}"
            }
        }
        listOf(Catalog.zones, Catalog.triggers, Catalog.therapy).forEach { c -> counts(recent, c)?.let { L += 0 to it } }
        L += 0 to ""

        L += 1 to "Altri sintomi (giorni nel periodo)"
        listOf(Catalog.period, Catalog.spotting, Catalog.pain, Catalog.pms, Catalog.feelings, Catalog.energy, Catalog.sleep, Catalog.digestion, Catalog.meds)
            .forEach { c -> counts(recent, c)?.let { L += 0 to it } }
        L += 0 to ""

        val events = days.filter { it.value[Catalog.EVENT]?.isNotEmpty() == true }.toSortedMap()
        if (events.isNotEmpty()) {
            L += 1 to "Visite e cambi di terapia"
            events.entries.toList().takeLast(15).forEach { (d, v) ->
                L += 0 to "   • ${LocalDate.parse(d).format(fmt)}: ${v[Catalog.EVENT]!!.joinToString("; ")}"
            }
        }
        return render(ctx, L, today)
    }

    private fun counts(recent: Map<String, Day>, c: Cat): String? {
        val m = mutableMapOf<String, Int>()
        recent.values.forEach { d -> d[c.key]?.forEach { v -> val k = Catalog.splitLevel(v).first; m[k] = (m[k] ?: 0) + 1 } }
        if (m.isEmpty()) return null
        return "${c.title}: " + m.entries.sortedByDescending { it.value }.joinToString(", ") { "${Catalog.label(c.key, it.key)} ${it.value}" }
    }

    private fun render(ctx: Context, lines: List<Pair<Int, String>>, today: LocalDate): File {
        val doc = PdfDocument()
        val w = 595; val h = 842; val m = 44f
        val body = Paint().apply { textSize = 10.5f; isAntiAlias = true }
        val head = Paint().apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; color = 0xFF8E2450.toInt() }
        val title = Paint().apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        var y = m
        fun newPage() {
            doc.finishPage(page); pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create()); y = m
        }
        for ((style, text) in lines) {
            val p = when (style) { 2 -> title; 1 -> head; else -> body }
            val lh = p.textSize * 1.45f
            if (style == 1) y += 4f
            if (text.isEmpty()) { y += lh / 2; continue }
            var rest = text
            while (rest.isNotEmpty()) {
                if (y + lh > h - m) newPage()
                val n = p.breakText(rest, true, w - 2 * m, null).coerceAtLeast(1)
                var cut = n
                if (n < rest.length) { val sp = rest.lastIndexOf(' ', n); if (sp > 0) cut = sp }
                y += lh
                page.canvas.drawText(rest.substring(0, cut), m, y, p)
                rest = rest.substring(cut).trimStart()
            }
        }
        doc.finishPage(page)
        val dir = File(ctx.cacheDir, "report").apply { mkdirs() }
        val f = File(dir, "riepilogo-ciclo-$today.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }
}
