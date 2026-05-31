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
package net.jami.services

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.jami.utils.Log
import java.lang.ref.WeakReference

/**
 * Android implementation of PictureInPictureManager.
 *
 * Uses PictureInPictureParams with Activity for PiP mode on Android 8.0+.
 * Remote actions are delivered via broadcast intents.
 */
class AndroidPictureInPictureManager : PictureInPictureManager {

    private val tag = "AndroidPipManager"

    private var activityRef: WeakReference<Activity>? = null
    private var autoEnterEnabled = true
    private var sourceRect: Rect? = null
    private var currentActions: List<PipAction> = listOf(
        PipAction.TOGGLE_MUTE,
        PipAction.HANG_UP
    )

    private val _pipState = MutableStateFlow(
        PipState(
            isInPipMode = false,
            isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        )
    )
    override val pipState: StateFlow<PipState> = _pipState.asStateFlow()

    private val _pipActions = MutableSharedFlow<PipAction>(extraBufferCapacity = 10)
    override val pipActions: Flow<PipAction> = _pipActions.asSharedFlow()

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = when (intent.getIntExtra(EXTRA_ACTION_TYPE, -1)) {
                ACTION_TOGGLE_MUTE -> PipAction.TOGGLE_MUTE
                ACTION_TOGGLE_VIDEO -> PipAction.TOGGLE_VIDEO
                ACTION_HANG_UP -> PipAction.HANG_UP
                ACTION_SWITCH_CAMERA -> PipAction.SWITCH_CAMERA
                else -> return
            }
            _pipActions.tryEmit(action)
            Log.d(tag, "PiP action received: $action")
        }
    }

    private var receiverRegistered = false

    /**
     * Attach an Activity for PiP operations.
     * Should be called from Activity.onCreate or when the call starts.
     */
    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
        registerReceiver(activity)

        _pipState.value = _pipState.value.copy(
            isSupported = isSupported() && activity.packageManager.hasSystemFeature(
                PackageManager.FEATURE_PICTURE_IN_PICTURE
            )
        )
    }

    /**
     * Detach the Activity when call ends or Activity is destroyed.
     */
    fun detachActivity() {
        activityRef?.get()?.let { activity ->
            unregisterReceiver(activity)
        }
        activityRef = null
        _pipState.value = _pipState.value.copy(isInPipMode = false)
    }

    private fun registerReceiver(activity: Activity) {
        if (receiverRegistered) return
        try {
            val filter = IntentFilter(ACTION_PIP_CONTROL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                activity.registerReceiver(actionReceiver, filter)
            }
            receiverRegistered = true
        } catch (e: Exception) {
            Log.e(tag, "Failed to register PiP receiver: ${e.message}")
        }
    }

    private fun unregisterReceiver(activity: Activity) {
        if (!receiverRegistered) return
        try {
            activity.unregisterReceiver(actionReceiver)
            receiverRegistered = false
        } catch (e: Exception) {
            Log.e(tag, "Failed to unregister PiP receiver: ${e.message}")
        }
    }

    override fun isSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun enterPipMode(aspectRatioWidth: Int, aspectRatioHeight: Int): Boolean {
        if (!isSupported()) return false

        val activity = activityRef?.get() ?: run {
            Log.w(tag, "No activity attached for PiP")
            return false
        }

        return try {
            val params = buildPipParams(aspectRatioWidth, aspectRatioHeight, activity)
            val result = activity.enterPictureInPictureMode(params)
            if (result) {
                _pipState.value = _pipState.value.copy(
                    isInPipMode = true,
                    aspectRatioWidth = aspectRatioWidth,
                    aspectRatioHeight = aspectRatioHeight
                )
                Log.d(tag, "Entered PiP mode (${aspectRatioWidth}:${aspectRatioHeight})")
            }
            result
        } catch (e: Exception) {
            Log.e(tag, "Failed to enter PiP: ${e.message}")
            false
        }
    }

    override fun exitPipMode() {
        _pipState.value = _pipState.value.copy(isInPipMode = false)
        Log.d(tag, "Exited PiP mode")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun updatePipParams(aspectRatioWidth: Int, aspectRatioHeight: Int) {
        if (!isSupported()) return

        val activity = activityRef?.get() ?: return
        if (!isInPipMode()) return

        try {
            val params = buildPipParams(aspectRatioWidth, aspectRatioHeight, activity)
            activity.setPictureInPictureParams(params)
            _pipState.value = _pipState.value.copy(
                aspectRatioWidth = aspectRatioWidth,
                aspectRatioHeight = aspectRatioHeight
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to update PiP params: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun setAutoEnterEnabled(enabled: Boolean) {
        autoEnterEnabled = enabled

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val activity = activityRef?.get() ?: return
            try {
                val state = _pipState.value
                val params = buildPipParams(state.aspectRatioWidth, state.aspectRatioHeight, activity)
                activity.setPictureInPictureParams(params)
            } catch (e: Exception) {
                Log.e(tag, "Failed to set auto-enter: ${e.message}")
            }
        }
    }

    override fun isInPipMode(): Boolean = _pipState.value.isInPipMode

    override fun setSourceRectHint(left: Int, top: Int, right: Int, bottom: Int) {
        sourceRect = Rect(left, top, right, bottom)
    }

    private var currentCallState: net.jami.ui.viewmodel.CallState? = null

    override fun attachCallState(callState: net.jami.ui.viewmodel.CallState) {
        currentCallState = callState
        Log.d(tag, "Call state attached: ${callState.callMode}")
    }

    override fun detachCallState() {
        currentCallState = null
        Log.d(tag, "Call state detached")
    }

    override fun configurePipActions(actions: List<PipAction>) {
        currentActions = actions.take(3) // Max 3 actions on Android

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPipMode()) {
            val activity = activityRef?.get() ?: return
            val state = _pipState.value
            try {
                val params = buildPipParams(state.aspectRatioWidth, state.aspectRatioHeight, activity)
                activity.setPictureInPictureParams(params)
            } catch (e: Exception) {
                Log.e(tag, "Failed to update PiP actions: ${e.message}")
            }
        }
    }

    /**
     * Called by Activity.onPictureInPictureModeChanged.
     */
    fun onPipModeChanged(isInPipMode: Boolean) {
        _pipState.value = _pipState.value.copy(isInPipMode = isInPipMode)
        Log.d(tag, "PiP mode changed: $isInPipMode")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(
        aspectRatioWidth: Int,
        aspectRatioHeight: Int,
        activity: Activity
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(aspectRatioWidth, aspectRatioHeight))
            .setActions(buildRemoteActions(activity))

        sourceRect?.let { rect ->
            builder.setSourceRectHint(rect)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnterEnabled)
            builder.setSeamlessResizeEnabled(true)
        }

        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildRemoteActions(activity: Activity): List<RemoteAction> {
        return currentActions.mapNotNull { action ->
            val (iconRes, title, actionType) = when (action) {
                PipAction.TOGGLE_MUTE -> Triple(
                    android.R.drawable.ic_lock_silent_mode_off,
                    "Mute",
                    ACTION_TOGGLE_MUTE
                )
                PipAction.TOGGLE_VIDEO -> Triple(
                    android.R.drawable.ic_menu_camera,
                    "Video",
                    ACTION_TOGGLE_VIDEO
                )
                PipAction.HANG_UP -> Triple(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Hang up",
                    ACTION_HANG_UP
                )
                PipAction.SWITCH_CAMERA -> Triple(
                    android.R.drawable.ic_menu_rotate,
                    "Switch",
                    ACTION_SWITCH_CAMERA
                )
            }

            try {
                val intent = Intent(ACTION_PIP_CONTROL).apply {
                    putExtra(EXTRA_ACTION_TYPE, actionType)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    activity,
                    actionType,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                RemoteAction(
                    Icon.createWithResource(activity, iconRes),
                    title,
                    title,
                    pendingIntent
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to create remote action: ${e.message}")
                null
            }
        }
    }

    companion object {
        private const val ACTION_PIP_CONTROL = "net.jami.action.PIP_CONTROL"
        private const val EXTRA_ACTION_TYPE = "action_type"

        private const val ACTION_TOGGLE_MUTE = 1
        private const val ACTION_TOGGLE_VIDEO = 2
        private const val ACTION_HANG_UP = 3
        private const val ACTION_SWITCH_CAMERA = 4
    }
}

/**
 * Create Android PiP manager.
 */
actual fun createPictureInPictureManager(): PictureInPictureManager {
    return AndroidPictureInPictureManager()
}
