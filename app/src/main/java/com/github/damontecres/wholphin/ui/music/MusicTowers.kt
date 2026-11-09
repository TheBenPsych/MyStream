package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.github.damontecres.wholphin.services.MusicSpectrum
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The door colours, low sounds to high: amber, blue, teal, pink, violet (Ben's pick) */
private val TowerColors =
    listOf(BenflixColors.Movies, BenflixColors.Shows, BenflixColors.LiveTv, BenflixColors.Games, BenflixColors.Music)

/**
 * The music bars (approved v-blocks): towers made of the same soft squares as the Benflix logo, one door
 * colour per group (low sounds amber, high sounds violet), with a faint square floating at each peak.
 * The heights come from the sound inside Benflix's player ([MusicSpectrum]), about 30 times a second.
 */
@Composable
fun MusicTowers(
    playing: Boolean,
    towers: Int,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        MusicSpectrum.watching++
        onDispose { MusicSpectrum.watching-- }
    }
    val shown = remember(towers) { FloatArray(towers) }
    val peaks = remember(towers) { FloatArray(towers) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing, towers) {
        var last = 0L
        var settled = false
        while (true) {
            withFrameNanos { now ->
                // About 30 frames a second is smooth enough and easy on older TVs
                if (now - last < 32_000_000L) return@withFrameNanos
                last = now
                val fresh = System.currentTimeMillis() - MusicSpectrum.lastSoundAt < 1500
                var any = false
                for (i in 0 until towers) {
                    val target =
                        when {
                            !playing -> 0f

                            fresh -> level(i, towers)

                            // No sound reached the bars (some TVs hand the sound straight to the speakers): a gentle made-up bounce
                            else -> fallback(i, towers, now / 1_000_000_000.0)
                        }
                    // Up fast, down slowly
                    shown[i] = if (target > shown[i]) shown[i] + (target - shown[i]) * 0.6f else max(target, shown[i] - 0.045f)
                    peaks[i] = if (shown[i] >= peaks[i]) shown[i] else max(0f, peaks[i] - 0.012f)
                    if (shown[i] > 0.001f || peaks[i] > 0.001f) any = true
                }
                settled = !playing && !any
                frame = now
            }
            if (settled) break
        }
    }
    Canvas(modifier = modifier) {
        // Reading the frame number redraws the towers every frame
        if (frame < 0L) return@Canvas
        val gapX = size.width / towers * 0.22f
        val w = (size.width - gapX * (towers - 1)) / towers
        val block = w
        val gapY = block * 0.18f
        val rows = max(1, ((size.height + gapY) / (block + gapY)).toInt())
        val corner = CornerRadius(block * 0.32f, block * 0.32f)
        for (i in 0 until towers) {
            val color = TowerColors[min(TowerColors.size - 1, i * TowerColors.size / towers)]
            val x = i * (w + gapX)
            val lit = (shown[i] * rows).toInt().coerceIn(if (shown[i] > 0.02f) 1 else 0, rows)
            for (r in 0 until lit) {
                val y = size.height - (r + 1) * block - r * gapY
                drawRoundRect(color = color, topLeft = Offset(x, y), size = Size(w, block), cornerRadius = corner)
            }
            val peakRow = (peaks[i] * rows).toInt()
            if (peakRow >= lit && peakRow in 1 until rows) {
                val y = size.height - (peakRow + 1) * block - peakRow * gapY
                drawRoundRect(color = color.copy(alpha = 0.35f), topLeft = Offset(x, y), size = Size(w, block), cornerRadius = corner)
            }
        }
    }
}

/** The loudness of tower [i] of [n], from the 32 measured bands */
private fun level(
    i: Int,
    n: Int,
): Float {
    val bands = MusicSpectrum.BANDS
    val from = i * bands / n
    val to = max(from + 1, (i + 1) * bands / n)
    var sum = 0f
    for (b in from until to) sum += MusicSpectrum.levels[b]
    return (sum / (to - from)).coerceIn(0f, 1f)
}

private fun fallback(
    i: Int,
    n: Int,
    t: Double,
): Float {
    val beat = Math.pow(max(0.0, sin(t * Math.PI * 2 * 1.05)), 6.0)
    val f = i.toDouble() / n
    val base = 0.55 - 0.35 * f
    val wobble = 0.18 * sin(t * (2.1 + f * 3) + i * 1.7)
    return (base + wobble + beat * (0.35 - 0.25 * f)).toFloat().coerceIn(0f, 1f)
}
