package net.jami.model

import kotlinx.serialization.Serializable

/**
 * Represents a message in a Jami swarm conversation.
 * Maps to the SwarmMessage struct in the daemon.
 */
@Serializable
data class SwarmMessage(
    val id: String,
    val type: String,
    val linearizedParent: String,
    val body: Map<String, String>,
    val reactions: Map<String, List<String>> = emptyMap(),
    val editions: List<Map<String, String>> = emptyList(),
    val status: Map<String, Int> = emptyMap()
) {
    val author: String
        get() = body["author"] ?: ""

    val timestamp: Long
        get() = body["timestamp"]?.toLongOrNull() ?: 0L

    val textContent: String
        get() = body["body"] ?: ""

    val isText: Boolean
        get() = type == "text/plain" || type == "application/data-transfer+json"

    val isCall: Boolean
        get() = type == "application/call-history+json"

    val isMember: Boolean
        get() = type == "member"

    val isReply: Boolean
        get() = body.containsKey("reply-to")

    val replyTo: String
        get() = body["reply-to"] ?: ""

    companion object {
        fun fromDaemonMap(map: Map<String, Any?>): SwarmMessage {
            @Suppress("UNCHECKED_CAST")
            return SwarmMessage(
                id = map["id"] as? String ?: "",
                type = map["type"] as? String ?: "",
                linearizedParent = map["linearizedParent"] as? String ?: "",
                body = (map["body"] as? Map<String, String>) ?: emptyMap(),
                reactions = (map["reactions"] as? Map<String, List<String>>) ?: emptyMap(),
                editions = (map["editions"] as? List<Map<String, String>>) ?: emptyList(),
                status = (map["status"] as? Map<String, Int>) ?: emptyMap()
            )
        }
    }
}
