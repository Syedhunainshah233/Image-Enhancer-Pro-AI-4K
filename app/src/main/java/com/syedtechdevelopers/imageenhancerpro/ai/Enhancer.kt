package com.syedtechdevelopers.imageenhancerpro.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.coroutines.coroutineContext
import kotlin.math.hypot
import kotlin.math.max

enum class Stage { Preparing, Faces, Upscale, Restore, Finishing }

class MissingModelsException : Exception("AI files are missing")

/**
 * On-device enhancer:
 *  1. ML Kit finds faces (5 points each).
 *  2. Real-ESRGAN (general x4v3) removes blur/noise and upscales x4, in tiles (1 or 2 passes) → long side 4096.
 *  3. GFPGAN v1.4 rebuilds every face at 512×512 and it is blended back with a soft mask.
 * Everything runs on the phone; the photo never leaves the device.
 */
class Enhancer(private val context: Context) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private fun options() = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }

    /** Opens a model from the install-time asset pack: mapped in place if stored uncompressed, else copied once. */
    private fun openSession(name: String): OrtSession {
        try {
            context.assets.openFd(name).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    val buf = stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                    return env.createSession(buf, options())
                }
            }
        } catch (_: IOException) {
            // Compressed asset (or missing): fall back to a one-time copy below.
        }
        val dir = File(context.noBackupFilesDir, "models").apply { mkdirs() }
        val file = File(dir, name)
        if (!file.exists()) {
            val tmp = File(dir, "$name.tmp")
            try {
                context.assets.open(name).use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 20) } }
            } catch (e: IOException) {
                tmp.delete()
                throw MissingModelsException()
            }
            tmp.renameTo(file)
        }
        return env.createSession(file.absolutePath, options())
    }

    /** True when the AI files were installed with the app (Play Store install or full APK). */
    fun modelsPresent(): Boolean = runCatching {
        context.assets.list("")?.toSet()?.containsAll(listOf(ESRGAN, GFPGAN)) == true ||
            File(context.noBackupFilesDir, "models/$GFPGAN").exists()
    }.getOrDefault(false)

    suspend fun enhance(src: Bitmap, progress: (Float, Stage) -> Unit): Bitmap = withContext(Dispatchers.Default) {
        progress(0.01f, Stage.Preparing)
        val faces = runCatching { detectFaces(src) }.getOrDefault(emptyList())
        val srShare = if (faces.isEmpty()) 0.90f else 0.62f
        val total = ImageMath.totalTiles(src.width, src.height).coerceAtLeast(1)
        var done = 0

        progress(0.04f, Stage.Upscale)
        var cur = src
        val esr = openSession(ESRGAN)
        try {
            repeat(ImageMath.passes(src.width, src.height)) {
                val (iw, ih) = ImageMath.passInput(cur.width, cur.height)
                val input = if (iw != cur.width || ih != cur.height) Bitmap.createScaledBitmap(cur, iw, ih, true) else cur
                val out = Bitmap.createBitmap(iw * 4, ih * 4, Bitmap.Config.ARGB_8888)
                for (t in ImageMath.tiles(iw, ih)) {
                    coroutineContext.ensureActive()
                    runTile(esr, input, out, t)
                    done++
                    progress(0.05f + srShare * done / total, Stage.Upscale)
                }
                if (input !== cur && input !== src) input.recycle()
                if (cur !== src) cur.recycle()
                cur = out
            }
        } finally {
            esr.close()
        }

        val (fw, fh) = ImageMath.scaledSize(cur.width, cur.height, ImageMath.TARGET_LONG)
        var result = cur
        if (fw != cur.width || fh != cur.height) {
            result = Bitmap.createScaledBitmap(cur, fw, fh, true)
            if (cur !== src && result !== cur) cur.recycle()
        }
        if (!result.isMutable) result = result.copy(Bitmap.Config.ARGB_8888, true)

        if (faces.isNotEmpty()) {
            progress(0.05f + srShare, Stage.Restore)
            restoreFaces(src, result, faces) { i ->
                progress(0.05f + srShare + (0.93f - 0.05f - srShare) * (i + 1) / faces.size, Stage.Restore)
            }
        }
        progress(0.97f, Stage.Finishing)
        result
    }

    private fun runTile(session: OrtSession, input: Bitmap, out: Bitmap, t: ImageMath.Tile) {
        val tw = t.x1 - t.x0
        val th = t.y1 - t.y0
        val plane = tw * th
        val px = IntArray(plane)
        input.getPixels(px, 0, tw, t.x0, t.y0, tw, th)
        val fb = ByteBuffer.allocateDirect(4 * 3 * plane).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (i in 0 until plane) {
            val p = px[i]
            fb.put(i, ((p shr 16) and 255) / 255f)
            fb.put(plane + i, ((p shr 8) and 255) / 255f)
            fb.put(2 * plane + i, (p and 255) / 255f)
        }
        OnnxTensor.createTensor(env, fb, longArrayOf(1, 3, th.toLong(), tw.toLong())).use { tensor ->
            session.run(mapOf("input" to tensor)).use { res ->
                val o = (res.get(0) as OnnxTensor).floatBuffer
                val ow = tw * 4
                val op = ow * th * 4
                val ox = (t.x - t.x0) * 4
                val oy = (t.y - t.y0) * 4
                val w4 = t.w * 4
                val h4 = t.h * 4
                val outPx = IntArray(w4 * h4)
                for (yy in 0 until h4) {
                    val row = (oy + yy) * ow + ox
                    for (xx in 0 until w4) {
                        val idx = row + xx
                        outPx[yy * w4 + xx] = argb(o.get(idx), o.get(op + idx), o.get(2 * op + idx), 255)
                    }
                }
                out.setPixels(outPx, 0, w4, t.x * 4, t.y * 4, w4, h4)
            }
        }
    }

    private suspend fun detectFaces(src: Bitmap): List<FloatArray> {
        val long = max(src.width, src.height)
        val s = if (long > 1280) 1280f / long else 1f
        val det = if (s < 1f) Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true) else src
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.04f)
                .build(),
        )
        try {
            val found = detector.process(InputImage.fromBitmap(det, 0)).await()
            // Image order expected by GFPGAN: eye on the left of the picture first (the person's right eye).
            val order = intArrayOf(
                FaceLandmark.RIGHT_EYE, FaceLandmark.LEFT_EYE, FaceLandmark.NOSE_BASE,
                FaceLandmark.MOUTH_RIGHT, FaceLandmark.MOUTH_LEFT,
            )
            return found
                .sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
                .mapNotNull { face ->
                    val pts = FloatArray(10)
                    for ((i, type) in order.withIndex()) {
                        val p = face.getLandmark(type)?.position ?: return@mapNotNull null
                        pts[2 * i] = p.x / s
                        pts[2 * i + 1] = p.y / s
                    }
                    val eyeDist = hypot(pts[2] - pts[0], pts[3] - pts[1])
                    if (eyeDist < 8f) return@mapNotNull null
                    // Nose base sits a little under the nose tip used by the template: nudge it towards the eyes.
                    val ex = (pts[0] + pts[2]) / 2f
                    val ey = (pts[1] + pts[3]) / 2f
                    pts[4] += (ex - pts[4]) * 0.15f
                    pts[5] += (ey - pts[5]) * 0.15f
                    pts
                }
                .take(MAX_FACES)
        } finally {
            detector.close()
            if (det !== src) det.recycle()
        }
    }

    private suspend fun restoreFaces(src: Bitmap, result: Bitmap, faces: List<FloatArray>, onFace: (Int) -> Unit) {
        val size = ImageMath.FACE
        val mask = ImageMath.faceMask()
        val scale = result.width.toFloat() / src.width
        val canvas = Canvas(result)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val gfp = openSession(GFPGAN)
        try {
            faces.forEachIndexed { i, lm ->
                coroutineContext.ensureActive()
                val (a, b, tx, ty) = ImageMath.similarity(lm, ImageMath.TEMPLATE).toList()
                val m = Matrix().apply { setValues(floatArrayOf(a, -b, tx, b, a, ty, 0f, 0f, 1f)) }
                val crop = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                Canvas(crop).drawRect(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    Paint(Paint.FILTER_BITMAP_FLAG).apply {
                        shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
                    },
                )
                val restored = runFace(gfp, crop, mask)
                crop.recycle()
                val inv = Matrix()
                if (m.invert(inv)) {
                    inv.postScale(scale, scale)
                    canvas.drawBitmap(restored, inv, paint)
                }
                restored.recycle()
                onFace(i)
            }
        } finally {
            gfp.close()
        }
    }

    private fun runFace(session: OrtSession, crop: Bitmap, mask: FloatArray): Bitmap {
        val size = ImageMath.FACE
        val plane = size * size
        val px = IntArray(plane)
        crop.getPixels(px, 0, size, 0, 0, size, size)
        val fb = ByteBuffer.allocateDirect(4 * 3 * plane).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (i in 0 until plane) {
            val p = px[i]
            fb.put(i, ((p shr 16) and 255) / 255f)
            fb.put(plane + i, ((p shr 8) and 255) / 255f)
            fb.put(2 * plane + i, (p and 255) / 255f)
        }
        val out = IntArray(plane)
        OnnxTensor.createTensor(env, fb, longArrayOf(1, 3, size.toLong(), size.toLong())).use { tensor ->
            session.run(mapOf("input" to tensor)).use { res ->
                val o = (res.get(0) as OnnxTensor).floatBuffer
                for (i in 0 until plane) {
                    out[i] = argb(o.get(i), o.get(plane + i), o.get(2 * plane + i), (mask[i] * 255f + 0.5f).toInt())
                }
            }
        }
        return Bitmap.createBitmap(out, size, size, Bitmap.Config.ARGB_8888)
    }

    companion object {
        const val ESRGAN = "esrgan_x4v3.onnx"
        const val GFPGAN = "gfpgan_v14.onnx"
        const val MAX_FACES = 8

        private fun c(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
        private fun argb(r: Float, g: Float, b: Float, a: Int): Int =
            (a.coerceIn(0, 255) shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }
}
