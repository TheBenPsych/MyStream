package com.github.damontecres.wholphin.ui.nav

import android.view.Gravity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.BenflixWatchTime
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
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
        is Destination.AnimeDoor -> BenflixDoor.ANIME
        is Destination.Channels -> BenflixDoor.LIVE_TV
        is Destination.Games -> BenflixDoor.GAMES
        is Destination.MusicDoor -> BenflixDoor.MUSIC
        is Destination.MusicList -> BenflixDoor.MUSIC
        is Destination.MusicUpNext -> BenflixDoor.MUSIC
        is Destination.BooksDoor -> BenflixDoor.BOOKS
        is Destination.BookTitle -> BenflixDoor.BOOKS
        is Destination.AudiobookPlayer -> BenflixDoor.BOOKS
        is Destination.BooksKids -> BenflixDoor.BOOKS
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

/**
 * Door pages draw right up to the screen's edges (their picture fills the right side). Redesign 2: every
 * door's side menu sits right at the left edge, so Live TV, Games and Books do too (their own margins).
 */
fun Destination.fullBleed(): Boolean =
    this is Destination.MoviesDoor ||
        this is Destination.ShowsDoor ||
        this is Destination.Channels ||
        this is Destination.Games ||
        this is Destination.BooksDoor ||
        underTopBar() ||
        (this is Destination.MediaItem && (type == BaseItemKind.MOVIE || type == BaseItemKind.SERIES)) ||
        // A show opened from one of its episodes is the same title page, so it gets the same edges
        this is Destination.SeriesOverview

/**
 * Ben: when the bar sits over a door's picture, a soft dark shadow behind the clock, search and your
 * picture keeps them easy to see on bright pictures. A plain drawing, so it costs nothing on older TVs.
 */
internal fun Modifier.softShadow(): Modifier =
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

/** "8:42" big, with a small "PM" beside it */
@Composable
internal fun BigClock(scale: Float) {
    val timeString by LocalClock.current.timeString
    val parts = timeString.trim().split(' ', ' ', ' ')
    val time = parts.firstOrNull().orEmpty()
    val suffix = parts.drop(1).joinToString(" ")
    // Ben 2026-11-18: no dark fade at the top of picture pages any more, so the clock gets a soft shadow instead
    val shadowed =
        LocalTextStyle.current.copy(
            shadow =
                Shadow(
                    color = Color.Black.copy(alpha = .6f),
                    offset = Offset(0f, 2f),
                    blurRadius = 10f,
                ),
        )
    // Glowfin new look "Clock with a soft backing" (Ben 2026-11-22): a faint see-through pill behind the clock so it
    // reads on bright pictures. A flat colour, no blur, so it costs a slow TV nothing.
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier =
            Modifier
                .background(
                    com.github.damontecres.wholphin.ui.theme.Glowfin.Ink
                        .copy(alpha = .35f),
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(50),
                ).padding(horizontal = 10.dp * scale, vertical = 1.dp),
    ) {
        Text(
            text = time,
            style = shadowed,
            color = BenflixColors.Soft,
            fontSize = 25.sp * scale,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (suffix.isNotBlank()) {
            Text(
                text = suffix,
                style = shadowed,
                color = BenflixColors.Muted,
                fontSize = 13.sp * scale,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
            )
        }
    }
}

/** The white outline the redesign uses for focus everywhere */
@Composable
fun focusRing(shape: androidx.compose.ui.graphics.Shape) =
    ClickableSurfaceDefaults.border(
        focusedBorder =
            Border(
                border = BorderStroke(2.5.dp, BenflixColors.Outline),
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
        @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
        private val requestReady: com.github.damontecres.wholphin.ui.main.RequestReadyService,
    ) : ViewModel() {
        init {
            com.github.damontecres.wholphin.ui.components.UpdateNotice
                .loadDot(context)
        }

        /**
         * Benflix: "Mark all as seen" clears the white dot on your picture: arrived requests, waiting
         * requests, the Server check and the update all count as seen. Only something new brings it back.
         */
        fun markAllSeen() {
            RequestsWaiting.markSeen()
            com.github.damontecres.wholphin.ui.components.ServerAlertNotice
                .clear()
            com.github.damontecres.wholphin.ui.components.UpdateNotice
                .hideDot(context)
            viewModelScope.launchIO {
                requestReady.refresh()
                requestReady.markAllSeen()
            }
        }

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
    /** The Me page's short line under the name, like "Picture, sound, subtitles" */
    val line: String? = null,
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
                    overflow = TextOverflow.Ellipsis,
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
                focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            item.note?.let {
                Text(
                    text = it,
                    color = BenflixColors.Tray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .background(BenflixColors.Text, RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}
