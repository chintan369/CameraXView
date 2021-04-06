package com.creative.camerax.analysis

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.creative.camerax.helper.toByteArray
import java.nio.ByteBuffer

typealias OnImageFrameProcessListener = (byteArray: ByteArray) -> Unit

class FrameProcessor(val frameProcessListener: OnImageFrameProcessListener) :
    ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        image.close()
        frameProcessListener(data)
    }
}