package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.AniListService
import com.github.damontecres.wholphin.services.MalFacts
import com.github.damontecres.wholphin.services.MalScores
import com.github.damontecres.wholphin.services.OmdbScores
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Benflix: extra facts for a movie or show you could ask for: age rating, where it's from, IMDb and Rotten
 * Tomatoes scores, and its tags (TMDB keywords), with grown-up tags marked in red.
 */
data class SeerrExtras(
    val ageRating: String? = null,
    val countries: List<String> = emptyList(),
    val imdb: Double? = null,
    val rtCritics: Int? = null,
    val rtAudience: Int? = null,
    val tags: List<String> = emptyList(),
    // Benflix: porn, hentai, softcore or erotica (see isAdultContent)
    val adult: Boolean = false,
    // Benflix: the language it was made in ("Japanese"), and whether that needs subtitles here
    val language: String? = null,
    val needsSubtitles: Boolean = false,
    // Benflix: "Director" (movies) or "Created by" (shows), and their names
    val makerLabel: String? = null,
    val maker: String? = null,
    // Benflix: anime get AniList + MyAnimeList scores instead of IMDb + Rotten Tomatoes
    val anime: Boolean = false,
    /** AniList fan score, 0 to 100 */
    val aniList: Int? = null,
    /** MyAnimeList score, 0 to 10 */
    val mal: Double? = null,
    /** TMDB's user score, 0 to 100 (used when Rotten Tomatoes has no audience score) */
    val tmdbUsers: Int? = null,
)

@HiltViewModel
class SeerrExtrasViewModel
    @Inject
    constructor(
        private val seerrService: SeerrService,
        private val omdbScores: OmdbScores,
        private val aniList: AniListService,
        private val malScores: MalScores,
    ) : ViewModel() {
        private val _extras = MutableStateFlow<SeerrExtras?>(null)
        val extras: StateFlow<SeerrExtras?> = _extras
        private var loadedFor: String? = null

        fun load(
            isMovie: Boolean,
            tmdbId: Int,
        ) {
            val key = "$isMovie-$tmdbId"
            if (loadedFor == key) return
            loadedFor = key
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val api = seerrService.api
                    val kind = if (isMovie) "movie" else "tv"
                    val details = api.getRaw("/$kind/$tmdbId")?.let { Json.parseToJsonElement(it).jsonObject }
                    val age = details?.let { if (isMovie) movieAgeRating(it) else tvAgeRating(it) }
                    val adult = details?.let { isAdultContent(it, age) } == true
                    if (adult) AdultContentFlags.mark("${if (isMovie) "MOVIE" else "TV"}-$tmdbId")
                    var extras =
                        SeerrExtras(
                            ageRating = age,
                            countries = details?.let(::countries).orEmpty(),
                            tags = details?.let(::tags).orEmpty(),
                            adult = adult,
                            language = details?.let(::originalLanguage),
                            needsSubtitles = details?.let(::needsSubtitles) == true,
                            makerLabel = if (isMovie) "Director" else "Created by",
                            maker = details?.let { if (isMovie) directors(it) else creators(it) },
                            anime = details?.let(::isAnime) == true,
                            tmdbUsers = details?.let(::tmdbUserScore),
                        )
                    _extras.value = extras
                    // Benflix: anime scores from AniList and MyAnimeList
                    if (extras.anime && details != null) {
                        val anime = animeScores(key, isMovie, details)
                        if (anime != null) {
                            extras = extras.copy(aniList = anime.first, mal = anime.second)
                            _extras.value = extras
                        }
                    }
                    // Scores come from a second, slower lookup
                    val ratings =
                        try {
                            if (isMovie) {
                                api.getRaw("/movie/$tmdbId/ratingscombined")?.let { Json.parseToJsonElement(it).jsonObject }
                            } else {
                                api.getRaw("/tv/$tmdbId/ratings")?.let { JsonObject(mapOf("rt" to Json.parseToJsonElement(it))) }
                            }
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "No scores for %s", key)
                            null
                        }
                    if (ratings != null) {
                        val rt = ratings["rt"] as? JsonObject
                        val imdb = ratings["imdb"] as? JsonObject
                        extras =
                            extras.copy(
                                imdb =
                                    imdb
                                        ?.get("criticsScore")
                                        ?.jsonPrimitive
                                        ?.doubleOrNull
                                        ?.takeIf { it > 0 },
                                rtCritics =
                                    rt
                                        ?.get("criticsScore")
                                        ?.jsonPrimitive
                                        ?.intOrNull
                                        ?.takeIf { it > 0 },
                                rtAudience =
                                    rt
                                        ?.get("audienceScore")
                                        ?.jsonPrimitive
                                        ?.intOrNull
                                        ?.takeIf { it > 0 },
                            )
                        _extras.value = extras
                    }
                    // Benflix: IMDb and Rotten Tomatoes from OMDb fill in whatever Seerr didn't have
                    // (Seerr never has IMDb for shows, and often misses older shows on Rotten Tomatoes)
                    if (!extras.anime && (extras.imdb == null || extras.rtCritics == null)) {
                        val imdbId = details?.let(::imdbIdOf)
                        val omdb = imdbId?.let { omdbScores.scoresFor(it) }
                        if (omdb != null) {
                            extras =
                                extras.copy(
                                    imdb = extras.imdb ?: omdb.imdb,
                                    rtCritics = extras.rtCritics ?: omdb.rtCritics,
                                )
                            _extras.value = extras
                        }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load extra details for %s", key)
                }
            }
        }

        /** AniList score (0-100) and MyAnimeList score (0-10) for an anime on the Requests pages */
        private suspend fun animeScores(
            key: String,
            isMovie: Boolean,
            details: JsonObject,
        ): Pair<Int?, Double?>? =
            try {
                val names =
                    listOfNotNull(
                        details["title"].str() ?: details["name"].str(),
                        details["originalTitle"].str() ?: details["originalName"].str(),
                    )
                val year =
                    (details["releaseDate"].str() ?: details["firstAirDate"].str())
                        ?.take(4)
                        ?.toIntOrNull()
                val tag = "${if (isMovie) "MOVIE" else "TV"}-${key.substringAfter('-')}"
                val found = aniList.forRequest(tag, names, year, isMovie)
                if (found == null) {
                    null
                } else {
                    // Matches saved before MyAnimeList numbers were kept have none yet: ask AniList
                    val malId = found.malId?.takeIf { it > 0 } ?: if (found.malId == null) aniList.malIdOf(found.id) else null
                    val mal =
                        malId?.let {
                            malScores.fetch(listOf(it))
                            MalFacts.scores.value[it]
                        }
                    found.score to mal
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "No anime scores for %s", key)
                null
            }
    }

