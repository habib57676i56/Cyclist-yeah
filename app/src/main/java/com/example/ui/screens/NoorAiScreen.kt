package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ai.AiStreamState
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.ui.components.NoorGlassCard
import com.example.ui.theme.LocalNoorGlass
import com.example.viewmodel.NoorViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoorAiScreen(vm: NoorViewModel) {
    val settings by vm.settings.collectAsState()
    val chat by vm.aiChat.collectAsState()
    val chunkCount by vm.approvedChunkCount.collectAsState()
    val remoteSources by vm.fsKnowledgeSources.collectAsState()
    val streamState by vm.aiStreamState.collectAsState()

    val strings = stringsFor(settings.language)
    val isArabic = settings.language == AppLanguage.AR
    val glass = LocalNoorGlass.current
    val approvedRemoteCount = remoteSources.count { it.approvalStatus == "APPROVED" }

    var question by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        NoorGlassCard(contentPadding = 12.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = glass.accentGold)
                    Column {
                        Text(strings.noorAi, style = MaterialTheme.typography.titleLarge, color = glass.accentGold)
                        Text(
                            text = if (isArabic)
                                "Gemini AI + بحث المصادر المعتمدة • $chunkCount محلي / $approvedRemoteCount سحابي"
                            else
                                "Gemini AI + Grounded RAG • $chunkCount local / $approvedRemoteCount cloud sources",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (streamState is AiStreamState.Loading || streamState is AiStreamState.Streaming) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (chat.isEmpty()) {
                item {
                    NoorGlassCard {
                        Text(
                            text = if (isArabic)
                                "مرحباً بك في مساعد نور التعليمي.\n\n• يعتمد على المصادر والمناهج المعتمدة من الإدارة (سحابياً ومحلياً) ويعمل مع Firebase AI Logic.\n• يذكر المراجع بدقة ولا يختلق آيات أو أحاديث عند عدم توفر دليل."
                            else
                                "Welcome to NOOR Educational Assistant.\n\n• Grounds responses in administrator-approved local & cloud Islamic sources via Firebase AI Logic.\n• Cites verified references and never fabricates Quranic verses or Hadith.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssistChip(
                                onClick = { vm.askNoorAi("ما فضل سورة الفاتحة؟") },
                                label = { Text(if (isArabic) "فضل سورة الفاتحة" else "Al-Fatihah Virtues") }
                            )
                            AssistChip(
                                onClick = { vm.askNoorAi("ما هي حروف الإدغام؟") },
                                label = { Text(if (isArabic) "حروف الإدغام" else "Idgham Letters") }
                            )
                            AssistChip(
                                onClick = { vm.askNoorAi("كيف أضع خطة لمراجعة القرآن؟") },
                                label = { Text(if (isArabic) "خطة مراجعة القرآن" else "Revision Plan Tips") }
                            )
                        }
                    }
                }
            }

            items(chat, key = { it.id }) { msg ->
                NoorGlassCard(
                    elevated = !msg.isUser,
                    borderColor = if (msg.isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else glass.glassBorder,
                    contentPadding = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val isGrounded = !msg.isUser && (msg.citations.isNotEmpty() || msg.answerMeta?.isGrounded == true)
                            if (isGrounded) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = when {
                                    msg.isUser -> if (isArabic) "سؤالك" else "You"
                                    msg.usedGemini -> "Gemini + Approved RAG"
                                    else -> msg.answerMeta?.modeLabel ?: strings.noorAi
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (msg.isStreaming) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(if (msg.isUser) "ai_user_msg" else "ai_answer_msg")
                    )

                    if (!msg.isUser && msg.citations.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            msg.citations.forEach { cite ->
                                AssistChip(onClick = {}, label = { Text(cite, style = MaterialTheme.typography.labelSmall) })
                            }
                        }
                    }

                    if (!msg.isUser && !msg.isStreaming && msg.questionRef.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (msg.feedbackSubmitted) {
                                Text(
                                    text = if (isArabic) "شكراً لتقييمك" else "Feedback sent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                IconButton(
                                    onClick = { vm.submitAiMessageFeedback(msg, helpful = true) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.ThumbUp, contentDescription = "Helpful", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { vm.submitAiMessageFeedback(msg, helpful = false) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.ThumbDown, contentDescription = "Needs Review", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 84.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = { Text(if (isArabic) "اسأل نور بالعربية أو الإنجليزية..." else "Ask NOOR AI...") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("ai_question_input")
            )
            FilledIconButton(
                onClick = {
                    if (question.isNotBlank()) {
                        vm.askNoorAi(question)
                        question = ""
                    }
                },
                modifier = Modifier.testTag("ai_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
