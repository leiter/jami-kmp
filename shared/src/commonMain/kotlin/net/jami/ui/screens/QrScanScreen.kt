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
package net.jami.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.theme.JamiTheme
import net.jami.utils.StringUtils

/**
 * QR scan screen stub.
 *
 * Camera-based scanning is a future platform-specific effort (CameraX, AVFoundation, etc.).
 * For now, provides a manual text field to paste a Jami ID or jami: URI.
 *
 * @param onBack Called when the user navigates back.
 * @param onConversationClick Called with the conversation/contact ID when a valid Jami ID is submitted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    onBack: () -> Unit,
    onConversationClick: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_title_qr_scan)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_desc_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JamiTheme.colors.surface,
                    titleContentColor = JamiTheme.colors.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(JamiTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.qr_scan_manual_hint),
                style = JamiTheme.typography.bodyLarge,
                color = JamiTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(JamiTheme.spacing.l))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("jami:…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            val jamiId = resolveJamiId(input)
            JamiButton(
                text = stringResource(Res.string.action_new_conversation),
                onClick = { if (jamiId != null) onConversationClick(jamiId) },
                enabled = jamiId != null,
            )
        }
    }
}

/** Extracts a raw Jami ID from a plain ID or a jami: URI. Returns null if input is blank/invalid. */
private fun resolveJamiId(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val id = if (trimmed.startsWith("jami:")) trimmed.removePrefix("jami:") else trimmed
    return if (StringUtils.isJamiId(id)) id else null
}
