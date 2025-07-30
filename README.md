# Fire Detection Android App

A modern Android application for real-time fire and smoke detection using YOLOv8 model. The app provides continuous monitoring through the device camera and sends alerts when fire or smoke is detected.

## Features

- 🔥 **Real-time Fire Detection**: Uses YOLOv8 model for accurate fire detection
- 💨 **Smoke Detection**: Detects smoke patterns that may indicate fire
- 📱 **Modern UI**: Material Design 3 interface with intuitive controls
- 🔔 **Smart Notifications**: Instant alerts with sound and vibration
- ⚙️ **Customizable Settings**: Adjust detection sensitivity and notification preferences
- 📷 **Camera Integration**: Real-time camera feed with detection overlay
- 🔄 **Background Monitoring**: Optional background detection service

## Prerequisites

- Android Studio Arctic Fox or later
- Android SDK 24+ (Android 7.0+)
- Your YOLOv8 fire/smoke detection model converted to TensorFlow Lite format

## Setup Instructions

### 1. Clone the Repository
```bash
git clone <repository-url>
cd firedetection
```

### 2. Add Your YOLOv8 Model

1. Convert your YOLOv8 model to TensorFlow Lite format:
   ```python
   # Example conversion script
   from ultralytics import YOLO
   
   # Load your trained model
   model = YOLO('path/to/your/fire_smoke_model.pt')
   
   # Export to TensorFlow Lite
   model.export(format='tflite', imgsz=640)
   ```

2. Place your converted model file (`fire_smoke_detection.tflite`) in:
   ```
   app/src/main/assets/
   ```

3. Update the model loading code in `FireDetectionModel.kt`:
   ```kotlin
   private fun loadModel() {
       try {
           val options = Interpreter.Options().apply {
               setNumThreads(numThreads)
               setUseXNNPACK(true)
           }
           
           // Load your actual model file
           val modelFile = loadModelFile("fire_smoke_detection.tflite")
           interpreter = Interpreter(modelFile, options)
           
       } catch (e: Exception) {
           throw RuntimeException("Error loading model: ${e.message}")
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
   ```

### 3. Update Model Configuration

Modify the `FireDetectionModel.kt` file to match your model's specifications:

```kotlin
class FireDetectionModel {
    private val inputSize = 640 // Your model's input size
    private val numClasses = 2 // Number of classes (fire, smoke)
    private val labels = listOf("fire", "smoke") // Your class labels
    
    // Update post-processing logic based on your model's output format
    private fun postprocessResults(outputBuffer: TensorBuffer, originalWidth: Int, originalHeight: Int): List<DetectionResult> {
        // Implement post-processing logic specific to your model
        // This will depend on your YOLOv8 model's output format
    }
}
```

### 4. Build and Run

1. Open the project in Android Studio
2. Sync Gradle files
3. Connect an Android device or start an emulator
4. Click "Run" to build and install the app

## Project Structure

```
app/
├── src/main/
│   ├── java/com/firedetection/app/
│   │   ├── MainActivity.kt              # Main app screen
│   │   ├── ml/
│   │   │   └── FireDetectionModel.kt   # ML model wrapper
│   │   ├── ui/
│   │   │   ├── CameraActivity.kt       # Camera and detection
│   │   │   ├── SettingsActivity.kt     # App settings
│   │   │   └── DetectionOverlayView.kt # Detection visualization
│   │   ├── service/
│   │   │   └── FireDetectionService.kt # Background service
│   │   ├── utils/
│   │   │   └── NotificationHelper.kt   # Notification management
│   │   └── viewmodel/
│   │       ├── MainViewModel.kt        # Main screen logic
│   │       └── CameraViewModel.kt      # Camera screen logic
│   ├── res/
│   │   ├── layout/                     # UI layouts
│   │   ├── values/                     # Strings, colors, themes
│   │   ├── drawable/                   # Icons and graphics
│   │   └── xml/                        # Preferences
│   └── assets/                         # Place your model here
└── build.gradle                        # App dependencies
```

## Key Components

### 1. FireDetectionModel
- Wraps your YOLOv8 TensorFlow Lite model
- Handles image preprocessing and post-processing
- Provides detection results with bounding boxes

### 2. CameraActivity
- Manages camera preview and image analysis
- Real-time detection with visual overlay
- Handles detection alerts and notifications

### 3. NotificationHelper
- Manages fire and smoke alert notifications
- Configurable sound and vibration
- High-priority alerts for safety

### 4. Settings
- Detection sensitivity adjustment
- Notification preferences
- Camera resolution settings

## Customization

### Model Integration
1. **Input Preprocessing**: Update `preprocessImage()` to match your model's requirements
2. **Output Post-processing**: Modify `postprocessResults()` to parse your model's output format
3. **Confidence Thresholds**: Adjust detection sensitivity in the settings

### UI Customization
- Colors: Modify `colors.xml`
- Themes: Update `themes.xml`
- Layouts: Edit layout files in `res/layout/`

### Notification Settings
- Alert sounds: Add custom sound files
- Vibration patterns: Modify vibration arrays
- Notification channels: Update channel configurations

## Permissions

The app requires the following permissions:
- `CAMERA`: For real-time detection
- `POST_NOTIFICATIONS`: For fire/smoke alerts
- `INTERNET`: For potential cloud features
- `FOREGROUND_SERVICE`: For background monitoring

## Performance Optimization

1. **Model Optimization**:
   - Use TensorFlow Lite model optimization
   - Enable XNNPACK for faster inference
   - Consider model quantization

2. **Camera Settings**:
   - Adjust resolution based on device capabilities
   - Optimize frame rate for detection accuracy

3. **Memory Management**:
   - Proper model cleanup in `onDestroy()`
   - Efficient image processing pipeline

## Troubleshooting

### Common Issues

1. **Model Loading Error**:
   - Ensure model file is in `assets/` folder
   - Check model format (TensorFlow Lite)
   - Verify model input/output specifications

2. **Camera Permission**:
   - Grant camera permission when prompted
   - Check device camera compatibility

3. **Detection Accuracy**:
   - Adjust confidence thresholds
   - Ensure proper lighting conditions
   - Test with various fire/smoke scenarios

### Debug Mode
Enable debug logging in `FireDetectionModel.kt`:
```kotlin
Log.d("FireDetection", "Detection results: $results")
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
1. Check the troubleshooting section
2. Review model integration requirements
3. Ensure all dependencies are properly configured

## Safety Notice

⚠️ **Important**: This app is designed for early warning and should not replace professional fire safety systems. Always follow local fire safety regulations and have proper fire detection equipment installed. 