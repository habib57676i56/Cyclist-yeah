package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ActiveAudioMode {
    NONE,
    LISTEN_AND_FOLLOW,
    RECORDING_CAPTURE,
    RECORDING_PLAYBACK,
    ADHAN
}

/**
 * Single process-wide coordinator enforcing mutual exclusion across:
 * 1) Sheikh Quran Listen & Follow
 * 2) Microphone Voice Recording
 * 3) User Recording Playback
 * 4) Adhan Playback
 */
object AudioFocusCoordinator {
    private val _activeMode = MutableStateFlow(ActiveAudioMode.NONE)
    val activeMode: StateFlow<ActiveAudioMode> = _activeMode.asStateFlow()

    private var onPreemptCallback: ((ActiveAudioMode) -> Unit)? = null
    private var focusRequest: AudioFocusRequest? = null

    fun acquireMode(
        context: Context,
        mode: ActiveAudioMode,
        onStopCurrent: (ActiveAudioMode) -> Unit
    ): Boolean {
        val prev = _activeMode.value
        if (prev != ActiveAudioMode.NONE && prev != mode) {
            onPreemptCallback?.invoke(prev)
        }
        onPreemptCallback = onStopCurrent
        _activeMode.value = mode

        if (mode == ActiveAudioMode.LISTEN_AND_FOLLOW ||
            mode == ActiveAudioMode.RECORDING_PLAYBACK ||
            mode == ActiveAudioMode.ADHAN
        ) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(
                                    if (mode == ActiveAudioMode.ADHAN) AudioAttributes.USAGE_ALARM
                                    else AudioAttributes.USAGE_MEDIA
                                )
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .build()
                    focusRequest = req
                    am.requestAudioFocus(req)
                } else {
                    @Suppress("DEPRECATION")
                    am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                }
            }
        }
        return true
    }

    fun releaseMode(context: Context, mode: ActiveAudioMode) {
        if (_activeMode.value == mode) {
            _activeMode.value = ActiveAudioMode.NONE
            onPreemptCallback = null
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
                focusRequest = null
            }
        }
    }
}
