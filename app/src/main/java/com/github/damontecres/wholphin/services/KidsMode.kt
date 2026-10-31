package com.github.damontecres.wholphin.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

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

    /** Item types that are checked */
    private val RATED_TYPES = setOf("Movie", "Series", "Episode", "Season", "Video", "Trailer", "MusicVideo")

    /** Item types hidden when they have no rating at all */
    private val MUST_BE_RATED = setOf("Movie", "Series", "Video", "MusicVideo")

    /** Server addresses that return lists of movies & shows */
    private val LIST_PATH =
        Regex(
            "(?i)(/Items(/Latest|/Resume|/Suggestions)?|/Shows/NextUp|/Shows/Upcoming|/Similar|/Search/Hints)$",
        )

    val interceptor =
        Interceptor { chain ->
            val request = chain.request()
            if (!isActive || request.method != "GET" || !LIST_PATH.containsMatchIn(request.url.encodedPath)) {
                return@Interceptor chain.proceed(request)
            }
            val url =
                if (request.url.queryParameter("maxOfficialRating") == null) {
                    request.url
                        .newBuilder()
                        .addQueryParameter("maxOfficialRating", MAX_RATING)
                        .build()
                } else {
                    request.url
                }
            val response = chain.proceed(request.newBuilder().url(url).build())
            filterResponse(response)
        }

    private fun filterResponse(response: Response): Response {
        val body = response.body
        val contentType = body.contentType()
        if (!response.isSuccessful || contentType?.subtype?.contains("json") != true) return response
        val text = body.string()
        val filtered =
            try {
                filterJson(text)
            } catch (ex: Exception) {
                Timber.w(ex, "Kids filter could not read response")
                text
            }
        return response.newBuilder().body(filtered.toResponseBody(contentType)).build()
    }

    private fun filterJson(text: String): String {
        val trimmed = text.trimStart()
        return if (trimmed.startsWith("[")) {
            filterArray(JSONArray(trimmed)).toString()
        } else {
            val obj = JSONObject(trimmed)
            for (key in listOf("Items", "SearchHints")) {
                obj.optJSONArray(key)?.let { obj.put(key, filterArray(it)) }
            }
            obj.toString()
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

    private fun isAllowed(item: JSONObject): Boolean {
        val type = item.optString("Type")
        if (type !in RATED_TYPES) return true
        val rating =
            if (item.isNull("OfficialRating")) null else item.optString("OfficialRating").trim().takeIf { it.isNotEmpty() }
        return if (rating == null) {
            type !in MUST_BE_RATED
        } else {
            rating.uppercase() in ALLOWED_RATINGS
        }
    }
}
