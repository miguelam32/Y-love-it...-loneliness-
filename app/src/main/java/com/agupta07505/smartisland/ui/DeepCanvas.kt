package com.agupta07505.smartisland.ui

import com.agupta07505.smartisland.util.PcmAnalyzer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure-Canvas port of the DEEP FIELD shader: aurora cloud + dotted terrain + thick tide + needles + beat/impact effects.
 * Used when AGSL is unavailable or fails, and tested on the JVM through [DcSink].
 */
internal class DeepCanvas {
    companion object {
        internal const val NC = 96
        private const val COLS = PcmAnalyzer.COLS
        private const val NB = PcmAnalyzer.NB

        fun gradc(u0: Float): FloatArray {
            val u = u0.coerceIn(0f, 1f)
            fun sm(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
            fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
            val p0 = floatArrayOf(0.10f, 0.85f, 1.00f)
            val p1 = floatArrayOf(0.22f, 0.50f, 1.00f)
            val p2 = floatArrayOf(0.55f, 0.28f, 1.00f)
            val p3 = floatArrayOf(0.95f, 0.35f, 0.90f)
            val o = FloatArray(3)
            for (k in 0 until 3) {
                val a = mix(p0[k], p1[k], sm(0f, 0.33f, u))
                val b = mix(p2[k], p3[k], sm(0.66f, 1f, u))
                o[k] = mix(a, b, sm(0.33f, 0.66f, u))
            }
            return o
        }

        internal fun fr(x: Float): Float = x - floor(x)

        fun hash(a: Float, b: Float): Float {
            var x = fr(a * 0.1031f)
            var y = fr(b * 0.1031f)
            var z = fr(a * 0.1031f)
            val d = x * (y + 33.33f) + y * (z + 33.33f) + z * (x + 33.33f)
            x += d; y += d; z += d
            return fr((x + y) * z)
        }

        internal fun smooth(a: Float, b: Float, x: Float): Float {
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }

    internal val ex = FloatArray(NC)
    internal val ey = FloatArray(NC)
    internal val ebm = FloatArray(NC)
    internal val ebw = FloatArray(NC)
    internal val bnd = FloatArray(NC)
    internal val ln = FloatArray(NC * 4)
    internal val batch = DotBatch()
    internal val gcol = Array(8) { gradc(it / 7f) }

    internal var wp = 1f
    internal var hp = 1f

    internal fun px(x: Float) = x * wp
    internal fun py(y: Float) = (0.5f - 0.5f * y) * hp
    internal fun at(a: FloatArray, x: Float): Float {
        val f = (x * NC - 0.5f).coerceIn(0f, NC - 1.001f)
        val i = f.toInt()
        val t = f - i
        return a[i] * (1f - t) + a[i + 1] * t
    }

    fun draw(sink: DcSink, w: Float, h: Float, data: FloatArray, t: Float, live: Float, quality: Float) {
        wp = w; hp = h
        batch.sink = sink
        batch.gain = live * 0.60f
        val gain = live
        val lowQ = quality < 0.75f

        val bass = data[PcmAnalyzer.AUX_OFF]
        val mid = data[PcmAnalyzer.AUX_OFF + 1]
        val hi = data[PcmAnalyzer.AUX_OFF + 2]
        val beat = data[PcmAnalyzer.AUX_OFF + 3]
        val frac = data[PcmAnalyzer.FRAC_OFF]
        val io = PcmAnalyzer.IMP_OFF
        val e1 = min(data[io] * 1.1f, 1f)
        val e2 = min(data[io + 1] * 1.2f, 1f)
        val icx = data[io + 2]
        val icy = data[io + 3]
        val age = data[io + 4]
        val seed = data[io + 5]
        val strength = data[io + 6]

        // ---- per-column envelope tables (same math as the shader's envAt/bandAt) ----
        for (c in 0 until NC) {
            val x = (c + 0.5f) / NC
            val cx = x * 39f + frac
            var cc = 0f; var cws = 0f; var sm = 0f; var ws = 0f; var bm = 0f; var bw = 0f
            for (i in 0 until COLS) {
                val d = cx - i
                val ab = abs(d)
                val bt = data[PcmAnalyzer.BEAT_OFF + i]
                bm = max(bm, bt * max(0f, 1f - ab * 0.8f))
                bw = max(bw, bt * max(0f, 1f - ab / 16f))
                if (ab < 7f) {
                    val lo = data[i * 2]
                    val hh = data[i * 2 + 1]
                    val g1 = exp(-d * d / 2.6f)
                    cc += 0.5f * (lo + hh) * g1; cws += g1
                    val g = exp(-d * d / 12.5f)
                    sm += 0.5f * (hh - lo) * g; ws += g
                }
            }
            ex[c] = cc / max(cws, 0.001f)
            ey[c] = sm / max(ws, 0.001f)
            ebm[c] = bm
            ebw[c] = bw
            val bx = x * 23f
            var v = 0f
            for (i in 0 until NB) v += data[PcmAnalyzer.BANDS_OFF + i] * max(0f, 1f - abs(bx - i))
            bnd[c] = v
        }

        // ---- haze ----
        sink.rect(0f, 0f, w, h, 0.02f, 0.02f, 0.09f, min(1f, (0.55f + 0.7f * bass)) * gain)
        sink.glow(w * 0.25f, h * 0.95f, w * 0.75f, h * 0.95f, 0.10f, 0.13f, 0.52f, min(1f, 0.40f + 0.5f * bass) * gain)
        sink.glow(w * 0.85f, h * 0.25f, w * 0.55f, h * 0.85f, 0.32f, 0.12f, 0.58f, min(1f, 0.30f + 0.5f * mid) * gain)

        // ---- bokeh + stars ----
        for (i in 0 until 7) {
            val hx = hash(i.toFloat(), 3f); val hy = hash(i.toFloat(), 9f); val hs = hash(i.toFloat(), 5f)
            val tw = 0.6f + 0.4f * sin(t * 0.7f + hs * 30f)
            sink.glow(hx * w, hy * h, h * (0.12f + 0.12f * hs), h * (0.12f + 0.12f * hs), 0.55f, 0.60f, 1f, 0.09f * tw * gain)
        }
        for (i in 0 until 46) {
            val hx = hash(i.toFloat(), 21f); val hy = hash(i.toFloat(), 22f); val hs = hash(i.toFloat(), 23f)
            val tw = 0.5f + 0.5f * sin(t * (1f + hs * 4f) + hs * 60f)
            batch.add(hx * w, hy * h, if (hs > 0.85f) 2.0f else 1.3f, 8, 0.45f * tw)
        }

        auroraLayer(sink, t, bass, mid, hi, lowQ, gain)
        terrainLayer(sink, t, bass, mid, quality, lowQ, gain)
        tideLayer(sink, t, bass, mid, hi, beat, lowQ, gain)
        batch.flushAll()
        DeepCanvasFx.beats(sink, w, h, data, t, gain)
        DeepCanvasFx.impact(sink, w, h, data, t, gain)
    }

    internal fun auroraY(x: Float, t: Float, bass: Float, mid: Float, hi: Float, e: Float): Float =
        0.56f + 0.17f * sin(x * 2.9f + t * 0.30f + 0.6f) * (0.55f + 0.9f * bass) +
            0.10f * sin(x * 6.4f - t * 0.52f + 1.9f) * (0.40f + 1.0f * mid) +
            0.045f * sin(x * 13.0f + t * 0.9f) * (0.30f + 2.0f * hi) + e * 0.12f

    internal fun terrH(x: Float, z: Float, t: Float, bass: Float, mid: Float): Float {
        val zf = z + t * 0.05f
        val sw = sin(x * 4.4f + zf * 7.0f + t * 0.25f) * 0.9f +
            0.6f * sin(x * 7.8f - zf * 11.0f - t * 0.35f) +
            0.35f * sin(x * 13.0f + zf * 17.0f)
        return sw * (0.45f + 0.9f * mid + 0.5f * bass)
    }

    internal fun tideY(x: Float, t: Float, bass: Float, mid: Float, hi: Float, e: Float): Float {
        val swell = 0.16f * sin(x * 3.4f + t * 0.55f + 1.3f) * (0.5f + 0.9f * bass) +
            0.08f * sin(x * 6.2f - t * 0.8f) * (0.4f + mid) +
            0.04f * sin(x * 10.0f + t * 1.3f) * (0.3f + 2.0f * hi)
        return -0.06f + swell + e * 0.08f
    }

    internal fun tideHw(ee: Float, bv: Float, beat: Float): Float =
        0.075f + 0.20f * (ee * 1.4f).coerceIn(0f, 1f) + 0.05f * bv + 0.0f * beat
}
