package com.github.damontecres.wholphin.ui.main

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.KidsMode
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
import org.jellyfin.sdk.api.client.extensions.itemUpdateApi
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
        private val posters: com.github.damontecres.wholphin.services.BenflixPosters,
        private val navigationManager: com.github.damontecres.wholphin.services.NavigationManager,
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
                    // Benflix: pictures you picked in Change poster stay; missing ones still get filled in
                    val keepPictures = posters.isKept(itemId)
                    api.itemRefreshApi.refreshItem(
                        itemId = itemId,
                        metadataRefreshMode = MetadataRefreshMode.FULL_REFRESH,
                        imageRefreshMode = MetadataRefreshMode.FULL_REFRESH,
                        replaceAllMetadata = true,
                        replaceAllImages = !keepPictures,
                    )
                    showToast(
                        context,
                        if (keepPictures) {
                            "Refreshing $name. Your picked pictures stay. New info shows up in a minute or two."
                        } else {
                            "Refreshing $name. The new info and pictures show up in a minute or two."
                        },
                        Toast.LENGTH_LONG,
                    )
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not refresh %s", name)
                    showToast(context, "Couldn't refresh $name. Try again in a moment.")
                }
            }
        }

        /** Opens Change poster for a movie, show, season or collection */
        fun changePoster(itemId: UUID) =
            navigationManager.navigateTo(
                com.github.damontecres.wholphin.ui.nav.Destination
                    .PosterPicker(itemId),
            )

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

        /** Ben's "Kids profile" choice for the title the hold menu was opened on */
        private val _kids = MutableStateFlow<KidsChoice?>(null)
        val kids: StateFlow<KidsChoice?> = _kids

        /** Opens the Kids profile choices. An episode or season opens its show, since the choice is for the whole show. */
        fun openKids(itemId: UUID) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    _kids.value = readKids(itemId)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not read Kids choice")
                    showToast(context, "Couldn't open that title.")
                }
            }
        }

        private suspend fun readKids(itemId: UUID): KidsChoice {
            val userId = serverRepository.currentUser?.id
            var dto = api.userLibraryApi.getItem(itemId, userId).content
            if (dto.type == BaseItemKind.EPISODE || dto.type == BaseItemKind.SEASON) {
                dto.seriesId?.let { dto = api.userLibraryApi.getItem(it, userId).content }
            }
            return KidsChoice(
                id = dto.id,
                name = dto.name.orEmpty(),
                show = dto.type == BaseItemKind.SERIES,
                rule =
                    KidsMode.ruleOf(dto.customRating),
                picked =
                    dto.tags.orEmpty().any {
                        it.equals(
                            KidsMode.PICK_TAG,
                            ignoreCase = true,
                        )
                    },
                rating = dto.officialRating?.takeIf { it.isNotBlank() },
            )
        }

        fun closeKids() {
            _kids.value = null
        }

        /** Saves Ben's choice as the title's custom rating. Jellyfin passes it on to every season and episode. */
        fun setKidsRule(
            choice: KidsChoice,
            rule: KidsMode.TitleRule,
        ) {
            _kids.value = null
            viewModelScope.launch(WholphinDispatchers.IO) {
                val words =
                    try {
                        saveKids(choice.id, rule, null)
                        when (rule) {
                            KidsMode.TitleRule.LET_THROUGH -> {
                                "Kids and Big kids can now see ${choice.name}."
                            }

                            KidsMode.TitleRule.HIDE -> {
                                "${choice.name} is now hidden from Kids and Big kids."
                            }

                            KidsMode.TitleRule.FOLLOW -> {
                                "${choice.name} follows its age rating again."
                            }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not save Kids choice")
                        "Couldn't save that. Try again in a moment."
                    }
                showToast(context, words, Toast.LENGTH_LONG)
            }
        }

        /**
         * Puts a title on (or takes it off) the Kids home's "Picked for you" row. A title Kids can't see yet is let through
         * at the same time, so it really shows up.
         */
        fun setKidsPick(
            choice: KidsChoice,
            on: Boolean,
        ) {
            _kids.value = null
            viewModelScope.launch(WholphinDispatchers.IO) {
                val letIn =
                    on &&
                        choice.rule != KidsMode.TitleRule.LET_THROUGH &&
                        (choice.rule == KidsMode.TitleRule.HIDE || !KidsMode.kidsRating(choice.rating))
                val words =
                    try {
                        saveKids(choice.id, if (letIn) KidsMode.TitleRule.LET_THROUGH else null, on)
                        when {
                            !on -> "${choice.name} is off the Kids home."
                            letIn -> "${choice.name} is on the Kids home. It wasn't rated for Kids, so it's let through too."
                            else -> "${choice.name} is on the Kids home."
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not save Kids pick")
                        "Couldn't save that. Try again in a moment."
                    }
                showToast(context, words, Toast.LENGTH_LONG)
            }
        }

        /** Changes the custom rating ([rule], when given) and the "Picked for you" tag ([picked], when given) in one save */
        private suspend fun saveKids(
            itemId: UUID,
            rule: KidsMode.TitleRule?,
            picked: Boolean?,
        ) {
            val dto = api.userLibraryApi.getItem(itemId, serverRepository.currentUser?.id).content
            val show = dto.type == BaseItemKind.SERIES
            val custom =
                when (rule) {
                    null -> dto.customRating
                    KidsMode.TitleRule.LET_THROUGH -> KidsMode.letThroughRating(show)
                    KidsMode.TitleRule.HIDE -> KidsMode.hideRating(show)
                    KidsMode.TitleRule.FOLLOW -> null
                }
            val tags =
                when (picked) {
                    null -> dto.tags
                    true -> (dto.tags.orEmpty() + KidsMode.PICK_TAG).distinct()
                    false -> dto.tags.orEmpty().filterNot { it.equals(KidsMode.PICK_TAG, ignoreCase = true) }
                }
            api.itemUpdateApi.updateItem(itemId, dto.copy(customRating = custom, tags = tags))
        }

        fun closeDetails() {
            _details.value = null
        }

        fun closeFixMatch() {
            _fixItem.value = null
        }
    }

/** One title's Kids profile settings, for the hold menu's "Kids profile" choices */
data class KidsChoice(
    val id: UUID,
    val name: String,
    val show: Boolean,
    val rule: KidsMode.TitleRule,
    val picked: Boolean,
    val rating: String?,
)

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
    val kids by viewModel.kids.collectAsState()
    kids?.let { choice -> KidsChoiceDialog(choice, viewModel) }
    fixItem?.let { item ->
        com.github.damontecres.wholphin.ui.detail.FixMatchDialog(
            item = item,
            onDismissRequest = viewModel::closeFixMatch,
            onFixed = viewModel::closeFixMatch,
        )
    }
}

