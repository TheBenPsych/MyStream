package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.ui.util.StringProvider
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto

/*
 * Benflix Home: what gets saved on the device between app starts (the last Home, the anime list,
 * the "On this day" list and the collection index), so Home can show up instantly.
 */

/** What Home looked like last time, saved so it can be shown instantly when the app opens */
@Serializable
internal data class HomeSnapshot(
    val rows: List<SnapshotServerRow>,
    val featured: List<SnapshotFeatured> = emptyList(),
    val trending: List<BaseItem> = emptyList(),
    val themed: SnapshotRow? = null,
    val monthly: List<SnapshotRow> = emptyList(),
    val because: SnapshotRow? = null,
    val becauseMore: SnapshotRow? = null,
    val collections: SnapshotRow? = null,
    val newest: SnapshotRow? = null,
    val myList: SnapshotRow? = null,
    val genreRows: List<SnapshotRow> = emptyList(),
    val animeTop: List<BaseItem> = emptyList(),
    val animeRows: List<SnapshotRow> = emptyList(),
) {
    fun applyTo(state: HomeState): HomeState {
        fun SnapshotRow?.row() = this?.let { ThemedRow(it.title, it.items) }
        return state.copy(
            featured = featured.map { FeaturedItem(it.item, it.reason, it.becauseOf) },
            trending = trending,
            themed = themed.row(),
            monthly = monthly.mapNotNull { it.row() },
            because = because.row(),
            becauseMore = becauseMore.row(),
            collections = collections.row(),
            newest = newest.row(),
            myList = myList.row(),
            genreRows = genreRows.mapNotNull { it.row() },
            animeTop = animeTop,
            animeRows = animeRows.mapNotNull { it.row() },
        )
    }
}

@Serializable
internal data class SnapshotServerRow(
    val title: StringProvider,
    val items: List<BaseItem>,
    val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    val rowType: HomeRowConfig? = null,
    val showViewMore: Boolean = true,
) {
    fun toState(): HomeRowLoadingState = HomeRowLoadingState.Success(title, items, viewOptions, rowType, showViewMore)
}

@Serializable
internal data class SnapshotRow(
    val title: String,
    val items: List<BaseItem>,
)

@Serializable
internal data class SnapshotFeatured(
    val item: BaseItem,
    val reason: FeaturedReason,
    val becauseOf: String? = null,
)

/** The anime libraries' movie & show ids as saved on disk */
@Serializable
internal data class AnimeIdsFile(
    val ids: List<String>,
)

internal const val ANIME_ID_FILE_MAX_AGE_MS = 24 * 60 * 60 * 1000L

/** Benflix: today's "On this day" movie ids as saved on disk */
@Serializable
internal data class OnThisDayFile(
    val ids: List<String>,
)

internal const val ON_THIS_DAY_FILE_MAX_AGE_MS = 24 * 60 * 60 * 1000L

/** The collection index as saved on disk: movie id to collection id, plus the collections */
@Serializable
internal data class CollectionIndexFile(
    val byMovie: Map<String, String>,
    val collections: List<BaseItemDto>,
)
