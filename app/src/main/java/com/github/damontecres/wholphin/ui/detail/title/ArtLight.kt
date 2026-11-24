package com.github.damontecres.wholphin.ui.detail.title

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/*
 * Glowfin details pages (Ben 2026-11-22, "Much better, build that too"):
 *  - a logo drawn in a dark colour is drawn in white instead, like the rest of the words
 *  - a very bright picture is turned down a little, and the words get a faint edge
 * Both need to know how light a picture is. It's worked out once from a tiny copy (48 px) off the main
 * thread and remembered, so weak TVs don't notice.
 */

private val lightCache = LruCache<String, Float>(200)

/** How light the picture at [url] is, 0 (black) to 1 (white), counting only the parts you can see. Null until known. */
@Composable
internal fun rememberImageLight(url: String?): Float? {
    val context = LocalContext.current
    var light by remember(url) { mutableStateOf(url?.let { lightCache.get(it) }) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank() || light != null) return@LaunchedEffect
        val value =
            withContext(WholphinDispatchers.IO) {
                try {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(tinyCopyUrl(url))
                            .size(48)
                            .allowHardware(false)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        averageLight(result.image.asDrawable(context.resources).toBitmap(config = Bitmap.Config.ARGB_8888))
                    } else {
                        null
                    }
                } catch (ex: Exception) {
                    Timber.d(ex, "Picture light")
                    null
                }
            }
        if (value != null) {
            lightCache.put(url, value)
            light = value
        }
    }
    return light
}

/**
 * Speed fix (Ben 2026-11-24): ask the server for a tiny copy to measure, instead of downloading and unpacking
 * the full wallpaper or logo a second time on the TV. Only Jellyfin picture links are changed; anything else
 * is measured as before.
 */
internal fun tinyCopyUrl(url: String): String {
    if (!url.contains("/Images/", ignoreCase = true)) return url
    val q = url.indexOf('?')
    val base = if (q < 0) url else url.substring(0, q)
    val kept =
        if (q < 0) {
            emptyList()
        } else {
            url
                .substring(q + 1)
                .split('&')
                .filter { it.isNotEmpty() && it.substringBefore('=').lowercase() !in SIZE_KEYS }
        }
    return base + "?" + (kept + TINY_PARAMS).joinToString("&")
}

private val SIZE_KEYS = setOf("fillheight", "fillwidth", "maxheight", "maxwidth", "width", "height", "quality")
private val TINY_PARAMS = listOf("maxWidth=48", "maxHeight=48", "quality=60")

private fun averageLight(bitmap: Bitmap): Float? {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return null
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    var sum = 0.0
    var weight = 0.0
    for (p in pixels) {
        val a = (p ushr 24 and 0xFF) / 255.0
        if (a < .25) continue
        val r = (p shr 16 and 0xFF) / 255.0
        val g = (p shr 8 and 0xFF) / 255.0
        val b = (p and 0xFF) / 255.0
        sum += (0.2126 * r + 0.7152 * g + 0.0722 * b) * a
        weight += a
    }
    return if (weight > 0) (sum / weight).toFloat() else null
}

/** A logo this dark is drawn in white */
internal const val DARK_LOGO = .3f

/** A picture this bright is turned down a little */
internal const val BRIGHT_ART = .62f
