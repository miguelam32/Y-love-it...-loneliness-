package com.agupta07505.smartisland.util

import android.media.audiofx.Visualizer
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Audio source for the neon visualizer. Priority 1: raw PCM16 sent by the music app over UDP to
 * 127.0.0.1:4215 (tag 0x01 "rate|channels", 0x02 + PCM16 interleaved, 0x03 transition), analysed here with
 * a 2048-point FFT at ~60 Hz. Fallback: android.media.audiofx.Visualizer on session 0 (needs RECORD_AUDIO).
 */
object SystemAudioCapture {
    private const val TAG = "SystemAudioCapture"
    const val BANDS = 14
    const val WAVE_POINTS = 48
    const val UDP_PORT = 4215
    const val UDP_ENABLED = true
    private const val FFT_N = 2048
    private const val HOP = 768
    private const val UDP_STALE_NS = 1_000_000_000L

    private var visualizer: Visualizer? = null
    private var refCount = 0

    private val _spectrum = MutableStateFlow(FloatArray(BANDS))
    private val _wave = MutableStateFlow(FloatArray(WAVE_POINTS))
    val spectrum: StateFlow<FloatArray> = _spectrum
    val wave: StateFlow<FloatArray> = _wave

    private var agc = 8f
    private var frames = 0
    private var silentFrames = 0
    private var loggedSilence = false

    // ---- UDP PCM source ----
    @Volatile private var udpLastNs = 0L
    @Volatile private var udpSocket: DatagramSocket? = null
    private var udpThread: Thread? = null
    private var pcmAgc = 0.5f

    private val window = FloatArray(FFT_N) { (0.5 - 0.5 * cos(2.0 * PI * it / (FFT_N - 1))).toFloat() }
    private val cosT = FloatArray(FFT_N / 2) { cos(-2.0 * PI * it / FFT_N).toFloat() }
    private val sinT = FloatArray(FFT_N / 2) { sin(-2.0 * PI * it / FFT_N).toFloat() }
    private val rev = IntArray(FFT_N) { i ->
        var x = i
        var r = 0
        repeat(11) { r = (r shl 1) or (x and 1); x = x shr 1 }
        r
    }
    private val re = FloatArray(FFT_N)
    private val im = FloatArray(FFT_N)
    private val edges = IntArray(BANDS + 1) { b ->
        (2.0 * (700.0 / 2.0).pow(b.toDouble() / BANDS)).toInt()
    }

    private fun udpActive(): Boolean {
        val t = udpLastNs
        return t != 0L && System.nanoTime() - t < UDP_STALE_NS
    }

