package com.agupta07505.smartisland.ui

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val NC = DeepCanvas.NC
private fun hash(a: Float, b: Float): Float = DeepCanvas.hash(a, b)

/** Back layer: violet/cyan particle cloud + thin PCM line. */
internal fun DeepCanvas.auroraLayer(sink: DcSink, t: Float, bass: Float, mid: Float, hi: Float, lowQ: Boolean, gain: Float) {
    val w = wp
    val h = hp
        val cw = h * 0.040f
        val n = w / cw
        val drift = t * 0.9f
        val c0 = floor(drift)
        val jn = if (lowQ) 4 else 7
        var ci = c0 - 1f
        val cEnd = c0 + n + 2f
        while (ci < cEnd) {
            for (j in 0 until jn) {
                val h1 = hash(ci, j + 31f)
                val h2 = hash(ci + 3.7f, j + 31f + 3.7f)
                val h3 = hash(ci + 8.9f, j + 31f + 8.9f)
                val xn = (ci + h1 - drift) / n
                if (xn < 0f || xn > 1f) continue
                val e = at(ex, xn); val ee = at(ey, xn); val bv = at(bnd, xn)
                val ya = auroraY(xn, t, bass, mid, hi, e)
                val sg = 0.11f + 0.20f * (ee * 1.6f).coerceIn(0f, 1f) + 0.10f * bv
                val tk = h2 + h3 - 1f
                val y = ya + tk * sg * (1f + 0.35f * sin(t * (0.5f + h1) + h2 * 30f))
                val b = 0.25f + 1.9f * h1 * h1 * h1
                val tw = 0.65f + 0.35f * sin(t * (2f + 5f * h3) + h2 * 40f)
                val dens = 1f - 0.55f * abs(tk)
                val lvl = 0.55f + 0.9f * bass + 0.7f * mid + 0.6f * bv
                val a = min(1f, b * tw * dens * 0.62f * lvl * 0.85f)
                val r = 0.55f + 0.9f * h3 * h3
                val pal = (((xn * 0.95f + 0.04f * sin(t * 0.2f)).coerceIn(0f, 1f)) * 7f + 0.5f).toInt().coerceIn(0, 7)
                batch.add(px(xn), py(y), r * 2.1f, pal, a)
                if (h1 > 0.62f) batch.add(px(xn), py(y), 1.4f, 8, min(1f, h1 * h1 * h1 * h1 * tw * 0.9f * lvl))
            }
            ci += 1f
        }
        // soft glow ribbon under the cloud
        for (m in 0 until 8) {
            val xn = (m + 0.5f) / 8f
            val e = at(ex, xn); val bv = at(bnd, xn)
            val ya = auroraY(xn, t, bass, mid, hi, e)
            val g = gcol[(xn * 7f + 0.5f).toInt().coerceIn(0, 7)]
            sink.glow(px(xn), py(ya), w / 8f * 1.2f, h * 0.20f, g[0], g[1], g[2], min(1f, 0.13f * (0.55f + 0.9f * bass + 0.7f * mid + 0.6f * bv)) * gain)
        }
        // thin PCM line
        var s = 0
        for (c in 0 until NC - 1) {
            val xa = (c + 0.5f) / NC; val xb = (c + 1.5f) / NC
            ln[s] = px(xa); ln[s + 1] = py(auroraY(xa, t, bass, mid, hi, ex[c]) + 0.03f + ex[c] * 0.20f)
            ln[s + 2] = px(xb); ln[s + 3] = py(auroraY(xb, t, bass, mid, hi, ex[c + 1]) + 0.03f + ex[c + 1] * 0.20f)
            s += 4
        }
        sink.lines(ln, (NC - 1), 3.4f, 0.55f, 0.65f, 1f, 0.10f * gain)
        sink.lines(ln, (NC - 1), 1.1f, 0.86f, 0.92f, 1f, min(1f, 0.55f + 0.5f * hi) * gain)
}
