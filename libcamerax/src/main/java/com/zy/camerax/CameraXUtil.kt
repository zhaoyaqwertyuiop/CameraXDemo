package com.zy.camerax

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.window.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.common.internal.ImageConvertUtils
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * @description：
 * @author: zhaoya
 * @create：2022/8/19 0019 15:37
 */
class CameraXUtil {

    val TAG = "CameraXUtil"

    var isInit = false
        private set
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK // 摄像头
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var windowManager: WindowManager
    private val cameraExecutor by lazy { Executors.newSingleThreadExecutor() }
    private var frameCallback: ((ImageProxy) -> Unit) ? = null
    private lateinit var owner: LifecycleOwner
    private lateinit var previewView: PreviewView

    fun init(owner: LifecycleOwner, previewView: PreviewView, frameCallback: ((ImageProxy) -> Unit) ? = null) {
        if (isInit) {
            return
        }
        isInit = true
        this.owner = owner
        this.previewView = previewView
        this.frameCallback = frameCallback
        windowManager = WindowManager(previewView.context)
        previewView.post {
            setUpCamera()
        }

        owner.lifecycle.addObserver(object : DefaultLifecycleObserver{
            override fun onDestroy(owner: LifecycleOwner) {
                cameraExecutor.shutdown()
                this@CameraXUtil.frameCallback = null
            }
        })
    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {
        val context = previewView.context
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(Runnable {

            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }


            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    /** Declare and bind preview, capture and analysis use cases (绑定用例) */
    fun bindCameraUseCases() {
        if (!isInit) {
            return
        }
        // Get screen metrics used to setup camera for full screen resolution
        val metrics = windowManager.getCurrentWindowMetrics().bounds
        Log.d(TAG, "Screen metrics: ${metrics.width()} x ${metrics.height()}")

        val screenAspectRatio = aspectRatio(metrics.width(), metrics.height())
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = previewView.display.rotation

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        preview = Preview.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation
            .setTargetRotation(rotation)
            .build()

        // ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // We request aspect ratio but no resolution to match preview config, but letting
            // CameraX optimize for whatever specific resolution best fits our use cases
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()

        // ImageAnalysis
        imageAnalyzer = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()
            // The analyzer can then be assigned to the instance
            .also {
                it.setAnalyzer(cameraExecutor) { image ->
                    // Since format in ImageAnalysis is YUV, image.planes[0] contains the luminance plane
                    val buffer = image.planes[0].buffer
                    // Extract image data from callback object
                    val data = buffer.toByteArray()

                    frameCallback?.invoke(image)
                    image.close()
                }
            }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(owner, cameraSelector, preview, imageCapture, imageAnalyzer)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(previewView.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    /**
     *  [androidx.camera.core.ImageAnalysis.Builder] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - 4.0 / 3.0) <= abs(previewRatio - 16.0 / 9.0)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    // 拍照
    fun takePickture(photoFile: File, resultCllback:(Boolean) -> Unit) {
        imageCapture?.let { imageCapture ->

            // Setup image capture metadata
            val metadata = ImageCapture.Metadata().apply {

                // Mirror image when using the front camera
                isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
            }

            // Create output options object which contains file + metadata
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                .setMetadata(metadata)
                .build()

            // Setup image capture listener which is triggered after photo has been taken
            imageCapture.takePicture(
                outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        resultCllback(false)
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                        Log.d(TAG, "Photo capture succeeded: $savedUri")
                        resultCllback(true)
                        // We can only change the foreground Drawable using API level 23+ API
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            // Update the gallery thumbnail with latest picture taken(使用拍摄的最新图片更新库缩略图)
//                            setGalleryThumbnail(savedUri)
                        }
                    }
                })
        }
    }

    companion object {

        /**
         *  convert image proxy to bitmap
         *  @param image
         */
        @SuppressLint("UnsafeOptInUsageError")
        fun imageToBitmap(image: ImageProxy): Bitmap? {
            val inputImage = InputImage.fromMediaImage(image.image, image.imageInfo.rotationDegrees)
            val bitmap = ImageConvertUtils.getInstance().getUpRightBitmap(inputImage)
            return bitmap
        }
    }
}