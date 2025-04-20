# Migrating VisionHelper to Flutter

This guide outlines how to recreate the VisionHelper app using Flutter for cross-platform deployment (Android and iOS).

## Setup

1. Install Flutter:
   ```bash
   # Install Flutter following the official guide:
   # https://flutter.dev/docs/get-started/install
   ```

2. Create a new Flutter project:
   ```bash
   flutter create vision_helper
   cd vision_helper
   ```

3. Configure your project in `pubspec.yaml`:
   ```yaml
   dependencies:
     flutter:
       sdk: flutter
     camera: ^0.10.5+5         # Camera functionality
     tflite_flutter: ^0.10.3    # TensorFlow Lite integration
     provider: ^6.0.5          # State management
     path_provider: ^2.1.1     # File system access
     image_picker: ^1.0.4      # Image picking
     image_gallery_saver: ^2.0.3 # Saving to gallery
     permission_handler: ^11.0.0 # Permission management
     intl: ^0.18.1             # Date formatting
   ```

## Project Structure

```
lib/
├── main.dart                 # App entry point
├── screens/
│   ├── camera_screen.dart    # Main camera UI
│   ├── gallery_screen.dart   # Custom gallery
│   └── preview_screen.dart   # Image preview before saving
├── models/
│   └── detection_model.dart  # Data models
├── services/
│   ├── camera_service.dart   # Camera functionality
│   └── tensorflow_service.dart # ML model operations
├── utils/
│   └── file_utils.dart       # File operations
└── widgets/
    ├── detection_box.dart    # Detection box overlay
    ├── detection_list.dart   # List of detections
    └── gallery_item.dart     # Gallery item widget
```

## TensorFlow Model

1. Download the MobileNet model:
   ```bash
   mkdir -p assets/ml
   curl -o assets/ml/mobilenet_v1_1.0_224_quant.tflite \
     https://github.com/tflite-soc/tensorflow-models/raw/master/mobilenet-v1/mobilenet_v1_1.0_224_quant.tflite
   curl -o assets/ml/labels_mobilenet_quant_v1_224.txt \
     https://raw.githubusercontent.com/tflite-soc/tensorflow-models/master/mobilenet-v1/labels_mobilenet_quant_v1_224.txt
   ```

2. Register assets in `pubspec.yaml`:
   ```yaml
   flutter:
     assets:
       - assets/ml/mobilenet_v1_1.0_224_quant.tflite
       - assets/ml/labels_mobilenet_quant_v1_224.txt
   ```

## Implementation Guide

### 1. TensorFlow Service

```dart
// lib/services/tensorflow_service.dart
import 'dart:io';
import 'package:flutter/services.dart';
import 'package:tflite_flutter/tflite_flutter.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart';

class DetectionResult {
  final String label;
  final double confidence;
  
  DetectionResult({required this.label, required this.confidence});
}

class TensorFlowService {
  Interpreter? _interpreter;
  List<String> _labels = [];
  
  Future<void> loadModel() async {
    try {
      final interpreterOptions = InterpreterOptions()..threads = 4;
      
      // Load model
      final modelFile = await _getModel();
      _interpreter = await Interpreter.fromFile(modelFile, options: interpreterOptions);
      
      // Load labels
      final labelsData = await rootBundle.loadString('assets/ml/labels_mobilenet_quant_v1_224.txt');
      _labels = labelsData.split('\n');
      
      print('TensorFlow model loaded successfully');
    } catch (e) {
      print('Error loading model: $e');
    }
  }
  
  Future<File> _getModel() async {
    final modelPath = 'mobilenet_v1_1.0_224_quant.tflite';
    final appDir = await getApplicationDocumentsDirectory();
    final file = File(join(appDir.path, modelPath));
    
    if (!await file.exists()) {
      final byteData = await rootBundle.load('assets/ml/$modelPath');
      await file.writeAsBytes(byteData.buffer.asUint8List());
    }
    
    return file;
  }
  
  Future<List<DetectionResult>> analyzeImage(File image) async {
    if (_interpreter == null) {
      await loadModel();
    }
    
    // Prepare input: resize image to 224x224
    final imageBytes = await image.readAsBytes();
    // Convert and resize image to match model input shape
    
    // Run model inference
    // ...
    
    // Process results and map to labels
    List<DetectionResult> results = [];
    
    return results;
  }
  
  void dispose() {
    _interpreter?.close();
  }
}
```

