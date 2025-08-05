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
    private var isTestMode = false
    private var testFireOverlay: android.view.View? = null

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

            // Test simulation buttons
            btnTestFire.setOnClickListener {
                startFireTest()
            }

            btnStopTest.setOnClickListener {
                stopFireTest()
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

        viewModel.detectionCount.observe(this) { count ->
            binding.tvDetectionCount.text = "Detections: $count"
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
                val results = fireDetectionModel?.detectFire(bitmap) ?: emptyList()
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
                color = Color.RED // Fire detection only
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



    private fun startDetection() {
        try {
            fireDetectionModel = FireDetectionModel(this)
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

    // Test simulation methods
    private fun startFireTest() {
        isTestMode = true
        binding.tvStatus.text = "Test Mode: Fire simulation active"
        
        // Ensure the model is loaded before testing
        if (fireDetectionModel == null) {
            try {
                fireDetectionModel = FireDetectionModel(this)
                binding.tvStatus.text = "Test Mode: Model loaded, starting inference..."
            } catch (e: Exception) {
                Log.e(TAG, "Error loading model for test", e)
                binding.tvStatus.text = "Test Mode: Failed to load model"
                Toast.makeText(this, "Failed to load detection model", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        // Create and add fire overlay using fire.jpg from assets
        testFireOverlay = android.widget.ImageView(this).apply {
            try {
                val inputStream = assets.open("fire.jpg")
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                
                // Scale the bitmap to cover more of the frame (e.g., 60% of screen width)
                val screenWidth = resources.displayMetrics.widthPixels
                val targetWidth = (screenWidth * 0.9).toInt()
                val targetHeight = (targetWidth * 0.9).toInt() // Maintain aspect ratio
                
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                    bitmap, 
                    targetWidth, 
                    targetHeight, 
                    true
                )
                
                setImageBitmap(scaledBitmap)
                layoutParams = android.view.ViewGroup.LayoutParams(
                    targetWidth,
                    targetHeight
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading fire image", e)
                // Fallback to a simple colored view if image loading fails
                setBackgroundColor(android.graphics.Color.RED)
                val screenWidth = resources.displayMetrics.widthPixels
                val targetWidth = (screenWidth * 0.6).toInt()
                val targetHeight = (targetWidth * 0.75).toInt()
                layoutParams = android.view.ViewGroup.LayoutParams(targetWidth, targetHeight)
            }
        }
        
        // Add overlay to the camera view
        (binding.viewFinder.parent as android.view.ViewGroup).addView(testFireOverlay)
        
        // Position the fire overlay in the center of the screen
        testFireOverlay?.post {
            testFireOverlay?.let { overlay ->
                val centerX = binding.viewFinder.width / 2 - overlay.width / 2
                val centerY = binding.viewFinder.height / 2 - overlay.height / 2
                overlay.x = centerX.toFloat()
                overlay.y = centerY.toFloat()
            }
        }
        
        // Simulate fire detection after a short delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isTestMode) {
                simulateFireDetection()
            }
        }, 2000)
        
        Toast.makeText(this, "Fire test simulation started", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopFireTest() {
        isTestMode = false
        binding.tvStatus.text = "Test Mode: Stopped"
        
        // Remove fire overlay
        testFireOverlay?.let { overlay ->
            (overlay.parent as? android.view.ViewGroup)?.removeView(overlay)
            testFireOverlay = null
        }
        
        // Clear detection results
        binding.overlayView.clear()
        binding.overlayView.invalidate()
        
        Toast.makeText(this, "Fire test simulation stopped", Toast.LENGTH_SHORT).show()
    }
    
    private fun simulateFireDetection() {
        if (!isTestMode) return
        
        // Load the fire.jpg image and run real model inference
        try {
            val inputStream = assets.open("fire.jpg")
            val fireBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            android.util.Log.d("CameraActivity", "Loaded fire.jpg: ${fireBitmap.width}x${fireBitmap.height}")
            android.util.Log.d("CameraActivity", "Fire bitmap config: ${fireBitmap.config}")
            
            // Run real model inference on the fire image
            val realResults = fireDetectionModel?.detectFire(fireBitmap) ?: emptyList()
            
            android.util.Log.d("CameraActivity", "Model inference completed, found ${realResults.size} detections")
            
            if (realResults.isNotEmpty()) {
                // Model detected fire - use real results
                viewModel.updateDetectionResults(realResults)
                viewModel.setFireDetected(true)
                notificationHelper.showFireAlert()
                binding.tvStatus.text = "Test Mode: Real fire detection! (${(realResults.first().confidence * 100).toInt()}% confidence)"
                
                android.util.Log.d("CameraActivity", "Fire detected with confidence: ${realResults.first().confidence}")
            } else {
                // No fire detected by model
                viewModel.updateDetectionResults(emptyList())
                viewModel.setFireDetected(false)
                binding.tvStatus.text = "Test Mode: No fire detected in test image"
                
                android.util.Log.d("CameraActivity", "No fire detected in the image")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error running model inference on test image", e)
            binding.tvStatus.text = "Test Mode: Error running model inference"
        }
        
        // Continue simulation every 3 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isTestMode) {
                simulateFireDetection()
            }
        }, 3000)
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