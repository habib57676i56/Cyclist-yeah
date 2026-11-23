package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.ai.ArabicNlpNormalizer
import com.example.security.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(
    entities = [
        UserAccountEntity::class,
        BookmarkEntity::class,
        ReadingHistoryEntity::class,
        MemorizationPlanEntity::class,
        MistakeRecordEntity::class,
        RecitationAssessmentEntity::class,
        KhatmahEntity::class,
        KhatmahDailyLogEntity::class,
        FastingPlanEntity::class,
        FastingDayLogEntity::class,
        HalaqahEntity::class,
        AttendanceEntity::class,
        EduLessonEntity::class,
        KnowledgeEntryEntity::class,
        KnowledgeChunkEntity::class,
        AnnouncementEntity::class,
        PromoCardEntity::class,
        FeatureToggleEntity::class,
        AuditLogEntity::class,
        ReciterConfigEntity::class,
        AdhanVoiceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NoorDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun quranDataDao(): QuranDataDao
    abstract fun memorizationDao(): MemorizationDao
    abstract fun worshipDao(): WorshipDao
    abstract fun halaqatDao(): HalaqatDao
    abstract fun libraryDao(): LibraryDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun adminDao(): AdminDao

    companion object {
        @Volatile
        private var INSTANCE: NoorDatabase? = null

        fun getInstance(context: Context): NoorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoorDatabase::class.java,
                    "noor_offline.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

object NoorDatabaseSeeder {
    suspend fun seedIfEmpty(db: NoorDatabase) = withContext(Dispatchers.IO) {
        val userDao = db.userDao()
        val adminDao = db.adminDao()

        if (userDao.findByUsername("superadmin") == null) {
            val adminSalt = PasswordHasher.generateSalt()
            val adminHash = PasswordHasher.hashPassword("noor-admin-2026", adminSalt)
            userDao.insert(
                UserAccountEntity(
                    name = "المشرف العام - Super Admin",
                    username = "superadmin",
                    passwordHash = adminHash,
                    salt = adminSalt,
                    role = UserRole.SUPER_ADMIN.name,
                    mustChangePassword = true
                )
            )
            val teacherSalt = PasswordHasher.generateSalt()
            val teacherId = userDao.insert(
                UserAccountEntity(
                    name = "الشيخ أحمد الحافظ",
                    username = "teacher1",
                    passwordHash = PasswordHasher.hashPassword("noor-teacher", teacherSalt),
                    salt = teacherSalt,
                    role = UserRole.TEACHER.name
                )
            )
            val halaqahId = db.halaqatDao().upsertHalaqah(
                HalaqahEntity(
                    name = "حلقة النور - جزء عم",
                    teacherId = teacherId,
                    teacherName = "الشيخ أحمد الحافظ",
                    scheduleText = "السبت والاثنين والأربعاء 17:00",
                    memorizationTarget = "سورة النبأ إلى الناس",
                    revisionPlan = "مراجعة نصف حزب يومياً"
                )
            )
            val studentSalt = PasswordHasher.generateSalt()
            userDao.insert(
                UserAccountEntity(
                    name = "طارق الشامي",
                    username = "student1",
                    passwordHash = PasswordHasher.hashPassword("noor-student", studentSalt),
                    salt = studentSalt,
                    role = UserRole.STUDENT.name,
                    halaqahId = halaqahId
                )
            )
        }

        // Default Reciters
        adminDao.upsertReciter(
            ReciterConfigEntity(
                id = "qatami",
                nameAr = "الشيخ ناصر القطامي",
                nameEn = "Sheikh Nasser Al-Qatami",
                remoteBaseUrl = "https://everyayah.com/data/Nasser_Alqatami_128kbps",
                isDefault = true
            )
        )
        adminDao.upsertReciter(
            ReciterConfigEntity(
                id = "muaiqly",
                nameAr = "الشيخ ماهر المعيقلي",
                nameEn = "Sheikh Maher Al-Muaiqly",
                remoteBaseUrl = "https://everyayah.com/data/Maher_AlMuaiqly_64kbps",
                isDefault = false
            )
        )
        adminDao.upsertReciter(
            ReciterConfigEntity(
                id = "afasy",
                nameAr = "الشيخ مشاري العفاسي",
                nameEn = "Sheikh Mishary Al-Afasy",
                remoteBaseUrl = "https://everyayah.com/data/Alafasy_128kbps",
                isDefault = false
            )
        )
        adminDao.upsertReciter(
            ReciterConfigEntity(
                id = "abdulbasit",
                nameAr = "الشيخ عبد الباسط عبد الصمد",
                nameEn = "Sheikh Abdulbasit Abdussamad",
                remoteBaseUrl = "https://everyayah.com/data/AbdulSamad_64kbps_QuranExplorer.Com",
                isDefault = false
            )
        )

        // Default Adhan Voices (Nasser Al-Qatami default + built-in fallback)
        adminDao.upsertAdhanVoice(
            AdhanVoiceEntity(
                id = "adhan_qatami",
                nameAr = "أذان الشيخ ناصر القطامي",
                nameEn = "Sheikh Nasser Al-Qatami Adhan",
                filePath = "",
                format = "MP3",
                assignedPrayers = "ALL",
                isDefault = true,
                isSystemFallback = false
            )
        )
        adminDao.upsertAdhanVoice(
            AdhanVoiceEntity(
                id = "adhan_fallback_tone",
                nameAr = "تنبيه الأذان الاحتياطي (مدمج)",
                nameEn = "Built-in Adhan Fallback Chime",
                filePath = "BUILTIN_TONE",
                format = "WAV",
                assignedPrayers = "FALLBACK",
                isDefault = false,
                isSystemFallback = true
            )
        )

        // Feature Toggles
        val defaultFeatures = listOf(
            FeatureToggleEntity("feature_ai", "مساعد نور الذكي", "NOOR Local AI", true),
            FeatureToggleEntity("feature_halaqat", "نظام الحلقات", "Halaqat System", true),
            FeatureToggleEntity("feature_library", "المكتبة التعليمية", "Educational Library", true),
            FeatureToggleEntity("feature_recording", "تقييم التلاوة الصوتي", "Voice Recitation Assessment", true),
            FeatureToggleEntity("feature_khatmah_fasting", "الختمة والصيام", "Khatmah & Fasting", true),
            FeatureToggleEntity("feature_promo_cards", "البطاقات الإعلانية", "Admin Promo Cards", true)
        )
        defaultFeatures.forEach { adminDao.upsertFeatureToggle(it) }

        // Seed Educational Library if empty
        val existingEntries = db.knowledgeDao().getApprovedEntries()
        if (existingEntries.isEmpty()) {
            db.libraryDao().upsertLesson(
                EduLessonEntity(
                    category = "TAJWEED",
                    titleAr = "أحكام النون الساكنة والتنوين",
                    titleEn = "Rules of Noon Sakinah & Tanween",
                    contentAr = "تنقسم أحكام النون الساكنة والتنوين إلى أربعة أقسام: الإظهار الحلقي عند حروف (ء هـ ع ح غ خ)، والإدغام في (يرملون) بقسميه بغنة وبغير غنة، والإقلاب عند الباء، والإخفاء الحقيقي عند باقي الحروف الخمسة عشر.",
                    contentEn = "Noon Sakinah and Tanween have four primary Tajweed rules: Idhar (clear pronunciation before throat letters), Idgham (merging into Ya-Ra-Meem-Lam-Waw-Noon), Iqlab (converting to Meem before Ba), and Ikhfa (concealment before the remaining 15 letters).",
                    referenceSource = "تحفة الأطفال للجمزوري / الميسر في علم التجويد",
                    isVerifiedReference = true
                )
            )
            db.libraryDao().upsertLesson(
                EduLessonEntity(
                    category = "MEMORIZATION",
                    titleAr = "منهجية التثبيت والمراجعة اليومية",
                    titleEn = "Structured Daily Revision Method",
                    contentAr = "أفضل طريقة لترسيخ الحفظ هي الجمع بين الورد الجديد والمراجعة القريبة (آخر خمسة أوجه) والمراجعة البعيدة (الربط التراكمي)، مع سماع القارئ المتقن قبل البدء لتصحيح التلاوة.",
                    contentEn = "Effective Quran retention pairs new memorization with near-term revision (last 5 pages) and cumulative long-term revision, preceded by listening to an authentic reciter.",
                    referenceSource = "إرشادات معلمي الحلقات القرآنية",
                    isVerifiedReference = false
                )
            )

            // Seed initial approved Knowledge entries for NOOR AI local RAG
            val starterKnowledge = listOf(
                KnowledgeEntryEntity(
                    title = "فضل سورة الفاتحة ومقاصدها",
                    category = "التفسير وعلوم القرآن",
                    question = "ما فضل سورة الفاتحة وما موضوعاتها الأساسية؟",
                    answer = "سورة الفاتحة هي أعظم سورة في القرآن الكريم، وتُسمى أم الكتاب والسبع المثاني. تشتمل على الثناء على الله تعالى، وإفراده بالعبادة والاستعانة، وطلب الهداية إلى الصراط المستقيم.",
                    keywords = "الفاتحة ام الكتاب السبع المثاني تفسير",
                    reference = "صحيح البخاري وتفسير ابن كثير",
                    sourceFileName = "Starter Verified Corpus",
                    approvalStatus = "APPROVED"
                ),
                KnowledgeEntryEntity(
                    title = "أحكام الإدغام في التجويد",
                    category = "التجويد",
                    question = "ما هي حروف الإدغام وأقسامه؟",
                    answer = "حروف الإدغام ستة مجموعة في كلمة (يرملون). وينقسم إلى: إدغام بغنة في حروف (ينمو)، وإدغام بغير غنة في حرفي (اللام والراء).",
                    keywords = "ادغام تجويد يرملون ينمو نون ساكنة",
                    reference = "متن تحفة الأطفال",
                    sourceFileName = "Starter Verified Corpus",
                    approvalStatus = "APPROVED"
                ),
                KnowledgeEntryEntity(
                    title = "خطة مراجعة القرآن الكريم",
                    category = "الحفظ والمراجعة",
                    question = "كيف أراجع حفظي بدون نسيان؟",
                    answer = "يُوصى بتخصيص ثلث الوقت للحفظ الجديد وثلثين للمراجعة، وتكرار المقطع عشر مرات نظراً ثم غيباً، واستخدام ميزة التسميع الصوتي في تطبيق نور لمقارنة التلاوة.",
                    keywords = "حفظ مراجعة نسيان تسميع خطة",
                    reference = "منهج الحلقات القرآنية في نور",
                    sourceFileName = "Starter Verified Corpus",
                    approvalStatus = "APPROVED"
                )
            )
            starterKnowledge.forEach { entry ->
                val id = db.knowledgeDao().upsertEntry(entry)
                val combined = "${entry.title} ${entry.question} ${entry.answer}"
                db.knowledgeDao().insertChunks(
                    listOf(
                        KnowledgeChunkEntity(
                            entryId = id,
                            chunkIndex = 0,
                            title = entry.title,
                            category = entry.category,
                            content = entry.answer,
                            normalizedTokens = ArabicNlpNormalizer.normalizeAndTokenize(combined).joinToString(" "),
                            keywords = entry.keywords,
                            reference = entry.reference,
                            isApproved = true
                        )
                    )
                )
            }

            // Seed initial Promo Card & Welcome Announcement
            adminDao.upsertPromoCard(
                PromoCardEntity(
                    title = "مشروع الختمة والمراجعة اليومية",
                    description = "تابع وردك اليومي وحفظك داخل تطبيق نور دون الحاجة للاتصال بالإنترنت.",
                    internalRoute = "worship",
                    displayOrder = 1,
                    isActive = true
                )
            )
            adminDao.upsertAnnouncement(
                AnnouncementEntity(
                    title = "أهلاً بكم في تطبيق نور",
                    body = "تم تفعيل نظام المصحف التفاعلي، التقييم الصوتي، ومساعد نور المعرفي المحلي.",
                    targetAudience = "ALL",
                    notificationType = "GENERAL"
                )
            )
        }
    }
}
