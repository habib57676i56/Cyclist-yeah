package com.example.recitation

import com.example.ai.ArabicNlpNormalizer
import com.example.quran.AyahRecord
import org.json.JSONArray
import org.json.JSONObject

enum class WordComparisonStatus {
    MATCHED,
    UNCERTAIN_REVIEW,
    OMITTED,
    ADDED,
    REPLACED,
    REPEATED
}

data class WordAssessmentItem(
    val surah: Int,
    val ayah: Int,
    val wordIndex: Int,
    val expectedWord: String,
    val spokenWord: String,
    val status: WordComparisonStatus,
    val similarity: Float
)

data class RecitationAssessmentResult(
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val recognizedTranscript: String,
    val wordItems: List<WordAssessmentItem>,
    val correctCount: Int,
    val uncertainCount: Int,
    val omittedCount: Int,
    val addedCount: Int,
    val replacedCount: Int,
    val repeatedCount: Int,
    val skippedAyahs: List<Int>,
    val orderWarning: Boolean,
    val longPauseCount: Int,
    val accuracyPercent: Int
) {
    fun toDiffJson(): String {
        val arr = JSONArray()
        wordItems.forEach { w ->
            arr.put(
                JSONObject().apply {
                    put("surah", w.surah)
                    put("ayah", w.ayah)
                    put("wordIndex", w.wordIndex)
                    put("expected", w.expectedWord)
                    put("spoken", w.spokenWord)
                    put("status", w.status.name)
                    put("similarity", w.similarity)
                }
            )
        }
        return arr.toString()
    }
}

object QuranRecitationComparator {

    private data class ExpectedToken(
        val surah: Int,
        val ayah: Int,
        val wordIndex: Int,
        val rawWord: String,
        val normalized: String
    )

    fun compare(
        expectedAyahs: List<AyahRecord>,
        recognizedTranscript: String,
        asrConfidence: Float = 0.78f,
        longPauseCount: Int = 0
    ): RecitationAssessmentResult {
        val expectedTokens = mutableListOf<ExpectedToken>()
        for (a in expectedAyahs) {
            val words = a.textUthmani.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            words.forEachIndexed { idx, w ->
                val norm = ArabicNlpNormalizer.normalizeQuranWord(w)
                if (norm.isNotBlank()) {
                    expectedTokens.add(ExpectedToken(a.surah, a.ayah, idx, w, norm))
                }
            }
        }

        val spokenRaw = recognizedTranscript.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val spokenNorm = spokenRaw.map { ArabicNlpNormalizer.normalizeQuranWord(it) }.filter { it.isNotBlank() }

        val n = expectedTokens.size
        val m = spokenNorm.size

        // Needleman-Wunsch alignment
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = -i
        for (j in 0..m) dp[0][j] = -j

        for (i in 1..n) {
            for (j in 1..m) {
                val sim = similarity(expectedTokens[i - 1].normalized, spokenNorm[j - 1])
                val subScore = when {
                    expectedTokens[i - 1].normalized == spokenNorm[j - 1] -> 4
                    sim >= 0.55f -> 1
                    else -> -3
                }
                val match = dp[i - 1][j - 1] + subScore
                val delete = dp[i - 1][j] - 1
                val insert = dp[i][j - 1] - 1
                dp[i][j] = maxOf(match, delete, insert)
            }
        }

        val aligned = mutableListOf<WordAssessmentItem>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            val diag = if (i > 0 && j > 0) {
                val sim = similarity(expectedTokens[i - 1].normalized, spokenNorm[j - 1])
                val subScore = when {
                    expectedTokens[i - 1].normalized == spokenNorm[j - 1] -> 4
                    sim >= 0.55f -> 1
                    else -> -3
                }
                dp[i - 1][j - 1] + subScore
            } else Int.MIN_VALUE

            if (i > 0 && j > 0 && dp[i][j] == diag) {
                val exp = expectedTokens[i - 1]
                val spk = spokenRaw[j - 1]
                val sim = similarity(exp.normalized, spokenNorm[j - 1])
                val status = when {
                    exp.normalized == spokenNorm[j - 1] -> WordComparisonStatus.MATCHED
                    sim >= 0.55f || asrConfidence < 0.65f -> WordComparisonStatus.UNCERTAIN_REVIEW
                    else -> WordComparisonStatus.REPLACED
                }
                aligned.add(WordAssessmentItem(exp.surah, exp.ayah, exp.wordIndex, exp.rawWord, spk, status, sim))
                i--
                j--
            } else if (i > 0 && (j == 0 || dp[i][j] == dp[i - 1][j] - 1)) {
                val exp = expectedTokens[i - 1]
                aligned.add(
                    WordAssessmentItem(exp.surah, exp.ayah, exp.wordIndex, exp.rawWord, "", WordComparisonStatus.OMITTED, 0f)
                )
                i--
            } else {
                val refAyah = expectedTokens.getOrNull((i - 1).coerceAtLeast(0))?.ayah ?: expectedAyahs.firstOrNull()?.ayah ?: 1
                val refSurah = expectedAyahs.firstOrNull()?.surah ?: 1
                val prevNorm = if (j >= 2) spokenNorm[j - 2] else ""
                val nextNorm = if (j < spokenNorm.size) spokenNorm[j] else ""
                val isRepeat = spokenNorm[j - 1] == prevNorm || spokenNorm[j - 1] == nextNorm
                aligned.add(
                    WordAssessmentItem(
                        surah = refSurah,
                        ayah = refAyah,
                        wordIndex = -1,
                        expectedWord = "",
                        spokenWord = spokenRaw[j - 1],
                        status = if (isRepeat) WordComparisonStatus.REPEATED else WordComparisonStatus.ADDED,
                        similarity = 0f
                    )
                )
                j--
            }
        }
        aligned.reverse()

