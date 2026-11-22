package com.github.damontecres.wholphin.ui.detail.title

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.OmdbScores
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Glowfin (Idea Box 27, Ben 2026-11-22): every score's number sits in a small box in its site's own
 * colour. IMDb yellow, Rotten Tomatoes red, Metacritic green, AniList light blue, MyAnimeList a darker
 * blue with an edge so the two blues look different, and user scores (TMDB, audience) a quiet grey.
 */
class ScoreBrand(
    val box: Color,
    val text: Color,
    val edge: Color? = null,
)

fun scoreBrand(label: String): ScoreBrand? {
    val l = label.lowercase()
    return when {
        l == "imdb" -> ScoreBrand(Color(0xFFF5C518), Color(0xFF111111))
        "rotten" in l || l == "critics" -> ScoreBrand(Color(0xFFFA320A), Color.White)
        "metacritic" in l -> ScoreBrand(Color(0xFF66CC33), Color(0xFF0B0C14))
        l == "anilist" -> ScoreBrand(Color(0xFF3DB4F2), Color(0xFF0B0C14))
        l == "mal" || "myanimelist" in l -> ScoreBrand(Color(0xFF2E51A2), Color.White, Color(0xFF6F8BD6))
        "tmdb" in l || l == "users" || l == "audience" -> ScoreBrand(Color(0xFF4A4F66), Color.White)
        else -> null
    }
}

/** The number in its coloured box. Falls back to plain bold text for a site without a colour. */
@Composable
fun ScoreBox(
    value: String,
    label: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    plainColor: Color = BenflixColors.Text,
) {
    val brand = scoreBrand(label)
    if (brand == null) {
        Text(text = value, color = plainColor, fontSize = fontSize, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = modifier)
        return
    }
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = value,
        color = brand.text,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        modifier =
            modifier
                .background(brand.box, shape)
                .then(if (brand.edge != null) Modifier.border(1.dp, brand.edge, shape) else Modifier)
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** "[7.8] IMDb  [91%] Rotten Tomatoes  [74] Metacritic" on one line */
@Composable
fun ScoreBadges(
    scores: List<StripPart>,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = BenflixType.small,
) {
    if (scores.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        scores.forEach { p ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (p.value != null) ScoreBox(p.value, p.label, fontSize)
                Text(
                    text = p.label,
                    color = BenflixColors.Muted,
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OmdbScoresEntryPoint {
    fun omdbScores(): OmdbScores
}

private fun omdbScores(context: Context): OmdbScores =
    EntryPointAccessors.fromApplication(context.applicationContext, OmdbScoresEntryPoint::class.java).omdbScores()

/**
 * The title's Metacritic score from OMDb (Ben's saved key), or null while loading, without a key, or
 * when Metacritic has none. Saved on the device for a week, like the other OMDb scores.
 */
@Composable
fun rememberMetacritic(item: BaseItem): Int? {
    val context = LocalContext.current
    val imdb = item.data.providerIds?.get("Imdb")
    var score by remember(imdb) { mutableStateOf<Int?>(null) }
    LaunchedEffect(imdb) {
        if (imdb != null && imdb.startsWith("tt")) {
            score =
                try {
                    omdbScores(context).scoresFor(imdb)?.metacritic
                } catch (ex: Exception) {
                    if (ex is kotlinx.coroutines.CancellationException) throw ex
                    null
                }
        }
    }
    return score
}
