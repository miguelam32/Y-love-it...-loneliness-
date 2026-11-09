package com.agupta07505.smartisland.ui

import com.agupta07505.smartisland.util.PcmAnalyzer
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
}

/** Back lines: the real PCM waveform (zero-cross triggered, slowly smoothed) as glowing neon lines. */
internal fun DeepCanvas.scopeLayer(sink: DcSink, data: FloatArray, level: Float, gain: Float) {
    val act = (0.40f + 0.60f * (level * 1.6f).coerceIn(0f, 1f)) * gain * 0.62f
    val yc = 0.34f
    val amp = 0.50f
    val nb = PcmAnalyzer.SCOPE_B
    for (q in 0 until nb) {
        val xn = q / (nb - 1f)
        ln[q * 2] = px(xn)
        ln[q * 2 + 1] = py(yc + 0.03f + 0.75f * amp * data[PcmAnalyzer.SCOPE_B_OFF + q])
    }
    segs(nb, sink, act, true)
    val na = PcmAnalyzer.SCOPE_N
    for (p in 0 until na) {
        val xn = p / (na - 1f)
        ln[p * 2] = px(xn)
        ln[p * 2 + 1] = py(yc + amp * data[PcmAnalyzer.SCOPE_OFF + p])
    }
    segs(na, sink, act, false)
}

private fun DeepCanvas.segs(n: Int, sink: DcSink, act: Float, echo: Boolean) {
    // polyline points live in ln[0 until 2n]; expand them into n-1 segments (reverse order so the in-place copy is safe)
    var k = n - 2
    while (k >= 0) {
        val x0 = ln[k * 2]; val y0 = ln[k * 2 + 1]; val x1 = ln[k * 2 + 2]; val y1 = ln[k * 2 + 3]
        ln[k * 4] = x0; ln[k * 4 + 1] = y0; ln[k * 4 + 2] = x1; ln[k * 4 + 3] = y1
        k--
    }
    if (echo) {
        sink.lines(ln, n - 1, 5.0f, 0.55f, 0.40f, 1f, 0.10f * act)
        sink.lines(ln, n - 1, 1.6f, 0.62f, 0.45f, 1f, 0.50f * act)
    } else {
        sink.lines(ln, n - 1, 6.0f, 0.30f, 0.80f, 1f, 0.10f * act)
        sink.lines(ln, n - 1, 2.6f, 0.30f, 0.92f, 1f, 0.30f * act)
        sink.lines(ln, n - 1, 1.2f, 0.80f, 0.95f, 1f, min(1f, 0.90f * act))
    }
}
