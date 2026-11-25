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
 *  2. Real-ESRGAN x2plus sharpens the whole photo (body, clothes, background) and doubles its size,
 *     in tiles (1–3 passes), then it is resized to a 4096 px long side.
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
            for ((iw, ih) in ImageMath.passPlan(src.width, src.height)) {
                val input = if (iw != cur.width || ih != cur.height) Bitmap.createScaledBitmap(cur, iw, ih, true) else cur
                val out = Bitmap.createBitmap(iw * S, ih * S, Bitmap.Config.ARGB_8888)
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
                val ow = tw * S
                val op = ow * th * S
                val ox = (t.x - t.x0) * S
                val oy = (t.y - t.y0) * S
                val w4 = t.w * S
                val h4 = t.h * S
                val outPx = IntArray(w4 * h4)
                for (yy in 0 until h4) {
                    val row = (oy + yy) * ow + ox
                    for (xx in 0 until w4) {
                        val idx = row + xx
                        outPx[yy * w4 + xx] = argb(o.get(idx), o.get(op + idx), o.get(2 * op + idx), 255)
                    }
                }
                out.setPixels(outPx, 0, w4, t.x * S, t.y * S, w4, h4)
            }
        }
    }

    internal suspend fun detectFaces(src: Bitmap): List<FloatArray> {
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
        val types = intArrayOf(
            FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE, FaceLandmark.NOSE_BASE,
            FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT,
        )
        try {
            // Upright first; sideways photos are retried turned 90° each way.
            for (rot in intArrayOf(0, 90, 270)) {
                val img = if (rot == 0) det else Bitmap.createBitmap(det, 0, 0, det.width, det.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
                val found = detector.process(InputImage.fromBitmap(img, 0)).await()
                if (img !== det) img.recycle()
                val faces = found
                    .sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
                    .mapNotNull { face ->
                        val raw = FloatArray(10)
                        for ((i, type) in types.withIndex()) {
                            val p = face.getLandmark(type)?.position ?: return@mapNotNull null
                            // back to upright-photo coordinates, then to the full-size photo
                            val (x, y) = when (rot) {
                                90 -> p.y to (det.height - 1 - p.x)
                                270 -> (det.width - 1 - p.y) to p.x
                                else -> p.x to p.y
                            }
                            raw[2 * i] = x / s
                            raw[2 * i + 1] = y / s
                        }
                        ImageMath.orderFacePoints(raw)
                    }
                    .take(MAX_FACES)
                if (faces.isNotEmpty()) return faces
            }
            return emptyList()
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
        const val ESRGAN = "esrgan_x2plus.onnx"
        private const val S = ImageMath.SCALE
        const val GFPGAN = "gfpgan_v14.onnx"
        const val MAX_FACES = 8

        private fun c(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
        private fun argb(r: Float, g: Float, b: Float, a: Int): Int =
            (a.coerceIn(0, 255) shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }
}
