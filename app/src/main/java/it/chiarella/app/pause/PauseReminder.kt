package it.chiarella.app.pause

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import it.chiarella.app.ChiarellaApp
import it.chiarella.app.R
import it.chiarella.app.ui.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * "Pausa di un minuto": negli orari scelti arriva una notifica gentile che, con un tocco,
 * apre un respiro guidato di circa un minuto con un suono acceso.
 */
object PauseReminder {

    const val ACTION_OPEN = "it.chiarella.app.PAUSE"
    private const val ACTION_FIRE = "it.chiarella.app.PAUSE_FIRE"
    private const val ACTION_SKIP_TODAY = "it.chiarella.app.PAUSE_SKIP_TODAY"
    private const val CHANNEL_ID = "pause"
    private const val NOTIFICATION_ID = 42

    /** Al massimo tre pause al giorno: di più diventano un fastidio. */
    const val MAX_TIMES = 3

    /** Programma la prossima pausa (o la annulla se disattivata). Si chiama a ogni avvio e cambio di orari. */
    fun schedule(context: Context) {
        val app = context.applicationContext as ChiarellaApp
        val prefs = app.store.preferences.value
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = fireIntent(context)
        alarms.cancel(pending)
        if (!prefs.pauseReminders) return
        val next = nextAt(prefs.pauseTimes, LocalDateTime.now()) ?: return
        // Allarme non esatto: qualche minuto di ritardo va bene e non serve il permesso degli allarmi esatti.
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            pending,
        )
    }

    /** Il primo orario (minuti dal mezzanotte) dopo [now], oggi o domani. */
    internal fun nextAt(times: List<Int>, now: LocalDateTime): LocalDateTime? {
        val sorted = times.distinct().sorted()
        if (sorted.isEmpty()) return null
        val today = now.toLocalDate()
        val nowMin = now.hour * 60 + now.minute
        val m = sorted.firstOrNull { it > nowMin }
        return if (m != null) today.atTime(LocalTime.of(m / 60, m % 60))
        else today.plusDays(1).atTime(LocalTime.of(sorted[0] / 60, sorted[0] % 60))
    }

    private fun fireIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, PauseReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as ChiarellaApp
        when (intent.action) {
            ACTION_FIRE -> {
                if (app.store.pauseSkippedDay != LocalDate.now().toEpochDay()) notify(context)
                schedule(context)
            }
            ACTION_SKIP_TODAY -> {
                app.store.pauseSkippedDay = LocalDate.now().toEpochDay()
                context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            }
            // Riavvio, aggiornamento dell'app, cambio di ora o fuso: gli allarmi vanno riprogrammati.
            else -> schedule(context)
        }
    }

    private fun notify(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_pause), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_pause_desc) }
        )
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).setAction(ACTION_OPEN).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val skip = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, PauseReceiver::class.java).setAction(ACTION_SKIP_TODAY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_chiarella)
            .setContentTitle("Un respiro?")
            .setContentText("Un minuto solo per te. Poi torni a quello che stavi facendo.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(60 * 60 * 1000L)
            .addAction(0, "Salta oggi", skip)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

class PauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = PauseReminder.onReceive(context, intent)
}
