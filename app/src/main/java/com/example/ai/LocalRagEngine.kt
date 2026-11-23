package com.example.ai

import com.example.data.local.KnowledgeChunkEntity
import com.example.data.local.KnowledgeDao
import com.example.data.local.KnowledgeEntryEntity
import kotlin.math.ln

data class RagCitation(
    val title: String,
    val reference: String,
    val category: String,
    val score: Double
)

data class NoorAiAnswer(
    val answerText: String,
    val isGrounded: Boolean,
    val citations: List<RagCitation>,
    val modeLabel: String
)

/**
 * Pure offline Local Retrieval-Augmented Generation (RAG) engine.
 * Searches ONLY admin-approved KnowledgeChunks in Room using BM25 + keyword overlap.
 * Never fabricates Quran verses, Hadith, or religious rulings.
 */
class LocalRagEngine(private val knowledgeDao: KnowledgeDao) {

    suspend fun chunkAndIndexEntry(entry: KnowledgeEntryEntity) {
        knowledgeDao.deleteChunksForEntry(entry.id)
        if (entry.approvalStatus != "APPROVED") return

        val sections = splitIntoSearchableSections(entry.answer, maxChars = 360, overlapChars = 60)
        val chunks = sections.mapIndexed { idx, sectionText ->
            val combined = "${entry.title} ${entry.question} $sectionText ${entry.keywords}"
            val tokens = ArabicNlpNormalizer.normalizeAndTokenize(combined)
            KnowledgeChunkEntity(
                entryId = entry.id,
                chunkIndex = idx,
                title = entry.title,
                category = entry.category,
                content = sectionText,
                normalizedTokens = tokens.joinToString(" "),
                keywords = entry.keywords,
                reference = entry.reference,
                isApproved = true
            )
        }
        if (chunks.isNotEmpty()) {
            knowledgeDao.insertChunks(chunks)
        }
    }

    suspend fun rebuildFullSearchIndex(): Int {
        knowledgeDao.deleteAllChunks()
        val approved = knowledgeDao.getApprovedEntries()
        var totalChunks = 0
        for (entry in approved) {
            val sections = splitIntoSearchableSections(entry.answer, maxChars = 360, overlapChars = 60)
            val chunks = sections.mapIndexed { idx, sectionText ->
                val combined = "${entry.title} ${entry.question} $sectionText ${entry.keywords}"
                val tokens = ArabicNlpNormalizer.normalizeAndTokenize(combined)
                KnowledgeChunkEntity(
                    entryId = entry.id,
                    chunkIndex = idx,
                    title = entry.title,
                    category = entry.category,
                    content = sectionText,
                    normalizedTokens = tokens.joinToString(" "),
                    keywords = entry.keywords,
                    reference = entry.reference,
                    isApproved = true
                )
            }
            knowledgeDao.insertChunks(chunks)
            totalChunks += chunks.size
        }
        return totalChunks
    }

