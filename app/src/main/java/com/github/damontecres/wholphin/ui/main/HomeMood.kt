package com.github.damontecres.wholphin.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.cards.ItemRowTitle
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * "Pick a mood": each mood finds movies & shows using what the server knows about them
 * (genre, rating, length, number of episodes)
 */
enum class HomeMood(
    @param:StringRes val title: Int,
    val colors: List<Color>,
    val types: List<BaseItemKind>,
    val genres: List<String>? = null,
    val minRating: Double? = null,
    val unwatchedOnly: Boolean = false,
    /** Extra check on each item after it comes back from the server */
    val keep: (BaseItem) -> Boolean = { true },
) {
    BINGE(
        title = R.string.mood_binge,
        colors = listOf(Color(0xFFE50914), Color(0xFF7A0410)),
        types = listOf(BaseItemKind.SERIES),
        unwatchedOnly = true,
        // Shows with plenty of episodes
        keep = { (it.data.recursiveItemCount ?: it.data.childCount?.times(10) ?: 0) >= 20 },
    ),
    THOUGHTFUL(
        title = R.string.mood_thoughtful,
        colors = listOf(Color(0xFF3A5BA0), Color(0xFF16233F)),
        types = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
        genres = listOf("Drama", "Documentary", "Mystery", "History"),
        minRating = 7.0,
    ),
    BACKGROUND(
        title = R.string.mood_background,
        colors = listOf(Color(0xFF2BA36B), Color(0xFF0F3D28)),
        types = listOf(BaseItemKind.SERIES),
        genres = listOf("Comedy", "Reality", "Food", "Home and Garden", "Game Show", "Talk"),
    ),
    QUICK(
        title = R.string.mood_quick,
        colors = listOf(Color(0xFFFFA000), Color(0xFF7A4A00)),
        types = listOf(BaseItemKind.MOVIE),
        unwatchedOnly = true,
        keep = { item -> item.data.runTimeTicks?.let { it / 600_000_000L < 100 } == true },
    ),
    DATE_NIGHT(
        title = R.string.mood_date_night,
        colors = listOf(Color(0xFFE0457B), Color(0xFF5E1531)),
        types = listOf(BaseItemKind.MOVIE),
        genres = listOf("Romance", "Comedy"),
    ),
    EDGE(
        title = R.string.mood_edge,
        colors = listOf(Color(0xFF6B2FBF), Color(0xFF220C45)),
        types = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
        genres = listOf("Thriller", "Action", "Horror"),
    ),
    FAMILY(
        title = R.string.mood_family,
        colors = listOf(Color(0xFF00A6C8), Color(0xFF00394A)),
        types = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
        genres = listOf("Family", "Animation"),
    ),
}

/**
 * Row of colorful mood tiles
 */
@Composable
fun MoodRow(
    selected: HomeMood?,
    onClickMood: (HomeMood) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        ItemRowTitle(stringResource(R.string.mood_row_title))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            items(HomeMood.entries) { mood ->
                MoodTile(
                    mood = mood,
                    selected = mood == selected,
                    onClick = { onClickMood(mood) },
                )
            }
        }
    }
}

@Composable
private fun MoodTile(
    mood: HomeMood,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border =
            ClickableSurfaceDefaults.border(
                border = if (selected) Border(BorderStroke(2.dp, Color.White.copy(alpha = .7f)), shape = shape) else Border.None,
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
            ),
        modifier = modifier.size(200.dp, 84.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(mood.colors)),
        ) {
            Text(
                text = stringResource(mood.title),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
