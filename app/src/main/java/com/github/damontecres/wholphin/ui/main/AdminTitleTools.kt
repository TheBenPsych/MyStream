package com.github.damontecres.wholphin.ui.main

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemRefreshApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MetadataRefreshMode
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Benflix admin tools for one movie, show or episode: refresh its info and pictures, see the
 * file's details (size, quality, sound and subtitle tracks), and open Fix match from the Server page.
 */
@HiltViewModel
class AdminTitleViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        val isAdmin: Boolean
            get() =
                com.github.damontecres.wholphin.services.GuestMode
                    .isAdmin(serverRepository.currentUserDto)

        /** File details to show (title to text) */
        private val _details = MutableStateFlow<Pair<String, String>?>(null)
        val details: StateFlow<Pair<String, String>?> = _details

        /** The title to open Fix match for (from a problem report) */
        private val _fixItem = MutableStateFlow<BaseItem?>(null)
        val fixItem: StateFlow<BaseItem?> = _fixItem

        /** Asks Jellyfin to look the title up again on the internet and get fresh pictures */
        fun refresh(
            itemId: UUID,
            name: String,
        ) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    api.itemRefreshApi.refreshItem(
                        itemId = itemId,
                        metadataRefreshMode = MetadataRefreshMode.FULL_REFRESH,
                        imageRefreshMode = MetadataRefreshMode.FULL_REFRESH,
                        replaceAllMetadata = true,
                        replaceAllImages = true,
                    )
                    showToast(context, "Refreshing $name. The new info and pictures show up in a minute or two.", Toast.LENGTH_LONG)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not refresh %s", name)
                    showToast(context, "Couldn't refresh $name. Try again in a moment.")
                }
            }
        }

        /** Reads the file's details. For a show, it shows the first episode's file. */
        fun fileDetails(itemId: UUID) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val userId = serverRepository.currentUser?.id
                    var dto = api.userLibraryApi.getItem(itemId, userId).content
                    if (dto.type == BaseItemKind.SERIES || dto.type == BaseItemKind.SEASON) {
                        val first =
                            api.tvShowsApi
                                .getEpisodes(
                                    seriesId = dto.seriesId ?: dto.id,
                                    userId = userId,
                                    seasonId = if (dto.type == BaseItemKind.SEASON) dto.id else null,
                                    fields = listOf(ItemFields.MEDIA_SOURCES),
                                    limit = 1,
                                ).content.items
                                .firstOrNull()
                        if (first != null) dto = api.userLibraryApi.getItem(first.id, userId).content
                    }
                    _details.value = titleOf(dto) to describeFile(dto)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not read file details")
                    _details.value = "File details" to "Couldn't read the file details. Try again in a moment."
                }
            }
        }

        /** Opens Fix match for a title by its id (used from a "Wrong movie or episode" report) */
        fun openFixMatch(itemId: UUID) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    var dto = api.userLibraryApi.getItem(itemId, serverRepository.currentUser?.id).content
                    // Fix match works on the movie or the show, not one episode
                    if (dto.type == BaseItemKind.EPISODE || dto.type == BaseItemKind.SEASON) {
                        dto.seriesId?.let { dto = api.userLibraryApi.getItem(it, serverRepository.currentUser?.id).content }
                    }
                    _fixItem.value = BaseItem(dto)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not open Fix match")
                    showToast(context, "Couldn't open that title.")
                }
            }
        }

        fun closeDetails() {
            _details.value = null
        }

        fun closeFixMatch() {
            _fixItem.value = null
        }
    }

private fun titleOf(dto: BaseItemDto): String =
    if (dto.type == BaseItemKind.EPISODE) {
        listOfNotNull(dto.seriesName, "S${dto.parentIndexNumber ?: 0}E${dto.indexNumber ?: 0}", dto.name).joinToString(" · ")
    } else {
        dto.name.orEmpty()
    }

/** Benflix: the file in plain words: where it is, how big, the picture, the sound tracks and subtitles */
internal fun describeFile(dto: BaseItemDto): String {
    val sources = dto.mediaSources.orEmpty()
    if (sources.isEmpty()) return "Jellyfin has no file for this."
    return sources.joinToString("\n\n") { source ->
        val streams = source.mediaStreams.orEmpty()
        buildList {
            source.path?.let { add("File: $it") }
            val size = source.size?.let { "%.1f GB".format(it / 1_000_000_000.0) }
            val mbps = source.bitrate?.let { "%.0f Mbps".format(it / 1_000_000.0) }
            add(listOfNotNull(source.container?.uppercase(), size, mbps).joinToString(" · "))
            streams.filter { it.type == MediaStreamType.VIDEO }.forEach { add("Picture: ${it.displayTitle ?: it.codec}") }
            val audio = streams.filter { it.type == MediaStreamType.AUDIO }
            if (audio.isEmpty()) add("Sound: none!")
            audio.forEach { add("Sound: ${it.displayTitle ?: it.codec}" + if (it.isDefault) " (starts with this)" else "") }
            val subs = streams.filter { it.type == MediaStreamType.SUBTITLE }
            if (subs.isEmpty()) add("Subtitles: none")
            subs.forEach {
                add(
                    "Subtitles: ${it.displayTitle ?: it.codec}" +
                        (if (it.isForced) " (forced)" else "") +
                        (if (it.isExternal) " (separate file)" else ""),
                )
            }
        }.joinToString("\n")
    }
}

/** Benflix: shows the File details pop-up and Fix match when an admin tool asked for them */
@Composable
fun AdminTitleDialogs(viewModel: AdminTitleViewModel) {
    val details by viewModel.details.collectAsState()
    val fixItem by viewModel.fixItem.collectAsState()
    details?.let { (title, body) ->
        ConfirmDialog(
            title = title,
            body = body,
            onCancel = viewModel::closeDetails,
            onConfirm = viewModel::closeDetails,
        )
    }
    fixItem?.let { item ->
        com.github.damontecres.wholphin.ui.detail.FixMatchDialog(
            item = item,
            onDismissRequest = viewModel::closeFixMatch,
            onFixed = viewModel::closeFixMatch,
        )
    }
}
