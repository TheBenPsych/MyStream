package com.github.damontecres.wholphin.ui.fit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.AskKind
import com.github.damontecres.wholphin.services.ParentsGuideLine
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.detail.AskForSimilarTab
import com.github.damontecres.wholphin.ui.detail.title.StripPart
import com.github.damontecres.wholphin.ui.detail.title.TitleButton
import com.github.damontecres.wholphin.ui.detail.title.TitleFacts
import com.github.damontecres.wholphin.ui.detail.title.TitleOverview
import com.github.damontecres.wholphin.ui.detail.title.TitlePage
import com.github.damontecres.wholphin.ui.detail.title.TitleParentsGuide
import com.github.damontecres.wholphin.ui.detail.title.TitlePicture
import com.github.damontecres.wholphin.ui.detail.title.TitleTab
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NamedBack
import com.github.damontecres.wholphin.ui.nav.WhereAmILine
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.touch.PhoneFit
import io.mockk.mockk

/** Long, awkward names that have cut text before */
object FitWords {
    const val LONG_ANIME = "Easygoing Territory Defense by the Optimistic Lord"
    const val JAPANESE = "辺境領主の楽観的な領地防衛～生産系魔術で名もなき村を最強の城塞都市に～"
    const val VERY_LONG = "The Lord of the Rings: The Fellowship of the Ring (Extended Edition Director's Cut)"
    const val PREVIOUS = "The Dreaming Boy Is a Realist"

    val asks =
        listOf(
            "Insanity: The Lost Child",
            "Revenger",
            "Heidi, Girl of the Alps",
            "Desert Punk",
            "Major",
            "Hero Tales",
            "Magical Academy of the Very Long Name",
            "Zoids",
        ).mapIndexed { i, name ->
            DiscoverItem(
                id = 1000 + i,
                type = SeerrItemType.TV,
                title = name,
                subtitle = null,
                overview = null,
                availability = SeerrAvailability.UNKNOWN,
                releaseDate = null,
                posterUrl = null,
                backDropUrl = null,
                logoUrl = null,
                jellyfinItemId = null,
            )
        }

    val story =
        "Hiro, the third son of a minor noble, is sent to rule the poorest village at the very edge of the " +
            "kingdom. Armed with nothing but a production magic everyone laughs at, he slowly turns the muddy " +
            "village into a fortress city, one wall, one well and one stubborn neighbour at a time. " +
            "Along the way he gathers a band of misfits who would rather nap than fight."
}

/** One page to draw: its name, and the page itself */
class FitScene(
    val name: String,
    val content: @Composable () -> Unit,
    /** Something to focus after drawing (by the words on it), like the first poster of a row */
    val focusText: String? = null,
)

/** The real app frame around a TV page: the tucked rail on the left and the page header on top */
@Composable
fun FitFrame(
    tv: Boolean,
    page: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalImageUrlService provides mockk(relaxed = true)) {
        WholphinTheme {
            PhoneFit {
                Box(Modifier.fillMaxSize().background(BenflixColors.Tray)) {
                    if (tv) {
                        Box(Modifier.fillMaxSize().padding(start = 66.dp)) {
                            page()
                            FitHeader()
                        }
                        Box(Modifier.width(66.dp).fillMaxSize().background(Color(0xFF2A1A1A)))
                    } else {
                        page()
                    }
                }
            }
        }
    }
}

/** The TV page header the way a deep page shows it: named Back, where-am-I line, clock */
@Composable
private fun FitHeader() {
    val stack =
        listOf(
            Destination.Home(),
            Destination.DoorRowGrid("shows", "a", "Anime"),
            Destination.DoorRowGrid("shows", "b", FitWords.PREVIOUS),
            Destination.DoorRowGrid("shows", "c", FitWords.LONG_ANIME),
        )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 48.dp, top = 18.dp, bottom = 6.dp).height(44.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            NamedBack(name = FitWords.PREVIOUS, onClick = {}, height = 38.dp)
            WhereAmILine(stack = stack, onPick = {}, fontSize = 14.sp, padded = false, modifier = Modifier.weight(1f, fill = false))
        }
        Text("9:19 PM", color = BenflixColors.Text, fontSize = 30.sp)
    }
}

object FitScenes {
    private fun buttons(play: String = "Play S1 · E1") =
        listOf(
            TitleButton(play, BenflixIcon.PLAY, {}),
            TitleButton("Episodes", BenflixIcon.LIST, {}, opensTab = "Episodes"),
            TitleButton("Trailer", BenflixIcon.TRAILER, {}),
            TitleButton("My List", BenflixIcon.PLUS, {}),
            TitleButton("More", BenflixIcon.MORE, {}),
        )

