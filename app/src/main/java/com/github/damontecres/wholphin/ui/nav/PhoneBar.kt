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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorSection
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixType

/** One tab along the phone's bottom bar */
data class PhoneTab(
    val key: String,
    val label: String,
    val icon: BenflixIcon,
    val color: Color,
    val door: BenflixDoor? = null,
)

const val TAB_HOME = "home"
const val TAB_MORE = "more"

/**
 * Approved pc-phone / r2-q-phonebar: Home, Movies, Shows, Music and More along the bottom of every phone page
 * (Kids: Home, Movies, Shows, Games). The other doors, Search, Requests and your profile are under More.
 */
fun phoneTabs(doors: List<BenflixDoor>): List<PhoneTab> {
    val wanted = listOf(BenflixDoor.MOVIES, BenflixDoor.SHOWS, BenflixDoor.MUSIC, BenflixDoor.GAMES)
    val shown = wanted.filter { it in doors }.take(3)
    return buildList {
        add(PhoneTab(TAB_HOME, "Home", BenflixIcon.HOME, BenflixColors.Text))
        shown.forEach { add(PhoneTab(it.name, it.title, it.railIcon(), it.color, it)) }
        add(PhoneTab(TAB_MORE, "More", BenflixIcon.MORE, BenflixColors.Text))
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
    moreDot: Boolean,
    onTab: (PhoneTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(BenflixColors.Tray)
                .navigationBarsPadding()
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
                                .background(
                                    if (on) tab.color.copy(alpha = .2f) else Color.Transparent,
                                    RoundedCornerShape(50),
                                ),
                    ) {
                        BenflixIconImage(
                            tab.icon,
                            color = if (on) tab.color else BenflixColors.Soft,
                            size = 24.dp,
                            contentDescription = null,
                        )
                        // Approved ix-badges: on phones the white dot for new notices sits on More
                        if (tab.key == TAB_MORE && moreDot) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-12).dp, y = 1.dp)
                                    .size(10.dp)
                                    .background(BenflixColors.Text, CircleShape),
                            )
                        }
                    }
                    Text(
                        text = tab.label,
                        color = if (on) BenflixColors.Text else BenflixColors.Muted,
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
    icon: BenflixIcon,
    label: String,
    color: Color,
    note: String?,
    onClick: () -> Unit,
    muted: Boolean = false,
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
            BenflixIconImage(icon, color = color, size = 22.dp)
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
    user: JellyfinUser,
    userImageUrl: String?,
    userName: String,
    minutesWatched: Long?,
    profileItems: List<ProfileMenuItem>?,
    onDoor: (BenflixDoor) -> Unit,
    onSearch: () -> Unit,
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
            doors.forEach { door ->
                SheetRow(door.railIcon(), door.title, door.color, null, { onDoor(door) })
            }
            if (showSearch) SheetRow(BenflixIcon.SEARCH, "Search", BenflixColors.Soft, null, onSearch)
            if (showRequests) SheetRow(BenflixIcon.REQUESTS, "Requests", BenflixColors.LiveTv, requestsNote, onRequests)
            SheetRow(BenflixIcon.HEART, "My List", BenflixColors.Soft, null, onMyList)
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
