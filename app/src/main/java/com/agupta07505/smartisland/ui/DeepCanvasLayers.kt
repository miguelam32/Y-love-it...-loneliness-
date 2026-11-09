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
private fun smooth(a: Float, b: Float, x: Float): Float = DeepCanvas.smooth(a, b, x)

/** Middle layer: dotted terrain rows + faint contour lines. */
internal fun DeepCanvas.terrainLayer(sink: DcSink, t: Float, bass: Float, mid: Float, quality: Float, lowQ: Boolean, gain: Float) {
    val w = wp
    val h = hp
        val cw = h * 0.030f
        val n = w / cw
        val rows = 12
        for (k in 0 until rows) {
            if (lowQ && k % 2 == 1) continue
            val z = (k + 0.5f) / rows
            val zz = z * z
            val fade = (1f - 0.20f * z) * smooth(0f, 0.10f, z + 0.06f) * (1f - smooth(0.86f, 1.08f, z)) * (1f + 0.7f * (1f - quality))
            var ci = 0
            var meanHH = 0f
            while (ci < n.toInt() + 1) {
                val jn = if (lowQ) 1 else 2
                for (j in 0 until jn) {
                    val h1 = hash(ci + k * 41f, j + 71f)
                    val h2 = hash(ci + k * 41f + 5.1f, j + 71f + 5.1f)
                    val h3 = hash(ci + k * 41f + 9.7f, j + 71f + 9.7f)
                    val xn = (ci + 0.25f + 0.5f * h1) / n
                    if (xn < 0f || xn > 1f) continue
                    val th = terrH(xn, z, t, bass, mid)
                    val bv = at(bnd, xn)
                    val yk = -0.14f - 0.80f * zz + th * (0.030f + 0.070f * z) + bv * 0.10f * z 
                    val hh = (th * 0.28f + 0.45f).coerceIn(0f, 1f)
                    val lit = 0.40f + 1.5f * hh * hh
                    val y = yk + (h2 - 0.5f) * (0.012f + 0.030f * z)
                    val tw = 0.7f + 0.3f * sin(t * (1.5f + 4f * h3) + h2 * 40f)
                    val s = (0.5f + 0.9f * h1 * h1) * tw
                    val a = min(1f, s * lit * fade * 0.85f)
                    val c = hh * 1.3f
                    val pal = if (c > 0.66f) 8 else if (c > 0.33f) 12 else 10
                    batch.add(px(xn), py(y), 1.5f + 0.7f * z + 0.4f * h3, pal, a)
                    if (j == 0) meanHH += hh
                }
                ci++
            }
            // faint contour line of the row
            var s2 = 0
            for (c in 0 until NC - 1) {
                val xa = (c + 0.5f) / NC; val xb = (c + 1.5f) / NC
                val ya = -0.14f - 0.80f * zz + terrH(xa, z, t, bass, mid) * (0.030f + 0.070f * z) + at(bnd, xa) * 0.10f * z 
                val yb = -0.14f - 0.80f * zz + terrH(xb, z, t, bass, mid) * (0.030f + 0.070f * z) + at(bnd, xb) * 0.10f * z 
                ln[s2] = px(xa); ln[s2 + 1] = py(ya); ln[s2 + 2] = px(xb); ln[s2 + 3] = py(yb)
                s2 += 4
            }
            sink.lines(ln, NC - 1, 1.0f, 0.55f, 0.62f, 1f, min(1f, 0.09f * fade * (0.7f + 0.8f * (meanHH / n))) * gain)
        }
}

