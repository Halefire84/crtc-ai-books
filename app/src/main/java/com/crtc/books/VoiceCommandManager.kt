package com.crtc.books

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class VoiceCommand { PLAY, PAUSE, STOP, SKIP_FORWARD, SKIP_BACK, SPEED_UP, SPEED_DOWN, NONE }

class VoiceCommandManager(private val ctx: Context) {

    private var recognizer: SpeechRecognizer? = null

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    private val _lastCommand = MutableStateFlow(VoiceCommand.NONE)
    val lastCommand: StateFlow<VoiceCommand> = _lastCommand

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun startListening(onCommand: (VoiceCommand) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            _error.value = "Speech recognition not available on this device"
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { _listening.value = true; _error.value = null }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { _listening.value = false }
                override fun onResults(results: Bundle?) {
                    _listening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val cmd = matches?.firstNotNullOfOrNull { parseCommand(it) } ?: VoiceCommand.NONE
                    if (cmd != VoiceCommand.NONE) {
                        _lastCommand.value = cmd
                        onCommand(cmd)
                    } else {
                        _error.value = "Didn't catch that — try: play, pause, stop, faster, slower, skip"
                    }
                }
                override fun onError(error: Int) {
                    _listening.value = false
                    _error.value = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match — try again"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout — tap mic and speak"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
                        else -> "Recognition error ($error)"
                    }
                }
                override fun onPartialResults(partial: Bundle?) {}
                override fun onEvent(type: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
        }
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        _listening.value = false
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun parseCommand(text: String): VoiceCommand? {
        val t = text.lowercase().trim()
        return when {
            t.contains("play") || t.contains("start") || t.contains("resume") || t.contains("continue") -> VoiceCommand.PLAY
            t.contains("pause") || t.contains("hold") || t.contains("wait") -> VoiceCommand.PAUSE
            t.contains("stop") -> VoiceCommand.STOP
            t.contains("skip") || t.contains("forward") || t.contains("next") || t.contains("ahead") -> VoiceCommand.SKIP_FORWARD
            t.contains("back") || t.contains("rewind") || t.contains("previous") || t.contains("again") -> VoiceCommand.SKIP_BACK
            t.contains("fast") || t.contains("speed up") || t.contains("quicker") || t.contains("faster") -> VoiceCommand.SPEED_UP
            t.contains("slow") || t.contains("slower") || t.contains("slow down") -> VoiceCommand.SPEED_DOWN
            else -> null
        }
    }
}
