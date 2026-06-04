package net.jami.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSError
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun QrCodeScannerView(
    modifier: Modifier,
    onQrDetected: (String) -> Unit,
) {
    var hasPermission by remember {
        mutableStateOf(
            AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized
        )
    }

    if (!hasPermission) {
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            hasPermission = granted
        }
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission required to scan QR codes")
        }
        return
    }

    val session = remember { AVCaptureSession() }
    val detectedRef = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { session.stopRunning() }
    }

    UIKitView(
        modifier = modifier,
        factory = {
            val view = UIView()

            val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return@UIKitView view
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                val input = AVCaptureDeviceInput(device, err.ptr) ?: return@memScoped
                if (session.canAddInput(input)) session.addInput(input)
            }

            val metadataOutput = AVCaptureMetadataOutput()
            if (session.canAddOutput(metadataOutput)) {
                session.addOutput(metadataOutput)
                metadataOutput.setMetadataObjectsDelegate(
                    object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
                        override fun captureOutput(
                            output: platform.AVFoundation.AVCaptureOutput,
                            didOutputMetadataObjects: List<*>,
                            fromConnection: platform.AVFoundation.AVCaptureConnection,
                        ) {
                            if (detectedRef.value) return
                            val obj = didOutputMetadataObjects.firstOrNull() as? platform.AVFoundation.AVMetadataMachineReadableCodeObject ?: return
                            val text = obj.stringValue ?: return
                            detectedRef.value = true
                            onQrDetected(text)
                        }
                    },
                    queue = dispatch_get_main_queue(),
                )
                metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            }

            val previewLayer = AVCaptureVideoPreviewLayer(session = session)
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            view.layer.addSublayer(previewLayer)
            previewLayer.frame = view.bounds
            CATransaction.commit()

            session.startRunning()
            view
        },
        update = { view ->
            val previewLayer = view.layer.sublayers?.firstOrNull() as? AVCaptureVideoPreviewLayer
            previewLayer?.frame = view.bounds
        },
    )
}
