package com.github.damontecres.wholphin.ui.music

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Glowfin (Ben 2026-11-18): Music and Books For you tops don't use the cover as a picture behind the page any more
 * (a square album or a tall book can't fill a wide screen). Instead the cover's own two main colours make a soft
 * glow across the top that fades into the dark page, and the real cover sits sharp beside it. The colours are
 * worked out once per cover from a tiny copy of it, and the glow itself is just two colour fades, so it costs
 * almost nothing even on the weak TVs.
 */
data class CoverGlowColors(
    val main: Color,
    val second: Color,
)

private val glowCache = LruCache<String, LongArray>(96)

/**
 * The glow colours for a cover. Dull, grey or black covers (and tiles with no cover) get a faint [fallback]
 * glow, so the top never looks empty. While a cover's colours are being worked out, the last glow stays.
 */
@Composable
fun rememberCoverGlow(
    url: String?,
    fallback: Color,
): CoverGlowColors {
    val context = LocalContext.current
    val plain = remember(fallback) { CoverGlowColors(fallback.copy(alpha = .30f), fallback.copy(alpha = .16f)) }
    var glow by remember { mutableStateOf(plain) }
    LaunchedEffect(url, plain) {
        if (url.isNullOrBlank()) {
            glow = plain
            return@LaunchedEffect
        }
        glowCache.get(url)?.let {
            glow = CoverGlowColors(Color(it[0].toULong()), Color(it[1].toULong()))
            return@LaunchedEffect
        }
        val found =
            withContext(WholphinDispatchers.IO) {
                try {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(url)
                            .size(64)
                            .allowHardware(false)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = result.image.asDrawable(context.resources).toBitmap(config = Bitmap.Config.ARGB_8888)
                        glowFrom(Palette.from(bitmap).generate())
                    } else {
                        null
                    }
                } catch (ex: Exception) {
                    Timber.d(ex, "Cover glow")
                    null
                }
            }
        val colors = found ?: plain
        if (found != null) glowCache.put(url, longArrayOf(colors.main.value.toLong(), colors.second.value.toLong()))
        glow = colors
    }
    return glow
}

/** The cover's strongest colour and a second one, or null when the cover is too dull to glow (grey, black) */
private fun glowFrom(p: Palette): CoverGlowColors? {
    val swatches =
        listOfNotNull(p.vibrantSwatch, p.darkVibrantSwatch, p.mutedSwatch, p.lightVibrantSwatch, p.darkMutedSwatch, p.dominantSwatch)
            .filter { it.hsl[1] >= .18f && it.hsl[2] in .10f..0.92f }
    val main = swatches.firstOrNull() ?: return null
    val second = swatches.firstOrNull { hueGap(it.hsl[0], main.hsl[0]) > 30f } ?: main
    return CoverGlowColors(Color(main.rgb).forPageTop().copy(alpha = .62f), Color(second.rgb).forPageTop().copy(alpha = .44f))
}

private fun hueGap(
    a: Float,
    b: Float,
): Float {
    val d = kotlin.math.abs(a - b) % 360f
    return if (d > 180f) 360f - d else d
}

/**
 * The glow itself: the main colour spreading down from the top right (behind the cover), the second colour from
 * the top left, both fading out well before the bottom. Changing card fades the colours over 0.4 seconds; only
 * the drawing reads them, so the page around it doesn't rebuild.
 */
@Composable
fun CoverGlow(
    colors: CoverGlowColors,
    modifier: Modifier = Modifier,
) {
    val main by animateColorAsState(colors.main, tween(400), label = "glowMain")
    val second by animateColorAsState(colors.second, tween(400), label = "glowSecond")
    Box(
        modifier.drawBehind {
            glowBlob(second, size.width * .28f, size.width * .55f)
            glowBlob(main, size.width * .80f, size.width * .60f)
        },
    )
}

/** One soft oval of colour hanging from the top edge, squashed to fit the glow's height */
private fun DrawScope.glowBlob(
    color: Color,
    centreX: Float,
    radius: Float,
) {
    if (color.alpha <= 0f || radius <= 0f || size.height <= 0f) return
    val top = Offset(centreX, 0f)
    scale(scaleX = 1f, scaleY = size.height / radius, pivot = top) {
        drawCircle(
            brush =
                Brush.radialGradient(
                    0f to color,
                    .55f to color.copy(alpha = color.alpha * .45f),
                    1f to Color.Transparent,
                    center = top,
                    radius = radius,
                ),
            radius = radius,
            center = top,
        )
    }
}
