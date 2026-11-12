package it.saranave.ciclo.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class CycleInfo(
    val starts: List<LocalDate>,
    /** durata in giorni di ogni ciclo completo (da un inizio al successivo) */
    val lengths: List<Int>,
    /** giorni di mestruazioni per ogni ciclo, stesso ordine di starts */
    val bleedDays: List<Int>,
    val avg: Int?,
    val minL: Int?,
    val maxL: Int?,
    val lastStart: LocalDate?,
    val dayOfCycle: Int?,
    val nextStart: LocalDate?,
    val nextEarliest: LocalDate?,
    val nextLatest: LocalDate?,
)

data class Alert(val title: String, val detail: String)

object Cycle {
    /** Due giorni di mestruazioni a più di 10 giorni di distanza = nuovo ciclo. */
    private const val GAP = 10

    fun periodDates(days: Map<String, Day>): List<LocalDate> =
        days.filter { it.value[Catalog.PERIOD]?.isNotEmpty() == true }.keys
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()

    fun compute(days: Map<String, Day>, today: LocalDate = LocalDate.now()): CycleInfo {
        val pd = periodDates(days)
        val starts = mutableListOf<LocalDate>()
        val bleed = mutableListOf<Int>()
        var prev: LocalDate? = null
        for (d in pd) {
            if (prev == null || ChronoUnit.DAYS.between(prev, d) > GAP) {
                starts += d; bleed += 1
            } else bleed[bleed.lastIndex] = ChronoUnit.DAYS.between(starts.last(), d).toInt() + 1
            prev = d
        }
        val lengths = starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
        val recent = lengths.filter { it in 15..60 }.takeLast(6)
        val avg = if (recent.isNotEmpty()) Math.round(recent.average()).toInt() else null
        val last = starts.lastOrNull { !it.isAfter(today) }
        val doc = last?.let { ChronoUnit.DAYS.between(it, today).toInt() + 1 }
        return CycleInfo(
            starts = starts, lengths = lengths, bleedDays = bleed,
            avg = avg, minL = recent.minOrNull(), maxL = recent.maxOrNull(),
            lastStart = last, dayOfCycle = doc,
            nextStart = if (last != null && avg != null) last.plusDays(avg.toLong()) else null,
            nextEarliest = if (last != null && recent.isNotEmpty()) last.plusDays(recent.min().toLong()) else null,
            nextLatest = if (last != null && recent.isNotEmpty()) last.plusDays(recent.max().toLong()) else null,
        )
    }

    /** Giorno del ciclo per una data qualsiasi (null se troppo lontano da un inizio). */
    fun cycleDayOf(date: LocalDate, starts: List<LocalDate>): Int? {
        val s = starts.lastOrNull { !it.isAfter(date) } ?: return null
        val d = ChronoUnit.DAYS.between(s, date).toInt() + 1
        return if (d <= 45) d else null
    }

    val phaseNames = listOf("Mestruale", "Follicolare", "Ovulatoria", "Luteale")
    val phaseShort = listOf("Mestr.", "Foll.", "Ovul.", "Lut.")

    /** Fasi stimate in base alla durata media del ciclo. */
    fun phase(day: Int, avgLen: Int?): Int {
        val len = avgLen ?: 28
        val ovu = (len - 14).coerceAtLeast(10)
        return when {
            day <= 5 -> 0
            day < ovu - 1 -> 1
            day <= ovu + 1 -> 2
            else -> 3
        }
    }

    fun headerText(info: CycleInfo, periodToday: Boolean, today: LocalDate = LocalDate.now()): String {
        val d = info.dayOfCycle ?: return "Ciclo"
        if (periodToday) return "🩸 Giorno $d del ciclo"
        val next = info.nextStart ?: return "Giorno $d del ciclo"
        val diff = ChronoUnit.DAYS.between(today, next).toInt()
        return when {
            diff > 1 -> "G. $d · ciclo tra ~$diff gg"
            diff == 1 -> "G. $d · ciclo previsto domani"
            diff == 0 -> "G. $d · ciclo previsto oggi"
            else -> "G. $d · in ritardo di ${-diff} gg"
        }
    }

    /** Segnali che, secondo le indicazioni mediche, vale la pena far vedere a un medico se si ripetono. */
    fun alerts(days: Map<String, Day>, info: CycleInfo, today: LocalDate = LocalDate.now()): List<Alert> {
        val out = mutableListOf<Alert>()
        val lastLens = info.lengths.takeLast(3)
        val short = lastLens.filter { it < 21 }
        val long = lastLens.filter { it in 36..89 }
        if (short.isNotEmpty()) out += Alert("Ciclo corto", "Negli ultimi 3 cicli: ${short.joinToString { "$it gg" }} (sotto i 21 giorni).")
        if (long.isNotEmpty()) out += Alert("Ciclo lungo", "Negli ultimi 3 cicli: ${long.joinToString { "$it gg" }} (oltre i 35 giorni).")
        val lastBleed = info.bleedDays.takeLast(3).filter { it > 7 }
        if (lastBleed.isNotEmpty()) out += Alert("Mestruazioni lunghe", "Durate recenti oltre i 7 giorni: ${lastBleed.joinToString { "$it gg" }}.")
        info.lastStart?.let {
            val since = ChronoUnit.DAYS.between(it, today)
            if (since >= 90) out += Alert("Nessun ciclo da $since giorni", "Oltre i 90 giorni senza mestruazioni segnate.")
        }
        // Spotting lontano dalle mestruazioni negli ultimi 90 giorni
        val pd = periodDates(days).toSet()
        val intermittent = days.filter { (k, v) ->
            val d = runCatching { LocalDate.parse(k) }.getOrNull() ?: return@filter false
            v["spotting"]?.isNotEmpty() == true && ChronoUnit.DAYS.between(d, today) in 0..90 &&
                (-3..3).none { pd.contains(d.plusDays(it.toLong())) }
        }.size
        if (intermittent >= 2) out += Alert("Perdite fuori dal ciclo", "$intermittent giorni di spotting lontano dalle mestruazioni negli ultimi 3 mesi.")
        return out
    }

    fun predictedDays(info: CycleInfo): Pair<Set<LocalDate>, Set<LocalDate>> {
        val strong = mutableSetOf<LocalDate>()
        val window = mutableSetOf<LocalDate>()
        val ns = info.nextStart ?: return strong to window
        val typicalBleed = info.bleedDays.takeLast(6).filter { it in 2..10 }.let { if (it.isEmpty()) 5 else Math.round(it.average()).toInt() }
        for (i in 0 until typicalBleed) strong += ns.plusDays(i.toLong())
        var d = info.nextEarliest ?: ns
        val end = info.nextLatest ?: ns
        while (!d.isAfter(end)) { window += d; d = d.plusDays(1) }
        return strong to window
    }

    // ---- Punteggi pelle
    fun skinScores(day: Day?): Pair<Int, Int> {
        val vals = day?.get(Catalog.SKIN) ?: return 0 to 0
        var ros = 0; var ds = 0
        for (v in vals) {
            val (k, l) = Catalog.splitLevel(v)
            if (k in Catalog.rosaceaKeys) ros += l
            if (k in Catalog.dsKeys) ds += l
        }
        return ros to ds
    }

    fun skinLogged(day: Day?): Boolean =
        day != null && (day[Catalog.SKIN]?.isNotEmpty() == true || day[Catalog.SKIN_OK]?.isNotEmpty() == true)
}
