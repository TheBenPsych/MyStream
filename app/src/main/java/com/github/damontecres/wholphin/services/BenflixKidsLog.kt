package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.core.content.edit
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/** One thing played on Kids */
data class KidsWatch(
    val time: Long,
    /** The show for episodes, the movie itself otherwise */
    val titleId: String,
    val title: String,
    val isEpisode: Boolean,
    val minutes: Int,
)

/** Totals for "What Kids watched this week" */
data class KidsWeek(
    val minutes: Int,
    val episodes: Int,
    val movies: Int,
    /** Most watched first: title, how many times */
    val top: List<Pair<String, Int>>,
)

/**
 * Benflix "What Kids watched this week". Kids uses a grown-up's sign-in, so the server can't
 * tell Kids apart; each device keeps a small log while Kids is on. Kept for 30 days.
 */
object BenflixKidsLog {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Call when something starts playing; only counts while Kids is on */
    fun record(
        context: Context,
        item: BaseItemDto,
    ) {
        if (!KidsMode.isActive) return
        try {
            val isEpisode = item.type == BaseItemKind.EPISODE
            val titleId = (if (isEpisode) item.seriesId else item.id)?.toString() ?: return
            val title = (if (isEpisode) item.seriesName else item.name) ?: return
            val minutes = ((item.runTimeTicks ?: 0L) / TICKS_PER_MINUTE).toInt()
            val now = System.currentTimeMillis()
            val list = entries(context).filter { now - it.time < KEEP_MS }
            // A movie started again within an hour (after a pause or a crash) counts once
            if (!isEpisode && list.any { it.titleId == titleId && now - it.time < HOUR_MS }) return
            save(context, list + KidsWatch(now, titleId, title, isEpisode, minutes))
        } catch (ex: Exception) {
            Timber.w(ex, "Kids log: couldn't save")
        }
    }

    fun thisWeek(context: Context): KidsWeek {
        val since = System.currentTimeMillis() - WEEK_MS
        val week = entries(context).filter { it.time >= since }
        return KidsWeek(
            minutes = week.sumOf { it.minutes },
            episodes = week.count { it.isEpisode },
            movies = week.count { !it.isEpisode },
            top =
                week
                    .groupBy { it.title }
                    .map { (title, list) -> title to list.size }
                    .sortedByDescending { it.second }
                    .take(TOP),
        )
    }

    /** Shows and movies watched most on Kids (last 30 days), most first, for "Watch it again" */
    fun favouriteIds(context: Context): List<String> =
        entries(context)
            .groupBy { it.titleId }
            .entries
            .sortedByDescending { it.value.size }
            .map { it.key }

    private fun entries(context: Context): List<KidsWatch> =
        try {
            val array = JSONArray(prefs(context).getString(KEY, "[]"))
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                KidsWatch(
                    time = o.optLong("t"),
                    titleId = o.optString("id"),
                    title = o.optString("name"),
                    isEpisode = o.optBoolean("ep"),
                    minutes = o.optInt("m"),
                )
            }
        } catch (ex: Exception) {
            emptyList()
        }

    private fun save(
        context: Context,
        list: List<KidsWatch>,
    ) {
        val array = JSONArray()
        list.takeLast(MAX).forEach {
            array.put(
                JSONObject()
                    .put("t", it.time)
                    .put("id", it.titleId)
                    .put("name", it.title)
                    .put("ep", it.isEpisode)
                    .put("m", it.minutes),
            )
        }
        prefs(context).edit { putString(KEY, array.toString()) }
    }

    private const val PREFS = "benflix_kids_log"
    private const val KEY = "log"
    private const val MAX = 600
    private const val TOP = 6
    private const val TICKS_PER_MINUTE = 600_000_000L
    private const val HOUR_MS = 60 * 60_000L
    private const val WEEK_MS = 7 * 24 * HOUR_MS
    private const val KEEP_MS = 30 * 24 * HOUR_MS
}
