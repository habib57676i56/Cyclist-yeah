package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.AiStreamState
import com.example.ai.DocumentExtractor
import com.example.ai.GeminiRagService
import com.example.ai.LocalRagEngine
import com.example.ai.NoorAiAnswer
import com.example.audio.QuranAudioEngine
import com.example.data.local.*
import com.example.firebase.*
import com.example.i18n.AppLanguage
import com.example.notifications.NotificationHelper
import com.example.prayer.AdhanAudioManager
import com.example.prayer.CalculationMethod
import com.example.prayer.DailyPrayerTimes
import com.example.prayer.PrayerTimesCalculator
import com.example.quran.AyahRecord
import com.example.quran.MushafCoordinateEngine
import com.example.quran.MushafPackImporter
import com.example.quran.QuranCatalog
import com.example.recitation.ArabicSpeechRecognizer
import com.example.recitation.AudioRecorderManager
import com.example.recitation.QuranRecitationComparator
import com.example.recitation.RecitationAssessmentResult
import com.example.security.AdminPermission
import com.example.security.PasswordHasher
import com.example.security.PermissionGuard
import com.example.utils.BackupAndReportExporter
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

private const val VM_TAG = "NoorViewModel"

data class AiChatMessage(
    val id: Long = System.nanoTime(),
    val isUser: Boolean,
    val text: String,
    val answerMeta: NoorAiAnswer? = null,
    val usedGemini: Boolean = false,
    val citations: List<String> = emptyList(),
    val isStreaming: Boolean = false,
    val questionRef: String = "",
    val feedbackSubmitted: Boolean = false
)

