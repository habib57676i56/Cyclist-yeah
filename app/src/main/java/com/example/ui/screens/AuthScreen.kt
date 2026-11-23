package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.firebase.GoogleSignInButton
import com.example.firebase.attemptAutoSignIn
import com.example.i18n.AppLanguage
import com.example.ui.components.NoorGlassBackground
import com.example.ui.components.NoorGlassCard
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.LocalNoorGlass

@Composable
fun AuthScreen(
    language: AppLanguage,
    darkMode: Boolean,
    onLanguageChange: (AppLanguage) -> Unit,
    onDarkModeToggle: () -> Unit,
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val glass = LocalNoorGlass.current
    val isArabic = language == AppLanguage.AR

    var authError by remember { mutableStateOf<String?>(null) }
    var checkingAutoSignIn by remember { mutableStateOf(true) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                checkingAutoSignIn = false
                onAuthenticated()
            },
            onUnauthenticated = {
                checkingAutoSignIn = false
            },
            scope = scope
        )
    }

    NoorGlassBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top quick language & theme bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onDarkModeToggle,
                        modifier = Modifier.testTag("auth_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Theme"
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            val next = when (language) {
                                AppLanguage.AR -> AppLanguage.EN
                                AppLanguage.EN -> AppLanguage.UZ
                                AppLanguage.UZ -> AppLanguage.AR
                            }
                            onLanguageChange(next)
                        },
                        modifier = Modifier.testTag("auth_language_toggle")
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = "Language", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(language.code.uppercase())
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Illuminated Crescent Emblem
                Image(
                    painter = painterResource(id = R.drawable.img_noor_welcome),
                    contentDescription = "NOOR Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(132.dp)
                        .clip(CircleShape)
                        .border(2.dp, glass.accentGold.copy(alpha = 0.7f), CircleShape)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "نور • NOOR",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = AmiriFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = glass.accentGold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (language) {
                        AppLanguage.AR -> "مصحف المدينة المنورة، التحفيظ الذكي، الأذان، ومساعد نور الإسلامي الموثق"
                        AppLanguage.UZ -> "Madina Mushafi, yodlash, azon va tasdiqlangan islomiy yordamchi"
                        AppLanguage.EN -> "Complete Madinah Mushaf, Memorization, Worship Tracker & Grounded Islamic AI"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(Modifier.height(20.dp))

                // Key highlights card
                NoorGlassCard(
                    elevated = true,
                    borderColor = glass.accentGold.copy(alpha = 0.45f),
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
                ) {
                    FeatureRow(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = if (isArabic) "مصحف 604 صفحة مع تلاوة الشيخ" else "604-Page Madinah Mushaf & Audio Sync",
                        subtitle = if (isArabic) "تظليل تفاعلي للآيات مع التفسير والإعراب والصرف" else "Tap Ayahs for Tafsir, I'rab, Sarf & Bookmark Notes"
                    )
                    Spacer(Modifier.height(10.dp))
                    FeatureRow(
                        icon = Icons.Default.Mic,
                        title = if (isArabic) "تقييم التلاوة واختبارات الحفظ" else "Recitation Assessment & 6 Memorization Tests",
                        subtitle = if (isArabic) "مقارنة الكلمات مع مراجعة الأخطاء السحابية" else "Word-level speech check, hidden-verse tests & cloud sync"
                    )
                    Spacer(Modifier.height(10.dp))
                    FeatureRow(
                        icon = Icons.Default.AutoAwesome,
                        title = if (isArabic) "مساعد نور (Gemini + مصادر معتمدة)" else "NOOR AI (Gemini + Approved Sources)",
                        subtitle = if (isArabic) "إجابات موثقة دون اختلاق آيات أو أحاديث" else "Grounded scholarly citations synced with your personal account"
                    )
                }

                Spacer(Modifier.height(22.dp))

                // Mandatory Google Sign-In Card
                NoorGlassCard(
                    elevated = true,
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
                ) {
                    if (checkingAutoSignIn) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (isArabic) "جاري التحقق من الجلسة..." else "Checking existing Google session...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    GoogleSignInButton(
                        label = when (language) {
                            AppLanguage.AR -> "تسجيل الدخول بحساب Google"
                            AppLanguage.UZ -> "Google orqali kirish"
                            AppLanguage.EN -> "Sign in with Google"
                        },
                        onAuthSuccess = {
                            authError = null
                            onAuthenticated()
                        },
                        onAuthError = { msg ->
                            authError = msg
                        },
                        onAuthCancelled = {
                            authError = if (isArabic) {
                                "تم إلغاء تسجيل الدخول بحساب Google. يمكنك المحاولة مرة أخرى."
                            } else {
                                "Google Sign-In was cancelled. Tap below to try again."
                            }
                        }
                    )

                    authError?.let { err ->
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("auth_error_banner")
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Privacy Policy & Terms of Use
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showPrivacyDialog = true },
                            modifier = Modifier.testTag("privacy_policy_button")
                        ) {
                            Text(if (isArabic) "سياسة الخصوصية" else "Privacy Policy")
                        }
                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            onClick = { showTermsDialog = true },
                            modifier = Modifier.testTag("terms_of_use_button")
                        ) {
                            Text(if (isArabic) "شروط الاستخدام" else "Terms of Use")
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(isArabic = isArabic, onDismiss = { showPrivacyDialog = false })
    }
    if (showTermsDialog) {
        TermsOfUseDialog(isArabic = isArabic, onDismiss = { showTermsDialog = false })
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    val glass = LocalNoorGlass.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = title, tint = glass.accentGold, modifier = Modifier.size(22.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PrivacyPolicyDialog(isArabic: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isArabic) "سياسة الخصوصية" else "Privacy Policy") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isArabic)
                        "• المصادقة والملف الشخصي: يستخدم تطبيق نور تسجيل الدخول بحساب Google عبر Firebase Authentication لحفظ اسم العرض، البريد الإلكتروني، والصورة الشخصية ومعرف الحساب الخاص بك."
                    else
                        "• Authentication & Profile: NOOR uses Firebase Authentication with Google Sign-In to store your display name, email, profile photo, and unique account ID."
                )
                Text(
                    if (isArabic)
                        "• بياناتك الشخصية: تُخزَّن علاماتك، تقدم القراءة، خطط الختمة والصيام، التقييمات ونتائج الاختبارات في مساحة معزولة بحسابك في Cloud Firestore."
                    else
                        "• Personal Data Isolation: Bookmarks, notes, reading history, Khatmah/Fasting progress, recitation assessments, and test scores are stored strictly scoped to your user ID in Cloud Firestore."
                )
                Text(
                    if (isArabic)
                        "• حذف الحساب: يمكنك حذف حسابك وجميع بياناتك السحابية والمحلية نهائياً في أي وقت من صفحة الملف الشخصي > الإعدادات."
                    else
                        "• Account Deletion: You may permanently delete your account and associated personal data at any time from Profile > Settings."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (isArabic) "إغلاق" else "Close") }
        }
    )
}

@Composable
fun TermsOfUseDialog(isArabic: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isArabic) "شروط الاستخدام" else "Terms of Use") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isArabic)
                        "• النص القرآني: يعرض التطبيق النص العثماني الموثق دون تحريف، ويُعد المساعد الذكي أداة تعليمية تعتمد على المصادر المعتمدة وليس بديلاً عن أهل العلم في الفتوى."
                    else
                        "• Sacred Text & Educational Scope: NOOR presents verified Uthmani Quran text and educational references. NOOR AI is an educational study companion grounded in approved sources and is not a substitute for qualified Islamic scholars for formal religious rulings (Fatwa)."
                )
                Text(
                    if (isArabic)
                        "• صلاحيات الإدارة: تقتصر لوحة الإدارة السحابية على الحسابات المخوَّلة من الخادم فقط."
                    else
                        "• Administrative Access: Cloud administrative controls are verified strictly via backend security rules and administrator registries."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (isArabic) "إغلاق" else "Close") }
        }
    )
}
