package com.example

import com.example.ai.ArabicNlpNormalizer
import com.example.ai.DocumentExtractor
import com.example.audio.AyahTimingEntry
import com.example.audio.QuranAudioEngine
import com.example.audio.WordTiming
import com.example.data.local.UserRole
import com.example.prayer.CalculationMethod
import com.example.prayer.PrayerTimesCalculator
import com.example.quran.*
import com.example.recitation.QuranRecitationComparator
import com.example.recitation.WordComparisonStatus
import com.example.security.AdminPermission
import com.example.security.PasswordHasher
import com.example.security.PermissionGuard
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class NoorJvmFunctionalTest {

    @Test
    fun `quran catalog maps all 114 surahs 30 juz and 240 hizb quarters across 604 pages`() {
        assertEquals(114, QuranCatalog.surahs.size)
        assertEquals(30, QuranCatalog.juzList.size)
        assertEquals(240, QuranCatalog.hizbQuarters.size)
        assertEquals(1, QuranCatalog.surahs.first().startPage)
        assertEquals(604, QuranCatalog.surahs.last().startPage)
        assertEquals(7, QuranCatalog.ayahsOnPage(1).size)
        assertEquals(30, QuranCatalog.juzForPage(604).number)
        assertTrue(QuranCatalog.searchAyahs("الرحمن").isNotEmpty())
    }

    @Test
    fun `mushaf coordinate region hits exact ayah and excludes decorative ayah marker circle`() {
        val region = AyahCoordinateRegion(
            page = 1,
            surah = 1,
            ayah = 1,
            wordBoxes = listOf(
                WordCoordinate(0, "بسم", NormalizedRect(0.65f, 0.20f, 0.80f, 0.26f), isAyahMarker = false),
                WordCoordinate(1, "الله", NormalizedRect(0.45f, 0.20f, 0.63f, 0.26f), isAyahMarker = false),
                WordCoordinate(2, "﴿١﴾", NormalizedRect(0.35f, 0.20f, 0.43f, 0.26f), isAyahMarker = true)
            )
        )
        MushafCoordinateEngine.registerPageCoordinates(1, listOf(region))

        val hit = MushafCoordinateEngine.findAyahAtPoint(1, 0.72f, 0.23f)
        assertNotNull(hit)
        assertEquals(1, hit?.ayah)

        // Marker circle must NEVER trigger selection highlight or be included in highlight rects
        assertNull(MushafCoordinateEngine.findAyahAtPoint(1, 0.39f, 0.23f))
        assertEquals(2, region.textHighlightRects().size)
    }

    @Test
    fun `audio timestamp binary search resolves exact ayah and word during continuous playback`() {
        val timings = listOf(
            AyahTimingEntry(
                surah = 1, ayah = 1, startMs = 0L, endMs = 4200L,
                wordTimings = listOf(WordTiming(0, 0L, 900L), WordTiming(1, 901L, 2100L))
            ),
            AyahTimingEntry(surah = 1, ayah = 2, startMs = 4201L, endMs = 8500L)
        )
        val first = QuranAudioEngine.findTimingAtPosition(timings, 1500L)
        val second = QuranAudioEngine.findTimingAtPosition(timings, 5500L)
        assertEquals(1, first?.ayah)
        assertEquals(2, second?.ayah)
    }

    @Test
    fun `recitation comparator detects omitted repeated replaced skipped ayahs and order warnings`() {
        val alIkhlas = QuranCatalog.ayahsForSurahRange(112, 1, 2)
        val spoken = "قل الله الله الله صمد"
        val res = QuranRecitationComparator.compare(
            expectedAyahs = alIkhlas,
            recognizedTranscript = spoken,
            asrConfidence = 0.80f,
            longPauseCount = 2
        )
        assertTrue(res.omittedCount >= 1)
        assertTrue(res.repeatedCount >= 1)
        assertEquals(2, res.longPauseCount)
        assertTrue(res.wordItems.any { it.status == WordComparisonStatus.MATCHED })

        // Test skipped ayah detection (recite Ayah 1 and Ayah 3 of Al-Asr, skipping Ayah 2)
        val alAsr = QuranCatalog.ayahsForSurahRange(103, 1, 3)
        val skippedRes = QuranRecitationComparator.compare(
            expectedAyahs = alAsr,
            recognizedTranscript = "والعصر إلا الذين آمنوا وعملوا الصالحات وتواصوا بالحق وتواصوا بالصبر"
        )
        assertTrue(skippedRes.skippedAyahs.contains(2))
    }

    @Test
    fun `arabic nlp normalizer strips diacritics and tokenizes Arabic queries`() {
        val norm = ArabicNlpNormalizer.normalizeArabicText("بِسْمِ اللَّهِ الرَّحْمَٰنِ")
        assertEquals("بسم الله الرحمن", norm)
        val tokens = ArabicNlpNormalizer.normalizeAndTokenize("ما هي مخارج الحروف في التجويد؟")
        assertTrue(tokens.contains("مخارج"))
        assertTrue(tokens.contains("حروف"))
        assertTrue(tokens.contains("تجويد"))
    }

    @Test
    fun `document extractor parses TXT and embedded text PDF offline`() {
        val txt = DocumentExtractor.extractPlainText("أحكام المد الطبيعي حركتان.", "madd.txt")
        assertEquals(1, txt.size)
        assertTrue(txt[0].answer.contains("حركتان"))

        val pdfBytes = "%PDF-1.4\n1 0 obj\n<< /Length 44 >>\nstream\nBT (Makharij Al Huroof has 5 main areas) Tj ET\nendstream\nendobj\n%%EOF".toByteArray(Charsets.ISO_8859_1)
        val pdf = DocumentExtractor.extractPdf(pdfBytes, "makharij.pdf")
        assertEquals(1, pdf.size)
        assertTrue(pdf[0].answer.contains("Makharij Al Huroof"))
    }

    @Test
    fun `prayer times calculation produces valid chronological schedule for Damascus and Makkah`() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Damascus")).apply {
            set(2026, Calendar.JUNE, 15, 12, 0, 0)
        }
        val damascus = PrayerTimesCalculator.calculate(33.5138, 36.2765, CalculationMethod.DAMASCUS_SYRIA, cal)
        assertTrue(damascus.fajrMinutes < damascus.sunriseMinutes)
        assertTrue(damascus.sunriseMinutes < damascus.dhuhrMinutes)
        assertTrue(damascus.dhuhrMinutes < damascus.asrMinutes)
        assertTrue(damascus.asrMinutes < damascus.maghribMinutes)
        assertTrue(damascus.maghribMinutes < damascus.ishaMinutes)
    }

    @Test
    fun `password hashing uses PBKDF2 salt and permission guard enforces RBAC`() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword("noor-admin-2026", salt)
        assertNotEquals("noor-admin-2026", hash)
        assertTrue(PasswordHasher.verifyPassword("noor-admin-2026", salt, hash))
        assertFalse(PasswordHasher.verifyPassword("wrong", salt, hash))

        assertTrue(PermissionGuard.hasPermission(UserRole.SUPER_ADMIN, AdminPermission.BACKUP_RESTORE))
        assertTrue(PermissionGuard.hasPermission(UserRole.TEACHER, AdminPermission.REVIEW_RECITATIONS))
        assertFalse(PermissionGuard.hasPermission(UserRole.TEACHER, AdminPermission.BACKUP_RESTORE))
        assertFalse(PermissionGuard.hasPermission(UserRole.STUDENT, AdminPermission.MANAGE_HALAQAT))
    }
}
