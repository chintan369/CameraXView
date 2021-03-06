package com.creative.cameratestapp

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.creative.camerax.helper.CameraLens
import com.creative.camerax.helper.CaptureMode
import com.creative.camerax.interfaces.OnMediaEventListener
import kotlinx.android.synthetic.main.activity_camerax.*

class CameraXActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camerax)
        cameraXView.bindLifeCycle(this)

        cameraXView.setMediaEventListener(object : OnMediaEventListener {
            override fun onPhotoTaken(file: Uri) {
                Log.e("Photo Saved", file.toString())
            }

            override fun onVideoTaken(file: Uri) {
                Log.e("Video Saved", file.toString())
            }

            override fun onError(exception: Throwable) {
                Log.e("Camera Page", exception.message, exception)
            }

            override fun onPhotoSnapTaken(bitmap: Bitmap) {
                super.onPhotoSnapTaken(bitmap)
            }

            override fun onPhotoSnapTaken(file: Uri) {
                Log.e("Photo Saved", file.toString())
            }
        })
        cameraXView.setCaptureMode(CaptureMode.PICTURE)
        cameraXView.setCameraFace(CameraLens.FRONT)
        btnSnapBitmap.setOnClickListener { cameraXView.takePhotoSnap(true) }
        btnPhoto.setOnClickListener { cameraXView.takePhoto() }
        btnSnapPhoto.setOnClickListener { cameraXView.takePhotoSnap() }
    }

}