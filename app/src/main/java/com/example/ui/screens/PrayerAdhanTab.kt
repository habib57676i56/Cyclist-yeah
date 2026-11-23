package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.prayer.AdhanAudioManager
import com.example.prayer.CalculationMethod
import com.example.prayer.PrayerTimesCalculator
import com.example.ui.components.NoorGlassCard
import com.example.ui.theme.LocalNoorGlass
import com.example.viewmodel.NoorViewModel
import java.util.Calendar

@Composable
fun PrayerAdhanTab(vm: NoorViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val prayerTimes by vm.dailyPrayerTimes.collectAsState()
    val adhanVoices by vm.adhanVoices.collectAsState()
    val adhanPlayback by AdhanAudioManager.state.collectAsState()
    val strings = stringsFor(settings.language)
    val isArabic = settings.language == AppLanguage.AR
    val glass = LocalNoorGlass.current

    var cityMenuOpen by remember { mutableStateOf(false) }
    var methodMenuOpen by remember { mutableStateOf(false) }
    var offsetDialogOpen by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.detectGpsLocation()
        else vm.postStatus("Location permission denied; select your city manually.")
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        vm.postStatus(if (granted) "Notification permission granted for Adhan." else "Notification permission denied; Android will block Adhan alerts.")
    }

    val importAdhanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importAdhanFile(it) } }

    val nowCal = Calendar.getInstance()
    val nowMin = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
    val (nextPrayerName, minsLeft) = prayerTimes.nextPrayer(nowMin)
    val city = PrayerTimesCalculator.cityById(settings.cityId)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NoorGlassCard(elevated = true, borderColor = glass.accentGold.copy(alpha = 0.5f)) {
                Text(strings.nextPrayer, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "$nextPrayerName • ${minsLeft / 60}h ${minsLeft % 60}m",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(onClick = { cityMenuOpen = true }, modifier = Modifier.testTag("prayer_city_selector")) {
                            Icon(Icons.Default.LocationCity, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (settings.cityId == "gps_detected") "GPS (${String.format("%.2f, %.2f", settings.latitude, settings.longitude)})" else if (isArabic) city.nameAr else city.nameEn)
                        }
                        DropdownMenu(expanded = cityMenuOpen, onDismissRequest = { cityMenuOpen = false }) {
                            PrayerTimesCalculator.offlineCities.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.nameAr} — ${c.nameEn}") },
                                    onClick = {
                                        vm.selectCity(c.id)
                                        cityMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    FilledTonalButton(onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                        Icon(Icons.Default.MyLocation, contentDescription = "GPS", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("GPS")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box {
                        TextButton(onClick = { methodMenuOpen = true }) {
                            Text(if (isArabic) settings.calculationMethod.displayAr else settings.calculationMethod.displayEn)
                        }
                        DropdownMenu(expanded = methodMenuOpen, onDismissRequest = { methodMenuOpen = false }) {
                            CalculationMethod.entries.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text("${m.displayAr} (${m.displayEn})") },
                                    onClick = { vm.setPrayerMethod(m); methodMenuOpen = false }
                                )
                            }
                        }
                    }
                    TextButton(onClick = { offsetDialogOpen = true }) {
                        Text(if (isArabic) "تعديل الدقائق" else "Offsets")
                    }
                }
            }
        }

        // 6 Daily Prayer Times + Individual Notification Toggles
        item {
            val rows = listOf(
                Triple(strings.fajr, prayerTimes.formatTime(prayerTimes.fajrMinutes), "FAJR" to settings.notifyFajr),
                Triple(strings.sunrise, prayerTimes.formatTime(prayerTimes.sunriseMinutes), null),
                Triple(strings.dhuhr, prayerTimes.formatTime(prayerTimes.dhuhrMinutes), "DHUHR" to settings.notifyDhuhr),
                Triple(strings.asr, prayerTimes.formatTime(prayerTimes.asrMinutes), "ASR" to settings.notifyAsr),
                Triple(strings.maghrib, prayerTimes.formatTime(prayerTimes.maghribMinutes), "MAGHRIB" to settings.notifyMaghrib),
                Triple(strings.isha, prayerTimes.formatTime(prayerTimes.ishaMinutes), "ISHA" to settings.notifyIsha)
            )
            NoorGlassCard {
                rows.forEachIndexed { idx, (label, timeStr, togglePair) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(timeStr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (togglePair != null) {
                                Switch(
                                    checked = togglePair.second,
                                    onCheckedChange = { enabled ->
                                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        vm.setPrayerToggle(togglePair.first, enabled)
                                    }
                                )
                            }
                        }
                    }
                    if (idx < rows.lastIndex) HorizontalDivider(color = glass.glassBorder)
                }
            }
        }

        // Adhan Voice Selection & Custom MP3/M4A/WAV Import
        item {
            NoorGlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.adhanVoice, style = MaterialTheme.typography.titleMedium)
                    FilledTonalButton(
                        onClick = { importAdhanLauncher.launch(arrayOf("audio/*")) },
                        modifier = Modifier.testTag("import_adhan_button")
                    ) {
                        Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isArabic) "استيراد أذان" else "Import MP3/WAV")
                    }
                }
                adhanPlayback.lastResultMessage?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (adhanPlayback.blockedBySystem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                adhanVoices.forEach { voice ->
                    val selected = settings.selectedAdhanVoiceId == voice.id
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(voice.nameAr, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            Text(
                                text = "${voice.format} • Assigned: ${voice.assignedPrayers}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    if (adhanPlayback.isPlaying) AdhanAudioManager.stopAdhan(context)
                                    else AdhanAudioManager.playAdhanVoice(context, voice)
                                },
                                modifier = Modifier.testTag("preview_adhan_${voice.id}")
                            ) {
                                Icon(
                                    if (adhanPlayback.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Preview Adhan"
                                )
                            }
                            FilledTonalButton(onClick = { vm.selectAdhanVoice(voice) }) {
                                Text(if (selected) "Active" else "Select")
                            }
                        }
                    }
                }
            }
        }
    }

    if (offsetDialogOpen) {
        var f by remember { mutableStateOf(settings.fajrOffsetMin.toString()) }
        var d by remember { mutableStateOf(settings.dhuhrOffsetMin.toString()) }
        var a by remember { mutableStateOf(settings.asrOffsetMin.toString()) }
        var m by remember { mutableStateOf(settings.maghribOffsetMin.toString()) }
        var i by remember { mutableStateOf(settings.ishaOffsetMin.toString()) }
        AlertDialog(
            onDismissRequest = { offsetDialogOpen = false },
            title = { Text("Prayer Time Adjustments (min)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = f, onValueChange = { f = it }, label = { Text(strings.fajr) })
                    OutlinedTextField(value = d, onValueChange = { d = it }, label = { Text(strings.dhuhr) })
                    OutlinedTextField(value = a, onValueChange = { a = it }, label = { Text(strings.asr) })
                    OutlinedTextField(value = m, onValueChange = { m = it }, label = { Text(strings.maghrib) })
                    OutlinedTextField(value = i, onValueChange = { i = it }, label = { Text(strings.isha) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.setPrayerOffsets(
                        f.toIntOrNull() ?: 0,
                        d.toIntOrNull() ?: 0,
                        a.toIntOrNull() ?: 0,
                        m.toIntOrNull() ?: 0,
                        i.toIntOrNull() ?: 0
                    )
                    offsetDialogOpen = false
                }) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { offsetDialogOpen = false }) { Text(strings.cancel) } }
        )
    }
}
