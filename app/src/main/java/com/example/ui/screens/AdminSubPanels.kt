package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.ui.components.NoorGlassCard
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.NoorViewModel

@Composable
fun AdminUsersAndHalaqatPanel(vm: NoorViewModel) {
    val users by vm.users.collectAsState()
    val halaqat by vm.halaqat.collectAsState()

    var newName by remember { mutableStateOf("") }
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf(UserRole.STUDENT) }
    var selectedHalaqahId by remember { mutableStateOf<Long?>(null) }

    var hName by remember { mutableStateOf("") }
    var hSchedule by remember { mutableStateOf("") }
    var hTarget by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NoorGlassCard {
            Text("Create Student / Teacher / Admin", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = newUsername, onValueChange = { newUsername = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, label = { Text("Password (min 6 chars)") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(UserRole.STUDENT, UserRole.TEACHER, UserRole.ADMIN).forEach { r ->
                    FilterChip(selected = newRole == r, onClick = { newRole = r }, label = { Text(r.labelEn) })
                }
            }
            if (halaqat.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    halaqat.take(3).forEach { h ->
                        FilterChip(
                            selected = selectedHalaqahId == h.id,
                            onClick = { selectedHalaqahId = if (selectedHalaqahId == h.id) null else h.id },
                            label = { Text(h.name.take(16)) }
                        )
                    }
                }
            }
            Button(
                onClick = {
                    vm.createUserAccount(newName, newUsername, newPassword, newRole, selectedHalaqahId)
                    newName = ""; newUsername = ""; newPassword = ""
                },
                modifier = Modifier.fillMaxWidth().testTag("admin_create_user_btn")
            ) { Text("Create Account") }
        }

        NoorGlassCard {
            Text("Create Halaqah Group", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = hName, onValueChange = { hName = it }, label = { Text("Halaqah Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = hSchedule, onValueChange = { hSchedule = it }, label = { Text("Schedule") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = hTarget, onValueChange = { hTarget = it }, label = { Text("Memorization Target") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (hName.isNotBlank()) {
                        val teacher = users.firstOrNull { it.role == UserRole.TEACHER.name }
                        vm.saveHalaqah(
                            HalaqahEntity(
                                name = hName,
                                teacherId = teacher?.id,
                                teacherName = teacher?.name ?: "Unassigned",
                                scheduleText = hSchedule.ifBlank { "Mon/Wed 17:00" },
                                memorizationTarget = hTarget.ifBlank { "Juz 'Amma" },
                                revisionPlan = "Daily 1 page"
                            )
                        )
                        hName = ""; hSchedule = ""; hTarget = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("admin_create_halaqah_btn")
            ) { Text("Save Halaqah") }
        }

        NoorGlassCard {
            Text("Registered Accounts (${users.size})", style = MaterialTheme.typography.titleMedium)
            users.forEach { u ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${u.name} (@${u.username})", style = MaterialTheme.typography.bodyMedium)
                        Text("${u.role} • Halaqah: ${u.halaqahId ?: "None"}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (u.username != "superadmin") {
                        IconButton(onClick = { vm.deleteUserAccount(u) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete User")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminAiKnowledgePanel(vm: NoorViewModel) {
    val entries by vm.knowledgeEntries.collectAsState()
    val chunkCount by vm.approvedChunkCount.collectAsState()

    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("التفسير وعلوم القرآن") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importKnowledgeDocument(it) } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NoorGlassCard {
            Text("NOOR AI Local RAG Knowledge Control", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Approved indexed chunks: $chunkCount. Note: Importing documents indexes searchable text for RAG retrieval and does NOT retrain neural model weights.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { docPicker.launch(arrayOf("text/plain", "application/json", "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "*/*")) },
                    modifier = Modifier.weight(1f).testTag("admin_import_ai_doc_btn")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import TXT/JSON/PDF/DOCX")
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { vm.rebuildAiSearchIndex() },
                    modifier = Modifier.weight(1f).testTag("admin_rebuild_index_btn")
                ) { Text("Rebuild Search Index") }
                FilledTonalButton(
                    onClick = { vm.exportDatabaseBackup(knowledgeOnly = true) },
                    modifier = Modifier.weight(1f)
                ) { Text("Backup AI KB") }
            }
        }

        NoorGlassCard {
            Text("Add Q&A / Educational Knowledge", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Question (Optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("Educational Answer / Content") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            OutlinedTextField(value = keywords, onValueChange = { keywords = it }, label = { Text("Keywords") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = reference, onValueChange = { reference = it }, label = { Text("Islamic Reference") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (title.isNotBlank() && answer.isNotBlank()) {
                        vm.addOrUpdateKnowledge(
                            KnowledgeEntryEntity(
                                title = title,
                                category = category,
                                question = question,
                                answer = answer,
                                keywords = keywords,
                                reference = reference.ifBlank { "إدارة نور المعتمدة" },
                                approvalStatus = "APPROVED"
                            )
                        )
                        title = ""; question = ""; answer = ""; keywords = ""; reference = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("admin_add_knowledge_btn")
            ) { Text("Save & Index Knowledge") }
        }

        NoorGlassCard {
            Text("Knowledge Entries (${entries.size})", style = MaterialTheme.typography.titleMedium)
            entries.forEach { e ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${e.approvalStatus} • ${e.sourceFileName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (e.approvalStatus == "APPROVED") SuccessGreen else WarningAmber
                        )
                    }
                    Row {
                        IconButton(onClick = {
                            val next = if (e.approvalStatus == "APPROVED") "REJECTED" else "APPROVED"
                            vm.addOrUpdateKnowledge(e.copy(approvalStatus = next))
                        }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Toggle Approval")
                        }
                        IconButton(onClick = { vm.deleteKnowledgeEntry(e) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Entry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminCommsAndContentPanel(vm: NoorViewModel) {
    val promoCards by vm.promoCards.collectAsState()
    val featureToggles by vm.featureToggles.collectAsState()
    val reciters by vm.reciters.collectAsState()
    val settings by vm.settings.collectAsState()

    var annTitle by remember { mutableStateOf("") }
    var annBody by remember { mutableStateOf("") }
    var annAudience by remember { mutableStateOf("ALL") }

    var cardTitle by remember { mutableStateOf("") }
    var cardDesc by remember { mutableStateOf("") }
    var cardImageUri by remember { mutableStateOf("") }
    var cardRoute by remember { mutableStateOf("quran") }

    var lessonTitleAr by remember { mutableStateOf("") }
    var lessonContentAr by remember { mutableStateOf("") }
    var lessonRef by remember { mutableStateOf("") }

    // Compliant Android Photo Picker (zero storage permissions!)
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { cardImageUri = it.toString() } }

    val mushafZipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importMushafZip(it) } }

    val reciterZipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importReciterPack(settings.selectedReciterId, it) } }

    val backupPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.restoreDatabaseBackup(it) } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Announcements
        NoorGlassCard {
            Text("Send Announcement / Message", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = annTitle, onValueChange = { annTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = annBody, onValueChange = { annBody = it }, label = { Text("Message Body") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL", "STUDENTS", "TEACHERS").forEach { aud ->
                    FilterChip(selected = annAudience == aud, onClick = { annAudience = aud }, label = { Text(aud) })
                }
            }
            Button(
                onClick = {
                    if (annTitle.isNotBlank() && annBody.isNotBlank()) {
                        vm.sendAnnouncement(annTitle, annBody, annAudience, "GENERAL")
                        annTitle = ""; annBody = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("admin_send_announcement_btn")
            ) { Text("Send Announcement") }
        }

        // Promo Cards Manager
        NoorGlassCard {
            Text("Manage Promotional & Announcement Cards (${promoCards.size})", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = cardTitle, onValueChange = { cardTitle = it }, label = { Text("Card Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = cardDesc, onValueChange = { cardDesc = it }, label = { Text("Card Description") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                ) { Text(if (cardImageUri.isBlank()) "Pick Image" else "Image Ready") }
                FilterChip(selected = cardRoute == "quran", onClick = { cardRoute = "quran" }, label = { Text("Quran") })
                FilterChip(selected = cardRoute == "worship", onClick = { cardRoute = "worship" }, label = { Text("Worship") })
                FilterChip(selected = cardRoute == "ai", onClick = { cardRoute = "ai" }, label = { Text("AI") })
            }
            Button(
                onClick = {
                    if (cardTitle.isNotBlank()) {
                        vm.savePromoCard(
                            PromoCardEntity(
                                title = cardTitle,
                                description = cardDesc,
                                imageUri = cardImageUri,
                                internalRoute = cardRoute,
                                displayOrder = promoCards.size + 1,
                                isActive = true
                            )
                        )
                        cardTitle = ""; cardDesc = ""; cardImageUri = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("admin_create_promo_btn")
            ) { Text("Publish Promo Card") }
            promoCards.forEach { card ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${card.title} (${if (card.isActive) "Active" else "Hidden"})", modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.savePromoCard(card.copy(isActive = !card.isActive)) }) {
                        Icon(Icons.Default.Visibility, contentDescription = "Toggle Visibility")
                    }
                    IconButton(onClick = { vm.deletePromoCard(card) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Card")
                    }
                }
            }
        }

        // Quran Content, Mushaf Pack, Reciter Audio Pack, Online Streaming Toggle
        NoorGlassCard {
            Text("Quran Resources, Mushaf Pack & Library", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Allow Optional Online Reciter Caching", modifier = Modifier.weight(1f))
                Switch(checked = settings.allowOnlineAudioStreaming, onCheckedChange = { vm.setOnlineStreaming(it) })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { mushafZipPicker.launch(arrayOf("application/zip", "*/*")) },
                    modifier = Modifier.weight(1f)
                ) { Text("Import 604 Mushaf ZIP") }
                OutlinedButton(
                    onClick = { reciterZipPicker.launch(arrayOf("application/zip", "*/*")) },
                    modifier = Modifier.weight(1f)
                ) { Text("Import Reciter ZIP") }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = lessonTitleAr, onValueChange = { lessonTitleAr = it }, label = { Text("New Library Lesson Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = lessonContentAr, onValueChange = { lessonContentAr = it }, label = { Text("Lesson Content") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = lessonRef, onValueChange = { lessonRef = it }, label = { Text("Verified Reference") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (lessonTitleAr.isNotBlank() && lessonContentAr.isNotBlank()) {
                        vm.saveEduLesson(
                            EduLessonEntity(
                                category = "QURAN",
                                titleAr = lessonTitleAr,
                                titleEn = lessonTitleAr,
                                contentAr = lessonContentAr,
                                contentEn = lessonContentAr,
                                referenceSource = lessonRef.ifBlank { "Admin Verified" },
                                isVerifiedReference = true
                            )
                        )
                        lessonTitleAr = ""; lessonContentAr = ""; lessonRef = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Publish Library Lesson") }
        }

        // Feature Toggles & Backup/Restore
        NoorGlassCard {
            Text("Application Feature Controls", style = MaterialTheme.typography.titleMedium)
            featureToggles.forEach { ft ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${ft.labelEn} (${ft.labelAr})", modifier = Modifier.weight(1f))
                    Switch(checked = ft.enabled, onCheckedChange = { vm.setFeatureToggle(ft, it) })
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Local Database Backup & Restore", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.exportDatabaseBackup(knowledgeOnly = false) }, modifier = Modifier.weight(1f)) {
                    Text("Export Backup JSON")
                }
                OutlinedButton(onClick = { backupPicker.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f)) {
                    Text("Restore Backup")
                }
            }
            Text(
                text = "Reciters loaded: ${reciters.size}. Local administration applies to this device; multi-device remote sync is disabled in offline-first mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
