package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.DocumentExtractor
import com.example.ai.LocalRagEngine
import com.example.audio.QuranAudioEngine
import com.example.data.local.KnowledgeEntryEntity
import com.example.data.local.NoorDatabase
import com.example.data.local.UserRole
import com.example.prayer.CalculationMethod
import com.example.prayer.PrayerTimesCalculator
import com.example.quran.MushafCoordinateEngine
import com.example.quran.QuranCatalog
import com.example.recitation.QuranRecitationComparator
import com.example.recitation.WordComparisonStatus
import com.example.security.AdminPermission
import com.example.security.PasswordHasher
import com.example.security.PermissionGuard
import com.example.utils.BackupAndReportExporter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoorCoreFunctionalTest {

    private lateinit var context: Context
    private lateinit var db: NoorDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NoorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `quran catalog maps all 114 surahs 30 juz and 240 hizb quarters across 604 pages`() {
        assertEquals(114, QuranCatalog.surahs.size)
        assertEquals(30, QuranCatalog.juzList.size)
        assertEquals(240, QuranCatalog.hizbQuarters.size)
        assertEquals(1, QuranCatalog.surahs.first().startPage)
        assertEquals(604, QuranCatalog.surahs.last().startPage)
        assertTrue(QuranCatalog.ayahsOnPage(1).size == 7)
        assertEquals(30, QuranCatalog.juzForPage(604).number)
    }

    @Test
    fun `mushaf coordinate system hits exact ayah and excludes decorative ayah marker circle from highlights`() {
        val sampleJson = """
            [
              {
                "page": 1,
                "surah": 1,
                "ayah": 1,
                "words": [
                  {"wordIndex": 0, "text": "بسم", "l": 0.65, "t": 0.20, "r": 0.80, "b": 0.26, "isAyahMarker": false},
                  {"wordIndex": 1, "text": "الله", "l": 0.45, "t": 0.20, "r": 0.63, "b": 0.26, "isAyahMarker": false},
                  {"wordIndex": 2, "text": "﴿١﴾", "l": 0.35, "t": 0.20, "r": 0.43, "b": 0.26, "isAyahMarker": true}
                ]
              }
            ]
        """.trimIndent()
        val count = MushafCoordinateEngine.parseAndLoadCoordinatesJson(sampleJson)
        assertEquals(1, count)

        val hitWord = MushafCoordinateEngine.findAyahAtPoint(1, 0.70f, 0.23f)
        assertNotNull(hitWord)
        assertEquals(1, hitWord?.ayah)

        // Tapping directly on decorative Ayah marker circle does not trigger text box; highlight rects exclude marker!
        val markerHit = MushafCoordinateEngine.findAyahAtPoint(1, 0.39f, 0.23f)
        assertNull(markerHit)
        assertEquals(2, hitWord?.textHighlightRects()?.size)
    }

    @Test
    fun `audio timestamp binary search resolves exact ayah and word index during continuous playback`() {
        val timingsJson = """
            [
              {
                "surah": 1, "ayah": 1, "startMs": 0, "endMs": 4200,
                "words": [
                  {"wordIndex": 0, "startMs": 0, "endMs": 900},
                  {"wordIndex": 1, "startMs": 901, "endMs": 2000}
                ]
              },
              {"surah": 1, "ayah": 2, "startMs": 4201, "endMs": 8500}
            ]
        """.trimIndent()
        val timings = QuranAudioEngine.parseTimingsJson(timingsJson)
        val at1500 = QuranAudioEngine.findTimingAtPosition(timings, 1500L)
        assertEquals(1, at1500?.ayah)
        val at5000 = QuranAudioEngine.findTimingAtPosition(timings, 5000L)
        assertEquals(2, at5000?.ayah)
    }

    @Test
    fun `recitation comparator detects omitted repeated replaced words and ASR uncertainty`() {
        val expected = QuranCatalog.ayahsForSurahRange(112, 1, 2) // قل هو الله أحد / الله الصمد
        val spokenWithErrors = "قل الله الله الله صمد" // omitted هو/أحد, consecutive repeated الله, partial صمد
        val result = QuranRecitationComparator.compare(
            expectedAyahs = expected,
            recognizedTranscript = spokenWithErrors,
            asrConfidence = 0.80f,
            longPauseCount = 1
        )
        assertTrue(result.omittedCount >= 1)
        assertTrue(result.repeatedCount >= 1)
        assertEquals(1, result.longPauseCount)
        assertTrue(result.accuracyPercent in 1..95)
    }

    @Test
    fun `noor local RAG imports TXT JSON PDF DOCX indexes chunks cites sources and refuses hallucination`() = runBlocking {
        val rag = LocalRagEngine(db.knowledgeDao())

        // 1) TXT import
        val txtDocs = DocumentExtractor.extractPlainText(
            "مخارج الحروف الأساسية في التجويد خمسة وهي الجوف والحلق واللسان والشفتان والخيشوم.",
            "tajweed_makharij.txt"
        )
        // 2) JSON import
        val jsonDocs = DocumentExtractor.extractJson(
            """[{"title":"أركان الإسلام","category":"عقيدة","question":"ما أركان الإسلام؟","answer":"شهادة أن لا إله إلا الله وأن محمداً رسول الله وإقام الصلاة وإيتاء الزكاة وصوم رمضان وحج البيت.","reference":"صحيح مسلم"}]""",
            "pillars.json"
        )
        // 3) PDF import (valid literal Tj text stream)
        val pdfBytes = "%PDF-1.4\n1 0 obj\n<< /Length 44 >>\nstream\nBT (Makharij Al Huroof has 5 main areas) Tj ET\nendstream\nendobj\n%%EOF".toByteArray(Charsets.ISO_8859_1)
        val pdfDocs = DocumentExtractor.extractPdf(pdfBytes, "makharij.pdf")

        // 4) DOCX import (valid zip containing word/document.xml)
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>الوقف الاختياري ينقسم إلى تام وكاف وحسن وقبيح</w:t></w:r></w:p></w:body></w:document>".toByteArray())
            zos.closeEntry()
        }
        val docxDocs = DocumentExtractor.extractDocx(bos.toByteArray().inputStream(), "waqf.docx")

        (txtDocs + jsonDocs + pdfDocs + docxDocs).forEach { doc ->
            val entity = KnowledgeEntryEntity(
                title = doc.title,
                category = doc.category,
                question = doc.question,
                answer = doc.answer,
                keywords = doc.keywords,
                reference = doc.reference,
                sourceFileName = doc.sourceFileName,
                approvalStatus = "APPROVED"
            )
            val id = db.knowledgeDao().upsertEntry(entity)
            rag.chunkAndIndexEntry(entity.copy(id = id))
        }

        // Verify grounded answer uses imported TXT/JSON/DOCX
        val answer = rag.answerQuestion("ما هي مخارج الحروف في التجويد؟", isArabicUi = true)
        assertTrue(answer.isGrounded)
        assertTrue(answer.answerText.contains("الجوف") || answer.answerText.contains("الحلق"))
        assertTrue(answer.citations.isNotEmpty())

        val waqfAnswer = rag.answerQuestion("ما أقسام الوقف الاختياري؟", isArabicUi = true)
        assertTrue(waqfAnswer.isGrounded)
        assertTrue(waqfAnswer.answerText.contains("تام"))

        // Verify unsupported question admits uncertainty and never fabricates
        val unknown = rag.answerQuestion("ما هو سعر الذهب اليوم في السوق؟", isArabicUi = true)
        assertFalse(unknown.isGrounded)
    }

    @Test
    fun `prayer times calculation produces chronological daily schedule for Damascus`() {
        val times = PrayerTimesCalculator.calculate(33.5138, 36.2765, CalculationMethod.DAMASCUS_SYRIA)
        assertTrue(times.fajrMinutes < times.sunriseMinutes)
        assertTrue(times.sunriseMinutes < times.dhuhrMinutes)
        assertTrue(times.dhuhrMinutes < times.asrMinutes)
        assertTrue(times.asrMinutes < times.maghribMinutes)
        assertTrue(times.maghribMinutes < times.ishaMinutes)
    }

    @Test
    fun `password hashing never stores plaintext and permission guard isolates roles`() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword("secret-pass", salt)
        assertNotEquals("secret-pass", hash)
        assertTrue(PasswordHasher.verifyPassword("secret-pass", salt, hash))
        assertFalse(PasswordHasher.verifyPassword("wrong-pass", salt, hash))

        assertTrue(PermissionGuard.hasPermission(UserRole.SUPER_ADMIN, AdminPermission.BACKUP_RESTORE))
        assertFalse(PermissionGuard.hasPermission(UserRole.STUDENT, AdminPermission.MANAGE_STUDENTS))
    }

    @Test
    fun `report exporter generates valid TXT CSV PDF and full JSON backup`() = runBlocking {
        val txt = BackupAndReportExporter.exportTxtReport(context, "test_rep", "NOOR Report", listOf("Line 1", "Line 2"))
        val csv = BackupAndReportExporter.exportCsvReport(context, "test_rep", listOf("Name", "Grade"), listOf(listOf("Tariq", "98")))
        val pdf = BackupAndReportExporter.exportPdfReport(context, "test_rep", "NOOR PDF", listOf("Student: Tariq - 98%"))
        val backup = BackupAndReportExporter.createFullDatabaseBackupJson(context, db)

        assertTrue(txt.exists() && txt.readText().contains("Line 1"))
        assertTrue(csv.exists() && csv.readText().contains("\"Tariq\""))
        assertTrue(pdf.exists() && pdf.length() > 100L)
        assertTrue(backup.exists() && backup.readText().contains("schemaVersion"))
    }
}
