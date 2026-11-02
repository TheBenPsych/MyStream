package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.SeerrService
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
)

@HiltViewModel
class SeerrExtrasViewModel
    @Inject
    constructor(
        private val seerrService: SeerrService,
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
                        )
                    _extras.value = extras
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
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load extra details for %s", key)
                }
            }
        }
    }

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

internal fun tags(details: JsonObject): List<String> =
    (details["keywords"] as? JsonArray)
        ?.mapNotNull { (it as? JsonObject)?.get("name").str() }
        ?.distinct()
        ?.take(14)
        .orEmpty()

private val GROWN_UP_WORDS =
    listOf("hentai", "erotic", "sex", "nudity", "porn", "softcore", "ecchi", "rape", "incest", "gore", "torture")

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeerrExtrasBlock(
    isMovie: Boolean,
    tmdbId: Int,
    modifier: Modifier = Modifier,
    viewModel: SeerrExtrasViewModel = hiltViewModel(key = "seerr-extras-$isMovie-$tmdbId"),
) {
    LaunchedEffect(isMovie, tmdbId) { viewModel.load(isMovie, tmdbId) }
    val extras by viewModel.extras.collectAsState()
    val e = extras ?: return
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            e.ageRating?.let { Pill(it, border = true) }
            e.imdb?.let { Pill("IMDb ${"%.1f".format(it)}", background = Color(0xFFF5C518), textColor = Color.Black) }
            e.rtCritics?.let { Pill("🍅 Critics $it%", background = if (it >= 60) Color(0xFFFA320A) else Color(0xFF3B7A2A)) }
            e.rtAudience?.let { Pill("🍿 Audience $it%") }
            if (e.countries.isNotEmpty()) Pill("From " + e.countries.joinToString(", "))
        }
        if (e.adult || e.tags.isNotEmpty()) {
            val grownUp = e.tags.filter(::isGrownUpTag)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Benflix: "Adult content" only for porn, hentai, softcore or erotica; other grown-up tags
                // (like gore or nudity) get the softer "Mature themes" note
                if (e.adult) {
                    Pill("🔞 Adult content", background = Color(0xFFB3261E))
                } else if (grownUp.isNotEmpty()) {
                    Pill("⚠ Mature themes", background = Color(0xFF8A4B0F))
                }
                e.tags.forEach { tag ->
                    if (isGrownUpTag(tag)) {
                        Pill(tag, background = Color(0xFFB3261E), small = true)
                    } else {
                        Pill(tag, small = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(
    text: String,
    background: Color = Color(0x33FFFFFF),
    textColor: Color = Color.White,
    border: Boolean = false,
    small: Boolean = false,
) {
    Text(
        text = text,
        color = textColor,
        fontSize = if (small) 12.sp else 14.sp,
        fontWeight = if (small) FontWeight.Normal else FontWeight.Bold,
        modifier =
            Modifier
                .background(if (border) Color(0x55FFFFFF) else background, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
