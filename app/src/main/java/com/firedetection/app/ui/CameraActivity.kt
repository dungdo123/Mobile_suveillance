package com.firedetection.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.firedetection.app.databinding.ActivityCameraBinding
import com.firedetection.app.ml.DetectionResult
import com.firedetection.app.ml.FireDetectionModel
import com.firedetection.app.service.FireDetectionService
import com.firedetection.app.utils.NotificationHelper
import com.firedetection.app.viewmodel.CameraViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var viewModel: CameraViewModel
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var notificationHelper: NotificationHelper

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var fireDetectionModel: FireDetectionModel? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CameraViewModel::class.java]
        cameraExecutor = Executors.newSingleThreadExecutor()
        notificationHelper = NotificationHelper(this)

        setupUI()
        setupObservers()
        checkPermissions()
    }

    private fun setupUI() {
        binding.apply {
            btnBack.setOnClickListener {
                finish()
            }

            btnSettings.setOnClickListener {
                // TODO: Open settings
            }

            switchDetection.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    startDetection()
                } else {
                    stopDetection()
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.detectionResults.observe(this) { results ->
            updateDetectionOverlay(results)
        }

        viewModel.isFireDetected.observe(this) { isFire ->
            if (isFire) {
                handleFireDetection()
            }
        }

        viewModel.isSmokeDetected.observe(this) { isSmoke ->
            if (isSmoke) {
                handleSmokeDetection()
            }
        }
    }

    private fun checkPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        if (!binding.switchDetection.isChecked) {
            imageProxy.close()
            return
        }

        val bitmap = imageProxy.toBitmap()
        if (bitmap != null) {
            try {
                val results = fireDetectionModel?.detectFireAndSmoke(bitmap) ?: emptyList()
                viewModel.updateDetectionResults(results)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image", e)
            }
        }
        imageProxy.close()
    }

    private fun updateDetectionOverlay(results: List<DetectionResult>) {
        binding.overlayView.clear()
        
        results.forEach { result ->
            val paint = Paint().apply {
                color = when (result.label) {
                    "fire" -> Color.RED
                    "smoke" -> Color.GRAY
                    else -> Color.YELLOW
                }
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }

            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 40f
                style = Paint.Style.FILL
            }

            binding.overlayView.addRect(result.boundingBox, paint)
            binding.overlayView.addText(
                "${result.label} (${(result.confidence * 100).toInt()}%)",
                result.boundingBox.left,
                result.boundingBox.top - 10
            )
        }
        
        binding.overlayView.invalidate()
    }

    private fun handleFireDetection() {
        notificationHelper.showFireAlert()
        // Start background service for continuous monitoring
        val serviceIntent = Intent(this, FireDetectionService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun handleSmokeDetection() {
        notificationHelper.showSmokeAlert()
    }

    private fun startDetection() {
        try {
            fireDetectionModel = FireDetectionModel()
            binding.tvStatus.text = "Detection started"
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start detection: ${e.message}", Toast.LENGTH_LONG).show()
            binding.switchDetection.isChecked = false
        }
    }

    private fun stopDetection() {
        fireDetectionModel?.close()
        fireDetectionModel = null
        binding.tvStatus.text = "Detection stopped"
        binding.overlayView.clear()
        binding.overlayView.invalidate()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        fireDetectionModel?.close()
    }

    companion object {
        private const val TAG = "CameraActivity"
    }
}

// Extension function to convert ImageProxy to Bitmap
fun ImageProxy.toBitmap(): Bitmap? {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
} 