    suspend fun answerQuestion(query: String, isArabicUi: Boolean = true): NoorAiAnswer {
        val queryTokens = ArabicNlpNormalizer.normalizeAndTokenize(query)
        val rawNormQuery = ArabicNlpNormalizer.normalizeArabicText(query)
        val chunks = knowledgeDao.getApprovedChunks()

        if (chunks.isEmpty() || queryTokens.isEmpty()) {
            return uncertainResponse(isArabicUi)
        }

        // BM25 scoring across all approved local chunks
        val docTokensList = chunks.map { it.normalizedTokens.split(" ").filter { t -> t.isNotBlank() } }
        val avgLen = docTokensList.map { it.size }.average().coerceAtLeast(1.0)
        val nDocs = chunks.size.toDouble()

        val df = mutableMapOf<String, Int>()
        for (docTokens in docTokensList) {
            for (t in docTokens.toSet()) {
                df[t] = (df[t] ?: 0) + 1
            }
        }

        val k1 = 1.5
        val b = 0.75

        val scored = chunks.mapIndexed { idx, chunk ->
            val docTokens = docTokensList[idx]
            val docLen = docTokens.size.toDouble().coerceAtLeast(1.0)
            var score = 0.0

            val tfMap = docTokens.groupingBy { it }.eachCount()
            for (qt in queryTokens) {
                val freq = (tfMap[qt] ?: 0).toDouble()
                if (freq > 0) {
                    val docFreq = (df[qt] ?: 0).toDouble()
                    val idf = ln((nDocs - docFreq + 0.5) / (docFreq + 0.5) + 1.0).coerceAtLeast(0.2)
                    val num = freq * (k1 + 1.0)
                    val den = freq + k1 * (1.0 - b + b * (docLen / avgLen))
                    score += idf * (num / den)
                }
            }

            // Explicit keyword boost
            val kwTokens = ArabicNlpNormalizer.normalizeAndTokenize(chunk.keywords)
            val kwOverlap = queryTokens.count { it in kwTokens }
            score += kwOverlap * 1.35

            // Title phrase boost
            val normTitle = ArabicNlpNormalizer.normalizeArabicText(chunk.title)
            if (rawNormQuery.length >= 4 && normTitle.contains(rawNormQuery)) {
                score += 2.5
            }

            chunk to score
        }.filter { it.second >= 0.45 }
            .sortedByDescending { it.second }
            .take(3)

        if (scored.isEmpty()) {
            return uncertainResponse(isArabicUi)
        }

        val citations = scored.map { (c, s) ->
            RagCitation(title = c.title, reference = c.reference, category = c.category, score = s)
        }.distinctBy { it.title to it.reference }

        val sb = StringBuilder()
        if (isArabicUi) {
            sb.append("استناداً إلى قاعدة المعرفة المعتمدة من الإدارة:\n\n")
            scored.forEachIndexed { idx, (chunk, _) ->
                if (idx == 0) {
                    sb.append("• ${chunk.title}: ${chunk.content}\n")
                } else {
                    sb.append("\n• إضافة ذات صلة (${chunk.title}): ${chunk.content}\n")
                }
            }
            sb.append("\nالمراجع المعتمدة:\n")
            citations.forEach { c ->
                sb.append("- ${c.title} (${c.reference})\n")
            }
        } else {
            sb.append("Based on administrator-approved NOOR knowledge:\n\n")
            scored.forEachIndexed { idx, (chunk, _) ->
                if (idx == 0) {
                    sb.append("• ${chunk.title}: ${chunk.content}\n")
                } else {
                    sb.append("\n• Related (${chunk.title}): ${chunk.content}\n")
                }
            }
            sb.append("\nVerified References:\n")
            citations.forEach { c ->
                sb.append("- ${c.title} (${c.reference})\n")
            }
        }

        return NoorAiAnswer(
            answerText = sb.toString().trim(),
            isGrounded = true,
            citations = citations,
            modeLabel = if (isArabicUi) "بحث دلالي محلي (RAG)" else "Local RAG Retrieval"
        )
    }

    private fun uncertainResponse(isArabicUi: Boolean): NoorAiAnswer {
        val msg = if (isArabicUi) {
            "عذراً، لا تتوفر لديّ معلومات معتمدة كافية للإجابة عن هذا السؤال في قاعدة المعرفة المحلية.\n\n" +
                "• لا يُصدر مساعد نور أحكاماً شرعية أو يختلق آيات أو أحاديث دون مصدر معتمد.\n" +
                "• يمكنك الطلب من المشرف العام إضافة المحتوى أو استيراد ملفات (TXT / JSON / PDF / DOCX) في لوحة التحكم."
        } else {
            "I do not have enough administrator-approved information in the local knowledge base to answer this reliably.\n\n" +
                "• NOOR AI never invents Quranic verses, Hadith, or religious rulings.\n" +
                "• A Super Admin can add Q&A entries or import TXT/JSON/PDF/DOCX documents in Admin > AI Control."
        }
        return NoorAiAnswer(
            answerText = msg,
            isGrounded = false,
            citations = emptyList(),
            modeLabel = if (isArabicUi) "تثبّت من المصادر" else "Uncertain / No Match"
        )
    }

    fun splitIntoSearchableSections(text: String, maxChars: Int = 360, overlapChars: Int = 60): List<String> {
        val clean = text.trim()
        if (clean.length <= maxChars) return listOf(clean)
        val sections = mutableListOf<String>()
        var start = 0
        while (start < clean.length) {
            var end = (start + maxChars).coerceAtMost(clean.length)
            if (end < clean.length) {
                val spaceIdx = clean.lastIndexOf(' ', end)
                if (spaceIdx > start + maxChars / 2) {
                    end = spaceIdx
                }
            }
            sections.add(clean.substring(start, end).trim())
            if (end >= clean.length) break
            start = (end - overlapChars).coerceAtLeast(start + 1)
        }
        return sections.filter { it.isNotBlank() }
    }
}
