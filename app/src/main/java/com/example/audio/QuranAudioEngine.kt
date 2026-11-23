package com.example.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import com.example.data.local.ReciterConfigEntity
import com.example.quran.QuranCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream

data class WordTiming(
    val wordIndex: Int,
    val startMs: Long,
    val endMs: Long
)

data class AyahTimingEntry(
    val surah: Int,
    val ayah: Int,
    val startMs: Long,
    val endMs: Long,
    val wordTimings: List<WordTiming> = emptyList()
)

data class QuranPlaybackState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isPreparing: Boolean = false,
    val reciterId: String = "qatami",
    val reciterName: String = "الشيخ ناصر القطامي",
    val currentSurah: Int = 1,
    val currentAyah: Int = 1,
    val currentWordIndex: Int? = null,
    val currentPage: Int = 1,
    val repeatAyahCount: Int = 1, // 1 = no repeat, 0 = infinite
    val repeatRangeStart: Int? = null,
    val repeatRangeEnd: Int? = null,
    val playbackSpeed: Float = 1.0f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val activeStreamCount: Int = 0, // Verified invariant: always 0 or 1
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * Single-stream Quran Audio Engine.
 * Strictly guarantees at most ONE active MediaPlayer voice stream at any time.
 * Supports:
 * 1) Continuous Surah MP3 + verified Ayah/word timestamp JSON (binary-search live sync)
 * 2) Separate per-Ayah MP3 files (local offline pack or cached stream) with continuous auto-advance
 */
object QuranAudioEngine {
    private val scope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    private val mutex = Mutex()
    private val sessionCounter = AtomicLong(0L)

    private var mediaPlayer: MediaPlayer? = null
    private var syncJob: Job? = null
    private var activeTimings: List<AyahTimingEntry> = emptyList()
    private var isContinuousSurahStream: Boolean = false
    private var currentAyahRepeatDone: Int = 0
    private var lastReciter: ReciterConfigEntity? = null
    private var allowOnline: Boolean = false

    private val _state = MutableStateFlow(QuranPlaybackState())
    val state: StateFlow<QuranPlaybackState> = _state.asStateFlow()

    fun recitersDir(context: Context): File = File(context.filesDir, "reciters")

    fun localAyahFile(context: Context, reciterId: String, surah: Int, ayah: Int): File {
        val code = "${surah.toString().padStart(3, '0')}${ayah.toString().padStart(3, '0')}.mp3"
        return File(File(recitersDir(context), reciterId), code)
    }

    fun localSurahFile(context: Context, reciterId: String, surah: Int): File {
        val code = "surah_${surah.toString().padStart(3, '0')}.mp3"
        return File(File(recitersDir(context), reciterId), code)
    }

    fun localSurahTimingsFile(context: Context, reciterId: String, surah: Int): File {
        val code = "timings_${surah.toString().padStart(3, '0')}.json"
        return File(File(recitersDir(context), reciterId), code)
    }

