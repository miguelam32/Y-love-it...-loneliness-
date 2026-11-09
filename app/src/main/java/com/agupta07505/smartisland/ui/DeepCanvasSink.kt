package com.agupta07505.smartisland.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.SparseArray
import kotlin.math.exp

/** Draws [DeepCanvas] output on a real android.graphics.Canvas with additive blending. */
internal class AndroidDcSink : DcSink {
    var canvas: Canvas? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val paintOver = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sprite: Bitmap = makeSprite()
    private val src = Rect(0, 0, SPR, SPR)
    private val dst = RectF()
    private val filters = SparseArray<PorterDuffColorFilter>()

    /** Tint filters are cached (5 bits per channel) so the glow path allocates nothing per frame. */
    private fun tint(r: Float, g: Float, b: Float): PorterDuffColorFilter {
        val qr = (r * 31f + 0.5f).toInt().coerceIn(0, 31)
        val qg = (g * 31f + 0.5f).toInt().coerceIn(0, 31)
        val qb = (b * 31f + 0.5f).toInt().coerceIn(0, 31)
        val key = (qr shl 10) or (qg shl 5) or qb
        var f = filters.get(key)
        if (f == null) {
            f = PorterDuffColorFilter(
                (0xFF shl 24) or ((qr * 255 / 31) shl 16) or ((qg * 255 / 31) shl 8) or (qb * 255 / 31),
                PorterDuff.Mode.SRC_IN
            )
            filters.put(key, f)
        }
        return f
    }

    private fun ch(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    private fun argb(a: Float, r: Float, g: Float, b: Float): Int = (ch(a) shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)

    override fun dots(xy: FloatArray, n: Int, size: Float, r: Float, g: Float, b: Float, a: Float) {
        val c = canvas ?: return
        paint.colorFilter = null
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = size
        paint.color = argb(a, r, g, b)
        c.drawPoints(xy, 0, n * 2, paint)
    }

    override fun lines(xy: FloatArray, n: Int, width: Float, r: Float, g: Float, b: Float, a: Float) {
        val c = canvas ?: return
        paint.colorFilter = null
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeWidth = width
        paint.color = argb(a, r, g, b)
        c.drawLines(xy, 0, n * 4, paint)
    }

    override fun glow(cx: Float, cy: Float, rx: Float, ry: Float, r: Float, g: Float, b: Float, a: Float) {
        val c = canvas ?: return
        if (a < 0.01f) return
        paint.style = Paint.Style.FILL
        paint.colorFilter = tint(r, g, b)
        paint.alpha = ch(a)
        dst.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawBitmap(sprite, src, dst, paint)
        paint.colorFilter = null
    }

    override fun ring(cx: Float, cy: Float, rad: Float, width: Float, r: Float, g: Float, b: Float, a: Float) {
        val c = canvas ?: return
        if (a < 0.01f) return
        paint.colorFilter = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = argb(a, r, g, b)
        c.drawCircle(cx, cy, rad, paint)
    }

    override fun rect(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, g: Float, b: Float, a: Float) {
        val c = canvas ?: return
        if (a < 0.01f) return
        paint.colorFilter = null
        paint.style = Paint.Style.FILL
        paint.color = argb(a, r, g, b)
        c.drawRect(x0, y0, x1, y1, paint)
    }

    override fun overDots(xy: FloatArray, n: Int, size: Float, r: Float, g: Float, b: Float, a: Float) {
        val c = canvas ?: return
        if (a < 0.01f) return
        paintOver.style = Paint.Style.STROKE
        paintOver.strokeCap = Paint.Cap.ROUND
        paintOver.strokeWidth = size
        paintOver.color = argb(a, r, g, b)
        c.drawPoints(xy, 0, n * 2, paintOver)
    }

    override fun overLines(xy: FloatArray, n: Int, width: Float, r: Float, g: Float, b: Float, a: Float) {
        val c = canvas ?: return
        if (a < 0.01f) return
        paintOver.style = Paint.Style.STROKE
        paintOver.strokeCap = Paint.Cap.BUTT
        paintOver.strokeWidth = width
        paintOver.color = argb(a, r, g, b)
        c.drawLines(xy, 0, n * 4, paintOver)
    }

    override fun overRect(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, g: Float, b: Float, a: Float) {
        val c = canvas ?: return
        if (a < 0.01f) return
        paintOver.style = Paint.Style.FILL
        paintOver.color = argb(a, r, g, b)
        c.drawRect(x0, y0, x1, y1, paintOver)
    }

    private companion object {
        const val SPR = 48

        fun makeSprite(): Bitmap {
            val px = IntArray(SPR * SPR)
            val e4 = exp(-4.0)
            val half = SPR / 2.0
            for (y in 0 until SPR) for (x in 0 until SPR) {
                val dx = (x + 0.5 - half) / half
                val dy = (y + 0.5 - half) / half
                val d2 = dx * dx + dy * dy
                val a = if (d2 >= 1.0) 0.0 else (exp(-d2 * 4.0) - e4) / (1.0 - e4)
                px[y * SPR + x] = (((a * 255.0 + 0.5).toInt().coerceIn(0, 255)) shl 24) or 0x00FFFFFF
            }
            return Bitmap.createBitmap(px, SPR, SPR, Bitmap.Config.ARGB_8888)
        }
    }
}
