package com.github.damontecres.wholphin.services

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.palette.graphics.Palette
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.ui.util.BlurHash
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores state for the backdrop of the app shown on non-full screen pages
 *
 * This is usually the backdrop of the currently focused media
 */
@Singleton
@OptIn(FlowPreview::class)
class BackdropService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val imageUrlService: ImageUrlService,
        private val preferences: DataStore<AppPreferences>,
    ) {
        private val extractedColorCache = LruCache<String, ExtractedColors>(50)

        private val _backdropFlow = MutableStateFlow<BackdropResult>(BackdropResult.NONE)
        val backdropFlow = _backdropFlow

        /**
         * Update the backdrop to use the specified item
         */
        suspend fun submit(
            item: BaseItem,
            strongGlow: Boolean = false,
        ) = withContext(WholphinDispatchers.IO) {
            val imageUrl =
                if (item.type == BaseItemKind.GENRE) {
                    item.imageUrlOverride
                } else {
                    imageUrlService.getItemImageUrl(item, ImageType.BACKDROP)
                }
            val glow = livingColour(item, if (strongGlow) DETAILS_GLOW_ALPHA else GLOW_ALPHA)
            submit(item.id.toString(), imageUrl, glow)
        }

        /**
         * Update the backdrop to use the specified discovered item
         */
        suspend fun submit(item: DiscoverItem) = submit("discover_${item.id}", item.backDropUrl)

        /**
         * Update the backdrop to use the specified URL
         */
        suspend fun submit(
            itemId: String,
            imageUrl: String?,
            glow: ExtractedColors? = null,
        ) = withContext(WholphinDispatchers.IO) {
            val current = backdropFlow.firstOrNull()
            // A new picture, or the same picture with a different glow (Home's soft glow, then the details page's stronger one)
            val newGlow = glow != null && current?.primaryColor != glow.primary
            if (current?.imageUrl != imageUrl || imageUrl == null || newGlow) {
                _backdropFlow.update {
                    it.copy(
                        itemId = itemId,
                        imageUrl = null,
                    )
                }
                extractColors(itemId, imageUrl, glow)
            }
        }

        /**
         * Remove the backdrop, such as when switching pages
         */
        suspend fun clearBackdrop() {
            _backdropFlow.update {
                BackdropResult.NONE
            }
        }

        private suspend fun extractColors(
            itemId: String,
            imageUrl: String?,
            glow: ExtractedColors?,
        ) {
            // Living Colour: the screen glows softly in the colours of the poster you're on. The colours come
            // from the poster's BlurHash (already sent by the server), so no picture is downloaded or scanned.
            // A short wait still stops the picture flickering while scrolling fast.
            delay(BACKDROP_SETTLE_MS)
            val colors: ExtractedColors? = glow ?: ExtractedColors.DEFAULT
            _backdropFlow.update {
                if (it.itemId == itemId) {
                    BackdropResult(
                        itemId = itemId,
                        imageUrl = imageUrl,
                        primaryColor = colors?.primary ?: it.primaryColor,
                        secondaryColor = colors?.secondary ?: it.secondaryColor,
                        tertiaryColor = colors?.tertiary ?: it.tertiaryColor,
                    )
                } else {
                    it
                }
            }
        }

        suspend fun extractColorsFromBackdrop(imageUrl: String?): ExtractedColors? =
            withContext(WholphinDispatchers.IO) {
                if (imageUrl.isNullOrBlank()) {
                    return@withContext null
                }
                extractedColorCache.get(imageUrl)?.let {
                    return@withContext it
                }

                try {
                    val loader = context.imageLoader
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(imageUrl)
                            .allowHardware(false)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .build()

                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val drawable = result.image.asDrawable(context.resources)
                        val bitmap = drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
                        extractColorsFromBitmap(bitmap).also {
                            extractedColorCache.put(imageUrl, it)
                        }
                    } else {
                        ExtractedColors.DEFAULT
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error extracting colors from URL: $imageUrl")
                    ExtractedColors.DEFAULT
                }
            }

        /**
         * Helper function to determine if a color is "cool" (blue/purple/green) vs "warm" (red/orange/yellow)
         *
         * Cool colors have more blue/green than red
         */
        private val Palette.Swatch.coolColor: Boolean
            get() {
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                return b > r && (b + g) > (r * 1.5f)
            }

        private fun toColor(
            swatch: Palette.Swatch?,
            alpha: Float,
        ): Color = swatch?.rgb?.let(::Color)?.copy(alpha = alpha) ?: Color.Transparent

        /**
         * Extracts colors from a bitmap using Android's Palette API.
         *
         * - Primary (Bottom-Right): darkVibrant -> darkMuted -> default
         * - Secondary (Top-Left): Smart selection based on color temperature (prefers cool colors)
         * - Tertiary (Top-Right): vibrant -> lightVibrant -> default
         *
         * @param bitmap The bitmap to extract colors from
         * @return ExtractedColors containing primary, secondary, and tertiary colors
         */
        private suspend fun extractColorsFromBitmap(bitmap: Bitmap): ExtractedColors =
            try {
                val palette = Palette.from(bitmap).generate()

                val vibrant = palette.vibrantSwatch
                val darkVibrant = palette.darkVibrantSwatch
                val lightVibrant = palette.lightVibrantSwatch
                val muted = palette.mutedSwatch
                val darkMuted = palette.darkMutedSwatch
                val lightMuted = palette.lightMutedSwatch
                val dominant = palette.dominantSwatch

                // Primary (Bottom-Right)
                val primaryColor = toColor(darkVibrant ?: darkMuted, .4f)

                // Secondary (Top-Left): Smart selection based on color properties
                // If Vibrant is cool (blue/purple), use it. If Vibrant is warm (yellow/orange) and Muted is cool, use Muted.
                // This ensures we get cool tones (blue/purple) for top-left when available
                val secondaryColor =
                    when {
                        vibrant != null && vibrant.coolColor -> vibrant
                        muted != null && muted.coolColor -> muted
                        vibrant != null -> vibrant
                        muted != null -> muted
                        else -> null
                    }.let { toColor(it, .4f) }

                // Tertiary (Top-Right under image)
                val tertiaryColor = toColor(vibrant ?: lightVibrant, .35f)

                Timber.v(
                    "Colors extracted: primary=%s, secondary=%s, tertiary=%s",
                    primaryColor,
                    secondaryColor,
                    tertiaryColor,
                )
                ExtractedColors(primaryColor, secondaryColor, tertiaryColor)
            } catch (e: Exception) {
                Timber.e(e, "Error extracting palette colors")
                ExtractedColors.DEFAULT
            }

        /**
         * Living Colour: three soft glow colours from the poster's BlurHash (its tiny blurry preview).
         * Top left takes the poster's top colour, bottom right its bottom colour, top right its average.
         */
        private fun livingColour(
            item: BaseItem,
            strength: Float,
        ): ExtractedColors? {
            val hashes = item.data.imageBlurHashes ?: return null
            val hash =
                (hashes[ImageType.PRIMARY] ?: hashes[ImageType.THUMB] ?: hashes[ImageType.BACKDROP])
                    ?.values
                    ?.firstOrNull() ?: return null
            val bitmap = BlurHash.decode(hash, 3, 3) ?: return null

            fun glow(
                x: Int,
                y: Int,
                alpha: Float,
            ) = Color(bitmap.getPixel(x, y)).boosted().copy(alpha = alpha)
            return ExtractedColors(
                primary = glow(2, 2, strength),
                secondary = glow(0, 0, strength),
                tertiary = glow(1, 1, strength * 0.8f),
            )
        }

        fun clearColorCache() {
            extractedColorCache.evictAll()
        }
    }

