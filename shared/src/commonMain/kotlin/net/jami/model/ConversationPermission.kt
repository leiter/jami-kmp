/*
 *  Copyright (C) 2004-2026 Savoir-faire Linux Inc.
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
package net.jami.model

/**
 * What this account is allowed to do in a swarm, as the daemon reports it.
 *
 * The daemon answers `getConversationPermissions` with a set of these string keys; anything
 * absent from that set is refused at the daemon boundary, not merely discouraged. The keys are
 * the daemon's own and must match `jami-client-android`'s `Conversation.Permission` exactly —
 * they are wire values, not display names.
 */
enum class ConversationPermission(val key: String) {
    Text("text"),
    TextReply("textReply"),
    File("file"),
    FileReply("fileReply"),
    Reaction("reaction"),
    Edit("edit"),
    Delete("delete"),
    DeleteFile("deleteFile"),
    Call("call"),
    HostConference("hostConference"),
    AddMember("addMember"),
    BanUnbanMember("banUnbanMember"),
    UpdateProfile("updateProfile"),
    CreateDocument("createDocument"),
    ;

    companion object {
        fun fromKey(key: String): ConversationPermission? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Thrown when an operation is attempted that the conversation does not grant.
 *
 * The reference client throws `java.lang.SecurityException` here, which common code has no
 * equivalent for, so this carries the same meaning across targets. It marks a client bug — the
 * UI should have hidden the affordance — and is deliberately not a checked, recoverable error:
 * callers that want to avoid it ask [Conversation.hasPermission] first.
 */
class ConversationPermissionException(val permission: ConversationPermission) :
    IllegalStateException("Conversation does not grant ${permission.key}")
