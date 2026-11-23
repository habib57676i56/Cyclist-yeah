package com.example.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TAG = "FirestoreNoorRepo"
private const val BOOTSTRAP_ADMIN_EMAIL = "habiybullohy@gmail.com"

class FirestoreNoorRepository(
    val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun isBootstrapAdminUser(user: FirebaseUser): Boolean =
        user.email == BOOTSTRAP_ADMIN_EMAIL && user.isEmailVerified

    private fun <T> unwrapFirestoreError(flow: Flow<T>, op: OperationType, path: String): Flow<T> =
        flow.catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val toLog = fsError ?: (error as? Exception)
            if (toLog != null && auth.currentUser != null) {
                handleFirestoreError(toLog, op, path)
            }
            throw (fsError ?: error)
        }

    /**
     * Verifies backend authorization via both verified email claim AND /admins/{uid} document.
     */
    suspend fun checkIsBackendAdmin(user: FirebaseUser?): Boolean {
        if (user == null) return false
        if (isBootstrapAdminUser(user)) return true
        return try {
            db.collection("admins").document(user.uid).get().await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Admin registry check failed for ${user.uid}: ${e.message}")
            false
        }
    }

    /**
     * Ensures /users/{uid}, /users/{uid}/user_preferences/default, and /users/{uid}/user_progress/default
     * exist cleanly without overwriting existing user progress or escalating roles on the client.
     */
    suspend fun ensureUserProfileAndDefaults(user: FirebaseUser): Result<FsUserProfile> {
        val path = "users/${user.uid}"
        return try {
            val docRef = db.collection("users").document(user.uid)
            val snap = docRef.get().await()
            val safeName = (user.displayName?.takeIf { it.isNotBlank() } ?: user.email?.substringBefore("@") ?: "NOOR User").take(80)
            val safeEmail = (user.email?.takeIf { it.contains("@") } ?: "${user.uid}@users.noor.local").take(120)
            val safePhoto = user.photoUrl?.toString()?.take(500)

            val profile = if (!snap.exists()) {
                val initialRole = if (isBootstrapAdminUser(user)) "SUPER_ADMIN" else "STUDENT"
                val payload = buildCleanMap(
                    "userId" to user.uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "role" to initialRole,
                    "accountStatus" to "ACTIVE",
                    "createdAt" to serverTs(),
                    "updatedAt" to serverTs()
                )
                docRef.set(payload).await()
                docRef.get().await().toObject(FsUserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                    ?: FsUserProfile(id = user.uid, userId = user.uid, displayName = safeName, email = safeEmail, photoUrl = safePhoto, role = initialRole)
            } else {
                // Partial update: preserve createdAt and existing server-assigned role
                val updatePayload = buildCleanMap(
                    "displayName" to safeName,
                    "photoUrl" to safePhoto,
                    "updatedAt" to serverTs()
                )
                docRef.update(updatePayload).await()
                snap.toObject(FsUserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                    ?: FsUserProfile(id = user.uid, userId = user.uid, displayName = safeName, email = safeEmail)
            }

            // Initialize defaults if absent
            val prefRef = docRef.collection("user_preferences").document("default")
            if (!prefRef.get().await().exists()) {
                prefRef.set(
                    buildCleanMap(
                        "userId" to user.uid,
                        "language" to "ar",
                        "darkMode" to true,
                        "autoScrollSpeed" to 1.0,
                        "selectedReciterId" to "qatami",
                        "playbackSpeed" to 1.0,
                        "prayerNotifications" to true,
                        "quranReminders" to true,
                        "revisionReminders" to true,
                        "updatedAt" to serverTs()
                    )
                ).await()
            }

            val progRef = docRef.collection("user_progress").document("default")
            if (!progRef.get().await().exists()) {
                progRef.set(
                    buildCleanMap(
                        "userId" to user.uid,
                        "lastPage" to 1L,
                        "lastSurah" to 1L,
                        "lastAyah" to 1L,
                        "memorizedAyahsCount" to 0L,
                        "memorizedPagesCount" to 0L,
                        "dailyGoalPages" to 4L,
                        "dailyReadPages" to 0L,
                        "completedRevisions" to 0L,
                        "updatedAt" to serverTs()
                    )
                ).await()
            }

            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<FsUserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            unwrapFirestoreError(
                db.collection("users").document(userId)
                    .snapshots()
                    .map { it.toObject(FsUserProfile::class.java, ServerTimestampBehavior.ESTIMATE) },
                OperationType.GET,
                path
            )
        )
    }

    fun observeUserProgress(userId: String): Flow<FsUserProgress?> = flow {
        val path = "users/$userId/user_progress/default"
        emitAll(
            unwrapFirestoreError(
                db.collection("users").document(userId)
                    .collection("user_progress").document("default")
                    .snapshots()
                    .map { it.toObject(FsUserProgress::class.java, ServerTimestampBehavior.ESTIMATE) },
                OperationType.GET,
                path
            )
        )
    }

    fun observeBookmarks(userId: String): Flow<List<FsBookmarkRecord>> = flow {
        val path = "users/$userId/bookmarks"
        emitAll(
            unwrapFirestoreError(
                db.collection("users").document(userId)
                    .collection("bookmarks")
                    .snapshots()
                    .map { it.toObjects(FsBookmarkRecord::class.java, ServerTimestampBehavior.ESTIMATE) },
                OperationType.LIST,
                path
            )
        )
    }

    fun observeTestResults(userId: String): Flow<List<FsMemorizationTestResult>> = flow {
        val path = "users/$userId/test_results"
        emitAll(
            unwrapFirestoreError(
                db.collection("users").document(userId)
                    .collection("test_results")
                    .snapshots()
                    .map { it.toObjects(FsMemorizationTestResult::class.java, ServerTimestampBehavior.ESTIMATE) },
                OperationType.LIST,
                path
            )
        )
    }

    fun observeNotifications(userId: String): Flow<List<FsUserNotification>> = flow {
        val path = "users/$userId/notifications"
        emitAll(
            unwrapFirestoreError(
                db.collection("users").document(userId)
                    .collection("notifications")
                    .snapshots()
                    .map { it.toObjects(FsUserNotification::class.java, ServerTimestampBehavior.ESTIMATE) },
                OperationType.LIST,
                path
            )
        )
    }

    fun observeAnnouncements(): Flow<List<FsAnnouncement>> = flow {
        val path = "announcements"
        emitAll(
            unwrapFirestoreError(
                db.collection("announcements")
                    .snapshots()
                    .map { it.toObjects(FsAnnouncement::class.java, ServerTimestampBehavior.ESTIMATE) },
                OperationType.LIST,
                path
            )
        )
    }

    fun observeApprovedKnowledgeSources(): Flow<List<FsApprovedKnowledgeSource>> = flow {
        val path = "approved_knowledge_sources"
        emitAll(
            unwrapFirestoreError(
                db.collection("approved_knowledge_sources")
                    .snapshots()
                    .map { it.toObjects(FsApprovedKnowledgeSource::class.java, ServerTimestampBehavior.ESTIMATE) },
                OperationType.LIST,
                path
            )
        )
    }

    fun observeAllUsersForAdmin(): Flow<List<FsUserProfile>> = flow {
        val path = "users"
        emitAll(
            unwrapFirestoreError(
                db.collection("users")
                    .snapshots()
                    .map { it.toObjects(FsUserProfile::class.java, ServerTimestampBehavior.ESTIMATE) },
                OperationType.LIST,
                path
            )
        )
    }

    fun observeAiFeedback(userId: String, isAdmin: Boolean): Flow<List<FsAiFeedback>> = flow {
        val path = "ai_feedback"
        val query = if (isAdmin) {
            db.collection("ai_feedback")
        } else {
            // Strict rule parity: non-admins MUST filter by userId == uid
            db.collection("ai_feedback").whereEqualTo("userId", userId)
        }
        emitAll(
            unwrapFirestoreError(
                query.snapshots()
                    .map { it.toObjects(FsAiFeedback::class.java, ServerTimestampBehavior.ESTIMATE) },
                OperationType.LIST,
                path
            )
        )
    }

    fun observeAuditLogsForAdmin(): Flow<List<FsAdminAuditLog>> = flow {
        val path = "admin_audit_logs"
        emitAll(
            unwrapFirestoreError(
                db.collection("admin_audit_logs")
                    .snapshots()
                    .map { it.toObjects(FsAdminAuditLog::class.java, ServerTimestampBehavior.ESTIMATE) },
                OperationType.LIST,
                path
            )
        )
    }

    suspend fun syncPreferences(
        language: String,
        darkMode: Boolean,
        autoScrollSpeed: Double,
        selectedReciterId: String,
        playbackSpeed: Double,
        prayerNotifications: Boolean,
        quranReminders: Boolean,
        revisionReminders: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val path = "users/$uid/user_preferences/default"
        return try {
            requireUserId()
            val payload = buildCleanMap(
                "userId" to uid,
                "language" to if (language in listOf("ar", "en", "uz")) language else "ar",
                "darkMode" to darkMode,
                "autoScrollSpeed" to autoScrollSpeed.coerceIn(0.2, 4.0),
                "selectedReciterId" to selectedReciterId.ifBlank { "qatami" }.take(80),
                "playbackSpeed" to playbackSpeed.coerceIn(0.5, 2.5),
                "prayerNotifications" to prayerNotifications,
                "quranReminders" to quranReminders,
                "revisionReminders" to revisionReminders,
                "updatedAt" to serverTs()
            )
            db.document(path).set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun syncProgress(
        lastPage: Int,
        lastSurah: Int,
        lastAyah: Int,
        memorizedAyahsCount: Int,
        memorizedPagesCount: Int,
        dailyGoalPages: Int,
        dailyReadPages: Int,
        completedRevisions: Int
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val path = "users/$uid/user_progress/default"
        return try {
            requireUserId()
            val payload = buildCleanMap(
                "userId" to uid,
                "lastPage" to lastPage.coerceIn(1, 604).toLong(),
                "lastSurah" to lastSurah.coerceIn(1, 114).toLong(),
                "lastAyah" to lastAyah.coerceIn(1, 286).toLong(),
                "memorizedAyahsCount" to memorizedAyahsCount.coerceIn(0, 6236).toLong(),
                "memorizedPagesCount" to memorizedPagesCount.coerceIn(0, 604).toLong(),
                "dailyGoalPages" to dailyGoalPages.coerceIn(1, 100).toLong(),
                "dailyReadPages" to dailyReadPages.coerceIn(0, 604).toLong(),
                "completedRevisions" to completedRevisions.coerceAtLeast(0).toLong(),
                "updatedAt" to serverTs()
            )
            db.document(path).set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveBookmark(page: Int, surah: Int, ayah: Int, favorite: Boolean, note: String? = null): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val docId = "bm_${page}_${surah}_${ayah}"
        val path = "users/$uid/bookmarks/$docId"
        return try {
            requireUserId()
            val ref = db.document(path)
            val existing = ref.get().await()
            val safeNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(500)
            if (existing.exists()) {
                ref.update(
                    buildCleanMap(
                        "favorite" to favorite,
                        "note" to safeNote,
                        "updatedAt" to serverTs()
                    )
                ).await()
            } else {
                ref.set(
                    buildCleanMap(
                        "userId" to uid,
                        "page" to page.coerceIn(1, 604).toLong(),
                        "surah" to surah.coerceIn(1, 114).toLong(),
                        "ayah" to ayah.coerceIn(1, 286).toLong(),
                        "favorite" to favorite,
                        "note" to safeNote,
                        "createdAt" to serverTs(),
                        "updatedAt" to serverTs()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addReadingHistory(page: Int, surah: Int, ayah: Int): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val path = "users/$uid/reading_history"
        return try {
            requireUserId()
            val ref = db.collection("users").document(uid).collection("reading_history").document()
            ref.set(
                buildCleanMap(
                    "userId" to uid,
                    "page" to page.coerceIn(1, 604).toLong(),
                    "surah" to surah.coerceIn(1, 114).toLong(),
                    "ayah" to ayah.coerceIn(1, 286).toLong(),
                    "createdAt" to serverTs()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveMemorizationSession(
        surah: Int,
        startAyah: Int,
        endAyah: Int,
        status: String,
        memorized: Boolean,
        accuracyPercent: Int
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val docId = "mem_${surah}_${startAyah}_${endAyah}"
        val path = "users/$uid/memorization_sessions/$docId"
        return try {
            requireUserId()
            val ref = db.document(path)
            val safeStatus = if (status in listOf("IN_PROGRESS", "MEMORIZED", "NEEDS_REVISION")) status else "IN_PROGRESS"
            if (ref.get().await().exists()) {
                ref.update(
                    buildCleanMap(
                        "status" to safeStatus,
                        "memorized" to memorized,
                        "accuracyPercent" to accuracyPercent.coerceIn(0, 100).toLong(),
                        "updatedAt" to serverTs()
                    )
                ).await()
            } else {
                val safeEnd = endAyah.coerceIn(startAyah.coerceIn(1, 286), 286)
                ref.set(
                    buildCleanMap(
                        "userId" to uid,
                        "surah" to surah.coerceIn(1, 114).toLong(),
                        "startAyah" to startAyah.coerceIn(1, 286).toLong(),
                        "endAyah" to safeEnd.toLong(),
                        "status" to safeStatus,
                        "memorized" to memorized,
                        "accuracyPercent" to accuracyPercent.coerceIn(0, 100).toLong(),
                        "createdAt" to serverTs(),
                        "updatedAt" to serverTs()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveRecordingAndAssessment(
        surah: Int,
        startAyah: Int,
        endAyah: Int,
        durationMs: Long,
        localFileName: String,
        speechMode: String,
        recognizedText: String,
        confidence: Double,
        accuracyPercent: Int,
        omittedCount: Int,
        repeatedCount: Int,
        replacedCount: Int,
        assessmentStatus: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val path = "users/$uid/recitation_assessments"
        return try {
            requireUserId()
            val safeEnd = endAyah.coerceIn(startAyah.coerceIn(1, 286), 286)
            val safeMode = if (speechMode in listOf("OFFLINE", "ONLINE_DEVICE", "RECORDED_ONLY")) speechMode else "RECORDED_ONLY"
            val safeStatus = if (assessmentStatus in listOf("CONFIRMED", "LOW_CONFIDENCE_REVIEW", "UNPROCESSED")) assessmentStatus else "UNPROCESSED"

            db.collection("users").document(uid).collection("recitation_recordings_metadata").document().set(
                buildCleanMap(
                    "userId" to uid,
                    "surah" to surah.coerceIn(1, 114).toLong(),
                    "startAyah" to startAyah.coerceIn(1, 286).toLong(),
                    "endAyah" to safeEnd.toLong(),
                    "durationMs" to durationMs.coerceIn(0L, 3600000L),
                    "localFileName" to localFileName.ifBlank { "rec.m4a" }.take(256),
                    "speechMode" to safeMode,
                    "createdAt" to serverTs()
                )
            ).await()

            db.collection("users").document(uid).collection("recitation_assessments").document().set(
                buildCleanMap(
                    "userId" to uid,
                    "surah" to surah.coerceIn(1, 114).toLong(),
                    "startAyah" to startAyah.coerceIn(1, 286).toLong(),
                    "endAyah" to safeEnd.toLong(),
                    "recognizedText" to recognizedText.take(4000),
                    "confidence" to confidence.coerceIn(0.0, 1.0),
                    "accuracyPercent" to accuracyPercent.coerceIn(0, 100).toLong(),
                    "omittedCount" to omittedCount.coerceIn(0, 1000).toLong(),
                    "repeatedCount" to repeatedCount.coerceIn(0, 1000).toLong(),
                    "replacedCount" to replacedCount.coerceIn(0, 1000).toLong(),
                    "assessmentStatus" to safeStatus,
                    "createdAt" to serverTs()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveTestResult(
        testType: String,
        surah: Int,
        startAyah: Int,
        endAyah: Int,
        scorePercent: Int,
        totalQuestions: Int,
        correctAnswers: Int
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val path = "users/$uid/test_results"
        return try {
            requireUserId()
            val safeType = if (testType in listOf("CONTINUE_AYAH", "MISSING_WORDS", "NEXT_AYAH", "IDENTIFY_SURAH", "CHOOSE_CONTINUATION", "RECITE_BLIND")) testType else "CONTINUE_AYAH"
            val totalQ = totalQuestions.coerceIn(1, 100)
            val correctQ = correctAnswers.coerceIn(0, totalQ)
            val safeEnd = endAyah.coerceIn(startAyah.coerceIn(1, 286), 286)
            db.collection("users").document(uid).collection("test_results").document().set(
                buildCleanMap(
                    "userId" to uid,
                    "testType" to safeType,
                    "surah" to surah.coerceIn(1, 114).toLong(),
                    "startAyah" to startAyah.coerceIn(1, 286).toLong(),
                    "endAyah" to safeEnd.toLong(),
                    "scorePercent" to scorePercent.coerceIn(0, 100).toLong(),
                    "totalQuestions" to totalQ.toLong(),
                    "correctAnswers" to correctQ.toLong(),
                    "createdAt" to serverTs()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun syncKhatmah(docId: String, title: String, targetDays: Int, completedPages: Int, lastPage: Int, completed: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val safeId = docId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(100)
        val path = "users/$uid/khatmah_sessions/$safeId"
        return try {
            requireUserId()
            val ref = db.document(path)
            if (ref.get().await().exists()) {
                ref.update(
                    buildCleanMap(
                        "completedPages" to completedPages.coerceIn(0, 604).toLong(),
                        "lastPage" to lastPage.coerceIn(1, 604).toLong(),
                        "completed" to completed,
                        "updatedAt" to serverTs()
                    )
                ).await()
            } else {
                ref.set(
                    buildCleanMap(
                        "userId" to uid,
                        "title" to title.ifBlank { "Khatmah" }.take(120),
                        "targetDays" to targetDays.coerceIn(1, 365).toLong(),
                        "completedPages" to completedPages.coerceIn(0, 604).toLong(),
                        "lastPage" to lastPage.coerceIn(1, 604).toLong(),
                        "completed" to completed,
                        "createdAt" to serverTs(),
                        "updatedAt" to serverTs()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun syncFasting(docId: String, title: String, planType: String, completedDays: Int, targetDays: Int): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val safeId = docId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(100)
        val path = "users/$uid/fasting_records/$safeId"
        return try {
            requireUserId()
            val ref = db.document(path)
            val safeType = if (planType in listOf("MON_THU", "WHITE_DAYS", "RAMADAN", "CUSTOM")) planType else "CUSTOM"
            if (ref.get().await().exists()) {
                ref.update(
                    buildCleanMap(
                        "completedDays" to completedDays.coerceIn(0, 365).toLong(),
                        "updatedAt" to serverTs()
                    )
                ).await()
            } else {
                ref.set(
                    buildCleanMap(
                        "userId" to uid,
                        "title" to title.ifBlank { "Fasting Plan" }.take(120),
                        "planType" to safeType,
                        "completedDays" to completedDays.coerceIn(0, 365).toLong(),
                        "targetDays" to targetDays.coerceIn(1, 365).toLong(),
                        "createdAt" to serverTs(),
                        "updatedAt" to serverTs()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addUserNotification(title: String, body: String, category: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val path = "users/$uid/notifications"
        return try {
            requireUserId()
            val safeCat = if (category in listOf("PRAYER", "QURAN", "MEMORIZATION", "REVISION", "SYSTEM")) category else "SYSTEM"
            db.collection("users").document(uid).collection("notifications").document().set(
                buildCleanMap(
                    "userId" to uid,
                    "title" to title.ifBlank { "NOOR" }.take(140),
                    "body" to body.ifBlank { "Reminder" }.take(1000),
                    "category" to safeCat,
                    "read" to false,
                    "createdAt" to serverTs()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(notifId: String, read: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val path = "users/$uid/notifications/$notifId"
        return try {
            requireUserId()
            db.document(path).update(mapOf("read" to read)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitAiFeedback(question: String, aiResponse: String, helpful: Boolean, unanswered: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val path = "ai_feedback"
        return try {
            requireUserId()
            db.collection("ai_feedback").document().set(
                buildCleanMap(
                    "userId" to uid,
                    "question" to question.ifBlank { "Question" }.take(1000),
                    "aiResponse" to aiResponse.take(8000),
                    "helpful" to helpful,
                    "unanswered" to unanswered,
                    "reviewed" to false,
                    "createdAt" to serverTs()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Admin Actions (Protected by Firestore Rules + isAdmin()) ---

    suspend fun publishAnnouncement(title: String, message: String, audience: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val path = "announcements"
        return try {
            requireUserId()
            val safeAudience = if (audience in listOf("ALL", "STUDENTS", "MEMORIZERS")) audience else "ALL"
            val docRef = db.collection("announcements").document()
            docRef.set(
                buildCleanMap(
                    "title" to title.ifBlank { "Announcement" }.take(160),
                    "message" to message.ifBlank { "..." }.take(2000),
                    "audience" to safeAudience,
                    "active" to true,
                    "createdBy" to uid,
                    "createdAt" to serverTs(),
                    "updatedAt" to serverTs()
                )
            ).await()
            logAdminAction("PUBLISH_ANNOUNCEMENT", docRef.id, title)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertKnowledgeSource(
        docId: String?,
        title: String,
        category: String,
        question: String?,
        answer: String,
        keywords: String?,
        reference: String,
        approvalStatus: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val safeStatus = if (approvalStatus in listOf("APPROVED", "PENDING", "REJECTED")) approvalStatus else "PENDING"
        val docRef = if (docId.isNullOrBlank()) db.collection("approved_knowledge_sources").document()
        else db.collection("approved_knowledge_sources").document(docId)
        val path = docRef.path
        return try {
            requireUserId()
            if (!docId.isNullOrBlank() && docRef.get().await().exists()) {
                docRef.update(
                    buildCleanMap(
                        "title" to title.ifBlank { "Source" }.take(200),
                        "category" to category.ifBlank { "GENERAL" }.take(60),
                        "question" to question?.take(500),
                        "answer" to answer.ifBlank { "-" }.take(8000),
                        "keywords" to keywords?.take(500),
                        "reference" to reference.ifBlank { "Verified Source" }.take(300),
                        "approvalStatus" to safeStatus,
                        "updatedAt" to serverTs()
                    )
                ).await()
            } else {
                docRef.set(
                    buildCleanMap(
                        "title" to title.ifBlank { "Source" }.take(200),
                        "category" to category.ifBlank { "GENERAL" }.take(60),
                        "question" to question?.take(500),
                        "answer" to answer.ifBlank { "-" }.take(8000),
                        "keywords" to keywords?.take(500),
                        "reference" to reference.ifBlank { "Verified Source" }.take(300),
                        "approvalStatus" to safeStatus,
                        "createdBy" to uid,
                        "createdAt" to serverTs(),
                        "updatedAt" to serverTs()
                    )
                ).await()
            }
            logAdminAction("UPSERT_KNOWLEDGE", docRef.id, "$title ($safeStatus)")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteKnowledgeSource(docId: String): Result<Unit> {
        val path = "approved_knowledge_sources/$docId"
        return try {
            requireUserId()
            db.document(path).delete().await()
            logAdminAction("DELETE_KNOWLEDGE", docId, "Removed knowledge source")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserRoleAndStatus(targetUid: String, targetEmail: String, role: String, status: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val path = "users/$targetUid"
        return try {
            requireUserId()
            val safeRole = if (role in listOf("STUDENT", "TEACHER", "ADMIN", "SUPER_ADMIN")) role else "STUDENT"
            val safeStatus = if (status in listOf("ACTIVE", "SUSPENDED")) status else "ACTIVE"
            db.document(path).update(
                buildCleanMap(
                    "role" to safeRole,
                    "accountStatus" to safeStatus,
                    "updatedAt" to serverTs()
                )
            ).await()
            val adminDoc = db.collection("admins").document(targetUid)
            if (safeRole == "ADMIN" || safeRole == "SUPER_ADMIN") {
                adminDoc.set(
                    buildCleanMap(
                        "uid" to targetUid,
                        "email" to targetEmail.take(120),
                        "grantedBy" to uid,
                        "createdAt" to serverTs()
                    )
                ).await()
            } else if (adminDoc.get().await().exists()) {
                adminDoc.delete().await()
            }
            logAdminAction("UPDATE_USER_ROLE", targetUid, "role=$safeRole status=$safeStatus")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markFeedbackReviewed(feedbackId: String): Result<Unit> {
        val path = "ai_feedback/$feedbackId"
        return try {
            requireUserId()
            db.document(path).update(mapOf("reviewed" to true)).await()
            logAdminAction("REVIEW_AI_FEEDBACK", feedbackId, "Marked reviewed")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun logAdminAction(action: String, target: String, details: String) {
        val user = auth.currentUser ?: return
        val email = (user.email ?: "${user.uid}@users.noor.local").take(120)
        runCatching {
            db.collection("admin_audit_logs").document().set(
                buildCleanMap(
                    "actorUid" to user.uid,
                    "actorEmail" to email,
                    "action" to action.take(120),
                    "target" to target.take(200),
                    "details" to details.take(1000),
                    "createdAt" to serverTs()
                )
            ).await()
        }
    }

    /**
     * Supports account deletion and associated user-data deletion across Firestore subcollections.
     */
    suspend fun deleteAccountAndPersonalData(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val uid = user.uid
        val path = "users/$uid"
        return try {
            val subCollections = listOf(
                "user_preferences", "user_progress", "bookmarks", "reading_history",
                "memorization_sessions", "revision_schedules", "recitation_recordings_metadata",
                "recitation_assessments", "test_results", "khatmah_sessions", "fasting_records", "notifications"
            )
            val userDoc = db.collection("users").document(uid)
            for (col in subCollections) {
                val docs = userDoc.collection(col).get().await()
                docs.documents.forEach { it.reference.delete().await() }
            }
            userDoc.delete().await()
            user.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
