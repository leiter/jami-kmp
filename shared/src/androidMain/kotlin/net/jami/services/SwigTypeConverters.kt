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
package net.jami.services

import net.jami.daemon.StringMap
import net.jami.daemon.VectMap
import net.jami.daemon.SwarmMessage as SwigSwarmMessage
import net.jami.model.MediaAttribute
import net.jami.model.SwarmMessage

/**
 * Extension functions for converting between SWIG-generated types and Kotlin types.
 *
 * These converters handle the transformation of daemon data structures
 * to Kotlin-friendly types used throughout the application.
 */

// ==================== StringMap Conversions ====================

/**
 * Convert SWIG StringMap to Kotlin Map<String, String>.
 */
fun StringMap.toNative(): Map<String, String> {
    val result = HashMap<String, String>()
    val keys = keys()
    for (i in 0 until keys.size.toInt()) {
        val key = keys[i]
        result[key] = get(key) ?: ""
    }
    return result
}

/**
 * Convert SWIG StringMap to Kotlin Map, handling UTF-8 decoding.
 */
fun StringMap.toNativeFromUtf8(): Map<String, String> {
    val result = HashMap<String, String>()
    val keys = keys()
    for (i in 0 until keys.size.toInt()) {
        val key = keys[i]
        // Values may be UTF-8 encoded, decode as needed
        result[key] = get(key) ?: ""
    }
    return result
}

/**
 * Convert Kotlin Map to SWIG StringMap.
 */
fun Map<String, String>.toSwigStringMap(): StringMap {
    val stringMap = StringMap()
    forEach { (k, v) -> stringMap[k] = v }
    return stringMap
}

// ==================== VectMap Conversions ====================

/**
 * Convert SWIG VectMap to Kotlin List<Map<String, String>>.
 */
fun VectMap.toNative(): List<Map<String, String>> {
    return (0 until size.toInt()).map { get(it).toNative() }
}

/**
 * Convert List<Map<String, String>> to SWIG VectMap.
 */
@JvmName("listMapToSwigVectMap")
fun List<Map<String, String>>.toSwigVectMap(): VectMap {
    val vectMap = VectMap()
    forEach { entry ->
        val map = StringMap()
        entry.forEach { (k, v) -> map[k] = v }
        vectMap.add(map)
    }
    return vectMap
}

// ==================== MediaAttribute Conversions ====================

/**
 * Convert MediaAttribute list to SWIG VectMap.
 */
@JvmName("mediaAttributeListToSwigVectMap")
fun List<MediaAttribute>.toSwigVectMap(): VectMap {
    val vectMap = VectMap()
    forEach { attr ->
        val map = StringMap()
        map["MEDIA_TYPE"] = when (attr.mediaType) {
            MediaAttribute.MediaType.AUDIO -> "MEDIA_TYPE_AUDIO"
            MediaAttribute.MediaType.VIDEO -> "MEDIA_TYPE_VIDEO"
        }
        map["ENABLED"] = attr.enabled.toString()
        map["MUTED"] = attr.muted.toString()
        map["SOURCE"] = attr.source
        map["LABEL"] = attr.label
        vectMap.add(map)
    }
    return vectMap
}

// ==================== SwarmMessage Conversions ====================

/**
 * Convert SWIG SwarmMessage to Kotlin SwarmMessage.
 */
fun SwigSwarmMessage.toKotlinSwarmMessage(): SwarmMessage {
    // Convert reactions VectMap to Map<String, List<String>>
    val reactionsMap = mutableMapOf<String, List<String>>()
    reactions?.let { rxns ->
        for (i in 0 until rxns.size) {
            val reactionMap = rxns[i].toNative()
            val msgId = reactionMap["id"] ?: continue
            val emoji = reactionMap["body"] ?: continue
            reactionsMap[msgId] = (reactionsMap[msgId] ?: emptyList()) + emoji
        }
    }

    // Convert status IntegerMap to Map<String, Int>
    val statusMap = mutableMapOf<String, Int>()
    status?.let { st ->
        for ((key, value) in st.entries) {
            statusMap[key] = value
        }
    }

    return SwarmMessage(
        id = id ?: "",
        type = type ?: "",
        linearizedParent = linearizedParent ?: "",
        body = body?.toNativeFromUtf8() ?: emptyMap(),
        reactions = reactionsMap,
        editions = editions?.toNative() ?: emptyList(),
        status = statusMap
    )
}

// ==================== Blob Conversions ====================

/**
 * Get bytes from SWIG Blob.
 */
fun net.jami.daemon.Blob.toByteArray(): ByteArray {
    return ByteArray(size) { i -> get(i) }
}

/**
 * Create a SWIG Blob from ByteArray.
 */
fun ByteArray.toSwigBlob(): net.jami.daemon.Blob {
    val blob = net.jami.daemon.Blob()
    forEach { blob.add(it) }
    return blob
}