/** IMDb id ("tt0214341") from Seerr's movie or show details */
internal fun imdbIdOf(details: JsonObject): String? =
    (details["imdbId"].str() ?: (details["externalIds"] as? JsonObject)?.get("imdbId").str())
        ?.takeIf { it.startsWith("tt") }

/** TMDB's user score as a percent, when enough people voted for it to mean something */
internal fun tmdbUserScore(details: JsonObject): Int? {
    val average = (details["voteAverage"] as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull ?: return null
    val count = (details["voteCount"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: 0
    if (average <= 0 || count < MIN_TMDB_VOTES) return null
    return (average * 10).roundToInt()
}

private const val MIN_TMDB_VOTES = 20

/** Benflix: shared readers for Seerr's movie and show JSON (used here and by the Requests page) */
internal fun JsonElement?.str(): String? = (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

private fun preferred(list: List<JsonObject>): JsonObject? {
    val code = { o: JsonObject -> o["iso_3166_1"].str() ?: o["iso31661"].str() }
    return list.firstOrNull { code(it) == Locale.getDefault().country }
        ?: list.firstOrNull { code(it) == "US" }
        ?: list.firstOrNull()
}

internal fun movieAgeRating(details: JsonObject): String? {
    val results =
        (details["releases"] as? JsonObject)
            ?.get("results")
            ?.let { it as? JsonArray }
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()
    val withRating =
        results.filter { r ->
            (r["release_dates"] as? JsonArray).orEmpty().any {
                !(it as? JsonObject)?.get("certification").str().isNullOrBlank()
            }
        }
    return preferred(withRating)
        ?.get("release_dates")
        ?.jsonArray
        ?.mapNotNull { (it as? JsonObject)?.get("certification").str() }
        ?.firstOrNull { it.isNotBlank() }
}

internal fun tvAgeRating(details: JsonObject): String? {
    val results =
        (details["contentRatings"] as? JsonObject)
            ?.get("results")
            ?.let { it as? JsonArray }
            ?.mapNotNull { it as? JsonObject }
            ?.filter { !it["rating"].str().isNullOrBlank() }
            .orEmpty()
    return preferred(results)?.get("rating").str()
}

internal fun countries(details: JsonObject): List<String> {
    val named =
        (details["productionCountries"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("name").str() }
            .orEmpty()
    if (named.isNotEmpty()) return named.distinct().take(3)
    return (details["originCountry"] as? JsonArray)
        ?.mapNotNull { it.str() }
        ?.map { Locale("", it).displayCountry.ifBlank { it } }
        ?.distinct()
        ?.take(3)
        .orEmpty()
}

private fun originalLanguageCode(details: JsonObject): String? =
    (details["originalLanguage"].str() ?: details["original_language"].str())
        ?.lowercase()
        ?.takeIf { it.isNotBlank() && it != "xx" }

/** Benflix: the language a title was made in, in words ("Japanese"). TMDB writes Cantonese as "cn". */
internal fun originalLanguage(details: JsonObject): String? {
    val code = originalLanguageCode(details) ?: return null
    if (code == "cn") return "Cantonese"
    val spoken =
        (details["spokenLanguages"] as? JsonArray ?: details["spoken_languages"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { (it["iso_639_1"].str() ?: it["iso6391"].str()) == code }
            ?.let { it["englishName"].str() ?: it["english_name"].str() }
    val name = Locale(code).getDisplayLanguage(Locale.getDefault())
    return when {
        name.isNotBlank() && !name.equals(code, ignoreCase = true) -> name.replaceFirstChar { it.titlecase() }
        !spoken.isNullOrBlank() -> spoken
        else -> code.uppercase()
    }
}

/** Benflix: made in a different language from the one this device uses */
internal fun needsSubtitles(details: JsonObject): Boolean {
    val code = originalLanguageCode(details) ?: return false
    return code != Locale.getDefault().language.lowercase()
}

private fun directors(details: JsonObject): String? =
    ((details["credits"] as? JsonObject)?.get("crew") as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?.filter { it["job"].str() == "Director" }
        ?.mapNotNull { it["name"].str()?.takeIf(String::isNotBlank) }
        ?.distinct()
        ?.take(2)
        ?.joinToString(", ")
        ?.takeIf { it.isNotBlank() }

private fun creators(details: JsonObject): String? =
    (details["createdBy"] as? JsonArray ?: details["created_by"] as? JsonArray)
        ?.mapNotNull { (it as? JsonObject)?.get("name").str()?.takeIf(String::isNotBlank) }
        ?.distinct()
        ?.take(2)
        ?.joinToString(", ")
        ?.takeIf { it.isNotBlank() }

internal fun tags(details: JsonObject): List<String> =
    (details["keywords"] as? JsonArray)
        ?.mapNotNull { (it as? JsonObject)?.get("name").str() }
        ?.distinct()
        ?.take(14)
        .orEmpty()

private val GROWN_UP_WORDS =
    listOf("hentai", "erotic", "sex", "nudity", "porn", "softcore", "rape", "incest", "gore", "torture")

/**
 * Benflix: words in TMDB tags that mean the title is basically porn, hentai, softcore or erotica.
 * Kept narrow on purpose, so a normal film that is only *about* the porn business (like Boogie Nights)
 * or just has a sex scene is not marked.
 */
private val ADULT_CONTENT_WORDS =
    listOf(
        "hentai",
        "softcore",
        "soft-core",
        "soft core",
        "erotica",
        "erotic movie",
        "erotic film",
        "pink film",
        "pinku",
        "porn film",
        "porno film",
        "pornographic film",
        "pornographic animation",
        "sex film",
        "sexploitation",
    )

/** Age ratings that only adult films get */
private val ADULT_CONTENT_RATINGS = setOf("X", "XXX", "X18+")

/**
 * Benflix: true when a Seerr movie or show looks like adult content (porn, hentai, softcore, erotica).
 * Three signs, any one is enough: TMDB's own "adult" switch, an X-type age rating, or an adult tag.
 */
fun isAdultContent(
    details: JsonObject,
    ageRating: String?,
): Boolean {
    if (details["adult"].str() == "true") return true
    if (ageRating?.trim()?.uppercase() in ADULT_CONTENT_RATINGS) return true
    return tags(details).any { tag ->
        val lower = tag.lowercase()
        ADULT_CONTENT_WORDS.any { lower.contains(it) }
    }
}

/**
 * Benflix: titles found to be adult content this app run ("MOVIE-123"), so every Seerr card can show
 * the "Adult content" banner once its details have been looked up.
 */
object AdultContentFlags {
    private val _flagged = MutableStateFlow<Set<String>>(emptySet())
    val flagged: StateFlow<Set<String>> = _flagged

    fun mark(key: String) {
        if (key !in _flagged.value) _flagged.update { it + key }
    }
}

/** True for tags a parent would want to know about before adding something */
fun isGrownUpTag(tag: String): Boolean {
    val lower = tag.lowercase()
    return GROWN_UP_WORDS.any { lower.contains(it) }
}

/**
 * Benflix: the facts boxes on a request details page (Ben's pick "A · Fact cards"): one box with the scores,
 * one with Spoken in, Made in, Age rating and Director / Created by. Side by side on TV, stacked on phones.
 * [fallbackCritic] and [fallbackAudience] fill in when the slower score lookup has nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeerrExtrasBlock(
    isMovie: Boolean,
    tmdbId: Int,
    modifier: Modifier = Modifier,
    fallbackCritic: Int? = null,
    fallbackAudience: Int? = null,
    viewModel: SeerrExtrasViewModel = hiltViewModel(key = "seerr-extras-$isMovie-$tmdbId"),
) {
    LaunchedEffect(isMovie, tmdbId) { viewModel.load(isMovie, tmdbId) }
    val extras by viewModel.extras.collectAsState()
    val e = extras ?: return
    val isTv = rememberIsTv()
    val scores =
        buildList {
            // Benflix: movies and shows = IMDb, Rotten Tomatoes critics, then the audience;
            // anime = AniList, MyAnimeList, then the audience
            if (e.anime) {
                e.aniList?.let { add(Score(String.format(Locale.US, "%.1f", it / 10.0), "AniList", ANILIST_BLUE)) }
                e.mal?.let { add(Score(String.format(Locale.US, "%.1f", it), "MyAnimeList", MAL_BLUE)) }
            } else {
                e.imdb?.let { add(Score(String.format(Locale.US, "%.1f", it), "IMDb", IMDB_GOLD)) }
                (e.rtCritics ?: fallbackCritic)?.let {
                    add(Score("$it%", "Critics", if (it >= 60) FRESH_RED else ROTTEN_GREEN))
                }
            }
            val audience = e.rtAudience ?: fallbackAudience
            when {
                audience != null -> add(Score("$audience%", "Audience", Color.White))
                e.tmdbUsers != null -> add(Score("${e.tmdbUsers}%", "Users", Color.White))
            }
        }
    val facts =
        buildList {
            e.language?.let { add(Fact("Spoken in", it, if (e.needsSubtitles) "Needs subtitles" else null)) }
            if (e.countries.isNotEmpty()) add(Fact("Made in", e.countries.joinToString(", "), null))
            e.ageRating?.let { add(Fact("Age rating", it, null)) }
            if (e.makerLabel != null && e.maker != null) add(Fact(e.makerLabel, e.maker, null))
        }
    val grownUp = e.adult || e.tags.any(::isGrownUpTag)
    if (scores.isEmpty() && facts.isEmpty() && !grownUp) return
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        // Benflix: "Adult content" matches the red strip on cards (porn, hentai, erotica); other grown-up
        // tags (ecchi, nudity, gore...) say "Mature content" so the page and the cards agree
        if (grownUp) Pill(if (e.adult) "⚠ Adult content" else "⚠ Mature content", background = Color(0xFFB3261E))
        if (isTv) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                if (scores.isNotEmpty()) ScoresCard(scores, big = true, modifier = Modifier.fillMaxHeight())
                if (facts.isNotEmpty()) FactsCard(facts, big = true, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            if (scores.isNotEmpty()) ScoresCard(scores, big = false, modifier = Modifier.fillMaxWidth())
            if (facts.isNotEmpty()) FactsCard(facts, big = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Benflix: the title's tags under the description, grown-up ones first and in red. Shows 4, and a
 * "+N more" button opens the rest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeerrTagsBlock(
    isMovie: Boolean,
    tmdbId: Int,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    viewModel: SeerrExtrasViewModel = hiltViewModel(key = "seerr-extras-$isMovie-$tmdbId"),
) {
    LaunchedEffect(isMovie, tmdbId) { viewModel.load(isMovie, tmdbId) }
    val extras by viewModel.extras.collectAsState()
    val tags = extras?.tags.orEmpty()
    if (tags.isEmpty()) return
    var expanded by rememberSaveable(isMovie, tmdbId) { mutableStateOf(false) }
    val sorted = remember(tags) { tags.filter(::isGrownUpTag) + tags.filterNot(::isGrownUpTag) }
    val shown = if (expanded) sorted else sorted.take(COLLAPSED_TAGS)
    val hiddenCount = sorted.size - shown.size
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        FactLabel("About", Modifier.padding(end = 4.dp))
        shown.forEach { tag ->
            TagPill(
                text = tag.replaceFirstChar { it.titlecase() },
                grownUp = isGrownUpTag(tag),
            )
        }
        if (sorted.size > COLLAPSED_TAGS) {
            Surface(
                onClick = { expanded = !expanded },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = LABEL_GREY,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black,
                    ),
                modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused() },
            ) {
                Text(
                    text = if (expanded) "Show less" else "+$hiddenCount more",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
    }
}

private const val COLLAPSED_TAGS = 4
private val IMDB_GOLD = Color(0xFFF5C518)
private val ANILIST_BLUE = Color(0xFF3DB4F2)
private val MAL_BLUE = Color(0xFF6A8CD8)
private val FRESH_RED = Color(0xFFFF7A59)
private val ROTTEN_GREEN = Color(0xFF7BC86C)
private val LABEL_GREY = Color(0xFFAEB3BD)
private val CARD_BACKGROUND = Color.White.copy(alpha = .07f)

private data class Score(
    val value: String,
    val label: String,
    val color: Color,
)

private data class Fact(
    val label: String,
    val value: String,
    val note: String?,
)

@Composable
private fun FactLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        color = LABEL_GREY,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
        modifier = modifier,
    )
}

@Composable
private fun ScoresCard(
    scores: List<Score>,
    big: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .background(CARD_BACKGROUND, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        FactLabel("Scores")
        Row(horizontalArrangement = Arrangement.spacedBy(if (big) 24.dp else 0.dp, Alignment.Start)) {
            scores.forEach { score ->
                Column(modifier = if (big) Modifier else Modifier.weight(1f)) {
                    Text(
                        text = score.value,
                        color = score.color,
                        fontSize = if (big) 28.sp else 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = score.label, color = Color(0xFFC9CCD4), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun FactsCard(
    facts: List<Fact>,
    big: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .background(CARD_BACKGROUND, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        facts.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { fact ->
                    Column(modifier = Modifier.weight(1f)) {
                        FactLabel(fact.label)
                        Text(
                            text = fact.value,
                            color = Color.White,
                            fontSize = if (big) 18.sp else 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        fact.note?.let { Text(text = it, color = Color(0xFFFFB3B3), fontSize = 12.sp) }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TagPill(
    text: String,
    grownUp: Boolean,
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 14.sp,
        modifier =
            Modifier
                .background(if (grownUp) Color(0xFFB3261E) else Color.White.copy(alpha = .1f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun Pill(
    text: String,
    background: Color = Color(0x33FFFFFF),
    textColor: Color = Color.White,
) {
    Text(
        text = text,
        color = textColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier
                .background(background, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
