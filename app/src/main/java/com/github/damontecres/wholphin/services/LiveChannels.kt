package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.GetProgramsDto
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetLiveTvChannelsRequest
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * The free live TV channels on the server (Jellyfin Live TV, filled by server/benflix_livetv.py),
 * as rows for the Channels guide. Servers without Live TV just give no rows.
 */
object LiveChannels {
    private var cached: List<ChannelSchedule> = emptyList()
    private var cachedAt = 0L
    private var cachedFor: UUID? = null

    /** [onProgress] gets the channels as soon as the list is in, then again as each part of the guide arrives */
    suspend fun load(
        api: ApiClient,
        userId: UUID?,
        onProgress: (List<ChannelSchedule>) -> Unit = {},
    ): List<ChannelSchedule> {
        val now = System.currentTimeMillis()
        if (userId == cachedFor && now - cachedAt < FRESH_MS) return cached
        val dtos =
            try {
                api.liveTvApi
                    .getLiveTvChannels(
                        GetLiveTvChannelsRequest(
                            userId = userId,
                            enableImages = true,
                            addCurrentProgram = false,
                        ),
                    ).content.items
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                // No Live TV on this server, or this person isn't allowed to watch it
                Timber.i(ex, "No live channels")
                emptyList()
            }
        if (dtos.isEmpty()) {
            remember(userId, emptyList())
            return emptyList()
        }
        val rows =
            dtos
                .mapIndexed { i, dto ->
                    val item = BaseItem(dto, useSeriesForPrimary = false)
                    ChannelSchedule(
                        number = dto.channelNumber?.toIntOrNull() ?: (NO_NUMBER + i),
                        channel = null,
                        programs = listOf(noGuide(item, now)),
                        live = LiveChannel(dto.id, dto.channelName ?: dto.name.orEmpty(), item),
                    )
                }.sortedBy { it.number }
                .toMutableList()
        onProgress(rows.toList())
        // The guide for the next few hours, a batch of channels at a time
        val from = LocalDateTime.now()
        rows.indices.chunked(GUIDE_BATCH).forEach { batch ->
            try {
                val programs =
                    api.liveTvApi
                        .getPrograms(
                            GetProgramsDto(
                                minEndDate = from,
                                maxStartDate = from.plusHours(GUIDE_HOURS),
                                channelIds = batch.mapNotNull { rows[it].live?.id },
                                sortBy = listOf(ItemSortBy.START_DATE),
                                userId = userId,
                                fields = listOf(ItemFields.OVERVIEW),
                            ),
                        ).content.items
                        .groupBy { it.channelId }
                batch.forEach { i ->
                    val row = rows[i]
                    val list = programs[row.live?.id].orEmpty().mapNotNull { it.toProgram() }
                    if (list.isNotEmpty()) rows[i] = row.copy(programs = list)
                }
                onProgress(rows.toList())
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Live TV guide failed for a batch")
            }
        }
        remember(userId, rows)
        return rows
    }

    private fun remember(
        userId: UUID?,
        rows: List<ChannelSchedule>,
    ) {
        cached = rows
        cachedAt = System.currentTimeMillis()
        cachedFor = userId
    }

    /** A channel with no guide: one long "Live" block around now */
    private fun noGuide(
        channel: BaseItem,
        now: Long,
    ) = ChannelProgram(channel, now - 6 * HOUR_MS, now + 24 * HOUR_MS, noGuide = true)

    private fun BaseItemDto.toProgram(): ChannelProgram? {
        val start = startDate ?: return null
        val end = endDate ?: return null
        val zone = ZoneId.systemDefault()
        return ChannelProgram(
            BaseItem(this, useSeriesForPrimary = false),
            start.atZone(zone).toInstant().toEpochMilli(),
            end.atZone(zone).toInstant().toEpochMilli(),
        )
    }

    private const val FRESH_MS = 15L * 60_000L
    private const val HOUR_MS = 3_600_000L
    private const val GUIDE_HOURS = 4L
    private const val GUIDE_BATCH = 100
    private const val NO_NUMBER = 5000
}
