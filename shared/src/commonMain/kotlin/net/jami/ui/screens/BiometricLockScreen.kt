/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.account_biometric_auth_description
import jami_kmp.shared.generated.resources.app_name
import kotlinx.coroutines.launch
import net.jami.services.BiometricResult
import net.jami.ui.theme.JamiTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen lock overlay shown when the app resumes and the current account
 * has biometric authentication enabled.
 *
 * Auto-prompts the biometric dialog on first composition. Shows a retry
 * button if authentication fails or is cancelled.
 */
@Composable
fun BiometricLockScreen(
    onAuthenticate: suspend () -> BiometricResult,
    onUnlocked: () -> Unit,
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun tryAuthenticate() {
        if (isAuthenticating) return
        isAuthenticating = true
        errorMessage = null
        scope.launch {
            when (val result = onAuthenticate()) {
                is BiometricResult.Success -> onUnlocked()
                is BiometricResult.Error -> {
                    errorMessage = result.message
                    isAuthenticating = false
                }
                is BiometricResult.Cancelled -> {
                    isAuthenticating = false
                }
            }
        }
    }

    // Auto-prompt on first composition
    LaunchedEffect(Unit) { tryAuthenticate() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = JamiTheme.colors.primary,
            )

            Spacer(Modifier.height(JamiTheme.spacing.l))

            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(JamiTheme.spacing.s))

            Text(
                text = stringResource(Res.string.account_biometric_auth_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(JamiTheme.spacing.m))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.xl))

            Button(
                onClick = { tryAuthenticate() },
                enabled = !isAuthenticating,
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(JamiTheme.spacing.xs))
                Text(
                    text = if (isAuthenticating) "Authenticating…" else "Unlock",
                )
            }
        }
    }
}