### 2. Camera Service

```dart
// lib/services/camera_service.dart
import 'package:camera/camera.dart';

class CameraService {
  CameraController? _controller;
  
  Future<void> initialize() async {
    final cameras = await availableCameras();
    final camera = cameras.first;
    
    _controller = CameraController(
      camera,
      ResolutionPreset.medium,
      enableAudio: false,
      imageFormatGroup: ImageFormatGroup.jpeg,
    );
    
    await _controller!.initialize();
  }
  
  CameraController? get controller => _controller;
  
  void dispose() {
    _controller?.dispose();
  }
}
```

### 3. Camera Screen

```dart
// lib/screens/camera_screen.dart
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:provider/provider.dart';
import '../services/camera_service.dart';
import '../services/tensorflow_service.dart';
import '../widgets/detection_box.dart';

class CameraScreen extends StatefulWidget {
  @override
  _CameraScreenState createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  final CameraService _cameraService = CameraService();
  final TensorFlowService _tensorFlowService = TensorFlowService();
  List<DetectionResult> _detectionResults = [];
  bool _isAnalyzing = false;
  
  @override
  void initState() {
    super.initState();
    _initializeCamera();
  }
  
  Future<void> _initializeCamera() async {
    await _cameraService.initialize();
    await _tensorFlowService.loadModel();
    setState(() {});
  }
  
  Future<void> _analyzeImage() async {
    if (_isAnalyzing) return;
    
    setState(() {
      _isAnalyzing = true;
    });
    
    try {
      final image = await _cameraService.controller!.takePicture();
      final results = await _tensorFlowService.analyzeImage(File(image.path));
      
      setState(() {
        _detectionResults = results;
      });
    } catch (e) {
      print('Error analyzing image: $e');
    } finally {
      setState(() {
        _isAnalyzing = false;
      });
    }
  }
  
  @override
  Widget build(BuildContext context) {
    if (_cameraService.controller == null || !_cameraService.controller!.value.isInitialized) {
      return Center(child: CircularProgressIndicator());
    }
    
    return Scaffold(
      body: Stack(
        children: [
          CameraPreview(_cameraService.controller!),
          
          // Overlay detection box
          DetectionBox(
            results: _detectionResults,
          ),
          
          // Bottom controls
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: Container(
              color: Colors.black54,
              padding: EdgeInsets.all(16),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    _getDetectionText(),
                    style: TextStyle(color: Colors.white),
                  ),
                  SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      ElevatedButton(
                        onPressed: _analyzeImage,
                        child: Text('Detect'),
                      ),
                      ElevatedButton(
                        onPressed: () {
                          // Navigate to preview screen
                        },
                        child: Text('Save'),
                      ),
                      IconButton(
                        icon: Icon(Icons.photo_library, color: Colors.white),
                        onPressed: () {
                          // Navigate to gallery
                        },
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
  
  String _getDetectionText() {
    if (_detectionResults.isEmpty) {
      return 'Point camera at objects...';
    }
    
    return _detectionResults.map((result) => 
      '${result.label}: ${(result.confidence * 100).toStringAsFixed(1)}%'
    ).join('\n');
  }
  
  @override
  void dispose() {
    _cameraService.dispose();
    _tensorFlowService.dispose();
    super.dispose();
  }
}
```

### 4. Gallery Screen

