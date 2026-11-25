package com.syedtechdevelopers.imageenhancerpro.ai

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure maths used by the enhancer (no Android classes, so it is unit tested on the JVM). */
object ImageMath {
    /** Final image size: long side 4096 px ("4K"), same shape as the user's photo. */
    const val TARGET_LONG = 4096

    /** Biggest input of one AI pass (x4 → 4096). */
    const val WORK_LONG = 1024

    const val TILE = 192
    const val PAD = 16
    const val FACE = 512

    /** GFPGAN's 512×512 face template (FFHQ): eyes, nose tip, mouth corners as seen in the image. */
    val TEMPLATE = floatArrayOf(
        192.98138f, 239.94708f, 318.90277f, 240.1936f, 256.63416f, 314.01935f,
        201.26117f, 371.41043f, 313.08905f, 371.15118f,
    )

    fun scaledSize(w: Int, h: Int, long: Int): Pair<Int, Int> {
        val s = long.toFloat() / max(w, h)
        return max(1, (w * s).roundToInt()) to max(1, (h * s).roundToInt())
    }

    /** Small photos get two x4 passes (e.g. 240 → 960 → 3840), bigger ones one pass. */
    fun passes(w: Int, h: Int): Int = if (max(w, h) * 4 < 2048) 2 else 1

    /** Size that goes into a pass: capped at 1024 on the long side. */
    fun passInput(w: Int, h: Int): Pair<Int, Int> = if (max(w, h) > WORK_LONG) scaledSize(w, h, WORK_LONG) else w to h

    data class Tile(val x: Int, val y: Int, val w: Int, val h: Int, val x0: Int, val y0: Int, val x1: Int, val y1: Int)

    /** Tiles with [PAD] px of context on each side, so tile seams don't show. */
    fun tiles(w: Int, h: Int): List<Tile> {
        val out = ArrayList<Tile>()
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val tw = min(TILE, w - x)
                val th = min(TILE, h - y)
                out += Tile(x, y, tw, th, max(x - PAD, 0), max(y - PAD, 0), min(x + tw + PAD, w), min(y + th + PAD, h))
                x += TILE
            }
            y += TILE
        }
        return out
    }

    /** Total tiles over all passes (for the progress bar). */
    fun totalTiles(w: Int, h: Int): Int {
        var cw = w; var ch = h; var n = 0
        repeat(passes(w, h)) {
            val (iw, ih) = passInput(cw, ch)
            n += tiles(iw, ih).size
            cw = iw * 4; ch = ih * 4
        }
        return n
    }

    /**
     * Least-squares similarity transform (rotation + uniform scale + shift) taking the 5 face points
     * [src] (x,y pairs) onto [dst]. Returns a, b, tx, ty with u = a·x − b·y + tx, v = b·x + a·y + ty.
     */
    fun similarity(src: FloatArray, dst: FloatArray): FloatArray {
        val n = src.size / 2
        var sx = 0.0; var sy = 0.0; var dx = 0.0; var dy = 0.0
        for (i in 0 until n) { sx += src[2 * i]; sy += src[2 * i + 1]; dx += dst[2 * i]; dy += dst[2 * i + 1] }
        sx /= n; sy /= n; dx /= n; dy /= n
        var num1 = 0.0; var num2 = 0.0; var den = 0.0
        for (i in 0 until n) {
            val x = src[2 * i] - sx; val y = src[2 * i + 1] - sy
            val u = dst[2 * i] - dx; val v = dst[2 * i + 1] - dy
            num1 += x * u + y * v
            num2 += x * v - y * u
            den += x * x + y * y
        }
        val a = num1 / den; val b = num2 / den
        val tx = dx - (a * sx - b * sy); val ty = dy - (b * sx + a * sy)
        return floatArrayOf(a.toFloat(), b.toFloat(), tx.toFloat(), ty.toFloat())
    }

    /**
     * Puts 5 face points (two eyes, nose, two mouth corners, any left/right labelling) into the
     * template order: picture-left eye, picture-right eye, nose, picture-left mouth, picture-right mouth,
     * as seen on an upright face. "Left" is worked out from the face itself (eyes → mouth = down),
     * so tilted, sideways or mirrored faces all line up. Returns null for unusable points.
     */
    fun orderFacePoints(p: FloatArray): FloatArray? {
        var e1x = p[0]; var e1y = p[1]; var e2x = p[2]; var e2y = p[3]
        val nx = p[4]; val ny = p[5]
        var m1x = p[6]; var m1y = p[7]; var m2x = p[8]; var m2y = p[9]
        val emx = (e1x + e2x) / 2; val emy = (e1y + e2y) / 2
        val dx = (m1x + m2x) / 2 - emx; val dy = (m1y + m2y) / 2 - emy // "down" along the face
        val dLen = kotlin.math.hypot(dx, dy)
        val eyeDist = kotlin.math.hypot(e2x - e1x, e2y - e1y)
        if (dLen < 4f || eyeDist < 8f) return null
        val lx = -dy; val ly = dx // picture-left of an upright face
        if ((e1x - emx) * lx + (e1y - emy) * ly < (e2x - emx) * lx + (e2y - emy) * ly) {
            e1x = e2x.also { e2x = e1x }; e1y = e2y.also { e2y = e1y }
        }
        val mmx = (m1x + m2x) / 2; val mmy = (m1y + m2y) / 2
        if ((m1x - mmx) * lx + (m1y - mmy) * ly < (m2x - mmx) * lx + (m2y - mmy) * ly) {
            m1x = m2x.also { m2x = m1x }; m1y = m2y.also { m2y = m1y }
        }
        // ML Kit gives the nose base; the template uses the nose tip a little higher.
        val tnx = nx + (emx - nx) * 0.15f; val tny = ny + (emy - ny) * 0.15f
        return floatArrayOf(e1x, e1y, e2x, e2y, tnx, tny, m1x, m1y, m2x, m2y)
    }

    /** Soft-edged square mask (0..1) for pasting the restored face back without a visible box. */
    fun faceMask(size: Int = FACE, edge: Float = 20f, feather: Float = 40f): FloatArray {
        val m = FloatArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            val d = min(min(x, y), min(size - 1 - x, size - 1 - y)).toFloat()
            val t = ((d - edge) / feather).coerceIn(0f, 1f)
            m[y * size + x] = t * t * (3 - 2 * t)
        }
        return m
    }
}
