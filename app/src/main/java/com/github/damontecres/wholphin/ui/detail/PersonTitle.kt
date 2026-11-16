package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.detail.title.TitleButton
import com.github.damontecres.wholphin.ui.detail.title.TitleGrid
import com.github.damontecres.wholphin.ui.detail.title.TitlePage
import com.github.damontecres.wholphin.ui.detail.title.TitlePicture
import com.github.damontecres.wholphin.ui.detail.title.TitleTab
import com.github.damontecres.wholphin.ui.detail.title.TitleTabScope
import com.github.damontecres.wholphin.ui.door.DoorPoster
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Benflix Redesign 2: an actor's page on the shared title page frame. Their picture stands where the
 * cover goes, then tabs: Movies, Shows and Not on Benflix yet (each only when it has something).
 * Buttons: Favourite and About them (the whole life story in the side panel).
 */
@Composable
fun PersonTitlePage(
    name: String,
    overview: String?,
    imageUrl: String?,
    birthdate: LocalDate?,
    deathdate: LocalDate?,
    birthPlace: String?,
    favorite: Boolean,
    doors: List<PersonDoor>,
    onClickItem: (BaseItem) -> Unit,
    onClickAsk: (DiscoverItem) -> Unit,
    overviewOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreToAsk: (() -> Unit)?,
    askForRest: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val info =
        remember(birthdate, deathdate, birthPlace) {
            val f = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
            listOfNotNull(
                birthdate?.let { "Born ${it.format(f)}" },
                deathdate?.let { "Died ${it.format(f)}" },
                birthPlace,
            ).joinToString(" · ")
        }
    val buttons =
        listOf(
            TitleButton(if (favorite) "Favourite" else "Add to favourites", BenflixIcon.HEART, favoriteOnClick),
            TitleButton(
                "About them",
                BenflixIcon.INFO,
                overviewOnClick,
                off = if (overview.isNullOrBlank()) "No life story for them yet" else null,
            ),
        )
    val tabs =
        doors.map { door ->
            val tabName = if (door is PersonDoor.Ask) "Not on Benflix yet" else door.title
            TitleTab(tabName) { scope ->
                Column(Modifier.fillMaxWidth()) {
                    PersonDoorGrid(scope, door, onClickItem, onClickAsk)
                    if (door is PersonDoor.Ask) {
                        if (moreToAsk != null) {
                            PillButton("More to ask for", moreToAsk, Modifier.padding(start = scope.side, top = 12.dp))
                        }
                        askForRest(Modifier.padding(start = scope.side, end = scope.side, top = 12.dp))
                    }
                }
            }
        }
    TitlePage(
        title = name,
        accent = BenflixColors.Movies,
        picture = TitlePicture.Cover(imageUrl, 2f / 3f, BenflixColors.Movies),
        info = info,
        buttons = buttons,
        tabs = tabs,
        modifier = modifier,
    )
}

/** One door's posters: 8 across on TV (up to 4 lines, then See all), 3 across on phones */
@Composable
private fun PersonDoorGrid(
    scope: TitleTabScope,
    door: PersonDoor,
    onClickItem: (BaseItem) -> Unit,
    onClickAsk: (DiscoverItem) -> Unit,
) {
    var all by rememberSaveable(door.key) { mutableStateOf(false) }
    val across = if (scope.wide) 8 else 3
    val first = across * if (scope.wide) 2 else 4
    val shown = if (all) door.count.coerceAtMost(PERSON_MAX) else door.count.coerceAtMost(first)
    TitleGrid(scope, shown, across = across) { index, width ->
        when (door) {
            is PersonDoor.Library -> {
                val item = door.items[index]
                DoorPoster(item = item, width = width, showName = !scope.wide, onClick = { onClickItem(item) })
            }

            is PersonDoor.Ask -> {
                val item = door.items[index]
                DiscoverItemCard(item = item, onClick = { onClickAsk(item) }, onLongClick = {}, width = width)
            }
        }
    }
    if (!all && door.count > shown) {
        PillButton("See all ${door.count}", { all = true }, Modifier.padding(start = scope.side, top = 12.dp))
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Soft,
                focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(
            text = "$text  →",
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** "See all" stops here, so a huge filmography doesn't slow the page down */
private const val PERSON_MAX = 60