    private fun animeTabs(): List<TitleTab> =
        listOf(
            TitleTab("Episodes") { scope -> TitleFacts(scope, listOf("Episode list goes here"), BenflixColors.Shows) },
            TitleTab("Overview") { scope ->
                TitleOverview(
                    scope = scope,
                    title = FitWords.LONG_ANIME,
                    story = FitWords.story,
                    strip = listOf(StripPart("IMDb", "7.4"), StripPart("Japanese"), StripPart("Studio", "Typhoon Graphics")),
                    details =
                        listOf(
                            "Studio" to "Typhoon Graphics, Lay-duce and a very long studio name",
                            "Released" to "October 4, 2026",
                            "Genres" to "Fantasy, Isekai, Comedy, Slice of Life",
                        ),
                )
            },
            TitleTab("Characters and voices") { scope -> TitleFacts(scope, listOf("Characters go here"), BenflixColors.Shows) },
            TitleTab("More like this") { scope -> TitleFacts(scope, listOf("Posters go here"), BenflixColors.Shows) },
            TitleTab("Ask for similar") { scope ->
                AskForSimilarTab(
                    kind = AskKind.ANIME,
                    best = FitWords.asks,
                    more = FitWords.asks.reversed().map { it.copy(id = it.id + 100) },
                    scope = scope,
                    onClick = {},
                )
            },
            TitleTab("Cast") { scope -> TitleFacts(scope, listOf("Cast goes here"), BenflixColors.Shows) },
            TitleTab("Did you know") { scope ->
                TitleFacts(
                    scope,
                    listOf(
                        "The author wrote the first chapters on his phone during his train ride to work.",
                        "The village's well was drawn from a real well in Gifu.",
                        "Every castle wall in the show was modelled in 3D first so the camera could fly around it.",
                    ),
                    BenflixColors.Shows,
                )
            },
            TitleTab("Parents' guide") { scope ->
                TitleParentsGuide(
                    scope,
                    listOf(
                        ParentsGuideLine("Violence", "Some", 2),
                        ParentsGuideLine("Language", "A little", 1),
                        ParentsGuideLine("Scary scenes", "A lot", 3),
                    ),
                )
            },
        )

    private fun animePage(openTab: String) =
        @Composable {
            TitlePage(
                title = FitWords.LONG_ANIME,
                originalTitle = FitWords.JAPANESE,
                accent = BenflixColors.Shows,
                picture = TitlePicture.Wide(null),
                info = "2026 · 1 season · Fantasy · SUB · Ended",
                rating = "TV-14",
                buttons = buttons(),
                tabs = animeTabs(),
                openTab = openTab,
            )
        }

    fun all(): List<FitScene> =
        buildList {
            // Ben's photo: the anime show page on the Ask for similar tab, with the first poster focused
            add(FitScene("anime-ask-focused", animePage("Ask for similar"), focusText = "Revenger"))
            add(FitScene("anime-ask", animePage("Ask for similar")))
            add(FitScene("anime-ask-second-row", animePage("Ask for similar"), focusText = "Zoids"))
            listOf("Episodes", "Overview", "Did you know", "Parents' guide").forEach { tab ->
                add(FitScene("anime-${tab.lowercase().replace(Regex("[^a-z]+"), "-").trim('-')}", animePage(tab)))
            }
            add(
                FitScene("movie-very-long-title", {
                    TitlePage(
                        title = FitWords.VERY_LONG,
                        accent = BenflixColors.Movies,
                        picture = TitlePicture.Wide(null),
                        info = "2001 · 3h 48m · Adventure · Fantasy · Action · Drama",
                        rating = "PG-13",
                        buttons = buttons("Resume 2h 14m"),
                        tabs = animeTabs().filter { it.name != "Episodes" && it.name != "Characters and voices" },
                        statusLine = "1h 34m left · Watched on Sunday",
                    )
                }),
            )
            add(
                FitScene("movie-long-title-and-original", {
                    TitlePage(
                        title = FitWords.VERY_LONG,
                        originalTitle = "Le Seigneur des anneaux : La Communauté de l'anneau (version longue)",
                        accent = BenflixColors.Movies,
                        picture = TitlePicture.Wide(null),
                        info = "2001 · 3h 48m · Adventure · Fantasy",
                        rating = "PG-13",
                        buttons = buttons("Play"),
                        tabs = animeTabs().take(2),
                        openTab = "Overview",
                    )
                }),
            )
            add(
                FitScene("easy-big", {
                    TitlePage(
                        title = FitWords.LONG_ANIME,
                        originalTitle = FitWords.JAPANESE,
                        accent = BenflixColors.Shows,
                        picture = TitlePicture.Wide(null),
                        info = "2026 · 1 season · Fantasy",
                        rating = "TV-14",
                        buttons = buttons(),
                        tabs = animeTabs(),
                        openTab = "Ask for similar",
                        big = true,
                    )
                }),
            )
        }
}
