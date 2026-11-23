package com.github.damontecres.wholphin.ui.games

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R

/**
 * Glowfin's own game covers (Ben 2026-11-18): one drawn cover for every Glowfin game, the same pictures as the
 * browser (server/web/img/games). The drawings live in the project files (benflix/game-covers/covers.js).
 * [key] is the game's name: a MovieGame or BuiltInGame name, "EMBER" or "surprise".
 */
@DrawableRes
internal fun gameCover(key: String): Int? =
    when (key.uppercase()) {
        "SCENES" -> R.drawable.game_cover_scenes
        "POSTER" -> R.drawable.game_cover_poster
        "GRID" -> R.drawable.game_cover_grid
        "TIMELINE" -> R.drawable.game_cover_timeline
        "ACTOR" -> R.drawable.game_cover_actor
        "QUOTE" -> R.drawable.game_cover_quote
        "ROLE" -> R.drawable.game_cover_role
        "BLITZ" -> R.drawable.game_cover_blitz
        "SEASON" -> R.drawable.game_cover_season
        "CAPTION" -> R.drawable.game_cover_caption
        "GUESS" -> R.drawable.game_cover_guess
        "RIDDLE" -> R.drawable.game_cover_riddle
        "HIGHER_LOWER" -> R.drawable.game_cover_higher_lower
        "QUIZ" -> R.drawable.game_cover_quiz
        "FAKE_PLOT" -> R.drawable.game_cover_fake_plot
        "DRAW" -> R.drawable.game_cover_draw
        "CLOSEST" -> R.drawable.game_cover_closest
        "POPCORN_POP" -> R.drawable.game_cover_popcorn_pop
        "REEL_RUSH" -> R.drawable.game_cover_reel_rush
        "SNACK_CATCH" -> R.drawable.game_cover_snack_catch
        "USHER_DASH" -> R.drawable.game_cover_usher_dash
        "FILM_CAN_STACK" -> R.drawable.game_cover_film_can_stack
        "MATINEE_MATCH" -> R.drawable.game_cover_matinee_match
        "EMBER", "EMBER_ROAD" -> R.drawable.game_cover_ember
        "SURPRISE" -> R.drawable.game_cover_surprise
        else -> null
    }

/** Covers are 2 wide by 3 tall */
internal const val GAME_COVER_RATIO = 2f / 3f

/**
 * The cover blurred big behind a game's page: decoded tiny (1/16 size) and stretched, so it looks soft on every
 * TV, even ones too old for real blur, for almost no work.
 */
@Composable
internal fun GameCoverBackdrop(
    @DrawableRes cover: Int,
    modifier: Modifier = Modifier,
) {
    val res = LocalContext.current.resources
    val tiny =
        remember(cover) {
            runCatching {
                BitmapFactory.decodeResource(res, cover, BitmapFactory.Options().apply { inSampleSize = 16 })?.asImageBitmap()
            }.getOrNull()
        }
    if (tiny != null) {
        Image(
            bitmap = tiny,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Low,
            alpha = .6f,
            modifier = modifier.fillMaxSize(),
        )
    }
}

/** The cover itself, at its own 2:3 shape with rounded corners */
@Composable
internal fun GameCoverImage(
    @DrawableRes cover: Int,
    title: String,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(cover),
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .aspectRatio(GAME_COVER_RATIO)
                .clip(RoundedCornerShape(12.dp)),
    )
}
