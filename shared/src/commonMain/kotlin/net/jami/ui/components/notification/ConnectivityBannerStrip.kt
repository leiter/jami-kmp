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
package net.jami.ui.components.notification

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.error_account_offline
import jami_kmp.shared.generated.resources.error_no_network
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.ConnectivityBanner
import org.jetbrains.compose.resources.stringResource

/**
 * Full-width strip saying why nothing written right now will reach anyone.
 *
 * Matches the reference's `error_msg_pane`: the error colour across the full width, centred and
 * bold, sitting above the screen's content rather than floating over it. A snackbar would be
 * wrong — the condition persists, so the notice has to persist with it.
 *
 * Shared by the chat and the conversation list. The reference only has it in a conversation, but
 * the list is where you land, and a dead network is worth knowing before picking someone to
 * message.
 */
@Composable
fun ConnectivityBannerStrip(banner: ConnectivityBanner, modifier: Modifier = Modifier) {
    val text = when (banner) {
        ConnectivityBanner.NoNetwork -> stringResource(Res.string.error_no_network)
        ConnectivityBanner.AccountOffline -> stringResource(Res.string.error_account_offline)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = JamiTheme.colors.error,
    ) {
        Text(
            text = text,
            style = JamiTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = JamiTheme.colors.onError,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(JamiTheme.spacing.s)
                // The strip is the screen's most important state; a reader arriving should hear
                // it without hunting for it.
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
