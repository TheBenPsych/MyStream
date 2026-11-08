package com.github.damontecres.wholphin.ui.nav

import android.view.Gravity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.BenflixWatchTime
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.ui.BenflixIntroState
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.components.BenflixWordLogo
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.touch.Dialog
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.LocalClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.model.api.BaseItemKind
import java.time.YearMonth
import javax.inject.Inject

/** Which door a page belongs to, so the top bar can show its name in the door's colour */
fun Destination.door(): BenflixDoor? =
    when (this) {
        is Destination.MoviesDoor -> BenflixDoor.MOVIES
        is Destination.ShowsDoor -> BenflixDoor.SHOWS
        is Destination.Channels -> BenflixDoor.LIVE_TV
        is Destination.Games -> BenflixDoor.GAMES
        is Destination.DoorBrowse -> BenflixDoor.entries.firstOrNull { it.name == door }
        is Destination.DoorRowGrid -> BenflixDoor.entries.firstOrNull { it.name == door }
        else -> null
    }

/**
 * Movie, show and Requests title pages draw their own full-screen picture. The app's shared picture
 * behind every page must stay off there, or it shows a second copy of the picture behind the top bar.
 */
fun Destination.drawsOwnPicture(): Boolean =
    (
        this is Destination.MediaItem &&
            (
                type == BaseItemKind.MOVIE ||
                    type == BaseItemKind.SERIES ||
                    type == BaseItemKind.VIDEO ||
                    type == BaseItemKind.MUSIC_VIDEO
            )
    ) ||
        this is Destination.DiscoveredItem

/** Door pages draw right up to the screen's edges (their picture fills the right side) */
fun Destination.fullBleed(): Boolean =
    this is Destination.MoviesDoor ||
        this is Destination.ShowsDoor ||
        (this is Destination.MediaItem && (type == BaseItemKind.MOVIE || type == BaseItemKind.SERIES))

/**
 * The redesigned TV top bar (approved h4-classic): the logo on the left (just decoration), and inside
 * a door its name in the door's colour. On the right a big clock, search, and your picture with a
 * small arrow, which opens the profile menu. A bigger white dot on the picture means something needs you.
 */
@Composable
fun BenflixTopBar(
    door: BenflixDoor?,
    user: JellyfinUser,
    userImageUrl: String?,
    showClock: Boolean,
    badge: Boolean,
    searchSelected: Boolean,
    onClickSearch: () -> Unit,
    onClickProfile: () -> Unit,
    profileFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    overPicture: Boolean = false,
) {
    val scale = if (big) 1.2f else 1f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = BenflixSpace.tvEdge, end = BenflixSpace.tvEdge, top = 18.dp, bottom = 6.dp)
                .height(TopNavBarHeight * scale)
                .focusGroup()
                .focusProperties {
                    // Coming up from the page lands on your picture
                    onEnter = { profileFocusRequester.tryRequestFocus() }
                },
    ) {
        BenflixLogo(
            height = 26.dp * scale,
            markModifier = Modifier.onGloballyPositioned { BenflixIntroState.barMark = it.boundsInWindow() },
        )
        if (door != null) {
            Text(
                text = door.title,
                color = door.color,
                fontSize = 22.sp * scale,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = if (overPicture) Modifier.softShadow() else Modifier,
        ) {
            if (showClock) BigClock(scale = scale)
            TopBarIconButton(
                icon = BenflixIcon.SEARCH,
                contentDescription = "Search",
                selected = searchSelected,
                onClick = onClickSearch,
                size = 40.dp * scale,
            )
            ProfileButton(
                user = user,
                userImageUrl = userImageUrl,
                badge = badge,
                onClick = onClickProfile,
                pictureSize = 40.dp * scale,
                modifier = Modifier.focusRequester(profileFocusRequester),
            )
        }
    }
}

/**
 * Ben: when the bar sits over a door's picture, a soft dark shadow behind the clock, search and your
 * picture keeps them easy to see on bright pictures. A plain drawing, so it costs nothing on older TVs.
 */
private fun Modifier.softShadow(): Modifier =
    drawBehind {
        val w = size.width + 120.dp.toPx()
        val h = size.height + 56.dp.toPx()
        scale(scaleX = 1f, scaleY = h / w, pivot = center) {
            drawCircle(
                brush =
                    Brush.radialGradient(
                        0f to Color.Black.copy(alpha = .62f),
                        0.55f to Color.Black.copy(alpha = .35f),
                        1f to Color.Transparent,
                        center = center,
                        radius = w / 2f,
                    ),
                radius = w / 2f,
                center = center,
            )
        }
    }

