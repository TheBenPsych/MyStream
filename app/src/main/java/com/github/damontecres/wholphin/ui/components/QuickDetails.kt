package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.QuickDetailsData
import com.github.damontecres.wholphin.preferences.DisplayToggle
import com.github.damontecres.wholphin.ui.dot
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.util.LocalClock
import com.github.damontecres.wholphin.ui.util.LocalInterfaceCustomization
import org.jellyfin.sdk.model.DateTime
import kotlin.time.Duration

@Composable
fun QuickDetails(
    details: QuickDetailsData?,
    timeRemaining: Duration?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    endsAt: DateTime? = null,
) {
    val enabled = LocalInterfaceCustomization.current.enabledDisplayToggles
    val inlineContentMap = rememberQuickDetailsContentMap(textStyle)
    // Benflix: anime show their AniList and MyAnimeList scores instead of the star rating
    val aniList by com.github.damontecres.wholphin.services.AniListFacts.cards
        .collectAsState()
    val isMatchedAnime = details?.itemId?.let { it in aniList } == true
    Row(modifier = modifier) {
        if (details != null) {
            QuickDetailsText(details.basic, Modifier, textStyle, inlineContentMap)
            if (DisplayToggle.OFFICIAL_RATING in enabled) {
                QuickDetailsText(details.officialRating, Modifier, textStyle, inlineContentMap)
            }
            if (DisplayToggle.COMMUNITY_RATING in enabled) {
                if (isMatchedAnime) {
                    AniListScoreText(details.itemId, textStyle = textStyle)
                } else {
                    QuickDetailsText(details.communityRating, Modifier, textStyle, inlineContentMap)
                }
            }
            if (DisplayToggle.CRITIC_RATING in enabled) {
                QuickDetailsText(details.criticRating, Modifier, textStyle, inlineContentMap)
            }
        }
        if (timeRemaining != null) {
            TimeRemaining(timeRemaining, textStyle = textStyle)
        } else if (endsAt != null) {
            EndsAt(endsAt, textStyle = textStyle)
        }
    }
}

/** "  •  AniList 6.9  •  MAL 7.1" for an anime matched on AniList; nothing for anything else */
@Composable
fun AniListScoreText(
    itemId: java.util.UUID?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    leadingDot: Boolean = true,
) {
    if (itemId == null) return
    val facts by com.github.damontecres.wholphin.services.AniListFacts.cards
        .collectAsState()
    val found = facts[itemId] ?: return
    val mal by com.github.damontecres.wholphin.services.MalFacts.scores
        .collectAsState()
    val text =
        buildAnnotatedString {
            if (leadingDot) dot()
            appendAnimeScores(found, mal, separator = { dot() })
        }
    if (text.isBlank()) return
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        style = textStyle,
        maxLines = 1,
        modifier = modifier,
    )
}

/** AniList's own blue */
val AniListBlue = Color(0xFF02A9FF)

/** MyAnimeList's own blue */
val MalBlue = Color(0xFF6A8CD8)

/**
 * Adds "AniList 6.9" and "MAL 7.1" (each only when known), with [separator] between them.
 * Returns whether anything was added.
 */
fun AnnotatedString.Builder.appendAnimeScores(
    facts: com.github.damontecres.wholphin.services.AniListCardFacts,
    malScores: Map<Int, Double>,
    separator: AnnotatedString.Builder.() -> Unit,
): Boolean {
    val aniList = facts.scoreText
    val mal =
        facts.malId
            ?.let { malScores[it] }
            ?.let { com.github.damontecres.wholphin.services.MalFacts.text(it) }
    aniList?.let {
        withStyle(SpanStyle(color = AniListBlue, fontWeight = FontWeight.Bold)) { append("AniList ") }
        append(it)
    }
    mal?.let {
        if (aniList != null) separator()
        withStyle(SpanStyle(color = MalBlue, fontWeight = FontWeight.Bold)) { append("MAL ") }
        append(it)
    }
    return aniList != null || mal != null
}

@NonRestartableComposable
@Composable
fun QuickDetailsText(
    str: AnnotatedString?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    inlineContentMap: Map<String, InlineTextContent> = rememberQuickDetailsContentMap(textStyle),
) = str?.let {
    Text(
        text = str,
        color = MaterialTheme.colorScheme.onSurface,
        style = textStyle,
        inlineContent = inlineContentMap,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
fun rememberQuickDetailsContentMap(textStyle: TextStyle = MaterialTheme.typography.titleSmall) =
    remember(textStyle) {
        mapOf(
            "star" to
                InlineTextContent(
                    Placeholder(
                        textStyle.fontSize,
                        textStyle.fontSize,
                        PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        tint = FilledStarColor,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            "rotten" to
                InlineTextContent(
                    Placeholder(
                        textStyle.fontSize,
                        textStyle.fontSize,
                        PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rotten_tomatoes_rotten),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.Unspecified,
                    )
                },
            "fresh" to
                InlineTextContent(
                    Placeholder(
                        textStyle.fontSize,
                        textStyle.fontSize,
                        PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rotten_tomatoes_fresh),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.Unspecified,
                    )
                },
        )
    }

@Composable
fun TimeRemaining(
    remaining: Duration,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val now by LocalClock.current.now
    EndsAt(
        endsAt = now.plusSeconds(remaining.inWholeSeconds),
        modifier = modifier,
        textStyle = textStyle,
    )
}

@Composable
fun EndsAt(
    endsAt: DateTime,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val resources = LocalResources.current
    val context = LocalContext.current
    val remainingStr =
        remember(endsAt, resources, context) {
            val endTimeStr = formatTime(context, endsAt)
            buildAnnotatedString {
                dot()
                append(resources.getString(R.string.ends_at, endTimeStr))
            }
        }
    Text(
        text = remainingStr,
        style = textStyle,
        maxLines = 1,
        modifier = modifier,
    )
}
