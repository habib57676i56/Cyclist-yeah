package com.example.ui.screens

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
import com.example.data.local.UserRole
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.ui.components.NoorEmptyState
import com.example.ui.components.NoorGlassCard
import com.example.ui.theme.LocalNoorGlass
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.NoorViewModel

@Composable
fun LibraryTab(vm: NoorViewModel) {
    val settings by vm.settings.collectAsState()
    val lessons by vm.lessons.collectAsState()
    val isArabic = settings.language == AppLanguage.AR
    var selectedCategory by remember { mutableStateOf("ALL") }

    val categories = listOf("ALL", "QURAN", "TAFSIR", "HADITH", "TAJWEED", "ARABIC", "MEMORIZATION", "REVISION", "ARTICLE")
    val filtered = remember(lessons, selectedCategory) {
        lessons.filter { it.isPublished && (selectedCategory == "ALL" || it.category == selectedCategory) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                edgePadding = 0.dp
            ) {
                categories.forEach { cat ->
                    Tab(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, text = { Text(cat) })
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                NoorEmptyState(
                    icon = Icons.Default.MenuBook,
                    title = if (isArabic) "لا توجد دروس في هذا التصنيف" else "No Lessons in Category",
                    description = if (isArabic) "يمكن للمدير العام إضافة المواد التعليمية من لوحة التحكم." else "Admins can publish verified lessons in Admin > Quran Content."
                )
            }
        } else {
            items(filtered, key = { it.id }) { lesson ->
                NoorGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isArabic) lesson.titleAr else lesson.titleEn, style = MaterialTheme.typography.titleMedium)
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    if (lesson.isVerifiedReference) (if (isArabic) "مرجع موثق" else "Verified")
                                    else (if (isArabic) "إرشاد عام" else "Guidance"),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(if (isArabic) lesson.contentAr else lesson.contentEn, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Source: ${lesson.referenceSource}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun HalaqatTab(vm: NoorViewModel) {
    val settings by vm.settings.collectAsState()
    val halaqat by vm.halaqat.collectAsState()
    val users by vm.users.collectAsState()
    val attendance by vm.attendance.collectAsState()
    val assessments by vm.assessments.collectAsState()
    val currentUser by vm.currentUser.collectAsState()

    val isArabic = settings.language == AppLanguage.AR
    val glass = LocalNoorGlass.current
    val role = UserRole.fromName(currentUser?.role)

    var reviewTargetId by remember { mutableStateOf<Long?>(null) }
    var reviewNotes by remember { mutableStateOf("") }

    // Weekly student rankings computed from actual attendance & grade records
    val rankings = remember(attendance) {
        attendance.groupBy { it.studentId to it.studentName }.map { (key, list) ->
            val avgGrade = list.map { it.grade }.average().toInt()
            val totalPages = list.sumOf { it.pagesMemorized }
            Triple(key.second, totalPages, avgGrade)
        }.sortedByDescending { it.second * 10 + it.third }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NoorGlassCard(contentPadding = 12.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = currentUser?.name ?: (if (isArabic) "وضع الزائر (طالب)" else "Guest Student View"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Role: ${role.labelEn}", style = MaterialTheme.typography.bodySmall, color = glass.accentGold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { vm.exportHalaqatReport("CSV") { vm.shareFile(it) } },
                            modifier = Modifier.testTag("export_csv_button")
                        ) { Text("CSV") }
                        OutlinedButton(
                            onClick = { vm.exportHalaqatReport("PDF") { vm.shareFile(it) } },
                            modifier = Modifier.testTag("export_pdf_button")
                        ) { Text("PDF") }
                    }
                }
            }
        }

        items(halaqat, key = { it.id }) { h ->
            val studentsInGroup = users.filter { it.role == UserRole.STUDENT.name && it.halaqahId == h.id }
            NoorGlassCard {
                Text(h.name, style = MaterialTheme.typography.titleLarge, color = glass.accentGold)
                Text("Teacher: ${h.teacherName} • ${h.scheduleText}", style = MaterialTheme.typography.bodySmall)
                Text("Target: ${h.memorizationTarget} | Revision: ${h.revisionPlan}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Students (${studentsInGroup.size}):", style = MaterialTheme.typography.labelLarge)
                studentsInGroup.forEach { stu ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stu.name, style = MaterialTheme.typography.bodyMedium)
                        if (role.level >= UserRole.TEACHER.level) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilledTonalButton(onClick = {
                                    vm.recordHalaqahAttendance(h.id, stu, "PRESENT", 2, 95, "Good recitation")
                                }) { Text("Present +2p") }
                            }
                        }
                    }
                }
            }
        }

        // Weekly Ranking
        if (rankings.isNotEmpty()) {
            item {
                NoorGlassCard {
                    Text(if (isArabic) "الترتيب الأسبوعي للحفظ" else "Weekly Memorization Ranking", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    rankings.forEachIndexed { idx, (name, pages, grade) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("#${idx + 1} $name", fontWeight = FontWeight.Medium)
                            Text("$pages pages • Avg $grade%", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Teacher Recitation Assessment Review Queue
        if (role.level >= UserRole.TEACHER.level && assessments.isNotEmpty()) {
            item {
                Text(if (isArabic) "مراجعة تلاوات الطلاب" else "Student Recitation Reviews", style = MaterialTheme.typography.titleMedium)
            }
            items(assessments, key = { it.id }) { a ->
                NoorGlassCard(contentPadding = 12.dp) {
                    Text("${a.studentName} • Surah ${a.surah}:${a.startAyah}-${a.endAyah} (${a.accuracyPercent}%)")
                    Text("Status: ${a.status} | Transcript: ${a.recognizedText.take(60)}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.reviewAssessment(a, "APPROVED", "Approved by teacher") },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) { Text("Approve") }
                        OutlinedButton(
                            onClick = { reviewTargetId = a.id; reviewNotes = a.teacherNotes }
                        ) { Text("Note / Revise") }
                    }
                    if (reviewTargetId == a.id) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = reviewNotes, onValueChange = { reviewNotes = it }, label = { Text("Teacher Note") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            vm.reviewAssessment(a, "NEEDS_REVISION", reviewNotes)
                            reviewTargetId = null
                        }) { Text("Send Revision Note") }
                    }
                }
            }
        }
    }
}

@Composable
fun WorshipLibraryScreen(vm: NoorViewModel) {
    val settings by vm.settings.collectAsState()
    val featureToggles by vm.featureToggles.collectAsState()
    val strings = stringsFor(settings.language)
    val toggleMap = featureToggles.associate { it.key to it.enabled }

    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(strings.prayerTimes) }, modifier = Modifier.testTag("worship_tab_prayer"))
            if (toggleMap["feature_khatmah_fasting"] != false) {
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("${strings.khatmah} & ${strings.fasting}") }, modifier = Modifier.testTag("worship_tab_khatmah"))
            }
            if (toggleMap["feature_library"] != false) {
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(strings.library) }, modifier = Modifier.testTag("worship_tab_library"))
            }
            if (toggleMap["feature_halaqat"] != false) {
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text(strings.halaqat) }, modifier = Modifier.testTag("worship_tab_halaqat"))
            }
        }

        when (selectedTab) {
            0 -> PrayerAdhanTab(vm)
            1 -> KhatmahFastingTab(vm)
            2 -> LibraryTab(vm)
            else -> HalaqatTab(vm)
        }
    }
}
