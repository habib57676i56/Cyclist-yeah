package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.AdhkarCatalog
import com.example.firebase.*
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.quran.QuranCatalog
import com.example.ui.components.NoorEmptyState
import com.example.ui.components.NoorGlassCard
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LocalNoorGlass
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.NoorViewModel

@Composable
fun ProfileHubScreen(
    vm: NoorViewModel,
    selectedSection: Int,
    onSectionChange: (Int) -> Unit,
    onNavigateQuranPage: (Int) -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val fsProfile by vm.fsUserProfile.collectAsState()
    val fsProgress by vm.fsUserProgress.collectAsState()
    val isBackendAdmin by vm.isBackendAdmin.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val fsNotifications by vm.fsNotifications.collectAsState()

    val isArabic = settings.language == AppLanguage.AR
    val strings = stringsFor(settings.language)
    val glass = LocalNoorGlass.current
    val unreadNotifCount = fsNotifications.count { !it.read }

    val sections = remember(isArabic, isBackendAdmin) {
        buildList {
            add(0 to if (isArabic) "العبادات والأذكار" else "Worship & Adhkar")
            add(1 to if (isArabic) "المحفوظات" else "Saved")
            add(2 to if (isArabic) "الإشعارات" else "Notifications")
            add(3 to strings.settings)
            if (isBackendAdmin) {
                add(4 to if (isArabic) "لوحة الإدارة" else "Admin Dashboard")
            }
        }
    }
    val safeSelected = if (!isBackendAdmin && selectedSection == 4) 0 else selectedSection.coerceIn(0, sections.lastIndex)

    Column(modifier = Modifier.fillMaxSize()) {
        // User Identity & Sync Status Banner
        NoorGlassCard(
            cornerRadius = 0.dp,
            contentPadding = 12.dp,
            borderColor = glass.accentGold.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val photoUrl = fsProfile?.photoUrl
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Profile Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, glass.accentGold, CircleShape)
                        )
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AccountCircle, contentDescription = "User", tint = glass.accentGold)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fsProfile?.displayName?.ifBlank { null } ?: if (isArabic) "حساب نور" else "NOOR Reader",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = fsProfile?.email ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = syncStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (isBackendAdmin) {
                                Surface(
                                    color = glass.accentGold.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "ADMIN VERIFIED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = glass.accentGold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        vm.signOutWithCleanup(context) { onSignedOut() }
                    },
                    modifier = Modifier.testTag("profile_sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isArabic) "خروج" else "Sign Out")
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = sections.indexOfFirst { it.first == safeSelected }.coerceAtLeast(0),
            edgePadding = 10.dp
        ) {
            sections.forEach { (secId, label) ->
                val badge = if (secId == 2 && unreadNotifCount > 0) " ($unreadNotifCount)" else ""
                Tab(
                    selected = safeSelected == secId,
                    onClick = { onSectionChange(secId) },
                    text = { Text("$label$badge") },
                    modifier = Modifier.testTag("profile_tab_$secId")
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (safeSelected) {
                0 -> WorshipAdhkarCombinedTab(vm)
                1 -> SavedBookmarksNotesTab(vm, onNavigateQuranPage)
                2 -> NotificationsCenterTab(vm)
                3 -> ProfileSettingsTab(vm, fsProgress, onSignedOut)
                else -> if (isBackendAdmin) CloudAdminDashboardTab(vm) else WorshipAdhkarCombinedTab(vm)
            }
        }
    }
}

@Composable
private fun WorshipAdhkarCombinedTab(vm: NoorViewModel) {
    val settings by vm.settings.collectAsState()
    val featureToggles by vm.featureToggles.collectAsState()
    val strings = stringsFor(settings.language)
    val isArabic = settings.language == AppLanguage.AR
    val toggleMap = featureToggles.associate { it.key to it.enabled }

    var subTab by remember { mutableIntStateOf(0) } // 0: Prayer, 1: Khatmah & Fasting, 2: Adhkar, 3: Library, 4: Halaqat

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = subTab, edgePadding = 12.dp) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text(strings.prayerTimes) }, modifier = Modifier.testTag("worship_tab_prayer"))
            if (toggleMap["feature_khatmah_fasting"] != false) {
                Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("${strings.khatmah} & ${strings.fasting}") }, modifier = Modifier.testTag("worship_tab_khatmah"))
            }
            Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text(if (isArabic) "الأذكار اليومية" else "Adhkar") }, modifier = Modifier.testTag("worship_tab_adhkar"))
            if (toggleMap["feature_library"] != false) {
                Tab(selected = subTab == 3, onClick = { subTab = 3 }, text = { Text(strings.library) }, modifier = Modifier.testTag("worship_tab_library"))
            }
            if (toggleMap["feature_halaqat"] != false) {
                Tab(selected = subTab == 4, onClick = { subTab = 4 }, text = { Text(strings.halaqat) }, modifier = Modifier.testTag("worship_tab_halaqat"))
            }
        }
        when (subTab) {
            0 -> PrayerAdhanTab(vm)
            1 -> KhatmahFastingTab(vm)
            2 -> DailyAdhkarTab(isArabic)
            3 -> LibraryTab(vm)
            else -> HalaqatTab(vm)
        }
    }
}

