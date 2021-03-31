package com.creative.camerax.interfaces

import com.creative.camerax.helper.CameraLens
import com.creative.camerax.helper.CaptureMode
import com.creative.camerax.helper.FlashMode

interface OnCameraControlListener {
    fun onFlashModeChanged(mode: FlashMode)
    fun onLensFacingChanged(lens: CameraLens)
    fun onCaptureModeChanged(mode: CaptureMode)
}