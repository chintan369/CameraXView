package com.creative.camerax

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import androidx.annotation.NonNull
import androidx.camera.core.*
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.OnVideoSavedCallback
import androidx.camera.view.video.OutputFileOptions
import androidx.camera.view.video.OutputFileResults
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.creative.camerax.analysis.BitmapProcessor
import com.creative.camerax.helper.*
import com.creative.camerax.interfaces.OnBitmapProcessing
import com.creative.camerax.interfaces.OnCameraControlListener
import com.creative.camerax.interfaces.OnMediaEventListener
import kotlinx.android.synthetic.main.layout_capture_mode.view.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraXView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val cameraPreviewView = PreviewView(context)
    private val layoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private lateinit var layoutSwitchingMode: View

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private var lifeCycleOwner: LifecycleOwner? = null
    private var onMediaEventListener: OnMediaEventListener? = null
    private var onCameraControlListener: OnCameraControlListener? = null

    private val backFacedCamera =
        CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
    private val frontFacedCamera =
        CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

    private var currentLensFacing = backFacedCamera
    private var currentFlashMode = FlashMode.OFF
    private var currentCaptureMode = CaptureMode.PICTURE

    var isBitmapProcessorEnabled: Boolean = false
        private set

    private var onBitmapProcessing: MutableList<OnBitmapProcessing> = mutableListOf()

    private var videoStopTimer: CountDownTimer? = null

    private var openCamera = false

    private var cameraController: LifecycleCameraController? = null

    init {
        addCameraPreviewView()
        initializeObjects()
    }

    /**
     * toggle the camera facing
     * @param
     * @return
     */
    fun toggleFacing() {
        toggleCameraLens()
        Log.d(TAG, "Lens Switched to ${currentLensFacing.lensFacing}")
    }

    fun setFlash(mode: FlashMode) {
        currentFlashMode = mode
        if (currentCaptureMode == CaptureMode.PICTURE) {
            cameraController?.enableTorch(false)
            when (currentFlashMode) {
                FlashMode.AUTO -> {
                    cameraController?.imageCaptureFlashMode = ImageCapture.FLASH_MODE_AUTO
                }
                FlashMode.ON, FlashMode.TORCH -> {
                    cameraController?.imageCaptureFlashMode = ImageCapture.FLASH_MODE_ON
                    currentFlashMode = FlashMode.ON
                }
                else -> {
                    cameraController?.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
                }
            }
        } else {
            cameraController?.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
            currentFlashMode = when (currentFlashMode) {
                FlashMode.ON, FlashMode.TORCH -> {
                    if (cameraController?.cameraInfo?.hasFlashUnit() == true) {
                        cameraController?.enableTorch(true)
                        FlashMode.TORCH
                    } else {
                        cameraController?.enableTorch(false)
                        FlashMode.OFF
                    }
                }
                else -> {
                    cameraController?.enableTorch(false)
                    FlashMode.OFF
                }
            }
        }
        this.onCameraControlListener?.onFlashModeChanged(mode)
    }

    fun isRecordingVideo(): Boolean = cameraController?.isRecording == true

    fun getFlash(): FlashMode = currentFlashMode

    fun setCaptureMode(mode: CaptureMode) {
        currentCaptureMode = mode
        showCaptureMode(true)
        isBitmapProcessorEnabled = when (currentCaptureMode) {
            CaptureMode.VIDEO -> {
                cameraController?.setEnabledUseCases(CameraController.VIDEO_CAPTURE)
                false
            }
            else -> {
                cameraController?.setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
                checkIfProcessorNeededToEnableOrDisable()
                true
            }
        }
        this.onCameraControlListener?.onCaptureModeChanged(mode)
        Log.d(TAG, "Mode Changed to ${mode.name}")
        Handler(Looper.getMainLooper()).postDelayed({
            showCaptureMode(false)
        }, 500L)

    }

    private fun showCaptureMode(show: Boolean = true) {
        if (show) {
            if (currentCaptureMode == CaptureMode.VIDEO) {
                layoutSwitchingMode.imgSwitchMode.setImageResource(R.drawable.ic_mode_video)
            } else {
                layoutSwitchingMode.imgSwitchMode.setImageResource(R.drawable.ic_mode_camera)
            }
            layoutSwitchingMode.visibility = View.VISIBLE
        } else {
            layoutSwitchingMode.visibility = View.GONE
        }
    }

    fun addBitmapProcessor(@NonNull processor: OnBitmapProcessing) {
        if (this.onBitmapProcessing.contains(processor)) return

        this.onBitmapProcessing.add(processor)

        checkIfProcessorNeededToEnableOrDisable()
    }

    private fun checkIfProcessorNeededToEnableOrDisable() {
        if (isBitmapProcessorEnabled && this.onBitmapProcessing.isEmpty()) {
            cameraController?.clearImageAnalysisAnalyzer()
            isBitmapProcessorEnabled = false
            return
        }

        if (isBitmapProcessorEnabled && this.onBitmapProcessing.isNotEmpty()) return

        if (!isBitmapProcessorEnabled && this.onBitmapProcessing.isNotEmpty() && currentCaptureMode == CaptureMode.VIDEO) return

        if (!isBitmapProcessorEnabled) {
            cameraController?.imageAnalysisBackpressureStrategy =
                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            cameraController?.let {
                it.setImageAnalysisAnalyzer(
                    cameraExecutor, BitmapProcessor { bitmap ->
                        this@CameraXView.onBitmapProcessing.forEach { processor ->
                            processor.onBitmapProcessed(bitmap)
                        }
                    })
            }
            isBitmapProcessorEnabled = true
        }
    }

    fun removeBitmapProcessor(@NonNull processor: OnBitmapProcessing) {
        if (!this.onBitmapProcessing.contains(processor)) return

        this.onBitmapProcessing.remove(processor)

        checkIfProcessorNeededToEnableOrDisable()
    }

    fun clearBitmapProcessors() {
        this.onBitmapProcessing.clear()
        checkIfProcessorNeededToEnableOrDisable()
    }

    fun getCaptureMode(): CaptureMode = currentCaptureMode

    private fun clickPhoto(
        file: File? = null,
        snapshot: Boolean = false,
        requiredBitmap: Boolean = false
    ) {
        if (currentCaptureMode == CaptureMode.VIDEO) return

        if (cameraController?.isImageCaptureEnabled != true) {
            onMediaEventListener?.onError(Exception("Set Capture mode to Picture"))
            return
        }

        val photoFile = file ?: File(
            outputDirectory,
            SimpleDateFormat(
                FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg"
        )

        // Set up image capture listener, which is triggered after photo has
        // been taken

        if (snapshot) {
            cameraController?.takePicture(ContextCompat.getMainExecutor(context), object :
                ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val imgProxy = image.image ?: return
                    var bitmap = imgProxy.toBitmap()
                    image.close()

                    bitmap = bitmap.rotateOn(image.imageInfo.rotationDegrees.toFloat())

                    if (requiredBitmap) {
                        onMediaEventListener?.onPhotoSnapTaken(bitmap)
                    } else {
                        val success = saveSnapShotToFile(bitmap, photoFile)

                        if (success) {
                            onMediaEventListener?.onPhotoSnapTaken(Uri.fromFile(photoFile))
                        }
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    super.onError(exc)
                    this@CameraXView.onMediaEventListener?.onError(exc)
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }
            })
        } else {

            // Create output options object which contains file + metadata
            val outputOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, photoFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/${photoFile.extension}")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, photoFile.absolutePath)
                }

                context.contentResolver.run {
                    val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

                    ImageCapture.OutputFileOptions.Builder(this, contentUri, contentValues)
                }
            } else {
                ImageCapture.OutputFileOptions.Builder(photoFile)
            }.build()

            cameraController?.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        this@CameraXView.onMediaEventListener?.onError(exc)
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                        this@CameraXView.onMediaEventListener?.onPhotoTaken(savedUri)
                        val msg = "Photo capture succeeded: $savedUri"
                        Log.d(TAG, msg)
                    }
                })
        }
    }

    private fun Image.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun saveSnapShotToFile(bitmap: Bitmap, file: File): Boolean {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out) // bmp is your Bitmap instance
            }
        } catch (e: IOException) {
            onMediaEventListener?.onError(e)
            e.printStackTrace()
            return false
        }
        return true
    }

    fun takePhoto() {
        clickPhoto()
    }

    fun takePhoto(file: File) {
        clickPhoto(file)
    }

    fun takePhotoSnap(bitmap: Boolean = false) {
        clickPhoto(snapshot = true, requiredBitmap = bitmap)
    }

    fun takePhotoSnap(file: File) {
        clickPhoto(file, true, requiredBitmap = false)
    }

    private fun startVideoRecording(file: File? = null, duration: Long = 0) {
        if (currentCaptureMode == CaptureMode.PICTURE || cameraController?.isVideoCaptureEnabled != true) {
            onMediaEventListener?.onError(Exception("Set Capture mode to Video"))
            return
        }

        if (isRecordingVideo()) {
            stopVideo()
        }

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

                OutputFileOptions.builder(this, contentUri, contentValues)
            }
        } else {
            OutputFileOptions.builder(outFile)
        }.build()

        if (duration >= MIN_REQUIRED_VIDEO_DURATION) {
            setTimer(duration)
        }

        cameraController?.startRecording(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : OnVideoSavedCallback {
                override fun onVideoSaved(outputFileResults: OutputFileResults) {
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

    }


    private fun setTimer(duration: Long) {
        videoStopTimer = object : CountDownTimer(
            duration,
            MIN_REQUIRED_VIDEO_DURATION
        ) {
            override fun onTick(p0: Long) = Unit

            override fun onFinish() {
                if (isRecordingVideo()) {
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
        if (!isRecordingVideo()) return
        cameraController?.stopRecording()
        videoStopTimer?.cancel()
        onMediaEventListener?.onVideoStopped()
    }

    private fun toggleCameraLens() {
        currentLensFacing = when (currentLensFacing) {
            backFacedCamera -> frontFacedCamera
            else -> backFacedCamera
        }
        sendLensFacingEvent()
    }

    fun setCameraFace(lens: CameraLens) {
        currentLensFacing = when (lens) {
            CameraLens.FRONT -> {
                frontFacedCamera
            }
            else -> {
                backFacedCamera
            }
        }
        cameraController?.cameraSelector = currentLensFacing
        sendLensFacingEvent()
    }

    fun getCameraFace(): CameraLens {
        return when (currentLensFacing) {
            frontFacedCamera -> CameraLens.FRONT
            else -> CameraLens.BACK
        }
    }

    private fun sendLensFacingEvent() {
        when (currentLensFacing) {
            backFacedCamera -> this.onCameraControlListener?.onLensFacingChanged(
                CameraLens.BACK
            )
            frontFacedCamera -> this.onCameraControlListener?.onLensFacingChanged(
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
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        addView(cameraPreviewView, 0)

        layoutSwitchingMode = layoutInflater.inflate(R.layout.layout_capture_mode, null)

        addView(layoutSwitchingMode)
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
        openCamera()
    }

    private fun openCamera() {
        openCamera = true
        startCameraWithCameraController()
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

    private fun startCameraWithCameraController() {
        this.lifeCycleOwner?.let { lifecycleOwner ->

            cameraController = LifecycleCameraController(context)

            cameraController?.let {
                it.bindToLifecycle(lifecycleOwner)

                cameraPreviewView.controller = it

                it.isPinchToZoomEnabled = true
                it.isTapToFocusEnabled = true

                val cameraControlInitializer = it.initializationFuture

                cameraControlInitializer.addListener(Runnable {
                    onMediaEventListener?.onCameraStarted()
                }, ContextCompat.getMainExecutor(context))
            }

        }
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