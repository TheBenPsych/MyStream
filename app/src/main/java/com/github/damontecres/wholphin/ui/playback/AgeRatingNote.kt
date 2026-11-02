package com.github.damontecres.wholphin.ui.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Benflix: for a few seconds after a movie or episode starts, a small note in the top corner
 * says its age rating and what that rating means (like "PG-13 · Parents strongly cautioned").
 *
 * Shown once per title per play. [rating] is looked up by the player (an episode with no
 * rating of its own uses its show's rating).
 */
@Composable
fun AgeRatingNote(
    item: BaseItem?,
    rating: String?,
    hidden: Boolean,
    modifier: Modifier = Modifier,
) {
    val isTv = rememberIsTv()
    var shownFor by remember { mutableStateOf<UUID?>(null) }
    var visible by remember { mutableStateOf(false) }
    val meaning = rating?.let { AgeRatings.meaning(it) }
    LaunchedEffect(item?.id, rating) {
        val id = item?.id ?: return@LaunchedEffect
        if (rating.isNullOrBlank() || shownFor == id) return@LaunchedEffect
        shownFor = id
        // Wait for the picture to settle first
        delay(1.5.seconds)
        visible = true
        delay(6.seconds)
        visible = false
    }
    AnimatedVisibility(
        visible = visible && !hidden && rating != null,
        enter = fadeIn() + slideInHorizontally { -it / 4 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .widthIn(max = if (isTv) 520.dp else 320.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // A thin red bar, the way the age note looks on big streaming apps
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(if (isTv) 40.dp else 32.dp)
                        .background(Color(0xFFE50914), RoundedCornerShape(2.dp)),
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = rating.orEmpty(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isTv) 20.sp else 16.sp,
                    maxLines = 1,
                )
                if (meaning != null) {
                    Text(
                        text = meaning,
                        color = Color.White.copy(alpha = .85f),
                        fontSize = if (isTv) 14.sp else 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Plain-English meanings for the common US, UK and Canadian age ratings */
object AgeRatings {
    fun meaning(rating: String): String? {
        val r =
            rating
                .trim()
                .uppercase()
                .removePrefix("US-")
                .removePrefix("GB-")
                .removePrefix("CA-")
        return when (r) {
            "G" -> "General audiences. All ages"
            "PG" -> "Parental guidance suggested"
            "PG-13" -> "Parents strongly cautioned. May not suit under 13"
            "R" -> "Restricted. Under 17 needs a parent"
            "NC-17" -> "Adults only"
            "TV-Y" -> "For all children"
            "TV-Y7", "TV-Y7-FV" -> "For kids 7 and up"
            "TV-G" -> "General audiences. All ages"
            "TV-PG" -> "Parental guidance suggested"
            "TV-14" -> "Parents strongly cautioned. May not suit under 14"
            "TV-MA" -> "Mature audiences only"
            "U" -> "Universal. All ages"
            "12", "12A" -> "Suitable for 12 and up"
            "15" -> "Suitable for 15 and up"
            "18" -> "Adults only"
            "14A" -> "Under 14 needs an adult"
            "18A" -> "Under 18 needs an adult"
            "NR", "NOT RATED", "UNRATED" -> "Not rated"
            else -> null
        }
    }
}
