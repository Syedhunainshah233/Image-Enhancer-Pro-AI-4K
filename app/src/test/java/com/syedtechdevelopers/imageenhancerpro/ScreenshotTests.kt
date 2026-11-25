package com.syedtechdevelopers.imageenhancerpro

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.syedtechdevelopers.imageenhancerpro.ai.Stage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders every screen and saves PNGs to app/build/screens for review. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTests {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun clearPrefs() {
        rule.activity.getSharedPreferences("limits", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        captureScreenRoboImage("build/screens/$name.png")
    }

    private fun scene(w: Int, h: Int, sharp: Boolean): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(90, 150, 230), Color.rgb(240, 200, 150), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = Color.rgb(60, 70, 90)
        val path = android.graphics.Path().apply { moveTo(0f, h * 0.7f); lineTo(w * 0.45f, h * 0.25f); lineTo(w.toFloat(), h * 0.75f); lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close() }
        c.drawPath(path, p)
        p.color = Color.rgb(30, 110, 60)
        c.drawRect(0f, h * 0.82f, w.toFloat(), h.toFloat(), p)
        p.color = Color.rgb(255, 230, 120)
        c.drawCircle(w * 0.78f, h * 0.2f, w * 0.08f, p)
        if (sharp) return b
        val small = Bitmap.createScaledBitmap(b, w / 12, h / 12, true)
        return Bitmap.createScaledBitmap(small, w, h, true)
    }

    @Test
    fun screens() {
        val vm = rule.activity.vm
        shot("01_home_empty")
        rule.runOnUiThread { vm.setSource(scene(900, 1200, sharp = false)) }
        shot("02_home_photo")
        rule.runOnUiThread { vm.progress = 0.46f; vm.stage = Stage.Upscale; vm.screen = Screen.Working }
        shot("03_working")
        rule.runOnUiThread { vm.showStop = true }
        shot("03b_stop_dialog")
        rule.runOnUiThread { vm.showStop = false }
        rule.runOnUiThread { vm.setResult(scene(3072, 4096, sharp = true)) }
        shot("04_result")
        rule.runOnUiThread { vm.showSave = true }
        shot("05_save_dialog")
        rule.runOnUiThread { vm.showSave = false; repeat(5) { vm.limits.useEnhance() }; vm.screen = Screen.Home; vm.showUnlock = true }
        shot("06_limit_dialog")
        rule.runOnUiThread { repeat(2) { vm.limits.adWatched() } }
        shot("07_limit_2_of_5")
        rule.runOnUiThread { repeat(3) { vm.limits.adWatched() }; vm.showUnlock = false }
        shot("08_home_unlimited")
        rule.runOnUiThread { vm.showInfo = true }
        shot("09_info")
    }
}