@Composable
private fun DailyAdhkarTab(isArabic: Boolean) {
    var category by remember { mutableStateOf("MORNING") }
    val counts = remember { mutableStateMapOf<String, Int>() }
    val glass = LocalNoorGlass.current
    val itemsList = remember(category) { AdhkarCatalog.items.filter { it.category == category } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScrollableTabRow(selectedTabIndex = listOf("MORNING", "EVENING", "AFTER_PRAYER", "SLEEP").indexOf(category).coerceAtLeast(0), edgePadding = 0.dp) {
                listOf(
                    "MORNING" to (if (isArabic) "أذكار الصباح" else "Morning"),
                    "EVENING" to (if (isArabic) "أذكار المساء" else "Evening"),
                    "AFTER_PRAYER" to (if (isArabic) "بعد الصلاة" else "After Prayer"),
                    "SLEEP" to (if (isArabic) "أذكار النوم" else "Sleep")
                ).forEach { (key, title) ->
                    Tab(selected = category == key, onClick = { category = key }, text = { Text(title) })
                }
            }
        }
        items(itemsList, key = { it.id }) { item ->
            val current = counts[item.id] ?: 0
            val done = current >= item.targetCount
            NoorGlassCard(borderColor = if (done) SuccessGreen.copy(alpha = 0.6f) else glass.glassBorder) {
                Text(
                    text = item.arabicText,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = AmiriFontFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isArabic) {
                    Spacer(Modifier.height(4.dp))
                    Text(item.translationEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.reference, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    FilledTonalButton(
                        onClick = { if (current < item.targetCount) counts[item.id] = current + 1 },
                        modifier = Modifier.testTag("dhikr_count_${item.id}")
                    ) {
                        Text("$current / ${item.targetCount}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedBookmarksNotesTab(vm: NoorViewModel, onNavigateQuranPage: (Int) -> Unit) {
    val settings by vm.settings.collectAsState()
    val fsBookmarks by vm.fsBookmarks.collectAsState()
    val localBookmarks by vm.bookmarks.collectAsState()
    val history by vm.readingHistory.collectAsState()
    val isArabic = settings.language == AppLanguage.AR

    var searchQuery by remember { mutableStateOf("") }
    var onlyFavorites by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<FsBookmarkRecord?>(null) }

    val filtered = remember(fsBookmarks, searchQuery, onlyFavorites) {
        fsBookmarks.filter { bm ->
            val surahName = QuranCatalog.surahByNumber(bm.surah.toInt())?.let { "${it.nameAr} ${it.nameEn}" } ?: ""
            val matchesFav = !onlyFavorites || bm.favorite
            val matchesQuery = searchQuery.isBlank() ||
                surahName.contains(searchQuery, ignoreCase = true) ||
                (bm.note ?: "").contains(searchQuery, ignoreCase = true) ||
                bm.page.toString().contains(searchQuery)
            matchesFav && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(if (isArabic) "ابحث في العلامات والملاحظات" else "Search bookmarks & notes") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = onlyFavorites,
                    onClick = { onlyFavorites = !onlyFavorites },
                    label = { Text(if (isArabic) "المفضلة" else "Favorites") }
                )
            }
        }

        if (filtered.isEmpty() && localBookmarks.isEmpty()) {
            item {
                NoorEmptyState(
                    icon = Icons.Default.BookmarkBorder,
                    title = if (isArabic) "لا توجد علامات محفوظة" else "No Bookmarks Yet",
                    description = if (isArabic) "اضغط مطولاً على أي آية أو احفظ الصفحة من شاشة المصحف." else "Save pages or Ayah notes directly from the Mushaf reader."
                )
            }
        } else {
            items(filtered, key = { it.id }) { bm ->
                val surah = QuranCatalog.surahByNumber(bm.surah.toInt())
                NoorGlassCard(
                    modifier = Modifier.fillMaxWidth().clickable {
                        vm.goToPage(bm.page.toInt(), recordHistory = false)
                        onNavigateQuranPage(bm.page.toInt())
                    },
                    contentPadding = 12.dp
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${surah?.nameAr ?: "سورة ${bm.surah}"} : ${bm.ayah}", style = MaterialTheme.typography.titleMedium)
                            Text("${if (isArabic) "صفحة" else "Page"} ${bm.page}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!bm.note.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(bm.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Row {
                            IconButton(onClick = { editingBookmark = bm }) {
                                Icon(Icons.Default.EditNote, contentDescription = "Edit Note")
                            }
                            IconButton(onClick = {
                                vm.toggleBookmark(bm.page.toInt(), bm.surah.toInt(), bm.ayah.toInt(), !bm.favorite, bm.note)
                            }) {
                                Icon(
                                    imageVector = if (bm.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (bm.favorite) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                Text(if (isArabic) "سجل القراءة الأخير" else "Recent Reading History", style = MaterialTheme.typography.titleMedium)
            }
            items(history.take(10), key = { it.id }) { h ->
                val surah = QuranCatalog.surahByNumber(h.surah)
                NoorGlassCard(
                    modifier = Modifier.fillMaxWidth().clickable {
                        vm.goToPage(h.page, recordHistory = false)
                        onNavigateQuranPage(h.page)
                    },
                    contentPadding = 10.dp
                ) {
                    Text("${surah?.nameAr ?: "سورة ${h.surah}"} • Page ${h.page}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    editingBookmark?.let { target ->
        var noteText by remember(target) { mutableStateOf(target.note ?: "") }
        AlertDialog(
            onDismissRequest = { editingBookmark = null },
            title = { Text(if (isArabic) "ملاحظة شخصية للآية" else "Personal Ayah Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(if (isArabic) "اكتب تأملك أو ملاحظتك" else "Your reflection or note") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.toggleBookmark(target.page.toInt(), target.surah.toInt(), target.ayah.toInt(), target.favorite, noteText)
                    editingBookmark = null
                }) { Text(if (isArabic) "حفظ" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingBookmark = null }) { Text(if (isArabic) "إلغاء" else "Cancel") }
            }
        )
    }
}

@Composable
private fun NotificationsCenterTab(vm: NoorViewModel) {
    val settings by vm.settings.collectAsState()
    val fsNotifs by vm.fsNotifications.collectAsState()
    val fsAnnouncements by vm.fsAnnouncements.collectAsState()
    val isArabic = settings.language == AppLanguage.AR

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isArabic) "مركز الإشعارات والتنبيهات" else "Notification History & Alerts", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = {
                    vm.sendDailyTestNotification(
                        title = if (isArabic) "تذكير بالورد اليومي" else "Daily Quran Wird Reminder",
                        body = if (isArabic) "حافظ على قراءة وردك اليومي من كتاب الله." else "Take a few peaceful minutes for your daily Quran reading.",
                        category = "QURAN"
                    )
                }) {
                    Text(if (isArabic) "إرسال تذكير" else "Log Reminder")
                }
            }
        }

        if (fsAnnouncements.isNotEmpty()) {
            item {
                Text(if (isArabic) "إعلانات الإدارة السحابية" else "Cloud Announcements", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            items(fsAnnouncements.filter { it.active }, key = { it.id }) { ann ->
                NoorGlassCard(contentPadding = 12.dp) {
                    Text(ann.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(ann.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (fsNotifs.isEmpty()) {
            item {
                NoorEmptyState(
                    icon = Icons.Default.NotificationsNone,
                    title = if (isArabic) "لا توجد إشعارات شخصية" else "No Notifications Yet",
                    description = if (isArabic) "ستظهر هنا تنبيهات الصلاة والختمة ومراجعة الحفظ." else "Prayer, Quran reading, and memorization revision alerts appear here."
                )
            }
        } else {
            items(fsNotifs, key = { it.id }) { notif ->
                NoorGlassCard(
                    elevated = !notif.read,
                    contentPadding = 12.dp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        vm.markCloudNotificationRead(notif.id, !notif.read)
                    }
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(notif.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (notif.read) FontWeight.Normal else FontWeight.Bold)
                            Text(notif.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AssistChip(onClick = { vm.markCloudNotificationRead(notif.id, !notif.read) }, label = { Text(if (notif.read) "Read" else "New") })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSettingsTab(
    vm: NoorViewModel,
    fsProgress: FsUserProgress?,
    onSignedOut: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val mushafCount by vm.mushafPackPagesCount.collectAsState()
    val isArabic = settings.language == AppLanguage.AR

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var goalPages by remember(fsProgress) { mutableIntStateOf((fsProgress?.dailyGoalPages ?: 4L).toInt()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NoorGlassCard {
                Text(if (isArabic) "تفضيلات القراءة والمظهر" else "Reading & Appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isArabic) "الوضع الليلي" else "Dark Mode")
                    Switch(checked = settings.darkMode, onCheckedChange = { vm.setDarkMode(it) })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { vm.setLanguage(lang) },
                            label = { Text(lang.displayName) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (isArabic) "الهدف اليومي للقراءة: $goalPages صفحات" else "Daily Quran Reading Goal: $goalPages pages",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = goalPages.toFloat(),
                    onValueChange = { goalPages = it.toInt().coerceIn(1, 30) },
                    onValueChangeFinished = { vm.setDailyGoalPages(goalPages) },
                    valueRange = 1f..30f
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isArabic) "بث التلاوات عبر الإنترنت" else "Online Audio Streaming", modifier = Modifier.weight(1f))
                    Switch(checked = settings.allowOnlineAudioStreaming, onCheckedChange = { vm.setOnlineStreaming(it) })
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isArabic) "صفحات المصحف المستوردة محلياً: $mushafCount / 604" else "Offline Mushaf Images Installed: $mushafCount / 604",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            NoorGlassCard {
                Text(if (isArabic) "الخصوصية وإدارة الحساب" else "Privacy & Account Management", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showPrivacy = true }, modifier = Modifier.weight(1f)) {
                        Text(if (isArabic) "سياسة الخصوصية" else "Privacy Policy")
                    }
                    OutlinedButton(onClick = { showTerms = true }, modifier = Modifier.weight(1f)) {
                        Text(if (isArabic) "شروط الاستخدام" else "Terms of Use")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    modifier = Modifier.fillMaxWidth().testTag("delete_account_button")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isArabic) "حذف الحساب وجميع البيانات" else "Delete Account & All Personal Data")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (isArabic) "تأكيد حذف الحساب" else "Confirm Account Deletion") },
            text = {
                Text(
                    if (isArabic) "سيتم حذف ملفك الشخصي، العلامات، نتائج الاختبارات وخططك السحابية نهائياً. هل أنت متأكد؟"
                    else "This permanently deletes your Firestore profile, bookmarks, test history, and worship plans. Continue?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        vm.deleteAccountAndPersonalData { ok, msg ->
                            vm.postStatus(msg)
                            if (ok) onSignedOut()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text(if (isArabic) "حذف نهائي" else "Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(if (isArabic) "إلغاء" else "Cancel") }
            }
        )
    }

    if (showPrivacy) PrivacyPolicyDialog(isArabic = isArabic, onDismiss = { showPrivacy = false })
    if (showTerms) TermsOfUseDialog(isArabic = isArabic, onDismiss = { showTerms = false })
}

@Composable
private fun CloudAdminDashboardTab(vm: NoorViewModel) {
    val fsUsers by vm.fsAllUsers.collectAsState()
    val fsKnowledge by vm.fsKnowledgeSources.collectAsState()
    val fsFeedback by vm.fsFeedback.collectAsState()
    val fsAuditLogs by vm.fsAuditLogs.collectAsState()
    val fsAnnouncements by vm.fsAnnouncements.collectAsState()

    var adminSubTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = adminSubTab, edgePadding = 12.dp) {
            Tab(selected = adminSubTab == 0, onClick = { adminSubTab = 0 }, text = { Text("Analytics & Audit") })
            Tab(selected = adminSubTab == 1, onClick = { adminSubTab = 1 }, text = { Text("Users (${fsUsers.size})") })
            Tab(selected = adminSubTab == 2, onClick = { adminSubTab = 2 }, text = { Text("AI Sources (${fsKnowledge.size})") })
            Tab(selected = adminSubTab == 3, onClick = { adminSubTab = 3 }, text = { Text("Feedback (${fsFeedback.count { !it.reviewed }})") })
            Tab(selected = adminSubTab == 4, onClick = { adminSubTab = 4 }, text = { Text("Local Tools & Halaqat") })
        }

        if (adminSubTab == 4) {
            SuperAdminScreen(vm)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (adminSubTab) {
                    0 -> {
                        item {
                            NoorGlassCard(elevated = true) {
                                Text("Cloud Analytics Overview", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                Text("Registered Users: ${fsUsers.size}")
                                Text("Approved Cloud Knowledge Sources: ${fsKnowledge.count { it.approvalStatus == "APPROVED" }}")
                                Text("Active Cloud Announcements: ${fsAnnouncements.count { it.active }}")
                                Text("Unreviewed AI Feedback / Unanswered: ${fsFeedback.count { !it.reviewed }}")
                            }
                        }
                        item {
                            var annTitle by remember { mutableStateOf("") }
                            var annMsg by remember { mutableStateOf("") }
                            NoorGlassCard {
                                Text("Publish Cloud Announcement", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(value = annTitle, onValueChange = { annTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = annMsg, onValueChange = { annMsg = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        if (annTitle.isNotBlank() && annMsg.isNotBlank()) {
                                            vm.publishCloudAnnouncement(annTitle, annMsg, "ALL")
                                            annTitle = ""; annMsg = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Publish to All Users") }
                            }
                        }
                        item {
                            NoorGlassCard {
                                Text("Cloud Admin Audit Logs (${fsAuditLogs.size})", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                fsAuditLogs.take(15).forEach { log ->
                                    Text("• [${log.actorEmail}] ${log.action}: ${log.details}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    1 -> {
                        items(fsUsers, key = { it.id }) { u ->
                            NoorGlassCard(contentPadding = 12.dp) {
                                Text("${u.displayName} (${u.email})", style = MaterialTheme.typography.titleSmall)
                                Text("Role: ${u.role} • Status: ${u.accountStatus}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("STUDENT", "TEACHER", "ADMIN").forEach { r ->
                                        FilterChip(
                                            selected = u.role == r,
                                            onClick = { vm.updateCloudUserRoleAndStatus(u.userId.ifBlank { u.id }, u.email, r, u.accountStatus) },
                                            label = { Text(r) }
                                        )
                                    }
                                    FilterChip(
                                        selected = u.accountStatus == "SUSPENDED",
                                        onClick = {
                                            val nextStatus = if (u.accountStatus == "SUSPENDED") "ACTIVE" else "SUSPENDED"
                                            vm.updateCloudUserRoleAndStatus(u.userId.ifBlank { u.id }, u.email, u.role, nextStatus)
                                        },
                                        label = { Text(if (u.accountStatus == "SUSPENDED") "Activate" else "Suspend") }
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        item {
                            var kTitle by remember { mutableStateOf("") }
                            var kCat by remember { mutableStateOf("TAJWID") }
                            var kAns by remember { mutableStateOf("") }
                            var kRef by remember { mutableStateOf("") }
                            NoorGlassCard {
                                Text("Add Approved Knowledge Source (Cloud RAG)", style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(value = kTitle, onValueChange = { kTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = kCat, onValueChange = { kCat = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = kAns, onValueChange = { kAns = it }, label = { Text("Verified Passage") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = kRef, onValueChange = { kRef = it }, label = { Text("Scholarly Reference") }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        if (kTitle.isNotBlank() && kAns.isNotBlank()) {
                                            vm.upsertCloudKnowledgeSource(null, kTitle, kCat, null, kAns, kTitle, kRef.ifBlank { "Verified Reference" }, "APPROVED")
                                            kTitle = ""; kAns = ""; kRef = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Save Approved Cloud Source") }
                            }
                        }
                        items(fsKnowledge, key = { it.id }) { src ->
                            NoorGlassCard(contentPadding = 12.dp) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${src.title} [${src.approvalStatus}]", style = MaterialTheme.typography.titleSmall)
                                        Text(src.reference, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        Text(src.answer.take(140), style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { vm.deleteCloudKnowledgeSource(src.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Source")
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        if (fsFeedback.isEmpty()) {
                            item {
                                NoorEmptyState(
                                    icon = Icons.Default.ThumbUp,
                                    title = "No AI Feedback Yet",
                                    description = "User feedback and unanswered Gemini queries appear here for moderation."
                                )
                            }
                        } else {
                            items(fsFeedback, key = { it.id }) { fb ->
                                NoorGlassCard(contentPadding = 12.dp) {
                                    Text(fb.question, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "Helpful: ${fb.helpful} • Unanswered: ${fb.unanswered} • Reviewed: ${fb.reviewed}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(fb.aiResponse.take(200), style = MaterialTheme.typography.bodySmall)
                                    if (!fb.reviewed) {
                                        Spacer(Modifier.height(6.dp))
                                        FilledTonalButton(onClick = { vm.markCloudFeedbackReviewed(fb.id) }) {
                                            Text("Mark Reviewed")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
