package com.github.damontecres.wholphin.ui.door

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.github.damontecres.wholphin.data.model.BaseItem
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import java.util.UUID

/*
 * Ben 2026-11-23 (TV photo): an episode card (Keep watching, Up next) showed no scores at the top right, because
 * the scores belong to the whole show. The top now shows the show's own scores (shows and anime alike). Each show
 * is asked for once and kept while the app is open, so moving along a row never waits on the server twice.
 */

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SeriesScoresEntryPoint {
    fun api(): ApiClient
}

private object SeriesCache {
    val shows = java.util.concurrent.ConcurrentHashMap<UUID, BaseItem>()
}

/** The show an episode belongs to (null while it loads, or when [item] isn't an episode) */
@Composable
internal fun rememberSeriesOf(item: BaseItem): BaseItem? {
    val seriesId = item.data.seriesId?.takeIf { item.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE }
    val context = LocalContext.current
    var series by remember(seriesId) { mutableStateOf(seriesId?.let { SeriesCache.shows[it] }) }
    LaunchedEffect(seriesId) {
        if (seriesId == null || series != null) return@LaunchedEffect
        series =
            try {
                BaseItem(api(context).userLibraryApi.getItem(seriesId).content).also { SeriesCache.shows[seriesId] = it }
            } catch (ex: Exception) {
                if (ex is kotlinx.coroutines.CancellationException) throw ex
                null
            }
    }
    return series
}

private fun api(context: Context): ApiClient =
    EntryPointAccessors.fromApplication(context.applicationContext, SeriesScoresEntryPoint::class.java).api()