```dart
// lib/screens/gallery_screen.dart
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:intl/intl.dart';

class GalleryScreen extends StatefulWidget {
  @override
  _GalleryScreenState createState() => _GalleryScreenState();
}

class _GalleryScreenState extends State<GalleryScreen> {
  List<ImageItem> _images = [];
  bool _isLoading = true;
  
  @override
  void initState() {
    super.initState();
    _loadImages();
  }
  
  Future<void> _loadImages() async {
    setState(() {
      _isLoading = true;
    });
    
    try {
      final appDir = await getApplicationDocumentsDirectory();
      final imagesDir = Directory('${appDir.path}/VisionHelper');
      
      if (await imagesDir.exists()) {
        final files = await imagesDir.list().toList();
        final imageFiles = files.whereType<File>()
            .where((file) => file.path.endsWith('.jpg'))
            .toList();
        
        _images = imageFiles.map((file) {
          final name = file.path.split('/').last;
          final regex = RegExp(r'VisionHelper_(.+?)_\d{8}_\d{6}\.jpg');
          final match = regex.firstMatch(name);
          final detection = match?.group(1)?.replaceAll('_', ' ') ?? 'Unknown';
          final date = DateTime.fromMillisecondsSinceEpoch(
              file.statSync().modified.millisecondsSinceEpoch);
          
          return ImageItem(file, detection, date);
        }).toList();
        
        // Sort by date (newest first)
        _images.sort((a, b) => b.date.compareTo(a.date));
      }
    } catch (e) {
      print('Error loading images: $e');
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Saved Detections'),
        actions: [
          IconButton(
            icon: Icon(Icons.refresh),
            onPressed: _loadImages,
          ),
        ],
      ),
      body: _isLoading 
          ? Center(child: CircularProgressIndicator())
          : _images.isEmpty
              ? Center(child: Text('No saved images yet'))
              : GridView.builder(
                  padding: EdgeInsets.all(4),
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 2,
                    childAspectRatio: 1,
                    crossAxisSpacing: 4,
                    mainAxisSpacing: 4,
                  ),
                  itemCount: _images.length,
                  itemBuilder: (context, index) {
                    final item = _images[index];
                    return GalleryItemWidget(
                      item: item,
                      onDelete: () => _deleteImage(index),
                    );
                  },
                ),
    );
  }
  
  Future<void> _deleteImage(int index) async {
    final item = _images[index];
    
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Delete Image'),
        content: Text('Delete image containing ${item.detection}?'),
        actions: [
          TextButton(
            child: Text('Cancel'),
            onPressed: () => Navigator.pop(context, false),
          ),
          TextButton(
            child: Text('Delete'),
            onPressed: () => Navigator.pop(context, true),
          ),
        ],
      ),
    );
    
    if (confirmed == true) {
      try {
        await item.file.delete();
        setState(() {
          _images.removeAt(index);
        });
      } catch (e) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error deleting image: $e')),
        );
      }
    }
  }
}

class ImageItem {
  final File file;
  final String detection;
  final DateTime date;
  
  ImageItem(this.file, this.detection, this.date);
}

class GalleryItemWidget extends StatelessWidget {
  final ImageItem item;
  final VoidCallback onDelete;
  
  GalleryItemWidget({required this.item, required this.onDelete});
  
  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          Image.file(
            item.file,
            fit: BoxFit.cover,
          ),
          Positioned(
            top: 4,
            right: 4,
            child: CircleAvatar(
              backgroundColor: Colors.red.withOpacity(0.7),
              radius: 16,
              child: IconButton(
                icon: Icon(Icons.delete, size: 16, color: Colors.white),
                onPressed: onDelete,
                padding: EdgeInsets.zero,
              ),
            ),
          ),
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: Container(
              color: Colors.black54,
              padding: EdgeInsets.all(8),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.detection,
                    style: TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    DateFormat('MMM d, yyyy').format(item.date),
                    style: TextStyle(color: Colors.white, fontSize: 12),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
```

## Cross-Platform Considerations

### iOS Specific Setup

Add the following to your `ios/Runner/Info.plist` file:

```xml
<key>NSCameraUsageDescription</key>
<string>This app needs camera access to detect objects</string>
<key>NSPhotoLibraryUsageDescription</key>
<string>This app needs photos access to save and view detected objects</string>
<key>NSPhotoLibraryAddUsageDescription</key>
<string>This app needs photos access to save detected objects</string>
```

### Android Specific Setup

Add the following to your `android/app/src/main/AndroidManifest.xml` file:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" 
                 android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" 
                 android:maxSdkVersion="32" />
```

## Testing

1. Test on both Android and iOS devices
2. Verify camera permissions
3. Check ML model accuracy 
4. Ensure images save properly on both platforms
5. Test gallery functionality across platforms

## Deployment

### Android

```bash
flutter build appbundle
```

### iOS

```bash
flutter build ios
```

## Resources

- [Flutter Camera Documentation](https://pub.dev/packages/camera)
- [TFLite Flutter Package](https://pub.dev/packages/tflite_flutter)
- [Flutter Platform Integration](https://flutter.dev/docs/development/platform-integration/platform-channels)
- [Flutter Deployment](https://flutter.dev/docs/deployment/android)

This guide provides a starting point for recreating your VisionHelper app in Flutter. You'll need to implement the full functionality as you develop the app, but this structure should give you a solid foundation for cross-platform development. 