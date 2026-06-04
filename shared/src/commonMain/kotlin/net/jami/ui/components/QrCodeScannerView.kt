package net.jami.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun QrCodeScannerView(
    modifier: Modifier = Modifier,
    onQrDetected: (String) -> Unit,
)
