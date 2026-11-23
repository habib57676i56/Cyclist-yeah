package com.example.utils

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.data.local.NoorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object BackupAndReportExporter {

    fun exportsDir(context: Context): File = File(context.filesDir, "exports").apply { mkdirs() }
    fun backupsDir(context: Context): File = File(context.filesDir, "backups").apply { mkdirs() }

    suspend fun exportTxtReport(context: Context, fileName: String, title: String, lines: List<String>): File =
        withContext(Dispatchers.IO) {
            val file = File(exportsDir(context), "$fileName.txt")
            file.writeText(buildString {
                appendLine(title)
                appendLine("=".repeat(40))
                lines.forEach { appendLine(it) }
            })
            file
        }

    suspend fun exportCsvReport(context: Context, fileName: String, headers: List<String>, rows: List<List<String>>): File =
        withContext(Dispatchers.IO) {
            val file = File(exportsDir(context), "$fileName.csv")
            file.writeText(buildString {
                appendLine(headers.joinToString(",") { escapeCsv(it) })
                rows.forEach { row -> appendLine(row.joinToString(",") { escapeCsv(it) }) }
            })
            file
        }

    suspend fun exportPdfReport(context: Context, fileName: String, title: String, lines: List<String>): File =
        withContext(Dispatchers.IO) {
            val file = File(exportsDir(context), "$fileName.pdf")
            val nativeOk = runCatching {
                val doc = PdfDocument()
                val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true }
                val bodyPaint = Paint().apply { textSize = 11.5f }

                var pageNum = 1
                var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
                var canvas = page.canvas
                var y = 48f
                canvas.drawText(title, 36f, y, titlePaint)
                y += 28f

                for (line in lines) {
                    if (y > 790f) {
                        doc.finishPage(page)
                        pageNum++
                        page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
                        canvas = page.canvas
                        y = 48f
                    }
                    canvas.drawText(line.take(92), 36f, y, bodyPaint)
                    y += 18f
                }
                doc.finishPage(page)
                FileOutputStream(file).use { doc.writeTo(it) }
                doc.close()
                true
            }.getOrDefault(false)

            if (!nativeOk) {
                writeMinimalPdf14(file, title, lines)
            }
            file
        }

    private fun writeMinimalPdf14(file: File, title: String, lines: List<String>) {
        fun esc(s: String): String = s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        val stream = buildString {
            appendLine("BT")
            appendLine("/F1 14 Tf")
            appendLine("40 780 Td")
            appendLine("(${esc(title)}) Tj")
            appendLine("/F1 10 Tf")
            lines.take(40).forEach { line ->
                appendLine("0 -16 Td")
                appendLine("(${esc(line.take(90))}) Tj")
            }
            appendLine("ET")
        }
        val objs = listOf(
            "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n",
            "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n",
            "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n",
            "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n",
            "5 0 obj << /Length ${stream.toByteArray(Charsets.ISO_8859_1).size} >>\nstream\n${stream}endstream\nendobj\n"
        )
        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        for (o in objs) {
            offsets.add(sb.length)
            sb.append(o)
        }
        val xrefPos = sb.length
        sb.append("xref\n0 ${objs.size + 1}\n0000000000 65535 f \n")
        for (off in offsets) {
            sb.append("%010d 00000 n \n".format(off))
        }
        sb.append("trailer << /Size ${objs.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        file.writeBytes(sb.toString().toByteArray(Charsets.ISO_8859_1))
    }

    suspend fun createKnowledgeBackupJson(context: Context, db: NoorDatabase): File =
        withContext(Dispatchers.IO) {
            val entries = db.knowledgeDao().observeEntries().first()
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(JSONObject().apply {
                    put("title", e.title)
                    put("category", e.category)
                    put("question", e.question)
                    put("answer", e.answer)
                    put("keywords", e.keywords)
                    put("reference", e.reference)
                    put("sourceFileName", e.sourceFileName)
                    put("approvalStatus", e.approvalStatus)
                })
            }
            val file = File(backupsDir(context), "noor_ai_knowledge_backup_${System.currentTimeMillis()}.json")
            file.writeText(arr.toString(2))
            file
        }

    suspend fun createFullDatabaseBackupJson(context: Context, db: NoorDatabase): File =
        withContext(Dispatchers.IO) {
            val root = JSONObject()
            root.put("schemaVersion", 1)
            root.put("exportedAt", System.currentTimeMillis())

            val halaqatArr = JSONArray()
            db.halaqatDao().observeHalaqat().first().forEach { h ->
                halaqatArr.put(JSONObject().apply {
                    put("id", h.id)
                    put("name", h.name)
                    put("teacherName", h.teacherName)
                    put("scheduleText", h.scheduleText)
                    put("memorizationTarget", h.memorizationTarget)
                    put("revisionPlan", h.revisionPlan)
                })
            }
            root.put("halaqat", halaqatArr)

            val kbArr = JSONArray()
            db.knowledgeDao().observeEntries().first().forEach { e ->
                kbArr.put(JSONObject().apply {
                    put("title", e.title)
                    put("category", e.category)
                    put("question", e.question)
                    put("answer", e.answer)
                    put("keywords", e.keywords)
                    put("reference", e.reference)
                    put("sourceFileName", e.sourceFileName)
                    put("approvalStatus", e.approvalStatus)
                })
            }
            root.put("knowledge", kbArr)

            val lessonsArr = JSONArray()
            db.libraryDao().observeLessons().first().forEach { l ->
                lessonsArr.put(JSONObject().apply {
                    put("category", l.category)
                    put("titleAr", l.titleAr)
                    put("titleEn", l.titleEn)
                    put("contentAr", l.contentAr)
                    put("contentEn", l.contentEn)
                    put("referenceSource", l.referenceSource)
                    put("isVerifiedReference", l.isVerifiedReference)
                })
            }
            root.put("lessons", lessonsArr)

            val file = File(backupsDir(context), "noor_full_backup_${System.currentTimeMillis()}.json")
            file.writeText(root.toString(2))
            file
        }

    suspend fun restoreBackupFromUri(context: Context, db: NoorDatabase, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("Unable to open backup file")

                var restoredKb = 0
                var restoredLessons = 0
                val trimmed = text.trim()
                if (trimmed.startsWith("[")) {
                    val arr = JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.knowledgeDao().upsertEntry(
                            com.example.data.local.KnowledgeEntryEntity(
                                title = o.getString("title"),
                                category = o.optString("category", "Restored"),
                                question = o.optString("question", ""),
                                answer = o.getString("answer"),
                                keywords = o.optString("keywords", ""),
                                reference = o.optString("reference", "Backup"),
                                sourceFileName = o.optString("sourceFileName", "Restored Backup"),
                                approvalStatus = o.optString("approvalStatus", "APPROVED")
                            )
                        )
                        restoredKb++
                    }
                } else {
                    val root = JSONObject(trimmed)
                    val kb = root.optJSONArray("knowledge")
                    if (kb != null) {
                        for (i in 0 until kb.length()) {
                            val o = kb.getJSONObject(i)
                            db.knowledgeDao().upsertEntry(
                                com.example.data.local.KnowledgeEntryEntity(
                                    title = o.getString("title"),
                                    category = o.optString("category", "Restored"),
                                    question = o.optString("question", ""),
                                    answer = o.getString("answer"),
                                    keywords = o.optString("keywords", ""),
                                    reference = o.optString("reference", "Backup"),
                                    sourceFileName = o.optString("sourceFileName", "Restored Backup"),
                                    approvalStatus = o.optString("approvalStatus", "APPROVED")
                                )
                            )
                            restoredKb++
                        }
                    }
                    val lessons = root.optJSONArray("lessons")
                    if (lessons != null) {
                        for (i in 0 until lessons.length()) {
                            val o = lessons.getJSONObject(i)
                            db.libraryDao().upsertLesson(
                                com.example.data.local.EduLessonEntity(
                                    category = o.optString("category", "ARTICLE"),
                                    titleAr = o.getString("titleAr"),
                                    titleEn = o.optString("titleEn"),
                                    contentAr = o.getString("contentAr"),
                                    contentEn = o.optString("contentEn"),
                                    referenceSource = o.optString("referenceSource", "Backup"),
                                    isVerifiedReference = o.optBoolean("isVerifiedReference", true)
                                )
                            )
                            restoredLessons++
                        }
                    }
                }
                "Restored $restoredKb knowledge entries and $restoredLessons lessons."
            }
        }

    fun calculateLocalStorageBytes(context: Context): Long {
        fun dirSize(f: File): Long {
            if (!f.exists()) return 0L
            if (f.isFile) return f.length()
            return f.listFiles()?.sumOf { dirSize(it) } ?: 0L
        }
        return dirSize(context.filesDir) + dirSize(context.getDatabasePath("noor_offline.db"))
    }

    private fun escapeCsv(s: String): String {
        val escaped = s.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
