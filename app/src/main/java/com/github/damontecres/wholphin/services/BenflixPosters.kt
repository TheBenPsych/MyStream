package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.remoteImageApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.RemoteImageInfo
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Which picture of a title the poster tools change */
enum class PosterKind(
    val type: ImageType,
    val label: String,
    /** Width / height of the picture, for the grid */
    val shape: Float,
) {
    POSTER(ImageType.PRIMARY, "Poster", 2f / 3f),
    BACKGROUND(ImageType.BACKDROP, "Background", 16f / 9f),
    LOGO(ImageType.LOGO, "Title logo", 16f / 6f),
}

/** One picture Jellyfin found online (TMDB, TheTVDB or Fanart.tv) */
data class PosterChoice(
    val url: String,
    val thumb: String,
    val provider: String,
    val language: String?,
    val width: Int?,
    val height: Int?,
) {
    /** "TMDB · 2000 × 3000 · English" */
    val describe: String
        get() =
            listOfNotNull(
                provider,
                if (width != null && height != null && width > 0) "$width × $height" else null,
                languageName(language),
            ).joinToString(" · ")
}

/** One title the fixer wants to show */
data class PosterProblem(
    /** The movie, show, season or collection whose picture is wrong */
    val itemId: UUID,
    val kind: PosterKind,
    val title: String,
    val subtitle: String,
    val problem: String,
    /** The picture there now, or null when there is none */
    val currentTag: String?,
    /** For "seasons with no poster": the show, so the picker opens on it */
    val showId: UUID? = null,
    val year: Int? = null,
    val isShow: Boolean = false,
) {
    val key: String get() = "$itemId-${kind.name}"
}

/**
 * Benflix "Change poster" and "Fix posters" (main profile only). Pictures come from whatever Jellyfin can
 * find online (TMDB, and TheTVDB / Fanart.tv when their plugins are on). Benflix never contacts
 * ThePosterDB: it has no way for apps to connect and its rules don't allow programs to search it.
 *
 * Posters you pick are remembered ("kept"), so Refresh info and pictures doesn't swap them back. Titles you
 * press "Leave it" on in the fixer are remembered too. Both live in the main profile's display preferences,
 * so the web version sees the same lists.
 */
