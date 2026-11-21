package com.github.damontecres.wholphin.ui.detail.discover

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Glowfin (Ben, 2026-11-21): the Details list on the page of a movie or show you could ask for.
 * Who made it and who owns that studio, when it came out, whether it is out at home yet, how big a
 * film it was (cost, cinema takings, awards), and for shows whether it is finished.
 * Everything comes from the lookup the page already does, so nothing gets slower.
 */
object RequestFacts {
    private val LONG_DAY = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
    private val SHORT_DAY = DateTimeFormatter.ofPattern("MMMM d", Locale.US)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.US)

    /** TMDB release types: 4 = digital, 5 = disc, 6 = TV */
    private val HOME_RELEASE_TYPES = setOf(4, 5, 6)
    private const val CINEMA_TO_HOME_DAYS = 90L
    const val BIG_BUDGET = 75_000_000L
    const val SMALL_BUDGET = 5_000_000L

    /** A company behind a title: its TMDB id (for the studio page), name and country code */
    data class Company(
        val id: Int?,
        val name: String,
        val country: String?,
    )

    fun companies(
        details: JsonObject,
        field: String = "productionCompanies",
    ): List<Company> =
        (details[field] as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { c ->
                val name = c["name"].str()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Company(
                    c["id"].str()?.toIntOrNull(),
                    name,
                    (c["originCountry"].str() ?: c["origin_country"].str())?.takeIf { it.isNotBlank() },
                )
            }.distinctBy { it.name.lowercase() }

    /** The company to show first: the first well known one, otherwise the first listed */
    fun main(list: List<Company>): Company? = list.firstOrNull { StudioOwners.of(it.name) != null } ?: list.firstOrNull()

    private fun countryName(code: String?): String? =
        code?.let { Locale("", it).getDisplayCountry(Locale.US).takeIf { n -> n.isNotBlank() && !n.equals(it, ignoreCase = true) } }

    private fun day(text: String?): LocalDate? =
        text
            ?.takeIf {
                it.length >= 10
            }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }

    fun longDay(d: LocalDate): String = d.format(LONG_DAY)

    private fun nearDay(
        d: LocalDate,
        today: LocalDate,
    ): String = if (d.year == today.year) d.format(SHORT_DAY) else d.format(LONG_DAY)

    /** "$85 million", "$1.2 billion", "$400,000" */
    fun money(amount: Long): String =
        when {
            amount >= 1_000_000_000L -> "$" + trim(amount / 1_000_000_000.0) + " billion"
            amount >= 1_000_000L -> "$" + trim(amount / 1_000_000.0) + " million"
            else -> "$" + String.format(Locale.US, "%,d", amount)
        }

    private fun trim(n: Double): String =
        if (n >= 100 ||
            n % 1.0 < 0.05
        ) {
            String.format(Locale.US, "%.0f", n)
        } else {
            String.format(Locale.US, "%.1f", n)
        }

    /** Early / mid / late + the month: a guess is never given to the day */
    private fun roughly(d: LocalDate): String =
        (
            if (d.dayOfMonth <= 10) {
                "early "
            } else if (d.dayOfMonth <= 20) {
                "mid "
            } else {
                "late "
            }
        ) + d.format(MONTH)

    /** Movies: (label, words) lines for the cinema and home release, as of [today] */
    fun releaseLines(
        details: JsonObject,
        today: LocalDate = LocalDate.now(),
    ): List<Pair<String, String>> {
        val cinema = day(details["releaseDate"].str())
        val home =
            ((details["releases"] as? JsonObject)?.get("results") as? JsonArray)
                .orEmpty()
                .mapNotNull { it as? JsonObject }
                .flatMap { (it["release_dates"] as? JsonArray).orEmpty().mapNotNull { d -> d as? JsonObject } }
                .filter { it["type"].str()?.toIntOrNull() in HOME_RELEASE_TYPES }
                .mapNotNull { day(it["release_date"].str()) }
                .minOrNull()
        return buildList {
            if (cinema != null) add((if (cinema.isAfter(today)) "Comes out" else "Released") to longDay(cinema))
            when {
                home != null && !home.isAfter(today) -> {
                    // Only worth saying while it's fresh: an old film is plainly out at home
                    if (home.isAfter(today.minusDays(365))) add("At home" to "Out since ${nearDay(home, today)}")
                }

                home != null -> {
                    add("At home" to "Not yet · ${nearDay(home, today)}")
                }

                cinema != null && cinema.plusDays(CINEMA_TO_HOME_DAYS).isAfter(today) -> {
                    add("At home" to "Not yet · around ${roughly(cinema.plusDays(CINEMA_TO_HOME_DAYS))}")
                }
            }
        }
    }

    /** Shows: finished or still going, how much of it there is, and the next episode */
    fun showLines(
        details: JsonObject,
        today: LocalDate = LocalDate.now(),
    ): List<Pair<String, String>> {
        val status = details["status"].str().orEmpty()
        val next = day((details["nextEpisodeToAir"] as? JsonObject)?.get("airDate").str())
        val seasons = details["numberOfSeasons"].str()?.toIntOrNull()?.takeIf { it > 0 }
        val episodes = details["numberOfEpisodes"].str()?.toIntOrNull()?.takeIf { it > 0 }
        val word =
            when (status) {
                "Returning Series" -> if (next != null) "Still going" else "Still going, between seasons"
                "Ended" -> "Finished"
                "Canceled" -> "Cancelled"
                "In Production", "Planned", "Pilot" -> "Not out yet"
                else -> null
            }
        val size =
            listOfNotNull(
                seasons?.let { if (it == 1) "1 season" else "$it seasons" },
                episodes?.let { if (it == 1) "1 episode" else "$it episodes" },
            )
        return buildList {
            val text = (listOfNotNull(word) + size).joinToString(" · ")
            if (text.isNotEmpty()) add("Status" to text)
            if (next != null && !next.isBefore(today)) add("Next episode" to nearDay(next, today))
        }
    }

    /** The whole Details list. [awards] is OMDb's awards line ("Won 2 Oscars. 11 wins & 34 nominations total"). */
    fun details(
        details: JsonObject,
        isMovie: Boolean,
        awards: String?,
        today: LocalDate = LocalDate.now(),
    ): List<Pair<String, String>> =
        buildList {
            val made = companies(details)
            val networks = if (isMovie) emptyList() else companies(details, "networks")
            val first = if (isMovie) main(made) else main(networks) ?: main(made)
            if (first != null) {
                val shownOn = !isMovie && networks.isNotEmpty()
                add((if (shownOn) "Shown on" else "Made by") to StudioOwners.line(first.name))
                StudioOwners.of(first.name)?.let { add("Studio" to it.kind) }
                val others =
                    made
                        .filter { it.name != first.name }
                        .take(3)
                        .map { c -> countryName(c.country)?.takeIf { it != "United States" }?.let { "${c.name} ($it)" } ?: c.name }
                if (others.isNotEmpty()) add((if (shownOn) "Made by" else "With") to others.joinToString(", "))
            }
            if (isMovie) {
                addAll(releaseLines(details, today))
                val budget = details["budget"].str()?.toLongOrNull()?.takeIf { it > 0 }
                val revenue = details["revenue"].str()?.toLongOrNull()?.takeIf { it > 0 }
                if (budget != null) {
                    val size =
                        if (budget >= BIG_BUDGET) {
                            " · big-budget film"
                        } else if (budget <= SMALL_BUDGET) {
                            " · small film"
                        } else {
                            ""
                        }
                    add("Cost" to money(budget) + size)
                }
                if (revenue != null) add("Cinemas" to "Made ${money(revenue)}")
            } else {
                day(details["firstAirDate"].str())?.let { add((if (it.isAfter(today)) "Starts" else "First aired") to longDay(it)) }
                addAll(showLines(details, today))
            }
            awards?.trim()?.takeIf { it.isNotEmpty() && !it.equals("N/A", ignoreCase = true) }?.let { add("Awards" to it) }
        }
}
