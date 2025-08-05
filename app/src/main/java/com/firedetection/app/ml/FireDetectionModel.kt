package com.firedetection.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.DataType
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class DetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
)

class FireDetectionModel(private val context: Context) {
    
    private var interpreter: Interpreter? = null
    private val modelFile = "fire_detection.tflite"
    
    private val inputSize = 416 // YOLOv8 input size
    private val numThreads = 4
    
    private var labels: List<String> = listOf("fire")
    
    init {
        loadModel()
        loadLabels()
    }
    
    private fun loadModel() {
        try {
            val options = Interpreter.Options().apply {
                setNumThreads(numThreads)
                setUseXNNPACK(true)
            }
            
            // Load the actual model file from assets
            interpreter = Interpreter(loadModelFile(), options)
            
        } catch (e: Exception) {
            throw RuntimeException("Error loading model: ${e.message}")
        }
    }
    
    private fun loadModelFile(): ByteBuffer {
        val assetManager = context.assets
        val modelFileDescriptor = assetManager.openFd(modelFile)
        val inputStream = FileInputStream(modelFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = modelFileDescriptor.startOffset
        val declaredLength = modelFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    private fun loadLabels() {
        try {
            // Using hardcoded labels for fire detection only
            labels = listOf("fire")
        } catch (e: Exception) {
            throw RuntimeException("Error loading labels: ${e.message}")
        }
    }
    
    fun detectFire(bitmap: Bitmap): List<DetectionResult> {
        if (interpreter == null) {
            throw RuntimeException("Model not loaded")
        }
        
        // Preprocess image
        val inputImage = preprocessImage(bitmap)
        
        // Get model input and output details
        val inputShape = interpreter!!.getInputTensor(0).shape()
        val outputShape = interpreter!!.getOutputTensor(0).shape()
        
        android.util.Log.d("FireDetectionModel", "Input shape: ${inputShape.contentToString()}")
        android.util.Log.d("FireDetectionModel", "Output shape: ${outputShape.contentToString()}")
        
        // Prepare output buffer based on actual model output shape
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32)
        
        // Run inference
        interpreter!!.run(inputImage.buffer, outputBuffer.buffer)
        
        // Post-process results
        return postprocessResults(outputBuffer, bitmap.width, bitmap.height)
    }
    
    private fun preprocessImage(bitmap: Bitmap): TensorImage {
        // Ensure bitmap is in RGB format
        val rgbBitmap = if (bitmap.config != android.graphics.Bitmap.Config.ARGB_8888) {
            bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        } else {
            bitmap
        }
        
        android.util.Log.d("FireDetectionModel", "Original bitmap size: ${bitmap.width}x${bitmap.height}")
        android.util.Log.d("FireDetectionModel", "Bitmap config: ${bitmap.config}")
        
        // Create image processor for YOLOv8 preprocessing
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(rgbBitmap)
        
        // Process the image
        val processedImage = imageProcessor.process(tensorImage)
        
        android.util.Log.d("FireDetectionModel", "Processed image size: ${processedImage.width}x${processedImage.height}")
        
        return processedImage
    }
    
    private fun postprocessResults(outputBuffer: TensorBuffer, originalWidth: Int, originalHeight: Int): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        
        // Get the actual output shape
        val outputShape = outputBuffer.shape
        val outputArray = outputBuffer.floatArray
        
        android.util.Log.d("FireDetectionModel", "Output array size: ${outputArray.size}")
        android.util.Log.d("FireDetectionModel", "Output shape: ${outputShape.contentToString()}")
        
        // Handle different YOLOv8 output formats
        when {
            // Format: [1, 84, 8400] - YOLOv8 with 80 classes + 4 bbox coords
            outputShape.size == 3 && outputShape[1] == 84 -> {
                val numClasses = 80
                val numDetections = outputShape[2]
                
                for (i in 0 until numDetections) {
                    val confidence = outputArray[4 * numDetections + i] // confidence is at index 4
                    
                    if (confidence > 0.25f) { // Lower threshold for testing
                        val classScores = FloatArray(numClasses)
                        for (j in 0 until numClasses) {
                            classScores[j] = outputArray[(4 + j) * numDetections + i]
                        }
                        
                        val maxClassIndex = classScores.indices.maxByOrNull { classScores[it] } ?: 0
                        val maxScore = classScores[maxClassIndex]
                        
                        if (maxScore > 0.25f) {
                            val x = outputArray[i] / inputSize.toFloat()
                            val y = outputArray[numDetections + i] / inputSize.toFloat()
                            val w = outputArray[2 * numDetections + i] / inputSize.toFloat()
                            val h = outputArray[3 * numDetections + i] / inputSize.toFloat()
                            
                            val boundingBox = RectF(
                                (x - w/2) * originalWidth,
                                (y - h/2) * originalHeight,
                                (x + w/2) * originalWidth,
                                (y + h/2) * originalHeight
                            )
                            
                            results.add(DetectionResult(
                                label = "fire", // Assuming fire is class 0 or we're using a fire-only model
                                confidence = maxScore,
                                boundingBox = boundingBox
                            ))
                        }
                    }
                }
            }
            
            // Format: [1, 25200, 6] - YOLOv8 with custom classes
            outputShape.size == 3 && outputShape[1] == 25200 -> {
                val numDetections = outputShape[1]
                val numClasses = outputShape[2] - 4 // 4 for bbox + confidence
                
                for (i in 0 until numDetections) {
                    val baseIndex = i * (numClasses + 5)
                    
                    if (baseIndex + 4 < outputArray.size) {
                        val confidence = outputArray[baseIndex + 4]
                        
                        if (confidence > 0.25f) {
                            val classScores = FloatArray(numClasses)
                            for (j in 0 until numClasses) {
                                if (baseIndex + 5 + j < outputArray.size) {
                                    classScores[j] = outputArray[baseIndex + 5 + j]
                                }
                            }
                            
                            val maxClassIndex = classScores.indices.maxByOrNull { classScores[it] } ?: 0
                            val maxScore = classScores[maxClassIndex]
                            
                            if (maxScore > 0.25f) {
                                val x = outputArray[baseIndex] / inputSize.toFloat()
                                val y = outputArray[baseIndex + 1] / inputSize.toFloat()
                                val w = outputArray[baseIndex + 2] / inputSize.toFloat()
                                val h = outputArray[baseIndex + 3] / inputSize.toFloat()
                                
                                val boundingBox = RectF(
                                    (x - w/2) * originalWidth,
                                    (y - h/2) * originalHeight,
                                    (x + w/2) * originalWidth,
                                    (y + h/2) * originalHeight
                                )
                                
                                results.add(DetectionResult(
                                    label = "fire",
                                    confidence = maxScore,
                                    boundingBox = boundingBox
                                ))
                            }
                        }
                    }
                }
            }
            
            // Format: [1, 6, 8400] - YOLOv8 with 1 class (fire only)
            outputShape.size == 3 && outputShape[1] == 6 -> {
                val numDetections = outputShape[2]
                
                for (i in 0 until numDetections) {
                    val confidence = outputArray[4 * numDetections + i]
                    
                    if (confidence > 0.25f) {
                        val classScore = outputArray[5 * numDetections + i]
                        
                        if (classScore > 0.25f) {
                            val x = outputArray[i] / inputSize.toFloat()
                            val y = outputArray[numDetections + i] / inputSize.toFloat()
                            val w = outputArray[2 * numDetections + i] / inputSize.toFloat()
                            val h = outputArray[3 * numDetections + i] / inputSize.toFloat()
                            
                            val boundingBox = RectF(
                                (x - w/2) * originalWidth,
                                (y - h/2) * originalHeight,
                                (x + w/2) * originalWidth,
                                (y + h/2) * originalHeight
                            )
                            
                            results.add(DetectionResult(
                                label = "fire",
                                confidence = classScore,
                                boundingBox = boundingBox
                            ))
                        }
                    }
                }
            }
            
            else -> {
                android.util.Log.e("FireDetectionModel", "Unknown output format: ${outputShape.contentToString()}")
            }
        }
        
        android.util.Log.d("FireDetectionModel", "Found ${results.size} detections")
        return results
    }
    
    fun isFireDetected(results: List<DetectionResult>): Boolean {
        return results.any { it.label == "fire" && it.confidence > 0.7f }
    }
    
    fun close() {
        interpreter?.close()
        interpreter = null
    }
} 