@Singleton
class BenflixPosters
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val displayPreferencesService: DisplayPreferencesService,
        private val tower: BenflixTower,
    ) {
        private val userId: UUID? get() = serverRepository.currentUser?.id

        // ---------- What's online ----------

        /** Every picture of [kind] Jellyfin can find for [itemId], best first (English first, then others) */
        suspend fun choices(
            itemId: UUID,
            kind: PosterKind,
        ): List<PosterChoice> =
            withContext(WholphinDispatchers.IO) {
                val found =
                    api.remoteImageApi
                        .getRemoteImages(
                            itemId = itemId,
                            type = kind.type,
                            limit = 300,
                            includeAllLanguages = true,
                        ).content.images
                        .orEmpty()
                rank(found, kind).mapNotNull { info ->
                    val url = info.url ?: return@mapNotNull null
                    PosterChoice(
                        url = url,
                        thumb = info.thumbnailUrl ?: url,
                        provider = info.providerName ?: "Online",
                        language = info.language,
                        width = info.width,
                        height = info.height,
                    )
                }
            }

        /** Puts [choice] on the title (and remembers you picked it) */
        suspend fun use(
            itemId: UUID,
            kind: PosterKind,
            choice: PosterChoice,
        ) = withContext(WholphinDispatchers.IO) {
            replacing(itemId, kind) {
                api.remoteImageApi.downloadRemoteImage(itemId = itemId, type = kind.type, imageUrl = choice.url)
            }
            keep(itemId)
        }

        /**
         * Backgrounds can be more than one, and Jellyfin adds a new one at the end. This takes the old ones
         * off after the new one is in, so the new one is the one Benflix shows.
         */
        private suspend fun replacing(
            itemId: UUID,
            kind: PosterKind,
            put: suspend () -> Unit,
        ) {
            val before =
                if (kind == PosterKind.BACKGROUND) {
                    api.imageApi
                        .getItemImageInfos(itemId)
                        .content
                        .count { it.imageType == ImageType.BACKDROP }
                } else {
                    0
                }
            put()
            for (index in before - 1 downTo 0) {
                try {
                    api.imageApi.deleteItemImageByIndex(itemId, ImageType.BACKDROP, index)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not take off old background %s", index)
                }
            }
        }

        // ---------- Kept and left alone ----------

        private var keptCache: Set<String>? = null
        private var skipCache: Set<String>? = null

        suspend fun kept(): Set<String> = keptCache ?: readSet(KEPT).also { keptCache = it }

        suspend fun skipped(): Set<String> = skipCache ?: readSet(SKIP).also { skipCache = it }

        suspend fun isKept(itemId: UUID): Boolean =
            try {
                itemId.toString() in kept()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                false
            }

        suspend fun keep(itemId: UUID) = addTo(KEPT, itemId.toString()).also { keptCache = it }

        suspend fun leaveAlone(itemId: UUID) = addTo(SKIP, itemId.toString()).also { skipCache = it }

        private suspend fun readSet(key: String): Set<String> {
            val user = userId ?: return emptySet()
            return displayPreferencesService
                .getDisplayPreferences(user, PREFS_ID)
                .customPrefs[key]
                ?.split(',')
                ?.filter { it.isNotBlank() }
                ?.toSet()
                .orEmpty()
        }

        private suspend fun addTo(
            key: String,
            value: String,
        ): Set<String> {
            val user = userId ?: return emptySet()
            var now = emptySet<String>()
            try {
                displayPreferencesService.updateDisplayPreferences(user, PREFS_ID) {
                    val list = (this[key]?.split(',').orEmpty().filter { it.isNotBlank() } + value).distinct()
                    now = list.toSet()
                    put(key, list.joinToString(","))
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not remember %s", key)
            }
            return now
        }

        // ---------- The fixer ----------

        /** Last night's fill-in and how Tower's size check is going, in plain words (null when Tower isn't reachable) */
        suspend fun nightNote(): String? =
            try {
                val j = tower.posters("status")
                val night = j.optJSONObject("night")
                val filled = night?.optInt("filled", -1) ?: -1
                when {
                    filled > 0 -> "Last night Benflix filled in $filled missing picture${if (filled == 1) "" else "s"} by itself."
                    filled == 0 -> "Benflix checks for missing pictures every night and fills them in."
                    else -> null
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                null
            }

        /** Everything that needs a look, worst first: no poster, seasons with no poster, blurry, wrong shape, no title logo */
        suspend fun problems(): List<PosterProblem> =
            withContext(WholphinDispatchers.IO) {
                coroutineScope {
                    val user = userId
                    val skip = async { safe { skipped() }.orEmpty() }
                    val kept = async { safe { kept() }.orEmpty() }
                    val small =
                        async {
                            safe {
                                val list = tower.posters("status").optJSONArray("small")
                                (0 until (list?.length() ?: 0)).associate { i ->
                                    val o = list!!.getJSONObject(i)
                                    o.optString("id") to (o.optInt("w") to o.optInt("h"))
                                }
                            }.orEmpty()
                        }
                    val titles =
                        async {
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = user,
                                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.BOX_SET),
                                        recursive = true,
                                        fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                                        enableImageTypes = listOf(ImageType.PRIMARY, ImageType.LOGO),
                                        sortBy = listOf(ItemSortBy.SORT_NAME),
                                        enableUserData = false,
                                    ),
                                ).content.items
                        }
                    val seasons =
                        async {
                            safe {
                                api.itemsApi
                                    .getItems(
                                        GetItemsRequest(
                                            userId = user,
                                            includeItemTypes = listOf(BaseItemKind.SEASON),
                                            recursive = true,
                                            enableImageTypes = listOf(ImageType.PRIMARY),
                                            sortBy = listOf(ItemSortBy.SORT_NAME),
                                            enableUserData = false,
                                        ),
                                    ).content.items
                            }.orEmpty()
                        }
                    val left = skip.await()
                    val mine = kept.await()
                    val sizes = small.await()
                    val all = titles.await().filterNot { it.id.toString() in left }
                    val noPoster = mutableListOf<PosterProblem>()
                    val blurry = mutableListOf<PosterProblem>()
                    val shape = mutableListOf<PosterProblem>()
                    val noLogo = mutableListOf<PosterProblem>()
                    all.forEach { item ->
                        val id = item.id.toString()
                        val tag = item.imageTags?.get(ImageType.PRIMARY)
                        val isShow = item.type == BaseItemKind.SERIES

                        fun problem(
                            kind: PosterKind,
                            text: String,
                            current: String?,
                        ) = PosterProblem(
                            itemId = item.id,
                            kind = kind,
                            title = item.name.orEmpty(),
                            subtitle = subtitleOf(item),
                            problem = text,
                            currentTag = current,
                            year = item.productionYear,
                            isShow = isShow,
                        )
                        when {
                            tag == null -> {
                                noPoster += problem(PosterKind.POSTER, "No poster", null)
                            }

                            id in mine -> {
                                // You picked this one, so it's fine
                            }

                            sizes[id] != null -> {
                                val (w, h) = sizes.getValue(id)
                                blurry += problem(PosterKind.POSTER, "Blurry: only $w × $h", tag)
                            }

                            (item.primaryImageAspectRatio ?: 0.67) > 0.85 -> {
                                shape += problem(PosterKind.POSTER, "Wrong shape: a wide picture in a tall spot", tag)
                            }
                        }
                        if (item.type != BaseItemKind.BOX_SET && item.imageTags?.get(ImageType.LOGO) == null) {
                            noLogo += problem(PosterKind.LOGO, "No title logo", null)
                        }
                    }
                    val showNames = all.associate { it.id to it.name.orEmpty() }
                    val seasonProblems =
                        seasons
                            .await()
                            .filter { it.imageTags?.get(ImageType.PRIMARY) == null && it.id.toString() !in left }
                            .filter { it.seriesId == null || it.seriesId.toString() !in left }
                            .map { season ->
                                val showName = season.seriesName ?: showNames[season.seriesId].orEmpty()
                                PosterProblem(
                                    itemId = season.id,
                                    kind = PosterKind.POSTER,
                                    title = showName,
                                    subtitle = season.name ?: "Season ${season.indexNumber}",
                                    problem = "${season.name ?: "This season"} has no poster",
                                    currentTag = null,
                                    showId = season.seriesId,
                                    isShow = true,
                                )
                            }
                    noPoster + seasonProblems + blurry + shape + noLogo
                }
            }

        /** The best picture for a problem, or null when Jellyfin found nothing that fits */
        suspend fun suggestion(problem: PosterProblem): PosterChoice? =
            safe {
                choices(problem.itemId, problem.kind).firstOrNull { c ->
                    // Never suggest words in another language by itself
                    problem.kind == PosterKind.BACKGROUND || c.language.isNullOrBlank() || c.language.equals("en", true)
                }
            }

        /** True when the problem was fixed some other way (like in the picker) */
        suspend fun stillAProblem(problem: PosterProblem): Boolean =
            safe {
                val dto = api.userLibraryApi.getItem(problem.itemId, userId).content
                val tag = dto.imageTags?.get(problem.kind.type)
                if (problem.currentTag == null) tag == null else tag == problem.currentTag
            } ?: true

        suspend fun item(id: UUID): BaseItemDto = api.userLibraryApi.getItem(id, userId).content

        suspend fun seasonsOf(showId: UUID): List<BaseItemDto> =
            safe {
                api.tvShowsApi
                    .getSeasons(seriesId = showId, userId = userId)
                    .content.items
            }.orEmpty()

        private suspend fun <T> safe(block: suspend () -> T): T? =
            try {
                block()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Posters")
                null
            }

        companion object {
            const val PREFS_ID = "benflix-posters"
            private const val KEPT = "kept"
            private const val SKIP = "skip"

            /** Best first: English (or no words, for backgrounds), then most votes, best score, biggest */
            internal fun rank(
                images: List<RemoteImageInfo>,
                kind: PosterKind,
            ): List<RemoteImageInfo> =
                images
                    .filter { !it.url.isNullOrBlank() }
                    .sortedWith(
                        compareBy<RemoteImageInfo> { langGroup(it.language, kind) }
                            .thenByDescending { it.voteCount ?: 0 }
                            .thenByDescending { it.communityRating ?: 0.0 }
                            .thenByDescending { it.width ?: 0 },
                    )

            private fun langGroup(
                language: String?,
                kind: PosterKind,
            ): Int {
                val lang = language.orEmpty().lowercase()
                return if (kind == PosterKind.BACKGROUND) {
                    if (lang.isEmpty()) {
                        0
                    } else if (lang == "en") {
                        1
                    } else {
                        2
                    }
                } else {
                    if (lang == "en") {
                        0
                    } else if (lang.isEmpty()) {
                        1
                    } else {
                        2
                    }
                }
            }

            internal fun subtitleOf(item: BaseItemDto): String =
                listOfNotNull(
                    when (item.type) {
                        BaseItemKind.SERIES -> "Show"
                        BaseItemKind.BOX_SET -> "Collection"
                        else -> "Movie"
                    },
                    item.productionYear?.toString(),
                ).joinToString(" · ")
        }
    }

private fun languageName(code: String?): String? =
    when (code?.lowercase()) {
        null, "" -> {
            "No words"
        }

        "en" -> {
            "English"
        }

        else -> {
            java.util.Locale
                .forLanguageTag(code)
                .displayLanguage
                .takeIf { it.isNotBlank() } ?: code
        }
    }
