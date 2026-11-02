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
                    var extras =
                        SeerrExtras(
                            ageRating = details?.let { if (isMovie) movieAgeRating(it) else tvAgeRating(it) },
                            countries = details?.let(::countries).orEmpty(),
                            tags = details?.let(::tags).orEmpty(),
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
                                imdb = imdb?.get("criticsScore")?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0 },
                                rtCritics = rt?.get("criticsScore")?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
                                rtAudience = rt?.get("audienceScore")?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
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

        private fun JsonElement?.str(): String? = (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

        private fun preferred(list: List<JsonObject>): JsonObject? {
            val code = { o: JsonObject -> o["iso_3166_1"].str() ?: o["iso31661"].str() }
            return list.firstOrNull { code(it) == Locale.getDefault().country }
                ?: list.firstOrNull { code(it) == "US" }
                ?: list.firstOrNull()
        }

        private fun movieAgeRating(details: JsonObject): String? {
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

        private fun tvAgeRating(details: JsonObject): String? {
            val results =
                (details["contentRatings"] as? JsonObject)
                    ?.get("results")
                    ?.let { it as? JsonArray }
                    ?.mapNotNull { it as? JsonObject }
                    ?.filter { !it["rating"].str().isNullOrBlank() }
                    .orEmpty()
            return preferred(results)?.get("rating").str()
        }

        private fun countries(details: JsonObject): List<String> {
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

        private fun tags(details: JsonObject): List<String> =
            (details["keywords"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.get("name").str() }
                ?.distinct()
                ?.take(14)
                .orEmpty()
    }

private val GROWN_UP_WORDS =
    listOf("hentai", "erotic", "sex", "nudity", "porn", "softcore", "ecchi", "rape", "incest", "gore", "torture")

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
        if (e.tags.isNotEmpty()) {
            val grownUp = e.tags.filter(::isGrownUpTag)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (grownUp.isNotEmpty()) Pill("⚠ Adult content", background = Color(0xFFB3261E))
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
