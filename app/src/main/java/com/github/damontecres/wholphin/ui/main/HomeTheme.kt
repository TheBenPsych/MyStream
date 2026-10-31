package com.github.damontecres.wholphin.ui.main

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * A row on the home page whose theme changes with the time of year
 */
data class ThemedRow(
    val title: String,
    val items: List<BaseItem>,
)

/**
 * What the themed row shows: items in any of [genres] and/or whose name matches [searchTerm]
 */
data class HomeTheme(
    @param:StringRes val title: Int,
    val genres: List<String>? = null,
    val searchTerm: String? = null,
    val titleArg: String = "",
) {
    companion object {
        // Genre names as Jellyfin gets them from TMDB
        private val weeklyGenres =
            listOf(
                "Comedy",
                "Science Fiction",
                "Documentary",
                "Animation",
                "Crime",
                "Mystery",
                "Fantasy",
                "Drama",
                "Thriller",
                "Family",
                "Adventure",
                "Music",
            )

        fun forDate(date: LocalDate): HomeTheme {
            val month = date.monthValue
            val day = date.dayOfMonth
            return when {
                month == 10 -> {
                    HomeTheme(R.string.theme_halloween, genres = listOf("Horror"))
                }

                month == 12 || (month == 11 && day >= 25) -> {
                    HomeTheme(R.string.theme_holiday, searchTerm = "Christmas")
                }

                month == 2 && day <= 14 -> {
                    HomeTheme(R.string.theme_date_night, genres = listOf("Romance"))
                }

                month in 6..8 -> {
                    HomeTheme(R.string.theme_summer, genres = listOf("Action", "Adventure"))
                }

                else -> {
                    weekly(date)
                }
            }
        }

        /**
         * A different genre each week, also used when the seasonal theme finds nothing on the server
         */
        fun weekly(date: LocalDate): HomeTheme {
            val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            val genre = weeklyGenres[week % weeklyGenres.size]
            return HomeTheme(R.string.theme_genre_of_week, genres = listOf(genre), titleArg = genre)
        }
    }
}
