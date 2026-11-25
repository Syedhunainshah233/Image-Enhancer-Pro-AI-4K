package com.syedtechdevelopers.imageenhancerpro

import com.syedtechdevelopers.imageenhancerpro.ai.ImageMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMathTest {
    @Test
    fun smallPhotoGetsTwoPassesTo4k() {
        assertEquals(2, ImageMath.passes(240, 240))
        assertEquals(1, ImageMath.passes(800, 600))
        assertEquals(1, ImageMath.passes(2048, 1536))
        assertEquals(4096 to 3072, ImageMath.scaledSize(2048, 1536, 4096))
        assertEquals(1024 to 768, ImageMath.passInput(2048, 1536))
    }

    @Test
    fun tilesCoverImageExactlyOnce() {
        val w = 1000; val h = 731
        val hit = IntArray(w * h)
        for (t in ImageMath.tiles(w, h)) {
            assertTrue(t.x0 <= t.x && t.y0 <= t.y && t.x1 >= t.x + t.w && t.y1 >= t.y + t.h)
            for (y in t.y until t.y + t.h) for (x in t.x until t.x + t.w) hit[y * w + x]++
        }
        assertTrue(hit.all { it == 1 })
    }

    @Test
    fun similarityRecoversRotationScaleShift() {
        val a = 0.8f; val b = 0.3f; val tx = 12f; val ty = -7f
        val src = floatArrayOf(10f, 20f, 50f, 22f, 30f, 40f, 15f, 60f, 45f, 61f)
        val dst = FloatArray(10)
        for (i in 0 until 5) {
            val x = src[2 * i]; val y = src[2 * i + 1]
            dst[2 * i] = a * x - b * y + tx; dst[2 * i + 1] = b * x + a * y + ty
        }
        val r = ImageMath.similarity(src, dst)
        assertEquals(a, r[0], 1e-4f); assertEquals(b, r[1], 1e-4f)
        assertEquals(tx, r[2], 1e-3f); assertEquals(ty, r[3], 1e-3f)
    }

    /** Aligning any labelling of the 5 points must give an upright face (positive scale, small rotation). */
    @Test
    fun facePointsAlwaysGiveUprightCrop() {
        val t = ImageMath.TEMPLATE
        for (angle in listOf(0.0, 25.0, -40.0, 90.0, 180.0)) for (mirror in listOf(false, true)) for (swap in listOf(false, true)) {
            val r = Math.toRadians(angle); val c = Math.cos(r).toFloat(); val s = Math.sin(r).toFloat()
            // A face in the picture = template turned by angle (mirrored photos keep an upright-looking face).
            val pts = FloatArray(10)
            for (i in 0 until 5) {
                val x = t[2 * i] - 256f; val y = t[2 * i + 1] - 256f
                pts[2 * i] = 600f + 2f * (c * x - s * y); pts[2 * i + 1] = 500f + 2f * (s * x + c * y)
            }
            fun sw(a: Int, b: Int) { for (k in 0..1) { val v = pts[2 * a + k]; pts[2 * a + k] = pts[2 * b + k]; pts[2 * b + k] = v } }
            if (mirror) for (i in 0 until 5) pts[2 * i] = 1200f - pts[2 * i]
            if (swap) { sw(0, 1); sw(3, 4) } // labels the other way round
            val o = ImageMath.orderFacePoints(pts)!!
            val m = ImageMath.similarity(o, t)
            val scale = Math.hypot(m[0].toDouble(), m[1].toDouble())
            val rot = Math.toDegrees(Math.atan2(m[1].toDouble(), m[0].toDouble()))
            assertEquals("scale a=$angle m=$mirror s=$swap", 0.5, scale, 0.05)
            val expected = if (mirror) angle else -angle
            val diff = ((rot - expected + 540) % 360) - 180
            assertTrue("rotation $rot vs $expected (a=$angle m=$mirror s=$swap)", Math.abs(diff) < 5)
        }
    }

    @Test
    fun maskIsSoftAtEdgesAndSolidInside() {
        val m = ImageMath.faceMask()
        assertEquals(0f, m[0], 0f)
        assertEquals(1f, m[256 * 512 + 256], 0f)
    }
}
