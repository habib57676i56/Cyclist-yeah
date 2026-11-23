package com.example.firebase

import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

sealed interface FsUiState<out T> {
    data object Loading : FsUiState<Nothing>
    data class Success<T>(val data: T) : FsUiState<T>
    data class Error(val message: String) : FsUiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class FsUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val role: String = "STUDENT",
    val accountStatus: String = "ACTIVE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsUserPreferences(
    @DocumentId val id: String = "",
    val userId: String = "",
    val language: String = "ar",
    val darkMode: Boolean = true,
    val autoScrollSpeed: Double = 1.0,
    val selectedReciterId: String = "qatami",
    val playbackSpeed: Double = 1.0,
    val prayerNotifications: Boolean = true,
    val quranReminders: Boolean = true,
    val revisionReminders: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsUserProgress(
    @DocumentId val id: String = "",
    val userId: String = "",
    val lastPage: Long = 1L,
    val lastSurah: Long = 1L,
    val lastAyah: Long = 1L,
    val memorizedAyahsCount: Long = 0L,
    val memorizedPagesCount: Long = 0L,
    val dailyGoalPages: Long = 4L,
    val dailyReadPages: Long = 0L,
    val completedRevisions: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsBookmarkRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val page: Long = 1L,
    val surah: Long = 1L,
    val ayah: Long = 1L,
    val favorite: Boolean = false,
    val note: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsReadingHistoryRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val page: Long = 1L,
    val surah: Long = 1L,
    val ayah: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsMemorizationSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val surah: Long = 1L,
    val startAyah: Long = 1L,
    val endAyah: Long = 1L,
    val status: String = "IN_PROGRESS",
    val memorized: Boolean = false,
    val accuracyPercent: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsRevisionSchedule(
    @DocumentId val id: String = "",
    val userId: String = "",
    val surah: Long = 1L,
    val startAyah: Long = 1L,
    val endAyah: Long = 1L,
    val nextDueDateIso: String = "",
    val intervalDays: Long = 3L,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsRecitationRecordingMeta(
    @DocumentId val id: String = "",
    val userId: String = "",
    val surah: Long = 1L,
    val startAyah: Long = 1L,
    val endAyah: Long = 1L,
    val durationMs: Long = 0L,
    val localFileName: String = "",
    val speechMode: String = "RECORDED_ONLY",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsRecitationAssessment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val surah: Long = 1L,
    val startAyah: Long = 1L,
    val endAyah: Long = 1L,
    val recognizedText: String = "",
    val confidence: Double = 0.0,
    val accuracyPercent: Long = 0L,
    val omittedCount: Long = 0L,
    val repeatedCount: Long = 0L,
    val replacedCount: Long = 0L,
    val assessmentStatus: String = "UNPROCESSED",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsMemorizationTestResult(
    @DocumentId val id: String = "",
    val userId: String = "",
    val testType: String = "CONTINUE_AYAH",
    val surah: Long = 1L,
    val startAyah: Long = 1L,
    val endAyah: Long = 7L,
    val scorePercent: Long = 0L,
    val totalQuestions: Long = 1L,
    val correctAnswers: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsKhatmahSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val targetDays: Long = 30L,
    val completedPages: Long = 0L,
    val lastPage: Long = 1L,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsFastingRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val planType: String = "MON_THU",
    val completedDays: Long = 0L,
    val targetDays: Long = 8L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsUserNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "SYSTEM",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsAnnouncement(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val audience: String = "ALL",
    val active: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsApprovedKnowledgeSource(
    @DocumentId val id: String = "",
    val title: String = "",
    val category: String = "",
    val question: String? = null,
    val answer: String = "",
    val keywords: String? = null,
    val reference: String = "",
    val approvalStatus: String = "APPROVED",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsAiFeedback(
    @DocumentId val id: String = "",
    val userId: String = "",
    val question: String = "",
    val aiResponse: String = "",
    val helpful: Boolean = true,
    val unanswered: Boolean = false,
    val reviewed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsAdminAuditLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val target: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Suppress("UNCHECKED_CAST")
fun buildCleanMap(vararg pairs: Pair<String, Any?>): Map<String, Any> =
    mapOf(*pairs).filterValues { it != null } as Map<String, Any>

fun serverTs(): Any = FieldValue.serverTimestamp()
