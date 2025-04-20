# VisionHelper

VisionHelper is an Android application that uses TensorFlow Lite to identify objects in real-time through your device's camera. The app allows users to save the recognized objects along with their detection details and provides a custom gallery for reviewing past detections.

<!-- 
## Screenshots
Add your app screenshots here:
- Main camera view with detection
- Preview dialog
- Gallery view
- etc.

Example:
![Camera View](screenshots/camera_view.jpg)
![Gallery View](screenshots/gallery_view.jpg)
-->

## Features

- **Real-time Object Detection**: Identify multiple objects in your environment using your device's camera
- **Visual Feedback**: Clear bounding box shows the area being analyzed
- **Detection Details**: View confidence levels for each recognized object
- **Image Capture**: Save images with detection metadata for later reference
- **Preview Before Saving**: Review detection results before saving images
- **Custom Gallery**: Browse, view, and manage all your saved detections
- **Delete Functionality**: Remove unwanted images from the gallery
- **User-Friendly Navigation**: Easy-to-use interface with intuitive controls

## Technologies Used

- Kotlin
- Android Jetpack components
- CameraX
- TensorFlow Lite
- MobileNet pre-trained model
- Material Design components

## Requirements

- Android Studio Arctic Fox or newer
- Android SDK 24+
- Android device with camera (or emulator with virtual camera)
- Android 7.0 (Nougat) or higher

## Installation

1. Clone this repository:
   ```
   git clone https://github.com/yourusername/visionhelper.git
   ```

2. Open the project in Android Studio.

3. Connect your Android device or start an emulator.

4. Build and run the app using Android Studio's "Run" button.

## Usage

### Object Detection

1. Launch the app and grant camera permissions when prompted.
2. Point your camera at objects you want to identify.
3. The app will display detection results in real-time, showing the object name and confidence level.
4. The green detection box helps you focus on the area being analyzed.

### Saving Images

1. When you've captured something you want to save, tap the "Save Recognition" button.
2. A preview dialog will appear showing the image and detection details.
3. Choose to either save the image or retake.
4. Saved images are stored in your gallery with detection information embedded in the filename.

### Using the Gallery

1. Tap the gallery icon in the top right corner to view your saved detections.
2. Browse through your saved images, which display the detected object and capture date.
3. Tap any image to view it in full screen.
4. To delete an image, tap the red delete button in the top-right corner of the image.
5. Use the "Return to camera" button at the bottom to go back to the camera view.

## Project Structure

- **MainActivity**: Handles camera functionality and real-time object detection
- **ObjectDetectionViewModel**: Manages TensorFlow Lite model and detection processing
- **CapturePreviewDialog**: Displays image preview with detection details before saving
- **GalleryActivity**: Custom gallery implementation for browsing saved detections

## How It Works

VisionHelper uses a MobileNet TensorFlow Lite model to classify objects in camera frames. The app:

1. Processes camera frames in real-time
2. Feeds the frames to the TensorFlow model
3. Interprets the model's output to determine object classes and confidence scores
4. Displays the results to the user with visual feedback
5. Allows saving images with metadata for later reference

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- TensorFlow team for the MobileNet model
- Android developers at Google for CameraX and other libraries
- The open-source community for various libraries and tools used in this project

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Support

If you encounter any issues or have questions, please file an issue on the GitHub repository. 