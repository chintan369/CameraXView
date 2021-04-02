package com.creative.camerax.interfaces

import android.net.Uri

interface OnMediaEventListener {
    fun onPhotoTaken(file: Uri)
    fun onVideoTaken(file: Uri)
    fun onVideoStarted()
    fun onVideoStopped()
    fun onFrameDataReceived(data: ByteArray)
    fun onError(exception: Throwable)
    fun onCameraStarted()
}