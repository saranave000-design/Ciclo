package it.saranave.ciclo.data

import org.json.JSONArray
import org.json.JSONObject

/** Converte i dati in arrivo (Clue, backup, collegamento) nel formato dell'app. */
object Importer {

    /** measurements.json di Clue -> giorni. */
    fun fromClue(text: String): Map<String, Map<String, Set<String>>> {
        val t = text.trim()
        val arr: JSONArray = if (t.startsWith("[")) JSONArray(t)
        else JSONObject(t).optJSONArray("measurements") ?: JSONArray()
        val out = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()
        fun add(date: String, cat: String, v: String) {
            out.getOrPut(date) { mutableMapOf() }.getOrPut(cat) { mutableSetOf() } += v
        }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = o.optString("type"); val date = o.optString("date").take(10)
            if (type.isEmpty() || date.length != 10) continue
            val value = o.opt("value")
            if (type == "weight") {
                val kg = (value as? JSONObject)?.optDouble("kilograms")
                if (kg != null && !kg.isNaN()) add(date, Catalog.WEIGHT, kg.toString())
                continue
            }
            val opts = mutableListOf<String>()
            when (value) {
                is JSONArray -> for (j in 0 until value.length()) {
                    val item = value.opt(j)
                    if (item is JSONObject) item.optString("option").takeIf { it.isNotEmpty() }?.let { opts += it }
                    else if (item != null) opts += item.toString()
                }
                is JSONObject -> value.optString("option").takeIf { it.isNotEmpty() }?.let { opts += it }
                is String -> opts += value
            }
            for (opt in opts) when (type) {
                "skin" -> when (opt) {
                    "good" -> add(date, Catalog.SKIN_OK, "yes")
                    "acne" -> add(date, Catalog.SKIN, "papules:2")
                    "dry" -> add(date, Catalog.SKIN, "dryness:1")
                    "oily" -> add(date, Catalog.SKIN, "oily:1")
                    else -> add(date, Catalog.SKIN, "$opt:1")
                }
                "tags" -> add(date, Catalog.TAGS, opt)
                else -> add(date, type, opt)
            }
        }
        // Se in un giorno ci sono sia "pelle ok" che sintomi, tengo i sintomi.
        for (d in out.values) if (d[Catalog.SKIN]?.isNotEmpty() == true) d.remove(Catalog.SKIN_OK)
        return out
    }

    data class Batch(val id: String, val replace: Boolean, val days: Map<String, Map<String, Set<String>>>)

    /**
     * File del collegamento con Claude:
     * { "batches": [ { "id": "...", "mode": "merge"|"replace", "days": {...} } ] }
     */
    fun batches(text: String): List<Batch> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("batches") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val b = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = b.optString("id").ifEmpty { return@mapNotNull null }
            val days = b.optJSONObject("days") ?: return@mapNotNull null
            Batch(id, b.optString("mode") == "replace", Store.parseDays(days))
        }
    }
}
