package com.github.damontecres.wholphin.ui.main

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.diwali
import com.github.damontecres.wholphin.services.lunarNewYear
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * A row on the home page whose theme changes with the time of year
 */
data class ThemedRow(
    val title: String,
    val items: List<BaseItem>,
)

/**
 * What the themed row shows.
 *
 * Holiday rows only show titles that really fit the holiday:
 * - [titles]: hand-picked movies and shows, found on the server by their exact name
 * - [tags]: TMDB keywords (Jellyfin saves them as tags), such as "day of the dead"
 * - [nameWords]: words in the title itself, such as "Christmas"
 *
 * If fewer than [minItems] fitting titles are on the server, the row is hidden for that holiday
 * (the genre of the week shows instead) rather than filled with random picks.
 *
 * [genres] is only for themes that really are a genre (Halloween = horror, Date night = romance):
 * those genre picks are added after the hand-picked ones.
 */
data class HomeTheme(
    @param:StringRes val title: Int,
    val genres: List<String>? = null,
    val tags: List<String> = emptyList(),
    val titles: List<String> = emptyList(),
    val nameWords: List<String> = emptyList(),
    val titleArg: String = "",
    val minItems: Int = MIN_HOLIDAY_ITEMS,
) {
    /** True when the row is built only from fitting titles (no broad genre to fill it) */
    val isStrict: Boolean get() = genres.isNullOrEmpty()

    /** True when [name] (or the original title) is one of the hand-picked [titles] */
    fun isPicked(
        name: String?,
        originalTitle: String? = null,
    ): Boolean {
        if (titles.isEmpty()) return false
        val wanted = pickedKeys
        return listOfNotNull(name, originalTitle).any { normalizeTitle(it) in wanted }
    }

    /** True when [name] contains one of the [nameWords] as a whole word */
    fun hasNameWord(name: String?): Boolean {
        if (name == null || nameWords.isEmpty()) return false
        val words = " ${normalizeTitle(name, keepThe = true)} "
        return nameWords.any { words.contains(" ${normalizeTitle(it, keepThe = true)} ") }
    }

    private val pickedKeys by lazy { titles.map { normalizeTitle(it) }.toSet() }

    companion object {
        const val MIN_HOLIDAY_ITEMS = 4

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

        /**
         * "The Book of Life!" and "book of life" match: lower case, no accents, no punctuation,
         * "&" = "and", and a leading "The"/"A" is ignored.
         */
        fun normalizeTitle(
            title: String,
            keepThe: Boolean = false,
        ): String {
            val plain =
                Normalizer
                    .normalize(title, Normalizer.Form.NFD)
                    .replace(Regex("\\p{M}+"), "")
                    .lowercase()
                    .replace("&", " and ")
                    .replace("'", "")
                    .replace("’", "")
                    .replace(Regex("[^a-z0-9]+"), " ")
                    .trim()
            return if (keepThe) plain else plain.replace(Regex("^(the|a|an) "), "")
        }

        val DAY_OF_THE_DEAD =
            HomeTheme(
                R.string.theme_day_of_the_dead,
                tags = listOf("day of the dead", "dia de los muertos", "dia de muertos", "día de muertos", "día de los muertos"),
                nameWords = listOf("Muertos"),
                titles =
                    listOf(
                        "Coco",
                        "The Book of Life",
                        "Corpse Bride",
                        "Día de Muertos",
                        "Macario",
                        "Spectre",
                        "Beetlejuice",
                        "Beetlejuice Beetlejuice",
                        "Soul",
                        "Kubo and the Two Strings",
                        "Big Fish",
                        "What Dreams May Come",
                        "Ghost",
                        "The Lovely Bones",
                        "Hereafter",
                        "Onward",
                        "Casper",
                        "Frankenweenie",
                        "Encanto",
                        "Elena and the Secret of Avalor",
                        "Victor and Valentino",
                        "Maya and the Three",
                        "The Haunted Mansion",
                        "Haunted Mansion",
                    ),
            )

        val HALLOWEEN =
            HomeTheme(
                R.string.theme_halloween,
                genres = listOf("Horror"),
                tags = listOf("halloween", "trick or treating", "haunted house", "witch", "jack o'lantern"),
                nameWords = listOf("Halloween"),
                titles =
                    listOf(
                        "Hocus Pocus",
                        "Hocus Pocus 2",
                        "It's the Great Pumpkin, Charlie Brown",
                        "Halloweentown",
                        "Trick 'r Treat",
                        "The Nightmare Before Christmas",
                        "Casper",
                        "Beetlejuice",
                        "Beetlejuice Beetlejuice",
                        "Ghostbusters",
                        "The Addams Family",
                        "Addams Family Values",
                        "Hotel Transylvania",
                        "Coraline",
                        "ParaNorman",
                        "Monster House",
                        "Goosebumps",
                        "Practical Magic",
                        "Corpse Bride",
                        "Frankenweenie",
                        "Monsters, Inc.",
                        "The Witches",
                        "Edward Scissorhands",
                        "Wednesday",
                        "Scooby-Doo",
                    ),
            )

        val THANKSGIVING =
            HomeTheme(
                R.string.theme_thanksgiving,
                tags = listOf("thanksgiving"),
                nameWords = listOf("Thanksgiving"),
                titles =
                    listOf(
                        "Planes, Trains and Automobiles",
                        "A Charlie Brown Thanksgiving",
                        "Free Birds",
                        "Home for the Holidays",
                        "Pieces of April",
                        "Dutch",
                        "Addams Family Values",
                        "The Ice Storm",
                        "Hannah and Her Sisters",
                        "Scent of a Woman",
                        "Miracle on 34th Street",
                        "The Last Waltz",
                        "Krisha",
                        "Friendsgiving",
                        "Thanksgiving",
                    ),
            )

        val CHRISTMAS =
            HomeTheme(
                R.string.theme_holiday,
                tags = listOf("christmas", "christmas eve", "santa claus", "christmas party", "christmas tree", "hanukkah"),
                nameWords = listOf("Christmas", "Xmas", "Santa", "Grinch", "Noel", "Hanukkah"),
                titles =
                    listOf(
                        "Elf",
                        "Home Alone",
                        "Home Alone 2: Lost in New York",
                        "Die Hard",
                        "The Polar Express",
                        "Love Actually",
                        "Klaus",
                        "Arthur Christmas",
                        "Jingle All the Way",
                        "Gremlins",
                        "It's a Wonderful Life",
                        "Miracle on 34th Street",
                        "The Holiday",
                        "Scrooged",
                        "The Muppet Christmas Carol",
                        "Rise of the Guardians",
                        "Frosty the Snowman",
                        "Rudolph the Red-Nosed Reindeer",
                        "Krampus",
                        "Violent Night",
                        "Last Christmas",
                        "White Christmas",
                        "Noelle",
                        "Fred Claus",
                        "Four Christmases",
                        "Deck the Halls",
                        "Jack Frost",
                        "Eight Crazy Nights",
                        "The Nightmare Before Christmas",
                        "Edward Scissorhands",
                        "Trading Places",
                        "Spirited",
                        "Single All the Way",
                        "Dash & Lily",
                        "Shazam!",
                        "Batman Returns",
                    ),
            )

        val VALENTINES =
            HomeTheme(
                R.string.theme_date_night,
                genres = listOf("Romance"),
                tags = listOf("valentine's day"),
                nameWords = listOf("Valentine"),
            )

        val ST_PATRICKS =
            HomeTheme(
                R.string.theme_st_patricks,
                tags = listOf("st. patrick's day", "saint patrick's day", "ireland", "irish", "leprechaun", "dublin, ireland"),
                nameWords = listOf("Leprechaun", "Irish", "Ireland", "Patrick's"),
                titles =
                    listOf(
                        "The Secret of Kells",
                        "Song of the Sea",
                        "Wolfwalkers",
                        "Darby O'Gill and the Little People",
                        "The Quiet Man",
                        "The Commitments",
                        "Sing Street",
                        "The Banshees of Inisherin",
                        "Waking Ned Devine",
                        "Waking Ned",
                        "P.S. I Love You",
                        "Leap Year",
                        "Brooklyn",
                        "Once",
                        "Belfast",
                        "Derry Girls",
                        "Luck",
                        "The Boondock Saints",
                        "Finian's Rainbow",
                        "Ondine",
                        "The Wind That Shakes the Barley",
                        "Michael Collins",
                        "In Bruges",
                    ),
            )

        val LUNAR_NEW_YEAR =
            HomeTheme(
                R.string.theme_lunar_new_year,
                tags = listOf("chinese new year", "lunar new year", "spring festival", "seollal", "tet"),
                nameWords = listOf("Lunar"),
                titles =
                    listOf(
                        "Turning Red",
                        "Mulan",
                        "Mulan II",
                        "Kung Fu Panda",
                        "Kung Fu Panda 2",
                        "Kung Fu Panda 3",
                        "Kung Fu Panda 4",
                        "Over the Moon",
                        "Crazy Rich Asians",
                        "The Farewell",
                        "Everything Everywhere All at Once",
                        "Crouching Tiger, Hidden Dragon",
                        "Shang-Chi and the Legend of the Ten Rings",
                        "Raya and the Last Dragon",
                        "Big Fish & Begonia",
                        "The Wandering Earth",
                        "Ne Zha",
                        "Wish Dragon",
                        "Kung Fu Hustle",
                        "Hero",
                        "Minari",
                        "Bao",
                        "American Born Chinese",
                        "Joy Luck Club",
                        "The Joy Luck Club",
                        "Meet the Patels",
                    ),
            )

        val DIWALI =
            HomeTheme(
                R.string.theme_diwali,
                tags = listOf("diwali", "deepavali", "festival of lights", "bollywood"),
                nameWords = listOf("Diwali", "Deepavali"),
                titles =
                    listOf(
                        "Kabhi Khushi Kabhie Gham...",
                        "Dilwale Dulhania Le Jayenge",
                        "Hum Saath-Saath Hain",
                        "Mohabbatein",
                        "Kuch Kuch Hota Hai",
                        "Taare Zameen Par",
                        "3 Idiots",
                        "RRR",
                        "Lagaan: Once Upon a Time in India",
                        "Baahubali: The Beginning",
                        "Baahubali 2: The Conclusion",
                        "Monsoon Wedding",
                        "Bend It Like Beckham",
                        "Sita Sings the Blues",
                        "Mira, Royal Detective",
                        "Mighty Little Bheem",
                        "Never Have I Ever",
                        "Sanjay's Super Team",
                        "Slumdog Millionaire",
                        "Life of Pi",
                        "The Lunchbox",
                        "Dangal",
                        "Om Shanti Om",
                        "Bride & Prejudice",
                    ),
            )

        val TANABATA =
            HomeTheme(
                R.string.theme_tanabata,
                tags = listOf("tanabata", "star festival", "wish upon a star"),
                nameWords = listOf("Tanabata"),
                titles =
                    listOf(
                        "Your Name.",
                        "Weathering with You",
                        "Suzume",
                        "5 Centimeters per Second",
                        "The Garden of Words",
                        "Whisper of the Heart",
                        "The Girl Who Leapt Through Time",
                        "Howl's Moving Castle",
                        "The Tale of the Princess Kaguya",
                        "Children Who Chase Lost Voices",
                        "A Silent Voice",
                        "Stardust",
                        "Pinocchio",
                        "Wish",
                        "Lu Over the Wall",
                        "Wolf Children",
                        "Belle",
                        "Words Bubble Up Like Soda Pop",
                        "Josee, the Tiger and the Fish",
                        "I Want to Eat Your Pancreas",
                    ),
            )

        val FOURTH_OF_JULY =
            HomeTheme(
                R.string.theme_fourth_of_july,
                tags = listOf("independence day", "fourth of july", "4th of july", "american revolution", "founding fathers"),
                nameWords = listOf("Independence Day", "Fourth of July", "4th of July"),
                titles =
                    listOf(
                        "Independence Day",
                        "Independence Day: Resurgence",
                        "Jaws",
                        "Captain America: The First Avenger",
                        "National Treasure",
                        "National Treasure: Book of Secrets",
                        "Hamilton",
                        "Born on the Fourth of July",
                        "The Patriot",
                        "Top Gun",
                        "Top Gun: Maverick",
                        "The Sandlot",
                        "Yankee Doodle Dandy",
                        "1776",
                        "Air Force One",
                        "Mr. Smith Goes to Washington",
                        "Apollo 13",
                        "Forrest Gump",
                        "Lincoln",
                        "John Adams",
                        "Liberty's Kids",
                        "Free State of Jones",
                        "Glory",
                    ),
            )

        val BONFIRE_NIGHT =
            HomeTheme(
                R.string.theme_bonfire_night,
                tags = listOf("guy fawkes", "bonfire night", "gunpowder plot", "fireworks"),
                nameWords = listOf("Fawkes", "Gunpowder", "Bonfire"),
                titles =
                    listOf(
                        "V for Vendetta",
                        "Gunpowder",
                        "The Gunpowder Plot",
                        "Wallace & Gromit: The Curse of the Were-Rabbit",
                        "Paddington",
                        "Paddington 2",
                    ),
            )

        fun forDate(date: LocalDate): HomeTheme {
            val month = date.monthValue
            val day = date.dayOfMonth
            val lny = lunarNewYear(date.year)
            val diwaliDay = diwali(date.year)
            val thanksgiving = date.withDayOfMonth(1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY))
            return when {
                lny != null && !date.isBefore(lny.minusDays(3)) && !date.isAfter(lny.plusDays(3)) -> LUNAR_NEW_YEAR
                month == 10 -> HALLOWEEN
                month == 11 && day <= 2 -> DAY_OF_THE_DEAD
                diwaliDay != null && !date.isBefore(diwaliDay.minusDays(2)) && !date.isAfter(diwaliDay.plusDays(2)) -> DIWALI
                month == 11 && day in 3..5 -> BONFIRE_NIGHT
                month == 11 && !date.isBefore(thanksgiving.minusDays(4)) && !date.isAfter(thanksgiving.plusDays(1)) -> THANKSGIVING
                month == 7 && day <= 4 -> FOURTH_OF_JULY
                month == 7 && day <= 7 -> TANABATA
                month == 12 || (month == 11 && day >= 25) -> CHRISTMAS
                month == 2 && day <= 14 -> VALENTINES
                month == 3 && day in 14..17 -> ST_PATRICKS
                month in 6..8 -> HomeTheme(R.string.theme_summer, genres = listOf("Action", "Adventure"))
                else -> weekly(date)
            }
        }

        /**
         * A different genre each week, also used when the seasonal theme finds too little on the server
         */
        fun weekly(date: LocalDate): HomeTheme {
            val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            val genre = weeklyGenres[week % weeklyGenres.size]
            return HomeTheme(R.string.theme_genre_of_week, genres = listOf(genre), titleArg = genre, minItems = 1)
        }
    }
}
