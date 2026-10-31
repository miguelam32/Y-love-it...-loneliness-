package com.agupta07505.smartisland.ui

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agupta07505.smartisland.util.AppLogRecorder
import com.agupta07505.smartisland.util.SystemAudioCapture
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val NEEDLES = 48
private const val RIBBON_PTS = 24

/**
 * AGSL (GPU fragment shader, Android 13+): needle spectrum with glowing tips, two glowing audio ribbons,
 * a perspective floor grid and a fading reflection. Resolution-independent: everything scales with the
 * height of the drawing area, so the pill can be made bigger later and it just gets sharper.
 */
private const val NEON_AGSL = """
uniform float2 iRes;
uniform float iTime;
uniform float iEnergy;
uniform float needles[48];
uniform float wavepts[24];

float3 pal(float t) {
  t = clamp(t, 0.0, 1.0);
  float3 c0 = float3(0.80, 0.22, 1.00);
  float3 c1 = float3(0.30, 0.40, 1.00);
  float3 c2 = float3(0.10, 0.88, 1.00);
  float3 a = mix(c0, c1, clamp(t * 2.0, 0.0, 1.0));
  float3 b = mix(c1, c2, clamp(t * 2.0 - 1.0, 0.0, 1.0));
  return mix(a, b, step(0.5, t));
}

float waveAt(float xn) {
  float x = xn * 23.0;
  float o = 0.0;
  for (int i = 0; i < 24; i++) {
    o += wavepts[i] * clamp(1.0 - abs(x - float(i)), 0.0, 1.0);
  }
  return o;
}

half4 main(float2 fc) {
  float s = iRes.y / 64.0;
  float y0 = iRes.y * 0.64;
  float hn = y0 - iRes.y * 0.05;
  float2 p = fc;
  float xn = p.x / iRes.x;
  float drift = 0.06 * sin(iTime * 0.7);
  float3 palx = pal(xn + drift);
  float3 col = float3(0.0);
  float up = y0 - p.y;
  float dn = p.y - y0;

  for (int k = 0; k < 48; k++) {
    float xk = (float(k) + 0.5) / 48.0 * iRes.x;
    float dx = p.x - xk;
    if (abs(dx) > 6.0 * s) { continue; }
    float hk = needles[k] * hn;
    float dx2 = dx * dx;
    float core = exp(-dx2 / (0.30 * s * s));
    float glow = exp(-dx2 / (4.0 * s * s)) * 0.22;
    float inside = smoothstep(-1.2 * s, 0.8 * s, up) * smoothstep(-1.5 * s, 1.0 * s, hk - up);
    float along = 0.55 + 0.45 * clamp(up / max(hk, 0.001), 0.0, 1.0);
    float a = (core + glow) * inside * along;
    float hr = hk * 0.5;
    float refl = smoothstep(-0.5 * s, 1.0 * s, dn) * smoothstep(-1.5 * s, 1.0 * s, hr - dn)
      * (1.0 - clamp(dn / max(hr, 0.001), 0.0, 1.0)) * 0.30;
    a += (core + glow * 0.5) * refl;
    float ty = p.y - (y0 - hk);
    float tip = exp(-(dx2 + ty * ty) / (1.2 * s * s)) * step(2.0 * s, hk) * 0.8;
    col += (a + tip) * palx;
  }

  float amp = iRes.y * 0.15;
  float e = 0.8 + 0.7 * iEnergy;
  float d1 = abs(p.y - (y0 + amp * waveAt(xn)));
  col += (exp(-(d1 * d1) / (0.72 * s * s)) + exp(-(d1 * d1) / (10.2 * s * s)) * 0.38)
    * pal(xn + drift) * e;
  float d2 = abs(p.y - (y0 + amp * 0.7 * waveAt(1.0 - xn)));
  col += (exp(-(d2 * d2) / (0.72 * s * s)) + exp(-(d2 * d2) / (10.2 * s * s)) * 0.38)
    * pal(xn + 0.18 + drift) * 0.55 * e;

  float tt = dn / (iRes.y - y0);
  if (tt > 0.02) {
    float z = 1.0 / (tt * 0.9 + 0.1);
    float gh = abs(fract(z * 0.55 - iTime * 0.4) - 0.5);
    float lh = smoothstep(0.40, 0.5, gh);
    float xx = (xn - 0.5) / (tt * 0.85 + 0.15);
    float gv = abs(fract(xx * 5.0) - 0.5);
    float lv = smoothstep(0.42, 0.5, gv);
    float g = max(lh, lv) * pow(max(1.0 - tt, 0.0001), 1.2) * smoothstep(0.0, 0.25, tt)
      * 0.16 * (0.7 + iEnergy);
    col += g * palx * 0.9;
  }

  col = float3(1.0) - exp(-col * 1.5);
  col = clamp(col, float3(0.0), float3(1.0));
  float alpha = max(col.r, max(col.g, col.b));
  return half4(half3(col), half(alpha));
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class NeonShader {
    val shader = RuntimeShader(NEON_AGSL)
    val needles = FloatArray(NEEDLES)
    val ribbon = FloatArray(RIBBON_PTS)
}

private fun needleNoise(i: Int): Float {
    val v = sin(i * 12.9898f + 4.1414f) * 43758.547f
    return 0.45f + 0.55f * (v - floor(v))
}

/**
 * Neon audio visualizer for the music pill. GPU shader on Android 13+, plain Canvas otherwise (or if the
 * shader fails). Fed by SystemAudioCapture (PCM over UDP from the music app, Visualizer as fallback).
 */
@SuppressLint("NewApi")
@Composable
fun NeonSpectrum(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    height: Dp = 32.dp
) {
    DisposableEffect(isPlaying) {
        if (isPlaying) SystemAudioCapture.start()
        onDispose { if (isPlaying) SystemAudioCapture.stop() }
    }

    val bands = SystemAudioCapture.BANDS
    val points = SystemAudioCapture.WAVE_POINTS
    val level = remember { FloatArray(bands) }
    val peak = remember { FloatArray(bands) }
    val wave = remember { FloatArray(points) }
    val noise = remember { FloatArray(NEEDLES) { needleNoise(it) } }
    var now by remember { mutableLongStateOf(0L) }
    val playing by rememberUpdatedState(isPlaying)

    val gpu = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { NeonShader() }
                .onFailure { AppLogRecorder.w("NeonSpectrum", "AGSL shader unavailable: ${it.message}") }
                .getOrNull()
        } else {
            null
        }
    }
    val gpuOk = remember { booleanArrayOf(true) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val active = playing || level.any { it > 0.01f } || wave.any { abs(it) > 0.01f }
            if (!active) {
                delay(150)
                last = 0L
                continue
            }
            val t = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((t - last) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
            last = t
            val srcS = SystemAudioCapture.spectrum.value
            val srcW = SystemAudioCapture.wave.value
            val attack = 1f - exp(-dt * 30f)
            val decay = 1f - exp(-dt * 8f)
            for (i in 0 until bands) {
                val target = if (playing) srcS[i] else 0f
                level[i] += (target - level[i]) * (if (target > level[i]) attack else decay)
                peak[i] = max(level[i], peak[i] - dt * 0.8f)
            }
            val follow = 1f - exp(-dt * 22f)
            for (i in 0 until points) {
                val target = if (playing) srcW[i] else 0f
                wave[i] += (target - wave[i]) * follow
            }
            now = t
        }
    }

    Canvas(modifier = modifier.size(width = width, height = height)) {
        val t = now / 1_000_000_000f
        var drawn = false
        if (gpu != null && gpuOk[0]) {
            try {
                var energy = 0f
                for (i in 0 until bands) energy += level[i]
                energy /= bands
                for (k in 0 until NEEDLES) {
                    val pos = k / (NEEDLES - 1f) * (bands - 1)
                    val i0 = pos.toInt().coerceAtMost(bands - 2)
                    val f = pos - i0
                    val v = level[i0] * (1f - f) + level[i0 + 1] * f
                    val shimmer = 1f + 0.08f * sin(t * 9f + k * 1.7f)
                    gpu.needles[k] = (v * noise[k] * shimmer * 1.2f).coerceIn(0f, 1f)
                }
                for (i in 0 until RIBBON_PTS) {
                    gpu.ribbon[i] = ((wave[2 * i] + wave[2 * i + 1]) * 0.8f).coerceIn(-1f, 1f)
                }
                gpu.shader.setFloatUniform("iRes", size.width, size.height)
                gpu.shader.setFloatUniform("iTime", t)
                gpu.shader.setFloatUniform("iEnergy", energy)
                gpu.shader.setFloatUniform("needles", gpu.needles)
                gpu.shader.setFloatUniform("wavepts", gpu.ribbon)
                drawRect(brush = ShaderBrush(gpu.shader))
                drawn = true
            } catch (e: Throwable) {
                gpuOk[0] = false
                AppLogRecorder.w("NeonSpectrum", "AGSL draw failed, using canvas: ${e.message}")
            }
        }
        if (!drawn) drawNeonFallback(level, peak, wave, t, bands, points)
    }
}

private fun DrawScope.drawNeonFallback(
    level: FloatArray,
    peak: FloatArray,
    wave: FloatArray,
    t: Float,
    bands: Int,
    points: Int
) {
    val w = size.width
    val h = size.height
    val base = h * 0.62f
    val up = base - 2.dp.toPx()
    val down = h - base - 1.dp.toPx()
    val gap = 1.6.dp.toPx()
    val barW = (w - gap * (bands - 1)) / bands
    val corner = CornerRadius(barW / 2f)
    val brush = Brush.horizontalGradient(
        colors = listOf(
            Color.hsv(300f + 30f * sin(t * 0.9f), 0.85f, 1f),
            Color.hsv(265f + 25f * sin(t * 0.9f + 1f), 0.85f, 1f),
            Color.hsv(225f + 20f * sin(t * 0.9f + 2f), 0.85f, 1f),
            Color.hsv(190f + 15f * sin(t * 0.9f + 3f), 0.85f, 1f)
        ),
        startX = 0f,
        endX = w
    )

    for (i in 0 until bands) {
        val x = i * (barW + gap)
        val bh = (level[i] * up).coerceAtLeast(1.dp.toPx())
        drawRoundRect(
            brush = brush,
            topLeft = Offset(x, base + 1.dp.toPx()),
            size = Size(barW, level[i] * down * 0.55f),
            cornerRadius = corner,
            alpha = 0.22f
        )
        drawRoundRect(
            brush = brush,
            topLeft = Offset(x - barW * 0.6f, base - bh - 1.dp.toPx()),
            size = Size(barW * 2.2f, bh + 2.dp.toPx()),
            cornerRadius = CornerRadius(barW),
            alpha = 0.16f,
            blendMode = BlendMode.Plus
        )
        drawRoundRect(
            brush = brush,
            topLeft = Offset(x, base - bh),
            size = Size(barW, bh),
            cornerRadius = corner
        )
        if (peak[i] > 0.04f) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(x, base - peak[i] * up - 2.5.dp.toPx()),
                size = Size(barW, 1.2.dp.toPx()),
                cornerRadius = CornerRadius(0.6.dp.toPx())
            )
        }
    }

    val path = Path()
    val amp = 5.dp.toPx()
    for (i in 0 until points) {
        val x = w * i / (points - 1)
        val y = base + (wave[i] * 1.6f).coerceIn(-1f, 1f) * amp
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path = path, brush = brush, alpha = 0.14f, blendMode = BlendMode.Plus,
        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = path, brush = brush, alpha = 0.3f, blendMode = BlendMode.Plus,
        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = path, brush = brush,
        style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}
