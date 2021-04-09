package com.creative.camerax.analysis

import android.graphics.*
import android.media.Image
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.creative.camerax.helper.rotateOn
import java.io.ByteArrayOutputStream

typealias OnImageBitmapProcessListener = (processedBitmap: Bitmap) -> Unit

class BitmapProcessor(val bitmapProcessListener: OnImageBitmapProcessListener) :
    ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        val imageItem = image.image ?: return
        var bitmap = imageItem.toBitmap()
        image.close()
        bitmap = bitmap.rotateOn(image.imageInfo.rotationDegrees.toFloat(), false)
        bitmapProcessListener(bitmap)
    }

    private fun Image.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer // Y
        val vuBuffer = planes[2].buffer // VU

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }


}