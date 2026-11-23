package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.NoorPreferences
import com.example.data.local.NoorUserSettings
import com.example.firebase.FirestoreNoorRepository
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.security.configureAppCheck
import com.example.ui.components.NoorGlassBackground
import com.example.ui.screens.*
import com.example.ui.theme.NoorTheme
import com.example.viewmodel.NoorViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var shortcutRouteState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MUST configure App Check before Compose UI or ViewModels initialize Firebase.
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        shortcutRouteState = mapIntentToRoute(intent)

        setContent {
            val prefs = remember(applicationContext) { NoorPreferences(applicationContext) }
            val localSettings by prefs.settings.collectAsState(initial = NoorUserSettings())
            val scope = rememberCoroutineScope()

            val authUser by remember { Firebase.auth.authStateFlow() }
                .collectAsState(initial = Firebase.auth.currentUser)

            NoorTheme(darkTheme = localSettings.darkMode, language = localSettings.language) {
                val activeUser = authUser
                if (activeUser == null) {
                    AuthScreen(
                        language = localSettings.language,
                        darkMode = localSettings.darkMode,
                        onLanguageChange = { lang -> scope.launch { prefs.setLanguage(lang) } },
                        onDarkModeToggle = { scope.launch { prefs.setDarkMode(!localSettings.darkMode) } },
                        onAuthenticated = { /* AuthStateListener drives navigation cleanly */ }
                    )
                } else {
                    // Key authenticated composition strictly on UID so ViewModels & caches reset across sign-out/account switches.
                    key(activeUser.uid) {
                        val repo = remember(activeUser.uid) { FirestoreNoorRepository(applicationContext) }
                        val vm: NoorViewModel = viewModel(
                            key = "noor_vm_${activeUser.uid}",
                            factory = NoorViewModel.factory(application, repo)
                        )
                        LaunchedEffect(activeUser.uid) {
                            vm.bindAuthenticatedUser(activeUser)
                        }

                        NoorAuthenticatedShell(
                            vm = vm,
                            shortcutRoute = shortcutRouteState,
                            onShortcutHandled = { shortcutRouteState = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shortcutRouteState = mapIntentToRoute(intent)
    }

    private fun mapIntentToRoute(intent: Intent?): String? = when (intent?.action) {
        "com.aistudio.noor.SHORTCUT_OPEN_QURAN" -> "quran"
        "com.aistudio.noor.SHORTCUT_CONTINUE_READING" -> "continue_reading"
        "com.aistudio.noor.SHORTCUT_MEMORIZATION" -> "memorize"
        "com.aistudio.noor.SHORTCUT_PRAYER_TIMES" -> "profile_worship"
        "com.aistudio.noor.SHORTCUT_LISTEN" -> "quran_listening"
        else -> null
    }
}

@Composable
internal fun NoorAuthenticatedShell(
    vm: NoorViewModel,
    shortcutRoute: String? = null,
    onShortcutHandled: () -> Unit = {}
) {
    val settings by vm.settings.collectAsState()
    val featureToggles by vm.featureToggles.collectAsState()
    val statusBanner by vm.statusBanner.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentRoute by remember { mutableStateOf("home") }
    var profileSection by remember { mutableIntStateOf(0) }
    var memorizeTargetSurah by remember { mutableIntStateOf(1) }
    var memorizeTargetAyah by remember { mutableIntStateOf(1) }
    var mushafFullScreen by remember { mutableStateOf(false) }

    val navigateToRoute: (String) -> Unit = { target ->
        mushafFullScreen = false
        when (target) {
            "profile_worship", "worship", "library", "halaqat" -> {
                profileSection = 0
                currentRoute = "profile"
            }
            "profile_saved" -> {
                profileSection = 1
                currentRoute = "profile"
            }
            "profile_notifications" -> {
                profileSection = 2
                currentRoute = "profile"
            }
            "profile_settings" -> {
                profileSection = 3
                currentRoute = "profile"
            }
            "profile_admin", "admin" -> {
                profileSection = 4
                currentRoute = "profile"
            }
            else -> currentRoute = target
        }
    }

    LaunchedEffect(shortcutRoute) {
        val r = shortcutRoute ?: return@LaunchedEffect
        when (r) {
            "continue_reading" -> {
                vm.goToPage(settings.lastPage, recordHistory = false)
                currentRoute = "quran"
            }
            "quran_listening" -> {
                currentRoute = "quran"
                vm.playFromAyah(settings.lastSurah, settings.lastAyah)
            }
            else -> navigateToRoute(r)
        }
        onShortcutHandled()
    }

    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearStatusBanner()
        }
    }

    if (currentRoute != "home") {
        BackHandler {
            if (currentRoute == "quran" && mushafFullScreen) {
                mushafFullScreen = false
            } else {
                currentRoute = "home"
            }
        }
    }

    val strings = stringsFor(settings.language)
    val isArabic = settings.language == AppLanguage.AR
    val toggleMap = featureToggles.associate { it.key to it.enabled }

    val navItems = buildList {
        add(Triple("home", strings.navHome, Icons.Default.Home))
        add(Triple("quran", strings.navQuran, Icons.AutoMirrored.Filled.MenuBook))
        add(Triple("memorize", strings.navMemorize, Icons.Default.Mic))
        if (toggleMap["feature_ai"] != false) {
            add(Triple("ai", strings.noorAi, Icons.Default.AutoAwesome))
        }
        add(Triple("profile", if (isArabic) "حسابي" else "Profile", Icons.Default.Person))
    }

    NoorGlassBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 700.dp
            val showChrome = !(currentRoute == "quran" && mushafFullScreen)

            Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!isWide && showChrome) {
                        NavigationBar(windowInsets = WindowInsets.navigationBars) {
                            navItems.forEach { (route, label, icon) ->
                                NavigationBarItem(
                                    selected = currentRoute == route,
                                    onClick = { navigateToRoute(route) },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_$route")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    AnimatedVisibility(visible = isWide && showChrome) {
                        NavigationRail {
                            navItems.forEach { (route, label, icon) ->
                                NavigationRailItem(
                                    selected = currentRoute == route,
                                    onClick = { navigateToRoute(route) },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("rail_$route")
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (currentRoute) {
                            "home" -> HomeScreen(vm = vm, onNavigateRoute = navigateToRoute)
                            "quran" -> MushafScreen(
                                vm = vm,
                                onNavigateMemorize = { s, a ->
                                    memorizeTargetSurah = s
                                    memorizeTargetAyah = a
                                    currentRoute = "memorize"
                                },
                                onFullScreenChanged = { mushafFullScreen = it }
                            )
                            "memorize" -> MemorizeScreen(
                                vm = vm,
                                initialSurah = memorizeTargetSurah,
                                initialAyah = memorizeTargetAyah
                            )
                            "ai" -> NoorAiScreen(vm = vm)
                            "profile" -> ProfileHubScreen(
                                vm = vm,
                                selectedSection = profileSection,
                                onSectionChange = { profileSection = it },
                                onNavigateQuranPage = { currentRoute = "quran" },
                                onSignedOut = { currentRoute = "home" }
                            )
                            else -> HomeScreen(vm = vm, onNavigateRoute = navigateToRoute)
                        }
                    }
                }
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    trySend(currentUser)
    awaitClose { removeAuthStateListener(listener) }
}
