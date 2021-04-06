package com.creative.camerax.helper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.Image
import java.nio.ByteBuffer

fun ByteBuffer.toByteArray(): ByteArray {
    rewind()    // Rewind the buffer to zero
    val data = ByteArray(remaining())
    get(data)   // Copy the buffer into a byte array
    return data // Return the byte array
}

fun Bitmap.rotateOn(rotationAngle: Float): Bitmap {
    // Rotate the bitmap
    var rotatedBitmap = this
    if (rotationAngle != 0f) {
        val matrix = Matrix()
        matrix.postRotate(rotationAngle)
        rotatedBitmap = Bitmap.createBitmap(
            this,
            0,
            0,
            this.width,
            this.height,
            matrix,
            true
        )
    }
    return rotatedBitmap
}