package com.github.damontecres.wholphin.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Benflix Kids profile. It uses a grown-up's sign-in, but while it's on every list of movies & shows
 * asks the server for kid ratings only, and anything not rated for kids is removed from the answer.
 *
 * Grown-up profiles can set a PIN in Settings so kids can't switch back to them.
 */
object KidsMode {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    val isActive: Boolean
        get() = _active.value

    fun set(on: Boolean) {
        if (_active.value != on) Timber.i("Kids mode: %s", on)
        seriesRatings.clear()
        _active.value = on
    }

    /** Highest rating the server is asked for */
    private const val MAX_RATING = "PG"

    /** Ratings allowed in Kids (US movie & TV, plus a few common others) */
    private val ALLOWED_RATINGS =
        setOf(
            "G",
            "PG",
            "TV-Y",
            "TV-Y7",
            "TV-Y7-FV",
            "TV-G",
            "TV-PG",
            "U",
            "UC",
            "ALL",
            "APPROVED",
            "0",
            "6",
            "7",
            "FSK-0",
            "FSK-6",
        )

    /** Item types that are checked. Anything of these types without a kid rating is hidden. */
    private val RATED_TYPES =
        setOf("Movie", "Series", "Episode", "Season", "Video", "Trailer", "MusicVideo", "BoxSet")

    /** Episodes & seasons often have no rating of their own, so they use their show's rating */
    private val USES_SERIES_RATING = setOf("Episode", "Season")

    /** Server addresses that return lists of movies & shows */
    private val LIST_PATH =
        Regex(
            "(?i)(Items(/Latest|/Resume|/Suggestions)?|/Shows/NextUp|/Shows/Upcoming|/Shows/[^/]+/(Episodes|Seasons)|" +
                "/Similar|/Search/Hints)$",
        )

    /** The part of a server address before the API path, e.g. "/jellyfin" */
    private val API_PATH = Regex("(?i)/(Users/[^/]+/Items|UserItems|Items|Shows|Search)(/.*)?$")

    /** Ratings of shows already looked up, by show id */
    private val seriesRatings = ConcurrentHashMap<String, String>()

    val interceptor =
        Interceptor { chain ->
            val request = chain.request()
            if (!isActive || request.method != "GET" || !LIST_PATH.containsMatchIn(request.url.encodedPath)) {
                return@Interceptor chain.proceed(request)
            }
            val builder = request.url.newBuilder()
            if (request.url.queryParameter("maxOfficialRating") == null) {
                builder.addQueryParameter("maxOfficialRating", MAX_RATING)
            }
            // Let the server drop unrated movies & shows too, so pages come back full
            // (otherwise long grids end up with blank gaps). Not for episodes & seasons,
            // which usually have no rating of their own and are checked against their show.
            val types = request.url.queryParameter("includeItemTypes")
            if (types != null &&
                !types.contains("Episode", ignoreCase = true) &&
                !types.contains("Season", ignoreCase = true) &&
                request.url.queryParameter("hasParentalRating") == null
            ) {
                builder.addQueryParameter("hasParentalRating", "true")
            }
            val url = builder.build()
            val response = chain.proceed(request.newBuilder().url(url).build())
            filterResponse(chain, request, response)
        }

    private fun filterResponse(
        chain: Interceptor.Chain,
        request: Request,
        response: Response,
    ): Response {
        val body = response.body
        val contentType = body.contentType()
        if (!response.isSuccessful || contentType?.subtype?.contains("json") != true) return response
        val text = body.string()
        val filtered =
            try {
                filterJson(chain, request, text)
            } catch (ex: Exception) {
                // When in doubt, show nothing rather than something that isn't for kids
                Timber.w(ex, "Kids filter could not read response")
                if (text.trimStart().startsWith("[")) "[]" else """{"Items":[],"TotalRecordCount":0}"""
            }
        return response.newBuilder().body(filtered.toResponseBody(contentType)).build()
    }

    private fun filterJson(
        chain: Interceptor.Chain,
        request: Request,
        text: String,
    ): String {
        val trimmed = text.trimStart()
        return if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            lookUpSeriesRatings(chain, request, listOf(array))
            filterArray(array).toString()
        } else {
            val obj = JSONObject(trimmed)
            val arrays = listOf("Items", "SearchHints").mapNotNull { key -> obj.optJSONArray(key)?.let { key to it } }
            lookUpSeriesRatings(chain, request, arrays.map { it.second })
            arrays.forEach { (key, array) -> obj.put(key, filterArray(array)) }
            obj.toString()
        }
    }

    /**
     * Ask the server for the ratings of the shows that unrated episodes & seasons belong to
     */
    private fun lookUpSeriesRatings(
        chain: Interceptor.Chain,
        request: Request,
        arrays: List<JSONArray>,
    ) {
        val missing = mutableSetOf<String>()
        arrays.forEach { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("Type") in USES_SERIES_RATING && ratingOf(item) == null) {
                    item.optString("SeriesId").takeIf { it.isNotBlank() && !seriesRatings.containsKey(it) }?.let(missing::add)
                }
            }
        }
        if (missing.isEmpty()) return
        val basePath = request.url.encodedPath.replace(API_PATH, "")
        val url =
            request.url
                .newBuilder()
                .encodedPath("$basePath/Items")
                .query(null)
                .addQueryParameter("ids", missing.joinToString(","))
                .build()
        chain.proceed(request.newBuilder().url(url).build()).use { response ->
            if (!response.isSuccessful) return
            val items = JSONObject(response.body.string()).optJSONArray("Items") ?: return
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                seriesRatings[item.optString("Id")] = ratingOf(item) ?: ""
            }
        }
    }

    private fun filterArray(array: JSONArray): JSONArray {
        val result = JSONArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i)
            if (item == null || isAllowed(item)) result.put(array.get(i))
        }
        return result
    }

    private fun ratingOf(item: JSONObject): String? =
        if (item.isNull("OfficialRating")) null else item.optString("OfficialRating").trim().takeIf { it.isNotEmpty() }

    private fun isAllowed(item: JSONObject): Boolean {
        val type = item.optString("Type")
        if (type !in RATED_TYPES) return true
        val rating =
            ratingOf(item)
                ?: if (type in USES_SERIES_RATING) seriesRatings[item.optString("SeriesId")]?.takeIf { it.isNotEmpty() } else null
        // No rating at all means the server can't tell what it is, so it stays out
        return rating != null && rating.uppercase() in ALLOWED_RATINGS
    }
}
