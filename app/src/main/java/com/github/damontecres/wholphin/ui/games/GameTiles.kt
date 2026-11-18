package com.github.damontecres.wholphin.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.theme.BenflixType

/** One spot on the Arcade wall */
internal sealed interface WallItem {
    data class Game(
        val game: BuiltInGame,
    ) : WallItem

    data object Surprise : WallItem
}

/**
 * "Your best scores": every built-in game with this profile's best, highest first.
 * TV: one slim strip across the page, a cell per game. Phone: a simple list.
 */
internal enum class PlayWith { REMOTE, CONTROLLER, PHONE }

/** A small round icon on a game card: remote, controller or phone */
@Composable
internal fun PlayIcon(kind: PlayWith) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(30.dp)
                .background(Color(0xFF33363F), RoundedCornerShape(50)),
    ) {
        when (kind) {
            PlayWith.REMOTE -> {
                RemoteIcon(Modifier.size(width = 8.dp, height = 16.dp))
            }

            PlayWith.CONTROLLER -> {
                BenflixIconImage(BenflixIcon.PAD, Color.White, 17.dp)
            }

            PlayWith.PHONE -> {
                BenflixIconImage(BenflixIcon.PHONE, Color.White, 17.dp)
            }
        }
    }
}

/**
 * A small "works with" chip: an icon plus a word, e.g. a controller icon with "Controller"
 */
@Composable
internal fun Badge(
    kind: PlayWith,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier =
            Modifier
                .background(Color.White.copy(alpha = .1f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        when (kind) {
            PlayWith.REMOTE -> {
                RemoteIcon(Modifier.size(width = 8.dp, height = 15.dp))
            }

            PlayWith.CONTROLLER -> {
                BenflixIconImage(BenflixIcon.PAD, Color.White, 15.dp)
            }

            PlayWith.PHONE -> {
                BenflixIconImage(BenflixIcon.PHONE, Color.White, 15.dp)
            }
        }
        Text(
            text = text,
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A simple TV remote drawing: a rounded stick with a round OK button and two small buttons
 */
@Composable
internal fun RemoteIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color.White, cornerRadius = CornerRadius(w * .35f, w * .35f))
        drawCircle(Color.Black, radius = w * .3f, center = Offset(w / 2, h * .27f))
        drawCircle(Color.Black, radius = w * .12f, center = Offset(w / 2, h * .6f))
        drawCircle(Color.Black, radius = w * .12f, center = Offset(w / 2, h * .8f))
    }
}
