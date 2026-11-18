package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap

/**
 * The 8 picture colours (two ends of a soft gradient), in the same order as the profile colours
 * (ProfileThemeChoices), so a colour someone already picked keeps its place.
 */
val SeaColours: List<Pair<Color, Color>> =
    listOf(
        Color(0xFFA55BE0) to Color(0xFF1FA8E6),
        Color(0xFF1FA8E6) to Color(0xFF3FE0E8),
        Color(0xFF3FE0E8) to Color(0xFF2BB88F),
        Color(0xFFF0A04B) to Color(0xFFE0605B),
        Color(0xFF6C6FE8) to Color(0xFFA55BE0),
        Color(0xFFE05BB5) to Color(0xFFA55BE0),
        Color(0xFF5FD08A) to Color(0xFF1FA8E6),
        Color(0xFF5A6488) to Color(0xFF2D3354),
    )

/** Kids always get the rainbow */
const val SEA_KIDS = -1

private val Rainbow = listOf(Color(0xFFF0A04B), Color(0xFFE05BB5), Color(0xFFA55BE0), Color(0xFF3FE0E8))
private val Night = Color(0xFF0B0D1A)
private val EyeInk = Color(0xFF14172E)
private val Blush = Color(0xFFFF7AA2)
private const val CIRCLE = "M0 50a50 50 0 1 0 100 0a50 50 0 1 0 -100 0z"
private const val RIM = "M1 50a49 49 0 1 0 98 0a49 49 0 1 0 -98 0z"

private const val WAVE = "M0 80 Q25 72 50 80 T100 80 V100 H0Z"
private val Bubbles =
    listOf(
        Triple("M78.6 24a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0z", .5f, 1.4f),
        Triple("M86 34a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z", .45f, 1.2f),
        Triple("M13.6 30a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0z", .35f, 1.2f),
    )

/** The colours one picture uses: the animal's tints of the main colour, and the deep background */
private class SeaPalette(
    colour: Int,
) {
    private val kids = colour == SEA_KIDS
    private val pair = SeaColours.getOrElse(colour) { SeaColours[0] }
    private val main = if (kids) Color(0xFFA55BE0) else pair.first
    val deep: List<Color> =
        if (kids) Rainbow.map { lerp(it, Night, .45f) } else listOf(lerp(pair.first, Night, .5f), lerp(pair.second, Night, .35f))

    fun role(c: Char): Color? =
        when (c) {
            'b' -> lerp(main, Color.White, .5f)
            's' -> lerp(main, Color.White, .2f)
            'l' -> lerp(main, Color.White, .82f)
            'e' -> EyeInk
            'w' -> Color.White
            'k' -> Blush
            else -> null
        }
}

private val drawn = ConcurrentHashMap<Pair<SeaFriend, Int>, ImageVector>()

/**
 * A sea friend in the Storybook style (ui-5 S3): the animal in soft tints of [colour] (a [SeaColours] index,
 * or [SEA_KIDS]) on a deep version of that colour, with a little wave and a few bubbles. Built once per
 * animal and colour and kept, and drawn from a cached picture, so a row of them stays quick on older TVs.
 */