    @Synchronized
    fun start() {
        refCount++
        startUdp()
        if (visualizer != null) return
        try {
            val range = Visualizer.getCaptureSizeRange()
            val size = 512.coerceIn(range[0], range[1])
            val rate = Visualizer.getMaxCaptureRate()
            if (rate <= 0) return
            frames = 0
            silentFrames = 0
            loggedSilence = false
            agc = 8f
            visualizer = Visualizer(0).apply {
                captureSize = size
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, sr: Int) {
                            w ?: return
                            if (udpActive()) return
                            onWave(w)
                        }
                        override fun onFftDataCapture(v: Visualizer?, f: ByteArray?, sr: Int) {
                            f ?: return
                            if (udpActive()) return
                            onFft(f)
                        }
                    },
                    rate, true, true
                )
                enabled = true
            }
            AppLogRecorder.i(TAG, "Visualizer started: captureSize=$size maxRate(mHz)=$rate")
        } catch (e: Throwable) {
            Log.w(TAG, "Visualizer unavailable: ${e.message}")
            AppLogRecorder.w(TAG, "Visualizer unavailable: ${e.message}")
            visualizer = null
        }
    }

    @Synchronized
    fun stop() {
        refCount = (refCount - 1).coerceAtLeast(0)
        if (refCount > 0) return
        stopUdp()
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Throwable) {
        } finally {
            visualizer = null
            _spectrum.value = FloatArray(BANDS)
            _wave.value = FloatArray(WAVE_POINTS)
        }
    }

    fun isAvailable(): Boolean = try { Visualizer.getMaxCaptureRate() > 0 } catch (_: Throwable) { false }

    // ------------------------------------------------------------------ UDP PCM

    private fun startUdp() {
        if (udpThread != null) return
        udpThread = Thread({ udpLoop() }, "SmartIsland-pcm-udp").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopUdp() {
        try { udpSocket?.close() } catch (_: Throwable) {}
        udpSocket = null
        udpThread = null
        udpLastNs = 0L
    }

    private fun udpLoop() {
        val socket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), UDP_PORT))
                soTimeout = 500
            }
        } catch (e: Throwable) {
            AppLogRecorder.w(TAG, "UDP $UDP_PORT unavailable: ${e.message}")
            udpThread = null
            return
        }
        udpSocket = socket
        AppLogRecorder.i(TAG, "Listening for PCM on udp://127.0.0.1:$UDP_PORT")
        val buf = ByteArray(65536)
        val pkt = DatagramPacket(buf, buf.size)
        val ring = FloatArray(FFT_N)
        var channels = 2
        var fresh = 0
        var loggedFirst = false
        while (!socket.isClosed) {
            try {
                pkt.length = buf.size
                socket.receive(pkt)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: Throwable) {
                break
            }
            val len = pkt.length
            if (len < 1) continue
            when (buf[0].toInt() and 0xFF) {
                0x01 -> {
                    val parts = String(buf, 1, len - 1, Charsets.UTF_8).trim().split('|')
                    channels = (parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 2).coerceIn(1, 2)
                    java.util.Arrays.fill(ring, 0f)
                    fresh = 0
                    loggedFirst = false
                    AppLogRecorder.i(TAG, "PCM format: ${parts.joinToString("|")} (channels=$channels)")
                }
                0x02 -> {
                    val frameBytes = 2 * channels
                    val n = (len - 1) / frameBytes
                    if (n <= 0) continue
                    val skip = max(0, n - FFT_N)
                    val keep = n - skip
                    System.arraycopy(ring, keep, ring, 0, FFT_N - keep)
                    var peak = 0f
                    for (j in 0 until keep) {
                        val base = 1 + (skip + j) * frameBytes
                        val l = ((buf[base + 1].toInt() shl 8) or (buf[base].toInt() and 0xFF)).toShort().toInt()
                        val m = if (channels == 2) {
                            val r = ((buf[base + 3].toInt() shl 8) or (buf[base + 2].toInt() and 0xFF)).toShort().toInt()
                            (l + r) * 0.5f
                        } else l.toFloat()
                        val v = m / 32768f
                        ring[FFT_N - keep + j] = v
                        if (abs(v) > peak) peak = abs(v)
                    }
                    udpLastNs = System.nanoTime()
                    if (!loggedFirst) {
                        loggedFirst = true
                        AppLogRecorder.i(TAG, "First PCM packet: frames=$n peak=$peak")
                    }
                    fresh += n
                    if (fresh >= HOP) {
                        fresh = 0
                        analyze(ring)
                    }
                }
            }
        }
    }

    private fun analyze(ring: FloatArray) {
        for (i in 0 until FFT_N) {
            val j = rev[i]
            re[j] = ring[i] * window[i]
            im[j] = 0f
        }
        var size = 2
        while (size <= FFT_N) {
            val half = size / 2
            val step = FFT_N / size
            var start = 0
            while (start < FFT_N) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosT[k]
                    val wi = sinT[k]
                    val xr = re[j + half] * wr - im[j + half] * wi
                    val xi = re[j + half] * wi + im[j + half] * wr
                    re[j + half] = re[j] - xr
                    im[j + half] = im[j] - xi
                    re[j] += xr
                    im[j] += xi
                    k += step
                }
                start += size
            }
            size = size shl 1
        }
        val norm = FFT_N / 4f
        val out = FloatArray(BANDS)
        var frameMax = 0f
        for (b in 0 until BANDS) {
            val lo = edges[b]
            val hi = max(lo + 1, edges[b + 1])
            var m = 0f
            for (k in lo until hi) {
                val mag = hypot(re[k], im[k]) / norm
                if (mag > m) m = mag
            }
            val db = 20f * log10(m + 1e-6f)
            val lv = (db + 66f + b * 0.9f) / 56f
            out[b] = lv
            if (lv > frameMax) frameMax = lv
        }
        pcmAgc = max(frameMax, pcmAgc * 0.998f).coerceAtLeast(0.5f)
        for (b in 0 until BANDS) out[b] = (out[b] / pcmAgc).coerceIn(0f, 1f).pow(1.2f)

        val w = FloatArray(WAVE_POINTS)
        val per = 20
        val from = FFT_N - WAVE_POINTS * per
        for (i in 0 until WAVE_POINTS) {
            var s = 0f
            for (k in 0 until per) s += ring[from + i * per + k]
            w[i] = (s / per * 1.5f).coerceIn(-1f, 1f)
        }
        _spectrum.value = out
        _wave.value = w
    }

    // ------------------------------------------------------------------ Visualizer fallback

    private fun onWave(w: ByteArray) {
        val out = FloatArray(WAVE_POINTS)
        val step = w.size.toFloat() / WAVE_POINTS
        for (i in 0 until WAVE_POINTS) {
            val idx = (i * step).toInt().coerceAtMost(w.size - 1)
            out[i] = ((w[idx].toInt() and 0xFF) - 128) / 128f
        }
        var peak = 0
        for (b in w) {
            val a = abs((b.toInt() and 0xFF) - 128)
            if (a > peak) peak = a
        }
        _wave.value = out
        frames++
        if (frames == 1) AppLogRecorder.i(TAG, "First waveform frame, peak=$peak")
        if (peak <= 1) silentFrames++ else silentFrames = 0
        if (silentFrames == 60 && !loggedSilence) {
            loggedSilence = true
            AppLogRecorder.w(TAG, "Capture silent for 60 frames: audio may be paused, or this app/ROM blocks capture")
        }
    }

    private fun onFft(f: ByteArray) {
        val bins = f.size / 2
        if (bins < 8) return
        val mags = FloatArray(bins)
        for (k in 1 until bins) {
            mags[k] = hypot(f[2 * k].toFloat(), f[2 * k + 1].toFloat())
        }
        val maxBin = bins - 1
        val out = FloatArray(BANDS)
        var frameMax = 0f
        for (b in 0 until BANDS) {
            val lo = edge(b, maxBin)
            val hi = max(lo + 1, edge(b + 1, maxBin)).coerceAtMost(bins)
            var m = 0f
            for (k in lo until hi) if (mags[k] > m) m = mags[k]
            m *= 1f + b * 0.06f
            out[b] = m
            if (m > frameMax) frameMax = m
        }
        agc = max(frameMax, agc * 0.985f).coerceAtLeast(4f)
        for (b in 0 until BANDS) out[b] = (out[b] / agc).coerceIn(0f, 1f).pow(0.85f)
        _spectrum.value = out
    }

    private fun edge(b: Int, maxBin: Int): Int =
        Math.pow(maxBin.toDouble(), b.toDouble() / BANDS).toInt().coerceIn(1, maxBin)
}
