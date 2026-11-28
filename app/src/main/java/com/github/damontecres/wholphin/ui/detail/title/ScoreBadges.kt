package com.github.damontecres.wholphin.ui.detail.title

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.OmdbScores
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
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
    val shape = BenflixShapes.Small
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

/**
 * The age rating and scores at the top right (Home tops and details pages). Ben 2026-11-23: separate small chips
 * instead of one shared pill. The age rating is a white outline, IMDb is its own yellow chip ("IMDb 7.4"), and the
 * other scores are dark see-through chips with the site's small mark and a white number. At most 3 scores.
 */
@Composable
fun ScorePill(
    rating: String?,
    scores: List<StripPart>,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val shown = scores.filter { it.value != null }.take(3)
    if (rating.isNullOrBlank() && shown.isEmpty()) return
    val font = if (small) BenflixType.tag else BenflixType.body
    val mark = if (small) 12.dp else 14.dp
    val chip = BenflixShapes.Small
    val padX = if (small) 6.dp else 8.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (small) 5.dp else 6.dp),
        modifier = modifier,
    ) {
        if (!rating.isNullOrBlank()) {
            Text(
                text = rating,
                color = Color.White,
                fontSize = font,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier =
                    Modifier
                        .background(CHIP_BACK, chip)
                        .border(1.5.dp, Color.White.copy(alpha = .85f), chip)
                        .padding(horizontal = padX - 1.dp),
            )
        }
        shown.forEach { p ->
            val imdb = p.label.lowercase() == "imdb"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier =
                    if (imdb) {
                        Modifier
                            .background(IMDB_YELLOW, chip)
                            .padding(horizontal = padX)
                    } else {
                        Modifier
                            .background(CHIP_BACK, chip)
                            .border(1.dp, Color.White.copy(alpha = .22f), chip)
                            .padding(horizontal = padX)
                    },
            ) {
                if (imdb) {
                    Text(
                        text = "IMDb",
                        color = Color.Black,
                        fontSize = (mark.value * .7f).sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                } else {
                    SiteMark(p.label, mark)
                }
                Text(
                    text = p.value.orEmpty(),
                    color = if (imdb) Color.Black else Color.White,
                    fontSize = font,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

private val CHIP_BACK = Color(0x800B0D1A)
private val IMDB_YELLOW = Color(0xFFF5C518)

/** A score site's small mark (IMDb, the tomato, Metacritic's M, AniList's A, MAL), drawn without pictures */
@Composable
fun SiteMark(
    label: String,
    size: Dp,
) {
    val l = label.lowercase()
    val word = (size.value * .62f).sp
    when {
        l == "imdb" -> {
            MarkWord("IMDb", Color(0xFFF5C518), Color.Black, word, RoundedCornerShape(3.dp))
        }

        l == "mal" || "myanimelist" in l -> {
            MarkWord("MAL", Color(0xFF2E51A2), Color.White, word, RoundedCornerShape(3.dp))
        }

        "rotten" in l || l == "critics" -> {
            Box(Modifier.size(size)) {
                Box(Modifier.size(size).background(Color(0xFFE8340F), CircleShape))
                Box(
                    Modifier
                        .offset(x = size * .3f, y = -size * .12f)
                        .size(width = size * .42f, height = size * .26f)
                        .background(Color(0xFF3AA33A), BenflixShapes.Pill),
                )
            }
        }

        "metacritic" in l -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(size)
                        .background(Color(0xFF111111), CircleShape)
                        .border(1.5.dp, Color(0xFFFFCC34), CircleShape),
            ) { Text("M", color = Color.White, fontSize = word, fontWeight = FontWeight.Black, maxLines = 1) }
        }

        l == "anilist" -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(size).background(Color(0xFF152232), RoundedCornerShape(3.dp)),
            ) { Text("A", color = Color(0xFF02A9FF), fontSize = word * 1.2f, fontWeight = FontWeight.Black, maxLines = 1) }
        }

        else -> {
            Text(
                text = label,
                color = BenflixColors.Soft,
                fontSize = word * 1.3f,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MarkWord(
    text: String,
    box: Color,
    ink: Color,
    size: TextUnit,
    shape: RoundedCornerShape,
) {
    Text(
        text = text,
        color = ink,
        fontSize = size,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        modifier = Modifier.background(box, shape).padding(horizontal = 3.dp, vertical = 1.dp),
    )
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
