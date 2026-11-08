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

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import net.jami.utils.Log

/**
 * Publishes recent conversations as launcher shortcuts — the long-press menu on the app icon,
 * and the direct-share row in the system share sheet.
 *
 * The reference client declares these; KMP had none at all. Dynamic rather than static:
 * which conversations matter changes constantly, which is the whole point.
 *
 * The intent is the same `ACTION_VIEW` on a `jami:` URI the app already accepts from outside,
 * with [EXTRA_CONVERSATION_ID] added so `MainActivity` can tell "open this conversation" from
 * the existing "search for this identifier" — a shortcut names a conversation the user is
 * already in, so dropping them in the search box would be a step backwards.
 */
class AndroidConversationShortcuts(
    private val context: Context,
) : ConversationShortcutService {

    override fun publish(shortcuts: List<ConversationShortcut>) {
        try {
            val infos = shortcuts.map { shortcut ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setPackage(context.packageName)
                    data = android.net.Uri.parse("jami:${shortcut.conversationId}")
                    putExtra(EXTRA_CONVERSATION_ID, shortcut.conversationId)
                    putExtra(EXTRA_ACCOUNT_ID, shortcut.accountId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                ShortcutInfoCompat.Builder(context, shortcut.conversationId)
                    .setShortLabel(shortcut.title.ifBlank { shortcut.conversationId.take(8) })
                    .setLongLabel(shortcut.title.ifBlank { shortcut.conversationId })
                    .setIntent(intent)
                    .apply { avatarIcon(shortcut)?.let { setIcon(it) } }
                    .setLongLived(true)
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, infos)
        } catch (e: Exception) {
            // A launcher that refuses shortcuts (rate limit, no launcher at all on a stripped
            // ROM) must not take the app down over a convenience.
            Log.w(TAG, "Unable to publish conversation shortcuts", e)
        }
    }

    override fun clear() {
        try {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to clear conversation shortcuts", e)
        }
    }

    private fun avatarIcon(shortcut: ConversationShortcut): IconCompat? {
        val bytes = shortcut.avatar ?: return null
        return try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            // Adaptive so the launcher can mask it to its own shape; a square photo in a
            // round launcher is the usual giveaway that an app got this wrong.
            IconCompat.createWithAdaptiveBitmap(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to decode avatar for a shortcut", e)
            null
        }
    }

    companion object {
        private const val TAG = "ConversationShortcuts"

        /** Set on a shortcut intent: open this conversation rather than search for it. */
        const val EXTRA_CONVERSATION_ID = "net.jami.extra.CONVERSATION_ID"
        const val EXTRA_ACCOUNT_ID = "net.jami.extra.ACCOUNT_ID"
    }
}
