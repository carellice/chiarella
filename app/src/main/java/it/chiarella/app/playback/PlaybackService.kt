package it.chiarella.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import it.chiarella.app.ChiarellaApp
import it.chiarella.app.R
import it.chiarella.app.audio.Mixer
import it.chiarella.app.audio.Sounds
import it.chiarella.app.data.Store
import it.chiarella.app.ui.MainActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Servizio in primo piano che tiene viva la riproduzione a schermo spento e mostra
 * la notifica "Live Update" (su Android 16 appare come chip nella barra di stato
 * con il conto alla rovescia del timer).
 */
class PlaybackService : Service() {

    private val scope = MainScope()
    private lateinit var mixer: Mixer
    private lateinit var store: Store
    private lateinit var manager: NotificationManager
    private var isForeground = false
    /** Pausa lunga: la notifica smette di essere una Live Update e si può scorrere via. */
    private var demoted = false
    private var lastRendered: RenderKey? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) mixer.pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as ChiarellaApp
        mixer = app.mixer
        store = app.store
        manager = getSystemService(NotificationManager::class.java)
        createChannel()
        ContextCompat.registerReceiver(
            this, noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        scope.launch {
            mixer.state
                .map { NotificationKey(it.active, it.playing, it.interrupted, it.timerEndsAt, it.presetId) }
                .distinctUntilChanged()
                .collectLatest { key ->
                    demoted = false
                    render(mixer.state.value)
                    coroutineScope {
                        // Mentre il timer corre aggiorna periodicamente la barra di avanzamento.
                        if (key.timerEndsAt > 0L) launch {
                            while (true) {
                                delay(15_000)
                                render(mixer.state.value, force = true)
                            }
                        }
                        // Dopo una pausa lunga la notifica torna normale, così non resta fissa per ore.
                        if (key.active.isNotEmpty() && !key.playing && !key.interrupted) launch {
                            delay(PAUSED_LIVE_MS)
                            demoted = true
                            render(mixer.state.value)
                        }
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Prima si esegue il comando, poi si disegna la notifica una sola volta con lo stato
        // aggiornato: niente doppi aggiornamenti (stato vecchio e poi nuovo) che la fanno "scattare".
        // Ogni comando è un'interazione dell'utente: la notifica torna a essere una Live Update.
        demoted = false
        when (intent?.action) {
            ACTION_TOGGLE -> mixer.togglePlay()
            ACTION_PLAY -> if (mixer.state.value.hasMix) mixer.play() else mixer.resumeLastMix()
            ACTION_PAUSE -> mixer.pause()
            ACTION_STOP -> mixer.stopAll()
            ACTION_TIMER_ADD -> mixer.extendTimer(15)
            ACTION_TIMER_START -> mixer.startTimer(store.lastTimerMinutes)
            ACTION_PRESET -> intent.getStringExtra(EXTRA_PRESET)
                ?.let(store::preset)
                ?.let(mixer::applyPreset)
        }
        val s = mixer.state.value
        // Contratto di startForegroundService: il servizio deve sempre entrare in primo piano,
        // anche se il comando era "Stop" e subito dopo la notifica viene rimossa.
        if (!s.hasMix) goForeground(buildNotification(s, live = false))
        render(s, force = true)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(noisyReceiver) }
        super.onDestroy()
    }

    private fun render(s: Mixer.State, force: Boolean = false) {
        if (!s.hasMix) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            manager.cancel(NOTIFICATION_ID)
            isForeground = false
            lastRendered = null
            stopSelf()
            return
        }
        // In riproduzione e in pausa la notifica resta una Live Update in primo piano;
        // solo dopo una pausa lunga diventa una notifica normale che si può scorrere via.
        val live = s.playing || s.interrupted || !demoted
        val key = RenderKey(s.active, s.playing, s.interrupted, s.timerEndsAt, s.presetId, live)
        if (!force && key == lastRendered) return
        lastRendered = key
        val notification = buildNotification(s, live)
        if (live) {
            goForeground(notification)
        } else {
            if (isForeground) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                isForeground = false
            }
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun goForeground(notification: Notification) {
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            isForeground = true
        } catch (e: Exception) {
            Log.w("PlaybackService", "startForeground non consentito", e)
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(s: Mixer.State, live: Boolean): Notification {
        val sounds = s.active.mapNotNull { Sounds[it] }
        val preset = s.presetId?.let(store::preset)
        val names = sounds.joinToString(" · ") { it.name }
        val title = preset?.name ?: when (sounds.size) {
            0 -> getString(R.string.app_name)
            1, 2 -> names
            else -> "${sounds[0].name}, ${sounds[1].name} +${sounds.size - 2}"
        }
        val status = when {
            s.interrupted -> "In attesa…"
            s.playing -> "In riproduzione"
            else -> "In pausa"
        }
        val timerText = if (s.timerActive) {
            " · si spegne alle " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.timerEndsAt))
        } else ""
        val accent = (sounds.firstOrNull()?.primary?.toArgb()) ?: 0xFF8B7CF6.toInt()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_chiarella)
            .setContentTitle(title)
            .setContentText(status + timerText)
            .setContentIntent(openAppIntent())
            .setColor(accent)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(live)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (s.playing) builder.addAction(R.drawable.ic_pause, "Pausa", actionIntent(ACTION_PAUSE))
        else builder.addAction(R.drawable.ic_play, "Riprendi", actionIntent(ACTION_PLAY))

        if (s.timerActive) builder.addAction(R.drawable.ic_timer_add, "+15 min", actionIntent(ACTION_TIMER_ADD))
        else builder.addAction(R.drawable.ic_timer_add, "Timer ${store.lastTimerMinutes} min", actionIntent(ACTION_TIMER_START))

        builder.addAction(R.drawable.ic_stop, "Stop", actionIntent(ACTION_STOP))

        if (s.timerActive) {
            val total = (s.timerDurationMs / 1000).toInt().coerceAtLeast(1)
            val left = ((s.timerEndsAt - System.currentTimeMillis()) / 1000).toInt()
            val elapsed = (total - left).coerceIn(0, total)
            builder
                .setWhen(s.timerEndsAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setStyle(
                    NotificationCompat.ProgressStyle()
                        .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(total).setColor(accent)))
                        .setProgress(elapsed)
                        .setStyledByProgress(true)
                        .setProgressTrackerIcon(IconCompat.createWithResource(this, R.drawable.ic_stat_chiarella))
                )
        } else {
            builder
                .setShowWhen(false)
                .setShortCriticalText(if (s.playing) "♪ ${sounds.size}" else "Pausa")
                .setStyle(NotificationCompat.BigTextStyle().bigText(if (preset != null) "$names\n$status" else status))
        }

        // Android 16+: notifica promossa a "Live Update" (chip nella barra di stato),
        // anche in pausa, così resta identica e si riprende con un tocco.
        builder.setRequestPromotedOngoing(live)

        return builder.build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getForegroundService(
        this, action.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_playback_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private data class RenderKey(
        val active: List<String>,
        val playing: Boolean,
        val interrupted: Boolean,
        val timerEndsAt: Long,
        val presetId: String?,
        val live: Boolean,
    )

    private data class NotificationKey(
        val active: List<String>,
        val playing: Boolean,
        val interrupted: Boolean,
        val timerEndsAt: Long,
        val presetId: String?,
    )

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 7
        private const val PAUSED_LIVE_MS = 30 * 60_000L

        const val ACTION_TOGGLE = "it.chiarella.app.TOGGLE"
        const val ACTION_PLAY = "it.chiarella.app.PLAY"
        const val ACTION_PAUSE = "it.chiarella.app.PAUSE"
        const val ACTION_STOP = "it.chiarella.app.STOP"
        const val ACTION_TIMER_ADD = "it.chiarella.app.TIMER_ADD"
        const val ACTION_TIMER_START = "it.chiarella.app.TIMER_START"
        const val ACTION_PRESET = "it.chiarella.app.PRESET"
        const val EXTRA_PRESET = "preset"
    }
}
