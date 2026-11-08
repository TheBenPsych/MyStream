package com.github.damontecres.wholphin.ui.games

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Size
import com.github.damontecres.wholphin.games.LocalConsole

/**
 * The shape of game box art (width divided by height). Boxes differ per console: SNES and N64 boxes are
 * wide, NES and Genesis boxes are tall, Game Boy and PlayStation are square. Cards start at their
 * console's usual shape, then take each cover's real shape once it has loaded, so no cover is ever cut off.
 */
internal object BoxArt {
    /** A standard tall game box, used when the console isn't known */
    const val DEFAULT_RATIO = 0.72f

    /** The real shape of each cover seen so far (by its address), kept while the app is open */
    val measured = mutableStateMapOf<String, Float>()

    fun learn(
        url: String?,
        size: Size,
    ) {
        if (url == null || size == Size.Unspecified || size.width <= 0f || size.height <= 0f) return
        val ratio = (size.width / size.height).coerceIn(MIN_RATIO, MAX_RATIO)
        // Small differences aren't worth moving the row for
        val old = measured[url]
        if (old == null || kotlin.math.abs(old - ratio) > 0.03f) measured[url] = ratio
    }

    fun consoleRatio(slug: String): Float =
        when (LocalConsole.forSlug(slug)?.key) {
            "nes" -> 0.72f
            "snes" -> 1.38f
            "n64" -> 1.38f
            "genesis" -> 0.70f
            "gb" -> 1.0f
            "gba" -> 1.0f
            "psx" -> 1.0f
            "atari2600" -> 0.72f
            "atari7800" -> 0.72f
            "lynx" -> 0.80f
            "pce" -> 0.80f
            "ngp" -> 0.80f
            "wonderswan" -> 0.80f
            "virtualboy" -> 0.80f
            "colecovision" -> 0.75f
            "arcade" -> 0.72f
            else -> DEFAULT_RATIO
        }

    private const val MIN_RATIO = 0.55f
    private const val MAX_RATIO = 1.6f
}
