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
    height: Dp = 32.dp
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
    var tick by remember { mutableLongStateOf(0L) }
    var t0 by remember { mutableLongStateOf(0L) }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { makeShader() }.getOrNull() else null
    }
    val failed = remember { BoolBox(false) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { n ->
                val dt = if (last == 0L) 0.016f else ((n - last) / 1e9f).coerceIn(0.001f, 0.1f)
                last = n
                if (t0 == 0L) t0 = n
                SystemAudioCapture.snapshot(data, dt)
                tick = n
            }
        }
    }

    Canvas(modifier.size(width, height)) {
        val n = tick
        System.arraycopy(data, 0, env, 0, env.size)
        System.arraycopy(data, PcmAnalyzer.BANDS_OFF, bands, 0, bands.size)
        System.arraycopy(data, PcmAnalyzer.BEAT_OFF, beats, 0, beats.size)
        val time = (n - t0) / 1e9f
        var done = false
        if (shader != null && !failed.value && Build.VERSION.SDK_INT >= 33) {
            try {
                drawShader(shader, data, env, bands, beats, time, live)
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
private fun makeShader(): RuntimeShader = RuntimeShader(DEEP_AGSL)

@RequiresApi(33)
private fun DrawScope.drawShader(
    rs: RuntimeShader, data: FloatArray, env: FloatArray, bands: FloatArray, beats: FloatArray, time: Float, live: Float
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

private const val DEEP_AGSL = """
uniform float2 uRes;
uniform float2 uT;
uniform float4 uAux;
uniform float2 uEnv[40];
uniform float uBand[24];
uniform float uBeat[40];

float3 pal(float t) {
    t = 1.0 - abs(fract(t * 0.5) * 2.0 - 1.0);
    float3 a = float3(0.10, 0.95, 1.00);
    float3 b = float3(0.22, 0.42, 1.00);
    float3 c = float3(0.68, 0.28, 1.00);
    float3 d = float3(1.00, 0.22, 0.78);
    float3 r = mix(a, b, smoothstep(0.0, 0.34, t));
    r = mix(r, c, smoothstep(0.30, 0.64, t));
    r = mix(r, d, smoothstep(0.60, 0.95, t));
    return r;
}

float hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

float3 ribbon(float x, float y) {
    float lo = 0.0;
    float hi = 0.0;
    float bm = 0.0;
    float cx = x * 39.0;
    for (int i = 0; i < 40; i++) {
        float w = max(0.0, 1.0 - abs(cx - float(i)));
        lo += uEnv[i].x * w;
        hi += uEnv[i].y * w;
        bm = max(bm, uBeat[i] * max(0.0, 1.0 - abs(cx - float(i)) * 0.8));
    }
    float taper = pow(clamp(sin(3.14159 * clamp(x, 0.0, 1.0)), 0.0, 1.0), 0.6);
    float c = 0.5 * (lo + hi) * 0.55;
    float h = max(0.5 * (hi - lo), 0.05) * 0.78 * taper;
    float px = 1.0 / uRes.y;
    float3 acc = float3(0.0);
    for (int k = 0; k < 7; k++) {
        float fk = float(k);
        float t = fk / 6.0 * 2.0 - 1.0;
        float rip = sin(x * 24.0 + uT.x * 3.4 + fk * 1.7) * 0.10 * (0.25 + uAux.y * 1.2);
        float yk = c * taper + t * h * (1.0 + rip * 2.0) + rip * 0.12 * taper;
        float d = (y - yk) / (px * 2.0);
        float core = exp(-d * d * 1.1);
        float glow = exp(-d * d * 0.05) * 0.22;
        float3 col = pal(x * 0.55 + (t * 0.5 + 0.5) * 0.35 + uT.x * 0.04 + uAux.x * 0.15);
        float outer = step(0.99, abs(t));
        float dxp = (fract(x * 13.0 + 0.5) - 0.5) * uRes.x / 13.0;
        float node = exp(-(dxp * dxp + d * d) * 0.8) * outer * 0.9;
        acc += col * (core * 0.95 + glow) + float3(0.8, 0.95, 1.0) * node;
    }
    float span = h + 0.25;
    float vb = smoothstep(span, span * 0.5, abs(y - c * taper));
    float mk = bm * bm * vb;
    acc += float3(1.0, 0.92, 0.75) * mk * 0.9;
    float dm = (y - c * taper) / (px * 2.0);
    acc += float3(1.0, 1.0, 1.0) * bm * exp(-dm * dm * 0.35) * 0.8;
    return acc;
}

half4 main(float2 fc) {
    float2 uv = fc / uRes;
    float x = uv.x;
    float y = (0.5 - uv.y) * 2.0;
    float beat = uAux.w;
    float off = 0.003 + beat * 0.010;
    float gl = step(0.5, beat) * beat;
    float sl = floor(uv.y * 9.0);
    float xg = x + (hash(float2(sl, floor(uT.x * 24.0))) - 0.5) * 0.07 * gl;
    float3 rc = ribbon(xg + off, y);
    float3 gc = ribbon(xg, y);
    float3 bc = ribbon(xg - off, y);
    float3 col = float3(rc.x, gc.y, bc.z);

    float bandV = 0.0;
    float bx = x * 23.0;
    for (int i = 0; i < 24; i++) {
        float w = max(0.0, 1.0 - abs(bx - float(i)));
        bandV += uBand[i] * w;
    }
    float fromBottom = (1.0 - uv.y);
    float cell = abs(fract(x * 24.0) - 0.5);
    float barMask = smoothstep(0.5, 0.30, cell);
    float bh = bandV * 0.55;
    float inside = smoothstep(bh, bh - 0.10, fromBottom);
    float3 bcol = pal(x * 0.6 + 0.1 + fromBottom * 0.25);
    col += bcol * inside * barMask * (0.12 + 0.18 * (1.0 - fromBottom / max(bh, 0.05)));

    float vg = fromBottom + 0.12;
    float gx = abs(fract((x - 0.5) / vg * 1.6 + 0.5) - 0.5);
    float gy = abs(fract(1.0 / vg * 0.7 - uT.x * (0.5 + uAux.x)) - 0.5);
    float grid = (smoothstep(0.05, 0.0, gx * vg) + smoothstep(0.06, 0.0, gy)) * smoothstep(0.42, 0.0, fromBottom);
    col += pal(x * 0.5 + 0.55) * grid * (0.10 + uAux.x * 0.25);
    col *= 0.93 + 0.07 * sin(uv.y * uRes.y * 3.14159);

    float2 gp = floor(float2(uv.x * 64.0, uv.y * 36.0));
    float hs = hash(gp);
    float tw = 0.5 + 0.5 * sin(uT.x * (2.0 + hs * 5.0) + hs * 40.0);
    float near = exp(-pow(y * 1.4, 2.0) * 2.0);
    float glit = step(0.965, hs) * tw * near * (0.5 + beat * 2.2);
    col += pal(hs * 3.0 + x) * glit;

    col += pal(x * 0.5 + uT.x * 0.05) * (beat * beat) * 0.22 * exp(-y * y * 1.5);

    col = float3(1.0) - exp(-col * 1.35);
    float edge = smoothstep(0.0, 0.07, x) * smoothstep(1.0, 0.93, x);
    col *= edge * uT.y;
    float a = clamp(max(col.x, max(col.y, col.z)), 0.0, 1.0);
    return half4(half3(col), half(a));
}
"""
