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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.jami.di.getViewModel
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.JamiSectionTitle
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.AccountSettingsViewModel
import net.jami.ui.viewmodel.DeviceItem
import net.jami.utils.QRCodeColors
import net.jami.utils.QRCodeUtils
import net.jami.utils.clearFocusOnTap
import net.jami.utils.shareText
import org.jetbrains.compose.resources.stringResource

/**
 * Account settings screen mirroring the official jami-client-android layout:
 * PROFIL (avatar + inline name) / KONTO card / GERÄTE card / EINSTELLUNGEN card.
 *
 * @param onBack Called when the user navigates back.
 * @param onBlockedContacts Called when "Blocked Contacts" is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    onBlockedContacts: () -> Unit,
) {
    val viewModel = getViewModel<AccountSettingsViewModel>()
    val state by viewModel.state.collectAsState()
    var showExportDialog by remember { mutableStateOf(false) }
    var showQrSheet by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Local editable display name — syncs from state
    var displayNameEdit by remember { mutableStateOf(state.displayName) }
    LaunchedEffect(state.displayName) { displayNameEdit = state.displayName }

    val exportSuccessMsg = stringResource(Res.string.snackbar_export_success)
    val exportErrorMsg = stringResource(Res.string.snackbar_export_error)
    val shareSubject = stringResource(Res.string.account_contact_me)
    val shareBodyTemplate = stringResource(Res.string.account_share_body)

    LaunchedEffect(Unit) {
        viewModel.loadAccount()
    }

    // Generate QR code bitmap when sheet opens
    LaunchedEffect(showQrSheet, state.identityHash) {
        if (showQrSheet && state.identityHash.isNotEmpty() && qrBitmap == null) {
            val bitmap = withContext(Dispatchers.Default) {
                val qrData = QRCodeUtils.encodeStringAsQRCodeData(
                    state.identityHash,
                    QRCodeColors.BLACK,
                    QRCodeColors.WHITE,
                ) ?: return@withContext null

                val imageBitmap = ImageBitmap(qrData.width, qrData.height)
                val canvas = androidx.compose.ui.graphics.Canvas(imageBitmap)
                val bgPaint = Paint().apply { color = Color.White }
                canvas.drawRect(
                    Rect(0f, 0f, qrData.width.toFloat(), qrData.height.toFloat()),
                    bgPaint,
                )
                val fgPoints = mutableListOf<Float>()
                for (i in qrData.data.indices) {
                    if (qrData.data[i] != QRCodeColors.WHITE) {
                        fgPoints.add((i % qrData.width) + 0.5f)
                        fgPoints.add((i / qrData.width) + 0.5f)
                    }
                }
                val fgPaint = Paint().apply {
                    color = Color.Black
                    strokeWidth = 1f
                    strokeCap = StrokeCap.Square
                }
                canvas.drawRawPoints(PointMode.Points, fgPoints.toFloatArray(), fgPaint)
                imageBitmap
            }
            qrBitmap = bitmap
        } else if (!showQrSheet) {
            qrBitmap = null
        }
    }

    if (showExportDialog) {
        ExportAccountDialog(
            onDismiss = { showExportDialog = false },
            onExport = { password ->
                showExportDialog = false
                val success = viewModel.exportAccount(password)
                coroutineScope.launch {
                    if (success) snackbarHostState.showSnackbar(exportSuccessMsg)
                    else snackbarHostState.showSnackbar(exportErrorMsg)
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.screen_title_account_settings),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = JamiTheme.spacing.m),
                    ) {
                        Text(
                            text = if (state.isOnline)
                                stringResource(Res.string.account_status_online)
                            else
                                stringResource(Res.string.account_status_offline),
                            style = JamiTheme.typography.bodySmall,
                            color = if (state.isOnline) JamiTheme.colors.positive
                                    else JamiTheme.colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(JamiTheme.spacing.xs))
                        Switch(
                            checked = state.isOnline,
                            onCheckedChange = { viewModel.setOnline(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = JamiTheme.colors.positive,
                            ),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JamiTheme.colors.surface,
                    titleContentColor = JamiTheme.colors.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clearFocusOnTap()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── PROFIL ────────────────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.profile))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Avatar — tappable (photo picker placeholder)
                Box(modifier = Modifier.clickable { /* TODO: photo picker */ }) {
                    JamiAvatar(
                        displayName = state.displayName.ifEmpty { state.username },
                        avatarBytes = state.avatarBytes,
                        size = AvatarSize.Large,
                    )
                }

                Spacer(Modifier.width(JamiTheme.spacing.m))

                OutlinedTextField(
                    value = displayNameEdit,
                    onValueChange = { displayNameEdit = it },
                    label = { Text(stringResource(Res.string.profile_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        IconButton(onClick = {
                            viewModel.updateDisplayName(displayNameEdit)
                            focusManager.clearFocus()
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.updateDisplayName(displayNameEdit)
                        focusManager.clearFocus()
                    }),
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // ── KONTO ─────────────────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.navigation_item_account))

            AccountCard {
                // Registered username row
                LabelValueRow(
                    label = stringResource(Res.string.registered_username),
                    value = state.username.ifEmpty {
                        stringResource(Res.string.no_registered_name_for_account)
                    },
                    valueBold = state.username.isNotEmpty(),
                )

                HorizontalDivider(color = JamiTheme.colors.outline)

                // Identity hash row
                LabelValueRow(
                    label = stringResource(Res.string.identity),
                    value = state.identityHash,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                HorizontalDivider(color = JamiTheme.colors.outline)

                // Share + QR-Code buttons
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            val body = shareBodyTemplate.format(
                                state.username.ifEmpty { state.identityHash },
                                "https://jami.net",
                            )
                            shareText(shareSubject, body)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(JamiTheme.spacing.xs))
                        Text(stringResource(Res.string.account_contact_me))
                    }
                    // Vertical separator
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(48.dp)
                            .background(JamiTheme.colors.outline)
                            .align(Alignment.CenterVertically),
                    )
                    TextButton(
                        onClick = { showQrSheet = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null)
                        Spacer(Modifier.width(JamiTheme.spacing.xs))
                        Text(stringResource(Res.string.qr_code))
                    }
                }
            }

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // ── GERÄTE ────────────────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.devices_header))

            val otherDevices = state.devices.filter { !it.isCurrent }
            var devicesExpanded by remember { mutableStateOf(false) }

            AccountCard {
                // Current device row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = JamiTheme.spacing.m,
                            vertical = JamiTheme.spacing.s,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.account_device_this_indicator),
                            style = JamiTheme.typography.labelSmall,
                            color = JamiTheme.colors.onSurfaceVariant,
                        )
                        Text(
                            text = state.currentDeviceName.ifEmpty { "—" },
                            style = JamiTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = JamiTheme.colors.onSurface,
                        )
                    }
                    IconButton(onClick = { /* TODO: rename device */ }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = JamiTheme.colors.onSurfaceVariant,
                        )
                    }
                }

                // "Show N more" expandable row
                if (otherDevices.isNotEmpty()) {
                    HorizontalDivider(color = JamiTheme.colors.outline)
                    TextButton(
                        onClick = { devicesExpanded = !devicesExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (devicesExpanded)
                                stringResource(Res.string.section_linked_devices)
                            else
                                stringResource(Res.string.account_link_show_button)
                                    .format(otherDevices.size),
                            color = JamiTheme.colors.onSurface,
                        )
                    }
                    if (devicesExpanded) {
                        otherDevices.forEach { device ->
                            HorizontalDivider(color = JamiTheme.colors.outline)
                            DeviceRow(device = device)
                        }
                    }
                }

                HorizontalDivider(color = JamiTheme.colors.outline)

                // Link new device
                TextButton(
                    onClick = { /* TODO: link new device */ },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.AddLink, contentDescription = null)
                    Spacer(Modifier.width(JamiTheme.spacing.xs))
                    Text(
                        text = stringResource(Res.string.link_new_device),
                        color = JamiTheme.colors.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // ── EINSTELLUNGEN ─────────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.menu_item_settings))

            AccountCard {
                SettingsCardRow(
                    label = stringResource(Res.string.pref_category_account),
                    icon = Icons.Default.AccountCircle,
                    onClick = { showExportDialog = true },
                )
                HorizontalDivider(color = JamiTheme.colors.outline)
                SettingsCardRow(
                    label = stringResource(Res.string.account_preferences_media_tab),
                    icon = Icons.Default.Image,
                    onClick = { /* TODO */ },
                )
                HorizontalDivider(color = JamiTheme.colors.outline)
                SettingsCardRow(
                    label = stringResource(Res.string.pref_category_messages),
                    icon = Icons.Default.ChatBubble,
                    onClick = { /* TODO */ },
                )
                HorizontalDivider(color = JamiTheme.colors.outline)
                SettingsCardRow(
                    label = stringResource(Res.string.account_preferences_advanced_tab),
                    icon = Icons.Default.Settings,
                    onClick = { /* TODO */ },
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.xxl))
        }

        // QR Code Bottom Sheet
        if (showQrSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQrSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = JamiTheme.colors.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.xl)
                        .padding(bottom = JamiTheme.spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = JamiTheme.colors.onSurface,
                        )
                        Spacer(Modifier.width(JamiTheme.spacing.s))
                        Text(
                            text = stringResource(Res.string.show_qr_code),
                            style = JamiTheme.typography.titleMedium,
                            color = JamiTheme.colors.onSurface,
                        )
                    }

                    Spacer(Modifier.height(JamiTheme.spacing.xl))

                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .background(Color.White, RoundedCornerShape(JamiTheme.spacing.m)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap!!,
                                contentDescription = stringResource(Res.string.show_qr_code),
                                modifier = Modifier
                                    .size(216.dp)
                                    .padding(JamiTheme.spacing.s),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                    }

                    Spacer(Modifier.height(JamiTheme.spacing.m))

                    Text(
                        text = state.identityHash,
                        style = JamiTheme.typography.bodySmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(JamiTheme.spacing.xl))
                }
            }
        }
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

