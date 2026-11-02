package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import org.jellyfin.sdk.model.api.BaseItemKind
import java.time.LocalDateTime
import java.time.ZoneOffset

/*
 * Benflix card badges, all in one place so every card looks the same.
 * Picked by Ben (2026-11-02): one word badge per card in the top-left corner,
 * a small white tick for watched, a red bar for part-watched. No heart, no counts.
 */

private const val NEW_DAYS = 7L

/**
 * The one word badge a card shows in its top-left corner, or nothing.
 * "New episode" / "New" wins; otherwise a rating reason like "TOP RATED".
 * On TV the rating only shows on the tile you're on, so rows stay calm.
 */
@Composable
fun CardWordBadge(
    item: BaseItem?,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    if (item == null) return
    val isTv = rememberIsTv()
    val isNew = remember(item) { item.isNew }
    if (isNew) {
        CardBadge(
            text = stringResource(if (item.type == BaseItemKind.SERIES) R.string.new_episode else R.string.new_badge),
            background = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )
        return
    }
    val reason = remember(item) { item.reasonTag() }
    if (reason != null && (!isTv || focused)) {
        CardBadge(text = reason, modifier = modifier)
    }
}

/** A small white tick (no circle) for something you've finished */
@Composable
fun CardWatchedTick(modifier: Modifier = Modifier) =
    Text(
        text = stringResource(R.string.fa_check),
        fontFamily = FontAwesome,
        fontSize = 14.sp,
        color = Color.White,
        style = TextStyle(shadow = Shadow(color = Color.Black, blurRadius = 6f)),
        modifier = modifier.padding(2.dp),
    )

/**
 * One small badge in a card's top-left corner. Every badge uses this so they all look the same.
 */
@Composable
fun CardBadge(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Color.Black.copy(alpha = .78f),
) = Text(
    text = text,
    fontSize = 10.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = .5.sp,
    color = Color.White,
    maxLines = 1,
    modifier =
        modifier
            .background(background, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
)

/**
 * Whether to show a "New" badge: a series with unwatched episodes added recently,
 * or an unwatched episode added recently
 */
private val BaseItem.isNew: Boolean
    get() {
        val cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(NEW_DAYS)
        return when (type) {
            BaseItemKind.SERIES -> {
                (data.userData?.unplayedItemCount ?: 0) > 0 &&
                    data.dateLastMediaAdded?.isAfter(cutoff) == true
            }

            BaseItemKind.EPISODE -> {
                data.userData?.played != true && data.dateCreated?.isAfter(cutoff) == true
            }

            else -> {
                false
            }
        }
    }

/**
 * A short reason tag for a tile, like "CRITICS 94%" or "TOP RATED", or null when nothing stands out.
 */
private fun BaseItem.reasonTag(): String? {
    if (type != BaseItemKind.MOVIE && type != BaseItemKind.SERIES) return null
    val critic = data.criticRating?.toInt()
    val community = data.communityRating
    val year = data.productionYear
    val thisYear =
        java.time.Year
            .now()
            .value
    return when {
        critic != null && critic >= 90 -> "CRITICS $critic%"
        community != null && community >= 8.0f -> "TOP RATED"
        year != null && year <= thisYear - 25 && community != null && community >= 7.3f -> "CLASSIC"
        else -> null
    }
}