/** The phone header: logo (and the door's name) on the left, search and your picture on the right. No clock. */
@Composable
fun BenflixPhoneHeader(
    door: BenflixDoor?,
    user: JellyfinUser,
    userImageUrl: String?,
    badge: Boolean,
    onClickSearch: () -> Unit,
    onClickProfile: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
    ) {
        if (onBack != null) {
            TopBarIconButton(
                icon = BenflixIcon.BACK,
                contentDescription = "Back",
                selected = false,
                onClick = onBack,
                size = 36.dp,
            )
        }
        BenflixLogo(
            height = 22.dp,
            markModifier = Modifier.onGloballyPositioned { BenflixIntroState.barMark = it.boundsInWindow() },
        )
        if (door != null) {
            Text(
                text = door.title,
                color = door.color,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        TopBarIconButton(
            icon = BenflixIcon.SEARCH,
            contentDescription = "Search",
            selected = false,
            onClick = onClickSearch,
            size = 38.dp,
        )
        ProfileButton(
            user = user,
            userImageUrl = userImageUrl,
            badge = badge,
            onClick = onClickProfile,
            pictureSize = 36.dp,
            showArrow = false,
        )
    }
}

/** "8:42" big, with a small "PM" beside it */
@Composable
private fun BigClock(scale: Float) {
    val timeString by LocalClock.current.timeString
    val parts = timeString.trim().split(' ', ' ', ' ')
    val time = parts.firstOrNull().orEmpty()
    val suffix = parts.drop(1).joinToString(" ")
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = time,
            color = BenflixColors.Soft,
            fontSize = 25.sp * scale,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        if (suffix.isNotBlank()) {
            Text(
                text = suffix,
                color = BenflixColors.Muted,
                fontSize = 13.sp * scale,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
            )
        }
    }
}

@Composable
private fun TopBarIconButton(
    icon: BenflixIcon,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    size: Dp,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = if (selected) BenflixColors.Text else BenflixColors.Muted,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = focusRing(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            BenflixIconImage(
                icon = icon,
                color = if (selected) BenflixColors.Text else BenflixColors.Muted,
                size = size * 0.52f,
                contentDescription = contentDescription,
            )
        }
    }
}

/** Your picture with a small down arrow. Focus = a white ring around both. */
@Composable
private fun ProfileButton(
    user: JellyfinUser,
    userImageUrl: String?,
    badge: Boolean,
    onClick: () -> Unit,
    pictureSize: Dp,
    modifier: Modifier = Modifier,
    showArrow: Boolean = true,
) {
    val kids by KidsMode.active.collectAsState()
    val guest by GuestMode.active.collectAsState()
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = BenflixColors.FocusRow,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 3.dp, end = if (showArrow) 6.dp else 3.dp, top = 3.dp, bottom = 3.dp),
        ) {
            Box {
                UserIconCardImage(
                    id = user.id,
                    name =
                        when {
                            kids -> KidsMode.displayName
                            guest -> "Guest"
                            else -> user.name
                        },
                    imageUrl = if (kids) null else userImageUrl,
                    modifier = Modifier.size(pictureSize - 6.dp),
                )
                if (badge) {
                    // Approved: a bigger white dot (not red, not a number)
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-3).dp)
                            .size(pictureSize * 0.38f)
                            .background(BenflixColors.Tray, CircleShape)
                            .padding(2.5.dp)
                            .background(BenflixColors.Text, CircleShape),
                    )
                }
            }
            if (showArrow) {
                BenflixIconImage(icon = BenflixIcon.DOWN, color = BenflixColors.Muted, size = pictureSize * 0.42f)
            }
        }
    }
}

/** The white outline the redesign uses for focus everywhere */
@Composable
fun focusRing(shape: androidx.compose.ui.graphics.Shape) =
    ClickableSurfaceDefaults.border(
        focusedBorder =
            Border(
                border = BorderStroke(2.5.dp, BenflixColors.Text),
                shape = shape,
            ),
    )

