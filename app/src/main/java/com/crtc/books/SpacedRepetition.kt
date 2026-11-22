package com.crtc.books

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class QuizCard(
    val id: String,
    val bookId: String,
    val chapterTitle: String,
    val question: String,
    val answer: String,
    var dueMillis: Long = System.currentTimeMillis(),
    var interval: Int = 1,
    var easeFactor: Float = 2.5f,
    var repetitions: Int = 0
)

enum class ReviewResult { AGAIN, HARD, GOOD, EASY }

object SpacedRepetitionEngine {

    private val gson = Gson()
    private const val PREFS_KEY = "quiz_cards"

    fun getCards(ctx: Context): List<QuizCard> {
        val json = ctx.getSharedPreferences("srs", Context.MODE_PRIVATE)
            .getString(PREFS_KEY, "[]") ?: "[]"
        val type = object : TypeToken<List<QuizCard>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveCards(ctx: Context, cards: List<QuizCard>) {
        ctx.getSharedPreferences("srs", Context.MODE_PRIVATE)
            .edit().putString(PREFS_KEY, gson.toJson(cards)).apply()
    }

    fun addCards(ctx: Context, newCards: List<QuizCard>) {
        val existing = getCards(ctx).associateBy { it.id }.toMutableMap()
        newCards.forEach { if (!existing.containsKey(it.id)) existing[it.id] = it }
        saveCards(ctx, existing.values.toList())
    }

    fun dueCards(ctx: Context, bookId: String? = null): List<QuizCard> {
        val now = System.currentTimeMillis()
        return getCards(ctx)
            .filter { it.dueMillis <= now && (bookId == null || it.bookId == bookId) }
            .sortedBy { it.dueMillis }
    }

    fun totalCards(ctx: Context, bookId: String): Int =
        getCards(ctx).count { it.bookId == bookId }

    fun reviewCard(ctx: Context, card: QuizCard, result: ReviewResult): QuizCard {
        val DAY = 86_400_000L
        val updated = when (result) {
            ReviewResult.AGAIN -> card.copy(
                interval = 1,
                repetitions = 0,
                easeFactor = (card.easeFactor - 0.2f).coerceAtLeast(1.3f),
                dueMillis = System.currentTimeMillis() + 10 * 60_000L
            )
            ReviewResult.HARD -> card.copy(
                interval = (card.interval * 1.2f).toInt().coerceAtLeast(1),
                repetitions = card.repetitions + 1,
                easeFactor = (card.easeFactor - 0.15f).coerceAtLeast(1.3f),
                dueMillis = System.currentTimeMillis() + card.interval * DAY
            )
            ReviewResult.GOOD -> {
                val ni = when (card.repetitions) { 0 -> 1; 1 -> 6; else -> (card.interval * card.easeFactor).toInt() }
                card.copy(interval = ni, repetitions = card.repetitions + 1,
                    dueMillis = System.currentTimeMillis() + ni * DAY)
            }
            ReviewResult.EASY -> {
                val ni = ((card.interval * card.easeFactor) * 1.3f).toInt().coerceAtLeast(4)
                card.copy(interval = ni, repetitions = card.repetitions + 1,
                    easeFactor = card.easeFactor + 0.15f,
                    dueMillis = System.currentTimeMillis() + ni * DAY)
            }
        }
        val all = getCards(ctx).map { if (it.id == card.id) updated else it }
        saveCards(ctx, all)
        return updated
    }
}