/**
 * The result from determining the backdrop URL and extracted colors for the dynamic backdrop
 */
data class BackdropResult(
    val itemId: String?,
    val imageUrl: String?,
    val primaryColor: Color = Color.Unspecified,
    val secondaryColor: Color = Color.Unspecified,
    val tertiaryColor: Color = Color.Unspecified,
) {
    val hasColors: Boolean =
        primaryColor.isSpecified ||
            secondaryColor.isSpecified ||
            tertiaryColor.isSpecified

    companion object {
        val NONE =
            BackdropResult(
                null,
                null,
            )
    }
}

/**
 * The colors extracted from an image
 */
data class ExtractedColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
) {
    companion object {
        val DEFAULT = ExtractedColors(Color.Unspecified, Color.Unspecified, Color.Unspecified)
    }
}

/** Living Colour: how strong the glow is (soft, so text stays easy to read) */
private const val GLOW_ALPHA = 0.32f

/** Poster colour page (Look Book pick P1): details pages are tinted more strongly in the poster's colours */
private const val DETAILS_GLOW_ALPHA = 0.5f

/** Posters are often dull or dark, so brighten and colour up the glow a little */
private fun Color.boosted(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    hsv[1] = (hsv[1] * 1.3f).coerceIn(0f, 1f)
    hsv[2] = hsv[2].coerceIn(0.45f, 0.85f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** How long the background waits before changing, so fast scrolling doesn't flicker */
private const val BACKDROP_SETTLE_MS = 150L
