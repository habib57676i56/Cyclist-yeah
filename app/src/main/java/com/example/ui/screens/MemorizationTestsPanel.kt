package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.ArabicNlpNormalizer
import com.example.firebase.FsMemorizationTestResult
import com.example.i18n.AppLanguage
import com.example.quran.AyahRecord
import com.example.quran.QuranCatalog
import com.example.ui.components.NoorGlassCard
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LocalNoorGlass
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.NoorViewModel

enum class MemTestMode(val labelAr: String, val labelEn: String) {
    CONTINUE_AYAH("أكمل الآية", "Continue Ayah"),
    MISSING_WORDS("الكلمة المفقودة", "Missing Word"),
    NEXT_AYAH("الآية التالية", "Next Ayah"),
    IDENTIFY_SURAH("تحديد السورة", "Identify Surah"),
    CHOOSE_CONTINUATION("اختر التكملة الصحيحة", "Choose Continuation"),
    RECITE_BLIND("تسميع غيبي", "Recite Blind")
}

private data class GeneratedTestQuestion(
    val mode: MemTestMode,
    val ayah: AyahRecord,
    val promptText: String,
    val options: List<String>,
    val correctOption: String,
    val expectedReveal: String
)

@Composable
fun MemorizationTestsPanel(
    vm: NoorViewModel,
    selectedSurah: Int,
    startAyah: Int,
    endAyah: Int,
    onJumpToVoiceRecite: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val testHistory by vm.fsTestResults.collectAsState()
    val isArabic = settings.language == AppLanguage.AR
    val glass = LocalNoorGlass.current

    var selectedMode by remember { mutableStateOf(MemTestMode.MISSING_WORDS) }
    var sessionSeed by remember { mutableIntStateOf(1) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var blindInput by remember { mutableStateOf("") }
    var revealBlindAnswer by remember { mutableStateOf(false) }
    var correctCount by remember { mutableIntStateOf(0) }
    var answeredCount by remember { mutableIntStateOf(0) }
    var sessionSaved by remember { mutableStateOf(false) }

    val pool = remember(selectedSurah, startAyah, endAyah) {
        val ranged = QuranCatalog.ayahsForSurahRange(selectedSurah, startAyah, endAyah)
        val allLoaded = QuranCatalog.allLoadedAyahs()
        if (ranged.size >= 2) ranged else allLoaded
    }

    val questions = remember(pool, selectedMode, sessionSeed) {
        buildTestQuestions(pool, QuranCatalog.allLoadedAyahs(), selectedMode, count = 5.coerceAtMost(pool.size))
    }

    val currentQ = questions.getOrNull(currentIndex)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NoorGlassCard(elevated = true, borderColor = glass.accentGold.copy(alpha = 0.45f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Quiz, contentDescription = null, tint = glass.accentGold)
                    Text(
                        text = if (isArabic) "اختبارات تثبيت الحفظ التفاعلية" else "Interactive Memorization Tests",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.accentGold
                    )
                }
                IconButton(
                    onClick = {
                        sessionSeed++
                        currentIndex = 0
                        selectedAnswer = null
                        blindInput = ""
                        revealBlindAnswer = false
                        correctCount = 0
                        answeredCount = 0
                        sessionSaved = false
                    },
                    modifier = Modifier.testTag("reset_mem_test_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Restart Test")
                }
            }

            Spacer(Modifier.height(8.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MemTestMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = {
                            selectedMode = mode
                            sessionSeed++
                            currentIndex = 0
                            selectedAnswer = null
                            blindInput = ""
                            revealBlindAnswer = false
                            correctCount = 0
                            answeredCount = 0
                            sessionSaved = false
                        },
                        label = { Text(if (isArabic) mode.labelAr else mode.labelEn) },
                        modifier = Modifier.testTag("test_mode_${mode.name.lowercase()}")
                    )
                }
            }

            if (currentQ == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (isArabic) "يرجى اختيار سورة تحتوي على آيات محملة لبدء الاختبار." else "Select a Surah with loaded Ayahs to start a test.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (answeredCount >= questions.size && sessionSaved) {
                val pct = ((correctCount * 100f) / questions.size.coerceAtLeast(1)).toInt()
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = SuccessGreen.copy(alpha = 0.16f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().testTag("mem_test_summary_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (isArabic) "اكتمل الاختبار! النتيجة: $pct%" else "Test Completed! Score: $pct%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Text(
                            text = if (isArabic) "الإجابات الصحيحة: $correctCount من ${questions.size}" else "Correct Answers: $correctCount / ${questions.size}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = {
                                sessionSeed++
                                currentIndex = 0
                                selectedAnswer = null
                                blindInput = ""
                                revealBlindAnswer = false
                                correctCount = 0
                                answeredCount = 0
                                sessionSaved = false
                            }
                        ) {
                            Text(if (isArabic) "اختبار جديد" else "Start New Round")
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (isArabic) "سؤال ${currentIndex + 1} من ${questions.size} • الصحيح: $correctCount"
                    else "Question ${currentIndex + 1} / ${questions.size} • Score: $correctCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = currentQ.promptText,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = AmiriFontFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))

                if (currentQ.mode == MemTestMode.RECITE_BLIND) {
                    OutlinedTextField(
                        value = blindInput,
                        onValueChange = { blindInput = it },
                        label = { Text(if (isArabic) "اكتب الآية غيباً للتحقق" else "Type the Ayah from memory") },
                        modifier = Modifier.fillMaxWidth().testTag("blind_test_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val expectedNorm = ArabicNlpNormalizer.normalizeArabicText(currentQ.expectedReveal)
                                val typedNorm = ArabicNlpNormalizer.normalizeArabicText(blindInput)
                                val matched = typedNorm.isNotBlank() &&
                                    (expectedNorm == typedNorm || expectedNorm.split(" ").intersect(typedNorm.split(" ").toSet()).size >= (expectedNorm.split(" ").size / 2).coerceAtLeast(1))
                                selectedAnswer = if (matched) currentQ.correctOption else "__WRONG__"
                                revealBlindAnswer = true
                                answeredCount++
                                if (matched) correctCount++
                            },
                            enabled = selectedAnswer == null && blindInput.isNotBlank()
                        ) {
                            Text(if (isArabic) "تحقق من الإجابة" else "Check Answer")
                        }
                        FilledTonalButton(onClick = {
                            revealBlindAnswer = !revealBlindAnswer
                        }) {
                            Text(if (revealBlindAnswer) (if (isArabic) "إخفاء" else "Hide") else (if (isArabic) "إظهار الآية" else "Reveal"))
                        }
                        OutlinedButton(onClick = onJumpToVoiceRecite) {
                            Text(if (isArabic) "تسميع صوتي" else "Voice")
                        }
                    }
                    if (revealBlindAnswer) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = currentQ.expectedReveal,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = AmiriFontFamily),
                            color = glass.accentGold
                        )
                    }
                } else {
                    currentQ.options.forEachIndexed { idx, opt ->
                        val isChosen = selectedAnswer == opt
                        val isCorrect = opt == currentQ.correctOption
                        val containerColor = when {
                            selectedAnswer == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            isCorrect -> SuccessGreen.copy(alpha = 0.24f)
                            isChosen -> ErrorRed.copy(alpha = 0.24f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                        Surface(
                            color = containerColor,
                            shape = MaterialTheme.shapes.medium,
                            onClick = {
                                if (selectedAnswer == null) {
                                    selectedAnswer = opt
                                    answeredCount++
                                    if (isCorrect) correctCount++
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .testTag("mem_test_option_$idx")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = opt,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = AmiriFontFamily),
                                    modifier = Modifier.weight(1f)
                                )
                                if (selectedAnswer != null && isCorrect) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Correct", tint = SuccessGreen)
                                }
                            }
                        }
                    }
                }

                if (selectedAnswer != null) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (currentIndex < questions.lastIndex) {
                                currentIndex++
                                selectedAnswer = null
                                blindInput = ""
                                revealBlindAnswer = false
                            } else {
                                val pct = ((correctCount * 100f) / questions.size.coerceAtLeast(1)).toInt()
                                vm.recordTestResult(
                                    testType = selectedMode.name,
                                    surah = selectedSurah,
                                    startAyah = startAyah,
                                    endAyah = endAyah,
                                    scorePercent = pct,
                                    totalQuestions = questions.size,
                                    correctAnswers = correctCount
                                )
                                sessionSaved = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("mem_test_next_button")
                    ) {
                        Text(
                            if (currentIndex < questions.lastIndex)
                                (if (isArabic) "السؤال التالي" else "Next Question")
                            else
                                (if (isArabic) "إنهاء وحفظ النتيجة" else "Finish & Save Result")
                        )
                    }
                }
            }
        }

        // Cloud Saved Test History
        if (testHistory.isNotEmpty()) {
            NoorGlassCard {
                Text(
                    text = if (isArabic) "سجل نتائج الاختبارات (${testHistory.size})" else "Saved Test Results (${testHistory.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                testHistory.take(8).forEach { r: FsMemorizationTestResult ->
                    val surahName = QuranCatalog.surahByNumber(r.surah.toInt())?.nameAr ?: "Surah ${r.surah}"
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${r.testType} • $surahName", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${r.scorePercent}% (${r.correctAnswers}/${r.totalQuestions})",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (r.scorePercent >= 80) SuccessGreen else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun buildTestQuestions(
    pool: List<AyahRecord>,
    allLoaded: List<AyahRecord>,
    mode: MemTestMode,
    count: Int
): List<GeneratedTestQuestion> {
    if (pool.isEmpty() || count <= 0) return emptyList()
    val candidates = pool.shuffled().take(count)
    val distractorPool = allLoaded.ifEmpty { pool }

    return candidates.map { ayah ->
        val words = ayah.textUthmani.split(" ").filter { it.isNotBlank() }
        val surahName = QuranCatalog.surahByNumber(ayah.surah)?.nameAr ?: "سورة ${ayah.surah}"
        when (mode) {
            MemTestMode.MISSING_WORDS -> {
                val hideIdx = (words.size / 2).coerceIn(0, (words.size - 1).coerceAtLeast(0))
                val targetWord = words.getOrElse(hideIdx) { ayah.textUthmani }
                val promptWords = words.mapIndexed { idx, w -> if (idx == hideIdx) "_____" else w }.joinToString(" ")
                val distractors = distractorPool
                    .flatMap { it.textUthmani.split(" ") }
                    .filter { it.isNotBlank() && it != targetWord }
                    .distinct()
                    .shuffled()
                    .take(3)
                val opts = (distractors + targetWord).distinct().shuffled()
                GeneratedTestQuestion(
                    mode = mode,
                    ayah = ayah,
                    promptText = "$surahName (${ayah.ayah}):\n$promptWords",
                    options = opts,
                    correctOption = targetWord,
                    expectedReveal = ayah.textUthmani
                )
            }
            MemTestMode.NEXT_AYAH -> {
                val nextAyah = allLoaded.firstOrNull { it.surah == ayah.surah && it.ayah == ayah.ayah + 1 }
                    ?: pool.firstOrNull { it.ayah != ayah.ayah }
                    ?: ayah
                val distractors = distractorPool
                    .filter { it.textUthmani != nextAyah.textUthmani }
                    .map { it.textUthmani }
                    .distinct()
                    .shuffled()
                    .take(3)
                val opts = (distractors + nextAyah.textUthmani).distinct().shuffled()
                GeneratedTestQuestion(
                    mode = mode,
                    ayah = ayah,
                    promptText = "ما الآية التالية لـ:\n${ayah.textUthmani} ﴿${ayah.ayah}﴾",
                    options = opts,
                    correctOption = nextAyah.textUthmani,
                    expectedReveal = nextAyah.textUthmani
                )
            }
            MemTestMode.IDENTIFY_SURAH -> {
                val distractors = QuranCatalog.surahs
                    .filter { it.number != ayah.surah }
                    .map { "${it.nameAr} (${it.nameEn})" }
                    .shuffled()
                    .take(3)
                val correctLabel = "$surahName (${QuranCatalog.surahByNumber(ayah.surah)?.nameEn ?: ""})"
                val opts = (distractors + correctLabel).distinct().shuffled()
                GeneratedTestQuestion(
                    mode = mode,
                    ayah = ayah,
                    promptText = "في أي سورة وردت هذه الآية؟\n${ayah.textUthmani}",
                    options = opts,
                    correctOption = correctLabel,
                    expectedReveal = correctLabel
                )
            }
            MemTestMode.CONTINUE_AYAH, MemTestMode.CHOOSE_CONTINUATION -> {
                val splitAt = (words.size / 2).coerceAtLeast(1)
                val firstHalf = words.take(splitAt).joinToString(" ")
                val secondHalf = words.drop(splitAt).joinToString(" ").ifBlank { ayah.textUthmani }
                val distractors = distractorPool
                    .filter { it != ayah }
                    .mapNotNull { rec ->
                        val w = rec.textUthmani.split(" ")
                        w.drop((w.size / 2).coerceAtLeast(1)).joinToString(" ").takeIf { it.isNotBlank() && it != secondHalf }
                    }
                    .distinct()
                    .shuffled()
                    .take(3)
                val opts = (distractors + secondHalf).distinct().shuffled()
                GeneratedTestQuestion(
                    mode = mode,
                    ayah = ayah,
                    promptText = "$surahName (${ayah.ayah}):\n$firstHalf ...",
                    options = opts,
                    correctOption = secondHalf,
                    expectedReveal = ayah.textUthmani
                )
            }
            MemTestMode.RECITE_BLIND -> {
                val cue = words.take(2.coerceAtMost(words.size)).joinToString(" ")
                GeneratedTestQuestion(
                    mode = mode,
                    ayah = ayah,
                    promptText = "سمّع الآية ${ayah.ayah} من $surahName (بدايتها: $cue ...)",
                    options = emptyList(),
                    correctOption = ayah.textUthmani,
                    expectedReveal = ayah.textUthmani
                )
            }
        }
    }
}
