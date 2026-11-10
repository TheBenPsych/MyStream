package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType

/*
 * Redesign 2 players (approved pl-family): music and audiobooks share one now-playing screen. On TV and
 * phones held sideways: the cover on the left, the kind, name and who it's by in the middle with the
 * same line and the same round buttons, and a column on the right (a song's words, or a book's
 * chapters). Only the colour changes: violet for music, lime for audiobooks.
 */

/**
 * The wide now-playing frame. [coverSize] is worked out here so both screens get the same size cover;
 * [cover] draws it (square for songs, a book shape for audiobooks). [side] is the right-hand column.
 */
@Composable
fun NowPlayingWideFrame(
    reservedBottom: Dp,
    cover: @Composable (coverSize: Dp) -> Unit,
    middle: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    side: (@Composable (Modifier) -> Unit)? = null,
) {
    BoxWithConstraints(modifier) {
        val height = maxHeight - reservedBottom - NOW_PLAYING_TOP
        val width = maxWidth
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, end = 48.dp, top = NOW_PLAYING_TOP)
                    .height(height),
        ) {
            cover((width * 0.22f).coerceAtMost(height - 20.dp))
            // Scrolls (focus brings the button into view) so the buttons are never squashed away
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
            ) {
                middle()
            }
            side?.invoke(Modifier.width(width * 0.27f).fillMaxHeight())
        }
    }
}

private val NOW_PLAYING_TOP = 40.dp

/** "NOW PLAYING · FROM ...", then the big name and the grey "who it's by" line */
@Composable
fun NowPlayingTitle(
    kicker: String,
    title: String,
    byLine: String,
    color: Color,
    big: Boolean,
    easy: Boolean = false,
) {
    MusicKicker(kicker, color = color)
    Text(
        text = title,
        color = BenflixColors.Text,
        fontSize = if (big) (if (easy) 46.sp else 40.sp) else 26.sp,
        lineHeight = if (big) 46.sp else 30.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 6.dp),
    )
    if (byLine.isNotBlank()) {
        Text(
            text = byLine,
            color = BenflixColors.Soft,
            fontSize = if (big) BenflixType.rowName else BenflixType.body,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The one now-playing line: how far through, with the time on the left and [rightText] on the right */
@Composable
fun NowPlayingLine(
    progress: Float,
    leftText: String,
    rightText: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(BenflixColors.Line, RoundedCornerShape(3.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(3.dp)),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(leftText, color = BenflixColors.Muted, fontSize = BenflixType.small)
            Spacer(Modifier.weight(1f))
            Text(rightText, color = BenflixColors.Muted, fontSize = BenflixType.small, maxLines = 1)
        }
    }
}