fun seaFriendVector(
    friend: SeaFriend,
    colour: Int,
): ImageVector =
    drawn.getOrPut(friend to colour) {
        val palette = SeaPalette(colour)
        val deep = palette.deep
        val builder =
            ImageVector.Builder(
                name = "sea-${friend.name}-$colour",
                defaultWidth = 100.dp,
                defaultHeight = 100.dp,
                viewportWidth = 100f,
                viewportHeight = 100f,
            )
        builder.addGroup(clipPathData = addPathNodes(CIRCLE))
        builder.addPath(
            pathData = addPathNodes(CIRCLE),
            fill = Brush.linearGradient(deep, start = Offset(0f, 100f), end = Offset(100f, 0f)),
        )
        builder.addPath(
            pathData = addPathNodes(WAVE),
            fill = SolidColor(Color.White),
            fillAlpha = .08f,
        )
        Bubbles.forEach { (d, a, w) ->
            builder.addPath(pathData = addPathNodes(d), stroke = SolidColor(Color.White), strokeAlpha = a, strokeLineWidth = w)
        }
        builder.addGroup(translationX = 9f, translationY = 9f, scaleX = .82f, scaleY = .82f)
        friend.parts.forEach { part ->
            val fill = palette.role(part.fill)
            val stroke = palette.role(part.stroke)
            builder.addPath(
                pathData = addPathNodes(part.d),
                fill = fill?.let { SolidColor(it) },
                fillAlpha = part.alpha,
                stroke = stroke?.let { SolidColor(it) },
                strokeAlpha = part.alpha,
                strokeLineWidth = part.width,
                strokeLineCap = if (part.roundCap) StrokeCap.Round else StrokeCap.Butt,
                strokeLineJoin = if (part.roundJoin) StrokeJoin.Round else StrokeJoin.Miter,
            )
        }
        builder.clearGroup()
        builder.clearGroup()
        builder.addPath(pathData = addPathNodes(RIM), stroke = SolidColor(Color.White), strokeAlpha = .18f, strokeLineWidth = 2f)
        builder.build()
    }

/** Draws a sea friend; clip [modifier] to a circle (the picture is already round) */
@Composable
fun SeaFriendImage(
    friend: SeaFriend,
    colour: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    Image(
        painter = rememberVectorPainter(seaFriendVector(friend, colour)),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        alpha = alpha,
        modifier = modifier,
    )
}

/**
 * The same picture as a [size] x [size] bitmap, for saving to the person's Jellyfin account (so Jellyfin's own
 * apps show their sea friend too). Drawn with plain Android drawing, so it works outside the screen.
 */
fun seaFriendBitmap(
    friend: SeaFriend,
    colour: Int,
    size: Int,
): android.graphics.Bitmap {
    val palette = SeaPalette(colour)
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.scale(size / 100f, size / 100f)

    fun path(d: String) =
        androidx.core.graphics.PathParser
            .createPathFromPathData(d)
    val circle = path(CIRCLE)
    canvas.save()
    canvas.clipPath(circle)
    val deep = palette.deep.map { it.toArgb() }.toIntArray()
    canvas.drawPath(
        circle,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(0f, 100f, 100f, 0f, deep, null, android.graphics.Shader.TileMode.CLAMP)
        },
    )

    fun fillPaint(
        c: Color,
        a: Float,
    ) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = c.copy(alpha = c.alpha * a).toArgb()
    }

    fun strokePaint(
        c: Color,
        a: Float,
        w: Float,
        roundCap: Boolean = false,
        roundJoin: Boolean = false,
    ) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        color = c.copy(alpha = c.alpha * a).toArgb()
        strokeWidth = w
        strokeCap = if (roundCap) android.graphics.Paint.Cap.ROUND else android.graphics.Paint.Cap.BUTT
        strokeJoin = if (roundJoin) android.graphics.Paint.Join.ROUND else android.graphics.Paint.Join.MITER
    }
    canvas.drawPath(path(WAVE), fillPaint(Color.White, .08f))
    Bubbles.forEach { (d, a, w) -> canvas.drawPath(path(d), strokePaint(Color.White, a, w)) }
    canvas.save()
    canvas.translate(9f, 9f)
    canvas.scale(.82f, .82f)
    friend.parts.forEach { part ->
        val p = path(part.d)
        palette.role(part.fill)?.let { canvas.drawPath(p, fillPaint(it, part.alpha)) }
        palette.role(part.stroke)?.let { canvas.drawPath(p, strokePaint(it, part.alpha, part.width, part.roundCap, part.roundJoin)) }
    }
    canvas.restore()
    canvas.restore()
    canvas.drawPath(path(RIM), strokePaint(Color.White, .18f, 2f))
    return bitmap
}
