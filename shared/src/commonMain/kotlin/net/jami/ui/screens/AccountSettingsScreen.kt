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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.jami.di.getViewModel
import net.jami.services.AuthError
import net.jami.ui.components.QrCodeScannerView
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.JamiSectionTitle
import net.jami.ui.components.inputs.JamiFormattedTextField
import net.jami.ui.platform.FilePickerEffect
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.AccountSettingsViewModel
import net.jami.ui.viewmodel.AddDeviceExportState
import net.jami.ui.viewmodel.DeviceItem
import net.jami.ui.viewmodel.ExportInputError
import net.jami.ui.viewmodel.UsernameCheckError
import net.jami.utils.FileUtils
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
 * @param onMedia Called when the Media settings row is tapped.
 * @param onMessages Called when the Messages settings row is tapped.
 * @param onAdvanced Called when the Advanced settings row is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    onBlockedContacts: () -> Unit,
    onAccount: () -> Unit = {},
    onMedia: () -> Unit = {},
    onMessages: () -> Unit = {},
    onAdvanced: () -> Unit = {},
) {
    val viewModel = getViewModel<AccountSettingsViewModel>()
    val state by viewModel.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showQrSheet by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showRenameDeviceDialog by remember { mutableStateOf(false) }
    var showLinkDeviceSheet by remember { mutableStateOf(false) }
    var showPhotoPicker by remember { mutableStateOf(false) }
    var showPhotoMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Local editable display name — syncs from state
    var displayNameEdit by remember { mutableStateOf(state.displayName) }
    LaunchedEffect(state.displayName) { displayNameEdit = state.displayName }

    val shareSubject = stringResource(Res.string.account_contact_me)
    val shareBody = stringResource(
        Res.string.account_share_body,
        state.username.ifEmpty { state.identityHash },
        "https://jami.net",
    )
    val linkSuccessMsg = stringResource(Res.string.account_link_device_success)
    val linkErrorMsg = stringResource(Res.string.account_link_device_error)

    LaunchedEffect(Unit) {
        viewModel.loadAccount()
    }

    // Close the sheet and show a snackbar when linking completes successfully.
    // Errors are shown inline inside the sheet — no snackbar needed for them.
    LaunchedEffect(state.linkDeviceState) {
        val s = state.linkDeviceState
        if (s is AddDeviceExportState.Done && s.error == null) {
            showLinkDeviceSheet = false
            snackbarHostState.showSnackbar(linkSuccessMsg)
            viewModel.cancelLinkDevice()
        }
    }

    val registerNameSuccessMsg = stringResource(Res.string.register_name_success)
    val registerNameFailedMsg = stringResource(Res.string.register_name_failed)
    LaunchedEffect(state.registerNameResult) {
        when (state.registerNameResult) {
            true -> {
                snackbarHostState.showSnackbar(registerNameSuccessMsg)
                viewModel.clearRegisterNameResult()
            }
            false -> {
                snackbarHostState.showSnackbar(registerNameFailedMsg)
                viewModel.clearRegisterNameResult()
            }
            null -> Unit
        }
    }

    if (state.registerNameDialogOpen) {
        RegisterNameDialog(
            state = state,
            onNameChange = { viewModel.setRegisterNameInput(it) },
            onConfirm = { password -> viewModel.confirmRegisterName(password) },
            onDismiss = { viewModel.dismissRegisterNameDialog() },
        )
    }

    // Image picker effect — reads selected image bytes and updates the avatar
    FilePickerEffect(show = showPhotoPicker, mimeTypes = listOf("image/*")) { path ->
        showPhotoPicker = false
        if (path != null) {
            coroutineScope.launch(Dispatchers.Default) {
                val bytes = FileUtils.readBytes(path)
                withContext(Dispatchers.Main) { viewModel.updateAvatar(bytes) }
            }
        }
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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.account_delete_dialog_title)) },
            text = { Text(stringResource(Res.string.account_delete_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.removeAccount() }) {
                    Text(stringResource(Res.string.menu_delete), color = JamiTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.export_side_step2_cancel))
                }
            },
        )
    }

    if (showRenameDeviceDialog) {
        RenameDeviceDialog(
            currentName = state.currentDeviceName,
            onDismiss = { showRenameDeviceDialog = false },
            onRename = { name ->
                showRenameDeviceDialog = false
                viewModel.renameCurrentDevice(name)
            },
        )
    }
    var textValue by remember { mutableStateOf(TextFieldValue()) }
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
            JamiFormattedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = textValue,
                onValueChange = { textValue = it },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Avatar — tappable: opens a menu to pick from gallery or remove photo
                Box {
                    Box(modifier = Modifier.clickable { showPhotoMenu = true }) {
                        JamiAvatar(
                            displayName = state.displayName.ifEmpty { state.username },
                            avatarBytes = state.avatarBytes,
                            size = AvatarSize.Large,
                        )
                    }
                    DropdownMenu(
                        expanded = showPhotoMenu,
                        onDismissRequest = { showPhotoMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.open_the_gallery)) },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = { showPhotoMenu = false; showPhotoPicker = true },
                        )
                        if (state.avatarBytes != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.remove_photo)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = { showPhotoMenu = false; viewModel.updateAvatar(null) },
                            )
                        }
                    }
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
                if (state.username.isNotEmpty()) {
                    LabelValueRow(
                        label = stringResource(Res.string.registered_username),
                        value = state.username,
                        valueBold = true,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.no_registered_name_for_account),
                            style = JamiTheme.typography.bodyMedium,
                            color = JamiTheme.colors.onSurfaceVariant,
                        )
                        TextButton(onClick = { viewModel.openRegisterNameDialog() }) {
                            Text(
                                text = stringResource(Res.string.register_name),
                                color = JamiTheme.colors.primary,
                            )
                        }
                    }
                }

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
                            shareText(shareSubject, shareBody)
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
                    IconButton(onClick = { showRenameDeviceDialog = true }) {
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
                                stringResource(Res.string.account_link_show_button, otherDevices.size),
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
                    onClick = { showLinkDeviceSheet = true },
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
                    label = stringResource(Res.string.navigation_item_account),
                    icon = Icons.Default.AccountCircle,
                    onClick = onAccount,
                )
                HorizontalDivider(color = JamiTheme.colors.outline)
                SettingsCardRow(
                    label = stringResource(Res.string.screen_title_blocked_contacts),
                    icon = Icons.Default.Block,
                    onClick = onBlockedContacts,
                )
                HorizontalDivider(color = JamiTheme.colors.outline)
                SettingsCardRow(
                    label = stringResource(Res.string.account_delete_label),
                    icon = Icons.Default.Delete,
                    onClick = { showDeleteDialog = true },
                    tint = JamiTheme.colors.error,
                    labelColor = JamiTheme.colors.error,
                )
                HorizontalDivider(color = JamiTheme.colors.outline)
                SettingsCardRow(
                    label = stringResource(Res.string.account_preferences_media_tab),
                    icon = Icons.Default.Image,
                    onClick = onMedia,
                )
                HorizontalDivider(color = JamiTheme.colors.outline)
                SettingsCardRow(
                    label = stringResource(Res.string.pref_category_messages),
                    icon = Icons.Default.ChatBubble,
                    onClick = onMessages,
                )
                HorizontalDivider(color = JamiTheme.colors.outline)
                SettingsCardRow(
                    label = stringResource(Res.string.account_preferences_advanced_tab),
                    icon = Icons.Default.Settings,
                    onClick = onAdvanced,
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

        // Link Device Bottom Sheet
        if (showLinkDeviceSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showLinkDeviceSheet = false
                    viewModel.cancelLinkDevice()
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = JamiTheme.colors.surface,
            ) {
                LinkDeviceSheetContent(
                    linkState = state.linkDeviceState,
                    onLink = { uri -> viewModel.startLinkDevice(uri) },
                    onConfirm = { viewModel.onIdentityConfirmation() },
                    onCancel = {
                        showLinkDeviceSheet = false
                        viewModel.cancelLinkDevice()
                    },
                )
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
    tint: androidx.compose.ui.graphics.Color = JamiTheme.colors.onSurfaceVariant,
    labelColor: androidx.compose.ui.graphics.Color = JamiTheme.colors.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = JamiTheme.spacing.m, vertical = JamiTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(JamiTheme.spacing.m))
        Text(
            text = label,
            style = JamiTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = labelColor,
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
 * Dialog for renaming the current device.
 */
@Composable
private fun RenameDeviceDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    val focusManager = LocalFocusManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.account_rename_device)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onRename(name) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(Res.string.action_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.export_side_step2_cancel))
            }
        },
    )
}

