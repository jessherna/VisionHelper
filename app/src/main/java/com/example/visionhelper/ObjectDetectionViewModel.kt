package com.example.visionhelper

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ObjectDetectionViewModel(application: Application) : AndroidViewModel(application) {
    private val application = getApplication<Application>()
    private var imageClassifier: ImageClassifier? = null
    private val labelList = mutableListOf<String>()

    private val _detectionResults = MutableLiveData<List<DetectionResult>>()
    val detectionResults: LiveData<List<DetectionResult>> = _detectionResults

    private val _imageSaved = MutableLiveData<Boolean>()
    val imageSaved: LiveData<Boolean> = _imageSaved

    private var _currentBitmap: Bitmap? = null
    val currentBitmap: Bitmap?
        get() = _currentBitmap

    init {
        loadLabels()
        setupImageClassifier()
    }

    private fun loadLabels() {
        try {
            val labelsInput: InputStream = application.assets.open("labels_mobilenet_quant_v1_224.txt")
            val reader = BufferedReader(InputStreamReader(labelsInput))
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                line?.trim()?.let { 
                    if (it.isNotEmpty()) {
                        labelList.add(it)
                    }
                }
            }
            
            reader.close()
            Log.d(TAG, "Loaded ${labelList.size} labels successfully")
            
            // Log first few labels for debugging
            val sampleLabels = labelList.take(5).joinToString(", ")
            Log.d(TAG, "Sample labels: $sampleLabels")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading labels: ${e.message}")
            
            // Try loading the smaller labels.txt as fallback
            try {
                labelList.clear()
                val fallbackInput: InputStream = application.assets.open("labels.txt")
                val reader = BufferedReader(InputStreamReader(fallbackInput))
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    line?.trim()?.let { 
                        if (it.isNotEmpty()) {
                            labelList.add(it)
                        }
                    }
                }
                
                reader.close()
                Log.d(TAG, "Loaded ${labelList.size} labels from fallback file")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading fallback labels: ${e.message}")
            }
        }
    }

    private fun setupImageClassifier() {
        try {
            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setMaxResults(3)
                .setScoreThreshold(0.3f)
                .build()

            imageClassifier = ImageClassifier.createFromFileAndOptions(
                application,
                "mobilenet_v1_1.0_224_quant.tflite",
                options
            )

            Log.d(TAG, "TensorFlow Lite model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading TensorFlow Lite model: ${e.message}")
        }
    }

    fun analyzeImage(bitmap: Bitmap) {
        _currentBitmap = bitmap

        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Resize bitmap to 224x224 (MobileNet input size)
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

                // Convert to TensorImage
                val tensorImage = TensorImage.fromBitmap(resizedBitmap)

                // Run inference
                val results = imageClassifier?.classify(tensorImage)

                // Process results
                if (!results.isNullOrEmpty() && results[0].categories.isNotEmpty()) {
                    val detectionResults = results[0].categories.map { category ->
                        // The category label might be an index or contain an index
                        val labelText = try {
                            val labelStr = category.label
                            // Check if it's just a number
                            if (labelStr.all { it.isDigit() || it.isWhitespace() }) {
                                val index = labelStr.trim().toInt()
                                if (index >= 0 && index < labelList.size) {
                                    labelList[index]
                                } else {
                                    "Unknown ($labelStr)"
                                }
                            } 
                            // Check if it starts with a number and colon (e.g., "0: tench")
                            else if (labelStr.contains(":")) {
                                val parts = labelStr.split(":")
                                val index = parts[0].trim().toInt()
                                if (index >= 0 && index < labelList.size) {
                                    labelList[index]
                                } else {
                                    parts.getOrElse(1) { labelStr }.trim()
                                }
                            } 
                            // If the model already returns the string label
                            else {
                                labelStr
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing label: ${category.label}, ${e.message}")
                            // If parsing fails, try to use the category display name or original label
                            category.displayName ?: category.label
                        }
                        
                        DetectionResult(
                            label = labelText,
                            confidence = category.score
                        )
                    }

                    withContext(Dispatchers.Main) {
                        _detectionResults.value = detectionResults
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _detectionResults.value = listOf(DetectionResult("No objects detected", 0f))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during image analysis: ${e.message}")
                withContext(Dispatchers.Main) {
                    _detectionResults.value = listOf(DetectionResult("Error: ${e.message}", 0f))
                }
            }
        }
    }

    fun saveRecognizedImage() {
        val bitmap = _currentBitmap ?: return
        val detections = _detectionResults.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Create a copy of the bitmap to save
                val bitmapToSave = bitmap.config?.let { bitmap.copy(it, false) }

                // Generate filename with top detection and timestamp
                val topDetection = if (detections.isNotEmpty()) detections[0].label else "unknown"
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val filename = "VisionHelper_${topDetection}_$timestamp.jpg"

                // Save the image
                val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (bitmapToSave != null) {
                        saveImageMediaStore(bitmapToSave, filename, topDetection)
                    } else {
                        false
                    }
                } else {
                    if (bitmapToSave != null) {
                        saveImageLegacy(bitmapToSave, filename)
                    } else {
                        false
                    }
                }

                withContext(Dispatchers.Main) {
                    _imageSaved.value = saved
                    // Reset after showing notification
                    if (saved) {
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(2000)
                            _imageSaved.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving image: ${e.message}")
                withContext(Dispatchers.Main) {
                    _imageSaved.value = false
                }
            }
        }
    }

    private fun saveImageMediaStore(bitmap: Bitmap, filename: String, description: String): Boolean {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VisionHelper")
            put(MediaStore.Images.Media.DESCRIPTION, "Object detected: $description")
        }

        val contentResolver = application.contentResolver
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        return if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
            Log.d(TAG, "Image saved: $uri")
            true
        } else {
            Log.e(TAG, "Failed to create MediaStore entry")
            false
        }
    }

    private fun saveImageLegacy(bitmap: Bitmap, filename: String): Boolean {
        val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val visionHelperDir = File(imagesDir, "VisionHelper")

        if (!visionHelperDir.exists()) {
            visionHelperDir.mkdirs()
        }

        val imageFile = File(visionHelperDir, filename)
        var outputStream: OutputStream? = null

        return try {
            outputStream = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            Log.d(TAG, "Image saved: ${imageFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image: ${e.message}")
            false
        } finally {
            outputStream?.close()
        }
    }

    override fun onCleared() {
        super.onCleared()
        imageClassifier?.close()
    }

    companion object {
        private const val TAG = "ObjectDetectionVM"
    }
}

data class DetectionResult(val label: String, val confidence: Float)