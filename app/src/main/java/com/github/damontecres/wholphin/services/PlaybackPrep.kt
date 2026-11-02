package com.github.damontecres.wholphin.services

import android.os.SystemClock
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix: gets a video ready before Play is pressed, so it starts sooner.
 *
 * - On a details page, the item Play will start is fetched and kept for a minute, so the player
 *   doesn't have to ask the server for it again.
 * - The first bit of the video file is read, which wakes the server's hard drive if it went to
 *   sleep (that spin-up is often the slowest part of starting a video on a home server).
 * - During the credits, the same is done for the next episode.
 */
@Singleton
class PlaybackPrep
    @Inject
    constructor(
        private val api: ApiClient,
        @param:AuthOkHttpClient private val http: OkHttpClient,
        @param:IoCoroutineScope private val scope: CoroutineScope,
    ) {
        private class Ready(
            val item: BaseItemDto,
            val at: Long,
        )

        private val ready = ConcurrentHashMap<UUID, Ready>()
        private val warmedAt = ConcurrentHashMap<UUID, Long>()

        /** The details page already has the full item: keep it and wake its disk */
        fun hold(item: BaseItemDto) {
            if (!item.isVideo()) return
            ready[item.id] = Ready(item, SystemClock.elapsedRealtime())
            trim()
            warm(item)
        }

        /** Fetch the full item (like the next episode of a show), keep it and wake its disk */
        fun prepare(itemId: UUID) {
            if (fresh(itemId) != null) return
            scope.launch {
                try {
                    hold(api.userLibraryApi.getItem(itemId).content)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.v(ex, "Could not get %s ready", itemId)
                }
            }
        }

        /** The item kept by [hold] or [prepare], if it's less than a minute old (used once) */
        fun take(itemId: UUID): BaseItemDto? = fresh(itemId)?.also { ready.remove(itemId) }

        /** Read the first bit of the video so a sleeping hard drive spins up now, not after Play */
        fun warm(item: BaseItemDto) {
            if (!item.isVideo()) return
            val now = SystemClock.elapsedRealtime()
            val last = warmedAt[item.id]
            if (last != null && now - last < WARM_AGAIN_MS) return
            warmedAt[item.id] = now
            scope.launch {
                try {
                    val url =
                        api.videosApi.getVideoStreamUrl(
                            itemId = item.id,
                            mediaSourceId = item.mediaSources?.firstOrNull()?.id,
                            static = true,
                        )
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .header("Range", "bytes=0-${WARM_BYTES - 1}")
                            .build()
                    http.newCall(request).execute().use { response ->
                        response.body.byteStream().use { it.read(ByteArray(READ_BUFFER)) }
                    }
                    Timber.v("Woke the disk for %s in %s ms", item.id, SystemClock.elapsedRealtime() - now)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.v(ex, "Could not wake the disk for %s", item.id)
                }
            }
        }

        private fun fresh(itemId: UUID): BaseItemDto? {
            val entry = ready[itemId] ?: return null
            return if (SystemClock.elapsedRealtime() - entry.at < FRESH_MS) {
                entry.item
            } else {
                ready.remove(itemId)
                null
            }
        }

        private fun trim() {
            if (ready.size <= MAX_ITEMS) return
            ready.entries
                .sortedBy { it.value.at }
                .take(ready.size - MAX_ITEMS)
                .forEach { ready.remove(it.key) }
        }

        private fun BaseItemDto.isVideo() = type == BaseItemKind.MOVIE || type == BaseItemKind.EPISODE

        companion object {
            private const val FRESH_MS = 60_000L
            private const val WARM_AGAIN_MS = 5 * 60_000L
            private const val WARM_BYTES = 262_144
            private const val READ_BUFFER = 65_536
            private const val MAX_ITEMS = 6
        }
    }
