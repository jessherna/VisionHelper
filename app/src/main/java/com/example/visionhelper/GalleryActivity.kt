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
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
    
    private var isSelectionMode = false
    private var menu: Menu? = null
    
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
                if (isSelectionMode) {
                    toggleItemSelection(adapter.getPosition(uri))
                } else {
                    openImageViewer(uri)
                }
            },
            onItemLongClick = { position ->
                if (!isSelectionMode) {
                    enableSelectionMode()
                    toggleItemSelection(position)
                    true
                } else {
                    false
                }
            },
            onSelectionChanged = { numSelected ->
                updateSelectionUI(numSelected)
            }
        )
        recyclerView.adapter = adapter
        
        // Show/hide empty view
        updateEmptyState()
        
        // Handle back button presses
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionMode) {
                    disableSelectionMode()
                } else {
                    finish()
                }
            }
        })
    }
    
    private fun updateSelectionUI(numSelected: Int) {
        // Update action bar title to show selection count
        supportActionBar?.title = if (numSelected > 0) {
            getString(R.string.items_selected, numSelected)
        } else {
            getString(R.string.gallery_title)
        }
        
        // Update menu items
        menu?.findItem(R.id.action_delete_selected)?.isVisible = numSelected > 0
    }
    
    private fun enableSelectionMode() {
        isSelectionMode = true
        adapter.setSelectionMode(true)
        
        // Show selection menu items
        menu?.findItem(R.id.action_select)?.isVisible = false
        menu?.findItem(R.id.action_delete_selected)?.isVisible = true
        menu?.findItem(R.id.action_select_all)?.isVisible = true
        menu?.findItem(R.id.action_deselect_all)?.isVisible = true
        menu?.findItem(R.id.action_refresh)?.isVisible = false
        
        // Change title to indicate selection mode
        supportActionBar?.title = getString(R.string.selection_mode)
    }
    
    private fun disableSelectionMode() {
        isSelectionMode = false
        adapter.setSelectionMode(false)
        
        // Hide selection menu items
        menu?.findItem(R.id.action_select)?.isVisible = true
        menu?.findItem(R.id.action_delete_selected)?.isVisible = false
        menu?.findItem(R.id.action_select_all)?.isVisible = false
        menu?.findItem(R.id.action_deselect_all)?.isVisible = false
        menu?.findItem(R.id.action_refresh)?.isVisible = true
        
        // Reset title
        supportActionBar?.title = getString(R.string.gallery_title)
    }
    
    private fun toggleItemSelection(position: Int) {
        if (position != RecyclerView.NO_POSITION) {
            adapter.toggleSelection(position)
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
    
    private fun confirmDeleteSelected() {
        val selectedCount = adapter.getSelectedCount()
        if (selectedCount == 0) return
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_selected))
            .setMessage(getString(R.string.delete_selected_confirmation, selectedCount))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteSelectedImages()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun deleteSelectedImages() {
        val selectedPositions = adapter.getSelectedItems()
        
        // Sort positions in descending order to avoid index shifting during removal
        val sortedPositions = selectedPositions.sortedDescending()
        
        var deletedCount = 0
        var failedCount = 0
        
        for (position in sortedPositions) {
            if (position >= 0 && position < imageList.size) {
                val item = imageList[position]
                
                val deleted = deleteImageFile(item.uri)
                
                if (deleted) {
                    imageList.removeAt(position)
                    deletedCount++
                } else {
                    failedCount++
                }
            }
        }
        
        // Update the adapter and UI
        adapter.clearSelections()
        adapter.notifyDataSetChanged()
        updateEmptyState()
        disableSelectionMode()
        
        // Show result message
        when {
            deletedCount > 0 && failedCount == 0 -> 
                Toast.makeText(this, "$deletedCount items deleted", Toast.LENGTH_SHORT).show()
            deletedCount > 0 && failedCount > 0 -> 
                Toast.makeText(this, "$deletedCount deleted, $failedCount failed", Toast.LENGTH_SHORT).show()
            else -> 
                Toast.makeText(this, "Failed to delete items", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun deleteImage(position: Int) {
        val item = imageList[position]
        
        val deleted = deleteImageFile(item.uri)
        
        if (deleted) {
            imageList.removeAt(position)
            adapter.notifyItemRemoved(position)
            adapter.notifyItemRangeChanged(position, imageList.size - position)
            updateEmptyState()
            Toast.makeText(this, R.string.image_deleted, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun deleteImageFile(uri: Uri): Boolean {
        return try {
            var deleted = false
            
            // For Android 10+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                if (uri.toString().startsWith("content://")) {
                    contentResolver.delete(uri, null, null)
                    deleted = true
                }
            }
            
            // For older Android versions or file:// URIs
            if (!deleted && uri.scheme == "file") {
                val file = File(uri.path!!)
                if (file.exists()) {
                    deleted = file.delete()
                }
            }
            
            deleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
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
        this.menu = menu
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (isSelectionMode) {
                    disableSelectionMode()
                } else {
                    finish()
                }
                true
            }
            R.id.action_select -> {
                enableSelectionMode()
                true
            }
            R.id.action_delete_selected -> {
                confirmDeleteSelected()
                true
            }
            R.id.action_select_all -> {
                adapter.selectAll()
                true
            }
            R.id.action_deselect_all -> {
                adapter.clearSelections()
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
        if (isSelectionMode) {
            disableSelectionMode()
            return true
        }
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
        private val onItemClick: (Uri) -> Unit,
        private val onItemLongClick: (Int) -> Boolean,
        private val onSelectionChanged: (Int) -> Unit
    ) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {
        
        private var selectionMode = false
        private val selectedItems = HashSet<Int>()
        
        fun setSelectionMode(enabled: Boolean) {
            if (selectionMode != enabled) {
                selectionMode = enabled
                if (!enabled) {
                    selectedItems.clear()
                }
                notifyDataSetChanged()
            }
        }
        
        fun toggleSelection(position: Int) {
            if (selectedItems.contains(position)) {
                selectedItems.remove(position)
            } else {
                selectedItems.add(position)
            }
            notifyItemChanged(position)
            onSelectionChanged(selectedItems.size)
        }
        
        fun selectAll() {
            selectedItems.clear()
            for (i in 0 until itemCount) {
                selectedItems.add(i)
            }
            notifyDataSetChanged()
            onSelectionChanged(selectedItems.size)
        }
        
        fun clearSelections() {
            selectedItems.clear()
            notifyDataSetChanged()
            onSelectionChanged(0)
        }
        
        fun getSelectedItems(): List<Int> {
            return selectedItems.toList()
        }
        
        fun getSelectedCount(): Int {
            return selectedItems.size
        }
        
        fun getPosition(uri: Uri): Int {
            return imageList.indexOfFirst { it.uri == uri }
        }
        
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
            
            // Update selection state
            val isSelected = selectedItems.contains(position)
            holder.itemView.setBackgroundColor(if (isSelected) Color.parseColor("#1A00B0FF") else Color.TRANSPARENT)
            
            // Show/hide checkbox based on selection mode
            holder.selectCheckBox.visibility = if (selectionMode) View.VISIBLE else View.GONE
            holder.selectCheckBox.isChecked = isSelected
            holder.deleteButton.visibility = if (selectionMode) View.GONE else View.VISIBLE
            
            // Set click listener for the whole item
            holder.itemView.setOnClickListener {
                onItemClick(item.uri)
            }
            
            // Set long click listener for the whole item
            holder.itemView.setOnLongClickListener {
                onItemLongClick(holder.adapterPosition)
            }
            
            // Set click listener for delete button
            holder.deleteButton.setOnClickListener {
                onDeleteClick(holder.adapterPosition)
            }
            
            // Set click listener for checkbox
            holder.selectCheckBox.setOnClickListener {
                toggleSelection(holder.adapterPosition)
            }
        }
        
        override fun getItemCount(): Int = imageList.size
        
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.galleryImageView)
            val detectionTextView: TextView = view.findViewById(R.id.detectionTextView)
            val dateTextView: TextView = view.findViewById(R.id.dateTextView)
            val deleteButton: ImageButton = view.findViewById(R.id.deleteImageButton)
            val selectCheckBox: CheckBox = view.findViewById(R.id.selectCheckBox)
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