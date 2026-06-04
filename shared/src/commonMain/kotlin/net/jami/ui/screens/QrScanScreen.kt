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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import net.jami.ui.components.QrCodeScannerView
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.theme.JamiTheme
import net.jami.utils.StringUtils
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    onBack: () -> Unit,
    onConversationClick: (String) -> Unit,
) {
    var manualMode by remember { mutableStateOf(false) }
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
                actions = {
                    IconButton(onClick = { manualMode = !manualMode }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Enter manually",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (manualMode) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
            } else {
                QrCodeScannerView(
                    modifier = Modifier.fillMaxSize(),
                    onQrDetected = { code ->
                        val jamiId = resolveJamiId(code)
                        if (jamiId != null) onConversationClick(jamiId)
                        else { input = code; manualMode = true }
                    },
                )
            }
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
