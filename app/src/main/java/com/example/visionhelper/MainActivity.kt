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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.core.content.FileProvider

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: ObjectDetectionViewModel
    private lateinit var previewView: PreviewView
    private lateinit var resultTextView: TextView
    private lateinit var captureButton: Button
    private lateinit var savedNotification: TextView
    private lateinit var galleryButton: ImageButton
    private lateinit var detectionBoxView: View
    private lateinit var boxLabelText: TextView
    
    // Performance metrics UI
    private lateinit var metricsToggleButton: ImageButton
    private lateinit var metricsPanel: View
    private lateinit var inferenceTimeText: TextView
    private lateinit var fpsText: TextView
    private lateinit var deviceInfoText: TextView
    private lateinit var modelInfoText: TextView
    private lateinit var exportMetricsButton: Button
    
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var boxAnimator: ValueAnimator? = null
    
    // Flag to track metrics visibility
    private var metricsVisible = false
    
    private lateinit var performanceExporter: PerformanceExporter
    
    // Add this property at the class level with the other properties
    private val appStartTime = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[ObjectDetectionViewModel::class.java]
        
        // Initialize performance exporter
        performanceExporter = PerformanceExporter(this)

        // Initialize views
        previewView = findViewById(R.id.previewView)
        resultTextView = findViewById(R.id.resultTextView)
        captureButton = findViewById(R.id.captureButton)
        savedNotification = findViewById(R.id.savedNotification)
        galleryButton = findViewById(R.id.galleryButton)
        detectionBoxView = findViewById(R.id.detectionBoxView)
        boxLabelText = findViewById(R.id.boxLabelText)
        
        // Initialize performance metrics UI
        metricsToggleButton = findViewById(R.id.metricsToggleButton)
        metricsPanel = findViewById(R.id.metricsPanel)
        inferenceTimeText = findViewById(R.id.inferenceTimeText)
        fpsText = findViewById(R.id.fpsText)
        deviceInfoText = findViewById(R.id.deviceInfoText)
        modelInfoText = findViewById(R.id.modelInfoText)
        exportMetricsButton = findViewById(R.id.exportMetricsButton)
        
        // Initially hide metrics panel
        metricsPanel.visibility = View.GONE

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
        
        // Set up metrics toggle button
        metricsToggleButton.setOnClickListener {
            toggleMetricsVisibility()
        }
        
        // Set up export metrics button
        exportMetricsButton.setOnClickListener {
            exportPerformanceReport()
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
        
        // Observe performance metrics
        observePerformanceMetrics()
        
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

    private fun observePerformanceMetrics() {
        // Observe inference time
        viewModel.inferenceTime.observe(this) { timeMs ->
            inferenceTimeText.text = "Inference: ${timeMs}ms"
        }
        
        // Observe FPS
        viewModel.framesPerSecond.observe(this) { fps ->
            fpsText.text = "FPS: ${String.format("%.1f", fps)}"
        }
        
        // Set device and model info
        val metrics = viewModel.getPerformanceReport()
        deviceInfoText.text = "Device: ${metrics.deviceInfo}"
        modelInfoText.text = "Model: ${metrics.modelName}"
    }
    
    private fun toggleMetricsVisibility() {
        metricsVisible = !metricsVisible
        metricsPanel.visibility = if (metricsVisible) View.VISIBLE else View.GONE
        
        // Update toggle button icon
        metricsToggleButton.setImageResource(
            if (metricsVisible) R.drawable.ic_metrics_on else R.drawable.ic_metrics_off
        )
    }

    private fun exportPerformanceReport() {
        // Get the latest metrics
        val metrics = viewModel.getPerformanceReport()
        
        // Create additional notes with test conditions
        val additionalNotes = "Test conditions:\n" +
                "- Lighting: Indoor artificial lighting\n" +
                "- Number of objects in frame: Varied (1-3)\n" +
                "- Distance to objects: 0.5-1.5 meters\n" +
                "- Sample duration: ${getDurationString()}"
        
        // Generate the report content
        val reportContent = performanceExporter.generateReportContent(metrics, additionalNotes)
        
        // Show the report in a dialog
        val dialog = PerformanceReportDialog(
            this,
            reportContent,
            onShareClick = { content ->
                // Save and share the report when the share button is clicked
                saveAndShareReport(metrics, additionalNotes)
            }
        )
        dialog.show()
    }
    
    private fun saveAndShareReport(metrics: ObjectDetectionViewModel.PerformanceMetrics, additionalNotes: String) {
        // Launch coroutine to export the report
        CoroutineScope(Dispatchers.Main).launch {
            val filePath = performanceExporter.exportMetricsToFile(metrics, additionalNotes)
            
            if (filePath != null) {
                Toast.makeText(
                    this@MainActivity,
                    "Performance report saved",
                    Toast.LENGTH_SHORT
                ).show()
                
                // If the filePath is a content URI, we can offer to share it
                if (filePath.startsWith("content://")) {
                    offerToShareReport(Uri.parse(filePath))
                } else {
                    // For file paths on older Android versions
                    val file = java.io.File(filePath)
                    val fileUri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${applicationContext.packageName}.fileprovider",
                        file
                    )
                    offerToShareReport(fileUri)
                }
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Failed to save performance report",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun offerToShareReport(fileUri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "VisionHelper Performance Report")
            putExtra(Intent.EXTRA_TEXT, "Attached is a performance report from the VisionHelper app.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        startActivity(Intent.createChooser(intent, "Share Performance Report"))
    }
    
    private fun getDurationString(): String {
        // Calculate time since app was started
        val uptime = (System.currentTimeMillis() - appStartTime) / 1000
        if (uptime < 60) {
            return "$uptime seconds"
        }
        val minutes = uptime / 60
        val seconds = uptime % 60
        return "$minutes minutes, $seconds seconds"
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