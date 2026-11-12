package it.saranave.ciclo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.saranave.ciclo.data.Cycle
import it.saranave.ciclo.data.Prefs
import it.saranave.ciclo.data.Store
import it.saranave.ciclo.data.Sync
import it.saranave.ciclo.widget.Widgets
import it.saranave.ciclo.widget.pickIntent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

object Reminder {
    private const val CHANNEL = "ciclo_promemoria"
    private const val EVENING_ID = 1
    private const val PERIOD_ID = 2

    /** Un controllo all'ora scelta, ogni giorno. */
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!Prefs.reminderOn(ctx) && !Prefs.periodAlertOn(ctx)) { wm.cancelUniqueWork("sera"); return }
        val hour = Prefs.reminderHour(ctx)
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        wm.enqueueUniquePeriodicWork("sera", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Promemoria", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun pick(ctx: Context, what: String, code: Int): PendingIntent =
        PendingIntent.getActivity(ctx, code, pickIntent(ctx, what), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun run(ctx: Context) {
        if (!canNotify(ctx)) return
        channel(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        val open = PendingIntent.getActivity(ctx, 10, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val today = Store.day(ctx, Store.today())

        if (Prefs.reminderOn(ctx)) {
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Com'è andata oggi? 🌙")
                .setContentText(if (Cycle.skinLogged(today)) "Vuoi aggiungere qualcosa?" else "Segna pelle e umore con un tocco")
                .setContentIntent(open).setAutoCancel(true)
                .addAction(0, "🌡️ Pelle", pick(ctx, "skin", 11))
                .addAction(0, "😊 Umore", pick(ctx, "mood", 12))
                .addAction(0, "➕ Altro", pick(ctx, "more", 13))
                .build()
            runCatching { nm.notify(EVENING_ID, n) }
        }

        if (Prefs.periodAlertOn(ctx)) {
            val info = Cycle.compute(Store.snapshot(ctx))
            val next = info.nextStart
            val todayDate = LocalDate.now()
            if (next != null && ChronoUnit.DAYS.between(todayDate, next) == 2L && Prefs.lastPeriodAlert(ctx) != next.toString()) {
                Prefs.setLastPeriodAlert(ctx, next.toString())
                val n = NotificationCompat.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle("Ciclo previsto tra ~2 giorni 🩸")
                    .setContentText("Finestra possibile: ${info.nextEarliest} – ${info.nextLatest}")
                    .setContentIntent(open).setAutoCancel(true).build()
                runCatching { nm.notify(PERIOD_ID, n) }
            }
        }
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { Sync.pull(applicationContext) }
        Reminder.run(applicationContext)
        runCatching { Widgets.refresh(applicationContext) }
        return Result.success()
    }
}
