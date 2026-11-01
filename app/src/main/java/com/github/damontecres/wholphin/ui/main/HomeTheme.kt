package com.github.damontecres.wholphin.ui.main

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.diwali
import com.github.damontecres.wholphin.services.easter
import com.github.damontecres.wholphin.services.isFriday13
import com.github.damontecres.wholphin.services.lunarNewYear
import com.github.damontecres.wholphin.services.midAutumn
import com.github.damontecres.wholphin.services.nthSunday
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

        val EASTER =
            HomeTheme(
                R.string.theme_easter,
                tags = listOf("easter", "easter bunny", "easter egg hunt"),
                nameWords = listOf("Easter", "Bunny"),
                titles =
                    listOf(
                        "Hop",
                        "Peter Rabbit",
                        "Peter Rabbit 2: The Runaway",
                        "Rise of the Guardians",
                        "Here Comes Peter Cottontail",
                        "It's the Easter Beagle, Charlie Brown",
                        "Easter Parade",
                        "The Velveteen Rabbit",
                        "Zootopia",
                        "Watership Down",
                        "Winnie the Pooh: Springtime with Roo",
                        "The Tale of Peter Rabbit",
                        "Bambi",
                        "Charlotte's Web",
                        "Babe",
                    ),
            )

        val STAR_WARS_DAY =
            HomeTheme(
                R.string.theme_star_wars,
                tags = listOf("star wars"),
                nameWords = listOf("Star Wars"),
                titles =
                    listOf(
                        "Star Wars",
                        "The Empire Strikes Back",
                        "Return of the Jedi",
                        "The Phantom Menace",
                        "Attack of the Clones",
                        "Revenge of the Sith",
                        "The Force Awakens",
                        "The Last Jedi",
                        "The Rise of Skywalker",
                        "Rogue One: A Star Wars Story",
                        "Solo: A Star Wars Story",
                        "The Mandalorian",
                        "Andor",
                        "The Book of Boba Fett",
                        "Ahsoka",
                        "Obi-Wan Kenobi",
                        "The Clone Wars",
                        "The Bad Batch",
                        "Star Wars Rebels",
                        "Skeleton Crew",
                        "The Acolyte",
                    ),
            )

        val POKEMON_DAY =
            HomeTheme(
                R.string.theme_pokemon,
                tags = listOf("pokemon", "pokémon"),
                nameWords = listOf("Pokemon", "Pikachu"),
                titles =
                    listOf(
                        "Detective Pikachu",
                        "Pokémon Detective Pikachu",
                        "Pokémon: The First Movie",
                        "Pokémon the Movie 2000",
                        "Pokémon: Mewtwo Strikes Back—Evolution",
                        "Pokémon Horizons",
                        "Pokémon Concierge",
                        "Pokémon Journeys",
                    ),
            )

        val MARIO_DAY =
            HomeTheme(
                R.string.theme_mario,
                tags = listOf("video game", "based on video game", "nintendo"),
                nameWords = listOf("Mario"),
                titles =
                    listOf(
                        "The Super Mario Bros. Movie",
                        "Super Mario Bros.",
                        "Detective Pikachu",
                        "Pokémon Detective Pikachu",
                        "Sonic the Hedgehog",
                        "Sonic the Hedgehog 2",
                        "Sonic the Hedgehog 3",
                        "Wreck-It Ralph",
                        "Ralph Breaks the Internet",
                        "A Minecraft Movie",
                        "Five Nights at Freddy's",
                        "The Angry Birds Movie",
                        "The Angry Birds Movie 2",
                        "Pixels",
                        "Free Guy",
                        "Ready Player One",
                        "Tron",
                        "Tron: Legacy",
                        "Scott Pilgrim vs. the World",
                        "Jumanji: Welcome to the Jungle",
                        "Jumanji: The Next Level",
                        "Tetris",
                        "Uncharted",
                        "Mortal Kombat",
                        "Tomb Raider",
                        "Lara Croft: Tomb Raider",
                        "Warcraft",
                        "Rampage",
                        "Gran Turismo",
                        "Arcane",
                        "The Last of Us",
                        "Fallout",
                        "Castlevania",
                        "Cyberpunk: Edgerunners",
                        "Sonic Prime",
                        "The Witcher",
                        "Halo",
                        "Resident Evil",
                        "Assassin's Creed",
                        "Prince of Persia: The Sands of Time",
                        "WarGames",
                        "The Wizard",
                    ),
            )

        val BACK_TO_THE_FUTURE_DAY =
            HomeTheme(
                R.string.theme_time_travel,
                tags = listOf("time travel", "time machine"),
                nameWords = listOf("Back to the Future"),
                titles =
                    listOf(
                        "Back to the Future",
                        "Back to the Future Part II",
                        "Back to the Future Part III",
                        "Bill & Ted's Excellent Adventure",
                        "Bill & Ted's Bogus Journey",
                        "Bill & Ted Face the Music",
                        "The Terminator",
                        "Terminator 2: Judgment Day",
                        "Looper",
                        "Hot Tub Time Machine",
                        "About Time",
                        "Time Bandits",
                        "The Time Machine",
                        "Interstellar",
                        "Tenet",
                        "Edge of Tomorrow",
                        "Twelve Monkeys",
                        "12 Monkeys",
                        "Meet the Robinsons",
                        "Mr. Peabody & Sherman",
                        "The Adam Project",
                        "Doctor Who",
                        "Dark",
                        "Timeless",
                        "11.22.63",
                        "Peggy Sue Got Married",
                        "Midnight in Paris",
                        "Kate & Leopold",
                        "Primer",
                        "The Butterfly Effect",
                        "Harry Potter and the Prisoner of Azkaban",
                        "Avengers: Endgame",
                    ),
            )

        val MOTHERS_DAY =
            HomeTheme(
                R.string.theme_mothers_day,
                tags = listOf("mother daughter relationship", "mother son relationship", "motherhood", "single mother"),
                nameWords = listOf("Mother", "Mom", "Moms", "Mama", "Mamma"),
                titles =
                    listOf(
                        "Mamma Mia!",
                        "Mamma Mia! Here We Go Again",
                        "Freaky Friday",
                        "Lady Bird",
                        "Terms of Endearment",
                        "Steel Magnolias",
                        "Little Women",
                        "Mrs. Doubtfire",
                        "Mother's Day",
                        "Coco",
                        "Brave",
                        "Turning Red",
                        "Everything Everywhere All at Once",
                        "The Blind Side",
                        "Erin Brockovich",
                        "Gilmore Girls",
                        "Stepmom",
                        "Bad Moms",
                        "Instant Family",
                        "Mermaids",
                        "The Joy Luck Club",
                        "Room",
                        "Tully",
                        "Encanto",
                        "Moana",
                    ),
            )

        val FATHERS_DAY =
            HomeTheme(
                R.string.theme_fathers_day,
                tags = listOf("father son relationship", "father daughter relationship", "single father", "fatherhood"),
                nameWords = listOf("Father", "Dad", "Daddy"),
                titles =
                    listOf(
                        "Finding Nemo",
                        "Big Fish",
                        "Field of Dreams",
                        "The Pursuit of Happyness",
                        "Father of the Bride",
                        "Interstellar",
                        "Taken",
                        "Hook",
                        "Indiana Jones and the Last Crusade",
                        "Big Daddy",
                        "Cheaper by the Dozen",
                        "Road to Perdition",
                        "Life Is Beautiful",
                        "The Lion King",
                        "Aftersun",
                        "How to Train Your Dragon",
                        "Up",
                        "Paddington",
                        "Despicable Me",
                        "Kramer vs. Kramer",
                        "Bluey",
                        "Logan",
                        "Gladiator",
                        "Tommy Boy",
                    ),
            )

        val EARTH_DAY =
            HomeTheme(
                R.string.theme_earth_day,
                tags = listOf("nature", "environmentalism", "climate change", "wildlife", "nature documentary"),
                nameWords = listOf("Planet", "Earth"),
                titles =
                    listOf(
                        "Planet Earth",
                        "Planet Earth II",
                        "Planet Earth III",
                        "Our Planet",
                        "Our Planet II",
                        "Blue Planet",
                        "The Blue Planet",
                        "Blue Planet II",
                        "Frozen Planet",
                        "Frozen Planet II",
                        "Seven Worlds, One Planet",
                        "Earth",
                        "One Day on Earth",
                        "WALL·E",
                        "FernGully: The Last Rainforest",
                        "The Lorax",
                        "Dr. Seuss' The Lorax",
                        "Avatar",
                        "Avatar: The Way of Water",
                        "Princess Mononoke",
                        "Nausicaä of the Valley of the Wind",
                        "An Inconvenient Truth",
                        "Chasing Coral",
                        "My Octopus Teacher",
                        "Penguins",
                        "Bears",
                        "Oceans",
                        "Kiss the Ground",
                        "The Wild Robot",
                    ),
            )

        val FRIDAY_13 =
            HomeTheme(
                R.string.theme_friday13,
                genres = listOf("Horror"),
                tags = listOf("summer camp", "slasher", "jason voorhees"),
                nameWords = listOf("Friday the 13th"),
                titles =
                    listOf(
                        "Friday the 13th",
                        "Friday the 13th Part 2",
                        "Friday the 13th Part III",
                        "Friday the 13th: The Final Chapter",
                        "Jason Lives: Friday the 13th Part VI",
                        "Jason X",
                        "Freddy vs. Jason",
                        "Sleepaway Camp",
                        "The Cabin in the Woods",
                        "Fear Street: 1978",
                        "Scream",
                        "Halloween",
                        "A Nightmare on Elm Street",
                        "The Burning",
                        "X",
                        "Happy Death Day",
                    ),
            )

        val GROUNDHOG_DAY =
            HomeTheme(
                R.string.theme_groundhog,
                tags = listOf("time loop", "groundhog day"),
                nameWords = listOf("Groundhog"),
                titles =
                    listOf(
                        "Groundhog Day",
                        "Palm Springs",
                        "Happy Death Day",
                        "Happy Death Day 2U",
                        "Edge of Tomorrow",
                        "Russian Doll",
                        "Source Code",
                        "Before I Fall",
                        "The Map of Tiny Perfect Things",
                        "Boss Level",
                        "Naked",
                        "Looper",
                        "Doctor Strange",
                        "Triangle",
                        "The Endless",
                        "Run Lola Run",
                    ),
            )

        val MID_AUTUMN =
            HomeTheme(
                R.string.theme_mid_autumn,
                tags = listOf("mid-autumn festival", "mooncake", "chang'e"),
                nameWords = listOf("Moon", "Moonlight"),
                titles =
                    listOf(
                        "Over the Moon",
                        "Kung Fu Panda",
                        "Kung Fu Panda 2",
                        "Kung Fu Panda 3",
                        "Kung Fu Panda 4",
                        "Mulan",
                        "Crouching Tiger, Hidden Dragon",
                        "Hero",
                        "House of Flying Daggers",
                        "Raya and the Last Dragon",
                        "Turning Red",
                        "Abominable",
                        "Spirited Away",
                        "Bao",
                        "Eat Drink Man Woman",
                        "The Farewell",
                        "Big Fish & Begonia",
                        "Everything Everywhere All at Once",
                        "Crazy Rich Asians",
                        "Shang-Chi and the Legend of the Ten Rings",
                        "Kubo and the Two Strings",
                    ),
            )

        val POPCORN_DAY =
            HomeTheme(
                R.string.theme_popcorn,
                genres = listOf("Adventure", "Comedy"),
                tags = listOf("blockbuster"),
                nameWords = listOf(),
                titles =
                    listOf(
                        "Jurassic Park",
                        "Back to the Future",
                        "Star Wars",
                        "Raiders of the Lost Ark",
                        "The Avengers",
                        "Avengers: Endgame",
                        "Top Gun: Maverick",
                        "Spider-Man: Into the Spider-Verse",
                        "Toy Story",
                        "The Lion King",
                        "Jaws",
                        "E.T. the Extra-Terrestrial",
                        "Ghostbusters",
                        "The Princess Bride",
                        "Home Alone",
                        "Finding Nemo",
                        "Shrek",
                        "The Incredibles",
                        "Pirates of the Caribbean: The Curse of the Black Pearl",
                        "The Dark Knight",
                        "Guardians of the Galaxy",
                        "Inside Out",
                        "Up",
                        "Ferris Bueller's Day Off",
                        "The Goonies",
                        "Men in Black",
                        "Independence Day",
                        "Titanic",
                        "Frozen",
                        "Paddington 2",
                    ),
            )

        val VIDEO_GAMES_DAY =
            HomeTheme(
                R.string.theme_video_games,
                tags = listOf("video game", "based on video game", "gamer"),
                nameWords = listOf("Mario", "Sonic", "Minecraft", "Pokemon"),
                titles =
                    listOf(
                        "The Super Mario Bros. Movie",
                        "Super Mario Bros.",
                        "Detective Pikachu",
                        "Pokémon Detective Pikachu",
                        "Sonic the Hedgehog",
                        "Sonic the Hedgehog 2",
                        "Sonic the Hedgehog 3",
                        "Wreck-It Ralph",
                        "Ralph Breaks the Internet",
                        "A Minecraft Movie",
                        "Five Nights at Freddy's",
                        "The Angry Birds Movie",
                        "The Angry Birds Movie 2",
                        "Pixels",
                        "Free Guy",
                        "Ready Player One",
                        "Tron",
                        "Tron: Legacy",
                        "Scott Pilgrim vs. the World",
                        "Jumanji: Welcome to the Jungle",
                        "Jumanji: The Next Level",
                        "Tetris",
                        "Uncharted",
                        "Mortal Kombat",
                        "Tomb Raider",
                        "Lara Croft: Tomb Raider",
                        "Warcraft",
                        "Rampage",
                        "Gran Turismo",
                        "Arcane",
                        "The Last of Us",
                        "Fallout",
                        "Castlevania",
                        "Cyberpunk: Edgerunners",
                        "Sonic Prime",
                        "The Witcher",
                        "Halo",
                        "Resident Evil",
                        "Assassin's Creed",
                        "Prince of Persia: The Sands of Time",
                        "WarGames",
                        "The Wizard",
                    ),
            )

        val CAT_DAY =
            HomeTheme(
                R.string.theme_cat_day,
                tags = listOf("cat"),
                nameWords = listOf("Cat", "Cats", "Kitty", "Kitten"),
                titles =
                    listOf(
                        "The Aristocats",
                        "Puss in Boots",
                        "Puss in Boots: The Last Wish",
                        "Garfield: The Movie",
                        "The Garfield Movie",
                        "Cats & Dogs",
                        "Kiki's Delivery Service",
                        "The Cat Returns",
                        "Flow",
                        "Keanu",
                        "A Street Cat Named Bob",
                        "The Cat in the Hat",
                        "Homeward Bound: The Incredible Journey",
                        "Stuart Little",
                        "Coraline",
                        "Kedi",
                        "Gabby's Dollhouse",
                        "Pete the Cat",
                        "Oliver & Company",
                        "The Secret Life of Pets",
                        "Alice in Wonderland",
                        "My Neighbor Totoro",
                    ),
            )

        val DOG_DAY =
            HomeTheme(
                R.string.theme_dog_day,
                tags = listOf("dog"),
                nameWords = listOf("Dog", "Dogs", "Puppy", "Pup", "Pups"),
                titles =
                    listOf(
                        "Marley & Me",
                        "Hachi: A Dog's Tale",
                        "A Dog's Purpose",
                        "A Dog's Journey",
                        "Bolt",
                        "Lady and the Tramp",
                        "One Hundred and One Dalmatians",
                        "101 Dalmatians",
                        "Beethoven",
                        "Air Bud",
                        "Isle of Dogs",
                        "Up",
                        "Balto",
                        "Turner & Hooch",
                        "Homeward Bound: The Incredible Journey",
                        "The Secret Life of Pets",
                        "Clifford the Big Red Dog",
                        "Lassie Come Home",
                        "Old Yeller",
                        "Togo",
                        "Eight Below",
                        "Best in Show",
                        "Bluey",
                        "Dog",
                        "PAW Patrol: The Movie",
                        "Frankenweenie",
                        "The Call of the Wild",
                        "Scooby-Doo",
                        "Benji",
                        "Strays",
                        "Oliver & Company",
                    ),
            )

        val PIRATE_DAY =
            HomeTheme(
                R.string.theme_pirate,
                tags = listOf("pirate", "treasure hunt", "buried treasure"),
                nameWords = listOf("Pirate", "Pirates", "Treasure"),
                titles =
                    listOf(
                        "Pirates of the Caribbean: The Curse of the Black Pearl",
                        "Pirates of the Caribbean: Dead Man's Chest",
                        "Pirates of the Caribbean: At World's End",
                        "Pirates of the Caribbean: On Stranger Tides",
                        "Pirates of the Caribbean: Dead Men Tell No Tales",
                        "Hook",
                        "The Goonies",
                        "Treasure Planet",
                        "Muppet Treasure Island",
                        "Treasure Island",
                        "Captain Blood",
                        "The Princess Bride",
                        "Peter Pan",
                        "Moana",
                        "One Piece",
                        "Our Flag Means Death",
                        "Black Sails",
                        "The Pirates! Band of Misfits",
                        "Cutthroat Island",
                        "Master and Commander: The Far Side of the World",
                        "The Sea Beast",
                        "Sinbad: Legend of the Seven Seas",
                        "Captain Phillips",
                        "Peter Pan & Wendy",
                    ),
            )

        fun forDate(date: LocalDate): HomeTheme {
            val month = date.monthValue
            val day = date.dayOfMonth
            val lny = lunarNewYear(date.year)
            val diwaliDay = diwali(date.year)
            val thanksgiving = date.withDayOfMonth(1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY))
            val easterDay = easter(date.year)
            val midAutumnDay = midAutumn(date.year)

            fun near(
                day: LocalDate?,
                before: Long,
                after: Long = 0,
            ) = day != null && !date.isBefore(day.minusDays(before)) && !date.isAfter(day.plusDays(after))

            fun onDay(
                m: Int,
                d: Int,
                before: Long = 2,
            ) = near(LocalDate.of(date.year, m, d), before)
            return when {
                isFriday13(date) -> FRIDAY_13
                onDay(10, 21, 1) -> BACK_TO_THE_FUTURE_DAY
                lny != null && !date.isBefore(lny.minusDays(3)) && !date.isAfter(lny.plusDays(3)) -> LUNAR_NEW_YEAR
                month == 10 -> HALLOWEEN
                month == 11 && day <= 2 -> DAY_OF_THE_DEAD
                diwaliDay != null && !date.isBefore(diwaliDay.minusDays(2)) && !date.isAfter(diwaliDay.plusDays(2)) -> DIWALI
                month == 11 && day in 3..5 -> BONFIRE_NIGHT
                month == 11 && !date.isBefore(thanksgiving.minusDays(4)) && !date.isAfter(thanksgiving.plusDays(1)) -> THANKSGIVING
                near(easterDay, 3, 1) -> EASTER
                near(midAutumnDay, 2, 1) -> MID_AUTUMN
                near(nthSunday(date, 5, 2), 3) -> MOTHERS_DAY
                near(nthSunday(date, 6, 3), 3) -> FATHERS_DAY
                onDay(1, 19) -> POPCORN_DAY
                onDay(2, 2, 1) -> GROUNDHOG_DAY
                onDay(2, 27) -> POKEMON_DAY
                onDay(3, 10) -> MARIO_DAY
                onDay(4, 22) -> EARTH_DAY
                onDay(5, 4, 3) -> STAR_WARS_DAY
                onDay(8, 8) -> CAT_DAY
                onDay(8, 26) -> DOG_DAY
                onDay(9, 12) -> VIDEO_GAMES_DAY
                onDay(9, 19) -> PIRATE_DAY
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
