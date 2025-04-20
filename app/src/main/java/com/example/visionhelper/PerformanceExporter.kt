package com.example.visionhelper

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class to export performance metrics to a file that can be shared
 */
class PerformanceExporter(private val context: Context) {

    /**
     * Exports performance metrics to a text file in the Documents directory
     * @param metrics The performance metrics to export
     * @param additionalNotes Any additional notes to include in the report
     * @return The absolute path to the created file, or null if export failed
     */
    suspend fun exportMetricsToFile(
        metrics: ObjectDetectionViewModel.PerformanceMetrics,
        additionalNotes: String = ""
    ): String? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "VisionHelper_Performance_${timestamp}.txt"
            val content = buildReportContent(metrics, additionalNotes)

            return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(filename, content)
            } else {
                saveToExternalStorage(filename, content)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Generates the performance report content without saving it to a file
     * @param metrics The performance metrics to include in the report
     * @param additionalNotes Any additional notes to include in the report
     * @return The generated report content as a string
     */
    fun generateReportContent(
        metrics: ObjectDetectionViewModel.PerformanceMetrics,
        additionalNotes: String = ""
    ): String {
        return buildReportContent(metrics, additionalNotes)
    }

    private fun buildReportContent(
        metrics: ObjectDetectionViewModel.PerformanceMetrics,
        additionalNotes: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine("======================================")
        sb.appendLine("       VISIONHELPER PERFORMANCE REPORT")
        sb.appendLine("======================================")
        sb.appendLine()
        sb.appendLine("DEVICE INFORMATION")
        sb.appendLine("--------------------------------------")
        sb.appendLine("Device: ${metrics.deviceInfo}")
        sb.appendLine("OS: ${metrics.androidVersion}")
        sb.appendLine()
        sb.appendLine("MODEL INFORMATION")
        sb.appendLine("--------------------------------------")
        sb.appendLine("Model: ${metrics.modelName}")
        sb.appendLine()
        sb.appendLine("PERFORMANCE METRICS")
        sb.appendLine("--------------------------------------")
        sb.appendLine("Average Inference Time: ${metrics.averageInferenceTimeMs} ms")
        sb.appendLine("Frames Per Second: ${String.format("%.1f", metrics.framesPerSecond)} FPS")
        sb.appendLine()
        
        if (additionalNotes.isNotEmpty()) {
            sb.appendLine("ADDITIONAL NOTES")
            sb.appendLine("--------------------------------------")
            sb.appendLine(additionalNotes)
            sb.appendLine()
        }
        
        sb.appendLine("Report generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("======================================")
        
        return sb.toString()
    }

    private fun saveToMediaStore(filename: String, content: String): String? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
        }

        val uri = context.contentResolver.insert(
            MediaStore.Files.getContentUri("external"),
            contentValues
        ) ?: return null

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(content.toByteArray())
        }

        return uri.toString()
    }

    private fun saveToExternalStorage(filename: String, content: String): String? {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val reportDir = File(documentsDir, "VisionHelper")
        if (!reportDir.exists()) {
            reportDir.mkdirs()
        }

        val file = File(reportDir, filename)
        FileOutputStream(file).use { outputStream ->
            outputStream.write(content.toByteArray())
        }

        return file.absolutePath
    }
} 