/** Front layer: thick dotted tide band + glow + needles. */
internal fun DeepCanvas.tideLayer(sink: DcSink, t: Float, bass: Float, mid: Float, hi: Float, beat: Float, lowQ: Boolean, gain: Float) {
    val w = wp
    val h = hp
        val cw = h * 0.028f
        val n = w / cw
        val jn = if (lowQ) 3 else 6
        var ci = 0
        while (ci < n.toInt() + 1) {
            for (j in 0 until jn) {
                val h1 = hash(ci.toFloat(), j + 131f)
                val h2 = hash(ci + 2.3f, j + 131f + 2.3f)
                val h3 = hash(ci + 6.1f, j + 131f + 6.1f)
                val xn = (ci + 0.2f + 0.6f * h1) / n
                if (xn < 0f || xn > 1f) continue
                val e = at(ex, xn); val ee = at(ey, xn); val bv = at(bnd, xn)
                val yT = tideY(xn, t, bass, mid, hi, e, bv)
                val hw = tideHw(ee, bv, beat)
                val yb = yT + hw * (1f - 2f * h2 * h2)
                val tw = 0.75f + 0.25f * sin(t * (2f + 4f * h3) + h2 * 40f)
                val lvl = 0.75f + 0.9f * bass + 0.5f * mid + 0.5f * bv
                val a = min(1f, (0.55f + 0.7f * (1f - h2 * h2)) * tw * 0.75f * lvl * 0.85f)
                batch.add(px(xn), py(yb), (0.65f + 0.55f * h3 * h3) * 2.3f, 9, a)
                if (h1 > 0.55f) batch.add(px(xn), py(yb), 1.5f, 8, min(1f, h1 * h1 * h1 * 1.0f * tw))
            }
            ci++
        }
        for (m in 0 until 12) {
            val xn = (m + 0.5f) / 12f
            val e = at(ex, xn); val ee = at(ey, xn); val bv = at(bnd, xn)
            val yT = tideY(xn, t, bass, mid, hi, e, bv)
            val hw = tideHw(ee, bv, beat)
            val lvl = 0.75f + 0.9f * bass + 0.5f * mid + 0.5f * bv
            val mx = 0.5f + 0.5f * xn
            sink.glow(px(xn), py(yT + hw * 0.55f), w / 12f * 1.5f, (hw + 0.03f) * h * 0.5f * 1.5f,
                0.45f + 0.30f * mx, 0.55f, 1.0f, min(1f, 0.26f * lvl) * gain)
            sink.glow(px(xn), py(yT + hw - 0.30f), w / 12f * 1.6f, 0.42f * h * 0.5f,
                0.17f, 0.22f, 0.62f, min(1f, 0.30f + 0.3f * lvl) * gain)
        }
        // needles (sway, flicker, jump on every kick)
        val nn = 64
        val cwn = w / nn
        for (i in 0 until nn) {
            val hs = hash(i.toFloat(), 3f)
            if (hs <= 0.52f) continue
            val hs2 = hash(i.toFloat(), 11f)
            val xn = (i + 0.5f) / nn
            val bvn = at(bnd, xn)
            val fl = 0.62f + 0.38f * sin(t * (3f + 5f * hs) + hs2 * 40f)
            val len = (0.03f + 1.25f * bvn) * (0.25f + 0.75f * hs2) * fl * (0.45f + bass) + beat * (0.10f + 0.34f * hs2)
            val e = at(ex, xn); val ee = at(ey, xn)
            val yT = tideY(xn, t, bass, mid, hi, e, bvn)
            val hw = tideHw(ee, bvn, beat)
            val top = yT + hw
            val bot = yT - hw
            val lean = 0.95f * sin(t * (0.8f + 1.6f * hs) + hs2 * 20f) * (0.3f + bass + beat)
            val sw = 0.28f * sin(t * (1.1f + 2.4f * hs2) + hs * 50f)
            val x = px(xn)
            val xm = x + (sw + lean * len * 0.5f).coerceIn(-0.42f, 0.42f) * cwn
            val xt = x + (sw + lean * len).coerceIn(-0.42f, 0.42f) * cwn
            ln[0] = x + sw * cwn; ln[1] = py(top); ln[2] = xm; ln[3] = py(top + len * 0.5f)
            sink.lines(ln, 1, 1.0f, 0.74f, 0.90f, 1f, 0.60f * gain)
            ln[0] = xm; ln[1] = py(top + len * 0.5f); ln[2] = xt; ln[3] = py(top + len)
            sink.lines(ln, 1, 1.0f, 0.74f, 0.90f, 1f, 0.25f * gain)
            ln[0] = x; ln[1] = py(bot); ln[2] = x; ln[3] = py(bot - len * 1.6f)
            sink.lines(ln, 1, 1.0f, 0.74f, 0.90f, 1f, 0.16f * gain)
        }
}

/** Glowing crest lines riding the top of the tide (front) and a dimmer cyan one behind it, so the wave peaks read clearly. */
internal fun DeepCanvas.crestLayer(sink: DcSink, t: Float, bass: Float, mid: Float, hi: Float, beat: Float, gain: Float) {
    val lvl = (0.65f + 0.8f * bass + 0.45f * mid).coerceIn(0f, 1.3f)
    for (pass in 0 until 2) {
        for (c in 0 until NC) {
            val xn = (c + 0.5f) / NC
            val e = at(ex, xn); val ee = at(ey, xn); val bv = at(bnd, xn)
            var y: Float
            if (pass == 0) {
                y = tideY((xn + 0.13f) % 1f, t * 0.8f + 2.1f, bass, mid, hi, e, bv) * 0.75f + 0.16f
            } else {
                y = tideY(xn, t, bass, mid, hi, e, bv) + tideHw(ee, bv, beat) + 0.02f
            }
            ln[c * 2] = px(xn); ln[c * 2 + 1] = py(y)
        }
        var k = NC - 2
        while (k >= 0) {
            val x0 = ln[k * 2]; val y0 = ln[k * 2 + 1]; val x1 = ln[k * 2 + 2]; val y1 = ln[k * 2 + 3]
            ln[k * 4] = x0; ln[k * 4 + 1] = y0; ln[k * 4 + 2] = x1; ln[k * 4 + 3] = y1
            k--
        }
        if (pass == 0) {
            sink.lines(ln, NC - 1, 4.0f, 0.25f, 0.80f, 1f, 0.10f * lvl * gain)
            sink.lines(ln, NC - 1, 1.2f, 0.35f, 0.92f, 1f, min(1f, 0.42f * lvl) * gain)
        } else {
            sink.lines(ln, NC - 1, 8.0f, 0.50f, 0.45f, 1f, 0.16f * lvl * gain)
            sink.lines(ln, NC - 1, 3.0f, 0.62f, 0.62f, 1f, 0.35f * lvl * gain)
            sink.lines(ln, NC - 1, 1.5f, 0.92f, 0.94f, 1f, min(1f, 0.85f * lvl) * gain)
        }
    }
}
