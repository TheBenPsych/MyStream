package com.github.damontecres.wholphin.ui.nav

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.Celebration
import com.github.damontecres.wholphin.services.LiteMode
import com.github.damontecres.wholphin.services.LogoEffect
import kotlin.math.sin

/*
 * Holiday looks for the whole BENFLIX logo (the looks Ben picked on the Holiday Costumes page).
 * Each holiday has two small pictures, drawn from the same 640 x 230 canvas:
 *  - "base": the logo itself with its holiday paint (and anything that sits behind it)
 *  - "front": the decorations on top (hats, snow, lanterns...), which drop on with a wiggle
 * On the canvas the plain logo sits at x 90, y 80 and is 457 x 96, so the pictures are drawn
 * bigger than the logo and spill a little outside it. Pictures are cheap: decoded once, no
 * per-frame drawing after the 1.6 second intro, and Lite mode shows them still.
 */
private const val CANVAS_W = 640f
private const val CANVAS_H = 230f
private const val LOGO_X = 90f
private const val LOGO_Y = 80f
private const val LOGO_W = 457f
private const val LOGO_H = 96f

private class HolidayLook(
    @param:DrawableRes val base: Int,
    @param:DrawableRes val front: Int,
    val flicker: Boolean = false,
)

private fun lookFor(celebration: Celebration): HolidayLook? =
    when (celebration.effect) {
        LogoEffect.FIREWORKS_GOLD -> {
            if (celebration.text.endsWith("Eve")) {
                HolidayLook(R.drawable.holiday_logo_nye_base, R.drawable.holiday_logo_nye_front)
            } else {
                HolidayLook(R.drawable.holiday_logo_newyear_base, R.drawable.holiday_logo_newyear_front)
            }
        }

        LogoEffect.FIREWORKS_USA -> HolidayLook(R.drawable.holiday_logo_july4_base, R.drawable.holiday_logo_july4_front)
        LogoEffect.HEART_FLOAT -> HolidayLook(R.drawable.holiday_logo_valentines_base, R.drawable.holiday_logo_valentines_front)
        LogoEffect.GREEN_SHIMMER -> HolidayLook(R.drawable.holiday_logo_stpatricks_base, R.drawable.holiday_logo_stpatricks_front)
        LogoEffect.GHOST_PEEK -> HolidayLook(R.drawable.holiday_logo_halloween_base, R.drawable.holiday_logo_halloween_front)
        LogoEffect.FALLING_LEAVES -> HolidayLook(R.drawable.holiday_logo_thanksgiving_base, R.drawable.holiday_logo_thanksgiving_front)
        LogoEffect.TWINKLE_LIGHTS -> HolidayLook(R.drawable.holiday_logo_christmas_base, R.drawable.holiday_logo_christmas_front)
        LogoEffect.CONFETTI -> HolidayLook(R.drawable.holiday_logo_birthday_base, R.drawable.holiday_logo_birthday_front)
        LogoEffect.MARIGOLDS -> HolidayLook(R.drawable.holiday_logo_dotd_base, R.drawable.holiday_logo_dotd_front)
        LogoEffect.LANTERNS -> HolidayLook(R.drawable.holiday_logo_lunarny_base, R.drawable.holiday_logo_lunarny_front)
        LogoEffect.EMBERS -> HolidayLook(R.drawable.holiday_logo_bonfire_base, R.drawable.holiday_logo_bonfire_front)
        LogoEffect.STAR_PAIR -> HolidayLook(R.drawable.holiday_logo_tanabata_base, R.drawable.holiday_logo_tanabata_front)
        LogoEffect.BLOSSOMS -> HolidayLook(R.drawable.holiday_logo_easter_base, R.drawable.holiday_logo_easter_front)
        LogoEffect.HYPERSPACE -> HolidayLook(R.drawable.holiday_logo_starwars_base, R.drawable.holiday_logo_starwars_front)
        LogoEffect.ZAPS -> HolidayLook(R.drawable.holiday_logo_pokemon_base, R.drawable.holiday_logo_pokemon_front)
        LogoEffect.COIN -> HolidayLook(R.drawable.holiday_logo_mario_base, R.drawable.holiday_logo_mario_front)
        LogoEffect.FIRE_TRAILS -> HolidayLook(R.drawable.holiday_logo_bttf_base, R.drawable.holiday_logo_bttf_front)
        LogoEffect.FLOWER_BLOOM -> HolidayLook(R.drawable.holiday_logo_mothers_base, R.drawable.holiday_logo_mothers_front)
        LogoEffect.SUNSET -> HolidayLook(R.drawable.holiday_logo_fathers_base, R.drawable.holiday_logo_fathers_front)
        LogoEffect.SPROUTS -> HolidayLook(R.drawable.holiday_logo_earth_base, R.drawable.holiday_logo_earth_front)
        LogoEffect.SPOOKY_CAT -> HolidayLook(R.drawable.holiday_logo_friday13_base, R.drawable.holiday_logo_friday13_front, flicker = true)
        LogoEffect.GROUNDHOG -> HolidayLook(R.drawable.holiday_logo_groundhog_base, R.drawable.holiday_logo_groundhog_front)
        LogoEffect.HARVEST_MOON -> HolidayLook(R.drawable.holiday_logo_midautumn_base, R.drawable.holiday_logo_midautumn_front)
        LogoEffect.POPCORN -> HolidayLook(R.drawable.holiday_logo_popcorn_base, R.drawable.holiday_logo_popcorn_front)
        LogoEffect.GAME_BUTTONS -> HolidayLook(R.drawable.holiday_logo_videogames_base, R.drawable.holiday_logo_videogames_front)
        LogoEffect.CAT_TAIL -> HolidayLook(R.drawable.holiday_logo_cat_base, R.drawable.holiday_logo_cat_front)
        LogoEffect.PAW_PRINTS -> HolidayLook(R.drawable.holiday_logo_dog_base, R.drawable.holiday_logo_dog_front)
        LogoEffect.PIRATE_FLAG -> HolidayLook(R.drawable.holiday_logo_pirate_base, R.drawable.holiday_logo_pirate_front)
        else -> null
    }

