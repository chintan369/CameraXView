package com.creative.cameratestapp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import io.fotoapparat.Fotoapparat
import io.fotoapparat.configuration.CameraConfiguration
import io.fotoapparat.log.fileLogger
import io.fotoapparat.log.logcat
import io.fotoapparat.log.loggers
import io.fotoapparat.parameter.ScaleType
import io.fotoapparat.selector.back
import kotlinx.android.synthetic.main.activity_foto_apparat.*

class FotoApparatActivity : AppCompatActivity() {

    lateinit var fotoapparat: Fotoapparat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_foto_apparat)

        initCameraView()
    }

    private fun initCameraView() {
        fotoapparat = Fotoapparat(
            context = this,
            view = fotoCameraView,                   // view which will draw the camera preview
            logger = loggers(                    // (optional) we want to log camera events in 2 places at once
                logcat(),                   // ... in logcat
                fileLogger(this)            // ... and to file
            ),
            cameraErrorCallback = { error ->
                Log.e("FotoApparat Error:", error.message ?: "", error)

            }   // (optional) log fatal errors
        )
    }

    override fun onStart() {
        super.onStart()
        fotoapparat.start()
    }

    override fun onStop() {
        fotoapparat.stop()
        super.onStop()
    }
}