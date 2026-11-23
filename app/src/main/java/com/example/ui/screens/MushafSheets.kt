package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.BookmarkEntity
import com.example.data.local.ReadingHistoryEntity
import com.example.data.local.ReciterConfigEntity
import com.example.i18n.NoorStrings
import com.example.quran.AyahRecord
import com.example.quran.QuranCatalog
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.LocalNoorGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciterPickerSheet(
    reciters: List<ReciterConfigEntity>,
    selectedReciterId: String,
    strings: NoorStrings,
    onSelect: (ReciterConfigEntity) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(strings.chooseReciter, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            reciters.forEach { rec ->
                val isSelected = rec.id == selectedReciterId
                ListItem(
                    headlineContent = { Text(rec.nameAr, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    supportingContent = { Text(rec.nameEn) },
                    leadingContent = {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RecordVoiceOver,
                            contentDescription = rec.nameEn,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .clickable {
                            onSelect(rec)
                            onDismiss()
                        }
                        .testTag("reciter_option_${rec.id}")
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinguisticSheet(
    ayah: AyahRecord,
    initialTab: Int, // 0 = Tafsir, 1 = I'rab, 2 = Sarf
    strings: NoorStrings,
    onDismiss: () -> Unit
) {
    var tab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val surah = QuranCatalog.surahByNumber(ayah.surah)
    val ling = ayah.linguistics
    val glass = LocalNoorGlass.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "${surah?.nameAr ?: "سورة"} • آية ${ayah.ayah}",
                style = MaterialTheme.typography.titleLarge,
                color = glass.accentGold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = ayah.textUthmani,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = AmiriFontFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(strings.tafsir) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(strings.irab) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(strings.sarf) })
            }
            Spacer(Modifier.height(12.dp))
            if (ling == null) {
                Text(
                    text = "Reference Tafsir / I'rab / Sarf data is not loaded for this specific Ayah yet. Import a verified linguistic JSON pack in Admin > Quran Content Control.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val content = when (tab) {
                    0 -> "${ling.tafsir}\n\n[Reference: ${ling.tafsirRef}]"
                    1 -> ling.irab
                    else -> ling.sarf
                }
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranIndexSheet(
    currentPage: Int,
    bookmarks: List<BookmarkEntity>,
    history: List<ReadingHistoryEntity>,
    strings: NoorStrings,
    onJumpToPage: (Int) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var pageInput by remember { mutableStateOf(currentPage.toString()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 16.dp)
        ) {
            // Direct Page Jump Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it.filter { c -> c.isDigit() } },
                    label = { Text("${strings.pageLabel} (1-604)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("page_jump_input")
                )
                Button(
                    onClick = {
                        val p = pageInput.toIntOrNull()?.coerceIn(1, 604) ?: currentPage
                        onJumpToPage(p)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("page_jump_button")
                ) {
                    Text("Go")
                }
            }
            Spacer(Modifier.height(8.dp))

            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(strings.surahIndex) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(strings.juzIndex) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(strings.hizbIndex) })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text(strings.search) })
                Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text(strings.bookmarks) })
                Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 }, text = { Text(strings.history) })
            }
            Spacer(Modifier.height(8.dp))

            when (selectedTab) {
                0 -> {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(strings.search) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    val filtered = remember(searchQuery) {
                        if (searchQuery.isBlank()) QuranCatalog.surahs
                        else QuranCatalog.surahs.filter {
                            it.nameAr.contains(searchQuery, true) ||
                                it.nameEn.contains(searchQuery, true) ||
                                it.number.toString() == searchQuery.trim()
                        }
                    }
                    LazyColumn {
                        items(filtered, key = { it.number }) { s ->
                            ListItem(
                                headlineContent = { Text("${s.number}. ${s.nameAr} (${s.nameEn})") },
                                supportingContent = { Text("${s.ayahCount} Ayahs • ${if (s.isMakki) "Makki" else "Madani"}") },
                                trailingContent = { Text("${strings.pageLabel} ${s.startPage}") },
                                modifier = Modifier.clickable {
                                    onJumpToPage(s.startPage)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
                1 -> LazyColumn {
                    items(QuranCatalog.juzList, key = { it.number }) { j ->
                        ListItem(
                            headlineContent = { Text("${j.number}. ${j.nameAr}") },
                            supportingContent = { Text("${QuranCatalog.surahByNumber(j.startSurah)?.nameAr ?: ""} : ${j.startAyah}") },
                            trailingContent = { Text("${strings.pageLabel} ${j.startPage}") },
                            modifier = Modifier.clickable {
                                onJumpToPage(j.startPage)
                                onDismiss()
                            }
                        )
                    }
                }
                2 -> LazyColumn {
                    items(QuranCatalog.hizbQuarters) { h ->
                        ListItem(
                            headlineContent = { Text("حزب ${h.hizb} • الربع ${h.quarter}") },
                            trailingContent = { Text("${strings.pageLabel} ${h.page}") },
                            modifier = Modifier.clickable {
                                onJumpToPage(h.page)
                                onDismiss()
                            }
                        )
                    }
                }
                3 -> {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search loaded Quran text...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    val matches = remember(searchQuery) { QuranCatalog.searchAyahs(searchQuery) }
                    LazyColumn {
                        items(matches, key = { "${it.surah}:${it.ayah}" }) { a ->
                            val sName = QuranCatalog.surahByNumber(a.surah)?.nameAr ?: ""
                            ListItem(
                                headlineContent = { Text(a.textUthmani, fontFamily = AmiriFontFamily, maxLines = 2) },
                                supportingContent = { Text("$sName (${a.surah}:${a.ayah}) • ${strings.pageLabel} ${a.page}") },
                                modifier = Modifier.clickable {
                                    onJumpToPage(a.page)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
                4 -> LazyColumn {
                    items(bookmarks, key = { it.id }) { b ->
                        ListItem(
                            headlineContent = { Text(b.label) },
                            supportingContent = { Text("${strings.pageLabel} ${b.page} • ${if (b.isFavorite) "Favorite" else "Bookmark"}") },
                            leadingContent = {
                                Icon(
                                    if (b.isFavorite) Icons.Default.Favorite else Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onDeleteBookmark(b) }) {
                                    Icon(Icons.Default.Delete, contentDescription = strings.deleteLabel)
                                }
                            },
                            modifier = Modifier.clickable {
                                onJumpToPage(b.page)
                                onDismiss()
                            }
                        )
                    }
                }
                5 -> LazyColumn {
                    items(history, key = { it.id }) { h ->
                        val sName = QuranCatalog.surahByNumber(h.surah)?.nameAr ?: ""
                        ListItem(
                            headlineContent = { Text("$sName • ${strings.pageLabel} ${h.page}") },
                            supportingContent = { Text("Ayah ${h.ayah}") },
                            modifier = Modifier.clickable {
                                onJumpToPage(h.page)
                                onDismiss()
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