        // Detect skipped Ayahs
        val skippedAyahs = expectedAyahs.filter { ayahRec ->
            val items = aligned.filter { it.ayah == ayahRec.ayah && it.expectedWord.isNotEmpty() }
            items.isNotEmpty() && items.all { it.status == WordComparisonStatus.OMITTED }
        }.map { it.ayah }

        // Detect verse order anomaly (e.g. a later Ayah spoken before an earlier Ayah)
        val matchedAyahOrder = mutableListOf<Int>()
        for (sp in spokenNorm) {
            val found = expectedTokens.firstOrNull { it.normalized == sp }?.ayah
            if (found != null && matchedAyahOrder.lastOrNull() != found) {
                matchedAyahOrder.add(found)
            }
        }
        val orderWarning = matchedAyahOrder.zipWithNext().any { (a, b) -> b < a }

        val correct = aligned.count { it.status == WordComparisonStatus.MATCHED }
        val uncertain = aligned.count { it.status == WordComparisonStatus.UNCERTAIN_REVIEW }
        val omitted = aligned.count { it.status == WordComparisonStatus.OMITTED }
        val added = aligned.count { it.status == WordComparisonStatus.ADDED }
        val replaced = aligned.count { it.status == WordComparisonStatus.REPLACED }
        val repeated = aligned.count { it.status == WordComparisonStatus.REPEATED }

        val denom = expectedTokens.size.coerceAtLeast(1)
        val accuracy = (((correct + uncertain * 0.5f) / denom) * 100f).toInt().coerceIn(0, 100)

        return RecitationAssessmentResult(
            surah = expectedAyahs.firstOrNull()?.surah ?: 1,
            startAyah = expectedAyahs.minOfOrNull { it.ayah } ?: 1,
            endAyah = expectedAyahs.maxOfOrNull { it.ayah } ?: 1,
            recognizedTranscript = recognizedTranscript,
            wordItems = aligned,
            correctCount = correct,
            uncertainCount = uncertain,
            omittedCount = omitted,
            addedCount = added,
            replacedCount = replaced,
            repeatedCount = repeated,
            skippedAyahs = skippedAyahs,
            orderWarning = orderWarning,
            longPauseCount = longPauseCount,
            accuracyPercent = accuracy
        )
    }

    fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        val dist = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length)
        return (1f - dist.toFloat() / maxLen).coerceIn(0f, 1f)
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(curr, 0, prev, 0, b.length + 1)
        }
        return prev[b.length]
    }
}