/**
 * Rounded card container used for KONTO, GERÄTE, and EINSTELLUNGEN sections.
 */
@Composable
private fun AccountCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = JamiTheme.colors.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = JamiTheme.spacing.l),
    ) {
        Column(content = content)
    }
}

/**
 * Two-line label + value row inside an AccountCard.
 */
@Composable
private fun LabelValueRow(
    label: String,
    value: String,
    valueBold: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Column(
        modifier = Modifier.padding(
            horizontal = JamiTheme.spacing.m,
            vertical = JamiTheme.spacing.s,
        ),
    ) {
        Text(
            text = label,
            style = JamiTheme.typography.labelSmall,
            color = JamiTheme.colors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (valueBold) JamiTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    else JamiTheme.typography.bodyLarge,
            color = JamiTheme.colors.onSurface,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}

/**
 * Icon + bold label row inside the EINSTELLUNGEN card.
 */
@Composable
private fun SettingsCardRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = JamiTheme.spacing.m, vertical = JamiTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = JamiTheme.colors.onSurfaceVariant)
        Spacer(Modifier.width(JamiTheme.spacing.m))
        Text(
            text = label,
            style = JamiTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = JamiTheme.colors.onSurface,
        )
    }
}

/**
 * Row displaying a non-current linked device.
 */
@Composable
private fun DeviceRow(device: DeviceItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = JamiTheme.spacing.m, vertical = JamiTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.deviceName,
                style = JamiTheme.typography.bodyLarge,
                color = JamiTheme.colors.onSurface,
            )
            Text(
                text = device.deviceId,
                style = JamiTheme.typography.bodySmall,
                color = JamiTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Dialog for exporting the account with a password.
 */
@Composable
private fun ExportAccountDialog(
    onDismiss: () -> Unit,
    onExport: (password: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_export_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.dialog_export_message),
                    style = JamiTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(JamiTheme.spacing.m))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.prompt_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus(); onExport(password) },
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(password) }) {
                Text(stringResource(Res.string.action_export))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
