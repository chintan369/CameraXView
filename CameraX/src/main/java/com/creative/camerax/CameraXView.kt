package com.creative.camerax

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.FrameLayout
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.creative.camerax.analysis.FrameProcessor
import com.creative.camerax.helper.CameraLens
import com.creative.camerax.helper.CaptureMode
import com.creative.camerax.helper.FlashMode
import com.creative.camerax.interfaces.OnCameraControlListener
import com.creative.camerax.interfaces.OnMediaEventListener
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias OnImageFrameProcessListener = (byteArray: ByteArray) -> Unit

class CameraXView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val cameraPreviewView = PreviewView(context)

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private var lifeCycleOwner: LifecycleOwner? = null
    private var onMediaEventListener: OnMediaEventListener? = null
    private var onCameraControlListener: OnCameraControlListener? = null

    private var currentLensFacing = CameraSelector.DEFAULT_BACK_CAMERA
    private var currentFlashMode = FlashMode.OFF
    private var currentCaptureMode = CaptureMode.PICTURE

    private var isRecording = false
    private var camera: Camera? = null
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    private val displayManager by lazy { context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }

    private var videoStopTimer: CountDownTimer? = null


    init {
        addCameraPreviewView()
        initializeObjects()
        startCamera()
    }

    /**
     * toggle the camera facing
     * @param
     * @return
     */
    fun toggleFacing() {
        toggleCameraLens()
        startCamera()
    }

    fun setFlash(mode: FlashMode) {
        currentFlashMode = mode
        this.onCameraControlListener?.onFlashModeChanged(mode)
        startCamera()
    }

    fun getIsTakingVideo(): Boolean = currentCaptureMode == CaptureMode.VIDEO && isRecording

    fun getFlash(): FlashMode = currentFlashMode

    fun setCaptureMode(mode: CaptureMode) {
        currentCaptureMode = mode
        this.onCameraControlListener?.onCaptureModeChanged(mode)
        startCamera()
    }

    fun getCaptureMode(): CaptureMode = currentCaptureMode

    private val orientationEventListener by lazy {
        object : OrientationEventListener(context) {
            @SuppressLint("UnsafeExperimentalUsageError")
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) {
                    return
                }

                val rotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                if (!isRecording) {
                    videoCapture?.setTargetRotation(rotation)
                }
                imageCapture?.targetRotation = rotation
                imageAnalyzer?.targetRotation = rotation
            }
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(p0: Int) = Unit

        override fun onDisplayRemoved(p0: Int) = Unit

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun onDisplayChanged(p0: Int) {
            if (!isRecording) {
                videoCapture?.setTargetRotation(this@CameraXView.display.rotation)
            }
            imageCapture?.targetRotation = this@CameraXView.display.rotation
            imageAnalyzer?.targetRotation = this@CameraXView.display.rotation
        }

    }

    private fun clickPhoto(file: File? = null) {
        if (currentCaptureMode == CaptureMode.VIDEO) return
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        val photoFile = file ?: File(
            outputDirectory,
            SimpleDateFormat(
                FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg"
        )


        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(photoFile)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    this@CameraXView.onMediaEventListener?.onError(exc)
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    this@CameraXView.onMediaEventListener?.onPhotoTaken(savedUri)
                    val msg = "Photo capture succeeded: $savedUri"
                    Log.d(TAG, msg)
                }
            })
    }

    fun takePhoto() {
        clickPhoto()
    }

    fun takePhoto(file: File) {
        clickPhoto(file)
    }

    private fun startVideoRecording(file: File? = null, duration: Long = 0) {
        if (currentCaptureMode == CaptureMode.PICTURE) return
        val videoCapture = videoCapture ?: return

        if (isRecording) return

        val outFile = file ?: File(
            outputDirectory,
            SimpleDateFormat(
                FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".mp4"
        )

        val outputOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, outFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, outFile.absolutePath)
            }

            context.contentResolver.run {
                val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

                VideoCapture.OutputFileOptions.Builder(this, contentUri, contentValues)
            }
        } else {
            VideoCapture.OutputFileOptions.Builder(outFile)
        }.build()

        if (duration >= MIN_REQUIRED_VIDEO_DURATION) {
            setTimer(duration)
        }

        videoCapture.startRecording(outputOptions,
            ContextCompat.getMainExecutor(context), object : VideoCapture.OnVideoSavedCallback {
                override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                    val uri = outputFileResults.savedUri ?: Uri.fromFile(outFile)
                    onMediaEventListener?.onVideoTaken(uri)
                    Log.d(TAG, "Saved Video at ${uri.toString()}")
                }

                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    Log.e(TAG, message, cause)
                    if (cause != null) {
                        onMediaEventListener?.onError(cause)
                    }

                }

            })

        onMediaEventListener?.onVideoStarted()

        isRecording = true

    }


    private fun setTimer(duration: Long) {
        videoStopTimer = object : CountDownTimer(
            duration + MIN_REQUIRED_VIDEO_DURATION,
            MIN_REQUIRED_VIDEO_DURATION
        ) {
            override fun onTick(p0: Long) = Unit

            override fun onFinish() {
                if (isRecording) {
                    stopVideo()
                }
            }
        }
        videoStopTimer?.start()
    }

    fun startVideo() {
        startVideoRecording()
    }

    fun startVideo(file: File) {
        startVideoRecording(file)
    }

    fun startVideo(duration: Long) {
        startVideoRecording(duration = duration)
    }

    fun startVideo(file: File, duration: Long) {
        startVideoRecording(file, duration)
    }

    fun stopVideo() {
        if (currentCaptureMode == CaptureMode.PICTURE) return
        val videoCapture = videoCapture ?: return
        if (!isRecording) return
        videoCapture.stopRecording()
        videoStopTimer?.cancel()
        isRecording = false
        onMediaEventListener?.onVideoStopped()
    }

    private fun toggleCameraLens() {
        currentLensFacing = when (currentLensFacing) {
            CameraSelector.DEFAULT_BACK_CAMERA -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        sendLensFacingEvent()
    }

    fun setCameraFace(lens: CameraLens) {
        currentLensFacing = when (lens) {
            CameraLens.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        sendLensFacingEvent()
        startCamera()
    }

    fun getCameraFace(): CameraLens {
        return when (currentLensFacing) {
            CameraSelector.DEFAULT_FRONT_CAMERA -> CameraLens.FRONT
            else -> CameraLens.BACK
        }
    }

    private fun sendLensFacingEvent() {
        when (currentLensFacing) {
            CameraSelector.DEFAULT_BACK_CAMERA -> this.onCameraControlListener?.onLensFacingChanged(
                CameraLens.BACK
            )
            CameraSelector.DEFAULT_FRONT_CAMERA -> this.onCameraControlListener?.onLensFacingChanged(
                CameraLens.FRONT
            )
        }
    }

    private fun initializeObjects() {
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    /**
     * Adds Preview view to the main layout
     * @param -
     * @return
     */
    private fun addCameraPreviewView() {
        cameraPreviewView.layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(cameraPreviewView)
    }

    /**
     * Bind lifecycle owner to cameraXView
     * @param lifeCycleOwner [LifecycleOwner]
     * @return
     */
    fun bindLifeCycle(lifeCycleOwner: LifecycleOwner) {
        this.lifeCycleOwner?.lifecycle?.removeObserver(lifeCycleEventObserver)
        this.lifeCycleOwner = lifeCycleOwner
        this.lifeCycleOwner?.let {
            it.lifecycle.addObserver(lifeCycleEventObserver)
        }
        startCamera()
    }

    /**
     * Adds media event listener
     * @param onMediaEventListener [OnMediaEventListener]
     * @return
     */
    fun setMediaEventListener(onMediaEventListener: OnMediaEventListener) {
        this.onMediaEventListener = onMediaEventListener
    }

    fun setCameraControlListener(onCameraControlListener: OnCameraControlListener) {
        this.onCameraControlListener = onCameraControlListener
    }

    /**
     * Start camera and bind to preview
     * @param
     * @return
     */
    private fun startCamera() {

        this.lifeCycleOwner?.let { lifecycleOwner ->
            cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(Runnable {
                // Used to bind the lifecycle of cameras to the lifecycle owner
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Preview
                preview = Preview.Builder()
                    //.setTargetRotation(cameraPreviewView.display.rotation)
                    .build()
                    .also {
                        it.setSurfaceProvider(cameraPreviewView.surfaceProvider)
                    }

                if (currentCaptureMode == CaptureMode.VIDEO) {
                    val videoCaptureConfig = VideoCapture.DEFAULT_CONFIG.config

                    videoCapture = VideoCapture.Builder.fromConfig(videoCaptureConfig).build()

                    videoCapture?.setTargetRotation(cameraPreviewView.display.rotation)

                } else {
                    val imageCaptureBuilder = ImageCapture.Builder()

                    when (currentFlashMode) {
                        FlashMode.ON -> imageCaptureBuilder.setFlashMode(ImageCapture.FLASH_MODE_ON)
                        FlashMode.AUTO -> imageCaptureBuilder.setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                        FlashMode.TORCH -> imageCaptureBuilder.setFlashMode(ImageCapture.FLASH_MODE_ON)
                        else -> imageCaptureBuilder.setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    }

                    imageCapture = imageCaptureBuilder.build()

                    imageCapture?.targetRotation = cameraPreviewView.display.rotation
                }

                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, FrameProcessor { byteData ->
                            this.onMediaEventListener?.onFrameDataReceived(byteData)
                        })
                    }

                val hasRequiredCamera = hasRequiredCamera(cameraProvider)
                if (!hasRequiredCamera) { //If the selected camera by user is not available
                    toggleCameraLens() //Change the camera lens facing
                    if (!hasRequiredCamera(cameraProvider)) return@Runnable // even if the camera is not available after changing the face of lens, then return as no camera in the device is available or camera is having some issue to not found
                }

                try {
                    // Unbind use cases before rebinding
                    cameraProvider.unbindAll()

                    // Bind use cases to camera
                    camera = if (currentCaptureMode == CaptureMode.VIDEO) {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, currentLensFacing, preview, videoCapture
                        )
                    } else {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, currentLensFacing, preview, imageCapture, imageAnalyzer
                        )
                    }

                    if (camera?.cameraInfo?.hasFlashUnit() == true && currentFlashMode == FlashMode.TORCH) {
                        camera?.cameraControl?.enableTorch(true)
                    } else {
                        camera?.cameraControl?.enableTorch(false)
                    }

                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }

            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun hasRequiredCamera(cameraProvider: ProcessCameraProvider): Boolean {
        return cameraProvider.hasCamera(currentLensFacing)
    }

    /**
     * creates and return output file directory
     * @param
     * @return
     */
    private fun getOutputDirectory(): File {
        val mediaDir = context.externalCacheDirs.firstOrNull()?.let {
            File(it, "CameraX").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else context.cacheDir
    }

    private val lifeCycleEventObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                displayManager.registerDisplayListener(displayListener, null)
                orientationEventListener.enable()
            }
            Lifecycle.Event.ON_STOP -> {
                displayManager.unregisterDisplayListener(displayListener)
                orientationEventListener.disable()
            }
            Lifecycle.Event.ON_DESTROY -> {
                cameraExecutor.shutdown()
            }
            else -> Unit
        }
    }

    companion object {
        private const val TAG = "CameraXView"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val MIN_REQUIRED_VIDEO_DURATION = 1000L
    }

}