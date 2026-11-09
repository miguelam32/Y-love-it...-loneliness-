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

private fun hash(a: Float, b: Float): Float = DeepCanvas.hash(a, b)

private fun vnz(x: Float, y: Float): Float {
    val ix = floor(x); val iy = floor(y)
    var fx = x - ix; var fy = y - iy
    fx = fx * fx * (3f - 2f * fx); fy = fy * fy * (3f - 2f * fy)
    val a = DeepCanvas.hash(ix, iy); val b = DeepCanvas.hash(ix + 1f, iy)
    val c = DeepCanvas.hash(ix, iy + 1f); val d = DeepCanvas.hash(ix + 1f, iy + 1f)
    return (a + (b - a) * fx) * (1f - fy) + (c + (d - c) * fx) * fy
}

private const val HT_BUCKETS = 5
private val htBuf = Array(HT_BUCKETS) { FloatArray(2400) }
private val htCnt = IntArray(HT_BUCKETS)
private val spk = FloatArray(4 * 12)

/**
 * The .html's hard-hit look, drawn UNDER the additive scene: lavender wash, dark halftone dots (bigger where the scene is dark),
 * ink blobs, white spokes and torn shards. Lingers about one second after a strong hit (e3 decays slowly).
 */
internal fun DeepCanvas.halftoneLayer(sink: DcSink, data: FloatArray, t: Float, bass: Float, mid: Float, hi: Float): Float {
    val io = PcmAnalyzer.IMP_OFF
    val age = data[io + 4]
    val strength = data[io + 6]
    if (strength < 0.02f || age > 2.8f) return 0f
    val seed = data[io + 5]
    val e1 = min(strength * exp(-age * 14f), 1f)
    val e2 = min(data[io + 1] * 1.2f, 1f)
    val e3 = strength * exp(-age * 2.2f)
    val k3 = DeepCanvas.smooth(0.15f, 0.7f, e3)
    val ks = min(max(k3, e1 * 0.8f), 0.94f)
    if (ks < 0.02f) return 0f
    val w = wp; val h = hp
    val cxp = data[io + 2] * w
    val cyp = (1f - data[io + 3]) * h
    sink.overRect(0f, 0f, w, h, 0.64f, 0.47f, 1f, ks * 0.92f)

    val s = h * 0.06f
    val p = s * 1.4142f
    val nx = (w / p).toInt() + 2
    val ny = (h / (p * 0.5f)).toInt() + 2
    val lvl = (0.6f + 0.8f * bass + 0.4f * mid).coerceIn(0f, 1.3f)
    for (j in 0 until ny) {
        val y = j * p * 0.5f
        val off = if (j and 1 == 1) p * 0.5f else 0f
        for (i in 0 until nx) {
            val x = i * p + off
            val dx = (x - cxp) / h
            val dy = (y - cyp) / h
            val sd = kotlin.math.sqrt(dx * dx + dy * dy)
            val an = kotlin.math.atan2(-dy, dx)
            val xn = (x / w).coerceIn(0f, 1f)
            val yn = 1f - 2f * y / h
            val e = at(ex, xn); val ee = at(ey, xn); val bv = at(bnd, xn)
            val yT = tideY(xn, t, bass, mid, hi, e, bv)
            val hw = tideHw(ee, bv, 0f)
            val dd = (yn - yT) / (hw + 0.10f)
            val lest = 0.10f + 0.62f * exp(-dd * dd) * lvl + 0.10f * bv
            val rays = vnz(an * 9f + 3f, seed * 0.37f)
            val tone = (0.24f + 1.1f * lest + 0.5f * exp(-sd * 2.4f) * (0.5f + 0.5f * e3) + 0.4f * (rays - 0.5f)).coerceIn(0f, 1f)
            var rad = 0.74f * (1f - tone)
            val br = vnz(an * 11f + seed * 2f, sd * 2.6f) * DeepCanvas.smooth(0.12f, 0.55f, sd)
            if (DeepCanvas.smooth(0.55f, 0.62f, br) * k3 > 0.5f) rad = 0.80f
            if (rad < 0.07f) continue
            val b = ((rad / 0.80f) * HT_BUCKETS - 0.001f).toInt().coerceIn(0, HT_BUCKETS - 1)
            var n = htCnt[b]
            if (n + 2 > htBuf[b].size) {
                sink.overDots(htBuf[b], n / 2, 2f * s * 0.80f * (b + 1) / HT_BUCKETS, 0.012f, 0f, 0.05f, ks)
                n = 0
            }
            htBuf[b][n] = x; htBuf[b][n + 1] = y
            htCnt[b] = n + 2
        }
    }
    for (b in 0 until HT_BUCKETS) {
        if (htCnt[b] > 0) sink.overDots(htBuf[b], htCnt[b] / 2, 2f * s * 0.80f * (b + 1) / HT_BUCKETS, 0.012f, 0f, 0.05f, ks)
        htCnt[b] = 0
    }

    // torn shards (ink / pale lavender strips, displaced)
    if (e2 > 0.05f) {
        val gs = seed * 7f + floor(t * 24f)
        for (r in 0 until 29) for (c in 0 until 11) {
            val hb = hash(r * 11f + c, gs * 7.7f)
            if (hb > 1f - 0.35f * e2) {
                val sh = (hash(r * 11f + c, gs + 2f) - 0.5f) * 0.3f * e2
                val x0 = (c / 11f + sh) * w
                val y0 = r / 29f * h
                if (hash(c.toFloat(), gs + r) > 0.45f) sink.overRect(x0, y0, x0 + w / 11f, y0 + h / 29f, 0.012f, 0f, 0.05f, 0.75f * e2)
                else sink.overRect(x0, y0, x0 + w / 11f, y0 + h / 29f, 0.92f, 0.85f, 1f, 0.60f * e2)
            }
        }
    }

    // white spokes from the impact centre (wedges: wider with distance)
    for (id in 0 until 64) {
        val r1 = hash(id.toFloat(), seed + 1f)
        if (r1 < 0.42f) continue
        val r2 = hash(id.toFloat(), seed + 2f)
        val ang = (id + 0.5f - seed) * 6.2832f / 64f
        val ca = cos(-ang); val sa = sin(-ang)
        val r0 = 0.17f
        val r3 = 0.25f + 0.8f * r1 + 0.15f
        val hwid = 0.04f + 0.18f * r2
        for (q in 0 until 3) {
            val ra = r0 + (r3 - r0) * q / 3f
            val rb = r0 + (r3 - r0) * (q + 1) / 3f
            val rm = 0.5f * (ra + rb)
            val fade = 1f - (q / 3f) * 0.5f
            val o = q * 4
            spk[o] = cxp + ca * ra * h; spk[o + 1] = cyp + sa * ra * h
            spk[o + 2] = cxp + ca * rb * h; spk[o + 3] = cyp + sa * rb * h
            sink.overLines(spk, 1, max(1.0f, 2f * hwid * 0.098f * rm * h), 0.98f, 0.95f, 1f, k3 * fade * 0.75f)
        }
    }
    return ks
}
