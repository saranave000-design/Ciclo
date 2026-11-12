package it.saranave.ciclo.data

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Collegamento con Claude: Claude scrive i dati nel file inbox.json sul ramo privato "inbox"
 * della repo, l'app li scarica e li aggiunge. Ogni pacchetto ha un id e viene applicato una sola volta.
 */
object Sync {
    private const val REPO = "https://api.github.com/repos/saranave000-design/ciclo"

    private fun get(token: String, url: String, raw: Boolean): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Accept", if (raw) "application/vnd.github.raw+json" else "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        return try {
            val code = c.responseCode
            val body = if (code in 200..299) c.inputStream.bufferedReader().readText() else ""
            code to body
        } finally { c.disconnect() }
    }

    fun test(token: String): Boolean = runCatching { get(token, REPO, false).first == 200 }.getOrDefault(false)

    sealed class Result {
        data class Ok(val applied: Int, val changes: Int) : Result()
        data class Error(val msg: String) : Result()
        object NotLinked : Result()
    }

    /** Da chiamare fuori dal thread principale. */
    fun pull(ctx: Context): Result {
        val token = Prefs.token(ctx) ?: return Result.NotLinked
        return try {
            val (code, body) = get(token, "$REPO/contents/inbox.json?ref=inbox", true)
            when (code) {
                200 -> {
                    val done = Prefs.appliedBatches(ctx)
                    val fresh = Importer.batches(body).filter { it.id !in done }
                    var changes = 0
                    for (b in fresh) changes += Store.merge(ctx, b.days, b.replace)
                    Prefs.addApplied(ctx, fresh.map { it.id })
                    Prefs.setLastSync(ctx, LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")))
                    Result.Ok(fresh.size, changes)
                }
                404 -> { Prefs.setLastSync(ctx, LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))); Result.Ok(0, 0) }
                401, 403 -> Result.Error("La chiave non funziona più: ricollega l'app.")
                else -> Result.Error("Errore $code")
            }
        } catch (e: Exception) {
            Result.Error("Niente connessione")
        }
    }
}