class NoorViewModel @JvmOverloads constructor(
    application: Application,
    private val firestoreRepo: FirestoreNoorRepository? = null
) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    val db: NoorDatabase = NoorDatabase.getInstance(ctx)
    val prefs = NoorPreferences(ctx)
    val ragEngine = LocalRagEngine(db.knowledgeDao())
    private val geminiRagService by lazy { GeminiRagService(ragEngine) }
    val audioRecorder = AudioRecorderManager(ctx)
    val speechRecognizer = ArabicSpeechRecognizer(ctx)

    private var cloudCollectorJob: Job? = null
    private var boundUid: String? = null

    init {
        viewModelScope.launch {
            NoorDatabaseSeeder.seedIfEmpty(db)
            MushafCoordinateEngine.loadSavedPackFromDisk(ctx)
        }
    }

    // Settings & Auth
    val settings: StateFlow<NoorUserSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NoorUserSettings())

    private val _currentUser = MutableStateFlow<UserAccountEntity?>(null)
    val currentUser: StateFlow<UserAccountEntity?> = _currentUser.asStateFlow()

    val currentRole: UserRole
        get() = UserRole.fromName(_currentUser.value?.role)

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    fun clearStatusBanner() { _statusBanner.value = null }
    fun postStatus(msg: String) { _statusBanner.value = msg }

    // --- Cloud Firestore State ---
    private val _isBackendAdmin = MutableStateFlow(false)
    val isBackendAdmin: StateFlow<Boolean> = _isBackendAdmin.asStateFlow()

    private val _syncStatus = MutableStateFlow(if (firestoreRepo != null) "Cloud Synced" else "Offline Ready")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _fsUserProfile = MutableStateFlow<FsUserProfile?>(null)
    val fsUserProfile: StateFlow<FsUserProfile?> = _fsUserProfile.asStateFlow()

    private val _fsUserProgress = MutableStateFlow<FsUserProgress?>(null)
    val fsUserProgress: StateFlow<FsUserProgress?> = _fsUserProgress.asStateFlow()

    private val _fsBookmarks = MutableStateFlow<List<FsBookmarkRecord>>(emptyList())
    val fsBookmarks: StateFlow<List<FsBookmarkRecord>> = _fsBookmarks.asStateFlow()

    private val _fsTestResults = MutableStateFlow<List<FsMemorizationTestResult>>(emptyList())
    val fsTestResults: StateFlow<List<FsMemorizationTestResult>> = _fsTestResults.asStateFlow()

    private val _fsNotifications = MutableStateFlow<List<FsUserNotification>>(emptyList())
    val fsNotifications: StateFlow<List<FsUserNotification>> = _fsNotifications.asStateFlow()

    private val _fsAnnouncements = MutableStateFlow<List<FsAnnouncement>>(emptyList())
    val fsAnnouncements: StateFlow<List<FsAnnouncement>> = _fsAnnouncements.asStateFlow()

    private val _fsKnowledgeSources = MutableStateFlow<List<FsApprovedKnowledgeSource>>(emptyList())
    val fsKnowledgeSources: StateFlow<List<FsApprovedKnowledgeSource>> = _fsKnowledgeSources.asStateFlow()

    private val _fsFeedback = MutableStateFlow<List<FsAiFeedback>>(emptyList())
    val fsFeedback: StateFlow<List<FsAiFeedback>> = _fsFeedback.asStateFlow()

    private val _fsAuditLogs = MutableStateFlow<List<FsAdminAuditLog>>(emptyList())
    val fsAuditLogs: StateFlow<List<FsAdminAuditLog>> = _fsAuditLogs.asStateFlow()

    private val _fsAllUsers = MutableStateFlow<List<FsUserProfile>>(emptyList())
    val fsAllUsers: StateFlow<List<FsUserProfile>> = _fsAllUsers.asStateFlow()

    // Quran Reader State
    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _selectedAyah = MutableStateFlow<AyahRecord?>(null)
    val selectedAyah: StateFlow<AyahRecord?> = _selectedAyah.asStateFlow()

    private val _mushafPackPagesCount = MutableStateFlow(MushafCoordinateEngine.installedImagePageCount(ctx))
    val mushafPackPagesCount: StateFlow<Int> = _mushafPackPagesCount.asStateFlow()

    val bookmarks = db.quranDataDao().observeBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val readingHistory = db.quranDataDao().observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reciters = db.adminDao().observeReciters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Memorization & Assessment
    val memorizationPlans = db.memorizationDao().observePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mistakes = db.memorizationDao().observeMistakes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val assessments = db.memorizationDao().observeAssessments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _latestComparison = MutableStateFlow<RecitationAssessmentResult?>(null)
    val latestComparison: StateFlow<RecitationAssessmentResult?> = _latestComparison.asStateFlow()

    // Worship: Prayer, Khatmah, Fasting
    val khatmahs = db.worshipDao().observeKhatmahs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val fastingPlans = db.worshipDao().observeFastingPlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val adhanVoices = db.adminDao().observeAdhanVoices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyPrayerTimes: StateFlow<DailyPrayerTimes> = settings.map { s ->
        PrayerTimesCalculator.calculate(
            latitude = s.latitude,
            longitude = s.longitude,
            method = s.calculationMethod,
            fajrOffset = s.fajrOffsetMin,
            dhuhrOffset = s.dhuhrOffsetMin,
            asrOffset = s.asrOffsetMin,
            maghribOffset = s.maghribOffsetMin,
            ishaOffset = s.ishaOffsetMin
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PrayerTimesCalculator.calculate(33.5138, 36.2765, CalculationMethod.DAMASCUS_SYRIA)
    )

    // Library & Halaqat & Admin
    val lessons = db.libraryDao().observeLessons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val halaqat = db.halaqatDao().observeHalaqat()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val attendance = db.halaqatDao().observeAttendance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val users = db.userDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val knowledgeEntries = db.knowledgeDao().observeEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val approvedChunkCount = db.knowledgeDao().observeApprovedChunkCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val announcements = db.adminDao().observeAnnouncements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val promoCards = db.adminDao().observePromoCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val featureToggles = db.adminDao().observeFeatureToggles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val auditLogs = db.adminDao().observeAuditLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // NOOR AI Chat & Streaming State
    private val _aiChat = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val aiChat: StateFlow<List<AiChatMessage>> = _aiChat.asStateFlow()

    private val _aiStreamState = MutableStateFlow<AiStreamState>(AiStreamState.Idle)
    val aiStreamState: StateFlow<AiStreamState> = _aiStreamState.asStateFlow()

    /**
     * Binds an authenticated Firebase user to this ViewModel, ensures Firestore profile + defaults,
     * checks backend admin authorization (/admins/{uid} + verified bootstrap email), and attaches
     * scoped real-time snapshot listeners cleanly.
     */
    fun bindAuthenticatedUser(user: FirebaseUser) {
        val repo = firestoreRepo ?: return
        if (boundUid == user.uid && cloudCollectorJob?.isActive == true) return
        boundUid = user.uid
        cloudCollectorJob?.cancel()

        cloudCollectorJob = viewModelScope.launch {
            _syncStatus.value = "Syncing..."
            val initResult = repo.ensureUserProfileAndDefaults(user)
            initResult.onSuccess { prof ->
                _fsUserProfile.value = prof
                _syncStatus.value = "Cloud Synced"
            }.onFailure { err ->
                _syncStatus.value = "Offline / Retry"
                Log.e(VM_TAG, "Cloud profile init failed: ${err.message}", err)
            }

            val isAdmin = repo.checkIsBackendAdmin(user)
            _isBackendAdmin.value = isAdmin
            if (isAdmin) {
                // Mirror verified backend admin role into local RBAC state for shared Room tools
                _currentUser.value = UserAccountEntity(
                    id = 1L,
                    name = user.displayName ?: "Super Admin",
                    username = user.email ?: "superadmin",
                    passwordHash = "",
                    salt = "",
                    role = UserRole.SUPER_ADMIN.name,
                    isActive = true
                )
            }

            launch {
                repo.observeUserProfile(user.uid)
                    .catch { e -> handleStreamError("user_profile", e) }
                    .collect { _fsUserProfile.value = it }
            }
            launch {
                repo.observeUserProgress(user.uid)
                    .catch { e -> handleStreamError("user_progress", e) }
                    .collect { prog ->
                        _fsUserProgress.value = prog
                        if (prog != null && _currentPage.value == 1 && prog.lastPage > 1L) {
                            _currentPage.value = prog.lastPage.toInt().coerceIn(1, 604)
                        }
                    }
            }
            launch {
                repo.observeBookmarks(user.uid)
                    .catch { e -> handleStreamError("bookmarks", e) }
                    .collect { _fsBookmarks.value = it }
            }
            launch {
                repo.observeTestResults(user.uid)
                    .catch { e -> handleStreamError("test_results", e) }
                    .collect { _fsTestResults.value = it }
            }
            launch {
                repo.observeNotifications(user.uid)
                    .catch { e -> handleStreamError("notifications", e) }
                    .collect { _fsNotifications.value = it }
            }
            launch {
                repo.observeAnnouncements()
                    .catch { e -> handleStreamError("announcements", e) }
                    .collect { _fsAnnouncements.value = it }
            }
            launch {
                repo.observeApprovedKnowledgeSources()
                    .catch { e -> handleStreamError("approved_knowledge_sources", e) }
                    .collect { _fsKnowledgeSources.value = it }
            }
            launch {
                repo.observeAiFeedback(user.uid, isAdmin = isAdmin)
                    .catch { e -> handleStreamError("ai_feedback", e) }
                    .collect { _fsFeedback.value = it }
            }

            if (isAdmin) {
                launch {
                    repo.observeAllUsersForAdmin()
                        .catch { e -> handleStreamError("admin_users", e) }
                        .collect { _fsAllUsers.value = it }
                }
                launch {
                    repo.observeAuditLogsForAdmin()
                        .catch { e -> handleStreamError("admin_audit_logs", e) }
                        .collect { _fsAuditLogs.value = it }
                }
            }
        }
    }

    private fun handleStreamError(label: String, error: Throwable) {
        if (error is CancellationException) throw error
        Log.e(VM_TAG, "Firestore stream error ($label): ${error.message}", error)
        _syncStatus.value = "Sync Issue ($label)"
    }

    private fun pushPrefsToCloud() {
        val repo = firestoreRepo ?: return
        if (Firebase.auth.currentUser == null) return
        val s = settings.value
        viewModelScope.launch {
            repo.syncPreferences(
                language = s.language.code,
                darkMode = s.darkMode,
                autoScrollSpeed = s.autoScrollSpeed.toDouble(),
                selectedReciterId = s.selectedReciterId,
                playbackSpeed = s.playbackSpeed.toDouble(),
                prayerNotifications = s.notifyFajr || s.notifyDhuhr || s.notifyAsr || s.notifyMaghrib || s.notifyIsha,
                quranReminders = true,
                revisionReminders = true
            )
        }
    }

    private fun pushProgressToCloud(
        page: Int = _currentPage.value,
        surah: Int = settings.value.lastSurah,
        ayah: Int = settings.value.lastAyah,
        dailyGoal: Int? = null,
        incrementReadPages: Int = 0
    ) {
        val repo = firestoreRepo ?: return
        if (Firebase.auth.currentUser == null) return
        val cur = _fsUserProgress.value
        viewModelScope.launch {
            repo.syncProgress(
                lastPage = page,
                lastSurah = surah,
                lastAyah = ayah,
                memorizedAyahsCount = (cur?.memorizedAyahsCount ?: 0L).toInt(),
                memorizedPagesCount = (cur?.memorizedPagesCount ?: 0L).toInt(),
                dailyGoalPages = dailyGoal ?: (cur?.dailyGoalPages ?: 4L).toInt(),
                dailyReadPages = ((cur?.dailyReadPages ?: 0L).toInt() + incrementReadPages).coerceAtMost(604),
                completedRevisions = (cur?.completedRevisions ?: 0L).toInt()
            )
        }
    }

    // Theme & Language
    fun setDarkMode(dark: Boolean) = viewModelScope.launch {
        prefs.setDarkMode(dark)
        pushPrefsToCloud()
    }

    fun setLanguage(lang: AppLanguage) = viewModelScope.launch {
        prefs.setLanguage(lang)
        pushPrefsToCloud()
    }

    fun setOnlineStreaming(allowed: Boolean) = viewModelScope.launch {
        prefs.setAllowOnlineAudioStreaming(allowed)
    }

    fun setDailyGoalPages(goal: Int) {
        pushProgressToCloud(dailyGoal = goal.coerceIn(1, 100))
        postStatus("Daily reading goal updated: ${goal.coerceIn(1, 100)} pages")
    }

    // Navigation & Mushaf Actions
    fun goToPage(page: Int, recordHistory: Boolean = true) {
        val clamped = page.coerceIn(1, 604)
        _currentPage.value = clamped
        val surah = QuranCatalog.surahsOnPage(clamped).firstOrNull()?.number ?: 1
        val ayah = QuranCatalog.ayahsOnPage(clamped).firstOrNull()?.ayah ?: 1
        viewModelScope.launch {
            prefs.saveLastPosition(clamped, surah, ayah)
            if (recordHistory) {
                db.quranDataDao().insertHistory(ReadingHistoryEntity(page = clamped, surah = surah, ayah = ayah))
                if (firestoreRepo != null && Firebase.auth.currentUser != null) {
                    firestoreRepo.addReadingHistory(clamped, surah, ayah)
                }
            }
            pushProgressToCloud(
                page = clamped,
                surah = surah,
                ayah = ayah,
                incrementReadPages = if (recordHistory) 1 else 0
            )
        }
    }

    fun selectAyah(ayah: AyahRecord?) { _selectedAyah.value = ayah }

    fun toggleBookmark(page: Int, surah: Int, ayah: Int, isFavorite: Boolean, note: String? = null) = viewModelScope.launch {
        val surahName = QuranCatalog.surahByNumber(surah)?.nameAr ?: "سورة $surah"
        db.quranDataDao().insertBookmark(
            BookmarkEntity(page = page, surah = surah, ayah = ayah, label = "$surahName : $ayah", isFavorite = isFavorite)
        )
        if (firestoreRepo != null && Firebase.auth.currentUser != null) {
            firestoreRepo.saveBookmark(page, surah, ayah, isFavorite, note).onFailure { err ->
                postStatus("Saved locally; cloud sync error: ${err.message}")
                return@launch
            }
        }
        postStatus(if (isFavorite) "Added to favorites" else "Page $page bookmarked")
    }

    fun deleteBookmark(b: BookmarkEntity) = viewModelScope.launch { db.quranDataDao().deleteBookmark(b) }

    fun playFromAyah(surah: Int, ayah: Int) {
        val reciter = reciters.value.firstOrNull { it.id == settings.value.selectedReciterId }
            ?: reciters.value.firstOrNull()
            ?: ReciterConfigEntity("qatami", "الشيخ ناصر القطامي", "Sheikh Nasser Al-Qatami", "")
        QuranAudioEngine.playFromAyah(
            context = ctx,
            reciter = reciter,
            surah = surah,
            ayah = ayah,
            speed = settings.value.playbackSpeed,
            allowOnlineStreaming = settings.value.allowOnlineAudioStreaming
        )
    }

    fun chooseReciter(reciter: ReciterConfigEntity) = viewModelScope.launch {
        prefs.setSelectedReciter(reciter.id)
        pushPrefsToCloud()
        val cur = QuranAudioEngine.state.value
        if (cur.isPlaying || cur.isPaused) {
            QuranAudioEngine.playFromAyah(
                context = ctx,
                reciter = reciter,
                surah = cur.currentSurah,
                ayah = cur.currentAyah,
                speed = settings.value.playbackSpeed,
                allowOnlineStreaming = settings.value.allowOnlineAudioStreaming
            )
        }
        postStatus("Reciter selected: ${reciter.nameAr}")
    }

    fun importMushafZip(uri: Uri) = viewModelScope.launch {
        val res = MushafPackImporter.importZipPack(ctx, uri)
        res.onSuccess { summary ->
            _mushafPackPagesCount.value = MushafCoordinateEngine.installedImagePageCount(ctx)
            postStatus("Imported ${summary.pagesImported} Mushaf pages, ${summary.coordinateEntriesLoaded} coordinate boxes, ${summary.ayahsLoaded} Ayahs.")
        }.onFailure {
            postStatus("Mushaf import failed: ${it.message}")
        }
    }

    // Memorization & Real Recitation Assessment
    fun createMemorizationPlan(title: String, surah: Int, startAyah: Int, endAyah: Int, dailyTarget: Int) =
        viewModelScope.launch {
            val safeEnd = endAyah.coerceAtLeast(startAyah)
            db.memorizationDao().upsertPlan(
                MemorizationPlanEntity(
                    title = title.ifBlank { QuranCatalog.surahByNumber(surah)?.nameAr ?: "Plan" },
                    surah = surah,
                    startAyah = startAyah,
                    endAyah = safeEnd,
                    dailyAyahTarget = dailyTarget.coerceAtLeast(1)
                )
            )
            if (firestoreRepo != null && Firebase.auth.currentUser != null) {
                firestoreRepo.saveMemorizationSession(surah, startAyah, safeEnd, "IN_PROGRESS", false, 0)
            }
            postStatus("Memorization plan saved")
        }

    fun evaluateRecitation(
        surah: Int,
        startAyah: Int,
        endAyah: Int,
        recognizedTranscript: String,
        audioPath: String,
        durationMs: Long,
        longPauses: Int,
        confidence: Float
    ) = viewModelScope.launch {
        val expected = QuranCatalog.ayahsForSurahRange(surah, startAyah, endAyah)
        if (expected.isEmpty()) {
            postStatus("No verified Quran text loaded for Surah $surah:$startAyah-$endAyah. Import full Quran pack or select a bundled Surah (e.g. Al-Fatihah, Al-Mulk, Al-Ikhlas).")
            return@launch
        }
        val result = QuranRecitationComparator.compare(
            expectedAyahs = expected,
            recognizedTranscript = recognizedTranscript,
            asrConfidence = confidence,
            longPauseCount = longPauses
        )
        _latestComparison.value = result
        val user = _currentUser.value
        val fsUser = _fsUserProfile.value
        db.memorizationDao().insertAssessment(
            RecitationAssessmentEntity(
                studentId = user?.id ?: 0L,
                studentName = fsUser?.displayName?.ifBlank { null } ?: user?.name ?: "Local Reader",
                halaqahId = user?.halaqahId,
                surah = surah,
                startAyah = startAyah,
                endAyah = endAyah,
                audioPath = audioPath,
                durationMs = durationMs,
                recognizedText = recognizedTranscript,
                accuracyPercent = result.accuracyPercent,
                correctCount = result.correctCount,
                uncertainCount = result.uncertainCount,
                mistakeCount = result.omittedCount + result.replacedCount + result.addedCount + result.repeatedCount,
                longPauseCount = result.longPauseCount,
                wordDiffJson = result.toDiffJson()
            )
        )
        result.wordItems.filter {
            it.status == com.example.recitation.WordComparisonStatus.OMITTED ||
                it.status == com.example.recitation.WordComparisonStatus.REPLACED
        }.take(20).forEach { m ->
            db.memorizationDao().insertMistake(
                MistakeRecordEntity(
                    surah = m.surah,
                    ayah = m.ayah,
                    wordIndex = m.wordIndex,
                    expectedWord = m.expectedWord,
                    spokenWord = m.spokenWord,
                    errorType = m.status.name
                )
            )
        }
        if (firestoreRepo != null && Firebase.auth.currentUser != null) {
            val statusStr = if (confidence >= 0.7f) "CONFIRMED" else "LOW_CONFIDENCE_REVIEW"
            val speechMode = if (audioPath.isBlank()) "RECORDED_ONLY" else "OFFLINE"
            firestoreRepo.saveRecordingAndAssessment(
                surah = surah,
                startAyah = startAyah,
                endAyah = endAyah,
                durationMs = durationMs,
                localFileName = File(audioPath).name.ifBlank { "recitation.m4a" },
                speechMode = speechMode,
                recognizedText = recognizedTranscript,
                confidence = confidence.toDouble(),
                accuracyPercent = result.accuracyPercent,
                omittedCount = result.omittedCount,
                repeatedCount = result.repeatedCount,
                replacedCount = result.replacedCount,
                assessmentStatus = statusStr
            )
            val memStatus = if (result.accuracyPercent >= 85) "MEMORIZED" else "NEEDS_REVISION"
            firestoreRepo.saveMemorizationSession(
                surah = surah,
                startAyah = startAyah,
                endAyah = endAyah,
                status = memStatus,
                memorized = result.accuracyPercent >= 85,
                accuracyPercent = result.accuracyPercent
            )
        }
        postStatus("Assessment saved (${result.accuracyPercent}% match)")
    }

    fun recordTestResult(
        testType: String,
        surah: Int,
        startAyah: Int,
        endAyah: Int,
        scorePercent: Int,
        totalQuestions: Int,
        correctAnswers: Int
    ) = viewModelScope.launch {
        if (firestoreRepo != null && Firebase.auth.currentUser != null) {
            firestoreRepo.saveTestResult(
                testType = testType,
                surah = surah,
                startAyah = startAyah,
                endAyah = endAyah,
                scorePercent = scorePercent,
                totalQuestions = totalQuestions,
                correctAnswers = correctAnswers
            ).onFailure { err ->
                postStatus("Failed to save cloud test result: ${err.message}")
                return@launch
            }
        }
        postStatus("Memorization test saved ($scorePercent%)")
    }

    fun resolveMistake(id: Long) = viewModelScope.launch {
        db.memorizationDao().markMistakeResolved(id, true)
    }

    fun reviewAssessment(assessment: RecitationAssessmentEntity, newStatus: String, notes: String) =
        viewModelScope.launch {
            val reviewer = _currentUser.value
            val isAllowed = _isBackendAdmin.value || UserRole.fromName(reviewer?.role).level >= UserRole.TEACHER.level
            if (!isAllowed) {
                postStatus("Teacher or Admin role required to approve recitation assessments.")
                return@launch
            }
            db.memorizationDao().updateAssessment(
                assessment.copy(status = newStatus, teacherNotes = notes, reviewedBy = reviewer?.name ?: "Teacher")
            )
            logAudit("REVIEW_ASSESSMENT", "Assessment #${assessment.id} -> $newStatus")
            postStatus("Assessment updated: $newStatus")
        }

    // Khatmah & Fasting (CRITICAL BUG FIX: NEVER exits or navigates away on task completion!)
    fun observeKhatmahLogs(khatmahId: Long) = db.worshipDao().observeKhatmahLogs(khatmahId)
    fun observeFastingLogs(planId: Long) = db.worshipDao().observeFastingLogs(planId)

    fun createKhatmah(title: String, targetDays: Int) = viewModelScope.launch {
        val days = targetDays.coerceIn(7, 365)
        val cleanTitle = title.ifBlank { "ختمة $days يوماً" }
        val id = db.worshipDao().upsertKhatmah(
            KhatmahEntity(title = cleanTitle, targetDays = days, totalPages = 604)
        )
        val perDay = ceil(604.0 / days).toInt().coerceAtLeast(1)
        val logs = mutableListOf<KhatmahDailyLogEntity>()
        var page = 1
        var day = 1
        while (page <= 604) {
            val endPage = (page + perDay - 1).coerceAtMost(604)
            logs.add(KhatmahDailyLogEntity(khatmahId = id, dayNumber = day, fromPage = page, toPage = endPage))
            page = endPage + 1
            day++
        }
        db.worshipDao().insertKhatmahLogs(logs)
        if (firestoreRepo != null && Firebase.auth.currentUser != null) {
            firestoreRepo.syncKhatmah("kh_$id", cleanTitle, days, 0, 1, false)
        }
        postStatus("Khatmah created ($days days, ~$perDay pages/day)")
    }

    fun toggleKhatmahDayCompleted(khatmah: KhatmahEntity, log: KhatmahDailyLogEntity) = viewModelScope.launch {
        val nextDone = !log.isCompleted
        db.worshipDao().updateKhatmahLog(
            log.copy(isCompleted = nextDone, completedAt = if (nextDone) System.currentTimeMillis() else null)
        )
        val allLogs = db.worshipDao().getKhatmahLogsOnce(khatmah.id)
        val pagesDone = allLogs.filter { it.isCompleted }.sumOf { (it.toPage - it.fromPage + 1) }.coerceAtMost(604)
        val isDone = pagesDone >= 604
        db.worshipDao().upsertKhatmah(
            khatmah.copy(completedPages = pagesDone, isCompleted = isDone)
        )
        if (firestoreRepo != null && Firebase.auth.currentUser != null) {
            firestoreRepo.syncKhatmah("kh_${khatmah.id}", khatmah.title, khatmah.targetDays, pagesDone, log.toPage, isDone)
        }
        postStatus(if (nextDone) "Daily portion Day ${log.dayNumber} completed ($pagesDone/604 pages)" else "Portion unchecked")
    }

    fun createFastingPlan(title: String, type: String, targetDays: Int) = viewModelScope.launch {
        val days = targetDays.coerceIn(1, 60)
        val cleanTitle = title.ifBlank { "خطة صيام ($days أيام)" }
        val id = db.worshipDao().upsertFastingPlan(
            FastingPlanEntity(title = cleanTitle, planType = type, targetDays = days)
        )
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val logs = (1..days).map { idx ->
            val dateStr = fmt.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, if (type == "MON_THU") 3 else 1)
            FastingDayLogEntity(planId = id, dayLabel = "Day $idx", dateIso = dateStr)
        }
        db.worshipDao().insertFastingLogs(logs)
        if (firestoreRepo != null && Firebase.auth.currentUser != null) {
            firestoreRepo.syncFasting("fp_$id", cleanTitle, type, 0, days)
        }
        postStatus("Fasting plan created")
    }

    fun toggleFastingDayCompleted(plan: FastingPlanEntity, log: FastingDayLogEntity) = viewModelScope.launch {
        val nextDone = !log.isCompleted
        db.worshipDao().updateFastingLog(
            log.copy(isCompleted = nextDone, completedAt = if (nextDone) System.currentTimeMillis() else null)
        )
        val all = db.worshipDao().getFastingLogsOnce(plan.id)
        val doneCount = all.count { it.isCompleted }
        db.worshipDao().upsertFastingPlan(plan.copy(completedDays = doneCount))
        if (firestoreRepo != null && Firebase.auth.currentUser != null) {
            firestoreRepo.syncFasting("fp_${plan.id}", plan.title, plan.planType, doneCount, plan.targetDays)
        }
        postStatus(if (nextDone) "Fasting day completed ($doneCount/${plan.targetDays})" else "Fasting day unchecked")
    }

    // Prayer & Adhan
    fun selectCity(cityId: String) = viewModelScope.launch {
        val c = PrayerTimesCalculator.cityById(cityId)
        prefs.setLocation(c.id, c.latitude, c.longitude)
        reschedulePrayers()
    }

    fun detectGpsLocation() = viewModelScope.launch {
        val coords = PrayerTimesCalculator.tryDetectLastKnownLocation(ctx)
        if (coords != null) {
            prefs.setLocation("gps_detected", coords.first, coords.second)
            reschedulePrayers()
            postStatus("Location updated from device sensors.")
        } else {
            postStatus("Location unavailable or permission denied; select a city manually.")
        }
    }

    fun setPrayerMethod(method: CalculationMethod) = viewModelScope.launch {
        prefs.setCalculationMethod(method)
        reschedulePrayers()
    }

    fun setPrayerToggle(prayer: String, enabled: Boolean) = viewModelScope.launch {
        prefs.setPrayerToggle(prayer, enabled)
        reschedulePrayers()
        pushPrefsToCloud()
    }

    fun setPrayerOffsets(fajr: Int, dhuhr: Int, asr: Int, maghrib: Int, isha: Int) = viewModelScope.launch {
        prefs.setPrayerOffsets(fajr, dhuhr, asr, maghrib, isha)
        reschedulePrayers()
    }

    private fun reschedulePrayers() {
        val s = settings.value
        val times = dailyPrayerTimes.value
        AdhanAudioManager.scheduleNextPrayerAlarms(
            ctx, times,
            mapOf(
                "FAJR" to s.notifyFajr,
                "DHUHR" to s.notifyDhuhr,
                "ASR" to s.notifyAsr,
                "MAGHRIB" to s.notifyMaghrib,
                "ISHA" to s.notifyIsha
            )
        )
    }

    fun importAdhanFile(uri: Uri) = viewModelScope.launch {
        AdhanAudioManager.importCustomAdhanAudio(ctx, uri).onSuccess { voice ->
            db.adminDao().upsertAdhanVoice(voice)
            prefs.setAdhanVoice(voice.id)
            postStatus("Imported Adhan audio: ${voice.nameEn} (${voice.format})")
        }.onFailure {
            postStatus("Adhan import error: ${it.message}")
        }
    }

    fun selectAdhanVoice(voice: AdhanVoiceEntity) = viewModelScope.launch {
        prefs.setAdhanVoice(voice.id)
        postStatus("Default Adhan set to ${voice.nameAr}")
    }

    fun assignAdhanPrayers(voice: AdhanVoiceEntity, prayersCsv: String) = viewModelScope.launch {
        db.adminDao().upsertAdhanVoice(voice.copy(assignedPrayers = prayersCsv))
        postStatus("Updated prayer assignment for ${voice.nameAr}")
    }

    // Notifications Center
    fun markCloudNotificationRead(notifId: String, read: Boolean) = viewModelScope.launch {
        firestoreRepo?.markNotificationRead(notifId, read)
    }

    fun sendDailyTestNotification(title: String, body: String, category: String = "QURAN") = viewModelScope.launch {
        NotificationHelper.notifyAnnouncement(
            ctx,
            AnnouncementEntity(title = title, body = body, targetAudience = "ALL", notificationType = category)
        )
        if (firestoreRepo != null && Firebase.auth.currentUser != null) {
            firestoreRepo.addUserNotification(title, body, category)
        }
        postStatus("Notification recorded")
    }

    // NOOR AI: Streaming Firebase AI Logic + Grounded Local & Cloud RAG
    fun askNoorAi(question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        val isArabic = settings.value.language == AppLanguage.AR
        val answerMsgId = System.nanoTime() + 1L

        _aiChat.update { it + AiChatMessage(isUser = true, text = q) }

        val canUseGemini = firestoreRepo != null && Firebase.auth.currentUser != null
        if (!canUseGemini) {
            viewModelScope.launch(Dispatchers.IO) {
                val answer = ragEngine.answerQuestion(q, isArabicUi = isArabic)
                _aiChat.update {
                    it + AiChatMessage(
                        id = answerMsgId,
                        isUser = false,
                        text = answer.answerText,
                        answerMeta = answer,
                        citations = answer.citations.map { c -> "${c.title} (${c.reference})" },
                        questionRef = q
                    )
                }
            }
            return
        }

        _aiChat.update {
            it + AiChatMessage(
                id = answerMsgId,
                isUser = false,
                text = if (isArabic) "جاري البحث في المصادر المعتمدة وصياغة الإجابة..." else "Searching approved references...",
                usedGemini = true,
                isStreaming = true,
                questionRef = q
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val remoteSources = _fsKnowledgeSources.value
            val finalState = geminiRagService.askWithRagStream(
                question = q,
                isArabicUi = isArabic,
                remoteApprovedSources = remoteSources
            ) { state ->
                _aiStreamState.value = state
                when (state) {
                    is AiStreamState.Streaming -> {
                        _aiChat.update { list ->
                            list.map { msg ->
                                if (msg.id == answerMsgId) {
                                    msg.copy(text = state.text, citations = state.citations, isStreaming = true)
                                } else msg
                            }
                        }
                    }
                    is AiStreamState.Success -> {
                        _aiChat.update { list ->
                            list.map { msg ->
                                if (msg.id == answerMsgId) {
                                    msg.copy(
                                        text = state.text,
                                        citations = state.citations,
                                        usedGemini = state.usedGemini,
                                        isStreaming = false
                                    )
                                } else msg
                            }
                        }
                    }
                    is AiStreamState.Error -> {
                        val fallback = state.localFallback
                        val combinedText = if (fallback != null) {
                            "${fallback.answerText}\n\n[${state.message}]"
                        } else {
                            state.message
                        }
                        _aiChat.update { list ->
                            list.map { msg ->
                                if (msg.id == answerMsgId) {
                                    msg.copy(
                                        text = combinedText,
                                        answerMeta = fallback,
                                        citations = fallback?.citations?.map { c -> "${c.title} (${c.reference})" } ?: emptyList(),
                                        usedGemini = false,
                                        isStreaming = false
                                    )
                                } else msg
                            }
                        }
                    }
                    else -> Unit
                }
            }

            if (finalState is AiStreamState.Success && !finalState.grounded) {
                firestoreRepo.submitAiFeedback(
                    question = q,
                    aiResponse = finalState.text,
                    helpful = true,
                    unanswered = true
                )
            }
        }
    }

    fun submitAiMessageFeedback(msg: AiChatMessage, helpful: Boolean) = viewModelScope.launch {
        val repo = firestoreRepo ?: return@launch
        repo.submitAiFeedback(
            question = msg.questionRef.ifBlank { "NOOR AI Query" },
            aiResponse = msg.text,
            helpful = helpful,
            unanswered = !helpful
        ).onSuccess {
            _aiChat.update { list ->
                list.map { if (it.id == msg.id) it.copy(feedbackSubmitted = true) else it }
            }
            postStatus("Feedback sent to reviewers. Thank you!")
        }.onFailure { err ->
            postStatus("Could not send feedback: ${err.message}")
        }
    }

    // Google Sign-Out & Account Deletion
    fun signOutWithCleanup(context: Context, onComplete: () -> Unit = {}) {
        cloudCollectorJob?.cancel()
        boundUid = null
        _isBackendAdmin.value = false
        _currentUser.value = null
        _fsUserProfile.value = null
        _fsUserProgress.value = null
        _fsBookmarks.value = emptyList()
        _fsTestResults.value = emptyList()
        _fsNotifications.value = emptyList()
        _fsAllUsers.value = emptyList()
        _fsAuditLogs.value = emptyList()
        signOutUser(context, viewModelScope, onSignOutComplete = onComplete)
    }

    fun deleteAccountAndPersonalData(onComplete: (Boolean, String) -> Unit) = viewModelScope.launch {
        val repo = firestoreRepo
        if (repo == null) {
            onComplete(false, "Cloud repository unavailable.")
            return@launch
        }
        val res = repo.deleteAccountAndPersonalData()
        res.onSuccess {
            cloudCollectorJob?.cancel()
            boundUid = null
            _isBackendAdmin.value = false
            _currentUser.value = null
            onComplete(true, "Account and personal data deleted.")
        }.onFailure { err ->
            onComplete(false, err.localizedMessage ?: "Account deletion may require recent Google Sign-In.")
        }
    }

    // Legacy Local Admin Auth (kept cleanly for offline Robolectric tests & local-only Halaqat management)
    fun login(username: String, password: String, onResult: (Boolean, String) -> Unit) = viewModelScope.launch {
        val user = db.userDao().findByUsername(username.trim())
        if (user == null || !user.isActive) {
            onResult(false, "Account not found or inactive.")
            return@launch
        }
        val valid = PasswordHasher.verifyPassword(password, user.salt, user.passwordHash)
        if (!valid) {
            onResult(false, "Incorrect password.")
            return@launch
        }
        _currentUser.value = user
        logAudit("LOGIN", "User ${user.username} (${user.role}) signed in")
        onResult(true, "Welcome ${user.name}")
    }

    fun logout() {
        val u = _currentUser.value
        _currentUser.value = null
        if (u != null) viewModelScope.launch { logAudit("LOGOUT", "${u.username} signed out") }
    }

    // Cloud Admin Actions (Enforced server-side by Firestore Security Rules)
    fun publishCloudAnnouncement(title: String, message: String, audience: String) = viewModelScope.launch {
        val repo = firestoreRepo ?: return@launch
        repo.publishAnnouncement(title, message, audience)
            .onSuccess { postStatus("Cloud announcement published") }
            .onFailure { postStatus("Admin permission error: ${it.message}") }
    }

    fun upsertCloudKnowledgeSource(
        docId: String?,
        title: String,
        category: String,
        question: String?,
        answer: String,
        keywords: String?,
        reference: String,
        approvalStatus: String
    ) = viewModelScope.launch {
        val repo = firestoreRepo ?: return@launch
        repo.upsertKnowledgeSource(docId, title, category, question, answer, keywords, reference, approvalStatus)
            .onSuccess { postStatus("Knowledge source synced to cloud") }
            .onFailure { postStatus("Knowledge update error: ${it.message}") }
    }

    fun deleteCloudKnowledgeSource(docId: String) = viewModelScope.launch {
        val repo = firestoreRepo ?: return@launch
        repo.deleteKnowledgeSource(docId)
            .onSuccess { postStatus("Knowledge source deleted") }
            .onFailure { postStatus("Delete error: ${it.message}") }
    }

    fun updateCloudUserRoleAndStatus(targetUid: String, targetEmail: String, role: String, status: String) = viewModelScope.launch {
        val repo = firestoreRepo ?: return@launch
        repo.updateUserRoleAndStatus(targetUid, targetEmail, role, status)
            .onSuccess { postStatus("Updated $targetEmail -> $role ($status)") }
            .onFailure { postStatus("Role update failed: ${it.message}") }
    }

    fun markCloudFeedbackReviewed(feedbackId: String) = viewModelScope.launch {
        val repo = firestoreRepo ?: return@launch
        repo.markFeedbackReviewed(feedbackId)
            .onSuccess { postStatus("Feedback marked reviewed") }
            .onFailure { postStatus("Feedback review error: ${it.message}") }
    }

    // Admin CRUD (Local Room Tools Guarded by PermissionGuard or Backend Admin)
    private fun effectiveRole(): UserRole =
        if (_isBackendAdmin.value) UserRole.SUPER_ADMIN else currentRole

    private suspend fun logAudit(action: String, details: String) {
        val u = _currentUser.value
        db.adminDao().insertAuditLog(
            AuditLogEntity(
                actorUsername = _fsUserProfile.value?.email ?: u?.username ?: "system",
                actorRole = effectiveRole().name,
                action = action,
                details = details
            )
        )
    }

    fun createUserAccount(name: String, username: String, password: String, role: UserRole, halaqahId: Long?) =
        viewModelScope.launch {
            runCatching {
                PermissionGuard.requirePermission(effectiveRole(), AdminPermission.MANAGE_STUDENTS)
                require(username.isNotBlank() && password.length >= 6) { "Username required and password >= 6 chars" }
                val salt = PasswordHasher.generateSalt()
                val hash = PasswordHasher.hashPassword(password, salt)
                db.userDao().insert(
                    UserAccountEntity(
                        name = name.trim(),
                        username = username.trim(),
                        passwordHash = hash,
                        salt = salt,
                        role = role.name,
                        halaqahId = halaqahId
                    )
                )
                logAudit("CREATE_USER", "Created ${role.name} account: $username")
            }.onSuccess { postStatus("Account created") }
                .onFailure { postStatus("Error: ${it.message}") }
        }

    fun deleteUserAccount(user: UserAccountEntity) = viewModelScope.launch {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.MANAGE_STUDENTS)
            if (user.username == "superadmin") error("Cannot delete primary Super Admin")
            db.userDao().delete(user)
            logAudit("DELETE_USER", "Deleted ${user.username}")
        }.onFailure { postStatus("Error: ${it.message}") }
    }

    fun saveHalaqah(h: HalaqahEntity) = viewModelScope.launch {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.MANAGE_HALAQAT)
            db.halaqatDao().upsertHalaqah(h)
            logAudit("UPSERT_HALAQAH", h.name)
        }.onSuccess { postStatus("Halaqah saved") }
            .onFailure { postStatus("Error: ${it.message}") }
    }

    fun deleteHalaqah(h: HalaqahEntity) = viewModelScope.launch {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.MANAGE_HALAQAT)
            db.halaqatDao().deleteHalaqah(h)
            logAudit("DELETE_HALAQAH", h.name)
        }.onFailure { postStatus("Error: ${it.message}") }
    }

    fun recordHalaqahAttendance(halaqahId: Long, student: UserAccountEntity, status: String, pages: Int, grade: Int, note: String) =
        viewModelScope.launch {
            val dateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            db.halaqatDao().upsertAttendance(
                AttendanceEntity(
                    halaqahId = halaqahId,
                    studentId = student.id,
                    studentName = student.name,
                    dateIso = dateIso,
                    status = status,
                    pagesMemorized = pages,
                    grade = grade,
                    teacherNote = note
                )
            )
            postStatus("Attendance & grade recorded for ${student.name}")
        }

    fun saveEduLesson(lesson: EduLessonEntity) = viewModelScope.launch {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.MANAGE_QURAN_CONTENT)
            db.libraryDao().upsertLesson(lesson)
            logAudit("UPSERT_LESSON", lesson.titleAr)
        }.onSuccess { postStatus("Educational lesson saved") }
            .onFailure { postStatus("Error: ${it.message}") }
    }

    fun deleteEduLesson(lesson: EduLessonEntity) = viewModelScope.launch {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.MANAGE_QURAN_CONTENT)
            db.libraryDao().deleteLesson(lesson)
        }.onFailure { postStatus("Error: ${it.message}") }
    }

    fun addOrUpdateKnowledge(entry: KnowledgeEntryEntity) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.MANAGE_AI_KNOWLEDGE)
            val id = db.knowledgeDao().upsertEntry(entry.copy(updatedAt = System.currentTimeMillis()))
            ragEngine.chunkAndIndexEntry(entry.copy(id = id))
            if (firestoreRepo != null && Firebase.auth.currentUser != null && _isBackendAdmin.value) {
                firestoreRepo.upsertKnowledgeSource(
                    docId = null,
                    title = entry.title,
                    category = entry.category,
                    question = entry.question,
                    answer = entry.answer,
                    keywords = entry.keywords,
                    reference = entry.reference,
                    approvalStatus = entry.approvalStatus
                )
            }
            logAudit("UPSERT_AI_KB", "${entry.title} (${entry.approvalStatus})")
        }.onSuccess { postStatus("Knowledge entry saved & indexed") }
            .onFailure { postStatus("Error: ${it.message}") }
    }

    fun deleteKnowledgeEntry(entry: KnowledgeEntryEntity) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.MANAGE_AI_KNOWLEDGE)
            db.knowledgeDao().deleteEntry(entry)
            logAudit("DELETE_AI_KB", entry.title)
        }.onFailure { postStatus("Error: ${it.message}") }
    }

    fun importKnowledgeDocument(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.MANAGE_AI_KNOWLEDGE)
            val docs = DocumentExtractor.extractFromUri(ctx, uri).getOrThrow()
            var chunks = 0
            docs.forEach { d ->
                val entity = KnowledgeEntryEntity(
                    title = d.title,
                    category = d.category,
                    question = d.question,
                    answer = d.answer,
                    keywords = d.keywords,
                    reference = d.reference,
                    sourceFileName = d.sourceFileName,
                    approvalStatus = "APPROVED"
                )
                val id = db.knowledgeDao().upsertEntry(entity)
                ragEngine.chunkAndIndexEntry(entity.copy(id = id))
                chunks++
            }
            logAudit("IMPORT_AI_DOC", "Imported $chunks entries from ${docs.firstOrNull()?.sourceFileName}")
            chunks
        }.onSuccess { count ->
            postStatus("Imported and indexed $count knowledge document(s) into local RAG index.")
        }.onFailure {
            postStatus("Document import failed: ${it.message}")
        }
    }

    fun rebuildAiSearchIndex() = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.MANAGE_AI_KNOWLEDGE)
            val n = ragEngine.rebuildFullSearchIndex()
            logAudit("REBUILD_AI_INDEX", "Rebuilt $n searchable chunks")
            n
        }.onSuccess { postStatus("Search index rebuilt ($it approved chunks)") }
            .onFailure { postStatus("Rebuild failed: ${it.message}") }
    }

    fun sendAnnouncement(title: String, body: String, audience: String, type: String) = viewModelScope.launch {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.SEND_ANNOUNCEMENTS)
            val item = AnnouncementEntity(title = title, body = body, targetAudience = audience, notificationType = type)
            val id = db.adminDao().upsertAnnouncement(item)
            NotificationHelper.notifyAnnouncement(ctx, item.copy(id = id))
            if (firestoreRepo != null && Firebase.auth.currentUser != null && _isBackendAdmin.value) {
                firestoreRepo.publishAnnouncement(title, body, audience)
            }
            logAudit("SEND_ANNOUNCEMENT", "$audience: $title")
        }.onSuccess { postStatus("Announcement published") }
            .onFailure { postStatus("Error: ${it.message}") }
    }

    fun deleteAnnouncement(a: AnnouncementEntity) = viewModelScope.launch {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.SEND_ANNOUNCEMENTS)
            db.adminDao().deleteAnnouncement(a)
        }
    }

    fun savePromoCard(card: PromoCardEntity) = viewModelScope.launch {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.MANAGE_PROMO_CARDS)
            db.adminDao().upsertPromoCard(card)
            logAudit("UPSERT_PROMO_CARD", card.title)
        }.onSuccess { postStatus("Promo card saved") }
            .onFailure { postStatus("Error: ${it.message}") }
    }

    fun deletePromoCard(card: PromoCardEntity) = viewModelScope.launch {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.MANAGE_PROMO_CARDS)
            db.adminDao().deletePromoCard(card)
        }
    }

    fun setFeatureToggle(toggle: FeatureToggleEntity, enabled: Boolean) = viewModelScope.launch {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.MANAGE_FEATURE_TOGGLES)
            db.adminDao().upsertFeatureToggle(toggle.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
            logAudit("TOGGLE_FEATURE", "${toggle.key} = $enabled")
        }.onFailure { postStatus("Error: ${it.message}") }
    }

    fun importReciterPack(reciterId: String, uri: Uri) = viewModelScope.launch {
        QuranAudioEngine.importReciterPackZip(ctx, reciterId, uri).onSuccess { count ->
            postStatus("Imported $count audio/timing files for $reciterId")
        }.onFailure {
            postStatus("Reciter import failed: ${it.message}")
        }
    }

    fun exportDatabaseBackup(knowledgeOnly: Boolean) = viewModelScope.launch {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.BACKUP_RESTORE)
            val file = if (knowledgeOnly) {
                BackupAndReportExporter.createKnowledgeBackupJson(ctx, db)
            } else {
                BackupAndReportExporter.createFullDatabaseBackupJson(ctx, db)
            }
            logAudit("EXPORT_BACKUP", file.name)
            file
        }.onSuccess { f -> postStatus("Backup saved: ${f.name}") }
            .onFailure { postStatus("Backup failed: ${it.message}") }
    }

    fun restoreDatabaseBackup(uri: Uri) = viewModelScope.launch {
        runCatching {
            PermissionGuard.requirePermission(effectiveRole(), AdminPermission.BACKUP_RESTORE)
            val msg = BackupAndReportExporter.restoreBackupFromUri(ctx, db, uri).getOrThrow()
            ragEngine.rebuildFullSearchIndex()
            logAudit("RESTORE_BACKUP", msg)
            msg
        }.onSuccess { postStatus(it) }
            .onFailure { postStatus("Restore failed: ${it.message}") }
    }

    fun exportHalaqatReport(format: String, onFileReady: (File) -> Unit) = viewModelScope.launch {
        val hList = halaqat.value
        val attList = attendance.value
        val lines = buildList {
            add("Total Halaqat: ${hList.size}, Total Attendance Records: ${attList.size}")
            hList.forEach { h -> add("Halaqah: ${h.name} | Teacher: ${h.teacherName} | Target: ${h.memorizationTarget}") }
            attList.take(50).forEach { a ->
                add("${a.dateIso} | ${a.studentName} | ${a.status} | Pages: ${a.pagesMemorized} | Grade: ${a.grade}%")
            }
        }
        val file = when (format.uppercase()) {
            "CSV" -> BackupAndReportExporter.exportCsvReport(
                ctx, "noor_report_${System.currentTimeMillis()}",
                listOf("Date", "Student", "Status", "Pages", "Grade", "Note"),
                attList.map { listOf(it.dateIso, it.studentName, it.status, it.pagesMemorized.toString(), it.grade.toString(), it.teacherNote) }
            )
            "PDF" -> BackupAndReportExporter.exportPdfReport(
                ctx, "noor_report_${System.currentTimeMillis()}", "NOOR Halaqat & Student Report", lines
            )
            else -> BackupAndReportExporter.exportTxtReport(
                ctx, "noor_report_${System.currentTimeMillis()}", "NOOR Halaqat & Student Report", lines
            )
        }
        postStatus("Report exported: ${file.name}")
        onFileReady(file)
    }

    fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val mime = when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "csv" -> "text/csv"
            "json" -> "application/json"
            else -> "text/plain"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(intent, "Export NOOR Report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onCleared() {
        cloudCollectorJob?.cancel()
        audioRecorder.release()
        speechRecognizer.destroy()
        super.onCleared()
    }

    companion object {
        fun factory(application: Application, firestoreRepo: FirestoreNoorRepository?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NoorViewModel(application, firestoreRepo) as T
                }
            }
    }
}
