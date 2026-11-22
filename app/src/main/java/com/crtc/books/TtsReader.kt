package com.crtc.books

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class TtsState { IDLE, PLAYING, PAUSED, ERROR }

val SPEED_STEPS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
val SPEED_LABELS = listOf("0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×")

class TtsReader(context: Context) {

    private var tts: TextToSpeech? = null
    private val chunks = mutableListOf<String>()
    private var cursor = 0
    private var speedRate = 1.0f
    private var sleepTimerJob: Job? = null
    var onChapterDone: (() -> Unit)? = null

    private val _state = MutableStateFlow(TtsState.IDLE)
    val state: StateFlow<TtsState> = _state

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _speedIndex = MutableStateFlow(2) // 1.0x default
    val speedIndex: StateFlow<Int> = _speedIndex

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(speedRate)
                tts?.setPitch(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        cursor++
                        if (cursor < chunks.size) {
                            speakChunk()
                            _progress.value = cursor.toFloat() / chunks.size
                        } else {
                            _state.value = TtsState.IDLE
                            _progress.value = 1f
                            onChapterDone?.invoke()
                        }
                    }
                    override fun onError(id: String?) { _state.value = TtsState.ERROR }
                })
            } else {
                _state.value = TtsState.ERROR
            }
        }
    }

    fun read(text: String) {
        stop()
        chunks.clear()
        // Split into ~4000 char chunks at sentence boundaries
        var remaining = text
        while (remaining.length > 4000) {
            val cut = remaining.lastIndexOf('. ', 4000).takeIf { it > 2000 } ?: 4000
            chunks.add(remaining.substring(0, cut + 1).trim())
            remaining = remaining.substring(cut + 1).trim()
        }
        if (remaining.isNotBlank()) chunks.add(remaining)
        cursor = 0
        _state.value = TtsState.PLAYING
        speakChunk()
    }

    fun pause() {
        if (_state.value == TtsState.PLAYING) {
            tts?.stop()
            _state.value = TtsState.PAUSED
        }
    }

    fun resume() {
        if (_state.value == TtsState.PAUSED && cursor < chunks.size) {
            _state.value = TtsState.PLAYING
            speakChunk()
        }
    }

    fun stop() {
        sleepTimerJob?.cancel()
        tts?.stop()
        chunks.clear()
        cursor = 0
        _state.value = TtsState.IDLE
        _progress.value = 0f
    }

    fun skipForward() {
        tts?.stop()
        cursor = (cursor + 5).coerceAtMost(chunks.size - 1)
        if (_state.value == TtsState.PLAYING) speakChunk()
        _progress.value = cursor.toFloat() / chunks.size.coerceAtLeast(1)
    }

    fun skipBack() {
        tts?.stop()
        cursor = (cursor - 5).coerceAtLeast(0)
        if (_state.value == TtsState.PLAYING) speakChunk()
        _progress.value = cursor.toFloat() / chunks.size.coerceAtLeast(1)
    }

    fun speedUp() {
        val next = (_speedIndex.value + 1).coerceAtMost(SPEED_STEPS.size - 1)
        setSpeedIndex(next)
    }

    fun speedDown() {
        val next = (_speedIndex.value - 1).coerceAtLeast(0)
        setSpeedIndex(next)
    }

    fun setSpeedIndex(index: Int) {
        _speedIndex.value = index
        speedRate = SPEED_STEPS[index]
        tts?.setSpeechRate(speedRate)
    }

    fun setSleepTimer(minutes: Int, scope: CoroutineScope) {
        sleepTimerJob?.cancel()
        if (minutes > 0) {
            sleepTimerJob = scope.launch {
                delay(minutes * 60_000L)
                stop()
            }
        }
    }

    private fun speakChunk() {
        tts?.speak(chunks[cursor], TextToSpeech.QUEUE_FLUSH, null, "chunk_$cursor")
    }

    fun shutdown() {
        sleepTimerJob?.cancel()
        tts?.stop()
        tts?.shutdown()
    }
}
