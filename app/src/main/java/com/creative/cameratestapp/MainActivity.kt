package com.creative.cameratestapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ckdroid.dynamicpermissions.PermissionStatus
import com.ckdroid.dynamicpermissions.PermissionUtils
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val REQUEST_PERMISSION_CODE = 1
    private val cameraPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO
    )
    private var selectedCameraButton = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setCameraButtonTapListener()

    }

    private fun setCameraButtonTapListener() {
        camera1.setOnClickListener {
            selectedCameraButton = 1
            checkForPermissionIfGiven()
        }
        camera1o1.setOnClickListener {
            selectedCameraButton = 2
            checkForPermissionIfGiven()
        }
        camera2.setOnClickListener {
            selectedCameraButton = 3
            checkForPermissionIfGiven()
        }
        camera3.setOnClickListener {
            selectedCameraButton = 4
            checkForPermissionIfGiven()
        }
    }

    private fun checkForPermissionIfGiven(isForFirstTimeAsking: Boolean = true) {
        val permissionResult = PermissionUtils.checkAndRequestPermissions(
            this,
            cameraPermissions.toMutableList(),
            REQUEST_PERMISSION_CODE,
            !isForFirstTimeAsking
        )

        when (permissionResult.finalStatus) {
            PermissionStatus.ALLOWED -> moveToCameraScreen()
            PermissionStatus.DENIED_PERMANENTLY -> {
                if (isForFirstTimeAsking) {
                    PermissionUtils.askUserToRequestPermissionExplicitly(this)
                }
            }
            else -> {
                //Do nothing
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_CODE) {
            checkForPermissionIfGiven(false)
        }
    }

    private fun moveToCameraScreen() {
        when (selectedCameraButton) {
            1 -> {
                val intent = Intent(this, NatarioActivity::class.java)
                startActivity(intent)
            }
            2 -> {
                val intent = Intent(this, NatarioActivity2::class.java)
                startActivity(intent)
            }
            3 -> {
                val intent = Intent(this, FotoApparatActivity::class.java)
                startActivity(intent)
            }
            4-> {
                val intent = Intent(this, CameraXActivity::class.java)
                startActivity(intent)
            }
        }
    }
}