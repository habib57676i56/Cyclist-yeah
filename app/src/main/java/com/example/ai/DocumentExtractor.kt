package com.example.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

data class ImportedKnowledgeDoc(
    val title: String,
    val category: String,
    val question: String,
    val answer: String,
    val keywords: String,
    val reference: String,
    val sourceFileName: String
)

object DocumentExtractor {

    fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx) ?: "imported_document"
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "imported_document"
    }

    fun extractFromUri(context: Context, uri: Uri): Result<List<ImportedKnowledgeDoc>> = runCatching {
        val name = resolveDisplayName(context, uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read selected file.")
        when (ext) {
            "txt", "md", "csv" -> extractPlainText(bytes.toString(Charsets.UTF_8), name)
            "json" -> extractJson(bytes.toString(Charsets.UTF_8), name)
            "docx" -> extractDocx(bytes.inputStream(), name)
            "pdf" -> extractPdf(bytes, name)
            else -> {
                // Attempt text or JSON fallback
                val text = bytes.toString(Charsets.UTF_8)
                if (text.trimStart().startsWith("[") || text.trimStart().startsWith("{")) {
                    extractJson(text, name)
                } else {
                    extractPlainText(text, name)
                }
            }
        }
    }

    fun extractPlainText(text: String, fileName: String): List<ImportedKnowledgeDoc> {
        val clean = text.trim()
        if (clean.isBlank()) error("TXT file is empty.")
        return listOf(
            ImportedKnowledgeDoc(
                title = fileName.substringBeforeLast('.'),
                category = "مستند مستورد",
                question = "",
                answer = clean,
                keywords = "",
                reference = "Imported TXT: $fileName",
                sourceFileName = fileName
            )
        )
    }

    fun extractJson(jsonText: String, fileName: String): List<ImportedKnowledgeDoc> {
        val trimmed = jsonText.trim()
        val list = mutableListOf<ImportedKnowledgeDoc>()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                parseJsonObj(arr.getJSONObject(i), fileName)?.let { list.add(it) }
            }
        } else {
            val obj = JSONObject(trimmed)
            val arr = obj.optJSONArray("entries") ?: obj.optJSONArray("items") ?: obj.optJSONArray("data")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    parseJsonObj(arr.getJSONObject(i), fileName)?.let { list.add(it) }
                }
            } else {
                parseJsonObj(obj, fileName)?.let { list.add(it) }
            }
        }
        if (list.isEmpty()) error("No valid entries found in JSON file.")
        return list
    }

    private fun parseJsonObj(obj: JSONObject, fileName: String): ImportedKnowledgeDoc? {
        val answer = obj.optString("answer").ifBlank { obj.optString("content").ifBlank { obj.optString("text") } }
        if (answer.isBlank()) return null
        return ImportedKnowledgeDoc(
            title = obj.optString("title").ifBlank { obj.optString("question").take(60) }.ifBlank { fileName },
            category = obj.optString("category").ifBlank { "JSON مستورد" },
            question = obj.optString("question", ""),
            answer = answer,
            keywords = obj.optString("keywords", ""),
            reference = obj.optString("reference").ifBlank { "Imported JSON: $fileName" },
            sourceFileName = fileName
        )
    }

    fun extractDocx(inputStream: InputStream, fileName: String): List<ImportedKnowledgeDoc> {
        val sb = StringBuilder()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance().apply {
                        isNamespaceAware = false
                    }
                    val parser = factory.newPullParser()
                    parser.setInput(zis, "UTF-8")
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        val tag = parser.name
                        if (event == XmlPullParser.START_TAG && (tag == "t" || tag == "w:t")) {
                            sb.append(parser.nextText())
                        } else if (event == XmlPullParser.END_TAG && (tag == "p" || tag == "w:p")) {
                            sb.append('\n')
                        }
                        event = parser.next()
                    }
                    break
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        val text = sb.toString().trim()
        if (text.isBlank()) error("No readable text found inside DOCX file.")
        return listOf(
            ImportedKnowledgeDoc(
                title = fileName.substringBeforeLast('.'),
                category = "مستند DOCX",
                question = "",
                answer = text,
                keywords = "",
                reference = "Imported DOCX: $fileName",
                sourceFileName = fileName
            )
        )
    }

    /**
     * Lightweight pure-JVM PDF text extractor supporting uncompressed & FlateDecode text streams.
     * Reports clearly when a PDF contains scanned images without selectable text.
     */
    fun extractPdf(bytes: ByteArray, fileName: String): List<ImportedKnowledgeDoc> {
        val raw = bytes.toString(Charsets.ISO_8859_1)
        val extracted = StringBuilder()

        // 1) Try stream blocks (both uncompressed and FlateDecode)
        val streamRegex = Regex("stream\\r?\\n([\\s\\S]*?)\\r?\\nendstream")
        for (match in streamRegex.findAll(raw)) {
            val startIdx = match.groups[1]?.range?.first ?: continue
            val endIdx = match.groups[1]?.range?.last?.plus(1) ?: continue
            val slice = bytes.copyOfRange(startIdx, endIdx)
            val contentStr = inflateIfZlib(slice) ?: slice.toString(Charsets.ISO_8859_1)
            extractPdfOperators(contentStr, extracted)
        }

        // 2) Fallback: scan entire raw string for literal Tj blocks if stream header format varied
        if (extracted.isBlank()) {
            extractPdfOperators(raw, extracted)
        }

        val finalText = extracted.toString().replace(Regex("\\s+"), " ").trim()
        if (finalText.isBlank()) {
            error("PDF contains no selectable embedded text (it may be a scanned image PDF or encrypted). Convert to TXT/DOCX or OCR first.")
        }
        return listOf(
            ImportedKnowledgeDoc(
                title = fileName.substringBeforeLast('.'),
                category = "مستند PDF",
                question = "",
                answer = finalText,
                keywords = "",
                reference = "Imported PDF: $fileName",
                sourceFileName = fileName
            )
        )
    }

    private fun inflateIfZlib(data: ByteArray): String? {
        return runCatching {
            val inflater = Inflater()
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) break
                out.write(buf, 0, n)
            }
            inflater.end()
            val result = out.toByteArray()
            if (result.isEmpty()) null else result.toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun extractPdfOperators(streamText: String, out: StringBuilder) {
        val tjRegex = Regex("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)\\s*Tj")
        for (m in tjRegex.findAll(streamText)) {
            val s = unescapePdfLiteral(m.groupValues[1])
            if (s.isNotBlank()) out.append(s).append(' ')
        }
        val tjArrayRegex = Regex("\\[(.*?)\\]\\s*TJ", RegexOption.DOT_MATCHES_ALL)
        val innerLiteral = Regex("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)")
        for (arrMatch in tjArrayRegex.findAll(streamText)) {
            for (lit in innerLiteral.findAll(arrMatch.groupValues[1])) {
                val s = unescapePdfLiteral(lit.groupValues[1])
                if (s.isNotBlank()) out.append(s)
            }
            out.append(' ')
        }
    }

    private fun unescapePdfLiteral(s: String): String =
        s.replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", " ")
}
