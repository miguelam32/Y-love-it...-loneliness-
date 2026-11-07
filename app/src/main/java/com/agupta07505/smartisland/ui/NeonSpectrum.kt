package com.agupta07505.smartisland.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.RuntimeShader
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
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
    val ctx = LocalContext.current
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                makeShader()
            } catch (e: Throwable) {
                GpuDiag.report(ctx, "compile", e)
                null
            }
        } else {
            GpuDiag.report(ctx, "sdk " + Build.VERSION.SDK_INT, null)
            null
        }
    }
    val sink = remember { AndroidDcSink() }
    val dcanvas = remember { DeepCanvas() }
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

    Canvas(if (fill) modifier.clip(RoundedCornerShape(50)) else modifier.size(width, height)) {
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
            } catch (e: Throwable) {
                failed.value = true
                GpuDiag.report(ctx, "draw", e)
            }
        }
        if (!done) {
            try {
                drawIntoCanvas { c ->
                    sink.canvas = c.nativeCanvas
                    dcanvas.draw(sink, size.width, size.height, data, time, live, quality[0])
                    sink.canvas = null
                }
            } catch (_: Throwable) {
                drawFallback(env, bands, data[PcmAnalyzer.AUX_OFF + 3], live)
            }
        }
    }
}

private class BoolBox(var value: Boolean)

/** One-shot (per process) report of why the AGSL shader is not used: short Toast + full text on the clipboard. */
private object GpuDiag {
    private var shown = false

    fun report(ctx: Context, stage: String, e: Throwable?) {
        if (shown) return
        shown = true
        val full = "AGSL " + stage + ": " + (if (e == null) "" else (e.message ?: e.javaClass.simpleName))
        try {
            Toast.makeText(ctx.applicationContext, full.replace('\n', ' ').take(160), Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {
        }
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("smartisland-gpu", full.take(1500)))
        } catch (_: Throwable) {
        }
    }
}

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
