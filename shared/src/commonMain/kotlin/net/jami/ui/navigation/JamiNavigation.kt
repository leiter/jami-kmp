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
import org.koin.mp.KoinPlatform
import net.jami.ui.viewmodel.AboutViewModel
import net.jami.ui.viewmodel.AccountCreationViewModel
import net.jami.ui.viewmodel.AccountSettingsViewModel
import net.jami.ui.viewmodel.AppSettingsViewModel
import net.jami.ui.viewmodel.AppState
import net.jami.ui.viewmodel.AppViewModel
import net.jami.ui.viewmodel.CallViewModel
import net.jami.ui.viewmodel.ChatViewModel
import net.jami.ui.viewmodel.ContactDetailsViewModel
import net.jami.ui.viewmodel.ContactsViewModel
import net.jami.ui.viewmodel.ConversationsViewModel
import net.jami.ui.viewmodel.ImportAccountViewModel
import net.jami.ui.viewmodel.NewConversationViewModel

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
        is AppState.NoAccounts -> OnboardingNavigation()
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
private fun OnboardingNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Welcome.route,
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onCreateAccount = {
                    navController.navigate(Screen.CreateAccount.route)
                },
                onImportAccount = {
                    navController.navigate(Screen.ImportAccount.route)
                },
            )
        }

        composable(Screen.CreateAccount.route) {
            val viewModel = remember { KoinPlatform.getKoin().get<AccountCreationViewModel>() }
            val state by viewModel.state.collectAsState()

            DisposableEffect(viewModel) {
                onDispose { viewModel.onCleared() }
            }

            LaunchedEffect(state.isCreated) {
                if (state.isCreated) {
                    // No-op: AppViewModel reactively switches to HasAccounts
                    // when AccountService.accounts updates after creation
                }
            }

            CreateAccountScreen(
                state = state,
                onAction = viewModel::onAction,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.ImportAccount.route) {
            val viewModel = remember { KoinPlatform.getKoin().get<ImportAccountViewModel>() }
            val state by viewModel.state.collectAsState()

            DisposableEffect(viewModel) {
                onDispose { viewModel.onCleared() }
            }

            LaunchedEffect(state.isImported) {
                if (state.isImported) {
                    // No-op: AppViewModel reactively switches to HasAccounts
                    // when AccountService.accounts updates after import
                }
            }

            ImportAccountScreen(
                state = state,
                onAction = viewModel::onAction,
                onBack = { navController.popBackStack() },
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
            val viewModel = getViewModel<ConversationsViewModel>()
            val conversationsState by viewModel.conversationsState.collectAsState()
            val headerState by viewModel.headerState.collectAsState()

            DisposableEffect(viewModel) {
                onDispose { viewModel.onCleared() }
            }

            HomeScreen(
                conversationsState = conversationsState,
                headerState = headerState,
                onAction = viewModel::onHomeAction,
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
            val viewModel = getViewModel<ConversationsViewModel>()
            val state by viewModel.searchState.collectAsState()

            DisposableEffect(viewModel) {
                onDispose { viewModel.onCleared() }
            }

            SearchScreen(
                state = state,
                onAction = viewModel::onSearchAction,
                onBack = { navController.popBackStack() },
                onConversationClick = { id ->
                    navController.navigate(Screen.Chat.createRoute(id))
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

            val viewModel = getViewModel<ChatViewModel>()
            val topBarState by viewModel.topBarState.collectAsState()
            val messagesState by viewModel.messagesState.collectAsState()
            val inputState by viewModel.inputState.collectAsState()

            LaunchedEffect(conversationId) {
                viewModel.loadConversation(conversationId)
            }

            DisposableEffect(viewModel) {
                onDispose { viewModel.onCleared() }
            }

            ChatScreen(
                topBarState = topBarState,
                messagesState = messagesState,
                inputState = inputState,
                onAction = viewModel::onAction,
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

            val viewModel = getViewModel<ContactDetailsViewModel>()
            val state by viewModel.state.collectAsState()

            LaunchedEffect(conversationId) {
                viewModel.loadContact(conversationId)
            }

            DisposableEffect(viewModel) {
                onDispose { viewModel.onCleared() }
            }

            ConversationDetailsScreen(
                state = state,
                onAction = viewModel::onAction,
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

            val viewModel = getViewModel<CallViewModel>()
            val peerState by viewModel.peerState.collectAsState()
            val controlsState by viewModel.controlsState.collectAsState()
            val timerState by viewModel.timerState.collectAsState()

            LaunchedEffect(contactId, isVideo) {
                viewModel.initCall(contactId, isVideo)
            }

            DisposableEffect(viewModel) {
                onDispose { viewModel.onCleared() }
            }

            CallScreen(
                peerState = peerState,
                controlsState = controlsState,
                timerState = timerState,
                onAction = viewModel::onAction,
                onEnd = { navController.popBackStack() },
            )
        }

        // ==================== New Conversation ====================

        composable(Screen.NewConversation.route) {
            val viewModel = getViewModel<NewConversationViewModel>()
            val searchState by viewModel.searchState.collectAsState()
            val selectionState by viewModel.selectionState.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.conversationCreated.collect { id ->
                    navController.navigate(Screen.Chat.createRoute(id)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            }

            DisposableEffect(viewModel) {
                onDispose { viewModel.onCleared() }
            }

            NewConversationScreen(
                searchState = searchState,
                selectionState = selectionState,
                onAction = viewModel::onAction,
                onBack = { navController.popBackStack() },
            )
        }

        // ==================== Settings ====================

        composable(Screen.AccountSettings.route) {
            val viewModel = getViewModel<AccountSettingsViewModel>()
            val profileState by viewModel.profileState.collectAsState()
            val devicesState by viewModel.devicesState.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.loadAccount()
            }

            DisposableEffect(viewModel) {
                onDispose { viewModel.onCleared() }
            }

            AccountSettingsScreen(
                profileState = profileState,
                devicesState = devicesState,
                onAction = viewModel::onAction,
                onBack = { navController.popBackStack() },
                onBlockedContacts = {
                    navController.navigate(Screen.BlockedContacts.route)
                },
            )
        }

        composable(Screen.AppSettings.route) {
            val viewModel = getViewModel<AppSettingsViewModel>()
            val state by viewModel.state.collectAsState()

            DisposableEffect(viewModel) {
                onDispose { viewModel.onCleared() }
            }

            AppSettingsScreen(
                state = state,
                onAction = viewModel::onAction,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.BlockedContacts.route) {
            val viewModel = getViewModel<ContactsViewModel>()
            val state by viewModel.blockedContactsState.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.loadContacts()
            }

            DisposableEffect(viewModel) {
                onDispose { viewModel.onCleared() }
            }

            BlockedContactsScreen(
                state = state,
                onAction = viewModel::onAction,
                onBack = { navController.popBackStack() },
            )
        }

        // ==================== About ====================

        composable(Screen.About.route) {
            val viewModel = getViewModel<AboutViewModel>()
            val state by viewModel.state.collectAsState()

            DisposableEffect(viewModel) {
                onDispose { viewModel.onCleared() }
            }

            AboutScreen(
                state = state,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