/**
 * The Benflix logo, [height] tall. On a holiday or birthday it wears that day's look:
 * the holiday paint fades in and the decorations drop on with a small wiggle, then it rests.
 */
@Composable
fun HolidayAwareLogo(
    celebration: Celebration?,
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
) {
    val width = height * (LOGO_W / LOGO_H)
    val look = celebration?.let { lookFor(it) }
    if (look == null) {
        Image(
            painter = painterResource(R.drawable.benflix_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = modifier.size(width, height),
        )
        return
    }
    val lite = LiteMode.isOn(LocalContext.current)
    val base = ImageBitmap.imageResource(look.base)
    val front = ImageBitmap.imageResource(look.front)
    val plain = ImageBitmap.imageResource(R.drawable.benflix_logo)
    val progress = remember(look) { Animatable(if (lite) 1f else 0f) }
    LaunchedEffect(look) {
        if (!lite) progress.animateTo(1f, tween(1600, easing = LinearEasing))
    }
    Box(
        modifier =
            modifier
                .size(width, height)
                .drawBehind {
                    val t = progress.value
                    val scale = size.height / LOGO_H
                    val left = -LOGO_X * scale
                    val top = -LOGO_Y * scale
                    val dstSize = IntSize((CANVAS_W * scale).toInt(), (CANVAS_H * scale).toInt())
                    val dstOffset = IntOffset(left.toInt(), top.toInt())
                    // the plain logo first, then the holiday paint fading in over it
                    val paint = (t / 0.55f).coerceIn(0f, 1f)
                    val flick = if (look.flicker) flickerAlpha(t) else 1f
                    if (paint < 1f) {
                        drawImage(plain, dstSize = IntSize(size.width.toInt(), size.height.toInt()), alpha = flick)
                    }
                    drawImage(base, dstOffset = dstOffset, dstSize = dstSize, alpha = paint * flick)
                    drawDecorations(front, t, dstOffset, dstSize)
                },
    )
}

/** The decorations drop from above, overshoot a little, wiggle and settle (like the mockup) */
private fun DrawScope.drawDecorations(
    front: ImageBitmap,
    t: Float,
    dstOffset: IntOffset,
    dstSize: IntSize,
) {
    val p = (t / 0.65f).coerceIn(0f, 1f)
    if (p <= 0f) return
    val drop =
        when {
            p < 0.55f -> -0.4f + (0.44f * (p / 0.55f))
            p < 0.75f -> 0.04f * (1f - (p - 0.55f) / 0.2f)
            else -> 0f
        }
    val angle = if (p < 1f) (sin(p * 18f) * 4f * (1f - p)) else 0f
    val alpha = (p / 0.3f).coerceIn(0f, 1f)
    val pivot = Offset(dstOffset.x + dstSize.width / 2f, dstOffset.y + dstSize.height * 0.6f)
    val dy = drop * dstSize.height * 0.5f
    withTransform(
        {
            translate(top = dy)
            rotate(angle, pivot)
        },
    ) {
        drawImage(front, dstOffset = dstOffset, dstSize = dstSize, alpha = alpha)
    }
}

/** Friday the 13th: the logo flickers like a dying light bulb, then stays on */
private fun flickerAlpha(t: Float): Float =
    when {
        t < 0.10f -> 1f
        t < 0.14f -> 0.25f
        t < 0.40f -> 1f
        t < 0.44f -> 0.4f
        t < 0.47f -> 1f
        t < 0.50f -> 0.55f
        else -> 1f
    }
