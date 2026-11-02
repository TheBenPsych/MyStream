package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.setup.seerr.createSeerrApiUrl
import okhttp3.OkHttpClient

/**
 * Wrapper for [SeerrApiClient]. In most cases, you should use [SeerrService] instead.
 */
class SeerrApi(
    private val okHttpClient: OkHttpClient,
) {
    var api: SeerrApiClient =
        SeerrApiClient(
            baseUrl = "",
            apiKey = null,
            okHttpClient = okHttpClient,
        )
        private set

    val active: Boolean get() = api.baseUrl.isNotNullOrBlank() && BuildConfig.DISCOVER_ENABLED

    fun update(
        baseUrl: String,
        apiKey: String?,
    ) {
        api = SeerrApiClient(createSeerrApiUrl(baseUrl), apiKey, okHttpClient)
    }

    /** Benflix: talk to Seerr through the Jellyfin Enhanced plugin on the Jellyfin server */
    fun useJellyfinEnhanced(
        jellyfinUrl: String,
        token: String,
    ) {
        api = SeerrApiClient(jellyfinEnhancedBase(jellyfinUrl), null, okHttpClient, jellyfinToken = token)
    }
}

/** Where Jellyfin Enhanced answers Seerr questions */
fun jellyfinEnhancedBase(jellyfinUrl: String): String = jellyfinUrl.trimEnd('/') + "/JellyfinEnhanced/jellyseerr"
