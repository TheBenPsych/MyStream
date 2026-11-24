package com.github.damontecres.wholphin.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.LiteMode
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.util.LocalClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalTime

/** Frames are capped at 30 a second: fish move slowly, so it looks as smooth as 60 and costs half */
private const val FRAME_NANOS = 33_000_000L

/**
 * The Coral Reef aquarium screensaver: the day tank, or the night tank from 10 PM to 7 AM,
 * an optional small clock that moves to a new corner every few minutes, and an optional
 * quiet bubbling sound. The tank itself is drawn by [ReefTank].
 */
@Composable
fun ReefScreensaver(
    showClock: Boolean,
    waterSound: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lite = remember { LiteMode.isOn(context) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var night by remember { mutableStateOf(ReefTank.isNightHour(LocalTime.now().hour)) }
    var tank by remember { mutableStateOf<ReefTank?>(null) }
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            night = ReefTank.isNightHour(LocalTime.now().hour)
        }
    }
    LaunchedEffect(size, night) {
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        // Painting the tank takes a moment, so it happens off the main thread
        tank =
            withContext(Dispatchers.Default) {
                try {
                    ReefTank(size.width, size.height, night, lite)
                } catch (e: Throwable) {
                    Timber.w(e, "Could not make the reef tank")
                    null
                }
            }
    }
    LaunchedEffect(tank) {
        if (tank == null) return@LaunchedEffect
        var start = -1L
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) {
                    start = now
                    last = now
                }
                if (now - last >= FRAME_NANOS) {
                    last = now
                    time = (now - start) / 1_000_000_000f
                }
            }
        }
    }
    if (waterSound) {
        DisposableEffect(Unit) {
            val player =
                try {
                    MediaPlayer.create(context, R.raw.aquarium_bubbles)?.apply {
                        isLooping = true
                        setVolume(0.35f, 0.35f)
                        start()
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Could not play the water sound")
                    null
                }
            onDispose { player?.release() }
        }
    }

    Box(
        modifier
            .background(Color.Black)
            .onSizeChanged { size = it },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val current = tank ?: return@Canvas
            val t = time
            drawIntoCanvas { current.draw(it.nativeCanvas, t) }
        }
        if (showClock) {
            MovingClock()
        }
        // The small logo squares in the corner, same as the movie-art screensaver
        BenflixMark(
            size = 26.dp,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = BenflixSpace.tvOuterEdge, top = 32.dp),
        )
    }
}

private val clockCorners = listOf(Alignment.TopEnd, Alignment.BottomEnd, Alignment.BottomStart, Alignment.TopCenter)

/** The small clock moves to a new corner every 5 minutes so it never burns into the screen */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.MovingClock() {
    val timeString by LocalClock.current.timeString
    var corner by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5 * 60_000L)
            corner = (corner + 1) % clockCorners.size
        }
    }
    Text(
        text = timeString,
        fontSize = BenflixType.rowName,
        color = Color.White.copy(alpha = .9f),
        modifier =
            Modifier
                .align(clockCorners[corner])
                .padding(horizontal = BenflixSpace.tvOuterEdge, vertical = 28.dp)
                .background(Glowfin.Ink.copy(alpha = .35f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 2.dp),
    )
}
