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
package net.jami.ui.components.inputs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.action_cancel
import jami_kmp.shared.generated.resources.emoji_picker_recent
import jami_kmp.shared.generated.resources.emoji_picker_search
import jami_kmp.shared.generated.resources.emoji_picker_title
import net.jami.ui.platform.LocalPrefKeys
import net.jami.ui.platform.LocalPrefs
import net.jami.ui.theme.JamiTheme
import org.jetbrains.compose.resources.stringResource

/** How many recently-used emoji to remember and show first. */
private const val RECENT_LIMIT = 24

/**
 * The emoji the user reached for last, most recent first.
 *
 * Kept in [LocalPrefs] rather than the synced settings: which emoji this person uses on this
 * device is not something another device needs, and it changes often enough that syncing it
 * would be noise.
 */
object RecentEmoji {

    fun load(): List<String> =
        LocalPrefs.getString(LocalPrefKeys.RECENT_EMOJI, "")
            .split(SEPARATOR)
            .filter { it.isNotEmpty() }

    /** Moves [emoji] to the front, keeping the list distinct and bounded. */
    fun record(emoji: String) {
        val updated = (listOf(emoji) + load().filter { it != emoji }).take(RECENT_LIMIT)
        LocalPrefs.setString(LocalPrefKeys.RECENT_EMOJI, updated.joinToString(SEPARATOR))
    }

    /** A character no emoji contains, so no entry can be split in half. */
    private const val SEPARATOR = "\u001F"
}

/**
 * The full emoji picker, opened from the "more" button on the six quick reactions.
 *
 * Recents first — the point of the list is that the next reaction is usually the last one —
 * then the catalogue by category, with a search that matches Unicode names.
 */
@Composable
fun EmojiPickerDialog(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var categoryIndex by remember { mutableStateOf(0) }
    val recent = remember { RecentEmoji.load() }
    val recentTitle = stringResource(Res.string.emoji_picker_recent)

    // Recents are a page like any other, but only when there are some: an empty first tab
    // that appears after the first reaction and not before is worse than no tab.
    val pages = remember(recent) {
        buildList {
            if (recent.isNotEmpty()) {
                add(EmojiCategory(recentTitle, recent.map { Emoji(it, "") }))
            }
            addAll(EmojiCatalog.categories)
        }
    }

    val shown = if (query.isBlank()) {
        pages.getOrNull(categoryIndex)?.emoji.orEmpty()
    } else {
        EmojiCatalog.search(query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.emoji_picker_title)) },
        text = {
            Column {
                JamiSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(Res.string.emoji_picker_search),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isBlank() && pages.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = categoryIndex.coerceIn(0, pages.lastIndex),
                        edgePadding = 0.dp,
                    ) {
                        pages.forEachIndexed { index, page ->
                            Tab(
                                selected = index == categoryIndex,
                                onClick = { categoryIndex = index },
                                text = {
                                    Text(
                                        text = page.title,
                                        style = JamiTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                    )
                                },
                            )
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 48.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(JamiTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(JamiTheme.spacing.xs),
                ) {
                    items(shown, key = { it.char }) { emoji ->
                        Text(
                            text = emoji.char,
                            fontSize = 26.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                // 48 dp: a reaction is a small glyph and an easy miss.
                                .size(48.dp)
                                .padding(top = 8.dp)
                                .clickable {
                                    RecentEmoji.record(emoji.char)
                                    onSelect(emoji.char)
                                }
                                // The glyph is not a label a screen reader can speak, so the
                                // Unicode name is spoken instead.
                                .clearAndSetSemantics {
                                    contentDescription = emoji.keywords.ifEmpty { emoji.char }
                                },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
