package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.example.MainActivity
import com.example.quran.QuranCatalog

class QuranPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "noor_quran_playback"
        const val NOTIFICATION_ID = 4101
        const val ACTION_PLAY_PAUSE = "com.aistudio.noor.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.aistudio.noor.ACTION_NEXT"
        const val ACTION_PREV = "com.aistudio.noor.ACTION_PREV"
        const val ACTION_STOP = "com.aistudio.noor.ACTION_STOP"
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        mediaSession = MediaSession(this, "NoorQuranMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { QuranAudioEngine.resume(this@QuranPlaybackService) }
                override fun onPause() { QuranAudioEngine.pause(this@QuranPlaybackService) }
                override fun onSkipToNext() { QuranAudioEngine.nextAyah(this@QuranPlaybackService) }
                override fun onSkipToPrevious() { QuranAudioEngine.previousAyah(this@QuranPlaybackService) }
                override fun onStop() { QuranAudioEngine.stop(this@QuranPlaybackService) }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> QuranAudioEngine.togglePlayPause(this)
            ACTION_NEXT -> QuranAudioEngine.nextAyah(this)
            ACTION_PREV -> QuranAudioEngine.previousAyah(this)
            ACTION_STOP -> {
                QuranAudioEngine.stop(this)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val state = QuranAudioEngine.state.value
        if (!state.isPlaying && !state.isPaused && !state.isPreparing) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        updateMediaSession(state)
        startForeground(NOTIFICATION_ID, buildNotification(state))
        return START_STICKY
    }

    private fun updateMediaSession(state: QuranPlaybackState) {
        val session = mediaSession ?: return
        val surahName = QuranCatalog.surahByNumber(state.currentSurah)?.nameAr ?: "سورة ${state.currentSurah}"
        val playbackStateCode = if (state.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_STOP
                )
                .setState(playbackStateCode, state.positionMs, state.playbackSpeed)
                .build()
        )
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "$surahName - آية ${state.currentAyah}")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, state.reciterName)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs)
                .build()
        )
    }

    private fun buildNotification(state: QuranPlaybackState): Notification {
        val surahMeta = QuranCatalog.surahByNumber(state.currentSurah)
        val title = "${surahMeta?.nameAr ?: "القرآن"} • آية ${state.currentAyah}"
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevAction = Notification.Action.Builder(
            android.R.drawable.ic_media_previous,
            "Previous",
            servicePendingIntent(ACTION_PREV, 1)
        ).build()

        val playPauseAction = Notification.Action.Builder(
            if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (state.isPlaying) "Pause" else "Play",
            servicePendingIntent(ACTION_PLAY_PAUSE, 2)
        ).build()

        val nextAction = Notification.Action.Builder(
            android.R.drawable.ic_media_next,
            "Next",
            servicePendingIntent(ACTION_NEXT, 3)
        ).build()

        val stopAction = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            servicePendingIntent(ACTION_STOP, 4)
        ).build()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(state.reciterName)
            .setContentIntent(contentIntent)
            .setOngoing(state.isPlaying)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun servicePendingIntent(action: String, code: Int): PendingIntent {
        val intent = Intent(this, QuranPlaybackService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            code,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                "NOOR Quran Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Lock screen and notification controls for Quran recitation"
            }
            nm.createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
