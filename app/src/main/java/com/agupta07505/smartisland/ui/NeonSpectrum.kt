package com.agupta07505.smartisland.ui

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.agupta07505.smartisland.util.SystemAudioCapture
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Neon spectrum for the music pill: real FFT bars with peak caps and a mirrored reflection, a glowing
 * waveform line on the baseline, and a slowly drifting purple-to-cyan gradient. All drawn on the GPU canvas.
 */
@Composable
fun NeonSpectrum(isPlaying: Boolean, modifier: Modifier = Modifier) {
    DisposableEffect(isPlaying) {
        if (isPlaying) SystemAudioCapture.start()
        onDispose { if (isPlaying) SystemAudioCapture.stop() }
    }

    val bands = SystemAudioCapture.BANDS
    val points = SystemAudioCapture.WAVE_POINTS
    val level = remember { FloatArray(bands) }
    val peak = remember { FloatArray(bands) }
    val wave = remember { FloatArray(points) }
    var now by remember { mutableLongStateOf(0L) }
    val playing by rememberUpdatedState(isPlaying)

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

    Canvas(modifier = modifier.size(width = 56.dp, height = 32.dp)) {
        val t = now / 1_000_000_000f
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
}
