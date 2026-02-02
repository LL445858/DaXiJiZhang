package com.example.daxijizhang.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 图片选择工具类
 * 支持从相册选择图片并进行圆形裁剪（头像）或方形裁剪（背景）
 */
class ImagePickerUtil private constructor() {

    companion object {
        const val REQUEST_AVATAR_PICK = 1001
        const val REQUEST_BACKGROUND_PICK = 1002
        
        private var callback: ImagePickerCallback? = null

        /**
         * 从相册选择头像图片
         */
        fun pickAvatarFromGallery(activity: Activity) {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            activity.startActivityForResult(intent, REQUEST_AVATAR_PICK)
        }

        /**
         * 从相册选择背景图片
         */
        fun pickBackgroundFromGallery(activity: Activity) {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            activity.startActivityForResult(intent, REQUEST_BACKGROUND_PICK)
        }

        /**
         * 处理Activity结果
         */
        fun handleActivityResult(
            context: Context,
            requestCode: Int,
            resultCode: Int,
            data: Intent?,
            callback: ImagePickerCallback
        ) {
            if (resultCode != Activity.RESULT_OK) return

            when (requestCode) {
                REQUEST_AVATAR_PICK -> {
                    data?.data?.let { uri ->
                        processAvatarImage(context, uri, callback)
                    }
                }
                REQUEST_BACKGROUND_PICK -> {
                    data?.data?.let { uri ->
                        processBackgroundImage(context, uri, callback)
                    }
                }
            }
        }

        /**
         * 处理头像图片：直接使用原图，不进行裁切
         */
        private fun processAvatarImage(context: Context, uri: Uri, callback: ImagePickerCallback) {
            try {
                // 加载原始图片
                val inputStream = context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap == null) {
                    callback.onError("无法加载图片")
                    return
                }

                // 缩放图片到合适大小
                val scaledBitmap = scaleBitmap(originalBitmap, 800)
                if (scaledBitmap != originalBitmap) {
                    originalBitmap.recycle()
                }

                // 直接保存图片，不进行圆形裁切
                val avatarPath = saveBitmapToFile(context, scaledBitmap, "avatar.jpg")

                // 回收内存
                scaledBitmap.recycle()

                if (avatarPath != null) {
                    callback.onAvatarPicked(avatarPath)
                } else {
                    callback.onError("保存头像失败")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                callback.onError("处理图片失败: ${e.message}")
            }
        }

        /**
         * 处理背景图片：加载、缩放、方形裁剪
         */
        private fun processBackgroundImage(context: Context, uri: Uri, callback: ImagePickerCallback) {
            try {
                // 加载原始图片
                val inputStream = context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap == null) {
                    callback.onError("无法加载图片")
                    return
                }

                // 缩放图片到合适大小
                val scaledBitmap = scaleBitmap(originalBitmap, 1200)
                if (scaledBitmap != originalBitmap) {
                    originalBitmap.recycle()
                }

                // 创建方形版本（用于背景）
                val squareBitmap = createSquareBitmap(scaledBitmap)

                // 保存图片到本地
                val backgroundPath = saveBitmapToFile(context, squareBitmap, "background.jpg")

                // 回收内存
                scaledBitmap.recycle()

                if (backgroundPath != null) {
                    callback.onBackgroundPicked(backgroundPath)
                } else {
                    callback.onError("保存背景失败")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                callback.onError("处理图片失败: ${e.message}")
            }
        }

        /**
         * 缩放图片到指定最大尺寸
         */
        private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
            val width = bitmap.width
            val height = bitmap.height

            if (width <= maxSize && height <= maxSize) {
                return bitmap
            }

            val ratio = width.toFloat() / height.toFloat()
            val newWidth: Int
            val newHeight: Int

            if (width > height) {
                newWidth = maxSize
                newHeight = (maxSize / ratio).toInt()
            } else {
                newHeight = maxSize
                newWidth = (maxSize * ratio).toInt()
            }

            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }

        /**
         * 创建方形位图（用于背景）
         */
        private fun createSquareBitmap(bitmap: Bitmap): Bitmap {
            val width = bitmap.width
            val height = bitmap.height

            // 如果图片已经是16:9或更宽，直接使用
            val targetRatio = 16f / 9f
            val currentRatio = width.toFloat() / height.toFloat()

            return if (currentRatio >= targetRatio) {
                // 图片足够宽，直接缩放
                val targetHeight = 1080
                val targetWidth = (targetHeight * targetRatio).toInt()
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            } else {
                // 需要裁剪或填充
                val targetWidth = width
                val targetHeight = (width / targetRatio).toInt()

                if (height >= targetHeight) {
                    // 图片足够高，居中裁剪
                    val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(output)
                    val srcRect = Rect(0, (height - targetHeight) / 2, width, (height + targetHeight) / 2)
                    val dstRect = Rect(0, 0, targetWidth, targetHeight)
                    canvas.drawBitmap(bitmap, srcRect, dstRect, null)
                    output
                } else {
                    // 图片不够高，缩放
                    Bitmap.createScaledBitmap(bitmap, 1920, 1080, true)
                }
            }
        }

        /**
         * 保存位图到文件
         */
        private fun saveBitmapToFile(context: Context, bitmap: Bitmap, fileName: String): String? {
            return try {
                val file = File(context.filesDir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                file.absolutePath
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }

        /**
         * 加载保存的头像
         */
        fun loadAvatar(context: Context): Bitmap? {
            val file = File(context.filesDir, "avatar.jpg")
            return if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        }

        /**
         * 加载保存的背景
         */
        fun loadBackground(context: Context): Bitmap? {
            val file = File(context.filesDir, "background.jpg")
            return if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        }

        /**
         * 检查是否有自定义头像
         */
        fun hasCustomAvatar(context: Context): Boolean {
            return File(context.filesDir, "avatar.jpg").exists()
        }

        /**
         * 检查是否有自定义背景
         */
        fun hasCustomBackground(context: Context): Boolean {
            return File(context.filesDir, "background.jpg").exists()
        }
    }

    interface ImagePickerCallback {
        fun onAvatarPicked(avatarPath: String)
        fun onBackgroundPicked(backgroundPath: String)
        fun onError(message: String)
    }
}