package com.syedtechdevelopers.imageenhancerpro.ai

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max

object ImageIO {
    /** Biggest photo kept in memory (faces are cut from this, so it stays sharp). */
    private const val MAX_SOURCE = 2048
    private const val FOLDER = "Image Enhancer Pro"

    /** Reads the picked photo upright, at most [MAX_SOURCE] px, as an opaque software bitmap. */
    fun decode(context: Context, uri: Uri): Bitmap {
        val raw: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                if (max(w, h) > MAX_SOURCE) {
                    val (tw, th) = ImageMath.scaledSize(w, h, MAX_SOURCE)
                    d.setTargetSize(tw, th)
                }
            }
        } else {
            decodeLegacy(context, uri)
        }
        return flatten(raw)
    }

    private fun decodeLegacy(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SOURCE) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Cannot read this image")
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        var out = bmp
        if (rotation != 0) {
            out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
            if (out !== bmp) bmp.recycle()
        }
        if (max(out.width, out.height) > MAX_SOURCE) {
            val (w, h) = ImageMath.scaledSize(out.width, out.height, MAX_SOURCE)
            val s = Bitmap.createScaledBitmap(out, w, h, true)
            if (s !== out) out.recycle()
            out = s
        }
        return out
    }

    /** ARGB_8888 with no transparency (transparent PNGs get a white background). */
    private fun flatten(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        if (src !== out) src.recycle()
        return out
    }

    fun scaledCopy(src: Bitmap, long: Int): Bitmap {
        if (max(src.width, src.height) <= long) return src
        val (w, h) = ImageMath.scaledSize(src.width, src.height, long)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    /** Saves to Pictures/Image Enhancer Pro. [long] = long side in px (4096 = 4K). Returns the file name. */
    fun saveToGallery(context: Context, image: Bitmap, long: Int): String {
        val bmp = scaledCopy(image, long)
        val name = "Enhanced_${System.currentTimeMillis()}.jpg"
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Cannot save")
                resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER).apply { mkdirs() }
                val file = File(dir, name)
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
            }
        } finally {
            if (bmp !== image) bmp.recycle()
        }
        return name
    }

    fun shareIntent(context: Context, image: Bitmap): Intent {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "enhanced.jpg")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share photo")
    }
}
