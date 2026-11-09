@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.ui.nav

import androidx.navigation3.runtime.NavKey
import com.github.damontecres.wholphin.data.filter.DiscoverFilter
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.util.StringProvider
import com.github.damontecres.wholphin.util.DiscoverRequestType
import com.github.damontecres.wholphin.util.RequestHandler
import com.github.damontecres.wholphin.util.SEERR_PAGE_SIZE
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

/**
 * Represents a page in the app
 *
 * @param fullScreen whether the page should be full page aka not include the nav drawer
 */
@Serializable
sealed class Destination(
    val fullScreen: Boolean = false,
) : NavKey {
    @Serializable
    data class Home(
        val id: Long = 0L,
    ) : Destination()

    @Serializable
    data class Settings(
        val screen: PreferenceScreenOption,
    ) : Destination(true)

    @Serializable
    data class SubtitleSettings(
        val hdr: Boolean,
    ) : Destination(true)

    @Serializable
    data class Search(
        val query: String = "",
    ) : Destination()

    data object UserAppPreferences : Destination(true)

    @Serializable
    data class SeriesOverview(
        val itemId: UUID,
        val type: BaseItemKind,
        val seasonEpisode: SeasonEpisodeIds? = null,
    ) : Destination() {
        override fun toString(): String = "SeriesOverview(itemId=$itemId, type=$type, seasonEpisode=$seasonEpisode)"
    }

    @Serializable
    data class MediaItem(
        val itemId: UUID,
        val type: BaseItemKind,
        val collectionType: CollectionType? = null,
        val initialSongId: UUID? = null,
    ) : Destination() {
        constructor(item: BaseItem) : this(item.id, item.type, item.data.collectionType)
    }

    @Serializable
    data class Recordings(
        val itemId: UUID,
    ) : Destination()

    @Serializable
    data class Playback(
        val itemId: UUID,
        val positionMs: Long,
        val forceTranscoding: Boolean = false,
        val backend: PlayerBackend? = null,
        val shuffle: Boolean = false,
    ) : Destination(true) {
        constructor(item: BaseItem) : this(item.id, item.resumeMs)
    }

    @Serializable
    data class PlaybackList(
        val itemId: UUID,
        val filter: GetItemsFilter = GetItemsFilter(),
        val startIndex: Int? = null,
        val shuffle: Boolean = false,
        val recursive: Boolean = false,
        val sortAndDirection: SortAndDirection? = null,
    ) : Destination(true) {
        override fun toString(): String = "PlaybackList(itemId=$itemId)"
    }

    @Serializable
    data class FilteredCollection(
        val itemId: UUID,
        val parentType: BaseItemKind,
        val filter: CollectionFolderFilter,
        val recursive: Boolean,
        val collectionType: CollectionType,
    ) : Destination(false)

    @Serializable
    data class ItemGrid<T>(
        val title: StringProvider,
        @Contextual val request: T,
        val requestHandler: RequestHandler<T>,
        val initialPosition: Int = 0,
        val viewOptions: ViewOptions = ViewOptions(),
    ) : Destination(false)

    @Serializable
    data class MoreHomeRow(
        val title: StringProvider,
        val config: HomeRowConfig,
        val initialPosition: Int,
    ) : Destination(false)

    @Serializable
    data class Slideshow(
        val parentId: UUID?,
        val index: Int,
        val filter: CollectionFolderFilter,
        val sortAndDirection: SortAndDirection,
        val recursive: Boolean,
        val startSlideshow: Boolean,
    ) : Destination(true)

    @Serializable
    data object Favorites : Destination(false)

    @Serializable
    data class DiscoveredItem(
        val item: DiscoverItem,
        // Index to scroll to on the person page grid
        val startIndex: Int = 0,
    ) : Destination(false)

    @Serializable
    data class DiscoverMoreResult(
        val type: DiscoverRequestType,
        val startIndex: Int = SEERR_PAGE_SIZE,
        val initialFilter: DiscoverFilter = DiscoverFilter(),
        val titleOverride: StringProvider? = null,
    ) : Destination(false)

    @Serializable
    data object NowPlaying : Destination(true)

    @Serializable
    data object Games : Destination(false)

    /** Benflix redesign: the Movies door (amber) */
    @Serializable
    data object MoviesDoor : Destination(false)

    /** Benflix redesign: the Shows door (blue) */
    @Serializable
    data object ShowsDoor : Destination(false)

    /** Benflix music: the Music door (violet) */
    @Serializable
    data object MusicDoor : Destination(false)

    /**
     * Benflix music: a list of songs or a grid of albums, artists or genres. [kind] is one of
     * liked, mix, albums, recent, played, artists, genres, genre, playlists
     */
    @Serializable
    data class MusicList(
        val kind: String,
        val id: String? = null,
        val title: String = "",
    ) : Destination(false)

    /** Benflix music: Up next, its own page (playing now, songs you added, then the mix) */
    @Serializable
    data object MusicUpNext : Destination(false)

    /** A "Browse by" tile inside a door: genres, collections, studios / networks, or everything */
    @Serializable
    data class DoorBrowse(
        val door: String,
        val what: String,
    ) : Destination(false)

    /** A door row's "See all": the whole list as a grid */
    @Serializable
    data class DoorRowGrid(
        val door: String,
        val key: String,
        val title: String,
    ) : Destination(false)

    /** A TV guide of Benflix channels made from your library */
    @Serializable
    data object Channels : Destination(false)

    /** Big franchises in story or release order */
    @Serializable
    data object WatchOrder : Destination(false)

    /** Full-screen trailers on the TV, one after another */
    @Serializable
    data object TrailerShuffle : Destination(true)

    /** Two quick questions for a group, then picks everyone will like */
    @Serializable
    data object MoodQuiz : Destination(false)

    /** Benflix "This or that": pick one of two posters, 8 rounds, the winner is tonight's pick */
    @Serializable
    data object ThisOrThat : Destination(false)

    /** Your own page on the TV: continue watching, My List, liked, watch history */
    @Serializable
    data object MyBenflix : Destination(false)

    /** Requests: find new things to add (admin) or see what you asked for */
    @Serializable
    data object Requests : Destination(false)

    /** Benflix (Requests): every anime with all of these AniList genres, like Isekai + Slice of Life */
    @Serializable
    data class AnimeGenres(
        val picked: List<String>,
    ) : Destination(false)

    /** A page for one genre: its best, hidden gems, what's new and shorter picks */
    @Serializable
    data class GenreHub(
        val genre: String,
    ) : Destination(false)

    /** A full page of picks for one "Pick a mood" tile */
    @Serializable
    data class Mood(
        val moodName: String,
    ) : Destination(false)

    @Serializable
    data class PlayGame(
        val gameName: String,
    ) : Destination(true)

    /** Benflix: the welcome setup the first time a person uses Benflix */
    @Serializable
    data object Welcome : Destination(true)

    /** Benflix: "Guess the movie" game made from your own library */
    @Serializable
    data object GuessTheMovie : Destination(true)

    /** Benflix "Movie riddle": timed riddles one after another until a miss */
    @Serializable
    data object DailyRiddle : Destination(true)

    /** Benflix: "Higher or Lower" critics score game */
    @Serializable
    data object HigherLower : Destination(true)

    /** Benflix "Ember Road": pixel adventure, carry the dragon egg to Ember Peak */
    @Serializable
    data object EmberRoad : Destination(true)

    /** Benflix: a game's own page (preview, how to play, scores). id is "movie:NAME" or "arcade:NAME" */
    @Serializable
    data class GameInfo(
        val id: String,
    ) : Destination(true)

    /** Benflix: one of the movie puzzle games (MovieGame name) */
    @Serializable
    data class MovieGamePlay(
        val name: String,
    ) : Destination(true)

    /** Benflix: hosting a party game on this TV (PartyKind name) */
    @Serializable
    data class PartyHost(
        val kind: String,
    ) : Destination(true)

    /** Benflix: this phone joining a party game on a TV */
    @Serializable
    data object PartyJoin : Destination(true)

    /** Benflix: simple help page (remote buttons and tips) */
    @Serializable
    data object HowTo : Destination(false)

    /** Benflix: admin-only page showing who's watching and how the server is doing */
    @Serializable
    data object ServerStatus : Destination(false)

    @Serializable
    data object UpdateApp : Destination(true)

    @Serializable
    data object License : Destination(true)

    @Serializable
    data object Debug : Destination(true)
}
