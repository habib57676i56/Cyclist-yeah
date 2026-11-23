package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.ui.components.NoorEmptyState
import com.example.ui.components.NoorGlassCard
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.NoorViewModel

@Composable
fun KhatmahFastingTab(vm: NoorViewModel) {
    val settings by vm.settings.collectAsState()
    val khatmahs by vm.khatmahs.collectAsState()
    val fastingPlans by vm.fastingPlans.collectAsState()
    val strings = stringsFor(settings.language)
    val isArabic = settings.language == AppLanguage.AR

    var subSection by remember { mutableIntStateOf(0) } // 0 = Khatmah, 1 = Fasting
    var showNewKhatmahDialog by remember { mutableStateOf(false) }
    var showNewFastingDialog by remember { mutableStateOf(false) }

    // Inline confirmation banner preserving exact user position without leaving or closing app!
    var inlineConfirmation by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("khatmah_fasting_list"),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = subSection == 0,
                    onClick = { subSection = 0 },
                    label = { Text(strings.khatmah) },
                    modifier = Modifier.weight(1f).testTag("subtab_khatmah")
                )
                FilterChip(
                    selected = subSection == 1,
                    onClick = { subSection = 1 },
                    label = { Text(strings.fasting) },
                    modifier = Modifier.weight(1f).testTag("subtab_fasting")
                )
            }
        }

        inlineConfirmation?.let { msg ->
            item {
                NoorGlassCard(borderColor = SuccessGreen, contentPadding = 12.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SuccessGreen,
                            modifier = Modifier.weight(1f).testTag("completion_confirmation_banner")
                        )
                        TextButton(onClick = { inlineConfirmation = null }) { Text(strings.close) }
                    }
                }
            }
        }

        if (subSection == 0) {
            item {
                Button(
                    onClick = { showNewKhatmahDialog = !showNewKhatmahDialog },
                    modifier = Modifier.fillMaxWidth().testTag("create_khatmah_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isArabic) "إنشاء ختمة قرآن جديدة" else "Create New Khatmah")
                }
            }

            if (showNewKhatmahDialog) {
                item(key = "new_khatmah_form") {
                    var title by remember { mutableStateOf("") }
                    var days by remember { mutableStateOf("30") }
                    NoorGlassCard(elevated = true) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isArabic) "ختمة قرآن جديدة" else "New Khatmah", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = days,
                                onValueChange = { days = it.filter { c -> c.isDigit() } },
                                label = { Text("Target Days (7-365)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showNewKhatmahDialog = false }) { Text(strings.cancel) }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        vm.createKhatmah(title, days.toIntOrNull() ?: 30)
                                        showNewKhatmahDialog = false
                                    },
                                    modifier = Modifier.testTag("khatmah_dialog_save_btn")
                                ) { Text(strings.save) }
                            }
                        }
                    }
                }
            }

            if (khatmahs.isEmpty()) {
                item {
                    NoorEmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = if (isArabic) "لا توجد ختمات نشطة" else "No Khatmah Yet",
                        description = if (isArabic) "اختر مدة الختمة وسيتم حساب الورد اليومي تلقائياً." else "Select target days and NOOR calculates your daily reading portion."
                    )
                }
            } else {
                items(khatmahs, key = { it.id }) { khatmah ->
                    val logs by vm.observeKhatmahLogs(khatmah.id).collectAsState(initial = emptyList())
                    val progress = (khatmah.completedPages / 604f).coerceIn(0f, 1f)
                    val juzCompleted = (khatmah.completedPages * 30) / 604
                    NoorGlassCard {
                        Text(khatmah.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${khatmah.completedPages}/604 ${strings.pageLabel} • Juz $juzCompleted/30 • Remaining ${604 - khatmah.completedPages} pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp))
                        Spacer(Modifier.height(10.dp))
                        logs.take(10).forEach { log ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Day ${log.dayNumber}: ${strings.pageLabel} ${log.fromPage}–${log.toPage}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(
                                    onClick = {
                                        // Updates state cleanly in-place — NEVER closes or navigates away!
                                        vm.toggleKhatmahDayCompleted(khatmah, log)
                                        inlineConfirmation = if (!log.isCompleted) {
                                            "Day ${log.dayNumber} portion (${log.fromPage}–${log.toPage}) marked completed!"
                                        } else {
                                            "Day ${log.dayNumber} marked incomplete."
                                        }
                                    },
                                    modifier = Modifier.testTag("khatmah_complete_day_${log.dayNumber}")
                                ) {
                                    Icon(
                                        imageVector = if (log.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Mark portion complete",
                                        tint = if (log.isCompleted) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Button(
                    onClick = { showNewFastingDialog = !showNewFastingDialog },
                    modifier = Modifier.fillMaxWidth().testTag("create_fasting_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isArabic) "إنشاء خطة صيام" else "Create Fasting Plan")
                }
            }

            if (showNewFastingDialog) {
                item(key = "new_fasting_form") {
                    var title by remember { mutableStateOf("") }
                    var type by remember { mutableStateOf("MON_THU") }
                    var days by remember { mutableStateOf("8") }
                    NoorGlassCard(elevated = true) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isArabic) "خطة صيام جديدة" else "New Fasting Plan", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = type == "MON_THU", onClick = { type = "MON_THU"; days = "8" }, label = { Text("Mon/Thu") })
                                FilterChip(selected = type == "WHITE_DAYS", onClick = { type = "WHITE_DAYS"; days = "3" }, label = { Text("13-15") })
                                FilterChip(selected = type == "RAMADAN", onClick = { type = "RAMADAN"; days = "30" }, label = { Text("30d") })
                            }
                            OutlinedTextField(
                                value = days,
                                onValueChange = { days = it.filter { c -> c.isDigit() } },
                                label = { Text("Target Days") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showNewFastingDialog = false }) { Text(strings.cancel) }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = {
                                    vm.createFastingPlan(title, type, days.toIntOrNull() ?: 8)
                                    showNewFastingDialog = false
                                }) { Text(strings.save) }
                            }
                        }
                    }
                }
            }

            if (fastingPlans.isEmpty()) {
                item {
                    NoorEmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = if (isArabic) "لا توجد خطط صيام" else "No Fasting Plans",
                        description = if (isArabic) "أنشئ خطة الاثنين والخميس أو الأيام البيض أو رمضان." else "Track Sunnah Monday/Thursday, White Days, or custom fasting plans."
                    )
                }
            } else {
                items(fastingPlans, key = { it.id }) { plan ->
                    val logs by vm.observeFastingLogs(plan.id).collectAsState(initial = emptyList())
                    NoorGlassCard {
                        Text(plan.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${plan.completedDays}/${plan.targetDays} days completed (${plan.planType})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (plan.completedDays.toFloat() / plan.targetDays.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(7.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        logs.take(10).forEach { log ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${log.dayLabel} (${log.dateIso})", style = MaterialTheme.typography.bodyMedium)
                                IconButton(
                                    onClick = {
                                        vm.toggleFastingDayCompleted(plan, log)
                                        inlineConfirmation = if (!log.isCompleted) {
                                            "${log.dayLabel} fasting marked completed!"
                                        } else {
                                            "${log.dayLabel} unchecked."
                                        }
                                    },
                                    modifier = Modifier.testTag("fasting_complete_${log.id}")
                                ) {
                                    Icon(
                                        imageVector = if (log.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Mark fasting day complete",
                                        tint = if (log.isCompleted) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
