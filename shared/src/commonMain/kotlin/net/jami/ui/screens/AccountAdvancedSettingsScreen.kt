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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import net.jami.di.getViewModel
import net.jami.ui.components.content.JamiSectionTitle
import net.jami.ui.components.content.JamiToggle
import net.jami.ui.platform.FilePickerEffect
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.AccountSubSettingsViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Account advanced settings screen.
 *
 * Sections:
 * - Name Server: RingNS URI
 * - OpenDHT: bootstrap node, DHT proxy, proxy list, local peer discovery
 * - Peer-to-Peer Connectivity: UPnP, TURN
 * - Audio-RTP Port Range: min/max
 *
 * @param onBack Called when the user navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountAdvancedSettingsScreen(
    onBack: () -> Unit,
) {
    val viewModel = getViewModel<AccountSubSettingsViewModel>()
    val state by viewModel.state.collectAsState()

    var showCaPicker by remember { mutableStateOf(false) }
    var showCertPicker by remember { mutableStateOf(false) }
    var showKeyPicker by remember { mutableStateOf(false) }

    FilePickerEffect(show = showCaPicker) { path ->
        showCaPicker = false
        if (path != null) viewModel.setTlsCaListFile(path)
    }
    FilePickerEffect(show = showCertPicker) { path ->
        showCertPicker = false
        if (path != null) viewModel.setTlsCertFile(path)
    }
    FilePickerEffect(show = showKeyPicker) { path ->
        showKeyPicker = false
        if (path != null) viewModel.setTlsPrivateKeyFile(path)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.account_preferences_advanced_tab),
                        color = JamiTheme.colors.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.export_side_step2_cancel),
                            tint = JamiTheme.colors.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JamiTheme.colors.surface,
                ),
            )
        },
        containerColor = JamiTheme.colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(JamiTheme.spacing.m))

            // ── Name Server ───────────────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.account_name_server_label))

            OutlinedTextField(
                value = state.nameServer,
                onValueChange = { viewModel.setNameServer(it) },
                label = { Text(stringResource(Res.string.account_name_server_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // ── OpenDHT ───────────────────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.account_dht_label))

            OutlinedTextField(
                value = state.bootstrapNode,
                onValueChange = { viewModel.setBootstrapNode(it) },
                label = { Text(stringResource(Res.string.account_bootstrap_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
            )

            HorizontalDivider(color = JamiTheme.colors.outline)

            JamiToggle(
                label = stringResource(Res.string.account_proxy_enable_label),
                checked = state.proxyEnabled,
                onCheckedChange = { viewModel.setProxyEnabled(it) },
            )

            if (state.proxyEnabled) {
                OutlinedTextField(
                    value = state.proxyServer,
                    onValueChange = { viewModel.setProxyServer(it) },
                    label = { Text(stringResource(Res.string.account_proxy_server_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                )
            }

            HorizontalDivider(color = JamiTheme.colors.outline)

            JamiToggle(
                label = stringResource(Res.string.account_proxy_list_enable_label),
                checked = state.proxyListEnabled,
                onCheckedChange = { viewModel.setProxyListEnabled(it) },
            )

            if (state.proxyListEnabled) {
                OutlinedTextField(
                    value = state.proxyListUrl,
                    onValueChange = { viewModel.setProxyListUrl(it) },
                    label = { Text(stringResource(Res.string.account_proxy_server_list_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                )
            }

            HorizontalDivider(color = JamiTheme.colors.outline)

            JamiToggle(
                label = stringResource(Res.string.account_peer_discovery_enable_label),
                description = stringResource(Res.string.account_peer_discovery_enable_summary),
                checked = state.peerDiscoveryEnabled,
                onCheckedChange = { viewModel.setPeerDiscovery(it) },
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // ── P2P Connectivity ──────────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.account_p2p_section_label))

            JamiToggle(
                label = stringResource(Res.string.account_upnp_label),
                checked = state.upnpEnabled,
                onCheckedChange = { viewModel.setUpnpEnabled(it) },
            )

            HorizontalDivider(color = JamiTheme.colors.outline)

            JamiToggle(
                label = stringResource(Res.string.account_turn_enable_label),
                checked = state.turnEnabled,
                onCheckedChange = { viewModel.setTurnEnabled(it) },
            )

            if (state.turnEnabled) {
                OutlinedTextField(
                    value = state.turnServer,
                    onValueChange = { viewModel.setTurnServer(it) },
                    label = { Text(stringResource(Res.string.account_turn_server_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                )
                OutlinedTextField(
                    value = state.turnUsername,
                    onValueChange = { viewModel.setTurnUsername(it) },
                    label = { Text(stringResource(Res.string.account_turn_username_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                )
                OutlinedTextField(
                    value = state.turnPassword,
                    onValueChange = { viewModel.setTurnPassword(it) },
                    label = { Text(stringResource(Res.string.account_turn_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                )
            }

            HorizontalDivider(color = JamiTheme.colors.outline)

            JamiToggle(
                label = stringResource(Res.string.account_stun_enable_label),
                checked = state.stunEnabled,
                onCheckedChange = { viewModel.setStunEnabled(it) },
            )

            if (state.stunEnabled) {
                OutlinedTextField(
                    value = state.stunServer,
                    onValueChange = { viewModel.setStunServer(it) },
                    label = { Text(stringResource(Res.string.account_stun_server_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // ── Audio-RTP Port Range ──────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.account_audio_rtp_label))

            OutlinedTextField(
                value = state.audioPortMin,
                onValueChange = { viewModel.setAudioPortMin(it) },
                label = { Text(stringResource(Res.string.account_audio_rtp_min_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
            )

            OutlinedTextField(
                value = state.audioPortMax,
                onValueChange = { viewModel.setAudioPortMax(it) },
                label = { Text(stringResource(Res.string.account_audio_rtp_max_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // ── Video-RTP Port Range ──────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.account_video_rtp_label))

            OutlinedTextField(
                value = state.videoPortMin,
                onValueChange = { viewModel.setVideoPortMin(it) },
                label = { Text(stringResource(Res.string.account_video_rtp_min_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
            )

            OutlinedTextField(
                value = state.videoPortMax,
                onValueChange = { viewModel.setVideoPortMax(it) },
                label = { Text(stringResource(Res.string.account_video_rtp_max_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // ── Security ──────────────────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.account_preferences_security_tab))

            JamiToggle(
                label = stringResource(Res.string.account_srtp_switch_label),
                checked = state.srtpKeyExchange,
                onCheckedChange = { viewModel.setSrtpKeyExchange(it) },
            )

            HorizontalDivider(color = JamiTheme.colors.outline)

            JamiToggle(
                label = stringResource(Res.string.account_tls_transport_switch_label),
                checked = state.tlsEnabled,
                onCheckedChange = { viewModel.setTlsEnabled(it) },
            )

            if (state.tlsEnabled) {
                OutlinedTextField(
                    value = state.tlsPort,
                    onValueChange = { viewModel.setTlsPort(it) },
                    label = { Text(stringResource(Res.string.account_tls_port_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                )

                HorizontalDivider(color = JamiTheme.colors.outline)

                CertFileRow(
                    label = stringResource(Res.string.account_tls_certificate_list_label),
                    filePath = state.tlsCaListFile,
                    onClick = { showCaPicker = true },
                )

                HorizontalDivider(color = JamiTheme.colors.outline)

                CertFileRow(
                    label = stringResource(Res.string.account_tls_certificate_file_label),
                    filePath = state.tlsCertFile,
                    onClick = { showCertPicker = true },
                )

                HorizontalDivider(color = JamiTheme.colors.outline)

                CertFileRow(
                    label = stringResource(Res.string.account_tls_private_key_file_label),
                    filePath = state.tlsPrivateKeyFile,
                    onClick = { showKeyPicker = true },
                )

                OutlinedTextField(
                    value = state.tlsPassword,
                    onValueChange = { viewModel.setTlsPassword(it) },
                    label = { Text(stringResource(Res.string.account_tls_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                )

                OutlinedTextField(
                    value = state.tlsMethod,
                    onValueChange = { viewModel.setTlsMethod(it) },
                    label = { Text(stringResource(Res.string.account_tls_method_label)) },
                    singleLine = true,
                    placeholder = { Text("TLSv1.2") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                )

                OutlinedTextField(
                    value = state.tlsCiphers,
                    onValueChange = { viewModel.setTlsCiphers(it) },
                    label = { Text(stringResource(Res.string.account_tls_ciphers_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                )

                OutlinedTextField(
                    value = state.tlsServerName,
                    onValueChange = { viewModel.setTlsServerName(it) },
                    label = { Text(stringResource(Res.string.account_tls_server_name_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                )

                HorizontalDivider(color = JamiTheme.colors.outline)

                JamiToggle(
                    label = stringResource(Res.string.account_tls_verify_server_label),
                    checked = state.tlsVerifyServer,
                    onCheckedChange = { viewModel.setTlsVerifyServer(it) },
                )

                HorizontalDivider(color = JamiTheme.colors.outline)

                JamiToggle(
                    label = stringResource(Res.string.account_tls_verify_client_label),
                    checked = state.tlsVerifyClient,
                    onCheckedChange = { viewModel.setTlsVerifyClient(it) },
                )

                HorizontalDivider(color = JamiTheme.colors.outline)

                JamiToggle(
                    label = stringResource(Res.string.account_require_client_certificate_label),
                    checked = state.tlsRequireClientCert,
                    onCheckedChange = { viewModel.setTlsRequireClientCert(it) },
                )

                OutlinedTextField(
                    value = state.tlsNegotiationTimeout,
                    onValueChange = { viewModel.setTlsNegotiationTimeout(it) },
                    label = { Text(stringResource(Res.string.account_tls_negotiation_timeout_sec)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.xxl))
        }
    }
}

@Composable
private fun CertFileRow(
    label: String,
    filePath: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = JamiTheme.typography.bodyLarge,
                color = JamiTheme.colors.onSurface,
            )
            if (filePath.isNotEmpty()) {
                Text(
                    text = filePath.substringAfterLast('/'),
                    style = JamiTheme.typography.bodySmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = null,
            tint = JamiTheme.colors.onSurfaceVariant,
        )
    }
}