    fun playFromAyah(
        context: Context,
        reciter: ReciterConfigEntity,
        surah: Int,
        ayah: Int,
        speed: Float = _state.value.playbackSpeed,
        allowOnlineStreaming: Boolean = false
    ) {
        scope.launch {
            val token = sessionCounter.incrementAndGet()
            mutex.withLock {
                if (token != sessionCounter.get()) return@withLock
                lastReciter = reciter
                allowOnline = allowOnlineStreaming
                currentAyahRepeatDone = 0

                // Enforce strict single-audio rule: release any existing player first
                releaseInternal(context)
                AudioFocusCoordinator.acquireMode(context, ActiveAudioMode.LISTEN_AND_FOLLOW) {
                    stop(context)
                }

                val targetPage = QuranCatalog.pageForAyah(surah, ayah)
                _state.update {
                    it.copy(
                        isPreparing = true,
                        isPlaying = false,
                        isPaused = false,
                        reciterId = reciter.id,
                        reciterName = reciter.nameAr,
                        currentSurah = surah,
                        currentAyah = ayah,
                        currentWordIndex = null,
                        currentPage = targetPage,
                        playbackSpeed = speed,
                        errorMessage = null,
                        statusMessage = null
                    )
                }

                // 1) Check for Continuous Surah MP3 + Verified Timestamps JSON
                val surahAudio = localSurahFile(context, reciter.id, surah)
                val timingsFile = localSurahTimingsFile(context, reciter.id, surah)
                if (surahAudio.exists() && surahAudio.length() > 0 && timingsFile.exists()) {
                    val timings = runCatching { parseTimingsJson(timingsFile.readText()) }.getOrDefault(emptyList())
                    if (timings.isNotEmpty()) {
                        startContinuousSurahLocked(context, token, surahAudio, timings, ayah, speed)
                        return@withLock
                    }
                }

                // 2) Otherwise check local per-Ayah MP3, or cache from CDN if online allowed
                val localAyah = localAyahFile(context, reciter.id, surah, ayah)
                if (localAyah.exists() && localAyah.length() > 0) {
                    startAyahFileLocked(context, token, localAyah, surah, ayah, speed)
                    return@withLock
                }

                if (allowOnlineStreaming && reciter.remoteBaseUrl.isNotBlank() && isNetworkAvailable(context)) {
                    val downloaded = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        downloadAyahToCache(reciter.remoteBaseUrl, surah, ayah, localAyah)
                    }
                    if (token != sessionCounter.get()) return@withLock
                    if (downloaded && localAyah.exists()) {
                        startAyahFileLocked(context, token, localAyah, surah, ayah, speed)
                        return@withLock
                    }
                }

                _state.update {
                    it.copy(
                        isPreparing = false,
                        isPlaying = false,
                        activeStreamCount = 0,
                        errorMessage = "Audio file not cached offline for ${reciter.nameEn} (${surah}:${ayah}). Import offline reciter pack in Admin > Reciters or enable online caching."
                    )
                }
            }
        }
    }

    private fun startContinuousSurahLocked(
        context: Context,
        token: Long,
        surahFile: File,
        timings: List<AyahTimingEntry>,
        startAyah: Int,
        speed: Float
    ) {
        isContinuousSurahStream = true
        activeTimings = timings
        val targetEntry = timings.firstOrNull { it.ayah == startAyah } ?: timings.first()

        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(surahFile.absolutePath)
            setOnPreparedListener { mp ->
                if (token != sessionCounter.get()) {
                    mp.release()
                    return@setOnPreparedListener
                }
                applySpeed(mp, speed)
                if (targetEntry.startMs > 0) {
                    mp.seekTo(targetEntry.startMs.toInt())
                }
                mp.start()
                _state.update {
                    it.copy(
                        isPreparing = false,
                        isPlaying = true,
                        isPaused = false,
                        durationMs = mp.duration.toLong(),
                        activeStreamCount = 1,
                        statusMessage = "Continuous Surah sync active"
                    )
                }
                startNotificationService(context)
                startTimestampSyncLoop(context, token)
            }
            setOnCompletionListener {
                if (token == sessionCounter.get()) {
                    advanceToNextSurahOrStop(context)
                }
            }
            setOnErrorListener { _, what, extra ->
                if (token == sessionCounter.get()) {
                    _state.update {
                        it.copy(
                            isPreparing = false,
                            isPlaying = false,
                            activeStreamCount = 0,
                            errorMessage = "MediaPlayer error ($what/$extra)"
                        )
                    }
                    stop(context)
                }
                true
            }
        }
        mediaPlayer = player
        player.prepareAsync()
    }

    private fun startAyahFileLocked(
        context: Context,
        token: Long,
        file: File,
        surah: Int,
        ayah: Int,
        speed: Float
    ) {
        isContinuousSurahStream = false
        activeTimings = emptyList()

        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(file.absolutePath)
            setOnPreparedListener { mp ->
                if (token != sessionCounter.get()) {
                    mp.release()
                    return@setOnPreparedListener
                }
                applySpeed(mp, speed)
                mp.start()
                _state.update {
                    it.copy(
                        isPreparing = false,
                        isPlaying = true,
                        isPaused = false,
                        currentSurah = surah,
                        currentAyah = ayah,
                        currentPage = QuranCatalog.pageForAyah(surah, ayah),
                        durationMs = mp.duration.toLong(),
                        activeStreamCount = 1
                    )
                }
                startNotificationService(context)
                startPositionTicker(token)
            }
            setOnCompletionListener {
                if (token == sessionCounter.get()) {
                    handleAyahFileCompleted(context)
                }
            }
            setOnErrorListener { _, what, extra ->
                if (token == sessionCounter.get()) {
                    _state.update {
                        it.copy(
                            isPreparing = false,
                            isPlaying = false,
                            activeStreamCount = 0,
                            errorMessage = "Audio playback error ($what/$extra)"
                        )
                    }
                    stop(context)
                }
                true
            }
        }
        mediaPlayer = player
        player.prepareAsync()
    }

    private fun handleAyahFileCompleted(context: Context) {
        val st = _state.value
        val repeatTarget = st.repeatAyahCount
        if (repeatTarget == 0 || currentAyahRepeatDone + 1 < repeatTarget) {
            currentAyahRepeatDone++
            mediaPlayer?.let {
                it.seekTo(0)
                it.start()
            }
            return
        }
        currentAyahRepeatDone = 0
        val reciter = lastReciter ?: return stop(context)
        val rangeEnd = st.repeatRangeEnd
        val rangeStart = st.repeatRangeStart
        if (rangeStart != null && rangeEnd != null && st.currentAyah >= rangeEnd) {
            playFromAyah(context, reciter, st.currentSurah, rangeStart, st.playbackSpeed, allowOnline)
            return
        }
        val surahMeta = QuranCatalog.surahByNumber(st.currentSurah)
        if (surahMeta != null && st.currentAyah < surahMeta.ayahCount) {
            playFromAyah(context, reciter, st.currentSurah, st.currentAyah + 1, st.playbackSpeed, allowOnline)
        } else if (st.currentSurah < 114) {
            playFromAyah(context, reciter, st.currentSurah + 1, 1, st.playbackSpeed, allowOnline)
        } else {
            stop(context)
        }
    }

    private fun advanceToNextSurahOrStop(context: Context) {
        val st = _state.value
        val reciter = lastReciter ?: return stop(context)
        if (st.currentSurah < 114) {
            playFromAyah(context, reciter, st.currentSurah + 1, 1, st.playbackSpeed, allowOnline)
        } else {
            stop(context)
        }
    }

    private fun startTimestampSyncLoop(context: Context, token: Long) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive && token == sessionCounter.get()) {
                val mp = mediaPlayer
                if (mp != null && _state.value.isPlaying) {
                    val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(0L)
                    val entry = findTimingAtPosition(activeTimings, pos)
                    if (entry != null) {
                        val wordIdx = entry.wordTimings.firstOrNull { pos in it.startMs..it.endMs }?.wordIndex
                        val newPage = QuranCatalog.pageForAyah(entry.surah, entry.ayah)
                        val st = _state.value
                        if (st.repeatAyahCount != 1 && pos >= entry.endMs - 80L &&
                            (st.repeatAyahCount == 0 || currentAyahRepeatDone + 1 < st.repeatAyahCount)
                        ) {
                            currentAyahRepeatDone++
                            mp.seekTo(entry.startMs.toInt())
                        } else {
                            if (entry.ayah != st.currentAyah) currentAyahRepeatDone = 0
                            _state.update {
                                it.copy(
                                    currentSurah = entry.surah,
                                    currentAyah = entry.ayah,
                                    currentWordIndex = wordIdx,
                                    currentPage = newPage,
                                    positionMs = pos
                                )
                            }
                            if (entry.ayah != st.currentAyah) {
                                startNotificationService(context)
                            }
                        }
                    } else {
                        _state.update { it.copy(positionMs = pos) }
                    }
                }
                delay(80L)
            }
        }
    }

    private fun startPositionTicker(token: Long) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive && token == sessionCounter.get()) {
                val mp = mediaPlayer
                if (mp != null && _state.value.isPlaying) {
                    val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(0L)
                    _state.update { it.copy(positionMs = pos) }
                }
                delay(150L)
            }
        }
    }

    fun pause(context: Context) {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            _state.update { it.copy(isPlaying = false, isPaused = true) }
            startNotificationService(context)
        }
    }

    fun resume(context: Context) {
        val mp = mediaPlayer ?: return
        if (!mp.isPlaying) {
            AudioFocusCoordinator.acquireMode(context, ActiveAudioMode.LISTEN_AND_FOLLOW) {
                stop(context)
            }
            mp.start()
            _state.update { it.copy(isPlaying = true, isPaused = false) }
            startNotificationService(context)
        }
    }

    fun togglePlayPause(context: Context) {
        val st = _state.value
        if (st.isPlaying) {
            pause(context)
        } else if (st.isPaused && mediaPlayer != null) {
            resume(context)
        } else {
            val reciter = lastReciter ?: ReciterConfigEntity(
                id = st.reciterId,
                nameAr = st.reciterName,
                nameEn = "Reciter",
                remoteBaseUrl = ""
            )
            playFromAyah(context, reciter, st.currentSurah, st.currentAyah, st.playbackSpeed, allowOnline)
        }
    }

    fun nextAyah(context: Context) {
        val st = _state.value
        if (isContinuousSurahStream && activeTimings.isNotEmpty()) {
            val next = activeTimings.firstOrNull { it.ayah > st.currentAyah }
            if (next != null) {
                mediaPlayer?.seekTo(next.startMs.toInt())
                _state.update {
                    it.copy(
                        currentAyah = next.ayah,
                        currentPage = QuranCatalog.pageForAyah(next.surah, next.ayah),
                        currentWordIndex = null
                    )
                }
                return
            }
        }
        val reciter = lastReciter ?: return
        val maxAyah = QuranCatalog.surahByNumber(st.currentSurah)?.ayahCount ?: 7
        if (st.currentAyah < maxAyah) {
            playFromAyah(context, reciter, st.currentSurah, st.currentAyah + 1, st.playbackSpeed, allowOnline)
        } else if (st.currentSurah < 114) {
            playFromAyah(context, reciter, st.currentSurah + 1, 1, st.playbackSpeed, allowOnline)
        }
    }

    fun previousAyah(context: Context) {
        val st = _state.value
        if (isContinuousSurahStream && activeTimings.isNotEmpty()) {
            val prev = activeTimings.lastOrNull { it.ayah < st.currentAyah }
            if (prev != null) {
                mediaPlayer?.seekTo(prev.startMs.toInt())
                _state.update {
                    it.copy(
                        currentAyah = prev.ayah,
                        currentPage = QuranCatalog.pageForAyah(prev.surah, prev.ayah),
                        currentWordIndex = null
                    )
                }
                return
            }
        }
        val reciter = lastReciter ?: return
        if (st.currentAyah > 1) {
            playFromAyah(context, reciter, st.currentSurah, st.currentAyah - 1, st.playbackSpeed, allowOnline)
        } else if (st.currentSurah > 1) {
            val prevSurah = QuranCatalog.surahByNumber(st.currentSurah - 1)
            playFromAyah(context, reciter, st.currentSurah - 1, prevSurah?.ayahCount ?: 1, st.playbackSpeed, allowOnline)
        }
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _state.update { it.copy(playbackSpeed = clamped) }
        mediaPlayer?.let { applySpeed(it, clamped) }
    }

    fun setRepeatAyah(count: Int) {
        _state.update { it.copy(repeatAyahCount = count) }
    }

    fun setRepeatRange(startAyah: Int?, endAyah: Int?) {
        _state.update { it.copy(repeatRangeStart = startAyah, repeatRangeEnd = endAyah) }
    }

    fun stop(context: Context) {
        sessionCounter.incrementAndGet()
        releaseInternal(context)
        AudioFocusCoordinator.releaseMode(context, ActiveAudioMode.LISTEN_AND_FOLLOW)
        _state.update {
            it.copy(
                isPlaying = false,
                isPaused = false,
                isPreparing = false,
                currentWordIndex = null,
                positionMs = 0L,
                activeStreamCount = 0
            )
        }
        runCatching {
            context.stopService(Intent(context, QuranPlaybackService::class.java))
        }
    }

    private fun releaseInternal(context: Context) {
        syncJob?.cancel()
        syncJob = null
        mediaPlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            }
        }
        mediaPlayer = null
    }

    private fun applySpeed(mp: MediaPlayer, speed: Float) {
        runCatching {
            val params: PlaybackParams = mp.playbackParams ?: PlaybackParams()
            params.speed = speed
            mp.playbackParams = params
        }
    }

    private fun startNotificationService(context: Context) {
        runCatching {
            val intent = Intent(context, QuranPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun findTimingAtPosition(timings: List<AyahTimingEntry>, posMs: Long): AyahTimingEntry? {
        if (timings.isEmpty()) return null
        var low = 0
        var high = timings.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val item = timings[mid]
            when {
                posMs < item.startMs -> high = mid - 1
                posMs > item.endMs -> low = mid + 1
                else -> return item
            }
        }
        return timings.lastOrNull { it.startMs <= posMs } ?: timings.first()
    }

    fun parseTimingsJson(json: String): List<AyahTimingEntry> {
        val arr = JSONArray(json)
        val out = mutableListOf<AyahTimingEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val wordsArr = obj.optJSONArray("words")
            val wordList = mutableListOf<WordTiming>()
            if (wordsArr != null) {
                for (w in 0 until wordsArr.length()) {
                    val wo = wordsArr.getJSONObject(w)
                    wordList.add(
                        WordTiming(
                            wordIndex = wo.getInt("wordIndex"),
                            startMs = wo.getLong("startMs"),
                            endMs = wo.getLong("endMs")
                        )
                    )
                }
            }
            out.add(
                AyahTimingEntry(
                    surah = obj.getInt("surah"),
                    ayah = obj.getInt("ayah"),
                    startMs = obj.getLong("startMs"),
                    endMs = obj.getLong("endMs"),
                    wordTimings = wordList
                )
            )
        }
        return out.sortedBy { it.startMs }
    }

    suspend fun importReciterPackZip(context: Context, reciterId: String, zipUri: Uri): Result<Int> =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                val targetDir = File(recitersDir(context), reciterId).apply { mkdirs() }
                val canonical = targetDir.canonicalPath
                var count = 0
                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val name = File(entry.name).name
                                val lower = name.lowercase()
                                if (lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav") || lower.endsWith(".json")) {
                                    val outFile = File(targetDir, name)
                                    if (!outFile.canonicalPath.startsWith(canonical)) {
                                        throw SecurityException("Blocked unsafe ZIP path")
                                    }
                                    FileOutputStream(outFile).use { out -> zis.copyTo(out) }
                                    count++
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                } ?: error("Cannot open reciter archive")
                count
            }
        }

    private fun downloadAyahToCache(baseUrl: String, surah: Int, ayah: Int, destFile: File): Boolean {
        return runCatching {
            val fileName = "${surah.toString().padStart(3, '0')}${ayah.toString().padStart(3, '0')}.mp3"
            val url = URL("${baseUrl.trimEnd('/')}/$fileName")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 10000
            }
            if (conn.responseCode in 200..299) {
                destFile.parentFile?.mkdirs()
                conn.inputStream.use { input ->
                    FileOutputStream(destFile).use { out -> input.copyTo(out) }
                }
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
