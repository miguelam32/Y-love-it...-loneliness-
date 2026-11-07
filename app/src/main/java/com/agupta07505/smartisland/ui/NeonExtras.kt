package com.agupta07505.smartisland.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.agupta07505.smartisland.util.PcmAnalyzer

internal fun DrawScope.drawFallback(env: FloatArray, bands: FloatArray, beat: Float, live: Float) {
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
