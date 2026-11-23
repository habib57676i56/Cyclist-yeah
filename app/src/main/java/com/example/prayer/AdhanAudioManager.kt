package com.example.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.app.NotificationManagerCompat
import com.example.audio.ActiveAudioMode
import com.example.audio.AudioFocusCoordinator
import com.example.data.local.AdhanVoiceEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

data class AdhanPlaybackState(
    val isPlaying: Boolean = false,
    val activeVoiceName: String = "",
    val usingFallbackTone: Boolean = false,
    val lastResultMessage: String? = null,
    val blockedBySystem: Boolean = false
)

object AdhanAudioManager {
    private var mediaPlayer: MediaPlayer? = null
    private val _state = MutableStateFlow(AdhanPlaybackState())
    val state: StateFlow<AdhanPlaybackState> = _state.asStateFlow()

    fun adhanDir(context: Context): File = File(context.filesDir, "adhan").apply { mkdirs() }

    /**
     * Generates a gentle offline harmonic WAV chime file as a guaranteed fallback
     * when a licensed Nasser Al-Qatami MP3 or custom user Adhan file has not been imported yet.
     */
    fun ensureFallbackToneWav(context: Context): File {
        val file = File(adhanDir(context), "fallback_chime.wav")
        if (file.exists() && file.length() > 1000L) return file

        val sampleRate = 22050
        val durationSec = 2.6
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numSamples)
        val freqs = listOf(523.25, 659.25, 783.99) // C5 - E5 - G5 soft chime
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 1.4) * (1.0 - exp(-t * 40.0))
            var sample = 0.0
            freqs.forEachIndexed { idx, f ->
                val start = idx * 0.25
                if (t >= start) {
                    val lt = t - start
                    sample += sin(2.0 * PI * f * lt) * exp(-lt * 1.8) * 0.28
                }
            }
            pcm[i] = (sample * env * 22000).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val dataSize = numSamples * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(dataSize)
        }
        FileOutputStream(file).use { out ->
            out.write(header.array())
            val buf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            pcm.forEach { buf.putShort(it) }
            out.write(buf.array())
        }
        return file
    }

    fun checkPlaybackAllowedBySystem(context: Context): String? {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return "Android notifications are disabled for NOOR; scheduled Adhan notifications cannot alert."
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null && am.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return "Device is in Silent / Do Not Disturb mode; Android may suppress Adhan audio."
        }
        return null
    }

    fun playAdhanVoice(context: Context, voice: AdhanVoiceEntity) {
        val systemBlock = checkPlaybackAllowedBySystem(context)
        stopAdhan(context)

        AudioFocusCoordinator.acquireMode(context, ActiveAudioMode.ADHAN) {
            stopAdhan(context)
        }

        val customFile = voice.filePath.takeIf { it.isNotBlank() && it != "BUILTIN_TONE" }?.let { File(it) }
        val useCustom = customFile != null && customFile.exists() && customFile.length() > 0L
        val targetFile = if (useCustom) customFile!! else ensureFallbackToneWav(context)
        val isFallback = !useCustom

        runCatching {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(targetFile.absolutePath)
                setOnPreparedListener {
                    it.start()
                    _state.update { st ->
                        st.copy(
                            isPlaying = true,
                            activeVoiceName = voice.nameAr,
                            usingFallbackTone = isFallback,
                            blockedBySystem = systemBlock != null,
                            lastResultMessage = when {
                                systemBlock != null -> systemBlock
                                isFallback -> "Playing built-in fallback chime (import licensed ${voice.nameEn} MP3/M4A/WAV to play full voice)."
                                else -> "Playing ${voice.nameAr}"
                            }
                        )
                    }
                }
                setOnCompletionListener { stopAdhan(context) }
                setOnErrorListener { _, what, extra ->
                    _state.update { st ->
                        st.copy(
                            isPlaying = false,
                            blockedBySystem = true,
                            lastResultMessage = "Adhan playback blocked or failed ($what/$extra)"
                        )
                    }
                    stopAdhan(context)
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        }.onFailure { ex ->
            AudioFocusCoordinator.releaseMode(context, ActiveAudioMode.ADHAN)
            _state.update {
                it.copy(
                    isPlaying = false,
                    blockedBySystem = true,
                    lastResultMessage = "Adhan playback failed: ${ex.message}"
                )
            }
        }
    }

    fun stopAdhan(context: Context) {
        mediaPlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            }
        }
        mediaPlayer = null
        AudioFocusCoordinator.releaseMode(context, ActiveAudioMode.ADHAN)
        _state.update { it.copy(isPlaying = false) }
    }

    fun importCustomAdhanAudio(context: Context, uri: Uri): Result<AdhanVoiceEntity> = runCatching {
        var displayName = "Custom Adhan"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) displayName = c.getString(idx) ?: displayName
            }
        }
        val ext = displayName.substringAfterLast('.', "mp3").lowercase()
        if (ext !in setOf("mp3", "m4a", "wav")) {
            error("Unsupported Adhan audio format .$ext. Supported formats: MP3, M4A, WAV.")
        }
        val id = "adhan_custom_${System.currentTimeMillis()}"
        val dest = File(adhanDir(context), "$id.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { out -> input.copyTo(out) }
        } ?: error("Cannot open selected audio file.")

        AdhanVoiceEntity(
            id = id,
            nameAr = displayName.substringBeforeLast('.'),
            nameEn = displayName.substringBeforeLast('.'),
            filePath = dest.absolutePath,
            format = ext.uppercase(),
            assignedPrayers = "ALL",
            isDefault = false,
            isSystemFallback = false
        )
    }

    fun scheduleNextPrayerAlarms(context: Context, times: DailyPrayerTimes, enabledMap: Map<String, Boolean>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prayers = listOf(
            "FAJR" to times.fajrMinutes,
            "DHUHR" to times.dhuhrMinutes,
            "ASR" to times.asrMinutes,
            "MAGHRIB" to times.maghribMinutes,
            "ISHA" to times.ishaMinutes
        )
        val now = Calendar.getInstance()
        prayers.forEachIndexed { idx, (name, minutes) ->
            val reqCode = 7000 + idx
            val intent = Intent(context, AdhanAlarmReceiver::class.java).apply {
                putExtra("prayer_name", name)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            if (enabledMap[name] != true) {
                am.cancel(pi)
                return@forEachIndexed
            }
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minutes / 60)
                set(Calendar.MINUTE, minutes % 60)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
            }
        }
    }
}
