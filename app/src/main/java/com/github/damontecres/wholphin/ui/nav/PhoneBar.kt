package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.components.BenflixPanelDialog
import com.github.damontecres.wholphin.ui.components.GlowfinIcon
import com.github.damontecres.wholphin.ui.components.GlowfinIconImage
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorSection
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin

/** One tab along the phone's bottom bar */
data class PhoneTab(
    val key: String,
    val label: String,
    val icon: GlowfinIcon,
    val color: Color,
    val door: BenflixDoor? = null,
)

const val TAB_HOME = "home"
const val TAB_ANIME = "anime"
const val TAB_MORE = "more"

/**
 * Glowfin (Ben, 2026-11-18): Home, Movies, Shows, Anime and More along the bottom of every phone page.
 * Without Anime (Kids, easy mode) the next door takes its place. Everything else (Search, Live TV, Music,
 * Games, Books, Requests, your profile, Settings) is under More.
 */
fun phoneTabs(
    doors: List<BenflixDoor>,
    anime: Boolean = true,
): List<PhoneTab> {
    val wanted = listOf(BenflixDoor.MOVIES, BenflixDoor.SHOWS, BenflixDoor.MUSIC, BenflixDoor.GAMES)
    val shown = wanted.filter { it in doors }.take(if (anime) 2 else 3)
    return buildList {
        add(PhoneTab(TAB_HOME, "Home", GlowfinIcon.HOME, BenflixColors.Text))
        shown.forEach { add(PhoneTab(it.name, it.title, it.glowIcon(), BenflixColors.Text, it)) }
        if (anime) add(PhoneTab(TAB_ANIME, "Anime", GlowfinIcon.ANIME, BenflixColors.Text, BenflixDoor.ANIME))
        add(PhoneTab(TAB_MORE, "More", GlowfinIcon.MORE, BenflixColors.Text))
    }
}

/** Which tab the page belongs to */
fun phoneTabFor(
    destination: Destination,
    tabs: List<PhoneTab>,
): String {
    if (destination is Destination.Home) return TAB_HOME
    val door = destination.door()
    return tabs.firstOrNull { it.door != null && it.door == door }?.key ?: TAB_MORE
}

@Composable
fun PhoneBottomBar(
    tabs: List<PhoneTab>,
    selected: String,
    onTab: (PhoneTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Glowfin.Deep)
                .drawBehind {
                    drawLine(
                        Glowfin.Line,
                        androidx.compose.ui.geometry
                            .Offset(0f, 0f),
                        androidx.compose.ui.geometry
                            .Offset(size.width, 0f),
                        1.dp.toPx(),
                    )
                }.navigationBarsPadding()
                .padding(top = 4.dp, bottom = 4.dp),
    ) {
        tabs.forEach { tab ->
            val on = tab.key == selected
            Surface(
                onClick = { onTab(tab) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = BenflixColors.FocusRow,
                        pressedContainerColor = BenflixColors.FocusRow,
                    ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.weight(1f).height(58.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .width(56.dp)
                                .height(30.dp)
                                .then(
                                    if (on) Modifier.background(Glowfin.GlowAcross, RoundedCornerShape(50)) else Modifier,
                                ),
                    ) {
                        // Glowfin: the tab you're on sits on the glow
                        GlowfinIconImage(
                            tab.icon,
                            color = if (on) Color.White else Glowfin.Dim,
                            size = 24.dp,
                            contentDescription = null,
                        )
                    }
                    Text(
                        text = tab.label,
                        color = if (on) BenflixColors.Text else Glowfin.Dim,
                        fontSize = 11.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** One line in the phone's More sheet or places sheet */
@Composable
private fun SheetRow(
    icon: BenflixIcon?,
    label: String,
    color: Color,
    note: String?,
    onClick: () -> Unit,
    muted: Boolean = false,
    glowIcon: GlowfinIcon? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
                pressedContainerColor = BenflixColors.FocusRow,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            if (glowIcon != null) {
                GlowfinIconImage(glowIcon, color = color, size = 22.dp)
            } else if (icon != null) {
                BenflixIconImage(icon, color = color, size = 22.dp)
            }
            Text(
                text = label,
                color = if (muted) BenflixColors.Soft else BenflixColors.Text,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            note?.let {
                Text(
                    text = it,
                    color = BenflixColors.Tray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .background(BenflixColors.Text, RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** The phone's More sheet: the doors that aren't tabs, Search, Requests, My List and your profile */
@Composable
fun PhoneMoreSheet(
    doors: List<BenflixDoor>,
    showSearch: Boolean,
    showRequests: Boolean,
    requestsNote: String?,
    /** Glowfin (ui-17): Kids and Guest have no My List here */
    showMyList: Boolean = true,
    user: JellyfinUser,
    userImageUrl: String?,
    userName: String,
    minutesWatched: Long?,
    profileItems: List<ProfileMenuItem>?,
    onDoor: (BenflixDoor) -> Unit,
    onSearch: () -> Unit,
    /** Glowfin: Anime has its own spot (null = hidden, like Kids' easy mode) */
    onAnime: (() -> Unit)? = null,
    onRequests: () -> Unit,
    onMyList: () -> Unit,
    onSwitchProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    BenflixPanelDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            if (showSearch) SheetRow(null, "Search", BenflixColors.Soft, null, onSearch, glowIcon = GlowfinIcon.SEARCH)
            if (onAnime != null) SheetRow(null, "Anime", BenflixColors.Soft, null, onAnime, glowIcon = GlowfinIcon.ANIME)
            doors.forEach { door ->
                SheetRow(null, door.title, BenflixColors.Soft, null, { onDoor(door) }, glowIcon = door.glowIcon())
            }
            if (showRequests) {
                SheetRow(null, "Requests", BenflixColors.Soft, requestsNote, onRequests, glowIcon = GlowfinIcon.REQUESTS)
            }
            if (showMyList) SheetRow(BenflixIcon.HEART, "My List", BenflixColors.Soft, null, onMyList)
            Box(
                Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BenflixColors.Line),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                UserIconCardImage(
                    id = user.id,
                    name = userName,
                    imageUrl = userImageUrl,
                    modifier = Modifier.size(44.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = userName,
                        color = BenflixColors.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    minutesWatched?.let {
                        Text(watchedWords(it), color = BenflixColors.Muted, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
            SheetRow(BenflixIcon.SWITCH, "Switch profile", BenflixColors.Soft, null, onSwitchProfile)
            profileItems.orEmpty().forEach { item ->
                SheetRow(
                    item.icon,
                    item.title,
                    if (item.apart) BenflixColors.Muted else BenflixColors.Soft,
                    item.note,
                    item.onClick,
                    item.apart,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Approved rx-phonepanel: a door's places, sliding up from the bottom (tap the door's tab again, or its name) */
@Composable
fun PhonePlacesSheet(
    door: BenflixDoor,
    places: List<DoorSection>,
    onPlace: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BenflixPanelDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = door.title,
                color = door.color,
                fontSize = BenflixType.pageTitle,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
            )
            places.forEach { place ->
                SheetRow(
                    icon = place.icon,
                    label = if (place.key == DOOR_HOME) "${door.title} home" else place.label,
                    color = BenflixColors.Soft,
                    note = null,
                    onClick = { onPlace(place.key) },
                )
            }
        }
    }
}
