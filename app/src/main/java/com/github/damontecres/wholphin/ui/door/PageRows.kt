package com.github.damontecres.wholphin.ui.door

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import timber.log.Timber
import java.util.UUID

/**
 * Glowfin (ui-7, Ben 2026-11-18): Settings > Look > Home rows. Each person switches every row of Home, Movies,
 * Shows and Anime on or off and puts them in their own order. Kept on this TV or phone, per profile.
 * Continue watching (and Next up) always stay at the top, so they aren't in the list.
 */
object PageRows {
    /** One row someone can switch on or off */
    data class Choice(
        val key: String,
        val label: String,
        val on: Boolean,
    )

    /** The pages that have rows to pick */
    enum class Page(
        val title: String,
    ) {
        HOME("Home"),
        MOVIES("Movies"),
        SHOWS("Shows"),
        ANIME("Anime"),
    }

    /** Counts up on every change, so open pages load their rows again */
    val changes = mutableIntStateOf(0)

    /** Every row a page can have below Continue watching, in the usual order, with its name in Settings */
    fun all(page: Page): List<Pair<String, String>> =
        when (page) {
            Page.HOME -> {
                listOf(
                    WHATS_NEW to "What's new",
                    DoorViewModel.NEW to "Recently added",
                    DoorViewModel.TOP_10 to "Top 10 this week",
                    DoorViewModel.BECAUSE to "Because you watched…",
                    DoorViewModel.TRENDING_MISSING to "Trending, not on Tower",
                    DoorViewModel.AIRING to "Anime airing this season",
                    DoorViewModel.WATCH_AGAIN to "Watch it again",
                )
            }

            Page.MOVIES -> {
                listOf(
                    DoorViewModel.TOP_10 to "Top 10 this week",
                    DoorViewModel.BECAUSE to "Because you watched…",
                    DoorViewModel.NEW to "Recently added",
                    DoorViewModel.RELEASED to "Recently released",
                    DoorViewModel.TOP_RATED to "Top rated you haven't seen",
                    DoorViewModel.STAND_UP to "Stand-up",
                )
            }

            Page.SHOWS -> {
                listOf(
                    DoorViewModel.TOP_10 to "Top 10 this week",
                    DoorViewModel.NEW_EPISODES to "New episodes",
                    DoorViewModel.BECAUSE to "Because you watched…",
                    DoorViewModel.NEW to "Recently added",
                    DoorViewModel.TOP_RATED to "Top rated you haven't seen",
                    DoorViewModel.TRENDING_MISSING to "Trending, not on Tower",
                )
            }

            Page.ANIME -> {
                listOf(
                    DoorViewModel.TOP_10_ANIME to "Top 10 anime",
                    DoorViewModel.AIRING to "Airing this season",
                    DoorViewModel.BECAUSE to "Because you watched…",
                    DoorViewModel.GEMS to "Hidden gem anime",
                    DoorViewModel.ANIME_MOVIES to "Anime movies",
                    DoorViewModel.NEW to "Recently added",
                )
            }
        }

    /** This person's rows for [page]: their order, with on/off. New rows (added in an update) join at the end, on. */
    fun choices(
        context: Context,
        userId: UUID,
        page: Page,
    ): List<Choice> {
        val all = all(page)
        val saved =
            try {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(userId, page), null)
            } catch (ex: Exception) {
                Timber.w(ex, "Could not read the row choices")
                null
            }
        if (saved.isNullOrBlank()) return all.map { (k, label) -> Choice(k, label, true) }
        val labels = all.toMap()
        val mine =
            saved.split(",").mapNotNull { part ->
                val k = part.removePrefix("-")
                labels[k]?.let { Choice(k, it, !part.startsWith("-")) }
            }
        val missing = all.filter { (k, _) -> mine.none { it.key == k } }.map { (k, label) -> Choice(k, label, true) }
        return (mine + missing).distinctBy { it.key }
    }

    /** The row keys that are switched on, in order */
    fun shown(
        context: Context,
        userId: UUID,
        page: Page,
    ): List<String> = choices(context, userId, page).filter { it.on }.map { it.key }

    fun save(
        context: Context,
        userId: UUID,
        page: Page,
        list: List<Choice>,
    ) {
        try {
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(key(userId, page), list.joinToString(",") { if (it.on) it.key else "-${it.key}" })
                .apply()
        } catch (ex: Exception) {
            Timber.w(ex, "Could not keep the row choices")
        }
        changes.intValue++
    }

    private fun key(
        userId: UUID,
        page: Page,
    ) = "$userId-${page.name}"

    /** Home's "What's new" row: the news cards (request arrivals, notes from Ben, Tower news) */
    const val WHATS_NEW = "whatsnew"

    private const val PREFS = "glowfin_page_rows"
}
