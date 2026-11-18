package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.ui.util.StringProvider
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import kotlinx.serialization.Serializable

// Benflix: rows saved on the device between app starts, so library pages show up instantly

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
internal data class SnapshotFeatured(
    val item: BaseItem,
    val reason: FeaturedReason,
    val becauseOf: String? = null,
)
