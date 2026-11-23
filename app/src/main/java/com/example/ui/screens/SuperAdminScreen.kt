package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.UserRole
import com.example.i18n.AppLanguage
import com.example.recitation.ArabicSpeechRecognizer
import com.example.security.AdminPermission
import com.example.security.PermissionGuard
import com.example.ui.components.NoorGlassCard
import com.example.ui.theme.LocalNoorGlass
import com.example.utils.BackupAndReportExporter
import com.example.viewmodel.NoorViewModel

@Composable
fun SuperAdminScreen(vm: NoorViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val currentUser by vm.currentUser.collectAsState()
    val users by vm.users.collectAsState()
    val halaqat by vm.halaqat.collectAsState()
    val assessments by vm.assessments.collectAsState()
    val knowledgeEntries by vm.knowledgeEntries.collectAsState()
    val approvedChunks by vm.approvedChunkCount.collectAsState()
    val auditLogs by vm.auditLogs.collectAsState()
    val mushafCount by vm.mushafPackPagesCount.collectAsState()

    val isArabic = settings.language == AppLanguage.AR
    val glass = LocalNoorGlass.current
    val role = UserRole.fromName(currentUser?.role)

    var username by remember { mutableStateOf("superadmin") }
    var password by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }
    var adminTab by remember { mutableIntStateOf(0) }

    if (currentUser == null || !PermissionGuard.hasPermission(role, AdminPermission.VIEW_DASHBOARD)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NoorGlassCard(elevated = true, borderColor = glass.accentGold.copy(alpha = 0.55f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = glass.accentGold)
                    Text(if (isArabic) "بوابة الإدارة الآمنة" else "Secure Admin Authentication", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isArabic)
                        "كلمات المرور مشفرة بـ PBKDF2-SHA256. الحساب الافتراضي الأولي: superadmin / noor-admin-2026"
                    else
                        "Passwords hashed with PBKDF2-SHA256. Initial bootstrap: superadmin / noor-admin-2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_login_username")
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("admin_login_password")
                )
                loginError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        vm.login(username, password) { ok, msg ->
                            loginError = if (ok) null else msg
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("admin_login_submit")
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isArabic) "تسجيل الدخول" else "Sign In")
                }
            }
        }
        return
    }

    val totalStudents = users.count { it.role == UserRole.STUDENT.name }
    val activeStudents = users.count { it.role == UserRole.STUDENT.name && it.isActive }
    val totalTeachers = users.count { it.role == UserRole.TEACHER.name }
    val pendingAssessments = assessments.count { it.status == "PENDING_REVIEW" }
    val storageKb = remember(knowledgeEntries, assessments, mushafCount) {
        BackupAndReportExporter.calculateLocalStorageBytes(context) / 1024L
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("NOOR Super Admin", style = MaterialTheme.typography.titleLarge, color = glass.accentGold)
                Text("${currentUser?.name} (${role.labelEn})", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.testTag("admin_logout_btn")) {
                Text("Sign Out")
            }
        }

        ScrollableTabRow(selectedTabIndex = adminTab, edgePadding = 12.dp) {
            Tab(selected = adminTab == 0, onClick = { adminTab = 0 }, text = { Text("Overview") })
            Tab(selected = adminTab == 1, onClick = { adminTab = 1 }, text = { Text("Students/Halaqat") })
            Tab(selected = adminTab == 2, onClick = { adminTab = 2 }, text = { Text("AI & Knowledge") })
            Tab(selected = adminTab == 3, onClick = { adminTab = 3 }, text = { Text("Content/Cards/Backup") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (adminTab) {
                0 -> {
                    item {
                        NoorGlassCard {
                            Text("Live System Metrics (From Local Room DB)", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            MetricRow("Total / Active Students", "$totalStudents / $activeStudents")
                            MetricRow("Total Teachers", "$totalTeachers")
                            MetricRow("Total Halaqat", "${halaqat.size}")
                            MetricRow("Completed / Pending Assessments", "${assessments.size} / $pendingAssessments")
                            MetricRow("AI Entries / Indexed Chunks", "${knowledgeEntries.size} / $approvedChunks")
                            MetricRow("Imported Mushaf Images", "$mushafCount / 604")
                            MetricRow("Local Storage Usage", "$storageKb KB")
                            MetricRow("Microphone Hardware / Perm", "${vm.audioRecorder.hasHardwareMicrophone()} / ${vm.audioRecorder.hasRecordPermission()}")
                            MetricRow("Arabic STT Engine / Local Pack", "${vm.speechRecognizer.state.value.engineAvailable} / ${ArabicSpeechRecognizer.hasLocalSttModelPack(context)}")
                        }
                    }
                    item {
                        NoorGlassCard {
                            Text("Recent Administrative Audit Log (${auditLogs.size})", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            auditLogs.take(15).forEach { log ->
                                Text(
                                    text = "• [${log.actorUsername}] ${log.action}: ${log.details}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                1 -> item { AdminUsersAndHalaqatPanel(vm) }
                2 -> item { AdminAiKnowledgePanel(vm) }
                else -> item { AdminCommsAndContentPanel(vm) }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
