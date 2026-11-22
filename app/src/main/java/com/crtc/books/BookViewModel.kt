package com.crtc.books

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BookViewModel(
    private val ctx: Context,
    val book: Book,
    val billing: BillingManager
) : ViewModel() {

    private val tts = TtsReader(ctx)
    private val gen = AudioGenerator(ctx)
    private val voice = VoiceCommandManager(ctx)

    val ttsState: StateFlow<TtsState> = tts.state
    val ttsProgress: StateFlow<Float> = tts.progress
    val speedIndex: StateFlow<Int> = tts.speedIndex
    val genProgress: StateFlow<GenerationProgress> = gen.progress
    val isPro: StateFlow<Boolean> = billing.isPro
    val voiceListening: StateFlow<Boolean> = voice.listening
    val voiceError: StateFlow<String?> = voice.error

    private val _importedBook = MutableStateFlow<ImportedBook?>(null)
    val importedBook: StateFlow<ImportedBook?> = _importedBook

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex

    private val _chapters = MutableStateFlow<List<EpubChapter>>(emptyList())
    val chapters: StateFlow<List<EpubChapter>> = _chapters

    private val _summaries = MutableStateFlow<Map<String, String>>(emptyMap())
    val summaries: StateFlow<Map<String, String>> = _summaries

    private val _dueCardCount = MutableStateFlow(0)
    val dueCardCount: StateFlow<Int> = _dueCardCount

    private val _sleepMinutes = MutableStateFlow(0)
    val sleepMinutes: StateFlow<Int> = _sleepMinutes

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(loadBookmarks())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks

    private val sampleText = """
        ${book.title.replace('\n', ' ')} by Chris Hale.
        ${book.subtitle}.
        ${book.tagline}

        This is a preview of ${book.title.replace('\n', ' ')}.
        To read the full book, tap Get the Book below and get instant access to ${book.words}.

        CRTC AI — crtcai.com — Mount Pleasant, South Carolina.
        Real automation. Real results. No hype.
    """.trimIndent()

    init {
        tts.onChapterDone = { onChapterFinished() }
        refreshDueCount()
    }

    private fun currentText(): String {
        val ch = _chapters.value
        return if (ch.isNotEmpty() && _currentChapterIndex.value < ch.size)
            ch[_currentChapterIndex.value].body
        else sampleText
    }

    fun play() {
        if (tts.state.value == TtsState.PAUSED) tts.resume()
        else tts.read(currentText())
    }

    fun pause() = tts.pause()
    fun stop() = tts.stop()
    fun skipForward() = tts.skipForward()
    fun skipBack() = tts.skipBack()

    fun setSpeed(index: Int) = tts.setSpeedIndex(index)
    fun speedUp() = tts.speedUp()
    fun speedDown() = tts.speedDown()

    fun setSleepTimer(minutes: Int) {
        _sleepMinutes.value = minutes
        tts.setSleepTimer(minutes, viewModelScope)
    }

    fun startVoiceCommand() {
        voice.startListening { cmd ->
            when (cmd) {
                VoiceCommand.PLAY -> play()
                VoiceCommand.PAUSE -> pause()
                VoiceCommand.STOP -> stop()
                VoiceCommand.SKIP_FORWARD -> skipForward()
                VoiceCommand.SKIP_BACK -> skipBack()
                VoiceCommand.SPEED_UP -> speedUp()
                VoiceCommand.SPEED_DOWN -> speedDown()
                VoiceCommand.NONE -> {}
            }
        }
    }

    fun stopVoiceCommand() = voice.stopListening()

    fun generateAudio() {
        viewModelScope.launch { gen.generate(book.id, currentText()) }
    }

    fun importEpub(imported: ImportedBook) {
        _importedBook.value = imported
        _chapters.value = imported.chapters
        _currentChapterIndex.value = 0
        generateSummariesFor(imported.chapters)
        generateQuizCardsFor(imported.chapters)
    }

    fun jumpToChapter(index: Int) {
        tts.stop()
        _currentChapterIndex.value = index.coerceIn(0, _chapters.value.size - 1)
    }

    fun nextChapter() {
        val next = _currentChapterIndex.value + 1
        if (next < _chapters.value.size) {
            _currentChapterIndex.value = next
        }
    }

    fun addBookmark() {
        val ch = _chapters.value
        val label = if (ch.isNotEmpty()) ch[_currentChapterIndex.value].title else book.title
        val bm = Bookmark(System.currentTimeMillis(), book.id, label, _currentChapterIndex.value)
        val list = (_bookmarks.value + bm).takeLast(20)
        _bookmarks.value = list
        ctx.getSharedPreferences("bookmarks_${book.id}", Context.MODE_PRIVATE)
            .edit().putString("list", com.google.gson.Gson().toJson(list)).apply()
    }

    private fun loadBookmarks(): List<Bookmark> {
        val json = ctx.getSharedPreferences("bookmarks_${book.id}", Context.MODE_PRIVATE)
            .getString("list", "[]") ?: "[]"
        return try {
            com.google.gson.Gson().fromJson(json, Array<Bookmark>::class.java).toList()
        } catch (e: Exception) { emptyList() }
    }

    private fun generateSummariesFor(chapters: List<EpubChapter>) {
        viewModelScope.launch {
            val map = mutableMapOf<String, String>()
            for (ch in chapters) {
                map[ch.title] = ChapterSummaryEngine.summarize(ch.body)
            }
            _summaries.value = map
        }
    }

    private fun generateQuizCardsFor(chapters: List<EpubChapter>) {
        viewModelScope.launch {
            val cards = chapters.flatMap { ch ->
                ChapterSummaryEngine.generateQuizCards(book.id, ch.title, ch.body)
            }
            SpacedRepetitionEngine.addCards(ctx, cards)
            refreshDueCount()
        }
    }

    private fun onChapterFinished() {
        val ch = _chapters.value
        val cur = _currentChapterIndex.value
        val summary = summaries.value[ch.getOrNull(cur)?.title]
        if (summary != null && ch.isNotEmpty()) {
            // Auto-advance to next chapter
            if (cur + 1 < ch.size) {
                _currentChapterIndex.value = cur + 1
            }
        }
        refreshDueCount()
    }

    fun refreshDueCount() {
        _dueCardCount.value = SpacedRepetitionEngine.dueCards(ctx, book.id).size
    }

    override fun onCleared() {
        super.onCleared()
        tts.shutdown()
        voice.destroy()
    }
}

data class Bookmark(val timestampMs: Long, val bookId: String, val chapterTitle: String, val chapterIndex: Int)

class BookViewModelFactory(
    private val ctx: Context,
    private val book: Book,
    private val billing: BillingManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BookViewModel(ctx, book, billing) as T
    }
}
