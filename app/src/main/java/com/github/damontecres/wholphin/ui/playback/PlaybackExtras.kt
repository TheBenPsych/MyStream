package com.github.damontecres.wholphin.ui.playback

import android.widget.Toast
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.preferences.SubtitleModePreference
import com.github.damontecres.wholphin.services.BenflixAfterCredits
import com.github.damontecres.wholphin.services.FillerKind
import com.github.damontecres.wholphin.services.FillerTags
import com.github.damontecres.wholphin.services.getPreferredLanguage
import com.github.damontecres.wholphin.ui.ItemRowFields
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID

/** Benflix: a few minutes before the end, wake the disk the next episode is on */
internal suspend fun PlaybackViewModel.warmNextNearEnd(itemId: UUID) {
    val prep = playbackPrep ?: return
    while (true) {
        delay(NEXT_CHECK_MS)
        if (this.itemId != itemId) return
        val left =
            withContext(WholphinDispatchers.Main) {
                val duration = player.duration
                if (duration > 0) duration - player.currentPosition else Long.MAX_VALUE
            }
        if (left <= NEXT_PREP_MS) {
            (state.value.nextItem() as? PlaylistItem.Media)?.let { prep.warm(it.item.data) }
            return
        }
    }
}

/**
 * Benflix: if the last episode of this show was watched more than two weeks ago,
 * a short "here's where you left off" card shows before the next one starts.
 */
internal suspend fun PlaybackViewModel.findRecap(item: BaseItemDto): BenflixRecap? {
    if (item.type != BaseItemKind.EPISODE) return null
    val seriesId = item.seriesId ?: return null
    return try {
        withTimeoutOrNull(RECAP_LOOKUP_MS) {
            val episodes =
                api.tvShowsApi
                    .getEpisodes(
                        GetEpisodesRequest(
                            seriesId = seriesId,
                            userId = serverRepository.currentUser?.id,
                            adjacentTo = item.id,
                            fields = listOf(ItemFields.OVERVIEW),
                            enableUserData = true,
                        ),
                    ).content.items
            val index = episodes.indexOfFirst { it.id == item.id }
            val previous = episodes.getOrNull(index - 1)?.takeIf { index > 0 } ?: return@withTimeoutOrNull null
            val lastPlayed = previous.userData?.lastPlayedDate ?: return@withTimeoutOrNull null
            if (lastPlayed.isAfter(LocalDateTime.now().minusDays(RECAP_AFTER_DAYS))) return@withTimeoutOrNull null
            val label =
                listOfNotNull(
                    previous.parentIndexNumber?.let { "Season $it" },
                    previous.indexNumber?.let { "Episode $it" },
                ).joinToString(", ")
            BenflixRecap(label = label, name = previous.name, overview = previous.overview?.takeIf { it.isNotBlank() })
        }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.v(ex, "No recap")
        null
    }
}

/**
 * Benflix "Up next": when a movie's credits start (or near its end), offer the next movie in its
 * collection, or else the first similar movie the profile hasn't watched. A corner card with a
 * countdown; see [BenflixAutoplayChain] for when it stops starting movies by itself.
 */