@HiltViewModel
class ProfileMenuViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val watchTime: BenflixWatchTime,
        private val setupNavigationManager: SetupNavigationManager,
    ) : ViewModel() {
        private val _minutes = MutableStateFlow<Long?>(null)

        /** Minutes watched this month, or null while loading */
        val minutes: StateFlow<Long?> = _minutes

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        fun load() {
            val id = serverRepository.currentUser?.id ?: return
            viewModelScope.launchIO {
                val time =
                    try {
                        watchTime.read(id)
                    } catch (ex: Exception) {
                        null
                    }
                _minutes.value = time?.seconds(YearMonth.now())?.div(60)
            }
        }

        fun switchProfile() {
            val server = serverRepository.currentServer ?: return
            setupNavigationManager.navigateTo(SetupDestination.UserList(server))
        }

        /** Signs this profile out of this TV or phone (the password is asked next time) */
        fun signOut() {
            val current = serverRepository.current.value ?: return
            viewModelScope.launchIO {
                serverRepository.removeUser(current.user)
                setupNavigationManager.navigateTo(SetupDestination.UserList(current.server))
            }
        }
    }

/** One line in the profile menu */
data class ProfileMenuItem(
    val icon: BenflixIcon,
    val title: String,
    val onClick: () -> Unit,
    /** A small white pill on the right, like "2 ready" */
    val note: String? = null,
    /** Sign out sits apart at the bottom */
    val apart: Boolean = false,
)

/**
 * The profile menu (approved h3-card + h3-drop): a card under your picture. Left: your big picture,
 * name, this month's watch time and Switch profile. Right: the list. Neutral greys, Home dimmed behind.
 */
@Composable
fun ProfileMenu(
    user: JellyfinUser,
    userImageUrl: String?,
    minutesWatched: Long?,
    items: List<ProfileMenuItem>,
    onSwitchProfile: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val narrow = screenWidth < 600
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val density = androidx.compose.ui.platform.LocalDensity.current
        SideEffect {
            window?.let {
                it.setGravity(if (narrow) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.END)
                it.setDimAmount(0.72f)
                val params = it.attributes
                params.x = with(density) { (if (narrow) 0.dp else 40.dp).roundToPx() }
                params.y = with(density) { (if (narrow) 60.dp else 72.dp).roundToPx() }
                it.attributes = params
            }
        }
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(30)
            firstFocus.tryRequestFocus()
        }
        val shape = RoundedCornerShape(16.dp)
        val card: @Composable (Modifier) -> Unit = { m ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = m.background(BenflixColors.PanelHigh).padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                UserIconCardImage(
                    id = user.id,
                    name = user.name,
                    imageUrl = userImageUrl,
                    modifier = Modifier.size(if (narrow) 64.dp else 86.dp),
                )
                Text(
                    text = user.name.orEmpty(),
                    color = BenflixColors.Text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                minutesWatched?.let {
                    Text(
                        text = formatWatched(it) + "\nwatched this month",
                        color = BenflixColors.Muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp,
                    )
                }
                MenuPill(text = "Switch profile", onClick = onSwitchProfile)
            }
        }
        val list: @Composable (Modifier) -> Unit = { m ->
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = m.padding(10.dp),
            ) {
                items.forEachIndexed { index, item ->
                    if (item.apart) {
                        Box(
                            Modifier
                                .padding(vertical = 6.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(BenflixColors.Line),
                        )
                    }
                    MenuRow(
                        item = item,
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
        }
        val outer =
            Modifier
                .background(BenflixColors.Panel, shape)
                .border(1.dp, BenflixColors.Line, shape)
        if (narrow) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .then(outer),
            ) {
                card(Modifier.fillMaxWidth())
                list(Modifier.fillMaxWidth())
            }
        } else {
            Row(modifier = outer.width(540.dp).height(IntrinsicSize.Min)) {
                card(
                    Modifier
                        .width(190.dp)
                        .fillMaxHeight(),
                )
                list(Modifier.weight(1f))
            }
        }
    }
}

/** "6 h 20 m", "45 m" */
private fun formatWatched(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "$h h $m m" else "$m m"
}

@Composable
private fun MenuPill(
    text: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Line,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.Text,
                focusedContentColor = BenflixColors.Tray,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun MenuRow(
    item: ProfileMenuItem,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = item.onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            BenflixIconImage(
                icon = item.icon,
                color = if (item.apart) BenflixColors.Muted else BenflixColors.Text,
                size = 20.dp,
            )
            Text(
                text = item.title,
                fontSize = 15.sp,
                fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            item.note?.let {
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
