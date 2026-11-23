package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class UserRole(val level: Int, val labelAr: String, val labelEn: String) {
    STUDENT(1, "طالب", "Student"),
    TEACHER(2, "معلم قرآن", "Quran Teacher"),
    ADMIN(3, "مدير", "Administrator"),
    SUPER_ADMIN(4, "مدير عام", "Super Administrator");

    companion object {
        fun fromName(value: String?): UserRole =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: STUDENT
    }
}

@Entity(tableName = "user_accounts", indices = [Index(value = ["username"], unique = true)])
data class UserAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val username: String,
    val passwordHash: String,
    val salt: String,
    val role: String, // UserRole.name
    val halaqahId: Long? = null,
    val phoneOrNote: String = "",
    val isActive: Boolean = true,
    val mustChangePassword: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val page: Int,
    val surah: Int,
    val ayah: Int,
    val label: String,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reading_history")
data class ReadingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val page: Int,
    val surah: Int,
    val ayah: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "memorization_plans")
data class MemorizationPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val dailyAyahTarget: Int = 5,
    val completedAyahs: Int = 0,
    val nextRevisionAt: Long = System.currentTimeMillis() + 86_400_000L,
    val status: String = "ACTIVE", // ACTIVE, REVISION, COMPLETED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mistake_records")
data class MistakeRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surah: Int,
    val ayah: Int,
    val wordIndex: Int,
    val expectedWord: String,
    val spokenWord: String,
    val errorType: String, // OMITTED, ADDED, REPLACED, REPEATED, SKIPPED_AYAH
    val resolved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recitation_assessments")
data class RecitationAssessmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long = 0,
    val studentName: String = "Local Reader",
    val halaqahId: Long? = null,
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val audioPath: String,
    val durationMs: Long,
    val recognizedText: String,
    val accuracyPercent: Int,
    val correctCount: Int,
    val uncertainCount: Int,
    val mistakeCount: Int,
    val longPauseCount: Int,
    val wordDiffJson: String,
    val status: String = "PENDING_REVIEW", // PENDING_REVIEW, APPROVED, NEEDS_REVISION
    val teacherNotes: String = "",
    val reviewedBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "khatmahs")
data class KhatmahEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetDays: Int,
    val totalPages: Int = 604,
    val completedPages: Int = 0,
    val startDateMillis: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val isCompleted: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0
)

@Entity(
    tableName = "khatmah_daily_logs",
    foreignKeys = [ForeignKey(
        entity = KhatmahEntity::class,
        parentColumns = ["id"],
        childColumns = ["khatmahId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("khatmahId")]
)
data class KhatmahDailyLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val khatmahId: Long,
    val dayNumber: Int,
    val fromPage: Int,
    val toPage: Int,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null
)

@Entity(tableName = "fasting_plans")
data class FastingPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val planType: String, // MON_THU, WHITE_DAYS, RAMADAN, CUSTOM
    val targetDays: Int,
    val completedDays: Int = 0,
    val reminderEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "fasting_day_logs",
    foreignKeys = [ForeignKey(
        entity = FastingPlanEntity::class,
        parentColumns = ["id"],
        childColumns = ["planId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("planId")]
)
data class FastingDayLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val dayLabel: String,
    val dateIso: String,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null
)

@Entity(tableName = "halaqat")
data class HalaqahEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val teacherId: Long? = null,
    val teacherName: String = "",
    val scheduleText: String,
    val memorizationTarget: String,
    val revisionPlan: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "attendance_records")
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val halaqahId: Long,
    val studentId: Long,
    val studentName: String,
    val dateIso: String,
    val status: String, // PRESENT, ABSENT, EXCUSED, LATE
    val pagesMemorized: Int = 0,
    val grade: Int = 100,
    val teacherNote: String = ""
)

@Entity(tableName = "edu_lessons")
data class EduLessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // QURAN, TAFSIR, HADITH, ISLAMIC_STUDIES, ARABIC, TAJWEED, MEMORIZATION, REVISION, ARTICLE
    val titleAr: String,
    val titleEn: String,
    val contentAr: String,
    val contentEn: String,
    val referenceSource: String,
    val isVerifiedReference: Boolean = true,
    val attachmentPath: String = "",
    val isPublished: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "knowledge_entries")
data class KnowledgeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val question: String,
    val answer: String,
    val keywords: String,
    val reference: String,
    val sourceFileName: String = "Admin Entry",
    val approvalStatus: String = "APPROVED", // PENDING, APPROVED, REJECTED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "knowledge_chunks",
    foreignKeys = [ForeignKey(
        entity = KnowledgeEntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("entryId")]
)
data class KnowledgeChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val chunkIndex: Int,
    val title: String,
    val category: String,
    val content: String,
    val normalizedTokens: String,
    val keywords: String,
    val reference: String,
    val isApproved: Boolean = true
)

@Entity(tableName = "announcements")
data class AnnouncementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val targetAudience: String, // ALL, STUDENTS, TEACHERS, HALAQAH, USER
    val targetId: Long? = null,
    val notificationType: String, // GENERAL, QURAN_REMINDER, MEMORIZATION_REMINDER, NEW_LESSON, HALAQAH, ADMIN_URGENT
    val scheduledAt: Long? = null,
    val sentAt: Long? = System.currentTimeMillis(),
    val deliveryStatus: String = "DELIVERED_LOCAL", // SCHEDULED, DELIVERED_LOCAL
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "promo_cards")
data class PromoCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val imageUri: String = "",
    val internalRoute: String = "quran", // quran, memorize, worship, library, halaqat, ai
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val visibleToRole: String = "ALL", // ALL, STUDENT, TEACHER, ADMIN
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "feature_toggles")
data class FeatureToggleEntity(
    @PrimaryKey val key: String,
    val labelAr: String,
    val labelEn: String,
    val enabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actorUsername: String,
    val actorRole: String,
    val action: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "reciter_configs")
data class ReciterConfigEntity(
    @PrimaryKey val id: String,
    val nameAr: String,
    val nameEn: String,
    val remoteBaseUrl: String,
    val localAudioDir: String = "",
    val isDefault: Boolean = false,
    val isCustom: Boolean = false
)

@Entity(tableName = "adhan_voices")
data class AdhanVoiceEntity(
    @PrimaryKey val id: String,
    val nameAr: String,
    val nameEn: String,
    val filePath: String = "", // empty means bundled fallback tone unless imported
    val format: String = "MP3",
    val assignedPrayers: String = "ALL", // comma-separated or ALL
    val isDefault: Boolean = false,
    val isSystemFallback: Boolean = false
)
