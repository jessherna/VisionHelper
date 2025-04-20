package com.example.visionhelper

import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.TextView

/**
 * Dialog to display performance report within the app
 */
class PerformanceReportDialog(
    context: Context,
    private val reportContent: String,
    private val onShareClick: (String) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_performance_report)
        
        // Make dialog take most of the screen
        window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Set up content text view
        val reportContentText: TextView = findViewById(R.id.reportContentText)
        reportContentText.text = reportContent
        
        // Set up buttons
        val shareButton: Button = findViewById(R.id.shareButton)
        shareButton.setOnClickListener {
            onShareClick(reportContent)
        }
        
        val dismissButton: Button = findViewById(R.id.dismissButton)
        dismissButton.setOnClickListener {
            dismiss()
        }
    }
} 