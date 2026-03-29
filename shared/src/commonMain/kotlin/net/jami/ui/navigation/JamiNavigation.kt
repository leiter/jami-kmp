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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.jami.di.getViewModel
import net.jami.ui.screens.*
import net.jami.ui.viewmodel.AppState
import net.jami.ui.viewmodel.AppViewModel

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

    DisposableEffect(appViewModel) {
        onDispose { appViewModel.onCleared() }
    }

    when (val state = appState) {
        is AppState.Loading -> LoadingScreen()
        is AppState.NoAccounts -> OnboardingNavigation(appViewModel)
        is AppState.Onboarding -> OnboardingNavigation(appViewModel)
        is AppState.HasAccounts -> MainNavigation(needsMigration = state.needsMigration)
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
                    navController.navigate(Screen.ImportAccount.route)
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
                onBack = { navController.popBackStack() },
                onImported = {
                    // Trigger AppViewModel to re-evaluate state with current accounts
                    appViewModel.finishOnboarding()
                },
            )
        }
    }
}

@Composable
private fun MainNavigation(needsMigration: Boolean) {
    val navController = rememberNavController()

    // Track whether the migration dialog has been dismissed this session
    var migrationDismissed by remember { mutableStateOf(false) }

    // TODO: Show migration dialog as overlay when needsMigration && !migrationDismissed

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

        // ==================== About ====================

        composable(Screen.About.route) {
            AboutScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
