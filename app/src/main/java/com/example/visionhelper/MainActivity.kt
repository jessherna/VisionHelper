package com.example.visionhelper

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.ImageButton
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: ObjectDetectionViewModel
    private lateinit var previewView: PreviewView
    private lateinit var resultTextView: TextView
    private lateinit var captureButton: Button
    private lateinit var savedNotification: TextView
    private lateinit var galleryButton: ImageButton
    private lateinit var detectionBoxView: View
    private lateinit var boxLabelText: TextView

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var boxAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[ObjectDetectionViewModel::class.java]

        // Initialize views
        previewView = findViewById(R.id.previewView)
        resultTextView = findViewById(R.id.resultTextView)
        captureButton = findViewById(R.id.captureButton)
        savedNotification = findViewById(R.id.savedNotification)
        galleryButton = findViewById(R.id.galleryButton)
        detectionBoxView = findViewById(R.id.detectionBoxView)
        boxLabelText = findViewById(R.id.boxLabelText)

        // Set up button click listeners
        captureButton.setOnClickListener {
            // Get current bitmap and detection results
            val currentBitmap = previewView.bitmap
            val currentResults = viewModel.detectionResults.value
            
            if (currentBitmap != null && currentResults != null) {
                // Show capture preview dialog
                val dialog = CapturePreviewDialog(
                    this,
                    currentBitmap,
                    currentResults,
                    onSaveClick = {
                        viewModel.saveRecognizedImage()
                    }
                )
                dialog.show()
            } else {
                // Fallback if no preview/results available
                viewModel.saveRecognizedImage()
            }
        }
        
        galleryButton.setOnClickListener {
            GalleryActivity.start(this)
        }

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Set up box animation
        setupBoxAnimation()

        // Observe detection results
        viewModel.detectionResults.observe(this) { results ->
            updateDetectionResults(results)
            updateBoxLabel(results)
        }

        // Observe image saved status
        viewModel.imageSaved.observe(this) { saved ->
            if (saved) {
                savedNotification.visibility = View.VISIBLE
                savedNotification.postDelayed({
                    savedNotification.visibility = View.GONE
                }, 2000)
            }
        }
        
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestRequiredPermissions()
        }
    }

    private fun setupBoxAnimation() {
        boxAnimator = ValueAnimator.ofFloat(1.0f, 1.05f).apply {
            duration = 1000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                detectionBoxView.scaleX = value
                detectionBoxView.scaleY = value
            }
        }
        boxAnimator?.start()
    }

    private fun updateBoxLabel(results: List<DetectionResult>) {
        if (results.isEmpty() || (results.size == 1 && results[0].label.startsWith("No objects"))) {
            boxLabelText.text = getString(R.string.analyzing)
        } else {
            // Find the first non-background detection with highest confidence
            val topResult = results.firstOrNull { 
                !it.label.equals("background", ignoreCase = true) 
            }
            
            if (topResult != null) {
                val confidence = String.format("%.0f", topResult.confidence * 100)
                
                // Format the label
                val formattedLabel = topResult.label
                    .split(" ")
                    .joinToString(" ") { word -> 
                        word.replaceFirstChar { 
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) 
                            else it.toString() 
                        }
                    }
                
                boxLabelText.text = "$formattedLabel: $confidence%"
            } else {
                boxLabelText.text = getString(R.string.analyzing)
            }
        }
    }

    private fun openGallery() {
        // Launch our own GalleryActivity instead of the system gallery
        GalleryActivity.start(this)
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, permissionsToRequest, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun updateDetectionResults(results: List<DetectionResult>) {
        val resultBuilder = StringBuilder()

        if (results.isEmpty() || (results.size == 1 && results[0].label.startsWith("No objects"))) {
            resultBuilder.append("No objects detected")
        } else {
            resultBuilder.append("Detected:\n")
            results.forEach { result ->
                // Skip "background" class which is usually the first class (index 0)
                if (result.label.equals("background", ignoreCase = true)) {
                    return@forEach
                }
                
                // Format the confidence percentage
                val confidence = String.format("%.1f", result.confidence * 100)
                
                // Capitalize the first letter of each word in the label
                val formattedLabel = result.label
                    .split(" ")
                    .joinToString(" ") { word -> 
                        word.replaceFirstChar { 
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) 
                            else it.toString() 
                        }
                    }
                
                resultBuilder.append("• $formattedLabel: $confidence%\n")
            }
            
            // If we only ended up with the header after filtering
            if (resultBuilder.toString() == "Detected:\n") {
                resultBuilder.clear()
                resultBuilder.append("No significant objects detected")
            }
        }

        resultTextView.text = resultBuilder.toString()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Set up Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Set up ImageCapture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // Set up ImageAnalysis for object detection
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind any bound use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )

            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        // Run getBitmap on the main thread to avoid "Not in application's main thread" error
        mainHandler.post {
            try {
                previewView.bitmap?.let { bitmap ->
                    viewModel.analyzeImage(bitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image", e)
            }
        }
        
        // Always close the ImageProxy
        imageProxy.close()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startCamera()
            } else {
                // Show a more informative message
                Toast.makeText(
                    this,
                    "Camera permissions are required for this app to function.",
                    Toast.LENGTH_LONG
                ).show()
                
                // Give the user another chance to grant permissions instead of just finishing
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                    requestRequiredPermissions()
                } else {
                    Toast.makeText(
                        this,
                        "Please enable camera permissions in app settings.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        boxAnimator?.cancel()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            arrayOf(
                Manifest.permission.CAMERA, 
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(Manifest.permission.CAMERA)
        }
    }
}