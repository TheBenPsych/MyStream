package com.github.damontecres.wholphin.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.CelebrationNow
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.LocalClock
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * One entry in the [TopNavBar]
 */
data class TopNavTab(
    val title: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    /** The Jellyfin library behind this tab, if it is one */
    val libraryId: UUID? = null,
    /** Browse card picture and how many titles the library has */
    val imageUrl: String? = null,
    val count: Int? = null,
    /** More menu: the short grey line under the name, and its icon */
    val hint: String? = null,
    val glyph: String? = null,
    /** More menu: My Benflix, shown with your profile picture */
    val isProfile: Boolean = false,
    /** Benflix: a small red number, like requests waiting for Ben */
    val badge: Int? = null,
)

/**
 * A streaming-service style navigation bar across the top of the screen: brand name, page tabs, search,
 * settings, the clock, and the current profile
 */
@Composable
fun TopNavBar(
    tabs: List<TopNavTab>,
    user: JellyfinUser,
    userImageUrl: String?,
    showClock: Boolean,
    searchSelected: Boolean,
    onClickSearch: () -> Unit,
    onClickSettings: () -> Unit,
    onClickProfile: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    moreTabs: List<TopNavTab> = emptyList(),
    browseTabs: List<TopNavTab> = emptyList(),
) {
    val moreSelected = moreTabs.any { it.selected }
    val browseSelected = browseTabs.any { it.selected }
    var showBrowse by remember { mutableStateOf(false) }
    val selectedIndex = tabs.indexOfFirst { it.selected }
    val kids by KidsMode.active.collectAsState()
    var showMore by remember { mutableStateOf(false) }
    // Where the Browse and More tabs are, so their menus drop down right under them
    var browseX by remember { mutableFloatStateOf(0f) }
    var moreX by remember { mutableFloatStateOf(0f) }
    var barBottom by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) { onDispose { TopBarInset.heightPx = 0 } }
    // Sliding underline (Look Book pick): a short line in the profile colour sits under the page
    // you're on and slides to whichever tab you move to
    val tabRects = remember { mutableStateMapOf<Int, Rect>() }
    var focusedKey by remember { mutableStateOf<Int?>(null) }
    val selectedKey =
        when {
            browseSelected -> BROWSE_KEY
            moreSelected -> MORE_KEY
            selectedIndex >= 0 -> selectedIndex
            else -> 0
        }
    val lineRect = tabRects[focusedKey ?: selectedKey]
    val lineX = remember { Animatable(0f) }
    val lineW = remember { Animatable(0f) }
    LaunchedEffect(lineRect) {
        val r = lineRect ?: return@LaunchedEffect
        val inset = r.width * 0.2f
        val spec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        if (lineW.value == 0f) {
            lineX.snapTo(r.left + inset)
            lineW.snapTo(r.width - inset * 2)
        } else {
            launch { lineX.animateTo(r.left + inset, spec) }
            lineW.animateTo(r.width - inset * 2, spec)
        }
    }
    val lineY = lineRect?.bottom ?: 0f
    val accent = BenflixRed

    fun tabModifier(key: Int) =
        Modifier
            .onGloballyPositioned { tabRects[key] = it.boundsInParent() }
            .onFocusChanged {
                if (it.isFocused) {
                    focusedKey = key
                } else if (focusedKey == key) {
                    focusedKey = null
                }
            }
    // One slim floating bar: logo, the main tabs and More, then search, settings, the clock and the profile
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .onSizeChanged { TopBarInset.heightPx = it.height }
                .onGloballyPositioned { barBottom = it.positionInWindow().y + it.size.height }
                // Floating bar (Look Book pick): a rounded strip over the artwork. A see-through colour,
                // not blur, so it stays fast on TV sticks
                .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 6.dp)
                .background(FLOATING_BAR, RoundedCornerShape(30.dp))
                .drawBehind {
                    if (lineW.value > 0f) {
                        drawRoundRect(
                            color = accent,
                            // tab positions are measured inside the inner padding below (20dp start, 4dp top)
                            topLeft = Offset(lineX.value + 20.dp.toPx(), lineY + 1.dp.toPx()),
                            size = Size(lineW.value, 3.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    }
                }.padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                .height(TopNavBarHeight)
                .focusGroup()
                .focusProperties {
                    // Entering the bar from the page lands on the current tab
                    onEnter = { focusRequester.tryRequestFocus() }
                },
    ) {
        BenflixLogo(modifier = Modifier.padding(end = 20.dp))
        if (kids) {
            Text(
                text = stringResource(R.string.kids_profile).uppercase(),
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                modifier =
                    Modifier
                        .padding(end = 8.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        tabs.forEachIndexed { index, tab ->
            TopNavTabButton(
                tab = tab,
                modifier =
                    tabModifier(index).ifElse(
                        index == selectedIndex || (selectedIndex < 0 && !moreSelected && !browseSelected && index == 0),
                        Modifier.focusRequester(focusRequester),
                    ),
            )
            // Browse (every library in one list) sits right after Home
            if (index == 0 && browseTabs.isNotEmpty()) {
                TopNavTabButton(
                    tab =
                        TopNavTab(
                            title = browseTitle(browseTabs),
                            selected = browseSelected,
                            onClick = { showBrowse = true },
                        ),
                    modifier =
                        tabModifier(BROWSE_KEY)
                            .onGloballyPositioned { browseX = it.positionInWindow().x }
                            .ifElse(browseSelected, Modifier.focusRequester(focusRequester)),
                )
            }
        }
        if (moreTabs.isNotEmpty()) {
            TopNavTabButton(
                tab =
                    TopNavTab(
                        title = stringResource(R.string.more),
                        selected = moreSelected,
                        onClick = { showMore = true },
                    ),
                modifier =
                    tabModifier(MORE_KEY)
                        .onGloballyPositioned { moreX = it.positionInWindow().x }
                        .ifElse(moreSelected, Modifier.focusRequester(focusRequester)),
            )
        }
        Spacer(Modifier.weight(1f))
        TopNavIconButton(
            icon = Icons.Default.Search,
            contentDescription = stringResource(R.string.search),
            selected = searchSelected,
            onClick = onClickSearch,
        )
        if (!kids) {
            TopNavIconButton(
                icon = Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings),
                selected = false,
                onClick = onClickSettings,
            )
        }
        if (showClock) {
            val timeString by LocalClock.current.timeString
            Text(
                text = timeString,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        IconButton(
            onClick = onClickProfile,
            colors =
                IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                ),
            modifier = Modifier.size(40.dp),
        ) {
            UserIconCardImage(
                id = user.id,
                name =
                    if (kids) {
                        KidsMode.displayName
                    } else if (GuestMode.isActive) {
                        "Guest"
                    } else {
                        user.name
                    },
                imageUrl = if (kids) null else userImageUrl,
                modifier = Modifier.size(32.dp),
            )
        }
    }
    if (showBrowse) {
        BrowseMenu(
            tabs = browseTabs,
            anchorX = browseX,
            top = barBottom,
            onDismissRequest = { showBrowse = false },
        )
    }
    if (showMore) {
        MoreMenu(
            tabs = moreTabs,
            userId = user.id,
            userName =
                if (kids) {
                    KidsMode.displayName
                } else if (GuestMode.isActive) {
                    "Guest"
                } else {
                    user.name.orEmpty()
                },
            userImageUrl = if (kids) null else userImageUrl,
            anchorX = moreX,
            top = barBottom,
            onDismissRequest = { showMore = false },
        )
    }
}

/** "Browse ▾", or the library you're in, like "Movies ▾" */
@Composable
private fun browseTitle(browseTabs: List<TopNavTab>): String =
    (browseTabs.firstOrNull { it.selected }?.title ?: stringResource(R.string.browse)) + "  ▾"

@Composable
private fun TopNavTabButton(
    tab: TopNavTab,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Button(
        onClick = tab.onClick,
        interactionSource = interactionSource,
        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        colors =
            ButtonDefaults.colors(
                containerColor = Color.Transparent,
                // The page you are on is white and bold; the sliding line shows where you are
                contentColor = if (tab.selected) Color.White else Color(0xFFB3B3B8),
                focusedContainerColor = Color.White.copy(alpha = 0.10f),
                focusedContentColor = Color.White,
            ),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        modifier = modifier.height(36.dp),
    ) {
        Text(
            text = tab.title,
            fontSize = 15.sp,
            fontWeight = if (tab.selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
        tab.badge?.takeIf { it > 0 }?.let { count ->
            Text(
                text = if (count > 9) "9+" else count.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier =
                    Modifier
                        .padding(start = 6.dp)
                        .background(Color(0xFFE5484D), RoundedCornerShape(9.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun TopNavIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        colors =
            IconButtonDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = if (selected) Color.White else Color(0xFFB3B3B8),
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Builds the tab name for a nav drawer item, using "My List" for favorites
 */
@Composable
fun topNavTitle(item: NavDrawerItem): String =
    if (item is NavDrawerItem.Favorites) {
        stringResource(R.string.my_list)
    } else {
        item.name(LocalContext.current)
    }

/** The floating bar: Benflix graphite, see-through so the artwork shows a little */
private val FLOATING_BAR = Color(0xE018181C)
private const val BROWSE_KEY = 1000
private const val MORE_KEY = 1001

/** Height of the top line of the bar (logo and buttons) */
val TopNavBarHeight = 44.dp

/** The Benflix logo. On holidays and birthdays the whole word wears that day's look (see HolidayLogo.kt) */
@Composable
fun BenflixLogo(modifier: Modifier = Modifier) {
    val celebration by CelebrationNow.current.collectAsState()
    HolidayAwareLogo(celebration = celebration, modifier = modifier)
}
