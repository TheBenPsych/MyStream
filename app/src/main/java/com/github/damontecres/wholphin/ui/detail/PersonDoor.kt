package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.door.DoorPoster
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType

/** One door's share of an actor's work: Movies (amber), Shows (blue) or Not on Benflix (grey) */
sealed interface PersonDoor {
    val key: String
    val title: String
    val colour: Color
    val count: Int

    data class Library(
        override val key: String,
        override val title: String,
        override val colour: Color,
        val items: List<BaseItem>,
    ) : PersonDoor {
        override val count: Int get() = items.size
    }

    data class Ask(
        val items: List<DiscoverItem>,
    ) : PersonDoor {
        override val key = "ask"
        override val title = "Not on Benflix"
        override val colour = BenflixColors.NotHere
        override val count: Int get() = items.size
    }
}

/**
 * Approved actor page (polish round, Nov 2026), TV: their work in coloured door columns side by side.
 * Up to 8 posters per column (two rows of four), then "See all". A door with nothing in it is left
 * out, and the others get wider. Moving right goes Movies, Shows, then Not on Benflix.
 */
@Composable
fun PersonDoorColumns(
    doors: List<PersonDoor>,
    focusKey: String?,
    focusRequester: FocusRequester,
    onClickItem: (String, BaseItem) -> Unit,
    onClickAsk: (String, DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = BenflixSpace.tvEdge),
    ) {
        doors.forEach { door ->
            var all by rememberSaveable(door.key) { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                DoorHeading(door, if (door is PersonDoor.Ask) "Ask for it" else door.count.toString())
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = 10.dp
                    val width = (maxWidth - gap * (COLUMNS - 1)) / COLUMNS
                    val shown = if (all) door.count.coerceAtMost(MAX_ALL) else door.count.coerceAtMost(COLUMN_MAX)
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        (0 until shown).chunked(COLUMNS).forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                line.forEach { index ->
                                    val key = "${door.key}-$index"
                                    val mod = if (key == focusKey) Modifier.focusRequester(focusRequester) else Modifier
                                    PersonPoster(door, index, width, showName = false, mod, onClickItem, onClickAsk)
                                }
                            }
                        }
                    }
                }
                if (door.count > COLUMN_MAX && !all) {
                    SeeAllButton("See all ${door.count}") { all = true }
                }
            }
        }
    }
}

/**
 * Approved actor page, phones: one swipe row per door under the picture and name, each with its
 * small coloured label, the same as other phone pages
 */
@Composable
fun PersonDoorRow(
    door: PersonDoor,
    focusKey: String?,
    focusRequester: FocusRequester,
    onClickItem: (String, BaseItem) -> Unit,
    onClickAsk: (String, DiscoverItem) -> Unit,
    posterWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        DoorHeading(door, null, Modifier.padding(horizontal = BenflixSpace.phoneEdge))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = BenflixSpace.phoneEdge),
        ) {
            itemsIndexed(List(door.count.coerceAtMost(MAX_ALL)) { it }, key = { _, i -> "${door.key}-$i" }) { _, index ->
                val key = "${door.key}-$index"
                val mod = if (key == focusKey) Modifier.focusRequester(focusRequester) else Modifier
                PersonPoster(door, index, posterWidth, showName = true, mod, onClickItem, onClickAsk)
            }
        }
    }
}

@Composable
private fun PersonPoster(
    door: PersonDoor,
    index: Int,
    width: Dp,
    showName: Boolean,
    modifier: Modifier,
    onClickItem: (String, BaseItem) -> Unit,
    onClickAsk: (String, DiscoverItem) -> Unit,
) {
    val key = "${door.key}-$index"
    when (door) {
        is PersonDoor.Library -> {
            val item = door.items[index]
            DoorPoster(item = item, width = width, showName = showName, onClick = { onClickItem(key, item) }, modifier = modifier)
        }

        is PersonDoor.Ask -> {
            val item = door.items[index]
            DiscoverItemCard(
                item = item,
                onClick = { onClickAsk(key, item) },
                onLongClick = {},
                width = width,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun DoorHeading(
    door: PersonDoor,
    note: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Spacer(
            Modifier
                .size(12.dp)
                .background(door.colour, RoundedCornerShape(3.dp)),
        )
        Text(
            text = door.title,
            color = BenflixColors.Text,
            fontSize = BenflixType.rowName,
            fontWeight = FontWeight.ExtraBold,
        )
        if (note != null) {
            Text(text = note, color = BenflixColors.Muted, fontSize = BenflixType.small, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SeeAllButton(
    text: String,
    onClick: () -> Unit,
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
    ) {
        Text(
            text = "$text  →",
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

private const val COLUMNS = 4
private const val COLUMN_MAX = 8

/** "See all" stops here, so a huge filmography doesn't slow the page down */
private const val MAX_ALL = 60
