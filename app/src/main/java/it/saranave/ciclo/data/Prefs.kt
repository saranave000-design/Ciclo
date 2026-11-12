package it.saranave.ciclo.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Impostazioni: quali voci si vedono, promemoria, collegamento con Claude. */
object Prefs {
    private fun p(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("ciclo_prefs", Context.MODE_PRIVATE)

    private val _rev = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _rev
    private fun bump() { _rev.value = _rev.value + 1 }

    // ---- Visibilità voci e opzioni
    fun catVisible(ctx: Context, cat: Cat): Boolean = p(ctx).getBoolean("cat_${cat.key}", cat.onByDefault)
    fun setCatVisible(ctx: Context, cat: Cat, v: Boolean) { p(ctx).edit().putBoolean("cat_${cat.key}", v).apply(); bump() }

    fun optVisible(ctx: Context, cat: Cat, opt: Opt): Boolean = p(ctx).getBoolean("opt_${cat.key}_${opt.key}", opt.onByDefault)
    fun setOptVisible(ctx: Context, cat: Cat, opt: Opt, v: Boolean) { p(ctx).edit().putBoolean("opt_${cat.key}_${opt.key}", v).apply(); bump() }

    // ---- Liste salvate come testo (così finiscono anche nel backup)
    private fun list(ctx: Context, key: String): List<String> = runCatching {
        val a = org.json.JSONArray(p(ctx).getString(key, "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())
    private fun setList(ctx: Context, key: String, v: List<String>) {
        p(ctx).edit().putString(key, org.json.JSONArray(v).toString()).apply(); bump()
    }

    const val CUSTOM_GROUP = "Aggiunte da te"

    /** Opzioni create da Sara: la chiave è il testo stesso, così si legge ovunque (tabella, PDF). */
    fun customOptions(ctx: Context, cat: Cat): List<Opt> {
        val g = if (cat.options.any { it.group != null }) CUSTOM_GROUP else null
        return list(ctx, "custom_${cat.key}").map { Opt(it, it, group = g) }
    }

    /** Ritorna false se esiste già un'opzione con lo stesso nome. */
    fun addCustom(ctx: Context, cat: Cat, label: String): Boolean {
        val clean = label.replace(":", " ").trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return false
        val existing = cat.options.firstOrNull { it.label.equals(clean, true) || it.key == clean }
        if (existing != null) {
            if (!optVisible(ctx, cat, existing)) { setOptVisible(ctx, cat, existing, true); return true }
            return false
        }
        val cur = list(ctx, "custom_${cat.key}")
        if (cur.any { it.equals(clean, true) }) return false
        setList(ctx, "custom_${cat.key}", cur + clean)
        return true
    }

    /** Elimina un'opzione dall'elenco. I giorni passati in cui era segnata restano nello storico. */
    fun removeOption(ctx: Context, cat: Cat, opt: Opt) {
        val cur = list(ctx, "custom_${cat.key}")
        if (opt.key in cur) setList(ctx, "custom_${cat.key}", cur - opt.key)
        else setOptVisible(ctx, cat, opt, false)
    }

    fun hiddenBuiltIns(ctx: Context, cat: Cat): List<Opt> = cat.options.filter { !optVisible(ctx, cat, it) }

    fun optOrder(ctx: Context, cat: Cat): List<String> = list(ctx, "order_${cat.key}")
    fun setOptOrder(ctx: Context, cat: Cat, keys: List<String>) = setList(ctx, "order_${cat.key}", keys)

    /** Tutte le opzioni attive, nell'ordine scelto da Sara (le nuove in fondo). */
    fun orderedOptions(ctx: Context, cat: Cat): List<Opt> {
        val all = cat.options.filter { optVisible(ctx, cat, it) } + customOptions(ctx, cat)
        val order = optOrder(ctx, cat)
        return all.sortedBy { o -> order.indexOf(o.key).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    /** Opzioni da mostrare: quelle attive + quelle già segnate quel giorno (così non spariscono). */
    fun visibleOptions(ctx: Context, cat: Cat, selected: Set<String> = emptySet()): List<Opt> {
        val selKeys = selected.map { Catalog.splitLevel(it).first }.toSet()
        val base = orderedOptions(ctx, cat)
        val hiddenSel = cat.options.filter { it.key in selKeys && base.none { b -> b.key == it.key } }
        val extra = selKeys.filter { k -> base.none { it.key == k } && cat.options.none { it.key == k } }.map { Opt(it, it) }
        return base + hiddenSel + extra
    }

    // ---- Ordine delle voci nella pagina del giorno
    fun orderedCats(ctx: Context): List<Cat> {
        val order = list(ctx, "cat_order")
        return Catalog.all.sortedBy { c -> order.indexOf(c.key).let { if (it < 0) Int.MAX_VALUE else it } }
    }
    fun setCatOrder(ctx: Context, keys: List<String>) = setList(ctx, "cat_order", keys)

    // ---- Promemoria
    fun reminderOn(ctx: Context) = p(ctx).getBoolean("rem_on", false)
    fun reminderHour(ctx: Context) = p(ctx).getInt("rem_hour", 21)
    fun setReminder(ctx: Context, on: Boolean, hour: Int) {
        p(ctx).edit().putBoolean("rem_on", on).putInt("rem_hour", hour).apply(); bump()
    }
    fun periodAlertOn(ctx: Context) = p(ctx).getBoolean("period_alert", true)
    fun setPeriodAlert(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("period_alert", on).apply(); bump() }
    fun lastPeriodAlert(ctx: Context) = p(ctx).getString("last_period_alert", "") ?: ""
    fun setLastPeriodAlert(ctx: Context, d: String) = p(ctx).edit().putString("last_period_alert", d).apply()

    // ---- Impostazioni nel backup (senza la chiave GitHub, per sicurezza)
    private val notInBackup = setOf("token", "batches", "last_sync", "last_period_alert")

    fun dumpSettings(ctx: Context): org.json.JSONObject {
        val o = org.json.JSONObject()
        for ((k, v) in p(ctx).all) {
            if (k in notInBackup) continue
            when (v) {
                is Boolean -> o.put(k, org.json.JSONObject().put("b", v))
                is Int -> o.put(k, org.json.JSONObject().put("i", v))
                is String -> o.put(k, org.json.JSONObject().put("s", v))
            }
        }
        return o
    }

    fun loadSettings(ctx: Context, o: org.json.JSONObject) {
        val e = p(ctx).edit()
        for (k in o.keys()) {
            if (k in notInBackup) continue
            val v = o.optJSONObject(k) ?: continue
            when {
                v.has("b") -> e.putBoolean(k, v.getBoolean("b"))
                v.has("i") -> e.putInt(k, v.getInt("i"))
                v.has("s") -> e.putString(k, v.getString("s"))
            }
        }
        e.apply(); bump()
    }

    // ---- Collegamento con Claude (GitHub)
    fun token(ctx: Context): String? = p(ctx).getString("token", null)?.takeIf { it.isNotBlank() }
    fun setToken(ctx: Context, t: String?) { p(ctx).edit().putString("token", t?.trim()).apply(); bump() }
    fun appliedBatches(ctx: Context): Set<String> = p(ctx).getStringSet("batches", emptySet()) ?: emptySet()
    fun addApplied(ctx: Context, ids: Collection<String>) {
        p(ctx).edit().putStringSet("batches", appliedBatches(ctx) + ids).apply()
    }
    fun lastSync(ctx: Context) = p(ctx).getString("last_sync", "") ?: ""
    fun setLastSync(ctx: Context, s: String) { p(ctx).edit().putString("last_sync", s).apply(); bump() }
}
