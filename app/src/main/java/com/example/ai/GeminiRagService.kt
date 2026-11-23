package com.example.ai

import android.util.Log
import com.example.firebase.FsApprovedKnowledgeSource
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiRagService"

sealed interface AiStreamState {
    data object Idle : AiStreamState
    data object Loading : AiStreamState
    data class Streaming(val text: String, val citations: List<String>) : AiStreamState
    data class Success(val text: String, val citations: List<String>, val usedGemini: Boolean, val grounded: Boolean) : AiStreamState
    data class Error(val message: String, val localFallback: NoorAiAnswer?) : AiStreamState
}

/**
 * Grounded NOOR AI engine pairing Local RAG retrieval (Room + Firestore approved_knowledge_sources)
 * with Firebase AI Logic (gemini-3.8-flash) protected by App Check.
 * Refuses to invent Quranic verses, Hadith, or unverified claims.
 */
class GeminiRagService(private val localRagEngine: LocalRagEngine) {

    // Lazy initialization per Firebase AI Logic contract (avoids eager evaluation before App Check).
    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    suspend fun askWithRagStream(
        question: String,
        isArabicUi: Boolean,
        remoteApprovedSources: List<FsApprovedKnowledgeSource>,
        onProgress: (AiStreamState) -> Unit
    ): AiStreamState {
        onProgress(AiStreamState.Loading)

        // 1. Always search local approved knowledge base first
        val localAnswer = localRagEngine.answerQuestion(question, isArabicUi)

        // 2. Also match against Firestore approved_knowledge_sources
        val tokens = ArabicNlpNormalizer.normalizeAndTokenize(question)
        val remoteMatches = remoteApprovedSources
            .filter { it.approvalStatus == "APPROVED" }
            .map { src ->
                val searchable = ArabicNlpNormalizer.normalizeArabicText("${src.title} ${src.question.orEmpty()} ${src.answer} ${src.keywords.orEmpty()}")
                val score = tokens.count { tok -> searchable.contains(tok) }
                src to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }

        val combinedCitations = (localAnswer.citations.map { "${it.title} (${it.reference})" } +
            remoteMatches.map { "${it.title} (${it.reference})" }).distinct()
        val evidenceContext = buildString {
            if (localAnswer.isGrounded) {
                appendLine("=== Local Approved Knowledge Base ===")
                appendLine(localAnswer.answerText)
            }
            if (remoteMatches.isNotEmpty()) {
                appendLine("=== Cloud Approved Knowledge Base ===")
                remoteMatches.forEach { src ->
                    appendLine("Title: ${src.title}")
                    appendLine("Category: ${src.category}")
                    appendLine("Reference: ${src.reference}")
                    appendLine("Passage: ${src.answer}")
                    appendLine("---")
                }
            }
        }.trim()

        val groundedPrompt = buildString {
            appendLine("You are NOOR AI, an Islamic educational assistant inside the NOOR Quran app.")
            appendLine("STRICT SCHOLARLY & ACCURACY RULES:")
            appendLine("1. Base your answer strictly on the Retrieved Approved Sources provided below when available.")
            appendLine("2. NEVER invent, paraphrase as verbatim, or fabricate Quranic verses, Hadith chains, or scholarly references.")
            appendLine("3. NEVER alter canonical Quranic spelling or diacritics.")
            appendLine("4. Cite ONLY references explicitly present in the Retrieved Approved Sources.")
            appendLine("5. If the Retrieved Approved Sources do not contain sufficient verified evidence to answer reliably, clearly state that this specific ruling/detail is not covered in NOOR's approved references yet and recommend consulting qualified scholars.")
            appendLine("6. Reply clearly in ${if (isArabicUi) "Arabic" else "English"} matching the user's question language.")
            appendLine()
            appendLine("User Question: $question")
            appendLine()
            if (evidenceContext.isNotEmpty()) {
                appendLine("Retrieved Approved Sources:")
                appendLine(evidenceContext)
            } else {
                appendLine("Retrieved Approved Sources: NONE FOUND IN APPROVED DATABASE.")
            }
        }

        val accumulated = StringBuilder()
        return try {
            withTimeout(60_000L) {
                model.generateContentStream(groundedPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        onProgress(AiStreamState.Streaming(accumulated.toString(), combinedCitations))
                    }
                }
            }
            val finalText = accumulated.toString().trim()
            if (finalText.isNotBlank()) {
                val res = AiStreamState.Success(
                    text = finalText,
                    citations = combinedCitations,
                    usedGemini = true,
                    grounded = combinedCitations.isNotEmpty()
                )
                onProgress(res)
                res
            } else {
                val err = AiStreamState.Error("Gemini returned an empty response.", localAnswer.takeIf { it.isGrounded })
                onProgress(err)
                err
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI generation timed out", e)
            val err = AiStreamState.Error(
                if (isArabicUi) "انتهت مهلة الاتصال بالذكاء الاصطناعي (60 ثانية). يتم عرض النتائج المحلية المعتمدة إن وجدت."
                else "AI generation timed out after 60 seconds. Showing verified offline RAG results if available.",
                localAnswer.takeIf { it.isGrounded }
            )
            onProgress(err)
            err
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            val err = AiStreamState.Error(
                e.localizedMessage ?: "AI request failed (check network or App Check attestation).",
                localAnswer.takeIf { it.isGrounded }
            )
            onProgress(err)
            err
        }
    }
}
