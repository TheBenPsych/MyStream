package com.github.damontecres.wholphin.ui.theme

import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.WholphinApplication
import timber.log.Timber
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Easter on the new side menu (approved 2026-11-17, thread "Fit Easter theme to new navigation", direction A
 * "Easter tree ribbon"): a meadow-green rail where the door icons become the Easter tree charms, a painted egg on a
 * ribbon by the icon you're on, a pink satin ribbon swagging across the top of Home with painted eggs swaying under
 * it, and spring grass with tulips and eggs along the bottom. Kids get it too. The pictures come from
 * `node tools/easter/navpictures.js` (res/drawable-nodpi/ea_nav_*.png); the web draws the same in js/holidaynav.js.
 */
internal object EasterNav {
    val Lights =
        listOf(
            Color(0xFFFFB8D2),
            Color(0xFFBFE6FF),
            Color(0xFFFFF1A6),
            Color(0xFFC9F2C0),
            Color(0xFFE3D5FF),
            Color(0xFFFFD9B8),
        )

    /** Hand-picked Easter films for the first big pick on Home (added to the holiday's Home row list) */
    val PickTitles =
        listOf(
            "Hop",
            "Peter Rabbit",
            "Peter Rabbit 2: The Runaway",
            "Rise of the Guardians",
            "The Easter Bunny Is Comin' to Town",
            "It's the Easter Beagle, Charlie Brown",
        )

    private val pictures = HashMap<Int, ImageBitmap?>()

    /** A picture, loaded once and kept (null if it can't be loaded, then nothing is drawn) */
    private fun pic(id: Int): ImageBitmap? =
        synchronized(pictures) {
            pictures.getOrPut(id) {
                try {
                    BitmapFactory.decodeResource(WholphinApplication.instance.resources, id)?.asImageBitmap()
                } catch (ex: Exception) {
                    Timber.w(ex, "Easter nav picture")
                    null
                }
            }
        }

    /** Draws [image] fitted into the box at [left], [top] of [w] x [h], keeping its shape, centred */
    private fun DrawScope.fit(
        image: ImageBitmap,
        left: Float,
        top: Float,
        w: Float,
        h: Float,
        alpha: Float = 1f,
    ) {
        val s = minOf(w / image.width, h / image.height)
        val dw = image.width * s
        val dh = image.height * s
        drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset((left + (w - dw) / 2).roundToInt(), (top + (h - dh) / 2).roundToInt()),
            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
            alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
    }

    private val doorPics =
        mapOf(
            "movies" to R.drawable.ea_nav_movies,
            "shows" to R.drawable.ea_nav_shows,
            "live" to R.drawable.ea_nav_live,
            "games" to R.drawable.ea_nav_games,
            "music" to R.drawable.ea_nav_music,
            "books" to R.drawable.ea_nav_books,
        )
    private val eggPics =
        listOf(
            R.drawable.ea_nav_egg0,
            R.drawable.ea_nav_egg1,
            R.drawable.ea_nav_egg2,
            R.drawable.ea_nav_egg3,
            R.drawable.ea_nav_egg4,
            R.drawable.ea_nav_egg5,
        )

    /**
     * The door icons become the Easter tree charms. They're drawn a little bigger than the usual line icon (the box
     * isn't clipped) so they read from the couch. The charms keep their own colours: a see-through colour (the grey
     * before start-up lights it) only fades them, and the start-up light adds a soft glow behind.
     */
    val icons: Map<String, HolidayIconArt> =
        doorPics.mapValues { (_, id) -> charmIcon(id) }

    private fun charmIcon(id: Int): HolidayIconArt =
        { color, _ ->
            pic(id)?.let { image ->
                val grow = 1.55f
                val w = size.width * grow
                val h = size.height * grow
                val dim = color.alpha < 1f
                fit(image, (size.width - w) / 2, (size.height - h) / 2, w, h, alpha = if (dim) .4f else 1f)
            }
        }

    /** A painted egg on a pink ribbon from a pearl pin, swaying gently by the icon you're on */
    val mark: HolidayArt = { t ->
        pic(R.drawable.ea_nav_mark)?.let { image ->
            val w = size.width * 1.5f
            val h = size.height * 1.5f
            rotate(degrees = 6f * sin(t * 1.4f), pivot = Offset(size.width / 2, 0f)) {
                fit(image, (size.width - w) / 2, 0f, w, h)
            }
        }
    }

    /** The pink satin ribbon swagging across the top of Home, a pearl pin at each high point, a painted egg swaying under each swag */
    val topTrim: HolidayArt = { t ->
        val d = density
        val step = 150f * d
        // Ben 2026-11-17: the greeting now sits top left on the clock's line, so the ribbon stays in a thin band
        // along the very top and the eggs end above the greeting's letters (about 34dp down on TV). Phones put
        // the greeting right at the top of the picture, so there the eggs only hang on the right, past the words.
        val phone = size.width < 600f * d
        val pinY = 3f * d
        val sag = 12f * d
        val n = (size.width / step).toInt() + 1
        val rope = Path()
        rope.moveTo(0f, pinY)
        for (i in 0 until n) {
            val x = i * step
            rope.quadraticTo(x + step / 2, pinY + sag, x + step, pinY)
        }
        drawPath(rope, Color(0xFFC25A86), style = Stroke(width = 4.5f * d))
        drawPath(rope, Color(0xFFF28DB2), style = Stroke(width = 3f * d))
        drawPath(rope, Color(0xFFFFD0E2).copy(alpha = .8f), style = Stroke(width = .8f * d))
        for (i in 0..n) {
            val x = i * step
            // the egg under this swag, on a short string from the lowest point
            val mx = x + step / 2
            val low = pinY + sag / 2
            val egg = pic(eggPics[i % eggPics.size])
            if (egg != null && mx < size.width + step && (!phone || mx > size.width * .62f)) {
                rotate(degrees = 6f * sin(t * 1.4f + i * .7f), pivot = Offset(mx, low)) {
                    drawLine(Color(0xFFC25A86), Offset(mx, low), Offset(mx, low + 4f * d), strokeWidth = 1.2f * d)
                    val eh = (if (phone) 26f else 21f) * d
                    fit(egg, mx - eh * .4f, low + 3f * d, eh * .8f, eh)
                }
            }
            drawCircle(Color(0xFFB8B0A0), 3.4f * d, Offset(x, pinY + .5f * d))
            drawCircle(Color(0xFFF4F1EA), 3f * d, Offset(x, pinY))
            drawCircle(Color.White, 1f * d, Offset(x - 1f * d, pinY - 1f * d))
        }
    }

    /** Spring grass with tulips and eggs half hidden in it, repeated along the bottom of Home */
    val bottomTrim: HolidayArt = {
        pic(R.drawable.ea_nav_grass)?.let { image ->
            // inside the trim's own box, so it never covers the news cards just above it
            val h = size.height
            val w = image.width * h / image.height
            var x = 0f
            while (x < size.width) {
                fit(image, x, size.height - h, w, h)
                x += w
            }
        }
    }
}
