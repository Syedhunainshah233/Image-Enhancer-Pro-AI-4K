package com.syedtechdevelopers.imageenhancerpro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.syedtechdevelopers.imageenhancerpro.ai.Enhancer
import com.syedtechdevelopers.imageenhancerpro.ai.ImageIO
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Runs on a real Android system (emulator in GitHub Actions) with the real AI models:
 * full enhance, face detection + restore, save to gallery, and the app screens with test ads.
 * Pictures go to /sdcard/Android/data/<app>/files/devtest for review.
 */
@RunWith(AndroidJUnit4::class)
class DeviceTests {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val outDir get() = File(ctx.getExternalFilesDir(null), "devtest").apply { mkdirs() }

    private fun save(name: String, bmp: Bitmap) {
        val small = ImageIO.scaledCopy(bmp, 1280)
        File(outDir, name).outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun screenshot(name: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { save(name, it) }
    }

    private fun scene(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(90, 150, 230))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 120, 60) }
        c.drawRect(0f, h * 0.6f, w.toFloat(), h.toFloat(), p)
        p.color = Color.rgb(250, 220, 90); c.drawCircle(w * 0.7f, h * 0.3f, h * 0.12f, p)
        p.color = Color.BLACK; p.textSize = h * 0.12f; c.drawText("Hello 4K", w * 0.1f, h * 0.5f, p)
        return Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(b, w / 3, h / 3, true), w, h, true)
    }

    private fun mean(b: Bitmap): Float {
        val s = ImageIO.scaledCopy(b, 64)
        val px = IntArray(s.width * s.height); s.getPixels(px, 0, s.width, 0, 0, s.width, s.height)
        return px.map { ((it shr 16 and 255) + (it shr 8 and 255) + (it and 255)) / 3f }.average().toFloat()
    }

    @Test
    fun enhanceSmallPhotoTo4k() = runBlocking {
        val e = Enhancer(ctx)
        assertTrue("AI files must be in the app", e.modelsPresent())
        val src = scene(320, 240)
        var last = 0f
        val t = SystemClock.elapsedRealtime()
        val out = e.enhance(src) { p, _ -> assertTrue(p >= 0f && p <= 1f); last = p }
        android.util.Log.i("DeviceTests", "small photo: ${SystemClock.elapsedRealtime() - t} ms")
        assertEquals(4096, out.width)
        assertEquals(3072, out.height)
        assertTrue(last > 0.9f)
        assertTrue("colours kept", abs(mean(out) - mean(src)) < 20f)
        save("small_after.jpg", out)
    }

    @Test
    fun enhanceFacePhoto() = runBlocking {
        val names = InstrumentationRegistry.getInstrumentation().context.assets.list("") ?: emptyArray()
        assumeTrue("face.jpg test photo not downloaded", "face.jpg" in names)
        val raw = InstrumentationRegistry.getInstrumentation().context.assets.open("face.jpg").use { BitmapFactory.decodeStream(it) }
        val src = ImageIO.scaledCopy(raw, 2048).copy(Bitmap.Config.ARGB_8888, false)
        val e = Enhancer(ctx)
        val faces = e.detectFaces(src)
        android.util.Log.i("DeviceTests", "faces found: ${faces.size}")
        assertTrue("ML Kit should find the face", faces.isNotEmpty())
        val t = SystemClock.elapsedRealtime()
        val out = e.enhance(src) { _, _ -> }
        android.util.Log.i("DeviceTests", "face photo ${src.width}x${src.height}: ${SystemClock.elapsedRealtime() - t} ms")
        assertEquals(4096, max(out.width, out.height))
        save("face_before.jpg", src)
        save("face_after.jpg", out)
        // Saving to the gallery works (no permission needed on Android 10+).
        assertTrue(ImageIO.saveToGallery(ctx, out, 4096).endsWith(".jpg"))
    }

    @Test
    fun appScreensWithAdsDoNotCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            SystemClock.sleep(12_000) // AdMob start, app open + native + banner test ads
            screenshot("app_1_home.jpg")
            sc.onActivity { it.vm.setSource(scene(600, 800)) }
            SystemClock.sleep(3_000)
            screenshot("app_2_photo.jpg")
            sc.onActivity { it.vm.startEnhance() }
            SystemClock.sleep(6_000)
            screenshot("app_3_working.jpg")
            sc.recreate() // e.g. dark mode switch while enhancing
            val deadline = SystemClock.elapsedRealtime() + 6 * 60_000
            var done = false
            while (!done && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(2_000)
                sc.onActivity { done = it.vm.screen == Screen.Result }
            }
            assertTrue("enhance finished in the app", done)
            SystemClock.sleep(4_000)
            screenshot("app_4_result.jpg")
            sc.moveToState(Lifecycle.State.CREATED) // app goes to background
            sc.moveToState(Lifecycle.State.RESUMED) // and back (app open ad path)
            SystemClock.sleep(3_000)
            sc.onActivity { it.vm.limits.tick() }
        }
    }
}
