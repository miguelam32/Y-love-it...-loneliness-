package com.agupta07505.smartisland.util

import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Audio source for the island visualizer.
 * Primary: PCM over UDP 127.0.0.1:4215 sent by Loved (tag 0x01 format, 0x02 PCM16LE, 0x03 transition).
 * Fallback: audiofx.Visualizer loudness (needs RECORD_AUDIO) driving a synthetic signal. It is started LAZILY,
 * only when music plays and no UDP PCM arrives for a few seconds, so it never loads the audio chain while Loved streams PCM.
 * The UI pulls frames with [snapshot] (PcmAnalyzer.OUT_SIZE floats).
 */
object SystemAudioCapture {
    private const val TAG = "SystemAudioCapture"
    const val UDP_PORT = 4215
    const val UDP_ENABLED = true
    const val BAR_COUNT = 3
    private const val UDP_STALE_MS = 250L
    private const val VIZ_AFTER_MS = 2500L
    private const val FREEZE_AFTER_MS = 2500L

    @Volatile private var analyzer = PcmAnalyzer(44100)
    @Volatile private var rate = 44100
    @Volatile private var channels = 2
    @Volatile private var lastUdpMs = 0L
    @Volatile private var lastVizMs = 0L
    @Volatile private var lastActiveMs = 0L
    @Volatile private var playing = false
    @Volatile private var udpAlive = false
    private var refCount = 0
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null
    @Volatile private var visualizer: Visualizer? = null
    private var seed = 12345
    private val zeros = ShortArray(20000)

    private val _levels = MutableStateFlow(FloatArray(BAR_COUNT))
    val levels: StateFlow<FloatArray> = _levels

    @Synchronized
    fun start() {
        refCount++
        if (refCount > 1) return
        lastActiveMs = SystemClock.uptimeMillis()
        if (UDP_ENABLED) startUdp()
    }

    @Synchronized
    fun stop() {
        refCount = (refCount - 1).coerceAtLeast(0)
        if (refCount > 0) return
        try { socket?.close() } catch (_: Throwable) {}
        socket = null; thread = null; udpAlive = false
        stopViz()
    }

    fun isAvailable(): Boolean = UDP_ENABLED || try { Visualizer.getMaxCaptureRate() > 0 } catch (_: Throwable) { false }

    /** The UI tells us whether the player is playing: the Visualizer fallback is only worth its cost then. */
    fun setPlaying(p: Boolean) {
        playing = p
        if (p) lastActiveMs = SystemClock.uptimeMillis()
        if (p && !udpAlive) Thread { ensureViz() }.also { it.isDaemon = true; it.name = "island-viz" }.start()
        if (!p) Thread { stopViz() }.also { it.isDaemon = true; it.name = "island-viz" }.start()
    }

    /** Fill [out] (size >= PcmAnalyzer.OUT_SIZE) with the current frame. dt = seconds since last call. */
    fun snapshot(out: FloatArray, dt: Float) {
        val now = SystemClock.uptimeMillis()
        val a = analyzer
        if (now - lastUdpMs > UDP_STALE_MS && now - lastVizMs > UDP_STALE_MS) {
            // no audio: let the analyzer decay to silence, then stop doing any work
            if (now - lastActiveMs < FREEZE_AFTER_MS) {
                val n = (dt * rate).toInt().coerceIn(1, minOf(rate / 10, zeros.size))
                a.feed(zeros, n, 1)
            }
        }
        a.snapshot(out, dt)
    }

    private fun startUdp() {
        thread = Thread {
            try {
                val addr = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
                val s = DatagramSocket(null)
                s.reuseAddress = true
                s.bind(java.net.InetSocketAddress(addr, UDP_PORT))
                s.soTimeout = 500
                socket = s
                udpAlive = true
                val buf = ByteArray(16384)
                val pkt = DatagramPacket(buf, buf.size)
                while (!s.isClosed) {
                    try {
                        pkt.length = buf.size
                        s.receive(pkt)
                        handle(buf, pkt.length)
                    } catch (_: SocketTimeoutException) {
                        val now = SystemClock.uptimeMillis()
                        if (playing && now - lastUdpMs > VIZ_AFTER_MS) ensureViz()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "UDP stopped: ${e.message}")
            }
            udpAlive = false
            if (playing) ensureViz()
        }.also { it.isDaemon = true; it.name = "island-pcm-udp"; it.start() }
    }

    private fun handle(b: ByteArray, len: Int) {
        if (len < 1) return
        when (b[0].toInt()) {
            0x01 -> try {
                val parts = String(b, 1, len - 1, Charsets.UTF_8).trim().split("|")
                val r = parts[0].toInt(); val c = parts[1].toInt()
                if (r in 8000..192000) {
                    if (r != rate) { analyzer = PcmAnalyzer(r); rate = r }
                    channels = c.coerceIn(1, 8)
                }
            } catch (_: Throwable) {}
            0x02 -> {
                val ch = channels
                val frames = (len - 1) / 2 / ch
                if (frames <= 0) return
                val pcm = ShortArray(frames * ch)
                var p = 1
                for (i in pcm.indices) {
                    pcm[i] = ((b[p].toInt() and 0xFF) or (b[p + 1].toInt() shl 8)).toShort()
                    p += 2
                }
                val now = SystemClock.uptimeMillis()
                lastUdpMs = now
                lastActiveMs = now
                if (visualizer != null) stopViz()
                analyzer.feed(pcm, frames, ch)
            }
        }
    }

    @Synchronized
    private fun ensureViz() {
        if (visualizer != null || refCount == 0) return
        try {
            val cr = Visualizer.getMaxCaptureRate()
            if (cr <= 0) return
            visualizer = Visualizer(0).apply {
                captureSize = 256
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, sr: Int) {
                        w ?: return
                        val now = SystemClock.uptimeMillis()
                        if (now - lastUdpMs < 500) return
                        var peak = 0
                        for (x in w) peak = maxOf(peak, abs((x.toInt() and 0xFF) - 128))
                        val lvl = peak / 128f
                        val n = rate / 20
                        val pcm = ShortArray(n)
                        for (i in 0 until n) {
                            seed = seed * 1103515245 + 12345
                            val noise = ((seed shr 16) and 0x7fff) / 16384f - 1f
                            val low = sin(i * 0.07f)
                            pcm[i] = ((noise * 0.5f + low * 0.5f) * lvl * 20000f).toInt().toShort()
                        }
                        lastVizMs = now
                        lastActiveMs = now
                        analyzer.feed(pcm, n, 1)
                    }
                    override fun onFftDataCapture(v: Visualizer?, f: ByteArray?, sr: Int) {}
                }, cr / 2, true, false)
                enabled = true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Visualizer unavailable: ${e.message}")
            visualizer = null
        }
    }

    @Synchronized
    private fun stopViz() {
        val v = visualizer ?: return
        visualizer = null
        try { v.enabled = false; v.release() } catch (_: Throwable) {}
    }
}
