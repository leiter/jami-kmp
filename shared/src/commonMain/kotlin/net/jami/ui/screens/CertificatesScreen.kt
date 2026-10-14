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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import net.jami.di.getViewModel
import net.jami.model.Certificate
import net.jami.model.CertificateDetail
import net.jami.model.CertificateStatus
import net.jami.ui.components.notification.JamiAlertDialog
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.CertificatesViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Pinned certificates for the active account.
 *
 * A row shows what the certificate is and whether the daemon's checks pass; tapping it opens the
 * details and the trust decision. Jami accounts verify peers through the DHT, so this screen is
 * usually empty — it matters for a custom authority, a SIP server with its own CA, or blocking a
 * certificate outright, which is why the empty state says so rather than looking broken.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificatesScreen(
    onBack: () -> Unit,
) {
    val viewModel = getViewModel<CertificatesViewModel>()
    val state by viewModel.state.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var pendingUnpin by remember { mutableStateOf<Certificate?>(null) }

    // Explicit types: the default "*/*" greys out every file in the iOS document picker, which
    // made this flow unusable there. PEM/DER certificates are x509; public.data is the fallback
    // for a file the system cannot classify.
    net.jami.ui.platform.FilePickerEffect(
        show = showPicker,
        mimeTypes = listOf(
            "public.x509-certificate",
            "application/x-x509-ca-cert",
            "application/x-pem-file",
            "public.data",
        ),
    ) { path ->
        showPicker = false
        if (path != null) viewModel.pinCertificateFile(path)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.screen_title_certificates),
                        style = JamiTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_desc_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showPicker = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(Res.string.certificates_pin_from_file),
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.action_refresh),
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
            when {
                state.isLoading && state.certificates.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.certificates.isEmpty() -> EmptyCertificates(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(JamiTheme.spacing.l),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.certificates, key = { it.id }) { certificate ->
                        CertificateRow(
                            certificate = certificate,
                            // A certificate the daemon just reported expired is flagged even if
                            // its checks were read before the signal arrived.
                            isExpired = certificate.isExpired ||
                                certificate.id in state.expiredIds,
                            isExpanded = state.expandedId == certificate.id,
                            onToggle = { viewModel.toggleExpanded(certificate.id) },
                            onStatusSelected = { viewModel.setStatus(certificate, it) },
                            onUnpin = { pendingUnpin = certificate },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingUnpin?.let { certificate ->
        JamiAlertDialog(
            title = stringResource(Res.string.certificates_unpin),
            body = certificate.commonName,
            confirmText = stringResource(Res.string.certificates_unpin),
            dismissText = stringResource(Res.string.action_cancel),
            isDestructive = true,
            onConfirm = {
                viewModel.unpinCertificate(certificate)
                pendingUnpin = null
            },
            onDismiss = { pendingUnpin = null },
        )
    }

    state.errorMessage?.let { error ->
        JamiAlertDialog(
            title = stringResource(Res.string.certificate),
            body = when (error) {
                CertificatesViewModel.PIN_FAILED ->
                    stringResource(Res.string.certificates_error_pin_failed)
                CertificatesViewModel.UNPIN_FAILED ->
                    stringResource(Res.string.certificates_error_unpin_failed)
                else -> stringResource(Res.string.certificates_error_status_failed)
            },
            // One button: there is nothing to cancel, the action already failed.
            dismissText = null,
            onConfirm = { viewModel.clearError() },
            onDismiss = { viewModel.clearError() },
        )
    }
}

@Composable
private fun CertificateRow(
    certificate: Certificate,
    isExpired: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onStatusSelected: (String) -> Unit,
    onUnpin: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = certificate.commonName,
                    style = JamiTheme.typography.bodyLarge,
                    color = JamiTheme.colors.onSurface,
                )
                val subtitle = listOfNotNull(
                    certificate.issuer?.takeIf { it.isNotBlank() },
                    if (certificate.isCertificateAuthority) {
                        stringResource(Res.string.cert_is_ca)
                    } else {
                        null
                    },
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = JamiTheme.typography.labelSmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }
                if (isExpired) {
                    CertificateWarning(stringResource(Res.string.certificates_expired))
                } else if (certificate.hasFailedChecks) {
                    CertificateWarning(
                        stringResource(
                            Res.string.certificates_checks_failed,
                            certificate.failedChecks.joinToString(", "),
                        )
                    )
                }
            }
            IconButton(onClick = onUnpin) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.certificates_unpin),
                    tint = JamiTheme.colors.onSurfaceVariant,
                )
            }
        }

        if (isExpanded) {
            CertificateDetails(
                certificate = certificate,
                onStatusSelected = onStatusSelected,
            )
        }
    }
}

