package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.LogoEffect
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.LocalClock

/**
 * One entry in the [TopNavBar]
 */
data class TopNavTab(
    val title: String,
    val selected: Boolean,
    val onClick: () -> Unit,
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
    DisposableEffect(Unit) { onDispose { TopBarInset.heightPx = 0 } }
    // One slim line: logo, the main tabs and More, then search, settings, the clock and the profile
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .onSizeChanged { TopBarInset.heightPx = it.height }
                // A dark shade behind the bar so the logo, tabs and icons stay easy to read over bright artwork
                .background(TopNavBarShade)
                .padding(start = 40.dp, end = 40.dp, top = 10.dp, bottom = 12.dp)
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
                    Modifier.ifElse(
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
                    modifier = Modifier.ifElse(browseSelected, Modifier.focusRequester(focusRequester)),
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
                modifier = Modifier.ifElse(moreSelected, Modifier.focusRequester(focusRequester)),
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
                name = if (kids) "Kids" else user.name,
                imageUrl = if (kids) null else userImageUrl,
                modifier = Modifier.size(32.dp),
            )
        }
    }
    if (showBrowse) {
        DialogPopup(
            showDialog = true,
            title = stringResource(R.string.browse),
            dialogItems =
                browseTabs.map { tab ->
                    DialogItem(
                        headlineContent = { Text(text = tab.title) },
                        onClick = tab.onClick,
                        selected = tab.selected,
                    )
                },
            onDismissRequest = { showBrowse = false },
            waitToLoad = false,
        )
    }
    if (showMore) {
        DialogPopup(
            showDialog = true,
            title = stringResource(R.string.more),
            dialogItems =
                moreTabs.map { tab ->
                    DialogItem(
                        headlineContent = { Text(text = tab.title) },
                        onClick = tab.onClick,
                        selected = tab.selected,
                    )
                },
            onDismissRequest = { showMore = false },
            waitToLoad = false,
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
                contentColor = if (tab.selected) Color.White else Color(0xFFB3B3B8),
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        modifier = modifier.height(36.dp),
    ) {
        Text(
            text = tab.title,
            fontSize = 15.sp,
            fontWeight = if (tab.selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
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

/** The dark fade behind the top bar: nearly solid at the top, clearing at the bottom edge */
private val TopNavBarShade =
    Brush.verticalGradient(
        0f to Color.Black.copy(alpha = .92f),
        .7f to Color.Black.copy(alpha = .7f),
        1f to Color.Transparent,
    )

/** Height of the top line of the bar (logo and buttons) */
val TopNavBarHeight = 44.dp

/** The Benflix logo, with a small animation and a short line underneath on holidays and birthdays */
@Composable
fun BenflixLogo(modifier: Modifier = Modifier) {
    val celebration by CelebrationNow.current.collectAsState()
    val effect = celebration?.effect ?: LogoEffect.NONE
    Box(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                LogoEffectBehind(effect)
                Image(
                    painter = painterResource(R.drawable.benflix_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.height(26.dp).logoShimmer(effect),
                )
                LogoEffectInFront(effect)
            }
            celebration?.let {
                Text(
                    text = it.text,
                    color = Color(it.color),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        // Days without their own animation keep the small emoji on the corner
        celebration?.takeIf { it.effect == LogoEffect.NONE }?.let {
            Text(
                text = it.emoji,
                fontSize = 14.sp,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-8).dp),
            )
        }
    }
}
