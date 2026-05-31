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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Web/JS stub implementation of PictureInPictureManager.
 *
 * Web browsers support Picture-in-Picture via the Picture-in-Picture API,
 * but implementation is deferred.
 */
class WebPictureInPictureManager : PictureInPictureManager {

    private val _pipState = MutableStateFlow(PipState(isSupported = false))
    override val pipState: StateFlow<PipState> = _pipState.asStateFlow()

    private val _pipActions = MutableSharedFlow<PipAction>()
    override val pipActions: Flow<PipAction> = _pipActions.asSharedFlow()

    override fun isSupported(): Boolean = false

    override fun enterPipMode(aspectRatioWidth: Int, aspectRatioHeight: Int): Boolean = false

    override fun exitPipMode() { }

    override fun updatePipParams(aspectRatioWidth: Int, aspectRatioHeight: Int) { }

    override fun setAutoEnterEnabled(enabled: Boolean) { }

    override fun isInPipMode(): Boolean = false

    override fun setSourceRectHint(left: Int, top: Int, right: Int, bottom: Int) { }

    override fun configurePipActions(actions: List<PipAction>) { }

    override fun attachCallState(callState: net.jami.ui.viewmodel.CallState) { }

    override fun detachCallState() { }
}

actual fun createPictureInPictureManager(): PictureInPictureManager {
    return WebPictureInPictureManager()
}
