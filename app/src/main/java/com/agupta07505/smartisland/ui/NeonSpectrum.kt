package com.agupta07505.smartisland.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agupta07505.smartisland.util.PcmAnalyzer
import com.agupta07505.smartisland.util.SystemAudioCapture

/** DEEP FIELD style PCM ribbon + ERB spectrum + beat effects, drawn by an AGSL shader (API 33+), Canvas fallback. */
@Composable
fun NeonSpectrum(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    height: Dp = 32.dp,
    fill: Boolean = false
) {
    DisposableEffect(Unit) {
        SystemAudioCapture.start()
        onDispose { SystemAudioCapture.stop() }
    }
    val live by animateFloatAsState(if (isPlaying) 1f else 0.3f, label = "neonLive")
    val data = remember { FloatArray(PcmAnalyzer.OUT_SIZE) }
    val env = remember { FloatArray(PcmAnalyzer.COLS * 2) }
    val bands = remember { FloatArray(PcmAnalyzer.NB) }
    val beats = remember { FloatArray(PcmAnalyzer.COLS) }
    val quality = remember { FloatArray(1) { 1f } }
    var tick by remember { mutableLongStateOf(0L) }
    var t0 by remember { mutableLongStateOf(0L) }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { makeShader() }.getOrNull() else null
    }
    val failed = remember { BoolBox(false) }

    LaunchedEffect(Unit) {
        var last = 0L
        var avg = 0.0167f
        var slow = 0f
        var fast = 0f
        var downs = 0
        while (true) {
            androidx.compose.runtime.withFrameNanos { n ->
                val dt = if (last == 0L) 0.016f else ((n - last) / 1e9f).coerceIn(0.001f, 0.1f)
                last = n
                if (t0 == 0L) t0 = n
                // adaptive detail: drop quality if the GPU cannot hold ~45 fps, restore slowly when it can
                avg += (dt - avg) * 0.05f
                if (avg > 0.024f) { slow += dt; fast = 0f } else if (avg < 0.0185f) { fast += dt; slow = 0f } else { slow = 0f; fast = 0f }
                if (slow > 1.2f && quality[0] > 0.35f) {
                    quality[0] = if (quality[0] > 0.6f) 0.5f else 0.3f
                    downs++; slow = 0f; avg = 0.0167f
                } else if (fast > 8f && quality[0] < 1f && downs < 2) {
                    quality[0] = if (quality[0] < 0.4f) 0.5f else 1f
                    fast = 0f
                }
                SystemAudioCapture.snapshot(data, dt)
                tick = n
            }
        }
    }

    Canvas(if (fill) modifier else modifier.size(width, height)) {
        val n = tick
        System.arraycopy(data, 0, env, 0, env.size)
        System.arraycopy(data, PcmAnalyzer.BANDS_OFF, bands, 0, bands.size)
        System.arraycopy(data, PcmAnalyzer.BEAT_OFF, beats, 0, beats.size)
        val time = (n - t0) / 1e9f
        var done = false
        if (shader != null && !failed.value && Build.VERSION.SDK_INT >= 33) {
            try {
                drawShader(shader, data, env, bands, beats, time, live, quality[0])
                done = true
            } catch (_: Throwable) {
                failed.value = true
            }
        }
        if (!done) drawFallback(env, bands, data[PcmAnalyzer.AUX_OFF + 3], live)
    }
}

private class BoolBox(var value: Boolean)

@RequiresApi(33)
private fun makeShader(): RuntimeShader = RuntimeShader(DeepShaderA.SRC + DeepShaderB.SRC + DeepShaderC.SRC + DeepShaderD.SRC)

@RequiresApi(33)
private fun DrawScope.drawShader(
    rs: RuntimeShader, data: FloatArray, env: FloatArray, bands: FloatArray, beats: FloatArray, time: Float, live: Float, quality: Float
) {
    rs.setFloatUniform("uRes", size.width, size.height)
    rs.setFloatUniform("uT", time, live)
    rs.setFloatUniform(
        "uAux",
        data[PcmAnalyzer.AUX_OFF], data[PcmAnalyzer.AUX_OFF + 1],
        data[PcmAnalyzer.AUX_OFF + 2], data[PcmAnalyzer.AUX_OFF + 3]
    )
    rs.setFloatUniform("uEnv", env)
    rs.setFloatUniform("uBand", bands)
    rs.setFloatUniform("uBeat", beats)
    rs.setFloatUniform("uFrac", data[PcmAnalyzer.FRAC_OFF])
    rs.setFloatUniform("uQ", quality)
    val io = PcmAnalyzer.IMP_OFF
    rs.setFloatUniform("uImp", data[io], data[io + 1], data[io + 2], data[io + 3])
    rs.setFloatUniform("uK", data[io + 4], data[io + 5], data[io + 6])
    drawRect(brush = ShaderBrush(rs))
}

private fun DrawScope.drawFallback(env: FloatArray, bands: FloatArray, beat: Float, live: Float) {
    val w = size.width; val h = size.height
    val cols = PcmAnalyzer.COLS
    val colors = listOf(Color(0xFF1AF2FF), Color(0xFF386BFF), Color(0xFFAD47FF), Color(0xFFFF38C7))
    // bars
    val bw = w / bands.size
    for (i in bands.indices) {
        val bh = bands[i] * h * 0.5f * live
        drawRect(
            colors[(i * colors.size / bands.size).coerceIn(0, 3)].copy(alpha = 0.35f),
            topLeft = Offset(i * bw + 0.5f, h - bh), size = androidx.compose.ui.geometry.Size(bw - 1f, bh)
        )
    }
    // ribbon filaments
    for (k in 0 until 5) {
        val t = k / 4f * 2f - 1f
        val path = Path()
        for (i in 0 until cols) {
            val lo = env[i * 2]; val hi = env[i * 2 + 1]
            val c = 0.5f * (lo + hi) * 0.55f
            val hh = maxOf(0.5f * (hi - lo), 0.05f) * 0.78f
            val taper = sin(Math.PI.toFloat() * i / (cols - 1)).coerceAtLeast(0f)
            val y = (0.5f - 0.5f * (c * taper + t * hh * taper) * live) * h
            val x = i * w / (cols - 1)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val col = colors[k % 4]
        drawPath(path, col.copy(alpha = 0.25f), style = Stroke(width = 4f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        drawPath(path, col.copy(alpha = 0.9f), style = Stroke(width = 1.4f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    }
    if (beat > 0.2f) drawRect(Color(0xFFAD47FF).copy(alpha = beat * 0.15f))
}

private fun sin(x: Float): Float = kotlin.math.sin(x)

/** Album-disc angle: turns while [isPlaying], and keeps the exact angle it had when the music pauses. */
@Composable
fun rememberDiscRotation(isPlaying: Boolean, degreesPerSecond: Float = 48f): State<Float> {
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var last = 0L
            while (true) {
                androidx.compose.runtime.withFrameNanos { n ->
                    if (last != 0L) {
                        angle.floatValue = (angle.floatValue + (n - last) / 1e9f * degreesPerSecond) % 360f
                    }
                    last = n
                }
            }
        }
    }
    return angle
}
