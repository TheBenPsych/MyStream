package com.github.damontecres.wholphin.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import timber.log.Timber

/**
 * Benflix Guest profile, for friends who come over. It uses the owner's sign-in, but:
 * - nothing watched is saved (no Continue watching, no "watched" ticks, no progress)
 * - likes, My List and favourites can't be changed
 * - the owner's Continue watching and Next up aren't shown
 */
object GuestMode {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    val isActive: Boolean get() = _active.value

    /** Whether the Guest tile shows on Who's watching on this device (on unless turned off) */
    fun tileOn(context: android.content.Context): Boolean =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean(TILE_KEY, true)

    fun setTileOn(
        context: android.content.Context,
        on: Boolean,
    ) = context
        .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        .edit()
        .putBoolean(TILE_KEY, on)
        .apply()

    private const val PREFS = "benflix_guest"
    private const val TILE_KEY = "tile"

    fun set(on: Boolean) {
        if (_active.value != on) Timber.i("Guest mode: %s", on)
        _active.value = on
    }

    /** Saving progress, watched, likes and favourites: quietly skipped */
    private val SAVE_PATH =
        Regex("(?i)/(Sessions/Playing(/Progress|/Stopped|/Ping)?|(Users/[^/]+/|User)(PlayedItems|FavoriteItems|Items/[^/]+/Rating)(/.*)?)$")

    /** The owner's own lists: shown empty */
    private val PRIVATE_LIST_PATH = Regex("(?i)/(Users/[^/]+/Items/Resume|UserItems/Resume|Shows/NextUp)$")

    /** Asking for things the owner has played or rated: shown empty */
    private const val EMPTY_LIST = """{"Items":[],"TotalRecordCount":0,"StartIndex":0}"""

    val interceptor =
        Interceptor { chain ->
            val request = chain.request()
            if (!isActive) return@Interceptor chain.proceed(request)
            val path = request.url.encodedPath
            when {
                request.method != "GET" && SAVE_PATH.containsMatchIn(path) -> {
                    fake(request, 204, "")
                }

                request.method == "GET" &&
                    (
                        PRIVATE_LIST_PATH.containsMatchIn(path) ||
                            request.url.queryParameter("isPlayed") == "true" ||
                            request.url.queryParameter("isFavorite") == "true" ||
                            request.url.queryParameter("filters")?.contains("IsPlayed") == true ||
                            request.url.queryParameter("filters")?.contains("IsResumable") == true ||
                            request.url.queryParameter("filters")?.contains("IsFavorite") == true ||
                            request.url.queryParameter("filters")?.contains("Likes") == true
                    ) -> {
                    fake(request, 200, EMPTY_LIST)
                }

                else -> {
                    chain.proceed(request)
                }
            }
        }

    private fun fake(
        request: okhttp3.Request,
        code: Int,
        body: String,
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 204) "No Content" else "OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
}
