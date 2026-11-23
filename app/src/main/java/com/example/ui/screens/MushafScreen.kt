package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.QuranAudioEngine
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.quran.AyahRecord
import com.example.quran.MushafCoordinateEngine
import com.example.quran.QuranCatalog
import com.example.ui.components.NoorGlassCard
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.LiveRecitationHighlight
import com.example.ui.theme.LocalNoorGlass
import com.example.ui.theme.UserSelectionHighlight
import com.example.viewmodel.NoorViewModel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun MushafScreen(
    vm: NoorViewModel,
    onNavigateMemorize: (surah: Int, ayah: Int) -> Unit,
    onFullScreenChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val currentPage by vm.currentPage.collectAsState()
    val selectedAyah by vm.selectedAyah.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val history by vm.readingHistory.collectAsState()
    val reciters by vm.reciters.collectAsState()
    val installedImageCount by vm.mushafPackPagesCount.collectAsState()
    val audioState by QuranAudioEngine.state.collectAsState()

    val strings = stringsFor(settings.language)
    val glass = LocalNoorGlass.current
    val isArabic = settings.language == AppLanguage.AR

    var isFullScreen by remember { mutableStateOf(false) }
    var showReciterSheet by remember { mutableStateOf(false) }
    var showIndexSheet by remember { mutableStateOf(false) }
    var linguisticTab by remember { mutableStateOf<Int?>(null) }

    // Auto-scroll state (strictly independent from Sheikh audio sync)
    var autoScrollRunning by remember { mutableStateOf(false) }
    var autoScrollPaused by remember { mutableStateOf(false) }
    var autoScrollSpeed by remember { mutableFloatStateOf(settings.autoScrollSpeed) }

    // Zoom & Pan state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val scrollState = rememberScrollState()

    // Automatic page sync when Sheikh audio advances across page boundaries
    LaunchedEffect(audioState.isPlaying, audioState.currentPage) {
        if (audioState.isPlaying && audioState.currentPage != currentPage) {
            vm.goToPage(audioState.currentPage, recordHistory = false)
        }
    }

    LaunchedEffect(isFullScreen) {
        onFullScreenChanged(isFullScreen)
    }

    // Smooth frame-driven Auto-Scroll
    LaunchedEffect(autoScrollRunning, autoScrollPaused, autoScrollSpeed, currentPage) {
        if (autoScrollRunning && !autoScrollPaused) {
            var lastNanos = 0L
            while (isActive && autoScrollRunning && !autoScrollPaused) {
                withFrameNanos { nanos ->
                    if (lastNanos != 0L) {
                        val dtSec = (nanos - lastNanos) / 1_000_000_000f
                        val deltaPx = (dtSec * 48f * autoScrollSpeed).toInt().coerceAtLeast(1)
                        if (scrollState.value < scrollState.maxValue) {
                            scope.launch { scrollState.scrollTo(scrollState.value + deltaPx) }
                        } else if (currentPage < 604) {
                            vm.goToPage(currentPage + 1)
                            scope.launch { scrollState.scrollTo(0) }
                        } else {
                            autoScrollRunning = false
                        }
                    }
                    lastNanos = nanos
                }
            }
        }
    }

    val mushafZipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importMushafZip(it) } }

    val pageSurahs = remember(currentPage) { QuranCatalog.surahsOnPage(currentPage) }
    val pageAyahs = remember(currentPage) { QuranCatalog.ayahsOnPage(currentPage) }
    val pageJuz = remember(currentPage) { QuranCatalog.juzForPage(currentPage) }
    val pageHizb = remember(currentPage) { QuranCatalog.hizbForPage(currentPage) }
    val pageImageFile = remember(currentPage, installedImageCount) {
        MushafCoordinateEngine.pageImageFile(context, currentPage)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Mushaf Chrome (hidden in Full-Screen mode without stopping audio!)
            AnimatedVisibility(visible = !isFullScreen) {
                NoorGlassCard(
                    cornerRadius = 0.dp,
                    contentPadding = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${pageSurahs.firstOrNull()?.nameAr ?: ""} • ${strings.pageLabel} $currentPage / 604",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("mushaf_page_header")
                            )
                            Text(
                                text = "${pageJuz.nameAr} • حزب ${pageHizb.hizb}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    val s = pageSurahs.firstOrNull()?.number ?: 1
                                    val a = pageAyahs.firstOrNull()?.ayah ?: 1
                                    vm.toggleBookmark(currentPage, s, a, isFavorite = false)
                                },
                                modifier = Modifier.testTag("mushaf_bookmark_button")
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = strings.bookmarks)
                            }
                            IconButton(
                                onClick = { showIndexSheet = true },
                                modifier = Modifier.testTag("mushaf_index_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = strings.surahIndex)
                            }
                            IconButton(
                                onClick = { mushafZipPicker.launch(arrayOf("application/zip", "*/*")) },
                                modifier = Modifier.testTag("mushaf_import_pack_button")
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = "Import Mushaf Pack")
                            }
                        }
                    }
                }
            }

            // Main Interactive Mushaf Page Canvas (Smooth vertical drag + pinch-to-zoom + tap/long-press Ayah selection)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(glass.mushafPaper)
                    .pointerInput(zoomScale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, 2.6f)
                            zoomScale = newScale
                            panOffset = if (newScale > 1.02f) {
                                panOffset + pan
                            } else {
                                Offset.Zero
                            }
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (pageImageFile != null) {
                        // Authentic Scanned/Image Madinah Mushaf Page + Exact Coordinate Overlay (preserving Ayah number circles)
                        ImportedMushafImagePage(
                            imagePath = pageImageFile.absolutePath,
                            page = currentPage,
                            selectedAyah = selectedAyah,
                            liveSurah = if (audioState.isPlaying || audioState.isPaused) audioState.currentSurah else null,
                            liveAyah = if (audioState.isPlaying || audioState.isPaused) audioState.currentAyah else null,
                            liveWordIndex = audioState.currentWordIndex,
                            onSelectAyah = { s, a ->
                                val rec = QuranCatalog.findAyah(s, a) ?: AyahRecord(s, a, currentPage, "")
                                vm.selectAyah(rec)
                            },
                            onTapEmpty = {
                                if (selectedAyah != null) vm.selectAyah(null) else isFullScreen = !isFullScreen
                            }
                        )
                    } else {
                        // Built-in Madinah-style Uthmani Page Renderer
                        UthmaniMushafPageCard(
                            page = currentPage,
                            pageSurahs = pageSurahs.map { it.number to it.nameAr },
                            ayahs = pageAyahs,
                            selectedAyah = selectedAyah,
                            liveSurah = if (audioState.isPlaying || audioState.isPaused) audioState.currentSurah else null,
                            liveAyah = if (audioState.isPlaying || audioState.isPaused) audioState.currentAyah else null,
                            onSelectAyah = { vm.selectAyah(it) },
                            onTapEmpty = {
                                if (selectedAyah != null) vm.selectAyah(null) else isFullScreen = !isFullScreen
                            },
                            onImportPackClick = { mushafZipPicker.launch(arrayOf("application/zip", "*/*")) },
                            isArabic = isArabic
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    // Page Turn Controls at bottom of page
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { if (currentPage > 1) vm.goToPage(currentPage - 1) },
                            enabled = currentPage > 1,
                            modifier = Modifier.testTag("mushaf_prev_page")
                        ) {
                            Text(if (isArabic) "الصفحة السابقة" else "Prev Page")
                        }
                        Text("$currentPage / 604", style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(
                            onClick = { if (currentPage < 604) vm.goToPage(currentPage + 1) },
                            enabled = currentPage < 604,
                            modifier = Modifier.testTag("mushaf_next_page")
                        ) {
                            Text(if (isArabic) "الصفحة التالية" else "Next Page")
                        }
                    }
                    Spacer(Modifier.height(100.dp))
                }
            }

            // Audio Player + Auto-Scroll + Single "Choose Reciter" Control Bar
            AnimatedVisibility(visible = !isFullScreen) {
                NoorGlassCard(
                    cornerRadius = 0.dp,
                    contentPadding = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (audioState.errorMessage != null) {
                        Text(
                            text = audioState.errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    // Single Main Button: Choose Reciter + Playback Transport
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FilledTonalButton(
                            onClick = { showReciterSheet = true },
                            modifier = Modifier.testTag("choose_reciter_button")
                        ) {
                            Icon(Icons.Default.Person, contentDescription = strings.chooseReciter, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.chooseReciter, maxLines = 1)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { QuranAudioEngine.previousAyah(context) },
                                modifier = Modifier.testTag("audio_prev_button")
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Ayah")
                            }
                            FilledIconButton(
                                onClick = {
                                    if (audioState.isPlaying || audioState.isPaused) {
                                        QuranAudioEngine.togglePlayPause(context)
                                    } else {
                                        val target = selectedAyah ?: pageAyahs.firstOrNull()
                                        val s = target?.surah ?: (pageSurahs.firstOrNull()?.number ?: 1)
                                        val a = target?.ayah ?: 1
                                        vm.playFromAyah(s, a)
                                    }
                                },
                                modifier = Modifier.testTag("audio_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (audioState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (audioState.isPlaying) "Pause" else "Play"
                                )
                            }
                            IconButton(
                                onClick = { QuranAudioEngine.nextAyah(context) },
                                modifier = Modifier.testTag("audio_next_button")
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next Ayah")
                            }
                            IconButton(
                                onClick = { QuranAudioEngine.stop(context) },
                                modifier = Modifier.testTag("audio_stop_button")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop")
                            }
                        }
                    }

                    // Secondary row: Active Reciter info + Repeat + Speed + Auto-Scroll Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${audioState.reciterName} • ${audioState.currentSurah}:${audioState.currentAyah}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = audioState.repeatAyahCount != 1,
                                onClick = {
                                    val next = when (audioState.repeatAyahCount) {
                                        1 -> 3
                                        3 -> 0
                                        else -> 1
                                    }
                                    QuranAudioEngine.setRepeatAyah(next)
                                },
                                label = {
                                    Text(
                                        when (audioState.repeatAyahCount) {
                                            0 -> "∞"
                                            1 -> "1x"
                                            else -> "${audioState.repeatAyahCount}x"
                                        }
                                    )
                                }
                            )
                            FilterChip(
                                selected = autoScrollRunning,
                                onClick = {
                                    if (!autoScrollRunning) {
                                        autoScrollRunning = true
                                        autoScrollPaused = false
                                    } else {
                                        autoScrollPaused = !autoScrollPaused
                                    }
                                },
                                label = { Text(if (!autoScrollRunning) strings.autoScroll else if (autoScrollPaused) "Resume" else "Pause") },
                                modifier = Modifier.testTag("auto_scroll_toggle")
                            )
                            if (autoScrollRunning) {
                                IconButton(onClick = { autoScrollRunning = false; autoScrollPaused = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Stop Auto-Scroll", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    if (autoScrollRunning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Speed ${"%.1f".format(autoScrollSpeed)}x", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = autoScrollSpeed,
                                onValueChange = { autoScrollSpeed = it },
                                valueRange = 0.4f..3.0f,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Contextual 9-Action Ayah Toolbar (remains visible while Ayah is selected!)
        selectedAyah?.let { ayah ->
            AyahContextualToolbar(
                ayah = ayah,
                strings = strings,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                onPlayFromHere = { vm.playFromAyah(ayah.surah, ayah.ayah) },
                onListenOne = {
                    QuranAudioEngine.setRepeatRange(ayah.ayah, ayah.ayah)
                    vm.playFromAyah(ayah.surah, ayah.ayah)
                },
                onMemorize = { onNavigateMemorize(ayah.surah, ayah.ayah) },
                onFavorite = { vm.toggleBookmark(ayah.page, ayah.surah, ayah.ayah, isFavorite = true) },
                onTafsir = { linguisticTab = 0 },
                onIrab = { linguisticTab = 1 },
                onSarf = { linguisticTab = 2 },
                onCopy = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Quran Ayah", "${ayah.textUthmani} (${ayah.surah}:${ayah.ayah})"))
                    vm.postStatus("Ayah copied")
                },
                onShare = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${ayah.textUthmani}\n— سورة ${QuranCatalog.surahByNumber(ayah.surah)?.nameAr ?: ""} : ${ayah.ayah}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, strings.shareAyah).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                onDismiss = { vm.selectAyah(null) }
            )
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

    if (showIndexSheet) {
        QuranIndexSheet(
            currentPage = currentPage,
            bookmarks = bookmarks,
            history = history,
            strings = strings,
            onJumpToPage = { vm.goToPage(it) },
            onDeleteBookmark = { vm.deleteBookmark(it) },
            onDismiss = { showIndexSheet = false }
        )
    }

    linguisticTab?.let { tabIdx ->
        selectedAyah?.let { ayah ->
            LinguisticSheet(
                ayah = ayah,
                initialTab = tabIdx,
                strings = strings,
                onDismiss = { linguisticTab = null }
            )
        }
    }
}

@Composable
private fun ImportedMushafImagePage(
    imagePath: String,
    page: Int,
    selectedAyah: AyahRecord?,
    liveSurah: Int?,
    liveAyah: Int?,
    liveWordIndex: Int?,
    onSelectAyah: (Int, Int) -> Unit,
    onTapEmpty: () -> Unit
) {
    val bitmap = remember(imagePath) { BitmapFactory.decodeFile(imagePath)?.asImageBitmap() }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    val regions = remember(page) { MushafCoordinateEngine.coordinatesForPage(page) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .onSizeChanged { imageSize = it }
            .pointerInput(page, regions, imageSize) {
                detectTapGestures(
                    onTap = { pos ->
                        if (imageSize.width > 0 && imageSize.height > 0) {
                            val nx = pos.x / imageSize.width
                            val ny = pos.y / imageSize.height
                            val hit = MushafCoordinateEngine.findAyahAtPoint(page, nx, ny)
                            if (hit != null) onSelectAyah(hit.surah, hit.ayah) else onTapEmpty()
                        } else {
                            onTapEmpty()
                        }
                    },
                    onLongPress = { pos ->
                        if (imageSize.width > 0 && imageSize.height > 0) {
                            val nx = pos.x / imageSize.width
                            val ny = pos.y / imageSize.height
                            val hit = MushafCoordinateEngine.findAyahAtPoint(page, nx, ny)
                            if (hit != null) onSelectAyah(hit.surah, hit.ayah)
                        }
                    }
                )
            }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Madinah Mushaf Page $page",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Highlight overlay: ONLY draws text word rects; original Ayah number circles are NEVER covered!
        Canvas(modifier = Modifier.fillMaxSize()) {
            regions.forEach { region ->
                val isSelected = selectedAyah?.surah == region.surah && selectedAyah.ayah == region.ayah
                val isLive = liveSurah == region.surah && liveAyah == region.ayah
                if (isSelected || isLive) {
                    region.wordBoxes.filter { !it.isAyahMarker }.forEach { wb ->
                        val color = when {
                            isLive && liveWordIndex == wb.wordIndex -> Color(0x7034D399)
                            isLive -> LiveRecitationHighlight
                            else -> UserSelectionHighlight
                        }
                        drawRect(
                            color = color,
                            topLeft = Offset(wb.rect.left * size.width, wb.rect.top * size.height),
                            size = Size(
                                (wb.rect.right - wb.rect.left) * size.width,
                                (wb.rect.bottom - wb.rect.top) * size.height
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UthmaniMushafPageCard(
    page: Int,
    pageSurahs: List<Pair<Int, String>>,
    ayahs: List<AyahRecord>,
    selectedAyah: AyahRecord?,
    liveSurah: Int?,
    liveAyah: Int?,
    onSelectAyah: (AyahRecord) -> Unit,
    onTapEmpty: () -> Unit,
    onImportPackClick: () -> Unit,
    isArabic: Boolean
) {
    val glass = LocalNoorGlass.current
    NoorGlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = glass.mushafBorder,
        contentPadding = 16.dp
    ) {
        // Ornamental Surah Header
        pageSurahs.forEach { (num, name) ->
            Surface(
                color = glass.accentGold.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, glass.accentGold.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Text(
                    text = "سُورَةُ $name ($num)",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = AmiriFontFamily),
                    color = glass.accentGold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        if (ayahs.isEmpty()) {
            Text(
                text = if (isArabic)
                    "صفحة $page من مصحف المدينة\n\nالنص العثماني الكامل لهذه الصفحة أو حزمة صور المصحف (604 صفحات) غير مستوردة بعد. اضغط على أيقونة الاستيراد أعلى الشاشة لتحميل حزمة مصحف المدينة الكاملة (PNG + coordinates.json)."
                else
                    "Madinah Mushaf Page $page\n\nFull Uthmani text / 604-page Madinah image pack for this page is not imported yet. Tap the import icon above to load a Madinah Mushaf ZIP pack (PNG + coordinates.json).",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTapEmpty() }
                    .padding(vertical = 28.dp)
            )
            OutlinedButton(
                onClick = onImportPackClick,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isArabic) "استيراد حزمة المصحف (ZIP)" else "Import Mushaf Pack (ZIP)")
            }
        } else {
            // Render Ayah text with exact character-range tags; Ayah rosettes ﴿١﴾ are unhighlighted so markers stay pristine!
            val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
            val annotated = remember(ayahs, selectedAyah, liveSurah, liveAyah) {
                buildAnnotatedString {
                    ayahs.forEach { ayah ->
                        val startIdx = length
                        val isSelected = selectedAyah?.surah == ayah.surah && selectedAyah.ayah == ayah.ayah
                        val isLive = liveSurah == ayah.surah && liveAyah == ayah.ayah
                        val bg = when {
                            isLive -> LiveRecitationHighlight
                            isSelected -> UserSelectionHighlight
                            else -> Color.Transparent
                        }
                        pushStringAnnotation("AYAH", "${ayah.surah}:${ayah.ayah}")
                        pushStyle(SpanStyle(background = bg))
                        append(ayah.textUthmani)
                        pop()
                        pop()
                        // Decorative Ayah circle marker kept OUTSIDE highlight SpanStyle!
                        append(" ﴿${ayah.ayah}﴾ ")
                        if (startIdx >= 0) Unit
                    }
                }
            }

            Text(
                text = annotated,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = AmiriFontFamily,
                    fontSize = 23.sp,
                    lineHeight = 44.sp
                ),
                color = glass.mushafInk,
                textAlign = TextAlign.Justify,
                onTextLayout = { layoutResult.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(annotated, ayahs) {
                        detectTapGestures(
                            onTap = { pos ->
                                val lr = layoutResult.value
                                val offset = lr?.getOffsetForPosition(pos) ?: -1
                                val ann = if (offset in annotated.indices) {
                                    annotated.getStringAnnotations("AYAH", offset, offset).firstOrNull()
                                } else null
                                if (ann != null) {
                                    val parts = ann.item.split(":")
                                    val found = ayahs.firstOrNull { it.surah == parts[0].toInt() && it.ayah == parts[1].toInt() }
                                    if (found != null) onSelectAyah(found) else onTapEmpty()
                                } else {
                                    onTapEmpty()
                                }
                            },
                            onLongPress = { pos ->
                                val lr = layoutResult.value
                                val offset = lr?.getOffsetForPosition(pos) ?: -1
                                val ann = if (offset in annotated.indices) {
                                    annotated.getStringAnnotations("AYAH", offset, offset).firstOrNull()
                                } else null
                                if (ann != null) {
                                    val parts = ann.item.split(":")
                                    ayahs.firstOrNull { it.surah == parts[0].toInt() && it.ayah == parts[1].toInt() }
                                        ?.let(onSelectAyah)
                                }
                            }
                        )
                    }
                    .testTag("mushaf_ayah_text")
            )
        }
    }
}

@Composable
private fun AyahContextualToolbar(
    ayah: AyahRecord,
    strings: com.example.i18n.NoorStrings,
    modifier: Modifier = Modifier,
    onPlayFromHere: () -> Unit,
    onListenOne: () -> Unit,
    onMemorize: () -> Unit,
    onFavorite: () -> Unit,
    onTafsir: () -> Unit,
    onIrab: () -> Unit,
    onSarf: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val surah = QuranCatalog.surahByNumber(ayah.surah)
    NoorGlassCard(
        elevated = true,
        modifier = modifier.fillMaxWidth().testTag("ayah_contextual_menu"),
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${surah?.nameAr ?: "سورة ${ayah.surah}"} • آية ${ayah.ayah}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = strings.close)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionPill(strings.playFromAyah, Icons.Default.PlayArrow, "ctx_play_from", onPlayFromHere)
            ActionPill(strings.listenAyah, Icons.Default.Hearing, "ctx_listen_ayah", onListenOne)
            ActionPill(strings.memorizeAyah, Icons.Default.Mic, "ctx_memorize", onMemorize)
            ActionPill(strings.addFavorite, Icons.Default.FavoriteBorder, "ctx_favorite", onFavorite)
            ActionPill(strings.tafsir, Icons.Default.MenuBook, "ctx_tafsir", onTafsir)
            ActionPill(strings.irab, Icons.Default.AccountTree, "ctx_irab", onIrab)
            ActionPill(strings.sarf, Icons.Default.Spellcheck, "ctx_sarf", onSarf)
            ActionPill(strings.copyAyah, Icons.Default.ContentCopy, "ctx_copy", onCopy)
            ActionPill(strings.shareAyah, Icons.Default.Share, "ctx_share", onShare)
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.testTag(tag)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
