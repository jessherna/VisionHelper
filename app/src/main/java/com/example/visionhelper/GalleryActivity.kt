package com.example.visionhelper

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: GalleryAdapter
    private val imageList = mutableListOf<ImageItem>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        
        // Set up toolbar
        setSupportActionBar(findViewById(R.id.galleryToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.gallery_title)
        
        // Set up UI elements
        recyclerView = findViewById(R.id.galleryRecyclerView)
        emptyView = findViewById(R.id.emptyGalleryText)
        
        // Load images
        loadImages()
        
        // Set up RecyclerView
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = GalleryAdapter(this, imageList, 
            onDeleteClick = { position -> 
                confirmDelete(position)
            },
            onItemClick = { uri ->
                openImageViewer(uri)
            }
        )
        recyclerView.adapter = adapter
        
        // Show/hide empty view
        updateEmptyState()
        
        // Set up footer home button
        findViewById<View>(R.id.homeButton).setOnClickListener {
            finish()
        }
    }
    
    private fun openImageViewer(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "image/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }
    
    private fun confirmDelete(position: Int) {
        val item = imageList[position]
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_image))
            .setMessage(getString(R.string.delete_image_confirmation, item.detection))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteImage(position)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun deleteImage(position: Int) {
        val item = imageList[position]
        var deleted = false
        
        try {
            // For Android 10+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val uri = item.uri
                if (uri.toString().startsWith("content://")) {
                    contentResolver.delete(uri, null, null)
                    deleted = true
                }
            }
            
            // For older Android versions or file:// URIs
            if (!deleted && item.uri.scheme == "file") {
                val file = File(item.uri.path!!)
                if (file.exists()) {
                    deleted = file.delete()
                }
            }
            
            if (deleted) {
                imageList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, imageList.size - position)
                updateEmptyState()
                Toast.makeText(this, R.string.image_deleted, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.delete_error, e.message), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateEmptyState() {
        if (imageList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.gallery_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_refresh -> {
                loadImages()
                adapter.notifyDataSetChanged()
                updateEmptyState()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun loadImages() {
        // Clear existing items
        imageList.clear()
        
        // Find images from MediaStore (for Android 10+)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("VisionHelper_%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val dateSeconds = cursor.getLong(dateColumn)
                val date = Date(dateSeconds * 1000)
                
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                
                // Parse detection from filename
                var detection = "Unknown"
                val regex = "VisionHelper_(.+?)_\\d{8}_\\d{6}\\.jpg".toRegex()
                val matchResult = regex.find(name)
                if (matchResult != null) {
                    detection = matchResult.groupValues[1].replace("_", " ")
                }
                
                imageList.add(ImageItem(contentUri, detection, date))
            }
        }
        
        // For older Android versions, also look in the VisionHelper directory
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val visionHelperDir = File(imagesDir, "VisionHelper")
            
            if (visionHelperDir.exists() && visionHelperDir.isDirectory) {
                visionHelperDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.startsWith("VisionHelper_") && file.name.endsWith(".jpg")) {
                        val uri = Uri.fromFile(file)
                        val date = Date(file.lastModified())
                        
                        // Parse detection from filename
                        var detection = "Unknown"
                        val regex = "VisionHelper_(.+?)_\\d{8}_\\d{6}\\.jpg".toRegex()
                        val matchResult = regex.find(file.name)
                        if (matchResult != null) {
                            detection = matchResult.groupValues[1].replace("_", " ")
                        }
                        
                        // Add if not already in list
                        if (imageList.none { it.uri.path == uri.path }) {
                            imageList.add(ImageItem(uri, detection, date))
                        }
                    }
                }
            }
        }
        
        // Sort by date (newest first)
        imageList.sortByDescending { it.date }
    }
    
    class GalleryAdapter(
        private val context: Context,
        private val imageList: List<ImageItem>,
        private val onDeleteClick: (Int) -> Unit,
        private val onItemClick: (Uri) -> Unit
    ) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.gallery_item, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = imageList[position]
            
            // Load image
            holder.imageView.setImageURI(item.uri)
            
            // Set detection text
            val formattedDetection = item.detection.replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) 
                else it.toString() 
            }
            holder.detectionTextView.text = formattedDetection
            
            // Set date text
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            holder.dateTextView.text = dateFormat.format(item.date)
            
            // Set click listener for the whole item
            holder.itemView.setOnClickListener {
                onItemClick(item.uri)
            }
            
            // Set click listener for delete button
            holder.deleteButton.setOnClickListener {
                onDeleteClick(holder.adapterPosition)
            }
        }
        
        override fun getItemCount(): Int = imageList.size
        
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.galleryImageView)
            val detectionTextView: TextView = view.findViewById(R.id.detectionTextView)
            val dateTextView: TextView = view.findViewById(R.id.dateTextView)
            val deleteButton: ImageButton = view.findViewById(R.id.deleteImageButton)
        }
    }
    
    data class ImageItem(
        val uri: Uri,
        val detection: String,
        val date: Date
    )
    
    companion object {
        fun start(context: Context) {
            val intent = Intent(context, GalleryActivity::class.java)
            context.startActivity(intent)
        }
    }
} 