package com.agupta07505.smartisland.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin PCM analyzer (no Android deps). Port of the DEEP FIELD v37 ideas:
 * ERB-spaced biquad bank, spectral-flux beat detector, min/max column history.
 *
 * Output layout of [snapshot]:
 *   [0 until COLS*2)            interleaved min,max per column (oldest -> newest), -1..1
 *   [BANDS_OFF until +NB)       band levels 0..1 (smoothed)
 *   [AUX_OFF + 0..5)            bass, mid, high, beat, level
 */
class PcmAnalyzer(private val rate: Int) {
    companion object {
        const val NB = 24
        const val COLS = 40
        const val COL_MS = 6.0
        const val BANDS_OFF = COLS * 2
        const val AUX_OFF = BANDS_OFF + NB
        const val IMP_OFF = AUX_OFF + 5
        const val BEAT_OFF = IMP_OFF + 7
        const val OUT_SIZE = BEAT_OFF + COLS
    }

    private val fc = DoubleArray(NB)
    private val b0 = DoubleArray(NB); private val b2 = DoubleArray(NB)
    private val a1 = DoubleArray(NB); private val a2 = DoubleArray(NB)
    private val z1 = DoubleArray(NB); private val z2 = DoubleArray(NB)
    private val env = DoubleArray(NB)
    private val atk: Double
    private val rel: Double

    private val colLen = max(1, (rate * COL_MS / 1000.0).toInt())
    private var colCount = 0
    private var cMin = 1f; private var cMax = -1f
    private val ringMin = FloatArray(COLS); private val ringMax = FloatArray(COLS)
    private val ringBeat = FloatArray(COLS)
    private var ringPos = 0

    private var pk = 0.2
    private var refDb = -30.0
    private val prevDb = DoubleArray(NB)
    private val fluxHist = DoubleArray(160)
    private var fluxPos = 0
    private var sinceBeat = 1000.0
    private var beat = 0.0
    private val disp = FloatArray(NB)
    private val target = FloatArray(NB)
    private var level = 0f
    private var pendingImp = 0f
    private var sinceImp = 1000.0
    private var impAge = 99f
    private var impS = 0f
    private var impU = 0.3f
    private var impSeed = 1

    init {
        fun erb(f: Double) = 21.4 * log10(1 + 0.00437 * f)
        fun inv(e: Double) = (10.0.pow(e / 21.4) - 1) / 0.00437
        val lo = erb(55.0); val hi = erb(min(15000.0, rate * 0.45))
        for (i in 0 until NB) {
            val f = inv(lo + (hi - lo) * i / (NB - 1))
            fc[i] = f
            val bw = 24.7 * (0.00437 * f + 1) * 1.6
            val q = f / bw
            val w = 2 * PI * f / rate
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha
            b0[i] = alpha / a0; b2[i] = -alpha / a0
            a1[i] = -2 * cos(w) / a0; a2[i] = (1 - alpha) / a0
        }
        atk = 1 - exp(-1.0 / (rate * 0.002))
        rel = 1 - exp(-1.0 / (rate * 0.045))
    }

    /** pcm = interleaved int16 values already converted to -1..1 floats is NOT required: pass shorts. */
    @Synchronized
    fun feed(pcm: ShortArray, frames: Int, channels: Int) {
        val ch = max(1, channels)
        for (n in 0 until frames) {
            var s = 0f
            for (c in 0 until ch) s += pcm[n * ch + c]
            s = s / ch / 32768f
            // column min/max
            if (s < cMin) cMin = s
            if (s > cMax) cMax = s
            val x = s.toDouble()
            for (i in 0 until NB) {
                val y = b0[i] * x + z1[i]
                z1[i] = -a1[i] * y + z2[i] + 0.0 // direct form II transposed (b1=0)
                z1[i] = z1[i]
                z2[i] = b2[i] * x - a2[i] * y
                val a = abs(y)
                env[i] += (if (a > env[i]) atk else rel) * (a - env[i])
            }
            if (++colCount >= colLen) { endColumn(); colCount = 0 }
        }
    }

