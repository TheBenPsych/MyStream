package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
@OptIn(ExperimentalLayoutApi::class)
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
) {
    val selectedIndex = tabs.indexOfFirst { it.selected }
    val kids by KidsMode.active.collectAsState()
    // Two lines: the logo and buttons on top, then every tab on its own full-width line so none are hidden
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 40.dp, top = 10.dp, bottom = 4.dp)
                .focusGroup()
                .focusProperties {
                    // Entering the bar from the page lands on the current tab
                    onEnter = { focusRequester.tryRequestFocus() }
                },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(TopNavBarHeight),
        ) {
            BenflixLogo(modifier = Modifier.padding(end = 8.dp))
            if (kids) {
                Text(
                    text = stringResource(R.string.kids_profile).uppercase(),
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
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
        // If there are ever more tabs than fit on one line, they wrap onto another line instead of hiding
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().offset(x = (-14).dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                TopNavTabButton(
                    tab = tab,
                    modifier =
                        Modifier.ifElse(
                            index == selectedIndex || (selectedIndex < 0 && index == 0),
                            Modifier.focusRequester(focusRequester),
                        ),
                )
            }
        }
    }
}

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
