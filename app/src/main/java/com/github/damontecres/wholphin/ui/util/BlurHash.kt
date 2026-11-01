package com.github.damontecres.wholphin.ui.util

import android.graphics.Bitmap
import android.util.LruCache
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.withSign

/**
 * Turns a BlurHash (a short text the Jellyfin server sends for every picture) into a tiny
 * blurry picture. Benflix shows it while the real poster loads, so rows never look empty.
 *
 * See https://blurha.sh for how the format works.
 */
object BlurHash {
    private const val CHARS =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

    private val cache = LruCache<String, Bitmap>(200)

    /** A small blurry picture for [hash], or null if the hash is broken */
    fun decode(
        hash: String,
        width: Int = 20,
        height: Int = 30,
    ): Bitmap? {
        val key = "$hash:$width:$height"
        cache.get(key)?.let { return it }
        val bitmap = decodeInternal(hash, width, height) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private fun decode83(
        str: String,
        from: Int,
        to: Int,
    ): Int {
        var result = 0
        for (i in from until to) {
            val index = CHARS.indexOf(str[i])
            if (index < 0) return -1
            result = result * 83 + index
        }
        return result
    }

    private fun srgbToLinear(value: Int): Float {
        val v = value / 255f
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        return if (v <= 0.0031308f) {
            (v * 12.92f * 255f + 0.5f).toInt()
        } else {
            ((1.055f * v.pow(1 / 2.4f) - 0.055f) * 255f + 0.5f).toInt()
        }
    }

    private fun signPow(
        value: Float,
        exp: Float,
    ): Float =
        kotlin.math
            .abs(value)
            .pow(exp)
            .withSign(value)

    private fun decodeInternal(
        hash: String,
        width: Int,
        height: Int,
    ): Bitmap? {
        if (hash.length < 6) return null
        val sizeFlag = decode83(hash, 0, 1)
        if (sizeFlag < 0) return null
        val numY = sizeFlag / 9 + 1
        val numX = sizeFlag % 9 + 1
        if (hash.length != 4 + 2 * numX * numY) return null
        val quantMax = decode83(hash, 1, 2)
        if (quantMax < 0) return null
        val maxAc = (quantMax + 1) / 166f
        val colors = Array(numX * numY) { FloatArray(3) }
        val dc = decode83(hash, 2, 6)
        if (dc < 0) return null
        colors[0][0] = srgbToLinear(dc shr 16)
        colors[0][1] = srgbToLinear((dc shr 8) and 255)
        colors[0][2] = srgbToLinear(dc and 255)
        for (i in 1 until numX * numY) {
            val ac = decode83(hash, 4 + i * 2, 6 + i * 2)
            if (ac < 0) return null
            val r = ac / (19 * 19)
            val g = (ac / 19) % 19
            val b = ac % 19
            colors[i][0] = signPow((r - 9) / 9f, 2f) * maxAc
            colors[i][1] = signPow((g - 9) / 9f, 2f) * maxAc
            colors[i][2] = signPow((b - 9) / 9f, 2f) * maxAc
        }
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (j in 0 until numY) {
                    for (i in 0 until numX) {
                        val basis =
                            (cos(PI * x * i / width) * cos(PI * y * j / height)).toFloat()
                        val c = colors[i + j * numX]
                        r += c[0] * basis
                        g += c[1] * basis
                        b += c[2] * basis
                    }
                }
                pixels[x + y * width] =
                    (0xFF shl 24) or (linearToSrgb(r) shl 16) or (linearToSrgb(g) shl 8) or linearToSrgb(b)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
