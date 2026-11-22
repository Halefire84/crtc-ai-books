package com.crtc.books

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

data class GenerationProgress(
    val bookId: String = "",
    val chunk: Int = 0,
    val total: Int = 0,
    val done: Boolean = false,
    val error: String? = null,
) {
    val fraction get() = if (total > 0) chunk.toFloat() / total else 0f
    val pct get() = (fraction * 100).toInt()
}

class AudioGenerator(private val context: Context) {

    private val _progress = MutableStateFlow(GenerationProgress())
    val progress: StateFlow<GenerationProgress> = _progress

    fun outputDir(bookId: String): File =
        File(context.getExternalFilesDir(null), "audiobooks/$bookId").also { it.mkdirs() }

    fun isGenerated(bookId: String): Boolean =
        outputDir(bookId).listFiles()?.any { it.extension == "wav" } == true

    fun chapterFiles(bookId: String): List<File> =
        outputDir(bookId).listFiles()?.filter { it.extension == "wav" }?.sortedBy { it.name } ?: emptyList()

    suspend fun generate(bookId: String, fullText: String) {
        val dir = outputDir(bookId)
        val chunks = splitText(fullText)
        _progress.value = GenerationProgress(bookId, 0, chunks.size)

        val tts = initTts() ?: run {
            _progress.value = _progress.value.copy(error = "TTS engine not available")
            return
        }

        try {
            chunks.forEachIndexed { i, chunk ->
                val outFile = File(dir, "ch${(i + 1).toString().padStart(3, '0')}.wav")
                if (!outFile.exists()) {
                    synthesizeToFile(tts, chunk, outFile)
                }
                _progress.value = GenerationProgress(bookId, i + 1, chunks.size)
            }
            _progress.value = GenerationProgress(bookId, chunks.size, chunks.size, done = true)
        } finally {
            tts.shutdown()
        }
    }

    private suspend fun initTts(): TextToSpeech? =
        suspendCancellableCoroutine { cont ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.setSpeechRate(0.95f)
                    cont.resume(tts)
                } else {
                    cont.resume(null)
                }
            }
        }

    private suspend fun synthesizeToFile(tts: TextToSpeech, text: String, file: File) =
        suspendCancellableCoroutine { cont ->
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, file.name)
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { cont.resume(Unit) }
                override fun onError(id: String?) { cont.resume(Unit) }
            })
            tts.synthesizeToFile(text, params, file, file.name)
        }

    private fun splitText(text: String): List<String> {
        val result = mutableListOf<String>()
        var remaining = text.trim()
        val limit = 3800
        while (remaining.length > limit) {
            val cut = remaining.lastIndexOf(". ", limit)
                .takeIf { it > limit / 2 } ?: remaining.lastIndexOf(' ', limit).takeIf { it > 0 } ?: limit
            result.add(remaining.substring(0, cut + 1).trim())
            remaining = remaining.substring(cut + 1).trim()
        }
        if (remaining.isNotBlank()) result.add(remaining)
        return result
    }
}
