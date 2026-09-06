package net.jami.ui.platform

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import net.jami.utils.PendingActivityResultTracker

@Composable
actual fun RingtoneLauncherEffect(
    show: Boolean,
    currentUri: String,
    onResult: (uri: String?) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Clear first, unconditionally — see PendingActivityResultTracker's kdoc.
        PendingActivityResultTracker.end()
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            onResult(uri?.toString())
        } else {
            onResult(null)
        }
    }

    LaunchedEffect(show) {
        if (show) {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                if (currentUri.isNotEmpty()) {
                    putExtra(
                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        android.net.Uri.parse(currentUri)
                    )
                }
            }
            // Marked *before* launch so JamiNavigation's biometric-lock swap can't dispose this
            // composable — and this launcher with it — before the picker result arrives.
            PendingActivityResultTracker.begin()
            launcher.launch(intent)
        }
    }
}
