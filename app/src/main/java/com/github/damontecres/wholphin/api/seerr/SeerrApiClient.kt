package com.github.damontecres.wholphin.api.seerr

import com.github.damontecres.wholphin.api.seerr.infrastructure.ApiClient
import com.github.damontecres.wholphin.services.CantFindBlocked
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import timber.log.Timber

class SeerrApiClient(
    val baseUrl: String,
    private val apiKey: String?,
    okHttpClient: OkHttpClient,
    /**
     * Benflix: set when Seerr is reached through the Jellyfin Enhanced plugin on the Jellyfin server
     * ([baseUrl] is then `{jellyfin}/JellyfinEnhanced/jellyseerr`). The plugin signs in to Seerr for us.
     */
    private val jellyfinToken: String? = null,
) {
    val viaJellyfin: Boolean get() = jellyfinToken.isNotNullOrBlank()

    private val cookieJar = SeerrCookieJar()

    private val client =
        okHttpClient
            .newBuilder()
            .cookieJar(cookieJar)
            .addInterceptor {
                Timber.d("SeerrApiClient: ${it.request().method} ${it.request().url}")
                // Benflix: titles Ben couldn't find can't be asked for again, from any Ask button
                blockedRequestFor(it.request())?.let { key ->
                    Timber.i("Not asking for %s: marked Couldn't find it", key)
                    return@addInterceptor okhttp3.Response
                        .Builder()
                        .request(it.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(403)
                        .message("Couldn't find it")
                        .body("{\"message\":\"Couldn't find it\"}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                it.proceed(
                    it
                        .request()
                        .newBuilder()
                        .apply {
                            if (apiKey.isNotNullOrBlank()) header("X-Api-Key", apiKey)
                            if (jellyfinToken.isNotNullOrBlank()) {
                                header("Authorization", "MediaBrowser Client=\"Benflix\", Token=\"$jellyfinToken\"")
                                jellyfinEnhancedUrl(it.request().url)?.let { newUrl -> url(newUrl) }
                            }
                        }.build(),
                )
            }.build()

    val hasValidCredentials: Boolean
        get() =
            apiKey.isNotNullOrBlank() ||
                jellyfinToken.isNotNullOrBlank() ||
                cookieJar.hasValidCredentials(baseUrl)

    /** Benflix: fetch any Seerr address as plain JSON text (for fields the generated models don't have) */
    fun getRaw(path: String): String? {
        val request =
            okhttp3.Request
                .Builder()
                .url(baseUrl.trimEnd('/') + path)
                .build()
        return client.newCall(request).execute().use { if (it.isSuccessful) it.body.string() else null }
    }

    /** Benflix: send any Seerr address with a method and optional JSON body. Returns the HTTP code (0 = no answer). */
    fun sendRaw(
        method: String,
        path: String,
        json: String? = null,
    ): Int {
        val body = json?.toRequestBody("application/json".toMediaType())
        val request =
            okhttp3.Request
                .Builder()
                .url(baseUrl.trimEnd('/') + path)
                .method(method, body ?: if (method == "POST" || method == "PUT") "".toRequestBody() else null)
                .build()
        return try {
            client.newCall(request).execute().use { it.code }
        } catch (ex: java.io.IOException) {
            Timber.w(ex, "Seerr %s %s failed", method, path)
            0
        }
    }

    private fun <T : ApiClient> create(initializer: (String, Call.Factory) -> T): Lazy<T> =
        lazy {
            initializer.invoke(baseUrl, client)
        }

    val authApi by create(::AuthApi)
    val blacklistApi by create(::BlacklistApi)
    val collectionApi by create(::CollectionApi)
    val issueApi by create(::IssueApi)
    val mediaApi by create(::MediaApi)
    val moviesApi by create(::MoviesApi)
    val otherApi by create(::OtherApi)
    val overrideruleApi by create(::OverrideruleApi)
    val personApi by create(::PersonApi)
    val publicApi by create(::PublicApi)
    val requestApi by create(::RequestApi)
    val searchApi by create(::SearchApi)
    val serviceApi by create(::ServiceApi)
    val settingsApi by create(::SettingsApi)
    val tmdbApi by create(::TmdbApi)
    val tvApi by create(::TvApi)
    val usersApi by create(::UsersApi)
    val watchlistApi by create(::WatchlistApi)
}

/**
 * Jellyfin Enhanced keeps approve / decline under its own "arr" address instead of "jellyseerr"
 */
private fun jellyfinEnhancedUrl(url: HttpUrl): HttpUrl? {
    val path = url.encodedPath
    APPROVE_PATH.find(path)?.let { match ->
        val (root, id, answer) = match.destructured
        return url
            .newBuilder()
            .encodedPath("$root/arr/requests/$id/$answer")
            .build()
    }
    // Jellyfin Enhanced drops a genre given as ?genre=, but has its own genre address (one genre at a time)
    if (DISCOVER_PATH.matches(path)) {
        val genres =
            url
                .queryParameter("genre")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        if (genres.isNotEmpty()) {
            // For anime (Animation + another genre) the anime keyword already keeps it to anime
            val pick = genres.lastOrNull { it != ANIMATION_GENRE } ?: genres.first()
            return url
                .newBuilder()
                .encodedPath("$path/genre/$pick")
                .removeAllQueryParameters("genre")
                .build()
        }
    }
    return null
}

/** Benflix: the "Couldn't find it" key when this is a new request for a blocked title */
private fun blockedRequestFor(request: okhttp3.Request): String? {
    if (request.method != "POST" ||
        !request.url.encodedPath
            .trimEnd('/')
            .endsWith("/request")
    ) {
        return null
    }
    val blocked = CantFindBlocked.keys.value
    if (blocked.isEmpty()) return null
    return try {
        val buffer = okio.Buffer()
        request.body?.writeTo(buffer) ?: return null
        val json =
            kotlinx.serialization.json.Json
                .parseToJsonElement(buffer.readUtf8()) as? kotlinx.serialization.json.JsonObject
        val type = (json?.get("mediaType") as? kotlinx.serialization.json.JsonPrimitive)?.content
        val id = (json?.get("mediaId") as? kotlinx.serialization.json.JsonPrimitive)?.content
        val key = (if (type == "movie") "MOVIE" else "TV") + "-$id"
        key.takeIf { type != null && id != null && it in blocked }
    } catch (ex: Exception) {
        null
    }
}

private val DISCOVER_PATH = Regex("^.*/jellyseerr/discover/(movies|tv)$")
private const val ANIMATION_GENRE = "16"

private val APPROVE_PATH = Regex("^(.*)/jellyseerr/request/(\\d+)/(approve|decline)$")

private class SeerrCookieJar : CookieJar {
    private val cookies = mutableMapOf<String, List<Cookie>>()

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        cookies
            .filter { it.name == "connect.sid" }
            .groupBy { it.domain }
            .forEach { (domain, cookies) ->
                this.cookies[domain] = cookies
            }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = this.cookies[url.host].orEmpty()

    fun hasValidCredentials(baseUrl: String): Boolean =
        baseUrl.toHttpUrlOrNull()?.host?.let { domain ->
            cookies[domain]?.any { it.expiresAt > System.currentTimeMillis() }
        } == true
}
