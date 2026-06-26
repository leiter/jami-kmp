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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.jami.di.getViewModel
import net.jami.services.AuthError
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.AddDeviceImportState
import net.jami.ui.viewmodel.LinkDeviceImportViewModel
import net.jami.utils.QRCodeColors
import net.jami.utils.QRCodeUtils
import org.jetbrains.compose.resources.stringResource

/**
 * Screen shown on the new device during account linking (import side).
 *
 * Steps mirror jami-client-android import_side_step* strings:
 *  - Init/TokenAvailable → display the authentication token as a QR code for
 *    the existing device to scan (plus the raw token text as fallback).
 *  - Connecting          → spinner while the peer validates the token.
 *  - Authenticating      → confirm peer Jami ID; optional password entry.
 *  - InProgress          → spinner while account data transfers.
 *  - Done(ok)            → parent navigates onward.
 *  - Done(err)           → inline error + retry (go back).
 *
 * @param onBack     Called on back-press (cancels the operation).
 * @param onSuccess  Called when [AddDeviceImportState.Done] with no error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkDeviceImportScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    val viewModel = getViewModel<LinkDeviceImportViewModel>()
    val uiState by viewModel.state.collectAsState()

    // The temp account is cleaned up in the ViewModel's onCleared(), invoked automatically
    // when this screen's ViewModelStoreOwner is destroyed (e.g. the user navigates away).

    // Navigate away on success.
    LaunchedEffect(uiState) {
        if (uiState is AddDeviceImportState.Done && (uiState as AddDeviceImportState.Done).error == null) {
            onSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.account_link_device_title)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onCancel(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_desc_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JamiTheme.colors.surface,
                    titleContentColor = JamiTheme.colors.onSurface,
                    navigationIconContentColor = JamiTheme.colors.onSurface,
                ),
            )
        },
        containerColor = JamiTheme.colors.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = JamiTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val s = uiState) {
                is AddDeviceImportState.Init -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(JamiTheme.spacing.m))
                    Text(
                        text = stringResource(Res.string.import_side_step1_preparing_device),
                        style = JamiTheme.typography.bodyMedium,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }

                is AddDeviceImportState.TokenAvailable -> {
                    TokenStep(token = s.token)
                }

                is AddDeviceImportState.Connecting -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(JamiTheme.spacing.m))
                    Text(
                        text = stringResource(Res.string.import_side_step1_connecting),
                        style = JamiTheme.typography.bodyMedium,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }

                is AddDeviceImportState.Authenticating -> {
                    AuthenticatingStep(
                        state = s,
                        onConfirm = { password -> viewModel.onAuthentication(password) },
                        onCancel = { viewModel.onCancel(); onBack() },
                    )
                }

                is AddDeviceImportState.InProgress -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(JamiTheme.spacing.m))
                    Text(
                        text = stringResource(Res.string.import_side_step3_body_loading),
                        style = JamiTheme.typography.bodyMedium,
                        color = JamiTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                is AddDeviceImportState.Done -> {
                    if (s.error != null) {
                        DoneErrorStep(error = s.error, onClose = onBack)
                    }
                    // success case is handled by LaunchedEffect above
                }
            }
        }
    }
}

@Composable
private fun TokenStep(token: String) {
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(token) {
        qrBitmap = withContext(Dispatchers.Default) {
            val qrData = QRCodeUtils.encodeStringAsQRCodeData(token, QRCodeColors.BLACK, QRCodeColors.WHITE)
                ?: return@withContext null
            val bmp = ImageBitmap(qrData.width, qrData.height)
            val canvas = androidx.compose.ui.graphics.Canvas(bmp)
            val bg = Paint().apply { color = Color.White }
            canvas.drawRect(Rect(0f, 0f, qrData.width.toFloat(), qrData.height.toFloat()), bg)
            val pts = mutableListOf<Float>()
            for (i in qrData.data.indices) {
                if (qrData.data[i] != QRCodeColors.WHITE) {
                    pts.add((i % qrData.width) + 0.5f)
                    pts.add((i / qrData.width) + 0.5f)
                }
            }
            val fg = Paint().apply { color = Color.Black; strokeWidth = 1f; strokeCap = StrokeCap.Square }
            canvas.drawRawPoints(PointMode.Points, pts.toFloatArray(), fg)
            bmp
        }
    }

    Text(
        text = stringResource(Res.string.import_side_step1_advice_qr),
        style = JamiTheme.typography.bodyMedium,
        color = JamiTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(JamiTheme.spacing.l))

    if (qrBitmap != null) {
        Image(
            bitmap = qrBitmap!!,
            contentDescription = stringResource(Res.string.qrcode_to_scan),
            modifier = Modifier.size(240.dp),
            contentScale = ContentScale.FillBounds,
        )
    } else {
        Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }

    Spacer(Modifier.height(JamiTheme.spacing.m))
    Text(
        text = token,
        style = JamiTheme.typography.bodySmall,
        color = JamiTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = JamiTheme.spacing.m),
    )
}

@Composable
private fun AuthenticatingStep(
    state: AddDeviceImportState.Authenticating,
    onConfirm: (password: String) -> Unit,
    onCancel: () -> Unit,
) {
    var password by remember { mutableStateOf("") }

    Text(
        text = stringResource(Res.string.import_side_step2_password_prompt),
        style = JamiTheme.typography.bodyMedium,
        color = JamiTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(JamiTheme.spacing.m))

    // Show resolved identity (Jami ID or registered name)
    val identity = state.registeredName?.takeIf { it.isNotBlank() } ?: state.peerId
    Text(
        text = identity,
        style = JamiTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = JamiTheme.colors.onSurface,
        textAlign = TextAlign.Center,
    )

    if (state.inputError != null) {
        Spacer(Modifier.height(JamiTheme.spacing.s))
        Text(
            text = if (state.inputError == AddDeviceImportState.InputError.BAD_PASSWORD)
                stringResource(Res.string.link_device_error_bad_password)
            else
                stringResource(Res.string.link_device_error_unknown),
            color = JamiTheme.colors.error,
            style = JamiTheme.typography.bodySmall,
        )
    }

    if (state.needPassword) {
        Spacer(Modifier.height(JamiTheme.spacing.m))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(Res.string.account_enter_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onConfirm(password) }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(JamiTheme.spacing.l))

    Button(
        onClick = { onConfirm(password) },
        enabled = !state.needPassword || password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(Res.string.import_side_step2_password_import))
    }
    Spacer(Modifier.height(JamiTheme.spacing.s))
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.export_side_step2_cancel))
    }
}

@Composable
private fun DoneErrorStep(error: AuthError, onClose: () -> Unit) {
    val msg = when (error) {
        AuthError.NETWORK        -> stringResource(Res.string.link_device_error_network)
        AuthError.AUTHENTICATION -> stringResource(Res.string.link_device_error_authentication)
        AuthError.TIMEOUT        -> stringResource(Res.string.link_device_error_timeout)
        AuthError.CANCELED       -> stringResource(Res.string.link_device_error_canceled)
        AuthError.UNKNOWN        -> stringResource(Res.string.link_device_error_unknown)
    }
    Text(
        text = msg,
        color = JamiTheme.colors.error,
        style = JamiTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(JamiTheme.spacing.l))
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.import_side_step3_exit))
    }
}