    private fun endColumn() {
        if (cMin > cMax) { cMin = 0f; cMax = 0f }
        val wcol = ringPos
        ringMin[ringPos] = cMin; ringMax[ringPos] = cMax; ringBeat[ringPos] = 0f
        ringPos = (ringPos + 1) % COLS
        val amp = max(abs(cMin), abs(cMax)).toDouble()
        pk = if (amp > pk) pk + (amp - pk) * 0.3 else pk * 0.99985
        cMin = 1f; cMax = -1f

        var flux = 0.0; var energy = 0.0
        var mx = -120.0
        for (i in 0 until NB) { val d0 = 20 * log10(env[i] + 1e-6) + i * 0.45; if (d0 > mx) mx = d0 }
        refDb = if (mx > refDb) mx else max(mx, refDb - 0.02)
        refDb = max(refDb, -50.0)
        for (i in 0 until NB) {
            val db = 20 * log10(env[i] + 1e-6) + i * 0.45
            val v = ((db - refDb + 34.0) / 34.0).coerceIn(0.0, 1.0).pow(1.7)
            target[i] = v.toFloat()
            if (i < 14) {
                val d = db - prevDb[i]
                if (d > 0) flux += d * (if (i < 8) 1.0 else 0.6)
            }
            prevDb[i] = db
            energy += v
        }
        level = (energy / NB).toFloat()
        fluxHist[fluxPos] = flux; fluxPos = (fluxPos + 1) % fluxHist.size
        var m = 0.0; for (f in fluxHist) m += f; m /= fluxHist.size
        var sd = 0.0; for (f in fluxHist) sd += (f - m) * (f - m); sd = sqrt(sd / fluxHist.size)
        sinceBeat += COL_MS; sinceImp += COL_MS
        if (flux > m + 1.5 * sd + 6.0 && sinceBeat > 170.0 && level > 0.12f) {
            beat = min(1.0, 0.55 + (flux - m) / 60.0)
            ringBeat[wcol] = beat.toFloat()
            if (beat > 0.76 && sinceImp > 450.0) { pendingImp = beat.toFloat(); sinceImp = 0.0 }
            sinceBeat = 0.0
        }
        beat *= exp(-COL_MS / 170.0)
    }

    /** dt in seconds since last snapshot. out.size must be >= OUT_SIZE. */
    @Synchronized
    fun snapshot(out: FloatArray, dt: Float) {
        val g = (0.9 / max(0.12, pk)).toFloat().coerceAtMost(6f)
        for (k in 0 until COLS) {
            val idx = (ringPos + k) % COLS
            out[k * 2] = (ringMin[idx] * g).coerceIn(-1f, 1f)
            out[k * 2 + 1] = (ringMax[idx] * g).coerceIn(-1f, 1f)
            out[BEAT_OFF + k] = ringBeat[idx]
        }
        val ka = 1 - exp(-dt / 0.025f); val kr = 1 - exp(-dt / 0.14f)
        for (i in 0 until NB) {
            disp[i] += (target[i] - disp[i]) * (if (target[i] > disp[i]) ka else kr)
            out[BANDS_OFF + i] = disp[i]
        }
        var bs = 0f; for (i in 0 until 5) bs += disp[i]
        var ms = 0f; for (i in 5 until 14) ms += disp[i]
        var hs = 0f; for (i in 14 until NB) hs += disp[i]
        out[AUX_OFF] = bs / 5; out[AUX_OFF + 1] = ms / 9; out[AUX_OFF + 2] = hs / 10
        out[AUX_OFF + 3] = beat.toFloat(); out[AUX_OFF + 4] = level
        if (pendingImp > 0f) {
            impAge = 0f; impS = pendingImp; pendingImp = 0f
            impSeed = impSeed % 97 + 1
            impU = (impU + 0.381966f + 0.08f * ((impSeed * 37 % 100) / 100f)) % 1f
        } else if (impAge < 90f) impAge += dt
        val e1 = impS * exp(-impAge * 18f); val e2 = impS * exp(-impAge * 5.5f)
        out[IMP_OFF] = e1; out[IMP_OFF + 1] = e2
        out[IMP_OFF + 2] = 0.5f + (0.12f + 0.76f * impU - 0.5f) * 1.2f
        out[IMP_OFF + 3] = 0.4f + 0.2f * ((impSeed * 0.618034f) % 1f)
        out[IMP_OFF + 4] = impAge; out[IMP_OFF + 5] = impSeed.toFloat(); out[IMP_OFF + 6] = impS
    }
}
