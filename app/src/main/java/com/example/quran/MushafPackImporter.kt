package com.example.quran

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class MushafImportSummary(
    val pagesImported: Int,
    val coordinateEntriesLoaded: Int,
    val ayahsLoaded: Int
)

object MushafPackImporter {

    suspend fun importZipPack(context: Context, zipUri: Uri): Result<MushafImportSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val baseDir = MushafCoordinateEngine.mushafDir(context).apply { mkdirs() }
                val pagesDir = File(baseDir, "pages").apply { mkdirs() }
                val canonicalBase = baseDir.canonicalPath
                var pagesCount = 0
                var coordsCount = 0
                var ayahsCount = 0

                context.contentResolver.openInputStream(zipUri)?.use { rawStream ->
                    ZipInputStream(rawStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val fileName = File(entry.name).name
                                val lower = fileName.lowercase()
                                val targetFile = when {
                                    lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ->
                                        File(pagesDir, fileName)
                                    lower == "coordinates.json" -> File(baseDir, "coordinates.json")
                                    lower == "quran_text.json" -> File(baseDir, "quran_text.json")
                                    else -> null
                                }
                                if (targetFile != null) {
                                    if (!targetFile.canonicalPath.startsWith(canonicalBase)) {
                                        throw SecurityException("Blocked unsafe ZIP path: ${entry.name}")
                                    }
                                    FileOutputStream(targetFile).use { out ->
                                        zis.copyTo(out)
                                    }
                                    if (targetFile.parentFile == pagesDir) pagesCount++
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                } ?: error("Unable to open Mushaf pack URI")

                val coordFile = File(baseDir, "coordinates.json")
                if (coordFile.exists()) {
                    coordsCount = MushafCoordinateEngine.parseAndLoadCoordinatesJson(coordFile.readText())
                }
                val textFile = File(baseDir, "quran_text.json")
                if (textFile.exists()) {
                    val ayahs = MushafCoordinateEngine.parseQuranTextJson(textFile.readText())
                    if (ayahs.isNotEmpty()) {
                        QuranCatalog.mergeImportedAyahs(ayahs)
                        ayahsCount = ayahs.size
                    }
                }

                MushafImportSummary(pagesCount, coordsCount, ayahsCount)
            }
        }
}
