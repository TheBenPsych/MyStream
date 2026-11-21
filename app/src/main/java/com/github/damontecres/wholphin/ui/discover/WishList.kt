package com.github.damontecres.wholphin.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.GameLibraryClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

/**
 * My List for titles that aren't on Glowfin yet (Ben 2026-11-21). Tower keeps the list; when one
 * arrives Tower puts it on the person's real My List by itself. Keys look like "movie:603".
 */
object WishList {
    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keys: StateFlow<Set<String>> = _keys

    fun key(
        isMovie: Boolean,
        tmdbId: Int,
    ) = "${if (isMovie) "movie" else "tv"}:$tmdbId"

    internal fun loaded(keys: Set<String>) {
        _keys.value = keys
    }

    internal fun set(
        key: String,
        on: Boolean,
    ) {
        _keys.update { if (on) it + key else it - key }
    }
}

@HiltViewModel
class WishListViewModel
    @Inject
    constructor(
        private val helper: GameLibraryClient,
    ) : ViewModel() {
        fun load() {
            viewModelScope.launch {
                try {
                    val list = helper.getJson("/api/asks/wish").optJSONArray("items")
                    WishList.loaded(
                        (0 until (list?.length() ?: 0))
                            .mapNotNull { i ->
                                val o = list?.optJSONObject(i) ?: return@mapNotNull null
                                "${o.optString("type")}:${o.optInt("tmdb")}"
                            }.toSet(),
                    )
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    // An older Tower without this yet: the button simply starts unticked
                    Timber.w(ex, "My List (not here yet): Tower didn't answer")
                }
            }
        }

        fun set(
            isMovie: Boolean,
            tmdbId: Int,
            title: String,
            posterPath: String?,
            on: Boolean,
        ) {
            val key = WishList.key(isMovie, tmdbId)
            WishList.set(key, on)
            viewModelScope.launch {
                try {
                    helper.postJson(
                        "/api/asks/wish",
                        JSONObject()
                            .put("type", if (isMovie) "movie" else "tv")
                            .put("tmdb", tmdbId)
                            .put("title", title)
                            .put("poster", posterPath.orEmpty())
                            .put("on", on),
                    )
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "My List (not here yet): couldn't save %s", key)
                    WishList.set(key, !on)
                }
            }
        }
    }