/** Benflix (Idea Box 25): Ben's "Kids profile" choices for one movie or show */
@Composable
private fun KidsChoiceDialog(
    choice: KidsChoice,
    viewModel: AdminTitleViewModel,
) {
    val ratedOk = KidsMode.kidsRating(choice.rating)
    val ratingWords = choice.rating?.let { "rated $it" } ?: "no rating"
    val line: @Composable (String) -> Unit = { androidx.tv.material3.Text(it) }
    com.github.damontecres.wholphin.ui.components.DialogPopup(
        showDialog = true,
        title = "Kids profile: ${choice.name}",
        dialogItems =
            listOf(
                com.github.damontecres.wholphin.ui.components.DialogItem(
                    headlineContent = { line("Let it through") },
                    supportingContent = { line("Kids and Big kids can see it") },
                    selected = choice.rule == KidsMode.TitleRule.LET_THROUGH,
                    onClick = { viewModel.setKidsRule(choice, KidsMode.TitleRule.LET_THROUGH) },
                ),
                com.github.damontecres.wholphin.ui.components.DialogItem(
                    headlineContent = { line("Hide it") },
                    supportingContent = { line("Kids and Big kids never see it") },
                    selected = choice.rule == KidsMode.TitleRule.HIDE,
                    onClick = { viewModel.setKidsRule(choice, KidsMode.TitleRule.HIDE) },
                ),
                com.github.damontecres.wholphin.ui.components.DialogItem(
                    headlineContent = { line("Follow its age rating") },
                    supportingContent = {
                        line(if (ratedOk) "It's $ratingWords, so Kids can see it" else "It's $ratingWords, so Kids can't see it")
                    },
                    selected = choice.rule == KidsMode.TitleRule.FOLLOW,
                    onClick = { viewModel.setKidsRule(choice, KidsMode.TitleRule.FOLLOW) },
                ),
                com.github.damontecres.wholphin.ui.components.DialogItemDivider,
                com.github.damontecres.wholphin.ui.components.DialogItem(
                    headlineContent = { line(if (choice.picked) "Take off the Kids home" else "Put on the Kids home") },
                    supportingContent = { line("The \"Picked for you\" row at the top of the Kids home") },
                    onClick = { viewModel.setKidsPick(choice, !choice.picked) },
                ),
            ),
        onDismissRequest = viewModel::closeKids,
        dismissOnClick = false,
        waitToLoad = false,
        elevation = 3.dp,
    )
}
