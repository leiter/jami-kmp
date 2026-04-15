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

/**
 * Sealed class defining all navigation routes in the Jami application.
 *
 * Each screen is represented as a subclass with a unique route string.
 * Screens with parameters use companion objects to define route templates
 * and factory methods for creating parameterized routes.
 */
sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object CreateAccount : Screen("create_account")
    data object ImportAccount : Screen("import_account")
    data object ProfileSetup : Screen("profile_setup")
    data object AccountSummary : Screen("account_summary")
    data object Home : Screen("home")
    data object Search : Screen("search")

    data class Chat(val conversationId: String) : Screen("chat/$conversationId") {
        companion object {
            const val ROUTE = "chat/{conversationId}"
            fun createRoute(conversationId: String) = "chat/$conversationId"
        }
    }

    data class ConversationDetails(val conversationId: String) :
        Screen("conversation_details/$conversationId") {
        companion object {
            const val ROUTE = "conversation_details/{conversationId}"
            fun createRoute(conversationId: String) = "conversation_details/$conversationId"
        }
    }

    data class Call(val contactId: String, val isVideo: Boolean) :
        Screen("call/$contactId/$isVideo") {
        companion object {
            const val ROUTE = "call/{contactId}/{isVideo}"
            fun createRoute(contactId: String, isVideo: Boolean) = "call/$contactId/$isVideo"
        }
    }

    data object ContactPicker : Screen("contact_picker")
    data object NewConversation : Screen("new_conversation")
    data object AccountSettings : Screen("account_settings")
    data object AccountSettingsMedia : Screen("account_settings_media")
    data object AccountSettingsMessages : Screen("account_settings_messages")
    data object AccountSettingsAdvanced : Screen("account_settings_advanced")
    data object AccountSettingsAccount : Screen("account_settings_account")
    data object AppSettings : Screen("app_settings")
    data object About : Screen("about")
    data object BlockedContacts : Screen("blocked_contacts")
    data object QrScan : Screen("qr_scan")
}
