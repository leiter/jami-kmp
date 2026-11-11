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

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVSampleBufferDisplayLayer
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth

/**
 * A child view that hosts one remote video [AVSampleBufferDisplayLayer] and keeps it sized to
 * the view. Used when the surface the UI hands to `HardwareService.addVideoSurface` is a plain
 * `UIView` (`VideoRenderer`), which has no layer of its own and would never re-lay one out.
 *
 * Layout goes through [layout] (the bridge's `layoutDisplayLayer`), not `layer.frame`: the bridge
 * turns the layer by the rotation the sender's frames carry, and a frame is undefined on a
 * rotated layer.
 */
@OptIn(ExperimentalForeignApi::class)
internal class DisplayLayerView private constructor(
    val displayLayer: AVSampleBufferDisplayLayer,
    private val layout: (AVSampleBufferDisplayLayer) -> Unit,
) : UIView(platform.CoreGraphics.CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    init {
        backgroundColor = UIColor.blackColor
        userInteractionEnabled = false
        autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        layer.addSublayer(displayLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        layout(displayLayer)
    }

    companion object {
        /** Adds a display layer filling [host] and returns it. Main thread only. */
        fun install(host: UIView, layout: (AVSampleBufferDisplayLayer) -> Unit): AVSampleBufferDisplayLayer {
            val layer = AVSampleBufferDisplayLayer().apply {
                videoGravity = AVLayerVideoGravityResizeAspect
                backgroundColor = UIColor.blackColor.CGColor
            }
            val view = DisplayLayerView(layer, layout)
            view.setFrame(host.bounds)
            host.addSubview(view)
            return layer
        }

        /** Removes the view [install] created for [layer], if there is one. */
        fun uninstall(layer: AVSampleBufferDisplayLayer) {
            val view = layer.superlayer?.delegate as? DisplayLayerView ?: return
            if (view.displayLayer === layer) view.removeFromSuperview()
        }
    }
}
