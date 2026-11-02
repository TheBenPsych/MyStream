package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.services.Celebration
import com.github.damontecres.wholphin.services.HomePageResolvedSettings
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState

data class HomeState(
    val loadingState: LoadingState,
    val refreshState: LoadingState,
    val homeRows: List<HomeRowLoadingState>,
    val settings: HomePageResolvedSettings,
    val featured: List<FeaturedItem> = emptyList(),
    val trending: List<BaseItem> = emptyList(),
    val trendingMissing: List<DiscoverItem> = emptyList(),
    val themed: ThemedRow? = null,
    /** Month-long rows, such as Black History Month in February or Pride Month in June */
    val monthly: List<ThemedRow> = emptyList(),
    val because: ThemedRow? = null,
    /** Benflix Kids: "Watch it again", what Kids watched most */
    val kidsAgain: ThemedRow? = null,
    val becauseMore: ThemedRow? = null,
    val collections: ThemedRow? = null,
    val mood: HomeMood? = null,
    val moodRow: ThemedRow? = null,
    val celebration: Celebration? = null,
    val newest: ThemedRow? = null,
    val myList: ThemedRow? = null,
    /** "Come back to it": started a while ago, never finished */
    val comeBack: ThemedRow? = null,
    /** Benflix: "While you were away", once, after two weeks or more without opening Benflix */
    val welcomeBack: ThemedRow? = null,
    /** Benflix "Around the world": movies from one country, like South Korea */
    val world: List<ThemedRow> = emptyList(),
    /** Benflix: anime shows airing right now */
    val airing: ThemedRow? = null,
    /** Person spotlight: an actor from what was watched lately */
    val spotlight: ThemedRow? = null,
    val genreRows: List<ThemedRow> = emptyList(),
    /** "Top 10 anime": Anime Movies and Anime Shows together */
    val animeTop: List<BaseItem> = emptyList(),
    /** Anime picks: because you watched an anime, hidden gems, anime by genre */
    val animeRows: List<ThemedRow> = emptyList(),
    /** Whether these rows were loaded for the Kids profile */
    val kids: Boolean = false,
) {
    companion object {
        val EMPTY =
            HomeState(
                LoadingState.Pending,
                LoadingState.Pending,
                emptyList(),
                HomePageResolvedSettings.EMPTY,
            )
    }
}
