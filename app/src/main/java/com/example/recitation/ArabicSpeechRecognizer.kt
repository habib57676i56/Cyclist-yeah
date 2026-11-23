package com.example.recitation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class SpeechRecognitionState(
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val confidenceScore: Float = 0f,
    val engineAvailable: Boolean = true,
    val offlineModelInstalled: Boolean = false,
    val errorReason: String? = null
)

/**
 * Real Arabic Speech-to-Text wrapper around Android's SpeechRecognizer (ar-SA)
 * with offline model directory check and explicit error diagnostics.
 */
class ArabicSpeechRecognizer(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow(
        SpeechRecognitionState(
            engineAvailable = SpeechRecognizer.isRecognitionAvailable(context),
            offlineModelInstalled = hasLocalSttModelPack(context)
        )
    )
    val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

    companion object {
        fun sttModelsDir(context: Context): File = File(context.filesDir, "stt_models")

        fun hasLocalSttModelPack(context: Context): Boolean {
            val dir = sttModelsDir(context)
            return dir.exists() && (dir.listFiles()?.isNotEmpty() == true)
        }

        fun mapSpeechError(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio capture failed on microphone input."
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client stopped unexpectedly."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "RECORD_AUDIO permission is required for Arabic speech recognition."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Arabic speech recognizer requires either an installed offline Arabic voice pack on this device or an active internet connection."
            SpeechRecognizer.ERROR_NO_MATCH -> "No clear Arabic speech matched. Please recite closer to the microphone and retry."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is currently busy; try again."
            SpeechRecognizer.ERROR_SERVER -> "Speech recognition service error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected before timeout."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "Arabic (ar-SA) language pack is not installed on this device's Speech Services."
            else -> "Speech recognition failed (code $error)."
        }
    }

    fun startListening(preferOffline: Boolean = true) {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        _state.update {
            it.copy(
                engineAvailable = available,
                offlineModelInstalled = hasLocalSttModelPack(context),
                errorReason = null
            )
        }
        if (!available) {
            _state.update {
                it.copy(
                    isListening = false,
                    errorReason = "No Android SpeechRecognizer service is available on this device. Install Google Speech Services offline Arabic pack or import a local acoustic model in Admin."
                )
            }
            return
        }

        destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.update { it.copy(isListening = true, errorReason = null) }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _state.update { it.copy(isListening = false) }
            }

            override fun onError(error: Int) {
                _state.update {
                    it.copy(
                        isListening = false,
                        errorReason = mapSpeechError(error)
                    )
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                val top = matches?.firstOrNull()?.trim().orEmpty()
                val conf = scores?.firstOrNull() ?: if (top.isNotEmpty()) 0.78f else 0f
                if (top.isEmpty()) {
                    _state.update {
                        it.copy(
                            isListening = false,
                            errorReason = "Speech engine returned an empty transcript. Speak clearly or check Arabic language pack."
                        )
                    }
                } else {
                    _state.update { st ->
                        val merged = if (st.finalTranscript.isBlank()) top else "${st.finalTranscript} $top"
                        st.copy(
                            isListening = false,
                            finalTranscript = merged.trim(),
                            partialTranscript = "",
                            confidenceScore = conf,
                            errorReason = null
                        )
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (partial.isNotEmpty()) {
                    _state.update { it.copy(partialTranscript = partial) }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        sr.startListening(intent)
    }

    fun stopListening() {
        runCatching { speechRecognizer?.stopListening() }
        _state.update { it.copy(isListening = false) }
    }

    fun clearTranscript() {
        _state.update { it.copy(partialTranscript = "", finalTranscript = "", errorReason = null) }
    }

    fun destroy() {
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
    }
}
