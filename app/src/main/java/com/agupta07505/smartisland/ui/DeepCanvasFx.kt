package com.agupta07505.smartisland.ui

import com.agupta07505.smartisland.util.PcmAnalyzer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Beat ticks / rings and the whole-pill impact distortion. */
internal object DeepCanvasFx {
    private val ln = FloatArray(4)
    private val gcol = Array(8) { DeepCanvas.gradc(it / 7f) }
    private fun hash(a: Float, b: Float): Float = DeepCanvas.hash(a, b)

    fun beats(sink: DcSink, w: Float, h: Float, data: FloatArray, t: Float, gain: Float) {
        val frac = data[PcmAnalyzer.FRAC_OFF]
        val beat = data[PcmAnalyzer.AUX_OFF + 3]
    for (i in 0 until PcmAnalyzer.COLS) {
        val b = data[PcmAnalyzer.BEAT_OFF + i]
        if (b < 0.15f) continue
        val a = (39f - i) / 39f
        val x = (i - frac) / 39f * w
        val fa = (1f - a) * (1f - a)
        // tick
        sink.rect(x - 1.4f, h * 0.075f, x + 1.4f, h * 0.925f, 1f, 0.92f, 0.80f, min(1f, b * b * 1.1f) * gain)
        // wide halo
        val g = gcol[((x / w) * 0.8f + 0.2f).coerceIn(0f, 1f).let { (it * 7f + 0.5f).toInt().coerceIn(0, 7) }]
        sink.glow(x, h * 0.5f, w * 0.26f, h * 0.9f, g[0], g[1], g[2], min(1f, b * b * 0.55f) * gain)
        if (b > 0.3f) {
            val rr = (0.10f + a * (1.1f + 1.4f * b)) * h * 0.5f
            val wd = (0.05f + 0.16f * a) * h * 0.5f
            sink.ring(x, h * 0.475f, rr, max(1.6f, wd * 1.6f), 0.70f, 0.55f, 1f, min(1f, b * fa * 0.95f) * gain)
            sink.glow(x, h * 0.525f, h * 0.45f, h * 0.45f, 1f, 0.97f, 1f, min(1f, b * fa * fa * 1.3f) * gain)
        }
    }
    if (beat > 0.05f) {
        val g = gcol[((t * 0.03f) % 1f * 7f + 0.5f).toInt().coerceIn(0, 7)]
        sink.glow(w * 0.5f, h * 0.5f, w * 0.7f, h * 0.9f, g[0], g[1], g[2], min(1f, beat * beat * 0.45f) * gain)
    }
    }

    fun impact(sink: DcSink, w: Float, h: Float, data: FloatArray, t: Float, gain: Float) {
        val io = PcmAnalyzer.IMP_OFF
        val e1 = min(data[io] * 1.1f, 1f)
        val e2 = min(data[io + 1] * 1.2f, 1f)
        val icx = data[io + 2]
        val icy = data[io + 3]
        val age = data[io + 4]
        val seed = data[io + 5]
        val strength = data[io + 6]
    if (e1 > 0.01f || e2 > 0.01f) {
        val gs = seed * 7f + floor(t * 20f)
        val cxp = icx * w
        val cyp = (1f - icy) * h
        if (e1 > 0.01f) sink.rect(0f, 0f, w, h, 0.55f, 0.30f, 1f, min(1f, e1 * 0.14f) * gain)
        if (e2 > 0.01f) {
            sink.glow(cxp, cyp, h * 1.9f, h * 1.1f, 0.35f, 0.12f, 0.80f, min(1f, e2 * 0.95f) * gain)
            // expanding ring + shock fronts
            val rad = (0.06f + age * 1.1f) * h
            val k = exp(-age * 3.5f) * strength
            sink.ring(cxp, cyp, rad, (0.05f + 0.05f * age) * h * 1.6f, 0.70f, 0.50f, 1f, min(1f, k * 0.8f) * gain)
            val dd = age * 3.2f * h
            val fk = exp(-age * 2.2f) * strength
            for (q2 in 0 until 2) {
                val sgn = q2 * 2 - 1
                val x = cxp + sgn * dd
                sink.rect(x - 0.26f * h, 0f, x + 0.26f * h, h, 0.75f, 0.55f, 1f, min(1f, fk * 0.28f) * gain)
                sink.rect(x - 0.10f * h, 0f, x + 0.10f * h, h, 0.85f, 0.70f, 1f, min(1f, fk * 0.50f) * gain)
            }
            // glitch blocks (displaced slices)
            for (r in 0 until 11) for (c in 0 until 5) {
                val hb = hash(r * 5f + c, gs * 3.1f)
                if (hb > 1f - 0.55f * e2) {
                    val sh = (hash(r * 5f + c, gs) - 0.5f) * 0.8f * e2
                    val x0 = (c / 5f + sh) * w
                    val y0 = r / 11f * h
                    val hv = hash(c.toFloat(), gs + r)
                    val cr = if (hv > 0.5f) 0.75f else 0.55f
                    val cg = if (hv > 0.5f) 0.62f else 0.50f
                    sink.rect(x0, y0, x0 + w / 5f, y0 + h / 11f, cr, cg, 1f, 0.22f * e2 * gain)
                }
            }
            // streak + flare
            if (e2 > 0.02f) {
                val ca = hash(gs, 9f) * 3.14159f
                val dx = cos(ca); val dy = sin(ca)
                val len = 2.6f * h
                ln[0] = cxp - dx * len; ln[1] = cyp - dy * len; ln[2] = cxp + dx * len; ln[3] = cyp + dy * len
                sink.lines(ln, 1, 0.22f * h, 0.62f, 0.35f, 1f, min(1f, e2 * 0.50f) * gain)
                sink.lines(ln, 1, 0.05f * h, 1f, 0.95f, 1f, min(1f, e2 * 1.0f) * gain)
                sink.glow(cxp, cyp, 0.26f * h, 0.26f * h, 1f, 0.9f, 1f, min(1f, e2 * 1.2f) * gain)
            }
        }
    }
    }
}
