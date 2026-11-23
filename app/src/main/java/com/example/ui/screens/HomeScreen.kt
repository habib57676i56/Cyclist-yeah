package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.AdhkarCatalog
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.prayer.PrayerTimesCalculator
import com.example.quran.QuranCatalog
import com.example.ui.components.NoorGlassCard
import com.example.ui.components.NoorSectionHeader
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.LocalNoorGlass
import com.example.viewmodel.NoorViewModel
import java.util.Calendar

@Composable
fun HomeScreen(
    vm: NoorViewModel,
    onNavigateRoute: (String) -> Unit
) {
    val settings by vm.settings.collectAsState()
    val prayerTimes by vm.dailyPrayerTimes.collectAsState()
    val khatmahs by vm.khatmahs.collectAsState()
    val fastingPlans by vm.fastingPlans.collectAsState()
    val promoCards by vm.promoCards.collectAsState()
    val announcements by vm.announcements.collectAsState()
    val featureToggles by vm.featureToggles.collectAsState()
    val fsProfile by vm.fsUserProfile.collectAsState()
    val fsProgress by vm.fsUserProgress.collectAsState()
    val fsTestResults by vm.fsTestResults.collectAsState()
    val fsNotifications by vm.fsNotifications.collectAsState()
    val isBackendAdmin by vm.isBackendAdmin.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()

    val strings = stringsFor(settings.language)
    val isArabic = settings.language == AppLanguage.AR
    val glass = LocalNoorGlass.current
    val toggleMap = featureToggles.associate { it.key to it.enabled }

    val nowCal = Calendar.getInstance()
    val nowMin = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
    val (nextPrayerName, minsLeft) = prayerTimes.nextPrayer(nowMin)
    val city = PrayerTimesCalculator.cityById(settings.cityId)
    val lastSurah = QuranCatalog.surahByNumber(settings.lastSurah)
    val unreadNotifications = fsNotifications.count { !it.read }
    val dailyGoal = (fsProgress?.dailyGoalPages ?: 4L).toInt().coerceAtLeast(1)
    val dailyRead = (fsProgress?.dailyReadPages ?: 0L).toInt().coerceAtLeast(0)
    val goalProgress = (dailyRead.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)
    val dailyDhikr = remember { AdhkarCatalog.items.first() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Row: Greeting + Theme + Language + Notifications
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isArabic) "نور القرآن" else "NOOR",
                        style = MaterialTheme.typography.displaySmall,
                        color = glass.accentGold
                    )
                    val userName = fsProfile?.displayName?.takeIf { it.isNotBlank() }
                    if (userName != null) {
                        Text(
                            text = if (isArabic) "أهلاً بك، $userName" else "Assalamu Alaikum, $userName",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = syncStatus,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = syncStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BadgedBox(
                        badge = {
                            if (unreadNotifications > 0) {
                                Badge { Text(unreadNotifications.toString()) }
                            }
                        }
                    ) {
                        FilledTonalIconButton(
                            onClick = { onNavigateRoute("profile_notifications") },
                            modifier = Modifier.testTag("home_notifications_button")
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    }
                    FilledTonalIconButton(
                        onClick = { vm.setDarkMode(!settings.darkMode) },
                        modifier = Modifier.testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (settings.darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (settings.darkMode) strings.lightMode else strings.darkMode
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            val next = when (settings.language) {
                                AppLanguage.AR -> AppLanguage.EN
                                AppLanguage.EN -> AppLanguage.UZ
                                AppLanguage.UZ -> AppLanguage.AR
                            }
                            vm.setLanguage(next)
                        },
                        modifier = Modifier.testTag("toggle_language_button")
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = strings.languageLabel, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(settings.language.code.uppercase())
                    }
                }
            }
        }

        // Hero Card: Next Prayer + Continue Reading + Daily Goal Progress
        item {
            NoorGlassCard(elevated = true, borderColor = glass.accentGold.copy(alpha = 0.55f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.nextPrayer,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$nextPrayerName • ${minsLeft / 60}h ${minsLeft % 60}m",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isArabic) city.nameAr else city.nameEn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { onNavigateRoute("profile_worship") },
                        modifier = Modifier.testTag("home_prayer_button")
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = strings.prayerTimes, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.prayerTimes)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = glass.glassBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.continueReading,
                            style = MaterialTheme.typography.labelMedium,
                            color = glass.accentGold
                        )
                        Text(
                            text = "${lastSurah?.nameAr ?: "الفاتحة"} • ${strings.pageLabel} ${settings.lastPage}",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = AmiriFontFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = QuranCatalog.juzForPage(settings.lastPage).nameAr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            vm.goToPage(settings.lastPage, recordHistory = false)
                            onNavigateRoute("quran")
                        },
                        modifier = Modifier.testTag("home_continue_reading_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = strings.openQuran, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.openQuran)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isArabic) "الهدف اليومي للقراءة" else "Daily Quran Reading Goal",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "$dailyRead / $dailyGoal ${strings.pageLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { goalProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp))
                )
            }
        }

        // Daily Dhikr & Memorization Snapshot
        item {
            NoorGlassCard(
                modifier = Modifier.fillMaxWidth().clickable { onNavigateRoute("profile_worship") },
                contentPadding = 14.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isArabic) "ذكر اليوم المأثور" else "Daily Dhikr Reminder",
                        style = MaterialTheme.typography.labelLarge,
                        color = glass.accentGold
                    )
                    Text(
                        text = dailyDhikr.reference,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = dailyDhikr.arabicText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = AmiriFontFamily),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Quick Worship & Memorization Summary
        if (toggleMap["feature_khatmah_fasting"] != false) {
            item {
                val activeKhatmah = khatmahs.firstOrNull { it.isActive }
                val activeFasting = fastingPlans.firstOrNull()
                val avgTestScore = fsTestResults.takeIf { it.isNotEmpty() }?.map { it.scorePercent }?.average()?.toInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NoorGlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateRoute("profile_worship") }
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = strings.khatmah, tint = glass.accentGold)
                        Spacer(Modifier.height(6.dp))
                        Text(strings.khatmah, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (activeKhatmah != null) "${activeKhatmah.completedPages}/604 ${strings.pageLabel}"
                            else if (isArabic) "ابدأ ختمة جديدة" else "Start a Khatmah",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    NoorGlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateRoute("memorize") }
                    ) {
                        Icon(Icons.Default.Quiz, contentDescription = strings.memorization, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Text(if (isArabic) "ملخص الحفظ" else "Memorization", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = when {
                                avgTestScore != null -> "${fsTestResults.size} tests • Avg $avgTestScore%"
                                activeFasting != null -> "${activeFasting.completedDays}/${activeFasting.targetDays} days"
                                isArabic -> "تقييم وتثبيت الحفظ"
                                else -> "Practice & review"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Admin-managed Promotional / Announcement Cards
        if (toggleMap["feature_promo_cards"] != false) {
            val visibleCards = promoCards.filter { it.isActive }
            if (visibleCards.isNotEmpty()) {
                item {
                    NoorSectionHeader(title = if (isArabic) "بطاقات وإعلانات نور" else "Highlights & Announcements")
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(visibleCards, key = { it.id }) { card ->
                            NoorGlassCard(
                                modifier = Modifier
                                    .width(270.dp)
                                    .clickable { onNavigateRoute(card.internalRoute) }
                                    .testTag("promo_card_${card.id}"),
                                contentPadding = 12.dp
                            ) {
                                if (card.imageUri.isNotBlank()) {
                                    AsyncImage(
                                        model = card.imageUri,
                                        contentDescription = card.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                Text(
                                    text = card.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = card.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Core Modules Grid
        item {
            NoorSectionHeader(title = if (isArabic) "أقسام التطبيق" else "Quick Access")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile(
                        title = strings.memorization,
                        icon = Icons.Default.Mic,
                        modifier = Modifier.weight(1f).testTag("home_memorize_tile"),
                        onClick = { onNavigateRoute("memorize") }
                    )
                    if (toggleMap["feature_ai"] != false) {
                        QuickTile(
                            title = strings.noorAi,
                            icon = Icons.Default.Psychology,
                            modifier = Modifier.weight(1f).testTag("home_ai_tile"),
                            onClick = { onNavigateRoute("ai") }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile(
                        title = if (isArabic) "المحفوظات والملاحظات" else "Saved & Notes",
                        icon = Icons.Default.Bookmark,
                        modifier = Modifier.weight(1f).testTag("home_saved_tile"),
                        onClick = { onNavigateRoute("profile_saved") }
                    )
                    if (isBackendAdmin) {
                        QuickTile(
                            title = strings.superAdmin,
                            icon = Icons.Default.AdminPanelSettings,
                            modifier = Modifier.weight(1f).testTag("home_admin_tile"),
                            onClick = { onNavigateRoute("profile_admin") }
                        )
                    } else {
                        QuickTile(
                            title = strings.halaqat,
                            icon = Icons.Default.Groups,
                            modifier = Modifier.weight(1f).testTag("home_halaqat_tile"),
                            onClick = { onNavigateRoute("profile_worship") }
                        )
                    }
                }
            }
        }

        // Recent Announcements
        if (announcements.isNotEmpty()) {
            item {
                NoorSectionHeader(title = strings.announcements)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    announcements.take(3).forEach { ann ->
                        NoorGlassCard(contentPadding = 12.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ann.title, style = MaterialTheme.typography.titleMedium)
                                AssistChip(onClick = {}, label = { Text(ann.targetAudience, style = MaterialTheme.typography.labelSmall) })
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(ann.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    NoorGlassCard(
        modifier = modifier
            .height(84.dp)
            .clickable(onClick = onClick),
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
