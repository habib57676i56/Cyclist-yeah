package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.ActiveAudioMode
import com.example.audio.AudioFocusCoordinator
import com.example.audio.QuranAudioEngine
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.quran.QuranCatalog
import com.example.recitation.WordComparisonStatus
import com.example.ui.components.NoorEmptyState
import com.example.ui.components.NoorGlassCard
import com.example.ui.theme.*
import com.example.viewmodel.NoorViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemorizeScreen(
    vm: NoorViewModel,
    initialSurah: Int = 1,
    initialAyah: Int = 1
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val reciters by vm.reciters.collectAsState()
    val plans by vm.memorizationPlans.collectAsState()
    val mistakes by vm.mistakes.collectAsState()
    val assessments by vm.assessments.collectAsState()
    val latestComparison by vm.latestComparison.collectAsState()
    val recordingState by vm.audioRecorder.state.collectAsState()
    val sttState by vm.speechRecognizer.state.collectAsState()
    val audioState by QuranAudioEngine.state.collectAsState()

    val strings = stringsFor(settings.language)
    val isArabic = settings.language == AppLanguage.AR
    val glass = LocalNoorGlass.current

    var selectedSurah by remember(initialSurah) { mutableIntStateOf(initialSurah) }
    var startAyah by remember(initialAyah) { mutableIntStateOf(initialAyah) }
    var endAyah by remember(initialSurah, initialAyah) {
        val maxA = QuranCatalog.surahByNumber(initialSurah)?.ayahCount ?: 7
        mutableIntStateOf(maxA.coerceAtMost(initialAyah + 6))
    }

    // Two strictly separated audio modes
    var isMyRecitationMode by remember { mutableStateOf(true) }
    var hideQuranText by remember { mutableStateOf(false) }
    var peekAyah by remember { mutableStateOf<Int?>(null) }
    var activeSubTab by remember { mutableIntStateOf(0) } // 0: Studio, 1: Plans & Tests, 2: Mistakes & History
    var showReciterSheet by remember { mutableStateOf(false) }
    var showPlanDialog by remember { mutableStateOf(false) }

    val surahList = remember { QuranCatalog.surahs }
    val currentSurahMeta = QuranCatalog.surahByNumber(selectedSurah) ?: surahList.first()
    val targetAyahs = remember(selectedSurah, startAyah, endAyah) {
        QuranCatalog.ayahsForSurahRange(selectedSurah, startAyah, endAyah)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        vm.audioRecorder.refreshDiagnostics()
        if (granted) {
            QuranAudioEngine.stop(context)
            val started = vm.audioRecorder.startRecording()
            if (started) vm.speechRecognizer.startListening(preferOffline = true)
        } else {
            vm.postStatus("Microphone permission denied. Enable RECORD_AUDIO in Android Settings to recite.")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(strings.memorization, style = MaterialTheme.typography.headlineMedium, color = glass.accentGold)
            Spacer(Modifier.height(6.dp))
            // Separate Modes: Listen & Follow vs My Recitation & Memorization
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isMyRecitationMode,
                    onClick = {
                        isMyRecitationMode = false
                        vm.audioRecorder.stopRecording()
                        vm.speechRecognizer.stopListening()
                    },
                    label = { Text(strings.listenAndFollowMode) },
                    leadingIcon = { Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f).testTag("mode_listen_follow")
                )
                FilterChip(
                    selected = isMyRecitationMode,
                    onClick = {
                        isMyRecitationMode = true
                        QuranAudioEngine.stop(context)
                    },
                    label = { Text(strings.myRecitationMode) },
                    leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f).testTag("mode_my_recitation")
                )
            }
        }

        // Organized Sub-tabs to avoid button overcrowding
        item {
            TabRow(selectedTabIndex = activeSubTab) {
                Tab(selected = activeSubTab == 0, onClick = { activeSubTab = 0 }, text = { Text(if (isArabic) "التلاوة" else "Recite") }, modifier = Modifier.testTag("mem_subtab_0"))
                Tab(selected = activeSubTab == 1, onClick = { activeSubTab = 1 }, text = { Text(if (isArabic) "الخطط والاختبار" else "Plans & Test") }, modifier = Modifier.testTag("mem_subtab_1"))
                Tab(selected = activeSubTab == 2, onClick = { activeSubTab = 2 }, text = { Text(if (isArabic) "الأخطاء والتقييم" else "Mistakes") }, modifier = Modifier.testTag("mem_subtab_2"))
            }
        }

        if (activeSubTab == 0) {
            // Surah & Ayah Range Card
            item {
                NoorGlassCard {
                    var surahMenuOpen by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            OutlinedButton(onClick = { surahMenuOpen = true }, modifier = Modifier.testTag("mem_surah_picker")) {
                                Text("${currentSurahMeta.number}. ${currentSurahMeta.nameAr}")
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = surahMenuOpen, onDismissRequest = { surahMenuOpen = false }) {
                                surahList.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text("${s.number}. ${s.nameAr} (${s.nameEn})") },
                                        onClick = {
                                            selectedSurah = s.number
                                            startAyah = 1
                                            endAyah = s.ayahCount.coerceAtMost(7)
                                            surahMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (isArabic) "إخفاء النص" else "Hide Text", style = MaterialTheme.typography.labelMedium)
                            Switch(
                                checked = hideQuranText,
                                onCheckedChange = { hideQuranText = it; peekAyah = null },
                                modifier = Modifier.testTag("hide_quran_switch")
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = startAyah.toString(),
                            onValueChange = { v ->
                                startAyah = (v.toIntOrNull() ?: 1).coerceIn(1, currentSurahMeta.ayahCount)
                                if (endAyah < startAyah) endAyah = startAyah
                            },
                            label = { Text(if (isArabic) "من آية" else "From Ayah") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endAyah.toString(),
                            onValueChange = { v ->
                                endAyah = (v.toIntOrNull() ?: startAyah).coerceIn(startAyah, currentSurahMeta.ayahCount)
                            },
                            label = { Text(if (isArabic) "إلى آية" else "To Ayah") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Ayahs Display (Supports Hide-Text memorization with tap-to-peek)
            item {
                NoorGlassCard {
                    if (targetAyahs.isEmpty()) {
                        Text(
                            text = if (isArabic)
                                "يرجى استيراد النص الكامل للسورة أو اختيار إحدى السور المدمجة (الفاتحة، الإخلاص، الفلق، الناس، العصر، الكوثر، آية الكرسي)."
                            else
                                "Full Ayah text for this Surah range is not bundled yet; select a bundled Surah (Al-Fatihah, Al-Ikhlas, Al-Falaq, An-Nas, Al-Asr, Al-Kawthar, 2:255) or import a full Quran JSON pack.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        targetAyahs.forEach { ayah ->
                            val concealed = hideQuranText && peekAyah != ayah.ayah
                            val isActive = !isMyRecitationMode && audioState.isPlaying &&
                                audioState.currentSurah == ayah.surah && audioState.currentAyah == ayah.ayah
                            Surface(
                                color = if (isActive) LiveRecitationHighlight else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { if (hideQuranText) peekAyah = if (peekAyah == ayah.ayah) null else ayah.ayah }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (concealed) "﴿ آية ${ayah.ayah} — اضغط للمعاينة ﴾" else "${ayah.textUthmani} ﴿${ayah.ayah}﴾",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = AmiriFontFamily,
                                        lineHeight = 36.sp
                                    ),
                                    color = if (concealed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Mode 1: Listen & Follow
            if (!isMyRecitationMode) {
                item {
                    NoorGlassCard(elevated = true) {
                        Text(strings.listenAndFollowMode, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(onClick = { showReciterSheet = true }) {
                                Text(strings.chooseReciter)
                            }
                            Button(
                                onClick = {
                                    AudioFocusCoordinator.releaseMode(context, ActiveAudioMode.RECORDING_CAPTURE)
                                    QuranAudioEngine.setRepeatRange(startAyah, endAyah)
                                    vm.playFromAyah(selectedSurah, startAyah)
                                },
                                modifier = Modifier.weight(1f).testTag("mem_listen_play")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(if (audioState.isPlaying) "${audioState.currentSurah}:${audioState.currentAyah}" else "Play Range")
                            }
                            if (audioState.isPlaying || audioState.isPaused) {
                                IconButton(onClick = { QuranAudioEngine.stop(context) }) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                                }
                            }
                        }
                        audioState.errorMessage?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                // Mode 2: Real Microphone Recording + Arabic Speech-to-Text + Assessment
                item {
                    NoorGlassCard(elevated = true, borderColor = glass.accentGold.copy(alpha = 0.5f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.myRecitationMode, style = MaterialTheme.typography.titleMedium)
                            // Indicator ONLY visible when MediaRecorder actually succeeded & is capturing!
                            if (recordingState.isRecording) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(if (recordingState.isPaused) WarningAmber else ErrorRed)
                                    )
                                    Text(
                                        text = "${recordingState.elapsedMs / 1000}s",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (recordingState.isPaused) WarningAmber else ErrorRed
                                    )
                                }
                            }
                        }

                        // Real Microphone Amplitude Meter while recording
                        if (recordingState.isRecording) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { recordingState.amplitudeNorm },
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!recordingState.isRecording) {
                                Button(
                                    onClick = {
                                        QuranAudioEngine.stop(context)
                                        vm.speechRecognizer.clearTranscript()
                                        if (vm.audioRecorder.hasRecordPermission()) {
                                            if (vm.audioRecorder.startRecording()) {
                                                vm.speechRecognizer.startListening(preferOffline = true)
                                            }
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("start_recording_button")
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = strings.startRecording)
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.startRecording)
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        if (recordingState.isPaused) vm.audioRecorder.resumeRecording()
                                        else vm.audioRecorder.pauseRecording()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (recordingState.isPaused) strings.resumeRecording else strings.pauseRecording)
                                }
                                Button(
                                    onClick = {
                                        val saved = vm.audioRecorder.stopRecording()
                                        vm.speechRecognizer.stopListening()
                                        val transcript = sttState.finalTranscript.ifBlank { sttState.partialTranscript }
                                        if (transcript.isNotBlank()) {
                                            vm.evaluateRecitation(
                                                surah = selectedSurah,
                                                startAyah = startAyah,
                                                endAyah = endAyah,
                                                recognizedTranscript = transcript,
                                                audioPath = saved ?: "",
                                                durationMs = recordingState.elapsedMs,
                                                longPauses = recordingState.longPauseCount,
                                                confidence = sttState.confidenceScore
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                    modifier = Modifier.weight(1f).testTag("stop_recording_button")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = strings.stopRecording)
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.stopRecording)
                                }
                            }
                        }

                        // Playback, Delete, Retry controls for actual saved audio
                        recordingState.savedFilePath?.let { path ->
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (recordingState.isPlayingBack) vm.audioRecorder.stopPlayback()
                                        else vm.audioRecorder.playRecording(path)
                                    },
                                    modifier = Modifier.weight(1f).testTag("play_recorded_audio_button")
                                ) {
                                    Icon(
                                        if (recordingState.isPlayingBack) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (recordingState.isPlayingBack) "Stop" else "Playback")
                                }
                                OutlinedButton(
                                    onClick = {
                                        vm.audioRecorder.deleteRecording(path)
                                        vm.speechRecognizer.clearTranscript()
                                    },
                                    modifier = Modifier.testTag("delete_recording_button")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = strings.deleteLabel, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Real Recognized Arabic Transcript & Diagnostic Errors (never silent!)
                        Spacer(Modifier.height(10.dp))
                        val shownTranscript = sttState.finalTranscript.ifBlank { sttState.partialTranscript }
                        if (shownTranscript.isNotBlank()) {
                            Text(
                                text = if (isArabic) "النص المُتعرّف عليه:" else "Recognized Arabic Speech:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = shownTranscript,
                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = AmiriFontFamily),
                                modifier = Modifier.padding(top = 4.dp).testTag("recognized_transcript_text")
                            )
                            if (!recordingState.isRecording) {
                                Spacer(Modifier.height(6.dp))
                                FilledTonalButton(
                                    onClick = {
                                        vm.evaluateRecitation(
                                            surah = selectedSurah,
                                            startAyah = startAyah,
                                            endAyah = endAyah,
                                            recognizedTranscript = shownTranscript,
                                            audioPath = recordingState.savedFilePath ?: "",
                                            durationMs = recordingState.elapsedMs,
                                            longPauses = recordingState.longPauseCount,
                                            confidence = sttState.confidenceScore
                                        )
                                    },
                                    modifier = Modifier.testTag("evaluate_recitation_button")
                                ) {
                                    Text(if (isArabic) "تقييم ومقارنة التلاوة" else "Compare & Evaluate")
                                }
                            }
                        }

                        recordingState.errorMessage?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        sttState.errorReason?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                        }
                    }
                }

                // Detailed Word-by-Word Comparison Breakdown
                latestComparison?.let { res ->
                    item {
                        NoorGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isArabic) "نتيجة التقييم: ${res.accuracyPercent}%" else "Assessment: ${res.accuracyPercent}%",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (res.accuracyPercent >= 85) SuccessGreen else WarningAmber
                                )
                                Text(
                                    text = "✓ ${res.correctCount}  ? ${res.uncertainCount}  ✕ ${res.omittedCount + res.replacedCount}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            if (res.skippedAyahs.isNotEmpty() || res.orderWarning || res.longPauseCount > 0) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = buildString {
                                        if (res.skippedAyahs.isNotEmpty()) append("Skipped Ayahs: ${res.skippedAyahs.joinToString()} • ")
                                        if (res.orderWarning) append("Verse order warning • ")
                                        if (res.longPauseCount > 0) append("Long pauses: ${res.longPauseCount}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WarningAmber
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                res.wordItems.forEach { w ->
                                    val bg = when (w.status) {
                                        WordComparisonStatus.MATCHED -> SuccessGreen.copy(alpha = 0.22f)
                                        WordComparisonStatus.UNCERTAIN_REVIEW -> WarningAmber.copy(alpha = 0.24f)
                                        else -> ErrorRed.copy(alpha = 0.24f)
                                    }
                                    val label = when (w.status) {
                                        WordComparisonStatus.OMITTED -> "[ناقص: ${w.expectedWord}]"
                                        WordComparisonStatus.ADDED -> "[زائد: ${w.spokenWord}]"
                                        WordComparisonStatus.REPEATED -> "[مكرر: ${w.spokenWord}]"
                                        WordComparisonStatus.REPLACED -> "${w.expectedWord} ≠ ${w.spokenWord}"
                                        WordComparisonStatus.UNCERTAIN_REVIEW -> "${w.expectedWord} (~${w.spokenWord})"
                                        WordComparisonStatus.MATCHED -> w.expectedWord
                                    }
                                    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = AmiriFontFamily),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (activeSubTab == 1) {
            // Memorization Plans + 6-Mode Interactive Memorization Tests
            item {
                Button(
                    onClick = { showPlanDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("new_mem_plan_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isArabic) "إنشاء خطة حفظ يومية" else "Create Daily Memorization Plan")
                }
            }

            item {
                MemorizationTestsPanel(
                    vm = vm,
                    selectedSurah = selectedSurah,
                    startAyah = startAyah,
                    endAyah = endAyah,
                    onJumpToVoiceRecite = {
                        activeSubTab = 0
                        isMyRecitationMode = true
                    }
                )
            }

            if (plans.isEmpty()) {
                item {
                    NoorEmptyState(
                        icon = Icons.AutoMirrored.Filled.EventNote,
                        title = if (isArabic) "لا توجد خطط حفظ بعد" else "No Memorization Plans Yet",
                        description = if (isArabic) "أنشئ خطة يومية مع جدول المراجعة والتثبيت." else "Create a daily memorization and revision schedule."
                    )
                }
            } else {
                items(plans, key = { it.id }) { p ->
                    NoorGlassCard {
                        Text(p.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Surah ${p.surah} (${p.startAyah}-${p.endAyah}) • ${p.dailyAyahTarget} Ayahs/day • ${p.status}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Mistake Revision Queue & Saved Assessment History
            item {
                Text(
                    text = if (isArabic) "مراجعة الأخطاء السابقة (${mistakes.count { !it.resolved }})" else "Mistakes Requiring Revision (${mistakes.count { !it.resolved }})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(mistakes.filter { !it.resolved }, key = { it.id }) { m ->
                NoorGlassCard(contentPadding = 12.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${m.expectedWord} (${m.surah}:${m.ayah})", style = MaterialTheme.typography.titleMedium.copy(fontFamily = AmiriFontFamily))
                            Text("${m.errorType} • Spoken: ${m.spokenWord.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                        }
                        FilledTonalButton(onClick = { vm.resolveMistake(m.id) }) {
                            Text(if (isArabic) "تم التصحيح" else "Resolved")
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isArabic) "سجل تقييمات التلاوة (${assessments.size})" else "Assessment History (${assessments.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(assessments, key = { it.id }) { a ->
                NoorGlassCard(contentPadding = 12.dp) {
                    Text(
                        text = "${QuranCatalog.surahByNumber(a.surah)?.nameAr ?: ""} (${a.startAyah}-${a.endAyah}) • ${a.accuracyPercent}%",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Status: ${a.status} • Correct: ${a.correctCount} • Uncertain: ${a.uncertainCount} • Mistakes: ${a.mistakeCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (a.teacherNotes.isNotBlank()) {
                        Text("Teacher: ${a.teacherNotes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showReciterSheet) {
        ReciterPickerSheet(
            reciters = reciters,
            selectedReciterId = settings.selectedReciterId,
            strings = strings,
            onSelect = { vm.chooseReciter(it) },
            onDismiss = { showReciterSheet = false }
        )
    }

    if (showPlanDialog) {
        var title by remember { mutableStateOf("") }
        var dailyTarget by remember { mutableStateOf("5") }
        AlertDialog(
            onDismissRequest = { showPlanDialog = false },
            title = { Text(if (isArabic) "إنشاء خطة حفظ" else "Create Memorization Plan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    OutlinedTextField(value = dailyTarget, onValueChange = { dailyTarget = it.filter { c -> c.isDigit() } }, label = { Text("Ayahs / Day") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.createMemorizationPlan(title, selectedSurah, startAyah, endAyah, dailyTarget.toIntOrNull() ?: 5)
                    showPlanDialog = false
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showPlanDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}
