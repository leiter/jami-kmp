/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.jami.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.jami.di.getViewModel
import net.jami.model.Call
import net.jami.services.AccountService
import net.jami.services.CallService
import net.jami.ui.screens.*
import net.jami.ui.viewmodel.AppState
import net.jami.ui.viewmodel.AppViewModel
import org.koin.compose.koinInject

/**
 * Extract a navigation argument in a KMP-compatible way.
 */
private fun androidx.navigation.NavBackStackEntry.extractArg(key: String): String {
    return this.savedStateHandle.get<String>(key) ?: ""
}

/**
 * Main navigation host for the Jami application.
 *
 * Uses a reactive state machine to gate the app on account existence:
 * - Loading: Shows a centered progress indicator during daemon initialization
 * - NoAccounts: Shows the Welcome/onboarding flow
 * - HasAccounts: Shows the main Home flow with all routes
 *
 * Navigation between states is automatic, driven by AccountService.accounts changes.
 */
@Composable
fun JamiNavigation() {
    val appViewModel = getViewModel<AppViewModel>()
    val appState by appViewModel.appState.collectAsState()
    val isLocked by appViewModel.isLocked.collectAsState()

    DisposableEffect(appViewModel) {
        onDispose { appViewModel.onCleared() }
    }

    // Lock when the app goes to background; the composable is recomposed on resume
    // so the lock screen appears before content is visible again.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, appViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                appViewModel.lockIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Show the biometric lock screen on top of whatever app state we're in
    if (isLocked && appState is AppState.HasAccounts) {
        BiometricLockScreen(
            onAuthenticate = { appViewModel.authenticateBiometric("Unlock Jami", "Use biometric to unlock") },
            onUnlocked = { appViewModel.unlock() },
        )
        return
    }

    when (val state = appState) {
        is AppState.Loading -> LoadingScreen()
        is AppState.HasAccounts -> MainNavigation(needsMigration = state.needsMigration)
        else -> OnboardingNavigation(appViewModel)
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun OnboardingNavigation(appViewModel: AppViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Welcome.route,
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onCreateAccount = {
                    appViewModel.startOnboarding()
                    navController.navigate(Screen.CreateAccount.route)
                },
                onImportAccount = {
                    appViewModel.startOnboarding()
                    navController.navigate(Screen.ImportAccount.route)
                },
                onLinkDevice = {
                    appViewModel.startOnboarding()
                    navController.navigate(Screen.LinkDeviceImport.route)
                },
            )
        }

        composable(Screen.CreateAccount.route) {
            CreateAccountScreen(
                onBack = {
                    appViewModel.finishOnboarding()
                    navController.popBackStack()
                },
                onAccountCreated = {
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(Screen.CreateAccount.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.ProfileSetup.route) {
            ProfileSetupScreen(
                onSkip = {
                    navController.navigate(Screen.AccountSummary.route) {
                        popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                    }
                },
                onComplete = {
                    navController.navigate(Screen.AccountSummary.route) {
                        popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.AccountSummary.route) {
            AccountSummaryScreen(
                onContinue = {
                    appViewModel.finishOnboarding()
                },
            )
        }

        composable(Screen.ImportAccount.route) {
            ImportAccountScreen(
                onBack = {
                    appViewModel.finishOnboarding()
                    navController.popBackStack()
                },
                onImported = {
                    appViewModel.finishOnboarding()
                },
            )
        }

        composable(Screen.LinkDeviceImport.route) {
            LinkDeviceImportScreen(
                onBack = {
                    appViewModel.finishOnboarding()
                    navController.popBackStack()
                },
                onSuccess = {
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(Screen.LinkDeviceImport.route) { inclusive = true }
                    }
                },
            )
        }
    }
}

@Composable
private fun MainNavigation(needsMigration: Boolean) {
    val navController = rememberNavController()
    val callService: CallService = koinInject()
    val accountService: AccountService = koinInject()

    // Track whether the migration dialog has been dismissed this session
    var migrationDismissed by remember { mutableStateOf(false) }

    if (needsMigration && !migrationDismissed) {
        val migrationAccountId = remember {
            accountService.accounts.value.firstOrNull { it.needsMigration }?.accountId
        }
        if (migrationAccountId != null) {
            MigrationDialog(
                accountId = migrationAccountId,
                onMigrated = { migrationDismissed = true },
                onDismiss = { migrationDismissed = true },
            )
        }
    }

    // Navigate to the call screen when a RINGING incoming call arrives.
    //
    // We use callUpdates (SharedFlow) rather than currentCalls (StateFlow) here because
    // StateFlow deduplicates by equality. Call is a plain class with reference equality,
    // so when the daemon fires CONNECTING → RINGING the same Call object is mutated
    // in-place: updateCurrentCalls() produces [sameRef] == [sameRef], the StateFlow
    // never re-emits, and the LaunchedEffect would be silently skipped. callUpdates is
    // a SharedFlow that never deduplicates, so every state change reaches us.
    LaunchedEffect(Unit) {
        callService.callUpdates.collect { call ->
            if (call.isIncoming && call.callStatus == Call.CallStatus.RINGING) {
                val callId = call.daemonId ?: return@collect
                if (navController.currentDestination?.route?.startsWith("call/view/") != true) {
                    navController.navigate(Screen.ViewCall.createRoute(callId)) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    // On the first non-empty emission, navigate to any already-ongoing call — handles the
    // case where the app is reopened while a call is in progress (process kill, swipe away,
    // etc.). After this one-shot check we do not force the user back to the call screen if
    // they intentionally navigated away.
    val currentCalls by callService.currentCalls.collectAsState()
    var initialActiveCallChecked by remember { mutableStateOf(false) }
    LaunchedEffect(currentCalls) {
        if (!initialActiveCallChecked && currentCalls.isNotEmpty()) {
            initialActiveCallChecked = true
            val active = currentCalls.firstOrNull {
                it.callStatus == Call.CallStatus.CURRENT || it.callStatus == Call.CallStatus.HOLD
            }
            if (active != null) {
                val callId = active.daemonId ?: return@LaunchedEffect
                if (navController.currentDestination?.route?.startsWith("call/view/") != true) {
                    navController.navigate(Screen.ViewCall.createRoute(callId)) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    // Navigate when a notification tap (VIEW_CALL / ACCEPT from notification) requests it.
    val pendingCallNavId by callService.pendingCallNavId.collectAsState()
    LaunchedEffect(pendingCallNavId) {
        val callId = pendingCallNavId ?: return@LaunchedEffect
        callService.consumePendingCallNavId()
        navController.navigate(Screen.ViewCall.createRoute(callId)) {
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
    ) {
        // ==================== Main Screens ====================

        composable(Screen.Home.route) {
            HomeScreen(
                onConversationClick = { id ->
                    navController.navigate(Screen.Chat.createRoute(id))
                },
                onRequestsClick = {
                    navController.navigate(Screen.PendingRequests.route)
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.AccountSettings.route)
                },
                onAppSettingsClick = {
                    navController.navigate(Screen.AppSettings.route)
                },
                onAboutClick = {
                    navController.navigate(Screen.About.route)
                },
                onNewConversation = {
                    navController.navigate(Screen.NewConversation.route)
                },
                onAddAccount = {
                    navController.navigate(Screen.CreateAccount.route)
                },
            )
        }

        // ==================== Add Account (from account picker) ====================

        composable(Screen.CreateAccount.route) {
            CreateAccountScreen(
                onBack = { navController.popBackStack() },
                onAccountCreated = {
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(Screen.CreateAccount.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.ProfileSetup.route) {
            ProfileSetupScreen(
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onConversationClick = { id ->
                    navController.navigate(Screen.Chat.createRoute(id))
                },
                onQrScanClick = {
                    navController.navigate(Screen.QrScan.route)
                },
                onNewGroupClick = {
                    navController.navigate(Screen.NewConversation.route)
                },
            )
        }

        composable(Screen.QrScan.route) {
            QrScanScreen(
                onBack = { navController.popBackStack() },
                onConversationClick = { id ->
                    navController.navigate(Screen.Chat.createRoute(id)) {
                        popUpTo(Screen.Home.route)
                    }
                },
            )
        }

        // ==================== Chat & Conversation ====================

        composable(
            route = Screen.Chat.ROUTE,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val conversationId = backStackEntry.extractArg("conversationId")
            if (conversationId.isEmpty()) return@composable
            ChatScreen(
                conversationId = conversationId,
                onBack = { navController.popBackStack() },
                onCallClick = { contactId, isVideo ->
                    navController.navigate(Screen.Call.createRoute(contactId, isVideo))
                },
                onDetailsClick = {
                    navController.navigate(
                        Screen.ConversationDetails.createRoute(conversationId)
                    )
                },
                onShareLocation = {
                    navController.navigate(
                        Screen.LocationSharing.createRoute(conversationId)
                    )
                },
                onImageClick = { filePath ->
                    MediaNavigationState.filePath = filePath
                    MediaNavigationState.fileName = ""
                    navController.navigate(Screen.MediaViewer.route)
                },
                onVideoClick = { filePath, fileName ->
                    MediaNavigationState.filePath = filePath
                    MediaNavigationState.fileName = fileName
                    navController.navigate(Screen.VideoPlayer.route)
                },
            )
        }

        composable(
            route = Screen.ConversationDetails.ROUTE,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val conversationId = backStackEntry.extractArg("conversationId")
            if (conversationId.isEmpty()) return@composable
            ConversationDetailsScreen(
                conversationId = conversationId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.LocationSharing.ROUTE,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val conversationId = backStackEntry.extractArg("conversationId")
            if (conversationId.isEmpty()) return@composable
            LocationSharingScreen(
                conversationId = conversationId,
                onBack = { navController.popBackStack() },
            )
        }

        // ==================== Call ====================

        composable(
            route = Screen.Call.ROUTE,
            arguments = listOf(
                navArgument("contactId") { type = NavType.StringType },
                navArgument("isVideo") { type = NavType.BoolType },
            ),
        ) { backStackEntry ->
            val contactId = backStackEntry.extractArg("contactId")
            if (contactId.isEmpty()) return@composable
            val isVideo = backStackEntry.savedStateHandle.get<Boolean>("isVideo") ?: false
            CallScreen(
                contactId = contactId,
                isVideo = isVideo,
                onEnd = { navController.popBackStack() },
            )
        }

        // View an existing in-progress call by daemon call ID (from notification)
        composable(
            route = Screen.ViewCall.ROUTE,
            arguments = listOf(navArgument("callId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val callId = backStackEntry.extractArg("callId")
            if (callId.isEmpty()) return@composable
            IncomingCallScreen(
                callId = callId,
                onEnd = { navController.popBackStack() },
            )
        }

        // ==================== New Conversation ====================

        composable(Screen.NewConversation.route) {
            NewConversationScreen(
                onBack = { navController.popBackStack() },
                onConversationCreated = { id ->
                    navController.navigate(Screen.Chat.createRoute(id)) {
                        popUpTo(Screen.Home.route)
                    }
                },
            )
        }

        // ==================== Settings ====================

        composable(Screen.AccountSettings.route) {
            AccountSettingsScreen(
                onBack = { navController.popBackStack() },
                onBlockedContacts = {
                    navController.navigate(Screen.BlockedContacts.route)
                },
                onAccount = { navController.navigate(Screen.AccountSettingsAccount.route) },
                onMedia = { navController.navigate(Screen.AccountSettingsMedia.route) },
                onMessages = { navController.navigate(Screen.AccountSettingsMessages.route) },
                onAdvanced = { navController.navigate(Screen.AccountSettingsAdvanced.route) },
            )
        }

        composable(Screen.AccountSettingsMedia.route) {
            AccountMediaSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.AccountSettingsMessages.route) {
            AccountMessagesSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.AccountSettingsAdvanced.route) {
            AccountAdvancedSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.AccountSettingsAccount.route) {
            AccountDetailsSettingsScreen(
                onBack = { navController.popBackStack() },
                onBlockedContacts = { navController.navigate(Screen.BlockedContacts.route) },
            )
        }

        composable(Screen.AppSettings.route) {
            AppSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.BlockedContacts.route) {
            BlockedContactsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.PendingRequests.route) {
            PendingRequestsScreen(
                onBack = { navController.popBackStack() },
                onConversationOpened = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId)) {
                        popUpTo(Screen.PendingRequests.route) { inclusive = true }
                    }
                },
            )
        }

        // ==================== About ====================

        composable(Screen.About.route) {
            AboutScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ==================== Media Viewer ====================

        composable(Screen.MediaViewer.route) {
            MediaViewerScreen(
                filePath = MediaNavigationState.filePath,
                onBack = { navController.popBackStack() },
            )
        }

        // ==================== Video Player ====================

        composable(Screen.VideoPlayer.route) {
            VideoPlayerScreen(
                filePath = MediaNavigationState.filePath,
                fileName = MediaNavigationState.fileName,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
