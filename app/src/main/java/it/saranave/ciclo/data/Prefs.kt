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

    /** Opzioni da mostrare: quelle attive + quelle già segnate quel giorno (così non spariscono). */
    fun visibleOptions(ctx: Context, cat: Cat, selected: Set<String> = emptySet()): List<Opt> {
        val selKeys = selected.map { Catalog.splitLevel(it).first }.toSet()
        val base = cat.options.filter { optVisible(ctx, cat, it) || it.key in selKeys }
        val extra = selKeys.filter { k -> cat.options.none { it.key == k } }.map { Opt(it, it) }
        return base + extra
    }

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
