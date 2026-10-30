package com.agupta07505.smartisland.util

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Real waveform of the system audio mix (Visualizer on session 0). Needs RECORD_AUDIO granted. */
object SystemAudioCapture {
    private const val TAG = "SystemAudioCapture"
    private const val CAPTURE_SIZE = 256
    const val BAR_COUNT = 3

    private var visualizer: Visualizer? = null
    private var refCount = 0
    private val _levels = MutableStateFlow(FloatArray(BAR_COUNT))
    val levels: StateFlow<FloatArray> = _levels

    @Synchronized
    fun start() {
        refCount++
        if (visualizer != null) return
        try {
            val rate = Visualizer.getMaxCaptureRate()
            if (rate <= 0) return
            visualizer = Visualizer(0).apply {
                captureSize = CAPTURE_SIZE
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, sr: Int) {
                            w ?: return
                            _levels.value = bucketize(w)
                        }
                        override fun onFftDataCapture(v: Visualizer?, f: ByteArray?, sr: Int) {}
                    },
                    rate / 2, true, false
                )
                enabled = true
            }
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
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Throwable) {
        } finally {
            visualizer = null
            _levels.value = FloatArray(BAR_COUNT)
        }
    }

    fun isAvailable(): Boolean = try { Visualizer.getMaxCaptureRate() > 0 } catch (_: Throwable) { false }

    private fun bucketize(w: ByteArray): FloatArray {
        val size = w.size / BAR_COUNT
        if (size <= 0) return FloatArray(BAR_COUNT)
        return FloatArray(BAR_COUNT) { bar ->
            var peak = 0
            for (i in bar * size until (bar + 1) * size) {
                val s = abs((w[i].toInt() and 0xFF) - 128)
                if (s > peak) peak = s
            }
            (peak / 128f).coerceIn(0f, 1f)
        }
    }
}
