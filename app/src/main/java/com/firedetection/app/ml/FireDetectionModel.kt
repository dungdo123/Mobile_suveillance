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
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
)

class FireDetectionModel {
    
    private var interpreter: Interpreter? = null
    private val modelFile = "fire_smoke_detection.tflite"
    private val labelsFile = "labels.txt"
    
    private val inputSize = 640 // YOLOv8 input size
    private val numThreads = 4
    
    private var labels: List<String> = listOf("fire", "smoke")
    
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
            
            // For now, we'll create a placeholder. You'll need to add your actual model file
            // interpreter = Interpreter(loadModelFile(), options)
            
        } catch (e: Exception) {
            throw RuntimeException("Error loading model: ${e.message}")
        }
    }
    
    private fun loadLabels() {
        try {
            // For now, using hardcoded labels. You can load from file if needed
            labels = listOf("fire", "smoke")
        } catch (e: Exception) {
            throw RuntimeException("Error loading labels: ${e.message}")
        }
    }
    
    fun detectFireAndSmoke(bitmap: Bitmap): List<DetectionResult> {
        if (interpreter == null) {
            throw RuntimeException("Model not loaded")
        }
        
        // Preprocess image
        val inputImage = preprocessImage(bitmap)
        
        // Prepare output buffer
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 25200, 85), interpreter!!.outputTensor(0).dataType())
        
        // Run inference
        interpreter!!.run(inputImage.buffer, outputBuffer.buffer)
        
        // Post-process results
        return postprocessResults(outputBuffer, bitmap.width, bitmap.height)
    }
    
    private fun preprocessImage(bitmap: Bitmap): TensorImage {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        
        val tensorImage = TensorImage.fromBitmap(bitmap)
        return imageProcessor.process(tensorImage)
    }
    
    private fun postprocessResults(outputBuffer: TensorBuffer, originalWidth: Int, originalHeight: Int): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        
        // This is a simplified post-processing. You'll need to implement proper YOLOv8 output parsing
        // based on your specific model's output format
        
        val outputArray = outputBuffer.floatArray
        val numDetections = 25200 // YOLOv8 default
        val numClasses = 2 // fire and smoke
        
        for (i in 0 until numDetections) {
            val baseIndex = i * (numClasses + 5) // 5 for bbox + confidence
            
            val confidence = outputArray[baseIndex + 4]
            
            if (confidence > 0.5f) { // Confidence threshold
                val classScores = FloatArray(numClasses)
                for (j in 0 until numClasses) {
                    classScores[j] = outputArray[baseIndex + 5 + j]
                }
                
                val maxClassIndex = classScores.indices.maxByOrNull { classScores[it] } ?: 0
                val maxScore = classScores[maxClassIndex]
                
                if (maxScore > 0.5f) { // Class confidence threshold
                    val x = outputArray[baseIndex]
                    val y = outputArray[baseIndex + 1]
                    val w = outputArray[baseIndex + 2]
                    val h = outputArray[baseIndex + 3]
                    
                    val boundingBox = RectF(
                        (x - w/2) * originalWidth / inputSize,
                        (y - h/2) * originalHeight / inputSize,
                        (x + w/2) * originalWidth / inputSize,
                        (y + h/2) * originalHeight / inputSize
                    )
                    
                    results.add(DetectionResult(
                        label = labels[maxClassIndex],
                        confidence = maxScore,
                        boundingBox = boundingBox
                    ))
                }
            }
        }
        
        return results
    }
    
    fun isFireDetected(results: List<DetectionResult>): Boolean {
        return results.any { it.label == "fire" && it.confidence > 0.7f }
    }
    
    fun isSmokeDetected(results: List<DetectionResult>): Boolean {
        return results.any { it.label == "smoke" && it.confidence > 0.7f }
    }
    
    fun close() {
        interpreter?.close()
        interpreter = null
    }
} 