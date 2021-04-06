package com.creative.camerax.analysis

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.creative.camerax.helper.rotateOn
import com.creative.camerax.helper.toBitmap

typealias OnImageBitmapProcessListener = (processedBitmap: Bitmap) -> Unit

class BitmapProcessor(val bitmapProcessListener: OnImageBitmapProcessListener) :
    ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        val imageItem = image.image ?: return
        var bitmap = imageItem.toBitmap()
        image.close()
        bitmap = bitmap.rotateOn(image.imageInfo.rotationDegrees.toFloat())
        bitmapProcessListener(bitmap)
    }
}