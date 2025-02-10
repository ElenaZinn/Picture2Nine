package com.example.cutthepicture
import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_IMAGE_PICK = 1001
        private const val REQUEST_STORAGE_PERMISSION = 1002
    }

    // 控件声明
    private lateinit var ivPreview: ImageView
    private lateinit var uploadContainer: FrameLayout
    private var selectedBitmap: Bitmap? = null
    private lateinit var hintText: TextView

    private var containerSize = 0
    private var previewGrid: GridLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化视图
        ivPreview = findViewById(R.id.iv_preview)
        uploadContainer = findViewById(R.id.upload_container)
        hintText = findViewById(R.id.tv_upload_hint)

        // 设置上传区域点击监听
        uploadContainer.setOnClickListener {
            openImagePicker()
        }

        uploadContainer.post {
            // 获取实际容器尺寸
            containerSize = uploadContainer.measuredWidth
            Toast.makeText(this, "容器尺寸: ${containerSize}px", Toast.LENGTH_SHORT).show()
        }
    }

    // 打开图片选择器
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }

    // 处理图片选择结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                loadSelectedImage(uri)
            }
        }

    }

    private fun isValidImageUri(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    // 加载选中图片
    private fun loadSelectedImage(uri: Uri) {
        hintText.text = "点击方框切换图片"

        clearPreviewGrid()

        // 隐藏十字线
        hideGuidelines()

        // 解码原始图片（需要处理大图情况）
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                selectedBitmap = BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show()
        }


        uploadContainer.post {
            Glide.with(this)
                .load(uri)
                .override(containerSize, containerSize) // 强制指定尺寸
                .centerCrop()
                .into(object : CustomTarget<Drawable>() {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        ivPreview.setImageDrawable(resource)
                        ivPreview.visibility = View.VISIBLE

                        // 获取实际显示的Bitmap
                        val bitmap = (resource as BitmapDrawable).bitmap
                        selectedBitmap = Bitmap.createScaledBitmap(
                            bitmap,
                            containerSize,
                            containerSize,
                            true
                        )
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        ivPreview.setImageDrawable(null)
                    }
                })
        }
    }

    // 创建菜单
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    // 处理菜单点击事件
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_download -> {
                checkStoragePermission()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // 检查存储权限
    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用Scoped Storage
            saveSplitImages()
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_STORAGE_PERMISSION
                )
            } else {
                saveSplitImages()
            }
        }
    }

    // 处理权限请求结果
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveSplitImages()
            } else {
                Toast.makeText(this, "需要存储权限保存图片", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 分割并保存图片
    private fun saveSplitImages() {
        selectedBitmap?.let { originalBitmap ->
            try {
                // 确保已经是正确尺寸
                if (originalBitmap.width != containerSize || originalBitmap.height != containerSize) {
                    selectedBitmap = Bitmap.createScaledBitmap(
                        originalBitmap,
                        containerSize,
                        containerSize,
                        true
                    )
                }

                // 确保是正方形
                val size = originalBitmap.width.coerceAtMost(originalBitmap.height)
                val squareBitmap = Bitmap.createBitmap(
                    originalBitmap,
                    (originalBitmap.width - size) / 2,
                    (originalBitmap.height - size) / 2,
                    size,
                    size
                )

                // 分割为3x3网格
                val chunkSize = size / 3
                var count = 0

                for (y in 0..2) {
                    for (x in 0..2) {
                        val chunk = Bitmap.createBitmap(
                            squareBitmap,
                            x * chunkSize,
                            y * chunkSize,
                            chunkSize,
                            chunkSize
                        )
                        saveImageToGallery(chunk, "piece_${System.currentTimeMillis()}_$count")
                        count++
                    }
                }
                Toast.makeText(this, "9张图片已保存到相册", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
        }
    }

    // 保存到系统相册
    private fun saveImageToGallery(bitmap: Bitmap, displayName: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ImageSplitter")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )

        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(it, contentValues, null, null)
            }
            bitmap.recycle()
        }
    }


    // 分割按钮点击事件
    fun onSplitClick(view: View) {

        saveSplitImages()
        showGridPreview()
    }

    private fun showGridPreview() {
        clearPreviewGrid()

        // 创建新网格容器
        previewGrid = GridLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                uploadContainer.width,
                uploadContainer.height
            )
            columnCount = 3
            rowCount = 3
        }

        // 确保容器存在再添加视图
        if (previewGrid?.parent == null) {
            uploadContainer.addView(previewGrid)
        }


        // 添加分割线
        addGridDividers()

        // 填充预览图片
        selectedBitmap?.let { bitmap ->
            val chunkSize = bitmap.width / 3
            for (i in 0..2) {
                for (j in 0..2) {
                    addGridItem(i, j, chunkSize, bitmap)
                }
            }
        }
    }

    private fun addGridItem(row: Int, col: Int, chunkSize: Int, src: Bitmap) {
        ImageView(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = chunkSize
                height = chunkSize
                rowSpec = GridLayout.spec(row)
                columnSpec = GridLayout.spec(col)
            }

            val cropped = Bitmap.createBitmap(
                src,
                col * chunkSize,
                row * chunkSize,
                chunkSize,
                chunkSize
            )

            setImageBitmap(cropped)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.8f
        }.also { previewGrid?.addView(it) }
    }


    private fun addGridDividers() {
        // 创建分割线时设置标签
        fun createDivider(): View {
            return View(this).apply {
                tag = "grid_divider" // 设置唯一标识
                setBackgroundColor(Color.WHITE)
            }
        }

        // 横向分割线（带标签）
        for (i in 1..2) {
            createDivider().apply {
                layoutParams = FrameLayout.LayoutParams(
                    uploadContainer.width,
                    2
                ).apply {
                    gravity = Gravity.TOP
                    topMargin = (uploadContainer.height / 3) * i
                }
                uploadContainer.addView(this)
            }
        }

        // 纵向分割线（带标签）
        for (i in 1..2) {
            createDivider().apply {
                layoutParams = FrameLayout.LayoutParams(
                    2,
                    uploadContainer.height
                ).apply {
                    gravity = Gravity.START
                    leftMargin = (uploadContainer.width / 3) * i
                }
                uploadContainer.addView(this)
            }
        }

    }


    private fun clearPreviewGrid() {
        // 安全清除网格布局
        previewGrid?.let {
            uploadContainer.removeView(it)
            it.removeAllViews()
            previewGrid = null
        }

        // 手动遍历删除带有特定标识的视图
        val toRemove = mutableListOf<View>()
        for (i in 0 until uploadContainer.childCount) {
            val child = uploadContainer.getChildAt(i)
            if (child.getTag() == "grid_divider") {
                toRemove.add(child)
            }
        }
        toRemove.forEach { uploadContainer.removeView(it) }
    }



    private fun hideGuidelines() {
        // 使用安全调用操作符
        uploadContainer.findViewById<View>(R.id.horizontal_line)?.visibility = View.GONE
        uploadContainer.findViewById<View>(R.id.vertical_line)?.visibility = View.GONE
    }

    private fun showGuidelines() {
        uploadContainer.findViewById<View>(R.id.horizontal_line)?.visibility = View.VISIBLE
        uploadContainer.findViewById<View>(R.id.vertical_line)?.visibility = View.VISIBLE
    }

}



