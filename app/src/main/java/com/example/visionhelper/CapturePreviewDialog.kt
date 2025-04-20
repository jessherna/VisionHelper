package com.example.visionhelper

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CapturePreviewDialog(
    context: Context,
    private val bitmap: Bitmap,
    private val detectionResults: List<DetectionResult>,
    private val onSaveClick: () -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_capture_preview)
        
        // Set dialog to match parent width
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        
        // Set up UI elements
        val captureImageView = findViewById<ImageView>(R.id.captureImageView)
        val detailsRecyclerView = findViewById<RecyclerView>(R.id.detailsRecyclerView)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val retakeButton = findViewById<Button>(R.id.retakeButton)
        
        // Set image
        captureImageView.setImageBitmap(bitmap)
        
        // Set up RecyclerView for detection details
        detailsRecyclerView.layoutManager = LinearLayoutManager(context)
        detailsRecyclerView.adapter = DetailsAdapter(detectionResults)
        
        // Hide RecyclerView if no results
        if (detectionResults.isEmpty() || (detectionResults.size == 1 && detectionResults[0].label.startsWith("No"))) {
            findViewById<TextView>(R.id.detectionDetailsTitle).visibility = View.GONE
            detailsRecyclerView.visibility = View.GONE
        }
        
        // Set button click listeners
        saveButton.setOnClickListener {
            onSaveClick()
            dismiss()
        }
        
        retakeButton.setOnClickListener {
            dismiss()
        }
    }
    
    class DetailsAdapter(
        private val detectionResults: List<DetectionResult>
    ) : RecyclerView.Adapter<DetailsAdapter.ViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = View.inflate(parent.context, R.layout.detection_detail_item, null)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val result = detectionResults[position]
            
            // Skip background class
            if (result.label.equals("background", ignoreCase = true)) {
                holder.itemView.visibility = View.GONE
                return
            }
            
            holder.itemView.visibility = View.VISIBLE
            
            // Format label with first letter capitalized
            val formattedLabel = result.label
                .split(" ")
                .joinToString(" ") { word ->
                    word.replaceFirstChar { 
                        if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) 
                        else it.toString() 
                    }
                }
            
            holder.labelTextView.text = formattedLabel
            
            // Format confidence as percentage and set progress bar
            val confidenceValue = result.confidence * 100
            val confidence = String.format("%.1f", confidenceValue)
            holder.confidenceTextView.text = "$confidence%"
            
            // Update progress bar with confidence value (0-100)
            holder.confidenceProgressBar.progress = confidenceValue.toInt()
        }
        
        override fun getItemCount(): Int = detectionResults.size
        
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val labelTextView: TextView = view.findViewById(R.id.detailLabelText)
            val confidenceTextView: TextView = view.findViewById(R.id.detailConfidenceText)
            val confidenceProgressBar: ProgressBar = view.findViewById(R.id.confidenceProgressBar)
        }
    }
} 