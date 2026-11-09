package com.agupta07505.smartisland.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Kick-drum detector (pure Kotlin). A kick is a sharp rise of the SUB-BASS band (about 35-105 Hz), where voices and
 * melodies hardly live. Events whose rise shows up more in the low-mid band (110-320 Hz) are voice/melody onsets and
 * are rejected. Call [feed] for every mono sample (-1..1) and [column] once per analysis column.
 *
 * Tuning (all in the companion): raise REL / MIN_RISE for fewer beats, raise LM_MAX to accept bassier voices.
 */
class KickDetector(private val rate: Int, private val colMs: Double) {
    companion object {
        const val REL = 0.50
        const val MIN_RISE = 0.0035
        const val MIN_RATIO = 1.3
        const val LM_MAX = 2.2
        const val GAP_MS = 240.0
        const val BASE_A = 2
        const val BASE_B = 4
        private const val H = 12
    }

    private class Bq(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        var z1 = 0.0
        var z2 = 0.0
        fun p(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    private fun lp(fc: Double, q: Double): Bq {
        val w = 2 * PI * fc / rate; val al = sin(w) / (2 * q); val c = cos(w); val a0 = 1 + al
        return Bq((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - al) / a0)
    }

    private fun hp(fc: Double, q: Double): Bq {
        val w = 2 * PI * fc / rate; val al = sin(w) / (2 * q); val c = cos(w); val a0 = 1 + al
        return Bq((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - al) / a0)
    }

    private fun bp(fc: Double, q: Double): Bq {
        val w = 2 * PI * fc / rate; val al = sin(w) / (2 * q); val c = cos(w); val a0 = 1 + al
        return Bq(al / a0, 0.0, -al / a0, -2 * c / a0, (1 - al) / a0)
    }

    private val sHp = hp(35.0, 0.707)
    private val sLp1 = lp(105.0, 0.5412)
    private val sLp2 = lp(105.0, 1.3066)
    private val lm = bp(200.0, 0.9)
    private val atk = 1 - exp(-1.0 / (rate * 0.0015))
    private val relS = 1 - exp(-1.0 / (rate * 0.030))
    private var sub = 0.0
    private var low = 0.0
    private val hs = DoubleArray(H)
    private val hl = DoubleArray(H)
    private var pos = 0
    private var pk = 0.0
    private var since = 1000.0
    private var age = 0.0
    private val pkDecay = exp(-colMs / 1000.0 * ln(2.0) / 3.0)

    fun feed(x: Double) {
        val s = abs(sLp2.p(sLp1.p(sHp.p(x))))
        sub += (if (s > sub) atk else relS) * (s - sub)
        val l = abs(lm.p(x))
        low += (if (l > low) atk else relS) * (l - low)
    }

    /** Returns kick strength 0..1 (0 = no kick in this column). */
    fun column(): Double {
        hs[pos] = sub; hl[pos] = low
        var bs = 1e9; var bl = 1e9
        for (k in BASE_A..BASE_B) {
            val i = (pos - k + H * 2) % H
            bs = min(bs, hs[i]); bl = min(bl, hl[i])
        }
        pos = (pos + 1) % H
        since += colMs
        age += colMs
        val rise = max(0.0, sub - bs)
        val lrise = max(0.0, low - bl)
        pk = max(pk * pkDecay, if (age < 400.0) 0.0 else rise)
        var out = 0.0
        if (rise > MIN_RISE && rise >= REL * pk && sub > MIN_RATIO * bs && lrise < LM_MAX * rise && since > GAP_MS && age > 400.0) {
            out = (rise / max(pk, 1e-9)).coerceIn(0.0, 1.0)
            since = 0.0
        }
        return out
    }
}
