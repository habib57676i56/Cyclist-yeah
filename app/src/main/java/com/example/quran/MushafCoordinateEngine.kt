package com.example.quran

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class NormalizedRect(
    val left: Float,   // 0f..1f relative to page image width
    val top: Float,    // 0f..1f relative to page image height
    val right: Float,  // 0f..1f relative to page image width
    val bottom: Float  // 0f..1f relative to page image height
) {
    fun contains(normX: Float, normY: Float): Boolean =
        normX in left..right && normY in top..bottom
}

data class WordCoordinate(
    val wordIndex: Int,
    val text: String,
    val rect: NormalizedRect,
    val isAyahMarker: Boolean = false
)

data class AyahCoordinateRegion(
    val page: Int,
    val surah: Int,
    val ayah: Int,
    val wordBoxes: List<WordCoordinate>,
    val markerExclusionRect: NormalizedRect? = null
) {
    /**
     * Returns only highlightable text word rectangles, strictly excluding the decorative Ayah
     * number circle so original Mushaf markers are never covered or distorted.
     */
    fun textHighlightRects(): List<NormalizedRect> =
        wordBoxes.filter { !it.isAyahMarker }.map { it.rect }

    fun hitTest(normX: Float, normY: Float): Boolean =
        wordBoxes.any { !it.isAyahMarker && it.rect.contains(normX, normY) }
}

/**
 * Manages verified Madinah Mushaf page images and word-level coordinate mappings.
 * Supports imported 604-page Mushaf packs in filesDir/mushaf_pack/ while keeping
 * built-in Uthmani page rendering functional out of the box.
 */
object MushafCoordinateEngine {

    private val pageCoordinates = mutableMapOf<Int, List<AyahCoordinateRegion>>()

    fun mushafDir(context: Context): File = File(context.filesDir, "mushaf_pack")

    fun pageImageFile(context: Context, page: Int): File? {
        val dir = File(mushafDir(context), "pages")
        if (!dir.exists()) return null
        val padded = page.toString().padStart(3, '0')
        val candidates = listOf(
            "page_$padded.png", "page_$padded.webp", "page_$padded.jpg",
            "$padded.png", "$padded.webp", "$padded.jpg",
            "$page.png", "$page.webp", "$page.jpg"
        )
        return candidates.map { File(dir, it) }.firstOrNull { it.exists() && it.length() > 0 }
    }

    fun installedImagePageCount(context: Context): Int {
        val dir = File(mushafDir(context), "pages")
        if (!dir.exists()) return 0
        return dir.listFiles()?.count {
            it.isFile && (it.extension.lowercase() in setOf("png", "webp", "jpg", "jpeg"))
        } ?: 0
    }

    fun hasCoordinatesForPage(page: Int): Boolean =
        !pageCoordinates[page].isNullOrEmpty()

    fun coordinatesForPage(page: Int): List<AyahCoordinateRegion> =
        pageCoordinates[page].orEmpty()

    fun findAyahAtPoint(page: Int, normX: Float, normY: Float): AyahCoordinateRegion? {
        val regions = pageCoordinates[page] ?: return null
        return regions.firstOrNull { it.hitTest(normX, normY) }
    }

    fun registerPageCoordinates(page: Int, regions: List<AyahCoordinateRegion>) {
        pageCoordinates[page] = regions
    }

    fun loadSavedPackFromDisk(context: Context): Int {
        val dir = mushafDir(context)
        val coordFile = File(dir, "coordinates.json")
        if (coordFile.exists()) {
            runCatching {
                parseAndLoadCoordinatesJson(coordFile.readText())
            }
        }
        val textFile = File(dir, "quran_text.json")
        if (textFile.exists()) {
            runCatching {
                val imported = parseQuranTextJson(textFile.readText())
                if (imported.isNotEmpty()) QuranCatalog.mergeImportedAyahs(imported)
            }
        }
        return installedImagePageCount(context)
    }

    fun parseAndLoadCoordinatesJson(jsonString: String): Int {
        val root = JSONArray(jsonString)
        var loadedCount = 0
        val grouped = mutableMapOf<Int, MutableList<AyahCoordinateRegion>>()
        for (i in 0 until root.length()) {
            val obj = root.getJSONObject(i)
            val page = obj.getInt("page")
            val surah = obj.getInt("surah")
            val ayah = obj.getInt("ayah")
            val wordsArr = obj.getJSONArray("words")
            val words = mutableListOf<WordCoordinate>()
            var markerRect: NormalizedRect? = null
            for (w in 0 until wordsArr.length()) {
                val wo = wordsArr.getJSONObject(w)
                val rect = NormalizedRect(
                    left = wo.getDouble("l").toFloat(),
                    top = wo.getDouble("t").toFloat(),
                    right = wo.getDouble("r").toFloat(),
                    bottom = wo.getDouble("b").toFloat()
                )
                val isMarker = wo.optBoolean("isAyahMarker", false)
                if (isMarker) markerRect = rect
                words.add(
                    WordCoordinate(
                        wordIndex = wo.optInt("wordIndex", w),
                        text = wo.optString("text", ""),
                        rect = rect,
                        isAyahMarker = isMarker
                    )
                )
            }
            grouped.getOrPut(page) { mutableListOf() }
                .add(AyahCoordinateRegion(page, surah, ayah, words, markerRect))
            loadedCount++
        }
        grouped.forEach { (page, list) -> pageCoordinates[page] = list }
        return loadedCount
    }

    fun parseQuranTextJson(jsonString: String): List<AyahRecord> {
        val arr = JSONArray(jsonString)
        val result = mutableListOf<AyahRecord>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val lingObj: JSONObject? = obj.optJSONObject("linguistics")
            val linguistics = lingObj?.let {
                LinguisticAnalysis(
                    tafsir = it.optString("tafsir", ""),
                    tafsirRef = it.optString("tafsirRef", "Imported Reference"),
                    irab = it.optString("irab", ""),
                    sarf = it.optString("sarf", "")
                )
            }
            result.add(
                AyahRecord(
                    surah = obj.getInt("surah"),
                    ayah = obj.getInt("ayah"),
                    page = obj.getInt("page"),
                    textUthmani = obj.getString("textUthmani"),
                    linguistics = linguistics
                )
            )
        }
        return result
    }
}
