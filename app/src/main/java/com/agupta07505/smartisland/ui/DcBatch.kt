package com.agupta07505.smartisland.ui

/** Target for [DeepCanvas]. dots/lines/glow/ring/rect blend ADDITIVELY (plus); the over* calls use normal blending. Colors 0..1, a 0..1. */
internal interface DcSink {
    fun dots(xy: FloatArray, n: Int, size: Float, r: Float, g: Float, b: Float, a: Float)
    fun lines(xy: FloatArray, n: Int, width: Float, r: Float, g: Float, b: Float, a: Float)
    fun glow(cx: Float, cy: Float, rx: Float, ry: Float, r: Float, g: Float, b: Float, a: Float)
    fun ring(cx: Float, cy: Float, rad: Float, width: Float, r: Float, g: Float, b: Float, a: Float)
    fun rect(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, g: Float, b: Float, a: Float)

    /** NORMAL (source-over) blending, used by the halftone / ink impact look. */
    fun overDots(xy: FloatArray, n: Int, size: Float, r: Float, g: Float, b: Float, a: Float)
    fun overLines(xy: FloatArray, n: Int, width: Float, r: Float, g: Float, b: Float, a: Float)
    fun overRect(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, g: Float, b: Float, a: Float)
}

/** Batches thousands of particles into a few drawPoints calls (palette x size x alpha buckets). */
internal class DotBatch {
    companion object {
        const val PAL = 14
        val SIZES = floatArrayOf(1.3f, 1.8f, 2.4f, 3.2f)
        val ALPHAS = floatArrayOf(0.10f, 0.22f, 0.38f, 0.58f, 0.80f, 1.0f)
        val PR = FloatArray(PAL)
        val PG = FloatArray(PAL)
        val PB = FloatArray(PAL)
        init {
            for (k in 0 until 8) {
                val c = DeepCanvas.gradc(k / 7f)
                PR[k] = c[0]; PG[k] = c[1]; PB[k] = c[2]
            }
            set(8, 0.95f, 0.97f, 1.0f)   // white
            set(9, 0.62f, 0.70f, 1.0f)   // lavender (tide)
            set(10, 0.42f, 0.52f, 1.0f)  // periwinkle (terrain, dim)
            set(11, 0.74f, 0.90f, 1.0f)  // needle
            set(12, 0.67f, 0.73f, 1.0f)  // terrain mid
            set(13, 0.55f, 0.42f, 1.0f)  // violet wash
        }
        private fun set(i: Int, r: Float, g: Float, b: Float) { PR[i] = r; PG[i] = g; PB[i] = b }
    }

    private val buf = Array(PAL * 4 * 6) { FloatArray(384) }
    private val cnt = IntArray(PAL * 4 * 6)
    lateinit var sink: DcSink
    var gain = 1f

    fun add(x: Float, y: Float, size: Float, pal: Int, a: Float) {
        if (a < 0.05f) return
        val si = when {
            size < 1.55f -> 0
            size < 2.1f -> 1
            size < 2.8f -> 2
            else -> 3
        }
        val ai = when {
            a < 0.16f -> 0
            a < 0.30f -> 1
            a < 0.48f -> 2
            a < 0.69f -> 3
            a < 0.90f -> 4
            else -> 5
        }
        val idx = (pal * 4 + si) * 6 + ai
        var n = cnt[idx]
        if (n + 2 > buf[idx].size) { flush(idx); n = 0 }
        buf[idx][n] = x
        buf[idx][n + 1] = y
        cnt[idx] = n + 2
    }

    private fun flush(idx: Int) {
        val n = cnt[idx]
        if (n == 0) return
        val ai = idx % 6
        val si = (idx / 6) % 4
        val pal = idx / 24
        sink.dots(buf[idx], n / 2, SIZES[si], PR[pal], PG[pal], PB[pal], ALPHAS[ai] * gain)
        cnt[idx] = 0
    }

    fun flushAll() { for (i in cnt.indices) flush(i) }
}