/**
 * Content for the "Link New Device" bottom sheet.
 *
 * The user pastes the device-request URI generated on the new device.
 * Multi-step sheet for linking another device to this account (export side).
 *
 * Steps mirror jami-client-android ExportSideViewModel / export_side_step* strings:
 *  - Init      → scan QR or paste URI manually
 *  - Connecting→ spinner while daemon contacts the peer
 *  - Authenticating → show peer address, ask user to confirm identity
 *  - InProgress → spinner while account data transfers
 *  - Done(ok)  → handled by parent (sheet closes + snackbar)
 *  - Done(err) → inline error + dismiss button
 */
@Composable
private fun LinkDeviceSheetContent(
    linkState: AddDeviceExportState,
    onLink: (uri: String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var manualEntry by remember { mutableStateOf(false) }
    var uri by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = JamiTheme.spacing.l)
            .padding(bottom = JamiTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AddLink,
                contentDescription = null,
                tint = JamiTheme.colors.onSurface,
            )
            Spacer(Modifier.width(JamiTheme.spacing.s))
            Text(
                text = stringResource(Res.string.account_link_device_title),
                style = JamiTheme.typography.titleMedium,
                color = JamiTheme.colors.onSurface,
            )
        }
        Spacer(Modifier.height(JamiTheme.spacing.m))

        when (linkState) {
            // ── Step 1: scan QR or paste URI ─────────────────────────────────
            is AddDeviceExportState.Init -> {
                if (linkState.error != null) {
                    Text(
                        text = stringResource(Res.string.account_link_device_error),
                        color = JamiTheme.colors.error,
                        style = JamiTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(JamiTheme.spacing.s))
                }

                if (manualEntry) {
                    Text(
                        text = stringResource(Res.string.account_link_device_hint),
                        style = JamiTheme.typography.bodyMedium,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(JamiTheme.spacing.m))
                    OutlinedTextField(
                        value = uri,
                        onValueChange = { uri = it },
                        label = { Text(stringResource(Res.string.account_link_device_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (uri.isNotBlank()) onLink(uri.trim())
                        }),
                    )
                    Spacer(Modifier.height(JamiTheme.spacing.s))
                    TextButton(onClick = { manualEntry = false; uri = "" }) {
                        Text(stringResource(Res.string.export_side_step1_switch_to_qr))
                    }
                    Spacer(Modifier.height(JamiTheme.spacing.m))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(Res.string.export_side_step2_cancel))
                        }
                        Spacer(Modifier.width(JamiTheme.spacing.s))
                        Button(
                            onClick = { focusManager.clearFocus(); onLink(uri.trim()) },
                            enabled = uri.isNotBlank(),
                        ) {
                            Text(stringResource(Res.string.action_link_device))
                        }
                    }
                } else {
                    Text(
                        text = stringResource(Res.string.export_side_step1_advice_qr),
                        style = JamiTheme.typography.bodyMedium,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(JamiTheme.spacing.m))
                    QrCodeScannerView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        onQrDetected = { scanned -> onLink(scanned) },
                    )
                    Spacer(Modifier.height(JamiTheme.spacing.s))
                    TextButton(onClick = { manualEntry = true }) {
                        Text(stringResource(Res.string.export_side_step1_switch_to_code))
                    }
                    Spacer(Modifier.height(JamiTheme.spacing.m))
                    TextButton(
                        modifier = Modifier.align(Alignment.End),
                        onClick = onCancel,
                    ) {
                        Text(stringResource(Res.string.export_side_step2_cancel))
                    }
                }
            }

            // ── Step 2: connecting (daemon contacting peer) ──────────────────
            is AddDeviceExportState.Connecting -> {
                Spacer(Modifier.height(JamiTheme.spacing.xl))
                CircularProgressIndicator()
                Spacer(Modifier.height(JamiTheme.spacing.m))
                Text(
                    text = stringResource(Res.string.import_side_step1_connecting),
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(JamiTheme.spacing.xl))
                TextButton(
                    modifier = Modifier.align(Alignment.End),
                    onClick = onCancel,
                ) {
                    Text(stringResource(Res.string.export_side_step2_cancel))
                }
            }

            // ── Step 3: confirm peer identity ────────────────────────────────
            is AddDeviceExportState.Authenticating -> {
                Text(
                    text = if (linkState.peerAddress != null)
                        stringResource(Res.string.export_side_step2_advice)
                    else
                        stringResource(Res.string.export_side_step2_advice_ip_only),
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
                if (linkState.peerAddress != null) {
                    Spacer(Modifier.height(JamiTheme.spacing.s))
                    Text(
                        text = linkState.peerAddress,
                        style = JamiTheme.typography.bodySmall,
                        color = JamiTheme.colors.onSurface,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(JamiTheme.spacing.m))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(Res.string.export_side_step2_cancel))
                    }
                    Spacer(Modifier.width(JamiTheme.spacing.s))
                    Button(onClick = onConfirm) {
                        Text(stringResource(Res.string.export_side_step2_confirm))
                    }
                }
            }

            // ── Step 4: transfer in progress ─────────────────────────────────
            is AddDeviceExportState.InProgress -> {
                Spacer(Modifier.height(JamiTheme.spacing.xl))
                CircularProgressIndicator()
                Spacer(Modifier.height(JamiTheme.spacing.m))
                Text(
                    text = stringResource(Res.string.export_side_step3_body_loading),
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(JamiTheme.spacing.xl))
            }

            // ── Step 5: done — success handled by parent; only errors shown ──
            is AddDeviceExportState.Done -> {
                val errorMsg = when (linkState.error) {
                    AuthError.NETWORK        -> stringResource(Res.string.link_device_error_network)
                    AuthError.AUTHENTICATION -> stringResource(Res.string.link_device_error_authentication)
                    AuthError.TIMEOUT        -> stringResource(Res.string.link_device_error_timeout)
                    AuthError.CANCELED       -> stringResource(Res.string.link_device_error_canceled)
                    AuthError.UNKNOWN, null  -> stringResource(Res.string.link_device_error_unknown)
                }
                Spacer(Modifier.height(JamiTheme.spacing.m))
                Text(
                    text = errorMsg,
                    color = JamiTheme.colors.error,
                    style = JamiTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(JamiTheme.spacing.m))
                Button(
                    modifier = Modifier.align(Alignment.End),
                    onClick = onCancel,
                ) {
                    Text(stringResource(Res.string.export_side_step3_exit))
                }
            }
        }
    }
}

