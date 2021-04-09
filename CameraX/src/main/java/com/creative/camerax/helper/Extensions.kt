package com.creative.camerax.helper

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import java.nio.ByteBuffer

fun ByteBuffer.toByteArray(): ByteArray {
    rewind()    // Rewind the buffer to zero
    val data = ByteArray(remaining())
    get(data)   // Copy the buffer into a byte array
    return data // Return the byte array
}

fun Bitmap.rotateOn(rotationAngle: Float, scale: Boolean = true): Bitmap {
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
    return if (scale) rotatedBitmap.getResizedBitmap() else rotatedBitmap
}

/**
 * reduces the size of the image
 * @return
 */
private fun Bitmap.getResizedBitmap(): Bitmap {
    val width = this.width
    val height = this.height

    val newWidth = width / 4
    val newHeight = height / 4

    Log.e("Size scaled to", "W:$width => $newWidth, H:$height => $newHeight")

    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}