@Composable
private fun CertificateDetails(
    certificate: Certificate,
    onStatusSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = JamiTheme.spacing.l,
                end = JamiTheme.spacing.l,
                bottom = JamiTheme.spacing.m,
            ),
    ) {
        DetailLine(
            label = stringResource(Res.string.cert_issuer),
            value = certificate.details[CertificateDetail.ISSUER_COMMON_NAME],
        )
        DetailLine(
            label = stringResource(Res.string.cert_valid_to),
            value = certificate.expirationDate,
        )
        DetailLine(
            label = stringResource(Res.string.cert_serial_number),
            value = certificate.details[CertificateDetail.SERIAL_NUMBER],
        )
        DetailLine(
            label = stringResource(Res.string.cert_signature_algorithm),
            value = certificate.details[CertificateDetail.SIGNATURE_ALGORITHM],
        )
        // The fingerprint is what someone compares against an out-of-band copy, so it is shown
        // in a monospace face and never shortened.
        DetailLine(
            label = stringResource(Res.string.cert_fingerprint),
            value = certificate.details[CertificateDetail.SHA1_FINGERPRINT],
            monospace = true,
        )

        Spacer(Modifier.height(JamiTheme.spacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(JamiTheme.spacing.s)) {
            for (status in CertificatesViewModel.statuses) {
                FilterChip(
                    selected = certificate.status == status,
                    onClick = { onStatusSelected(status) },
                    label = { Text(statusLabel(status)) },
                )
            }
        }
        if (certificate.status == CertificateStatus.ALLOWED) {
            CertificateWarning(stringResource(Res.string.certificates_status_allowed_warning))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String?, monospace: Boolean = false) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = JamiTheme.spacing.xs)) {
        Text(
            text = label,
            style = JamiTheme.typography.labelSmall,
            color = JamiTheme.colors.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = if (monospace) {
                JamiTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                JamiTheme.typography.labelSmall
            },
            color = JamiTheme.colors.onSurface,
            modifier = Modifier.weight(0.6f),
        )
    }
}

/** A problem with a certificate, in error colour so it is not read as ordinary detail. */
@Composable
private fun CertificateWarning(text: String) {
    Row(
        modifier = Modifier.padding(top = JamiTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = JamiTheme.colors.error,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(JamiTheme.spacing.xs))
        Text(
            text = text,
            style = JamiTheme.typography.labelSmall,
            color = JamiTheme.colors.error,
        )
    }
}

@Composable
private fun statusLabel(status: String): String = when (status) {
    CertificateStatus.ALLOWED -> stringResource(Res.string.certificates_status_allowed)
    CertificateStatus.BANNED -> stringResource(Res.string.certificates_status_banned)
    else -> stringResource(Res.string.certificates_status_undefined)
}

@Composable
private fun EmptyCertificates(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.certificates_empty),
            style = JamiTheme.typography.bodyMedium,
            color = JamiTheme.colors.onSurface,
        )
        Spacer(Modifier.height(JamiTheme.spacing.s))
        Text(
            text = stringResource(Res.string.certificates_empty_hint),
            style = JamiTheme.typography.labelSmall,
            color = JamiTheme.colors.onSurfaceVariant,
        )
    }
}