/**
 * Dialog for registering a public username on the name server.
 * Shows a username field with live availability feedback and an optional
 * password field for password-protected accounts.
 */
@Composable
private fun RegisterNameDialog(
    state: net.jami.ui.viewmodel.AccountSettingsState,
    onNameChange: (String) -> Unit,
    onConfirm: (password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val canConfirm = state.registerNameAvailable == true && !state.registerNameInProgress

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.register_username)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(JamiTheme.spacing.s)) {
                OutlinedTextField(
                    value = state.registerNameInput,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(Res.string.register_name)) },
                    singleLine = true,
                    isError = state.registerNameAvailable == false,
                    trailingIcon = when {
                        state.registerNameChecking -> {{ CircularProgressIndicator(modifier = androidx.compose.ui.Modifier.size(20.dp), strokeWidth = 2.dp) }}
                        state.registerNameAvailable == true -> {{ Icon(androidx.compose.material.icons.Icons.Default.AccountCircle, null, tint = JamiTheme.colors.primary) }}
                        else -> null
                    },
                    supportingText = when {
                        state.registerNameChecking -> {{ Text(stringResource(Res.string.looking_for_username_availability)) }}
                        state.registerNameAvailable == true -> {{ Text(stringResource(Res.string.username_available), color = JamiTheme.colors.primary) }}
                        state.registerNameAvailable == false && state.registerNameError == null -> {{ Text(stringResource(Res.string.username_already_taken), color = JamiTheme.colors.error) }}
                        state.registerNameError == UsernameCheckError.INVALID -> {{ Text(stringResource(Res.string.invalid_username), color = JamiTheme.colors.error) }}
                        state.registerNameError != null -> {{ Text(stringResource(Res.string.unknown_error), color = JamiTheme.colors.error) }}
                        else -> null
                    },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                )
                if (state.hasPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(Res.string.account_password_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    )
                }
                if (state.registerNameInProgress) {
                    Text(
                        text = stringResource(Res.string.trying_to_register_name),
                        style = JamiTheme.typography.bodySmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = canConfirm && (!state.hasPassword || password.isNotEmpty()),
            ) {
                Text(stringResource(Res.string.register_name))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.export_side_step2_cancel))
            }
        },
    )
}