internal fun PlaybackViewModel.watchForMovieNext(item: BaseItem) {
    movieNextJob?.cancel()
    outroReached = false
    mutableState.update { it.copy(movieNext = null) }
    if (item.type != BaseItemKind.MOVIE) return
    movieNextJob =
        viewModelScope.launchIO {
            val userId = serverRepository.currentUser?.id
            val inCollection =
                try {
                    userId?.let { collectionLookup?.collectionFor(it, item.id) }?.let { collection ->
                        val index = collection.items.indexOfFirst { it.id == item.id }
                        collection.items.drop(index + 1).firstOrNull()
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    null
                }
            val pick =
                inCollection ?: try {
                    api.libraryApi
                        .getSimilarItems(
                            GetSimilarItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                itemId = item.id,
                                fields = ItemRowFields,
                                limit = 20,
                            ),
                        ).content.items
                        .firstOrNull { it.type == BaseItemKind.MOVIE && it.userData?.played != true }
                        ?.let { BaseItem(it, true) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.v(ex, "Couldn't find a movie to watch next")
                    null
                } ?: return@launchIO
            while (isActive) {
                delay(3_000L)
                val (position, duration) = onMain { player.currentPosition to player.duration }
                if (duration > 0 && (outroReached || duration - position <= MOVIE_NEXT_NOTICE_MS)) {
                    mutableState.update { it.copy(movieNext = pick) }
                    break
                }
            }
        }
}

/** This show's intro is always skipped (the set can't be asked about null, so movies are checked first) */
internal fun PlaybackViewModel.alwaysSkipsIntro(): Boolean = currentSeriesId()?.let { it in alwaysSkipIntroSeries } == true

/** This show's credits are always skipped */
internal fun PlaybackViewModel.alwaysSkipsOutro(): Boolean = currentSeriesId()?.let { it in alwaysSkipOutroSeries } == true

internal fun PlaybackViewModel.showNotice(notice: PlayerNotice?) {
    mutableState.update { it.copy(notice = notice) }
}

/** Benflix: the checks that run each time something starts playing */
internal fun PlaybackViewModel.benflixStartChecks(
    item: BaseItem,
    positionMs: Long,
) {
    noticeJob?.cancel()
    soundWatchdog.reset()
    soundWatchdog.check("start")
    pictureWatchdog.check(item.id, "start")
    firstReadyAt = 0L
    startPositionMs = positionMs.coerceAtLeast(0L)
    bufferTimes.clear()
    val pending = BenflixPlayerPrefs.pendingNotice
    BenflixPlayerPrefs.pendingNotice = null
    mutableState.update {
        it.copy(
            notice = pending,
            showDone = null,
            alwaysSkipIntro = item.data.seriesId?.let { id -> id in alwaysSkipIntroSeries } == true,
            alwaysSkipOutro = item.data.seriesId?.let { id -> id in alwaysSkipOutroSeries } == true,
            noCreditsLeft = false,
        )
    }
    watchForBusy(item.id)
    findMissingSubtitles(item)
    if (item.type != BaseItemKind.EPISODE) return
    val seriesId = item.data.seriesId ?: return
    noticeJob =
        viewModelScope.launchIO {
            loadAlwaysSkipIntro()
            mutableState.update {
                it.copy(
                    alwaysSkipIntro = seriesId in alwaysSkipIntroSeries,
                    alwaysSkipOutro = seriesId in alwaysSkipOutroSeries,
                )
            }
            val series =
                try {
                    api.userLibraryApi.getItem(seriesId).content
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.v(ex, "Couldn't get the show")
                    null
                } ?: return@launchIO
            // Don't talk over the "It's been a while" card
            while (isActive && state.value.recap != null) delay(500L)
            if (pending != null) return@launchIO
            if (checkFiller(item, BaseItem(series, false))) return@launchIO
            if (positionMs <= 0 && !shuffleSession && !isPlaylist && !item.played) checkSkipped(item, series)
        }
}

/** Benflix: "You haven't seen episode 4 yet. Watch it first?" */
internal suspend fun PlaybackViewModel.checkSkipped(
    item: BaseItem,
    series: BaseItemDto,
) {
    if (BenflixPlayerPrefs.watchInAnyOrder(series.genres)) return
    // Only once they've started the show (starting somewhere on purpose is fine)
    if ((series.userData?.playedPercentage ?: 0.0) <= 0.0) return
    if ((item.data.parentIndexNumber ?: 0) == 0) return
    try {
        val episodes =
            api.tvShowsApi
                .getEpisodes(
                    GetEpisodesRequest(
                        seriesId = series.id,
                        userId = serverRepository.currentUser?.id,
                        adjacentTo = item.id,
                        enableUserData = true,
                    ),
                ).content.items
        val index = episodes.indexOfFirst { it.id == item.id }
        val previous = episodes.getOrNull(index - 1)?.takeIf { index > 0 } ?: return
        if ((previous.parentIndexNumber ?: 0) == 0 || previous.userData?.played == true) return
        // Benflix: they've just watched it (Next / autoplay moved on from it)
        if (previous.id in movedOnFrom) return
        val label =
            if (previous.parentIndexNumber == item.data.parentIndexNumber) {
                "episode ${previous.indexNumber}"
            } else {
                "season ${previous.parentIndexNumber}, episode ${previous.indexNumber}"
            }
        if (state.value.notice != null || this.itemId != item.id) return
        showNotice(
            PlayerNotice(
                title = "You haven't seen $label yet",
                message = previous.name?.let { "\"$it\". Watch it first?" } ?: "Watch it first?",
                actions = listOf("Watch it first" to NoticeAction.WATCH_SKIPPED, "Not now" to NoticeAction.DISMISS),
                targetId = previous.id,
            ),
        )
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.v(ex, "Couldn't check for a skipped episode")
    }
}

/** Benflix: tell them when an anime episode is filler, and offer to skip it. True if it did something */
internal suspend fun PlaybackViewModel.checkFiller(
    item: BaseItem,
    series: BaseItem,
): Boolean {
    val service = fillerService ?: return false
    if (item.id in BenflixPlayerPrefs.fillerAllowed) return false
    try {
        service.load(api, series, serverRepository.currentUser?.id)
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.v(ex, "No filler list")
        return false
    }
    val kind = FillerTags.tags.value[item.id] ?: return false
    if (this.itemId != item.id) return false
    if (kind == FillerKind.FILLER && BenflixPlayerPrefs.alwaysSkipFiller(context)) {
        val (next, skipped) = nextStoryEpisode(item) ?: return false
        BenflixPlayerPrefs.pendingNotice =
            PlayerNotice(
                title = if (skipped.size == 1) "Skipped a filler episode" else "Skipped ${skipped.size} filler episodes",
                actions =
                    listOf(
                        "Watch it anyway" to NoticeAction.WATCH_FILLER_ANYWAY,
                        "Stop skipping filler" to NoticeAction.STOP_SKIPPING_FILLER,
                    ),
                autoHideMs = 8_000L,
                targetId = skipped.first(),
                extraIds = skipped,
            )
        withContext(WholphinDispatchers.Main) {
            player.pause()
            navigationManager.goBack()
            navigationManager.navigateTo(Destination.Playback(next, 0L))
        }
        return true
    }
    showNotice(
        PlayerNotice(
            title = if (kind == FillerKind.FILLER) "This is a filler episode" else "This episode is part filler",
            message =
                if (kind == FillerKind.FILLER) {
                    "It isn't part of the main story. Want to skip it?"
                } else {
                    "Some of it is main story, some is filler. Want to skip it?"
                },
            actions =
                buildList {
                    add("Skip filler" to NoticeAction.SKIP_FILLER)
                    add("Watch it" to NoticeAction.DISMISS)
                    if (kind == FillerKind.FILLER) add("Always skip filler" to NoticeAction.ALWAYS_SKIP_FILLER)
                },
            autoHideMs = 15_000L,
            targetId = item.id,
        ),
    )
    return true
}

/** The next episode after [item] that's part of the main story, and the filler ones in between (including [item]) */
internal suspend fun PlaybackViewModel.nextStoryEpisode(item: BaseItem): Pair<UUID, List<UUID>>? {
    val seriesId = item.data.seriesId ?: return null
    return try {
        val episodes =
            api.tvShowsApi
                .getEpisodes(
                    GetEpisodesRequest(
                        seriesId = seriesId,
                        userId = serverRepository.currentUser?.id,
                        startItemId = item.id,
                        limit = 150,
                    ),
                ).content.items
        val tags = FillerTags.tags.value
        val after = episodes.dropWhile { it.id != item.id }.drop(1)
        val next = after.firstOrNull { tags[it.id] != FillerKind.FILLER } ?: return null
        val skipped = listOf(item.id) + after.takeWhile { it.id != next.id }.map { it.id }
        next.id to skipped
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.v(ex, "Couldn't find the next story episode")
        null
    }
}

/** Swap this video for another one, so Back still goes to where you were before */
internal fun PlaybackViewModel.playInstead(itemId: UUID) {
    player.pause()
    navigationManager.goBack()
    navigationManager.navigateTo(Destination.Playback(itemId, 0L))
}

internal fun PlaybackViewModel.setAlwaysSkipIntro(
    seriesId: UUID,
    on: Boolean,
) {
    if (on) alwaysSkipIntroSeries.add(seriesId) else alwaysSkipIntroSeries.remove(seriesId)
    mutableState.update { it.copy(alwaysSkipIntro = on) }
    saveAlwaysSkip(ALWAYS_SKIP_INTRO_KEY, seriesId, on)
}

internal fun PlaybackViewModel.setAlwaysSkipOutro(
    seriesId: UUID,
    on: Boolean,
) {
    if (on) alwaysSkipOutroSeries.add(seriesId) else alwaysSkipOutroSeries.remove(seriesId)
    mutableState.update { it.copy(alwaysSkipOutro = on) }
    saveAlwaysSkip(ALWAYS_SKIP_OUTRO_KEY, seriesId, on)
}

/** Saves the per-profile list of shows on the server, so it follows the profile to every device */
internal fun PlaybackViewModel.saveAlwaysSkip(
    key: String,
    seriesId: UUID,
    on: Boolean,
) {
    val userId = serverRepository.currentUser?.id ?: return
    val service = displayPreferencesService ?: return
    viewModelScope.launchIO {
        try {
            service.updateDisplayPreferences(userId) {
                val saved =
                    this[key]
                        .orEmpty()
                        .split('|')
                        .filter { it.isNotBlank() }
                        .toMutableSet()
                if (on) saved.add(seriesId.toString()) else saved.remove(seriesId.toString())
                put(key, saved.joinToString("|"))
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w(ex, "Couldn't save always skip %s", key)
        }
    }
}

internal suspend fun PlaybackViewModel.loadAlwaysSkipIntro() {
    if (alwaysSkipIntroLoaded) return
    val userId = serverRepository.currentUser?.id ?: return
    val service = displayPreferencesService ?: return
    try {
        val custom = service.getDisplayPreferences(userId).customPrefs

        fun ids(key: String) =
            custom[key]
                .orEmpty()
                .split('|')
                .mapNotNull { it.toUUIDOrNull() }
        alwaysSkipIntroSeries.addAll(ids(ALWAYS_SKIP_INTRO_KEY))
        alwaysSkipOutroSeries.addAll(ids(ALWAYS_SKIP_OUTRO_KEY))
        alwaysSkipIntroLoaded = true
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.v(ex, "Couldn't load always skip intro")
    }
}

/** Benflix: the video keeps stopping to load? Offer a lower quality, just for today */
internal fun PlaybackViewModel.trackBuffering(playbackState: Int) {
    val now = android.os.SystemClock.elapsedRealtime()
    if (playbackState == Player.STATE_READY && firstReadyAt == 0L) {
        firstReadyAt = now
        busyJob?.cancel()
        if (state.value.notice?.busy == true) showNotice(null)
    }
    if (playbackState != Player.STATE_BUFFERING || firstReadyAt == 0L || now - lastSeekAt < SEEK_GRACE_MS) return
    bufferTimes.addLast(now)
    while (bufferTimes.isNotEmpty() && now - bufferTimes.first() > STUTTER_WINDOW_MS) bufferTimes.removeFirst()
    if (bufferTimes.size < STUTTER_COUNT || stutterOffered || state.value.notice != null) return
    val actual =
        minOf(
            state.value.maxBitrate,
            state.value.currentPlayback
                ?.mediaSourceInfo
                ?.bitrate
                ?.toLong()
                ?.takeIf { it > 0 } ?: Long.MAX_VALUE,
        )
    if (actual <= 1_000_000L) return
    stutterOffered = true
    showNotice(
        PlayerNotice(
            title = "The video keeps stopping to load",
            message = "Try a lower quality for today? It goes back to normal tomorrow.",
            actions = listOf("Lower quality" to NoticeAction.LOWER_QUALITY, "Not now" to NoticeAction.DISMISS),
            autoHideMs = 15_000L,
        ),
    )
}

/**
 * Benflix: the profile wants subtitles, but this has none in their language. Quietly look
 * them up on the server and turn them on (needs subtitle permission on the Jellyfin user).
 */
internal fun PlaybackViewModel.findMissingSubtitles(item: BaseItem) {
    subtitlesJob?.cancel()
    if (item.type != BaseItemKind.EPISODE && item.type != BaseItemKind.MOVIE) return
    if (!BenflixPlayerPrefs.subtitlesChecked.add(item.id)) return
    subtitlesJob =
        viewModelScope.launchIO {
            delay(AUTO_SUBS_AFTER_MS)
            val playback = state.value.currentPlayback ?: return@launchIO
            if (playback.item.id != item.id) return@launchIO
            val userConfig = serverRepository.currentUserDto?.configuration
            val language = getPreferredLanguage(MediaStreamType.SUBTITLE, preferences, userConfig) ?: return@launchIO
            val mode =
                when (preferences.userPreferences?.subtitleMode) {
                    SubtitleModePreference.USE_USER_PROFILE, null -> userConfig?.subtitleMode
                    SubtitleModePreference.ALWAYS -> SubtitlePlaybackMode.ALWAYS
                    SubtitleModePreference.SMART -> SubtitlePlaybackMode.SMART
                    else -> SubtitlePlaybackMode.NONE
                }
            val streams = playback.mediaSourceInfo.mediaStreams.orEmpty()
            val audioLanguage =
                streams.firstOrNull { it.type == MediaStreamType.AUDIO && it.index == playback.audioIndex }?.language
                    ?: streams.firstOrNull { it.type == MediaStreamType.AUDIO }?.language
            val wanted =
                mode == SubtitlePlaybackMode.ALWAYS ||
                    (mode == SubtitlePlaybackMode.SMART && audioLanguage != null && !audioLanguage.equals(language, true))
            if (!wanted) return@launchIO
            val subtitles = streams.filter { it.type == MediaStreamType.SUBTITLE }
            if (subtitles.any { it.language.isNullOrBlank() || it.language == "und" || it.language.equals(language, true) }) {
                return@launchIO
            }
            try {
                val pick =
                    api.subtitleApi
                        .searchRemoteSubtitles(itemId = playback.itemId, language = language)
                        .content
                        .sortedWith(
                            compareByDescending<RemoteSubtitleInfo> { it.isHashMatch }
                                .thenByDescending { it.communityRating }
                                .thenByDescending { it.downloadCount },
                        ).firstOrNull() ?: return@launchIO
                if (state.value.currentPlayback
                        ?.item
                        ?.id != item.id
                ) {
                    return@launchIO
                }
                showToast(context, "No subtitles in your language, so Benflix found some", Toast.LENGTH_SHORT)
                downloadAndSwitchSubtitles(pick.id, onMain { player.isPlaying }, quiet = true)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.v(ex, "Couldn't find subtitles automatically")
            }
        }
}

/**
 * Benflix: if this movie has a scene during or after the credits (TMDB marks these),
 * say so when the credits start, and don't skip the credits.
 */
internal fun PlaybackViewModel.watchForAfterCreditsScene(itemId: UUID) {
    afterCreditsJob?.cancel()
    hasAfterCreditsScene = false
    creditsReached = false
    mutableState.update { it.copy(afterCreditsScene = false) }
    afterCreditsJob =
        viewModelScope.launchIO {
            val item =
                try {
                    api.userLibraryApi.getItem(itemId).content
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.v(ex, "Couldn't check for a scene after the credits")
                    return@launchIO
                }
            if (!BenflixAfterCredits.has(item)) return@launchIO
            hasAfterCreditsScene = true
            while (isActive) {
                delay(2_000L)
                val (position, duration) = onMain { player.currentPosition to player.duration }
                if (duration > 0 && (creditsReached || duration - position <= AFTER_CREDITS_NOTICE_MS)) {
                    mutableState.update { it.copy(afterCreditsScene = true) }
                    delay(AFTER_CREDITS_SHOW_MS)
                    mutableState.update { it.copy(afterCreditsScene = false) }
                    break
                }
            }
        }
}

/** Benflix: MPV had trouble with this video, so play it in the normal player from the same spot */
internal fun PlaybackViewModel.fallBackFromAutoMpv() {
    autoMpvActive = false
    viewModelScope.launch(WholphinDispatchers.Main + ExceptionHandler()) {
        val currentPlayback = state.value.currentPlayback ?: return@launch
        val positionMs = player.currentPosition
        Timber.w("MPV could not play this, switching to the normal player")
        withContext(WholphinDispatchers.IO) {
            val video = currentPlayback.mediaSourceInfo.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
            createPlayer(isHdr = false, is4k = (video?.width ?: 0) > 2560)
        }
        mutableState.update { it.copy(currentPlayback = null) }
        changeStreams(
            item = currentItem.item,
            sourceId = currentPlayback.mediaSourceInfo.id,
            audioIndex = currentPlayback.audioIndex,
            subtitleIndex = currentPlayback.subtitleIndex,
            positionMs = positionMs,
        )
        player.prepare()
        player.play()
    }
}
