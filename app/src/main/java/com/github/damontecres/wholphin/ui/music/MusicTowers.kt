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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.services.MusicSpectrum
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.Glowfin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Glowfin (M1): the logo's glow from the bottom row to the top, violet > blue > aqua (no more amber door colours)
 */
private val TowerColors =
    listOf(
        Glowfin.Violet,
        lerp(Glowfin.Violet, Glowfin.Blue, 0.5f),
        Glowfin.Blue,
        lerp(Glowfin.Blue, Glowfin.Aqua, 0.5f),
        Glowfin.Aqua,
    )

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

/**
 * Music player 2026-11-17 (Ben's pick p3-progress): the progress line made of the same logo-square towers.
 * Towers left of [progress] are lit in the glow colours (violet at the bottom up to aqua at the top) and
 * dance to the music; the rest are grey and dance quietly. Every tower always keeps at least one square, so
 * the line reads as a line even when the song is quiet or paused.
 */
@Composable
fun TowerLine(
    progress: Float,
    playing: Boolean,
    towers: Int,
    modifier: Modifier = Modifier,
    dancing: Boolean = true,
    // The small players (pill, strip, mini bar) dance to a made-up beat, so the sound isn't measured on every page
    measure: Boolean = true,
) {
    DisposableEffect(dancing, measure) {
        val on = dancing && measure
        if (on) MusicSpectrum.watching++
        onDispose { if (on) MusicSpectrum.watching-- }
    }
    val shown = remember(towers) { FloatArray(towers) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing, towers, dancing) {
        if (!dancing) return@LaunchedEffect
        var last = 0L
        var settled = false
        while (true) {
            withFrameNanos { now ->
                if (now - last < 32_000_000L) return@withFrameNanos
                last = now
                val fresh = System.currentTimeMillis() - MusicSpectrum.lastSoundAt < 1500
                var any = false
                for (i in 0 until towers) {
                    // Spread the 32 sound bands over the line twice, so the whole line moves, not just one end
                    val band = (i * 2) % towers
                    val target =
                        when {
                            !playing -> 0f
                            fresh -> level(band, towers)
                            else -> fallback(band, towers, now / 1_000_000_000.0)
                        }
                    shown[i] = if (target > shown[i]) shown[i] + (target - shown[i]) * 0.6f else max(target, shown[i] - 0.05f)
                    if (shown[i] > 0.001f) any = true
                }
                settled = !playing && !any
                frame = now
            }
            if (settled) break
        }
    }
    Canvas(modifier = modifier) {
        if (frame < 0L) return@Canvas
        val gapX = size.width / towers * 0.25f
        val w = (size.width - gapX * (towers - 1)) / towers
        // Five rows of thin squares, one glow colour each, violet at the bottom up to aqua at the top (the
        // approved sheet). Tiny lines (the pill, the mini bar) get fewer rows so the squares stay visible.
        val rows = (size.height / 4.dp.toPx()).toInt().coerceIn(2, TowerColors.size)
        val gapY = size.height * 0.08f
        val block = (size.height - gapY * (rows - 1)) / rows
        val corner = CornerRadius(minOf(w, block) * 0.3f, minOf(w, block) * 0.3f)
        val litTowers = (progress.coerceIn(0f, 1f) * towers).toInt()
        val grey = BenflixColors.Muted.copy(alpha = 0.45f)
        for (i in 0 until towers) {
            val x = i * (w + gapX)
            val played = i < litTowers
            // Quiet sounds still reach a few rows up (square root), and the grey part dances a little lower
            val level = sqrt(shown[i].coerceIn(0f, 1f)) * (if (played) 1f else 0.6f)
            val lit =
                kotlin.math
                    .ceil(level * rows)
                    .toInt()
                    .coerceIn(1, rows)
            for (r in 0 until lit) {
                val y = size.height - (r + 1) * block - r * gapY
                val color = if (played) TowerColors[min(TowerColors.size - 1, r * TowerColors.size / rows)] else grey
                drawRoundRect(color = color, topLeft = Offset(x, y), size = Size(w, block), cornerRadius = corner)
            }
        }
    }
}
