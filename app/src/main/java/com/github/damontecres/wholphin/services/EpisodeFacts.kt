package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Benflix: small facts about episodes, worked out from a show's episode list:
 * - SEASON FINALE / SERIES FINALE tags on episode cards
 * - "3 left in season" on Continue watching cards
 */
object EpisodeFacts {
    private val _finale = MutableStateFlow<Map<UUID, String>>(emptyMap())
    val finale: StateFlow<Map<UUID, String>> = _finale

    private val _seasonLeft = MutableStateFlow<Map<UUID, Int>>(emptyMap())
    val seasonLeft: StateFlow<Map<UUID, Int>> = _seasonLeft

    /** Something was marked watched in this show (series id), so pages showing it can refresh */
    private val _watchedChanged = MutableSharedFlow<UUID>(extraBufferCapacity = 4)
    val watchedChanged: SharedFlow<UUID> = _watchedChanged

    fun notifyWatchedChanged(seriesId: UUID) {
        _watchedChanged.tryEmit(seriesId)
    }

    internal fun addFinale(found: Map<UUID, String>) {
        if (found.isNotEmpty()) _finale.update { it + found }
    }

    internal fun addSeasonLeft(found: Map<UUID, Int>) {
        if (found.isNotEmpty()) _seasonLeft.update { it + found }
    }

    /** Looks up how many episodes are left in the season for each Continue watching episode */
    suspend fun loadSeasonLeft(
        api: ApiClient,
        userId: UUID?,
        items: List<BaseItem>,
    ) {
        val found = mutableMapOf<UUID, Int>()
        items
            .filter { it.type == BaseItemKind.EPISODE && it.data.seasonId != null && it.data.seriesId != null }
            .take(20)
            .forEach { ep ->
                try {
                    val episodes =
                        api.tvShowsApi
                            .getEpisodes(
                                GetEpisodesRequest(
                                    seriesId = ep.data.seriesId!!,
                                    seasonId = ep.data.seasonId,
                                    userId = userId,
                                    enableUserData = true,
                                ),
                            ).content.items
                    val index = episodes.indexOfFirst { it.id == ep.id }
                    if (index >= 0) {
                        // This one plus the unwatched ones after it
                        found[ep.id] = 1 + episodes.drop(index + 1).count { it.userData?.played != true }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.v(ex, "Couldn't count the season for %s", ep.id)
                }
            }
        addSeasonLeft(found)
    }
}

/** "SEASON FINALE" / "SERIES FINALE" for this episode, or null */
@Composable
fun finaleTagFor(item: BaseItem?): String? {
    if (item?.type != BaseItemKind.EPISODE) return null
    return EpisodeFacts.finale.collectAsState().value[item.id]
}

/** How many episodes are left in this episode's season (counting this one), or null */
@Composable
fun seasonLeftFor(item: BaseItem?): Int? {
    if (item?.type != BaseItemKind.EPISODE) return null
    return EpisodeFacts.seasonLeft.collectAsState().value[item.id]
}

/** Benflix: how far through a show this profile is */
data class ShowProgress(
    /** Unwatched episodes (not counting specials) */
    val episodesLeft: Int,
    /** Time left to watch, in minutes */
    val minutesLeft: Long,
    /** The first episode, for "Rewatch from the start" */
    val firstEpisodeId: UUID?,
    /** Watched at least part of an episode */
    val started: Boolean,
)

/**
 * Goes through all of a show's episodes once: tags its finales, and works out how much is left to watch.
 */
suspend fun loadShowProgress(
    api: ApiClient,
    series: BaseItemDto,
    userId: UUID?,
): ShowProgress? =
    try {
        val episodes =
            api.tvShowsApi
                .getEpisodes(
                    GetEpisodesRequest(
                        seriesId = series.id,
                        userId = userId,
                        enableUserData = true,
                    ),
                ).content.items
                .filter { (it.parentIndexNumber ?: 0) > 0 }
        val seasons = episodes.groupBy { it.parentIndexNumber ?: 0 }
        val lastSeason = seasons.keys.maxOrNull()
        val ended = series.status.equals("Ended", true)
        val tags = mutableMapOf<UUID, String>()
        seasons.forEach { (season, eps) ->
            val last = eps.maxByOrNull { it.indexNumber ?: 0 } ?: return@forEach
            when {
                season == lastSeason && ended -> {
                    tags[last.id] = "SERIES FINALE"
                }

                // The newest season of a running show may still be airing, so its last episode isn't known yet
                season == lastSeason -> {}

                else -> {
                    tags[last.id] = "SEASON FINALE"
                }
            }
        }
        EpisodeFacts.addFinale(tags)
        val left = episodes.filter { it.userData?.played != true }
        val ticksLeft =
            left.sumOf { ep ->
                ((ep.runTimeTicks ?: 0L) - (ep.userData?.playbackPositionTicks ?: 0L)).coerceAtLeast(0L)
            }
        ShowProgress(
            episodesLeft = left.size,
            minutesLeft = ticksLeft / 600_000_000L,
            firstEpisodeId = episodes.firstOrNull()?.id,
            started = episodes.any { it.userData?.played == true || (it.userData?.playbackPositionTicks ?: 0L) > 0L },
        )
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.w(ex, "Couldn't go through the episodes of %s", series.id)
        null
    }

/** "5 episodes left · 3 h 40 m" */
fun ShowProgress.words(): String? {
    if (!started || episodesLeft <= 0) return null
    val time =
        when {
            minutesLeft <= 0 -> null
            minutesLeft < 60 -> "$minutesLeft m"
            minutesLeft % 60 == 0L -> "${minutesLeft / 60} h"
            else -> "${minutesLeft / 60} h ${minutesLeft % 60} m"
        }
    val count = if (episodesLeft == 1) "1 episode left" else "$episodesLeft episodes left"
    return listOfNotNull(count, time).joinToString(" · ")
}

/** Benflix "Mark watched up to here": marks this episode and every unwatched one before it */
@HiltViewModel
class MarkWatchedUpToViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        /** The Jellyfin user is an admin (shows "Wrong match? Fix it") */
        val isAdmin: Boolean get() = GuestMode.isAdmin(serverRepository.currentUserDto)

        fun markUpTo(episode: BaseItem) {
            val seriesId = episode.data.seriesId ?: return
            viewModelScope.launchIO {
                try {
                    val episodes =
                        api.tvShowsApi
                            .getEpisodes(
                                GetEpisodesRequest(
                                    seriesId = seriesId,
                                    userId = serverRepository.currentUser?.id,
                                    enableUserData = true,
                                ),
                            ).content.items
                            .filter { (it.parentIndexNumber ?: 0) > 0 || it.id == episode.id }
                    val index = episodes.indexOfFirst { it.id == episode.id }
                    if (index < 0) return@launchIO
                    val toMark = episodes.take(index + 1).filter { it.userData?.played != true }
                    toMark.forEach { api.playStateApi.markPlayedItem(it.id) }
                    // Benflix: AniList only needs the last one (it never moves backwards)
                    AniListSync.instance?.markedWatched(episode.id)
                    showToast(
                        context,
                        if (toMark.size == 1) "Marked 1 episode as watched" else "Marked ${toMark.size} episodes as watched",
                    )
                    EpisodeFacts.notifyWatchedChanged(seriesId)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Mark watched up to here")
                    showToast(context, "Couldn't mark them as watched")
                }
            }
        }
    }
