package com.github.damontecres.wholphin.services

import kotlinx.coroutines.CancellationException
import okhttp3.Interceptor
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix "Not for me": each profile can hide genres it never wants to see (like Horror).
 * Movies and shows in those genres are taken out of every list Benflix shows (Home rows,
 * the banner, Surprise me, Tonight's pick, library pages). Search still finds them on purpose.
 *
 * The list is saved in the person's own settings on the server, so it follows them to every TV.
 */
object HiddenGenres {
    const val PREF_KEY = "benflix.hiddenGenres"

    /** Lower-case genre names hidden for the person signed in now */
    @Volatile var hidden: Set<String> = emptySet()

    private val LIST_PATH =
        Regex("(?i)(Items(/Latest|/Resume|/Suggestions)?|/Shows/NextUp|/Shows/Upcoming|/Similar)$")

    /** Only movies & shows are checked; an episode's genres are usually empty */
    private val CHECKED_TYPES = setOf("Movie", "Series", "BoxSet", "Video")

    fun decode(saved: String?): Set<String> =
        saved
            ?.split('|')
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

    fun encode(genres: Set<String>): String = genres.joinToString("|") { it.trim().lowercase() }

    val interceptor =
        Interceptor { chain ->
            val request = chain.request()
            val hide = hidden
            val url = request.url
            if (hide.isEmpty() ||
                KidsMode.isActive ||
                request.method != "GET" ||
                !LIST_PATH.containsMatchIn(url.encodedPath) ||
                // Search finds hidden genres on purpose
                url.queryParameter("searchTerm") != null ||
                // Asking for exact titles (like a details page) is never filtered
                url.queryParameter("ids") != null
            ) {
                return@Interceptor chain.proceed(request)
            }
            // Make sure the answer says each title's genres
            val fields = url.queryParameter("fields") ?: url.queryParameter("Fields")
            val newUrl =
                if (fields == null) {
                    url.newBuilder().addQueryParameter("fields", "Genres").build()
                } else if (!fields.contains("Genres", ignoreCase = true)) {
                    url
                        .newBuilder()
                        .removeAllQueryParameters("fields")
                        .removeAllQueryParameters("Fields")
                        .addQueryParameter("fields", "$fields,Genres")
                        .build()
                } else {
                    url
                }
            val response = chain.proceed(request.newBuilder().url(newUrl).build())
            val body = response.body
            val contentType = body.contentType()
            if (!response.isSuccessful || contentType?.subtype?.contains("json") != true) return@Interceptor response
            val text = body.string()
            val filtered =
                try {
                    filter(text, hide)
                } catch (ex: Exception) {
                    Timber.w(ex, "Hidden genres: could not read the answer")
                    text
                }
            response.newBuilder().body(filtered.toResponseBody(contentType)).build()
        }

    private fun filter(
        text: String,
        hide: Set<String>,
    ): String {
        val trimmed = text.trimStart()
        return if (trimmed.startsWith("[")) {
            keep(JSONArray(trimmed), hide).toString()
        } else {
            val obj = JSONObject(trimmed)
            obj.optJSONArray("Items")?.let { obj.put("Items", keep(it, hide)) }
            obj.toString()
        }
    }

    private fun keep(
        array: JSONArray,
        hide: Set<String>,
    ): JSONArray {
        val result = JSONArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i)
            if (item == null || item.optString("Type") !in CHECKED_TYPES) {
                result.put(array.get(i))
                continue
            }
            val genres = item.optJSONArray("Genres")
            var hit = false
            if (genres != null) {
                for (g in 0 until genres.length()) {
                    if (genres.optString(g).trim().lowercase() in hide) {
                        hit = true
                        break
                    }
                }
            }
            if (!hit) result.put(array.get(i))
        }
        return result
    }
}

/** Loads and saves a person's hidden genres on the server */
@Singleton
class HiddenGenresStore
    @Inject
    constructor(
        private val displayPreferencesService: DisplayPreferencesService,
    ) {
        suspend fun load(userId: UUID): Set<String> {
            val genres =
                try {
                    HiddenGenres.decode(displayPreferencesService.getDisplayPreferences(userId).customPrefs[HiddenGenres.PREF_KEY])
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Couldn't read hidden genres")
                    emptySet()
                }
            HiddenGenres.hidden = genres
            return genres
        }

        suspend fun save(
            userId: UUID,
            genres: Set<String>,
        ) {
            displayPreferencesService.updateDisplayPreferences(userId) {
                if (genres.isEmpty()) remove(HiddenGenres.PREF_KEY) else put(HiddenGenres.PREF_KEY, HiddenGenres.encode(genres))
            }
            HiddenGenres.hidden = genres.map { it.lowercase() }.toSet()
        }
    }
