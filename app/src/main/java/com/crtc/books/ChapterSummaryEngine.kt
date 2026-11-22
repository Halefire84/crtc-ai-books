package com.crtc.books

object ChapterSummaryEngine {

    fun summarize(text: String, maxSentences: Int = 5): String {
        val sentences = splitSentences(text)
        if (sentences.size <= maxSentences) return sentences.joinToString(" ")

        val scored = sentences.mapIndexed { i, s ->
            val posScore = when {
                i < sentences.size * 0.2 -> 2.0
                i > sentences.size * 0.8 -> 1.5
                else -> 1.0
            }
            val lenScore = if (s.length in 60..200) 1.3 else 1.0
            Triple(i, s, posScore * lenScore)
        }

        return scored.sortedByDescending { it.third }
            .take(maxSentences)
            .sortedBy { it.first }
            .joinToString(" ") { it.second }
    }

    fun generateQuizCards(bookId: String, chapterTitle: String, text: String): List<QuizCard> {
        val sentences = splitSentences(text).filter { it.length in 60..260 }
        if (sentences.isEmpty()) return emptyList()

        val count = minOf(3, sentences.size)
        val step = sentences.size / count
        return (0 until count).mapNotNull { n ->
            val sentence = sentences[(n * step).coerceAtMost(sentences.size - 1)]
            val words = sentence.split(" ").filter { it.length > 2 }
            if (words.size < 4) return@mapNotNull null
            val blankIdx = (words.size * 0.65).toInt().coerceAtLeast(1)
            val answer = words[blankIdx].trimEnd('.', ',', '!', '?', ';', ':')
            val blanked = words.mapIndexed { i, w ->
                if (i == blankIdx) "______" else w
            }.joinToString(" ")
            QuizCard(
                id = "${bookId}_${chapterTitle.hashCode()}_$n",
                bookId = bookId,
                chapterTitle = chapterTitle,
                question = "Fill in the blank:\n$blanked",
                answer = answer
            )
        }
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+(?=[A-Z])"))
            .map { it.trim() }
            .filter { it.length > 25 }
}
