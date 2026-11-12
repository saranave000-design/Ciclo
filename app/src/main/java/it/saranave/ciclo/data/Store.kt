package it.saranave.ciclo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

typealias Day = Map<String, Set<String>>
private typealias MDay = MutableMap<String, MutableSet<String>>

/**
 * Tutti i dati vivono in un unico file JSON sul telefono:
 * { "days": { "2026-11-12": { "period": ["medium"], "skin_symptoms": ["papules:2"] } } }
 */
object Store {
    private const val FILE = "ciclo.json"
    private var cache: MutableMap<String, MDay>? = null
    private val _rev = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _rev

    fun today(): String = LocalDate.now().toString()

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, FILE)

    @Synchronized
    private fun data(ctx: Context): MutableMap<String, MDay> {
        cache?.let { return it }
        val f = file(ctx)
        val loaded: MutableMap<String, MDay> = if (f.exists()) {
            try {
                fromJson(f.readText())
            } catch (e: Exception) {
                // Non sovrascrivere mai un file che non riusciamo a leggere.
                f.copyTo(File(f.parentFile, "ciclo.illeggibile.${System.currentTimeMillis()}.json"), overwrite = true)
                mutableMapOf()
            }
        } else mutableMapOf()
        cache = loaded
        return loaded
    }

    @Synchronized
    fun snapshot(ctx: Context): Map<String, Day> =
        data(ctx).mapValues { (_, d) -> d.mapValues { it.value.toSet() } }

    @Synchronized
    fun day(ctx: Context, date: String): Day =
        data(ctx)[date]?.mapValues { it.value.toSet() } ?: emptyMap()

    @Synchronized
    fun get(ctx: Context, date: String, cat: String): Set<String> =
        data(ctx)[date]?.get(cat)?.toSet() ?: emptySet()

    @Synchronized
    fun set(ctx: Context, date: String, cat: String, values: Set<String>) {
        setNoSave(ctx, date, cat, values)
        save(ctx)
    }

    @Synchronized
    private fun setNoSave(ctx: Context, date: String, cat: String, values: Set<String>) {
        val all = data(ctx)
        if (values.isEmpty()) {
            all[date]?.remove(cat)
            if (all[date]?.isEmpty() == true) all.remove(date)
        } else {
            all.getOrPut(date) { mutableMapOf() }[cat] = values.toMutableSet()
        }
    }

    /** Tocca un'opzione: la aggiunge o la toglie. */
    @Synchronized
    fun toggle(ctx: Context, date: String, cat: Cat, value: String) {
        val cur = get(ctx, date, cat.key)
        val next = when {
            value in cur -> cur - value
            cat.single -> setOf(value)
            else -> cur + value
        }
        set(ctx, date, cat.key, next)
        if (cat.key == Catalog.SKIN && next.isNotEmpty()) set(ctx, date, Catalog.SKIN_OK, emptySet())
    }

    /** Opzione con livello: nessuno -> 1 -> 2 -> 3 -> nessuno. */
    @Synchronized
    fun cycleLevel(ctx: Context, date: String, catKey: String, key: String): Int {
        val cur = get(ctx, date, catKey)
        val existing = cur.firstOrNull { Catalog.splitLevel(it).first == key }
        val lvl = existing?.let { Catalog.splitLevel(it).second } ?: 0
        val nextLvl = if (lvl >= 3) 0 else lvl + 1
        val next = cur.filterNot { Catalog.splitLevel(it).first == key }.toMutableSet()
        if (nextLvl > 0) next += "$key:$nextLvl"
        set(ctx, date, catKey, next)
        if (catKey == Catalog.SKIN && next.isNotEmpty()) set(ctx, date, Catalog.SKIN_OK, emptySet())
        return nextLvl
    }

    /** Un tocco sul widget del ciclo: nessuno -> leggere -> medie -> abbondanti -> molto -> nessuno. */
    @Synchronized
    fun cyclePeriod(ctx: Context, date: String): String? {
        val order = Catalog.periodLevels
        val cur = get(ctx, date, Catalog.PERIOD).firstOrNull()
        val idx = order.indexOf(cur)
        val next = when {
            cur == null -> order.first()
            idx == -1 || idx == order.lastIndex -> null
            else -> order[idx + 1]
        }
        set(ctx, date, Catalog.PERIOD, next?.let { setOf(it) } ?: emptySet())
        return next
    }

    @Synchronized
    fun setSkinOk(ctx: Context, date: String, ok: Boolean) {
        if (ok) {
            setNoSave(ctx, date, Catalog.SKIN, emptySet())
            setNoSave(ctx, date, Catalog.SKIN_OK, setOf("yes"))
        } else setNoSave(ctx, date, Catalog.SKIN_OK, emptySet())
        save(ctx)
    }

    @Synchronized
    fun allValues(ctx: Context, cat: String): Set<String> =
        data(ctx).values.flatMap { it[cat] ?: emptySet() }.toSet()

    /**
     * Unisce dati in arrivo (import o collegamento).
     * replace = true: le voci presenti nei dati in arrivo sostituiscono quelle del giorno.
     */
    @Synchronized
    fun merge(ctx: Context, incoming: Map<String, Map<String, Set<String>>>, replace: Boolean): Int {
        var touched = 0
        for ((date, cats) in incoming) {
            for ((cat, values) in cats) {
                val cur = get(ctx, date, cat)
                val single = Catalog.cat(cat)?.single == true
                val next = when {
                    replace -> values
                    single && cur.isNotEmpty() -> cur
                    else -> cur + values
                }
                if (next != cur) {
                    setNoSave(ctx, date, cat, next); touched++
                }
            }
        }
        save(ctx)
        return touched
    }

    @Synchronized
    fun exportJson(ctx: Context): String = toJson(data(ctx)).toString(1)

    @Synchronized
    fun restore(ctx: Context, text: String) {
        val parsed = fromJson(text)
        val f = file(ctx)
        if (f.exists()) f.copyTo(File(f.parentFile, "ciclo.prima-del-ripristino.json"), overwrite = true)
        cache = parsed
        save(ctx)
    }

    @Synchronized
    private fun save(ctx: Context) {
        val f = file(ctx)
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(toJson(data(ctx)).toString())
        if (!tmp.renameTo(f)) {
            f.delete(); tmp.renameTo(f)
        }
        _rev.value = _rev.value + 1
    }

    fun isBackup(text: String): Boolean =
        runCatching { JSONObject(text).has("days") }.getOrDefault(false)

    private fun toJson(d: Map<String, MDay>): JSONObject {
        val days = JSONObject()
        for (date in d.keys.sorted()) {
            val o = JSONObject()
            for ((cat, vals) in d.getValue(date)) o.put(cat, JSONArray(vals.sorted()))
            days.put(date, o)
        }
        return JSONObject().put("app", "ciclo").put("version", 1).put("days", days)
    }

    fun parseDays(daysObj: JSONObject): MutableMap<String, MDay> {
        val out = mutableMapOf<String, MDay>()
        for (date in daysObj.keys()) {
            val o = daysObj.getJSONObject(date)
            val m: MDay = mutableMapOf()
            for (cat in o.keys()) {
                val arr = o.optJSONArray(cat)
                val s = mutableSetOf<String>()
                if (arr != null) for (i in 0 until arr.length()) s += arr.getString(i)
                else o.optString(cat).takeIf { it.isNotEmpty() }?.let { s += it }
                m[cat] = s
            }
            out[date] = m
        }
        return out
    }

    private fun fromJson(text: String): MutableMap<String, MDay> =
        parseDays(JSONObject(text).getJSONObject("days"))
}
