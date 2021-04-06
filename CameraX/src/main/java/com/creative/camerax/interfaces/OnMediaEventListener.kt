package com.creative.camerax.interfaces

import android.graphics.Bitmap
import android.net.Uri

interface OnMediaEventListener {
    fun onPhotoTaken(file: Uri) = Unit
    fun onPhotoSnapTaken(file: Uri) = Unit
    fun onPhotoSnapTaken(bitmap: Bitmap) = Unit
    fun onVideoTaken(file: Uri) = Unit
    fun onVideoStarted() = Unit
    fun onVideoStopped() = Unit
    fun onError(exception: Throwable) = Unit
    fun onCameraStarted() = Unit
}