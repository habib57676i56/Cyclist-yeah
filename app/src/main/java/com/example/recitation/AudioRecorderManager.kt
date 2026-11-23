package com.example.recitation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.audio.ActiveAudioMode
import com.example.audio.AudioFocusCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class RecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val isPlayingBack: Boolean = false,
    val elapsedMs: Long = 0L,
    val amplitudeNorm: Float = 0f,
    val longPauseCount: Int = 0,
    val savedFilePath: String? = null,
    val micAvailable: Boolean = true,
    val hasPermission: Boolean = false,
    val errorMessage: String? = null
)

class AudioRecorderManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: MediaRecorder? = null
    private var playbackPlayer: MediaPlayer? = null
    private var meterJob: Job? = null
    private var currentOutputFile: File? = null
    private var silentRunMs: Long = 0L
    private var lastTickMs: Long = 0L

    private val _state = MutableStateFlow(
        RecordingState(
            micAvailable = hasHardwareMicrophone(),
            hasPermission = hasRecordPermission()
        )
    )
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    fun recordingsDir(): File = File(context.filesDir, "recordings").apply { mkdirs() }

    fun hasHardwareMicrophone(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun refreshDiagnostics() {
        _state.update {
            it.copy(
                micAvailable = hasHardwareMicrophone(),
                hasPermission = hasRecordPermission()
            )
        }
    }

    fun startRecording(): Boolean {
        refreshDiagnostics()
        if (!hasHardwareMicrophone()) {
            _state.update { it.copy(isRecording = false, errorMessage = "No microphone hardware detected on this device.") }
            return false
        }
        if (!hasRecordPermission()) {
            _state.update { it.copy(isRecording = false, errorMessage = "Microphone permission (RECORD_AUDIO) was not granted.") }
            return false
        }

        stopPlayback()
        releaseRecorder()
        AudioFocusCoordinator.acquireMode(context, ActiveAudioMode.RECORDING_CAPTURE) {
            stopRecording()
        }

        val outFile = File(recordingsDir(), "rec_${System.currentTimeMillis()}.m4a")
        currentOutputFile = outFile
        silentRunMs = 0L

        return runCatching {
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(16000)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            lastTickMs = System.currentTimeMillis()
            _state.update {
                it.copy(
                    isRecording = true,
                    isPaused = false,
                    elapsedMs = 0L,
                    amplitudeNorm = 0f,
                    longPauseCount = 0,
                    savedFilePath = null,
                    errorMessage = null
                )
            }
            startMeterLoop()
            true
        }.getOrElse { ex ->
            releaseRecorder()
            runCatching { outFile.delete() }
            AudioFocusCoordinator.releaseMode(context, ActiveAudioMode.RECORDING_CAPTURE)
            _state.update {
                it.copy(
                    isRecording = false,
                    isPaused = false,
                    errorMessage = "Failed to start microphone capture: ${ex.message ?: ex.javaClass.simpleName}"
                )
            }
            false
        }
    }

    fun pauseRecording() {
        val mr = recorder ?: return
        if (!_state.value.isRecording || _state.value.isPaused) return
        runCatching {
            mr.pause()
            _state.update { it.copy(isPaused = true, amplitudeNorm = 0f) }
        }.onFailure { ex ->
            _state.update { it.copy(errorMessage = "Pause failed: ${ex.message}") }
        }
    }

    fun resumeRecording() {
        val mr = recorder ?: return
        if (!_state.value.isRecording || !_state.value.isPaused) return
        runCatching {
            mr.resume()
            lastTickMs = System.currentTimeMillis()
            _state.update { it.copy(isPaused = false) }
        }.onFailure { ex ->
            _state.update { it.copy(errorMessage = "Resume failed: ${ex.message}") }
        }
    }

    fun stopRecording(): String? {
        meterJob?.cancel()
        meterJob = null
        val mr = recorder
        var savedPath: String? = null
        if (mr != null) {
            runCatching {
                mr.stop()
                val file = currentOutputFile
                if (file != null && file.exists() && file.length() > 0L) {
                    savedPath = file.absolutePath
                }
            }.onFailure { ex ->
                _state.update { it.copy(errorMessage = "Recording stop warning: ${ex.message}") }
            }
            releaseRecorder()
        }
        AudioFocusCoordinator.releaseMode(context, ActiveAudioMode.RECORDING_CAPTURE)
        _state.update {
            it.copy(
                isRecording = false,
                isPaused = false,
                amplitudeNorm = 0f,
                savedFilePath = savedPath
            )
        }
        return savedPath
    }

    fun playRecording(path: String) {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            _state.update { it.copy(errorMessage = "Saved recording file does not exist.") }
            return
        }
        stopPlayback()
        AudioFocusCoordinator.acquireMode(context, ActiveAudioMode.RECORDING_PLAYBACK) {
            stopPlayback()
        }
        runCatching {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    it.start()
                    _state.update { st -> st.copy(isPlayingBack = true, errorMessage = null) }
                }
                setOnCompletionListener {
                    stopPlayback()
                }
                setOnErrorListener { _, what, extra ->
                    _state.update { st -> st.copy(isPlayingBack = false, errorMessage = "Playback failed ($what/$extra)") }
                    stopPlayback()
                    true
                }
                prepareAsync()
            }
            playbackPlayer = mp
        }.onFailure { ex ->
            AudioFocusCoordinator.releaseMode(context, ActiveAudioMode.RECORDING_PLAYBACK)
            _state.update { it.copy(isPlayingBack = false, errorMessage = "Playback error: ${ex.message}") }
        }
    }

    fun stopPlayback() {
        playbackPlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            }
        }
        playbackPlayer = null
        AudioFocusCoordinator.releaseMode(context, ActiveAudioMode.RECORDING_PLAYBACK)
        _state.update { it.copy(isPlayingBack = false) }
    }

    fun deleteRecording(path: String?) {
        stopPlayback()
        if (!path.isNullOrBlank()) {
            runCatching { File(path).delete() }
        }
        if (_state.value.savedFilePath == path) {
            _state.update { it.copy(savedFilePath = null, elapsedMs = 0L, longPauseCount = 0) }
        }
    }

    private fun startMeterLoop() {
        meterJob?.cancel()
        meterJob = scope.launch {
            while (isActive && _state.value.isRecording) {
                val now = System.currentTimeMillis()
                val delta = (now - lastTickMs).coerceIn(0L, 500L)
                lastTickMs = now

                if (!_state.value.isPaused) {
                    val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    val norm = (amp / 18000f).coerceIn(0f, 1f)
                    if (amp < 450) {
                        silentRunMs += delta
                    } else {
                        if (silentRunMs >= 2500L) {
                            _state.update { it.copy(longPauseCount = it.longPauseCount + 1) }
                        }
                        silentRunMs = 0L
                    }
                    _state.update {
                        it.copy(
                            elapsedMs = it.elapsedMs + delta,
                            amplitudeNorm = norm
                        )
                    }
                }
                delay(120L)
            }
        }
    }

    private fun releaseRecorder() {
        recorder?.let { mr ->
            runCatching { mr.reset() }
            runCatching { mr.release() }
        }
        recorder = null
    }

    fun release() {
        stopRecording()
        stopPlayback()
    }
}
