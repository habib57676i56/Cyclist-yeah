package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM user_accounts ORDER BY role DESC, name ASC")
    fun observeAll(): Flow<List<UserAccountEntity>>

    @Query("SELECT * FROM user_accounts WHERE role = :role ORDER BY name ASC")
    fun observeByRole(role: String): Flow<List<UserAccountEntity>>

    @Query("SELECT * FROM user_accounts WHERE halaqahId = :halaqahId ORDER BY name ASC")
    fun observeByHalaqah(halaqahId: Long): Flow<List<UserAccountEntity>>

    @Query("SELECT * FROM user_accounts WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserAccountEntity?

    @Query("SELECT * FROM user_accounts WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): UserAccountEntity?

    @Query("SELECT COUNT(*) FROM user_accounts WHERE role = :role")
    suspend fun countByRole(role: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserAccountEntity): Long

    @Update
    suspend fun update(user: UserAccountEntity)

    @Delete
    suspend fun delete(user: UserAccountEntity)
}

@Dao
interface QuranDataDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("SELECT * FROM reading_history ORDER BY timestamp DESC LIMIT 50")
    fun observeHistory(): Flow<List<ReadingHistoryEntity>>

    @Insert
    suspend fun insertHistory(entry: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history")
    suspend fun clearHistory()
}

@Dao
interface MemorizationDao {
    @Query("SELECT * FROM memorization_plans ORDER BY createdAt DESC")
    fun observePlans(): Flow<List<MemorizationPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(plan: MemorizationPlanEntity): Long

    @Delete
    suspend fun deletePlan(plan: MemorizationPlanEntity)

    @Query("SELECT * FROM mistake_records ORDER BY resolved ASC, createdAt DESC")
    fun observeMistakes(): Flow<List<MistakeRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMistake(mistake: MistakeRecordEntity): Long

    @Query("UPDATE mistake_records SET resolved = :resolved WHERE id = :id")
    suspend fun markMistakeResolved(id: Long, resolved: Boolean)

    @Query("SELECT * FROM recitation_assessments ORDER BY createdAt DESC")
    fun observeAssessments(): Flow<List<RecitationAssessmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssessment(assessment: RecitationAssessmentEntity): Long

    @Update
    suspend fun updateAssessment(assessment: RecitationAssessmentEntity)

    @Delete
    suspend fun deleteAssessment(assessment: RecitationAssessmentEntity)
}

@Dao
interface WorshipDao {
    @Query("SELECT * FROM khatmahs ORDER BY isActive DESC, startDateMillis DESC")
    fun observeKhatmahs(): Flow<List<KhatmahEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKhatmah(khatmah: KhatmahEntity): Long

    @Query("SELECT * FROM khatmah_daily_logs WHERE khatmahId = :khatmahId ORDER BY dayNumber ASC")
    fun observeKhatmahLogs(khatmahId: Long): Flow<List<KhatmahDailyLogEntity>>

    @Query("SELECT * FROM khatmah_daily_logs WHERE khatmahId = :khatmahId ORDER BY dayNumber ASC")
    suspend fun getKhatmahLogsOnce(khatmahId: Long): List<KhatmahDailyLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKhatmahLogs(logs: List<KhatmahDailyLogEntity>)

    @Update
    suspend fun updateKhatmahLog(log: KhatmahDailyLogEntity)

    @Query("SELECT * FROM fasting_plans ORDER BY createdAt DESC")
    fun observeFastingPlans(): Flow<List<FastingPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFastingPlan(plan: FastingPlanEntity): Long

    @Query("SELECT * FROM fasting_day_logs WHERE planId = :planId ORDER BY id ASC")
    fun observeFastingLogs(planId: Long): Flow<List<FastingDayLogEntity>>

    @Query("SELECT * FROM fasting_day_logs WHERE planId = :planId ORDER BY id ASC")
    suspend fun getFastingLogsOnce(planId: Long): List<FastingDayLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFastingLogs(logs: List<FastingDayLogEntity>)

    @Update
    suspend fun updateFastingLog(log: FastingDayLogEntity)
}

@Dao
interface HalaqatDao {
    @Query("SELECT * FROM halaqat ORDER BY createdAt DESC")
    fun observeHalaqat(): Flow<List<HalaqahEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHalaqah(halaqah: HalaqahEntity): Long

    @Delete
    suspend fun deleteHalaqah(halaqah: HalaqahEntity)

    @Query("SELECT * FROM attendance_records ORDER BY dateIso DESC, id DESC")
    fun observeAttendance(): Flow<List<AttendanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendance(record: AttendanceEntity): Long
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM edu_lessons ORDER BY createdAt DESC")
    fun observeLessons(): Flow<List<EduLessonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLesson(lesson: EduLessonEntity): Long

    @Delete
    suspend fun deleteLesson(lesson: EduLessonEntity)
}

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_entries ORDER BY updatedAt DESC")
    fun observeEntries(): Flow<List<KnowledgeEntryEntity>>

    @Query("SELECT * FROM knowledge_entries WHERE approvalStatus = 'APPROVED'")
    suspend fun getApprovedEntries(): List<KnowledgeEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: KnowledgeEntryEntity): Long

    @Delete
    suspend fun deleteEntry(entry: KnowledgeEntryEntity)

    @Query("SELECT * FROM knowledge_chunks WHERE isApproved = 1")
    suspend fun getApprovedChunks(): List<KnowledgeChunkEntity>

    @Query("SELECT COUNT(*) FROM knowledge_chunks WHERE isApproved = 1")
    fun observeApprovedChunkCount(): Flow<Int>

    @Query("DELETE FROM knowledge_chunks WHERE entryId = :entryId")
    suspend fun deleteChunksForEntry(entryId: Long)

    @Query("DELETE FROM knowledge_chunks")
    suspend fun deleteAllChunks()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)
}

@Dao
interface AdminDao {
    @Query("SELECT * FROM announcements ORDER BY createdAt DESC")
    fun observeAnnouncements(): Flow<List<AnnouncementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnnouncement(item: AnnouncementEntity): Long

    @Delete
    suspend fun deleteAnnouncement(item: AnnouncementEntity)

    @Query("SELECT * FROM promo_cards ORDER BY displayOrder ASC, createdAt DESC")
    fun observePromoCards(): Flow<List<PromoCardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPromoCard(card: PromoCardEntity): Long

    @Delete
    suspend fun deletePromoCard(card: PromoCardEntity)

    @Query("SELECT * FROM feature_toggles ORDER BY key ASC")
    fun observeFeatureToggles(): Flow<List<FeatureToggleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFeatureToggle(toggle: FeatureToggleEntity)

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("SELECT * FROM reciter_configs ORDER BY isDefault DESC, nameEn ASC")
    fun observeReciters(): Flow<List<ReciterConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReciter(reciter: ReciterConfigEntity)

    @Delete
    suspend fun deleteReciter(reciter: ReciterConfigEntity)

    @Query("SELECT * FROM adhan_voices ORDER BY isDefault DESC, nameEn ASC")
    fun observeAdhanVoices(): Flow<List<AdhanVoiceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAdhanVoice(voice: AdhanVoiceEntity)

    @Delete
    suspend fun deleteAdhanVoice(voice: AdhanVoiceEntity)
}
