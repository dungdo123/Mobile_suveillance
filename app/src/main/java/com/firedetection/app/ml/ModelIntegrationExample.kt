package com.firedetection.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Example implementation showing how to properly integrate your YOLOv8 model
 * This is a template - you'll need to modify it based on your specific model
 */
class ModelIntegrationExample(private val context: Context) {
    
    private var interpreter: Interpreter? = null
    private val modelFile = "fire_smoke_detection.tflite"
    
    // YOLOv8 specific parameters - adjust based on your model
    private val inputSize = 640
    private val numClasses = 2 // fire, smoke
    private val labels = listOf("fire", "smoke")
    
    init {
        loadModel()
    }
    
    private fun loadModel() {
        try {
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseXNNPACK(true)
                // Enable GPU delegation if available
                // setUseGPU(true)
            }
            
            val modelBuffer = loadModelFile(modelFile)
            interpreter = Interpreter(modelBuffer, options)
            
            Log.d("ModelIntegration", "Model loaded successfully")
            
        } catch (e: Exception) {
            Log.e("ModelIntegration", "Error loading model: ${e.message}")
            throw RuntimeException("Failed to load model", e)
        }
    }
    
    private fun loadModelFile(fileName: String): ByteBuffer {
        val assetManager = context.assets
        val modelFile = assetManager.openFd(fileName)
        val inputStream = FileInputStream(modelFile.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = modelFile.startOffset
        val declaredLength = modelFile.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    fun detectFireAndSmoke(bitmap: Bitmap): List<DetectionResult> {
        if (interpreter == null) {
            throw RuntimeException("Model not loaded")
        }
        
        try {
            // Preprocess image
            val inputImage = preprocessImage(bitmap)
            
            // Prepare output buffer - adjust size based on your model's output
            val outputShape = interpreter!!.getOutputTensor(0).shape()
            val outputBuffer = TensorBuffer.createFixedSize(outputShape, interpreter!!.getOutputTensor(0).dataType())
            
            // Run inference
            interpreter!!.run(inputImage.buffer, outputBuffer.buffer)
            
            // Post-process results
            return postprocessResults(outputBuffer, bitmap.width, bitmap.height)
            
        } catch (e: Exception) {
            Log.e("ModelIntegration", "Error during inference: ${e.message}")
            return emptyList()
        }
    }
    
    private fun preprocessImage(bitmap: Bitmap): TensorImage {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            // Add normalization if your model requires it
            // .add(NormalizeOp(0f, 255f))
            .build()
        
        val tensorImage = TensorImage.fromBitmap(bitmap)
        return imageProcessor.process(tensorImage)
    }
    
    private fun postprocessResults(outputBuffer: TensorBuffer, originalWidth: Int, originalHeight: Int): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        
        // This is a generic YOLOv8 post-processing example
        // You'll need to modify this based on your specific model's output format
        
        val outputArray = outputBuffer.floatArray
        val outputShape = outputBuffer.shape
        
        // YOLOv8 typically outputs [1, 25200, 85] for COCO format
        // or [1, 25200, num_classes + 5] for custom models
        val numDetections = outputShape[1] // 25200 for YOLOv8
        val outputPerDetection = outputShape[2] // 85 for COCO, or num_classes + 5
        
        Log.d("ModelIntegration", "Output shape: ${outputShape.contentToString()}")
        Log.d("ModelIntegration", "Num detections: $numDetections, Output per detection: $outputPerDetection")
        
        for (i in 0 until numDetections) {
            val baseIndex = i * outputPerDetection
            
            // Extract confidence score (typically at index 4)
            val confidence = outputArray[baseIndex + 4]
            
            if (confidence > 0.5f) { // Confidence threshold
                // Extract class scores
                val classScores = FloatArray(numClasses)
                for (j in 0 until numClasses) {
                    classScores[j] = outputArray[baseIndex + 5 + j]
                }
                
                val maxClassIndex = classScores.indices.maxByOrNull { classScores[it] } ?: 0
                val maxScore = classScores[maxClassIndex]
                
                if (maxScore > 0.5f) { // Class confidence threshold
                    // Extract bounding box coordinates
                    val x = outputArray[baseIndex] // center x
                    val y = outputArray[baseIndex + 1] // center y
                    val w = outputArray[baseIndex + 2] // width
                    val h = outputArray[baseIndex + 3] // height
                    
                    // Convert to pixel coordinates
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
                    
                    Log.d("ModelIntegration", "Detection: ${labels[maxClassIndex]} (${(maxScore * 100).toInt()}%) at $boundingBox")
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
        Log.d("ModelIntegration", "Model closed")
    }
}

/**
 * Instructions for integrating your YOLOv8 model:
 * 
 * 1. Convert your model to TensorFlow Lite:
 *    ```python
 *    from ultralytics import YOLO
 *    model = YOLO('your_model.pt')
 *    model.export(format='tflite', imgsz=640)
 *    ```
 * 
 * 2. Place the .tflite file in app/src/main/assets/
 * 
 * 3. Update the model parameters in this class:
 *    - inputSize: Your model's input size
 *    - numClasses: Number of classes your model detects
 *    - labels: Your class labels
 * 
 * 4. Modify postprocessResults() based on your model's output format:
 *    - Check the output shape and format
 *    - Adjust the parsing logic accordingly
 *    - Update confidence thresholds
 * 
 * 5. Test with your specific fire/smoke detection scenarios
 */ 