@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.ui.detail.series

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID


/** Where a show page opens: a season (and the episode that was picked). The old season page is gone, see SeriesDetails. */
@Serializable
data class SeasonEpisode(
    val season: Int,
    val episode: Int,
)

@Serializable
data class SeasonEpisodeIds(
    val seasonId: UUID,
    val seasonNumber: Int?,
    val episodeId: UUID?,
    val episodeNumber: Int?,
)

@Serializable
data class SeriesOverviewPosition(
    val seasonTabIndex: Int,
    val episodeRowIndex: Int,
)
